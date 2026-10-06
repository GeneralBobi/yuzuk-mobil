package app.claudian.yuzuk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

/**
 * Uygulamadan çıkınca teslimi sürdüren ön plan servisi (0.11.8, 06.10.2026).
 * Kayıt durdurulup uygulama kapatıldığında Android WebView'ı uyutur; o güne kadar yükleme ve durum sorma yalnız uygulama
 * açıkken çalışıyordu, 15 dakikalık arka plan işi de yalnız hazır notu teslim alıyordu. Bu servis bekleyen kaydı
 * buluta yükler, bilgisayar notu yazana kadar sorar, notu Obsidian'a yazar ve iş kalmayınca kendini kapatır.
 * Uygulama yeniden açılınca hattı uygulama devralır, servis çekilir. Görünür bildirim Android'in şartıdır.
 */
public class TeslimServisi extends Service {
    static final String KANAL = "teslim";
    static final int BILDIRIM = 3;
    /** İlerleme olmasa da en çok bu kadar sürer; sonrasını 15 dakikalık arka plan işi devralır. */
    private static final long EN_COK_MS = TimeUnit.MINUTES.toMillis(60);
    static volatile boolean calisiyor;
    private volatile boolean durdur;
    private PowerManager.WakeLock kilit;

    /** Bekleyen iş varsa başlatır. Etkinlik görünürken (onPause) çağrılır: Android ön plan servisine ancak o an izin verir. */
    static void baslat(Context c) {
        if (calisiyor || !ArkaPlanIsci.bekleyenVar(c)) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(new Intent(c, TeslimServisi.class));
            else c.startService(new Intent(c, TeslimServisi.class));
        } catch (Exception e) {
            ArkaPlanIsci.hemen(c); // servis açılamadıysa (ör. pil kısıtlaması) en azından arka plan işi dener
        }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification b = bildirim("Kayıt Obsidian'a taşınıyor");
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(BILDIRIM, b, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(BILDIRIM, b);
        } catch (Exception e) {
            stopSelf();
            ArkaPlanIsci.hemen(this);
            return START_NOT_STICKY;
        }
        if (calisiyor) return START_NOT_STICKY;
        calisiyor = true;
        durdur = false;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        kilit = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yuzuk:teslim");
        kilit.setReferenceCounted(false);
        kilit.acquire(EN_COK_MS + 60_000L);
        new Thread(this::calis, "yuzuk-teslim").start();
        return START_NOT_STICKY;
    }

    private void calis() {
        long son = SystemClock.elapsedRealtime() + EN_COK_MS;
        boolean kilitli = false;
        try {
            kilitli = ArkaPlanIsci.CALISMA.tryLock(60, TimeUnit.SECONDS);
            while (kilitli && !durdur && !ArkaPlanIsci.onPlanda && SystemClock.elapsedRealtime() < son) {
                int kalan = ArkaPlanIsci.tur(this, son, () -> durdur || ArkaPlanIsci.onPlanda, this::guncelle);
                if (kalan <= 0) break;
                // Bilgisayar notu yazarken 15 sn'de bir sorulur; bilgisayar hazır değilse dakikada bir.
                bekle(ArkaPlanIsci.engel ? 60_000L : 15_000L);
            }
        } catch (Exception ignored) {
            ArkaPlanIsci.hemen(this);
        } finally {
            if (kilitli) ArkaPlanIsci.CALISMA.unlock();
            calisiyor = false;
            if (kilit != null && kilit.isHeld()) kilit.release();
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void bekle(long ms) {
        long bitis = SystemClock.elapsedRealtime() + ms;
        while (!durdur && !ArkaPlanIsci.onPlanda && SystemClock.elapsedRealtime() < bitis) {
            try { Thread.sleep(500); } catch (InterruptedException e) { return; }
        }
    }

    private void guncelle(String metin) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try { nm.notify(BILDIRIM, bildirim(metin)); } catch (SecurityException ignored) { }
    }

    private Notification bildirim(String metin) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // Düşük önem: ses çıkarmaz, durum çubuğunda sessiz durur. Not hazır olunca ayrı bildirim gelir.
        nm.createNotificationChannel(new NotificationChannel(KANAL, "Kayıt teslimi", NotificationManager.IMPORTANCE_LOW));
        PendingIntent ac = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, KANAL)
                .setContentTitle("Yüzük")
                .setContentText(metin)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(ac)
                .build();
    }

    /** Android 15: veri eşitleme servisinin günlük süresi dolarsa sistem çağırır; tur yarıda bırakılır, ses telefonda kalır. */
    @Override public void onTimeout(int startId, int fgsType) { durdur = true; }

    @Override public void onDestroy() { durdur = true; super.onDestroy(); }
}
