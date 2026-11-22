package app.claudian.yuzuk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * 0.15.0 (22.11.2026, Boran): "çok sık yüzük kaydını açık unuttuğum oluyor". Kayıt sürerken her saat başı sesli bir
 * bildirim: "Kayıt 2 saattir sürüyor". Dokununca uygulama açılır; durdurma uygulamadan yapılır (bildirimden doğrudan
 * durdurmak oturumu yarım bırakabilir: döküm nota açılmadan servis kapanır). Kayıt bitince bildirim kalkar.
 *
 * Kayıt servisinin kendi bildirimi sessizdir (IMPORTANCE_LOW, sürekli görünür); bu bildirim ayrı kanaldadır ve titreşir.
 */
final class KayitHatirlatici {
    static final String KANAL = "kayit-suruyor";
    static final int BILDIRIM = 7;
    static final long ARALIK_MS = 60L * 60L * 1000L;

    private static final Handler el = new Handler(Looper.getMainLooper());
    private static Runnable is = null;

    private KayitHatirlatici() { }

    static synchronized void baslat(Context c, long baslangicMs) {
        dur(c);
        Context u = c.getApplicationContext();
        is = new Runnable() {
            @Override public void run() {
                long saat = Math.max(1, Math.round((System.currentTimeMillis() - baslangicMs) / (double) ARALIK_MS));
                bildir(u, saat);
                el.postDelayed(this, ARALIK_MS);
            }
        };
        el.postDelayed(is, Math.max(1000L, baslangicMs + ARALIK_MS - System.currentTimeMillis()));
    }

    static synchronized void dur(Context c) {
        if (is != null) { el.removeCallbacks(is); is = null; }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(BILDIRIM);
    }

    static void kanalKur(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(KANAL) == null) {
            NotificationChannel k = new NotificationChannel(KANAL, "Kayıt sürüyor uyarısı", NotificationManager.IMPORTANCE_HIGH);
            k.setDescription("Kayıt açık kaldıysa her saat başı hatırlatır.");
            k.enableVibration(true);
            nm.createNotificationChannel(k);
        }
    }

    private static void bildir(Context c, long saat) {
        kanalKur(c);
        PendingIntent ac = PendingIntent.getActivity(c, 7, new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, KANAL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Kayıt " + saat + " saattir sürüyor")
                .setContentText("Ders ya da toplantı bittiyse dokun ve kaydı durdur.")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(ac)
                .build();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        try { nm.notify(BILDIRIM, n); } catch (SecurityException ignored) { } // bildirim izni yoksa sessizce geçilir
    }
}
