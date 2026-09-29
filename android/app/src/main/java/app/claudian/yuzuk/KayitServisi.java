package app.claudian.yuzuk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;

/**
 * Ekran kilitliyken de süren kayıt (not alma ve "Sesini tanıt"). Android mikrofonu arka planda yalnız görünür bir ön plan
 * servisine verir. Ses uygulamanın kendi klasörüne yazılır; not Obsidian'a yazılınca (ya da ses izi çıkarılınca) silinir.
 * 0.11.0 (29.09.2026): not kaydı yeniden bu servisle — bağlantı kopsa da kayıt telefonda bekler (Boran'ın A kararı).
 */
public class KayitServisi extends Service {
    static final String KANAL = "kayit";
    static final int BILDIRIM = 1;
    static final String YOL = "yol";

    static volatile boolean calisiyor = false;
    static volatile long baslangicMs = 0L;
    static volatile String sonHata = null;
    static volatile String yol = null;
    private static volatile MediaRecorder kaydedici = null;

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
                .setContentTitle("Claudian dinliyor")
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
        try {
            MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            // MIC: otomatik kazanç açık kalır — derste uzaktaki konuşmacıyı duymak için.
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioChannels(1);
            r.setAudioSamplingRate(16000);
            r.setAudioEncodingBitRate(48000);
            r.setOutputFile(hedef);
            r.prepare();
            r.start();
            kaydedici = r;
            yol = hedef;
            calisiyor = true;
            baslangicMs = System.currentTimeMillis();
            sonHata = null;
        } catch (Exception e) {
            sonHata = e.getMessage() != null ? e.getMessage() : e.toString();
            calisiyor = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        MediaRecorder r = kaydedici;
        kaydedici = null;
        if (r != null) {
            try { r.stop(); } catch (Exception ignored) { }
            r.release();
        }
        calisiyor = false;
        super.onDestroy();
    }

    static void kanalKur(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(KANAL) == null) {
            nm.createNotificationChannel(new NotificationChannel(KANAL, "Kayıt", NotificationManager.IMPORTANCE_LOW));
        }
    }
}
