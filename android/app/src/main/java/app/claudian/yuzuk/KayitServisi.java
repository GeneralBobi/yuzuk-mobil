package app.claudian.yuzuk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Ekran kilitliyken de süren kayıt (not alma ve "Sesini tanıt"). Android mikrofonu arka planda yalnız görünür bir ön plan
 * servisine verir. Ses uygulamanın kendi klasörüne yazılır; not Obsidian'a yazılınca (ya da ses izi çıkarılınca) silinir.
 * 0.11.0 (29.09.2026): not kaydı yeniden bu servisle — bağlantı kopsa da kayıt telefonda bekler (Boran'ın A kararı).
 * 0.11.5 (29.09.2026): 79 dakikalık bir kaydın dosyası 34. dakikada bitmişti; sayaç sürerken kaydedici yazmayı
 * bırakmıştı. Artık kaydedici hata verirse ya da dosya 60 sn büyümezse yeni bir parçaya geçilir, kayıt durunca parçalar
 * tek dosyada birleşir. CPU kilidi (PARTIAL_WAKE_LOCK) kayıt boyunca tutulur.
 */
public class KayitServisi extends Service {
    static final String KANAL = "kayit";
    static final int BILDIRIM = 1;
    static final String YOL = "yol";
    private static final long NOBET_MS = 20_000L;

    static volatile boolean calisiyor = false;
    static volatile long baslangicMs = 0L;
    static volatile String sonHata = null;
    static volatile String yol = null;
    /** Bu kaydın parça dosyaları, sırasıyla. İlki `yol`dur. */
    static final List<String> parcalar = new ArrayList<>();
    /** Kaydedicinin kaç kez yeniden başlatıldığı (hata ya da duran dosya). */
    static volatile int kesinti = 0;
    private static volatile MediaRecorder kaydedici = null;

    private final Handler nobetci = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock kilit;
    private long sonBoyut = -1;
    private int duranTur = 0;
    private volatile boolean bitiyor = false;

    /** 0..1 arası anlık ses şiddeti; arayüzdeki seviye çubuğu için. */
    static float seviye() {
        MediaRecorder r = kaydedici;
        if (r == null) return 0f;
        try {
            return Math.min(1f, r.getMaxAmplitude() / 20000f);
        } catch (Exception e) {
            return 0f;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String hedef = intent != null ? intent.getStringExtra(YOL) : null;
        if (hedef == null) return START_NOT_STICKY;
        kanalKur(this);
        PendingIntent ac = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification bildirim = new Notification.Builder(this, KANAL)
                .setContentTitle("Yüzük dinliyor")
                .setContentText("Not almak için kayıt sürüyor. Durdurmak için uygulamaya dön.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .setContentIntent(ac)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(BILDIRIM, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(BILDIRIM, bildirim);
        }
        synchronized (parcalar) {
            parcalar.clear();
        }
        kesinti = 0;
        yol = hedef;
        try {
            kaydediciBaslat(hedef);
            calisiyor = true;
            baslangicMs = System.currentTimeMillis();
            sonHata = null;
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            kilit = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yuzuk:kayit");
            kilit.setReferenceCounted(false);
            kilit.acquire(6 * 60 * 60 * 1000L); // en uzun kayıt süresi; kayıt durunca hemen bırakılır
            nobetci.postDelayed(this::nobet, NOBET_MS);
            KayitHatirlatici.baslat(this, baslangicMs); // 0.15.4: kayıt açık unutulmasın, saat başı uyarı
        } catch (Exception e) {
            sonHata = e.getMessage() != null ? e.getMessage() : e.toString();
            calisiyor = false;
            yol = null;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void kaydediciBaslat(String dosya) throws Exception {
        MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
        // MIC: otomatik kazanç açık kalır — derste uzaktaki konuşmacıyı duymak için.
        r.setAudioSource(MediaRecorder.AudioSource.MIC);
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        r.setAudioChannels(1);
        r.setAudioSamplingRate(16000);
        r.setAudioEncodingBitRate(48000);
        r.setOutputFile(dosya);
        r.setOnErrorListener((mr, what, extra) -> nobetci.post(() -> yenidenBaslat("kaydedici hatası " + what + "/" + extra)));
        r.prepare();
        r.start();
        kaydedici = r;
        synchronized (parcalar) {
            parcalar.add(dosya);
        }
        sonBoyut = -1;
        duranTur = 0;
    }

    /** Dosya büyümüyorsa kaydedici sessizce durmuştur: yeni parçaya geçilir. */
    private void nobet() {
        if (!calisiyor) return;
        String son;
        synchronized (parcalar) {
            son = parcalar.isEmpty() ? null : parcalar.get(parcalar.size() - 1);
        }
        if (kaydedici == null) {
            yenidenBaslat(null); // önceki deneme başlatamadıysa (mikrofon başka uygulamadaydı) yeniden dene
        } else if (son != null) {
            long boyut = new File(son).length();
            duranTur = boyut == sonBoyut ? duranTur + 1 : 0;
            sonBoyut = boyut;
            if (duranTur >= 3) yenidenBaslat("dosya büyümüyor");
        }
        if (calisiyor) nobetci.postDelayed(this::nobet, NOBET_MS);
    }

    private void yenidenBaslat(String neden) {
        if (!calisiyor || bitiyor) return;
        MediaRecorder eski = kaydedici;
        kaydedici = null;
        if (eski != null) {
            try { eski.stop(); } catch (Exception ignored) { }
            eski.release();
            kesinti++;
        }
        if (neden != null) sonHata = neden;
        String temel = yol != null ? yol : new File(getFilesDir(), "kayit_" + baslangicMs + ".m4a").getAbsolutePath();
        String dosya = temel.replaceAll("\\.m4a$", "") + "_p" + (System.currentTimeMillis() % 1_000_000) + ".m4a";
        try {
            kaydediciBaslat(dosya);
        } catch (Exception e) {
            sonHata = e.getMessage() != null ? e.getMessage() : e.toString();
            new File(dosya).delete();
        }
    }

    @Override
    public void onDestroy() {
        bitiyor = true;
        nobetci.removeCallbacksAndMessages(null);
        KayitHatirlatici.dur(this);
        MediaRecorder r = kaydedici;
        kaydedici = null;
        if (r != null) {
            try { r.stop(); } catch (Exception ignored) { }
            r.release();
        }
        // Dosya kapandıktan sonra: kayitDurdur bunu bekleyip parçaları birleştirir.
        calisiyor = false;
        if (kilit != null && kilit.isHeld()) kilit.release();
        super.onDestroy();
    }

    /** Kayıt durduktan sonra: parçalar tek dosyada birleşir (sıra ve zaman korunur). Tek parça varsa o döner. */
    static String birlestir() {
        List<String> liste;
        synchronized (parcalar) {
            liste = new ArrayList<>(parcalar);
        }
        List<String> dolu = new ArrayList<>();
        for (String p : liste) if (new File(p).length() > 2000) dolu.add(p);
        if (dolu.isEmpty()) return liste.isEmpty() ? null : liste.get(0);
        if (dolu.size() == 1) {
            for (String p : liste) if (!p.equals(dolu.get(0))) new File(p).delete();
            return dolu.get(0);
        }
        String hedef = dolu.get(0).replaceAll("\\.m4a$", "") + "_tam.m4a";
        MediaMuxer mux = null;
        try {
            mux = new MediaMuxer(hedef, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int iz = -1;
            long kayma = 0;
            ByteBuffer tampon = ByteBuffer.allocate(1 << 16);
            MediaCodec.BufferInfo bilgi = new MediaCodec.BufferInfo();
            for (String p : dolu) {
                MediaExtractor ex = new MediaExtractor();
                try {
                    ex.setDataSource(p);
                    if (ex.getTrackCount() == 0) continue;
                    ex.selectTrack(0);
                    if (iz < 0) {
                        iz = mux.addTrack(ex.getTrackFormat(0));
                        mux.start();
                    }
                    long son = 0;
                    while (true) {
                        int n = ex.readSampleData(tampon, 0);
                        if (n < 0) break;
                        son = ex.getSampleTime();
                        bilgi.set(0, n, kayma + son, (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                        mux.writeSampleData(iz, tampon, bilgi);
                        ex.advance();
                    }
                    kayma += son + 64_000L; // bir AAC çerçevesi (1024 örnek / 16 kHz)
                } catch (Exception okunamadi) {
                    // bitmemiş parça (süreç öldüyse) atlanır; kalanlar birleşir
                } finally {
                    ex.release();
                }
            }
            if (iz < 0) {
                mux.release();
                new File(hedef).delete();
                return dolu.get(0);
            }
            mux.stop();
            mux.release();
            for (String p : liste) new File(p).delete();
            return hedef;
        } catch (Exception e) {
            sonHata = "parçalar birleştirilemedi: " + e.getMessage();
            if (mux != null) try { mux.release(); } catch (Exception ignored) { }
            new File(hedef).delete();
            return dolu.get(0);
        }
    }

    /** Dosyadaki sesin gerçek süresi (sn); okunamazsa 0. */
    static long sesSuresiSn(String dosya) {
        MediaMetadataRetriever m = new MediaMetadataRetriever();
        try {
            m.setDataSource(dosya);
            String s = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return s != null ? Long.parseLong(s) / 1000 : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            try { m.release(); } catch (Exception ignored) { }
        }
    }

    static void kanalKur(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(KANAL) == null) {
            nm.createNotificationChannel(new NotificationChannel(KANAL, "Kayıt", NotificationManager.IMPORTANCE_LOW));
        }
    }
}
