package app.claudian.yuzuk;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Ses dosyası olmayan kayıt (27.09.2026). Mikrofon sesi yalnız bellekte, sessizlikte kesilen kısa parçalar hâlinde
 * durur. Her parça önce bilgisayara (bulut → işlem düğümü, büyük Whisper) gider; bağlantı yoksa telefonda
 * (whisper.cpp) metne çevrilir. Parça metne dönünce bellekten atılır. Diske yazılan tek şey metindir: oturumun
 * döküm dosyası (JSON satırları: cümle, zaman, ses izi sayıları). Hukuki gerekçe: tekrar dinlenebilir ses kaydı
 * üretilmez (Yargıtay 12. CD 2014/2511). Güvence: bağlantı kopsa da not kaybolmaz (Boran, 27.09.2026 — yurt
 * bodrumunda 96 dakikalık ders).
 *
 * Bildirimdeki "Claudian not alıyor" satırı Android'in mikrofon için zorunlu kıldığı görünür ön plan göstergesidir.
 */
public class CanliKayitServisi extends Service {
    static final int BILDIRIM = 2;
    static final int HIZ = 16000;
    static final int KARE = 480;                 // 30 ms
    static final int EN_UZUN_KARE = 25_000 / 30; // 25 sn'de kes: Whisper'ın 30 sn penceresine sığsın
    static final int SESSIZ_BITIS = 700 / 30;    // 0,7 sn sessizlik cümleyi bitirir
    static final int ON_KARE = 300 / 30;         // konuşma başlamadan önceki 0,3 sn de parçaya girer
    static final int EN_AZ_KONUSMA = 500 / 30;   // 0,5 sn'den az konuşma içeren parça atılır

    static volatile boolean calisiyor = false;   // mikrofon açık
    static volatile boolean isleniyor = false;   // mikrofon kapandı, kalan parçalar metne dönüyor
    static volatile long baslangicMs = 0L;
    static volatile float seviye = 0f;
    static volatile String sonHata = null;
    static volatile String dokum = null;         // döküm dosyasının yolu
    static volatile int gonderilen = 0, yerelde = 0, bekleyen = 0, cumle = 0;
    static volatile String sonDurum = null;      // "bilgisayar" | "bulut" | "telefon" | "bekliyor"

    private static final class Parca {
        final double bas;
        final byte[] pcm;
        Parca(double bas, byte[] pcm) { this.bas = bas; this.pcm = pcm; }
    }

    private final LinkedBlockingDeque<Parca> kuyruk = new LinkedBlockingDeque<>();
    private volatile boolean dur = false;
    private Thread kayitci, isci;
    private String sunucu, anahtar, dil;
    private boolean bulut; // 0.15.0 [beta]: parça bilgisayara değil bulutun GPU'suna gider (/v1/bulut/parca)
    private String testPcm; // yalnız hata ayıklama derlemesi: mikrofon yerine 16 kHz PCM dosyası (emülatör testi)
    private OutputStream dokumYazici;
    private long ornekSayisi = 0L;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "dur".equals(intent.getAction())) {
            mikrofonuKapat();
            return START_NOT_STICKY;
        }
        if (intent == null || calisiyor || isleniyor) return START_NOT_STICKY;
        sunucu = intent.getStringExtra("sunucu");
        anahtar = intent.getStringExtra("anahtar");
        bulut = intent.getBooleanExtra("bulut", false);
        dil = intent.getStringExtra("dil");
        testPcm = BuildConfig.DEBUG ? intent.getStringExtra("testPcm") : null;
        String yol = intent.getStringExtra("dokum");
        KayitServisi.kanalKur(this);
        Notification b = bildirim("Claudian not alıyor", "Ses kaydedilmiyor; konuşma metne çevriliyor. Durdurmak için uygulamaya dön.");
        if (Build.VERSION.SDK_INT >= 29) startForeground(BILDIRIM, b, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(BILDIRIM, b);
        try {
            dokumYazici = new FileOutputStream(yol, true);
            satir(new JSONObject().put("tur", "oturum").put("baslangic", System.currentTimeMillis()));
            dokum = yol;
            gonderilen = yerelde = bekleyen = cumle = 0;
            sonHata = null;
            sonDurum = null;
            baslangicMs = System.currentTimeMillis();
            calisiyor = true;
            kayitci = new Thread(this::kaydet, "yuzuk-mikrofon");
            isci = new Thread(this::isle, "yuzuk-parca");
            kayitci.start();
            isci.start();
        } catch (Exception e) {
            sonHata = e.getMessage() != null ? e.getMessage() : e.toString();
            calisiyor = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    /** Arayüz "Durdur" dedi: mikrofon kapanır, bekleyen parçalar metne dönene kadar servis sürer. */
    static void durdur(Context c) {
        c.startService(new Intent(c, CanliKayitServisi.class).setAction("dur"));
    }

    // --------------------------------------------------------------- mikrofon → parçalar

    private void kaydet() {
        int tampon = Math.max(AudioRecord.getMinBufferSize(HIZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), KARE * 2 * 8);
        AudioRecord r = null;
        java.io.DataInputStream test = null;
        try {
            if (testPcm != null) {
                test = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(testPcm)));
            } else {
                r = new AudioRecord(MediaRecorder.AudioSource.MIC, HIZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, tampon);
                r.startRecording();
            }
        } catch (SecurityException | IllegalStateException | java.io.IOException e) {
            sonHata = "Mikrofon açılamadı: " + e.getMessage();
            calisiyor = false;
            kuyruk.offer(new Parca(-1, null));
            return;
        }
        short[] kare = new short[KARE];
        ArrayDeque<byte[]> onceki = new ArrayDeque<>();
        ByteArrayOutputStream parca = null;
        int parcaKare = 0, konusmaKare = 0, sessiz = 0;
        double parcaBas = 0;
        double taban = 400;
        try {
            while (!dur) {
                int n = r != null ? r.read(kare, 0, KARE) : testOku(test, kare);
                if (n < 0) { dur = true; break; }
                if (n <= 0) continue;
                double top = 0;
                for (int i = 0; i < n; i++) top += (double) kare[i] * kare[i];
                double rms = Math.sqrt(top / n);
                seviye = (float) Math.min(1.0, rms / 6000.0);
                // Gürültü tabanı yavaş yükselir, hızlı düşer: sınıf uğultusunda da konuşma seçilir.
                taban = rms < taban ? taban * 0.9 + rms * 0.1 : taban * 0.999 + rms * 0.001;
                boolean konusma = rms > Math.max(taban * 2.5, 250);
                byte[] b = new byte[n * 2];
                for (int i = 0; i < n; i++) { b[2 * i] = (byte) kare[i]; b[2 * i + 1] = (byte) (kare[i] >> 8); }
                double an = ornekSayisi / (double) HIZ;
                ornekSayisi += n;
                if (parca == null) {
                    onceki.addLast(b);
                    if (onceki.size() > ON_KARE) onceki.removeFirst();
                    if (konusma) {
                        parca = new ByteArrayOutputStream(HIZ * 2 * 10);
                        parcaBas = an - (onceki.size() - 1) * 0.03;
                        for (byte[] o : onceki) parca.write(o, 0, o.length);
                        onceki.clear();
                        parcaKare = ON_KARE;
                        konusmaKare = 1;
                        sessiz = 0;
                    }
                    continue;
                }
                parca.write(b, 0, b.length);
                parcaKare++;
                if (konusma) { konusmaKare++; sessiz = 0; } else sessiz++;
                if ((sessiz >= SESSIZ_BITIS && parcaKare > 1000 / 30) || parcaKare >= EN_UZUN_KARE) {
                    if (konusmaKare >= EN_AZ_KONUSMA) kuyruk.offer(new Parca(Math.max(0, parcaBas), parca.toByteArray()));
                    parca = null;
                }
            }
            if (parca != null && konusmaKare >= EN_AZ_KONUSMA) kuyruk.offer(new Parca(Math.max(0, parcaBas), parca.toByteArray()));
        } finally {
            if (r != null) {
                try { r.stop(); } catch (Exception ignored) { }
                r.release();
            }
            if (test != null) try { test.close(); } catch (Exception ignored) { }
            seviye = 0f;
            isleniyor = true;  // mikrofon kapandı; kalan parçalar metne dönene kadar servis sürer
            calisiyor = false;
            kuyruk.offer(new Parca(-1, null)); // son işareti
        }
    }

    // --------------------------------------------------------------- parçalar → metin

    private void isle() {
        ArrayDeque<Parca> bekleyenler = new ArrayDeque<>(); // ne bilgisayara ne telefona çevrilebilenler (model yok)
        boolean son = false;
        try {
            while (!son || !bekleyenler.isEmpty()) {
                Parca p = son ? null : kuyruk.poll(bekleyenler.isEmpty() ? 60 : 15, TimeUnit.SECONDS);
                if (p != null && p.pcm == null) { son = true; continue; }
                if (p == null) {
                    if (bekleyenler.isEmpty()) continue;
                    p = bekleyenler.pollFirst();
                    if (!coz(p)) { bekleyenler.addFirst(p); if (son) Thread.sleep(15000); }
                } else if (!coz(p)) {
                    bekleyenler.addLast(p);
                }
                bekleyen = bekleyenler.size() + kuyruk.size();
            }
            bekleyen = 0;
            satir(new JSONObject().put("tur", "son").put("sure_sn", ornekSayisi / (double) HIZ).put("dil", dil == null ? JSONObject.NULL : dil));
        } catch (Exception e) {
            sonHata = "Parça işlenemedi: " + e.getMessage();
        } finally {
            try { if (dokumYazici != null) dokumYazici.close(); } catch (Exception ignored) { }
            isleniyor = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    /** Parçayı metne çevirir: önce bilgisayar, olmazsa telefon. Başarırsa parça bellekten düşer (true). */
    private boolean coz(Parca p) throws Exception {
        String cevap = bilgisayara(p);
        boolean yerel = false;
        if (cevap == null) {
            cevap = YerelWhisper.metin(this, ornekler(p.pcm), dil);
            yerel = cevap != null;
        }
        if (cevap == null) { sonDurum = "bekliyor"; return false; }
        JSONObject j = new JSONObject(cevap);
        if (j.has("hata")) { sonDurum = "bekliyor"; return false; }
        if (dil == null && j.optString("dil", "").length() == 2) dil = j.optString("dil");
        JSONArray cs = j.optJSONArray("cumleler");
        for (int i = 0; cs != null && i < cs.length(); i++) {
            JSONObject c = cs.getJSONObject(i);
            if (yerel || bulut) { // telefonun ve bulutun zamanları parçanın başından; bilgisayar oturum zamanını döndürür
                c.put("bas", Math.round((c.getDouble("bas") + p.bas) * 100) / 100.0);
                c.put("son", Math.round((c.getDouble("son") + p.bas) * 100) / 100.0);
                c.put(yerel ? "yerel" : "bulut", true);
            }
            satir(c);
            cumle++;
        }
        if (yerel) yerelde++; else gonderilen++;
        sonDurum = yerel ? "telefon" : bulut ? "bulut" : "bilgisayar";
        return true;
    }

    private String bilgisayara(Parca p) {
        if (sunucu == null || anahtar == null || anahtar.isEmpty()) return null;
        HttpURLConnection b = null;
        try {
            String adres = sunucu + (bulut ? "/v1/bulut/parca" : "/v1/parca") + "?bas=" + String.format(java.util.Locale.ROOT, "%.2f", p.bas) + (dil != null ? "&dil=" + dil : "");
            b = (HttpURLConnection) new URL(adres).openConnection();
            b.setConnectTimeout(6000);
            b.setReadTimeout(45000);
            b.setDoOutput(true);
            b.setRequestMethod("POST");
            b.setRequestProperty("Authorization", "Bearer " + anahtar);
            b.setRequestProperty("Content-Type", "application/octet-stream");
            b.setFixedLengthStreamingMode(p.pcm.length);
            try (OutputStream o = b.getOutputStream()) { o.write(p.pcm); }
            if (b.getResponseCode() != 200) return null;
            try (InputStream in = b.getInputStream()) {
                ByteArrayOutputStream t = new ByteArrayOutputStream();
                byte[] k = new byte[8192];
                int n;
                while ((n = in.read(k)) > 0) t.write(k, 0, n);
                return t.toString("UTF-8");
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (b != null) b.disconnect();
        }
    }

    private int testSessiz = 0;

    /** Test dosyasından bir kare (little-endian). Dosya bitince 1 sn sessizlik, sonra -1. */
    private int testOku(java.io.DataInputStream in, short[] kare) {
        try {
            for (int i = 0; i < kare.length; i++) {
                int a = in.read(), b = in.read();
                if (a < 0 || b < 0) throw new java.io.EOFException();
                kare[i] = (short) (a | (b << 8));
            }
            return kare.length;
        } catch (java.io.IOException e) {
            if (testSessiz++ > 1000 / 30) return -1;
            java.util.Arrays.fill(kare, (short) 0);
            return kare.length;
        }
    }

    private static float[] ornekler(byte[] pcm) {
        float[] f = new float[pcm.length / 2];
        for (int i = 0; i < f.length; i++) f[i] = (short) ((pcm[2 * i] & 0xff) | (pcm[2 * i + 1] << 8)) / 32768f;
        return f;
    }

    private synchronized void satir(JSONObject j) throws Exception {
        dokumYazici.write((j.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        dokumYazici.flush();
    }

    private Notification bildirim(String baslik, String metin) {
        PendingIntent ac = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, KayitServisi.KANAL).setContentTitle(baslik).setContentText(metin)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).setContentIntent(ac).build();
    }

    void mikrofonuKapat() {
        if (kayitci == null) return;
        dur = true;
        isleniyor = true;
        {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(BILDIRIM, bildirim("Claudian notu hazırlıyor", "Son parçalar metne çevriliyor."));
        }
    }
}
