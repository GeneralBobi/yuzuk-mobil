package app.claudian.yuzuk;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Notlardaki tarihli işler telefonda hatırlatıcı olur. AlarmManager kurulan alarmı telefon yeniden
 * başlayınca unutur; bu yüzden her hatırlatıcı ayrıca SharedPreferences'ta durur ve açılışta yeniden kurulur.
 * Kesin zamanlı alarm izni istenmez: sabah 09:00 civarı yeterli, pil dostu.
 */
public class HatirlaticiAlici extends BroadcastReceiver {
    static final String KANAL = "hatirlatici";
    static final String DEPO = "yuzuk_hatirlaticilar";

    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) || "android.intent.action.MY_PACKAGE_REPLACED".equals(i.getAction())) {
            hepsiniKur(c);
            return;
        }
        String id = i.getStringExtra("id");
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(KANAL) == null) {
            nm.createNotificationChannel(new NotificationChannel(KANAL, "Hatırlatıcılar", NotificationManager.IMPORTANCE_HIGH));
        }
        PendingIntent ac = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        String metin = i.getStringExtra("metin");
        Notification n = new Notification.Builder(c, KANAL)
                .setContentTitle(i.getStringExtra("baslik"))
                .setContentText(metin)
                .setStyle(new Notification.BigTextStyle().bigText(metin))
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentIntent(ac)
                .setAutoCancel(true)
                .build();
        nm.notify(id != null ? id.hashCode() : 0, n);
        sil(c, id);
    }

    static void kur(Context c, String id, long zamanMs, String baslik, String metin) throws Exception {
        SharedPreferences p = c.getSharedPreferences(DEPO, Context.MODE_PRIVATE);
        JSONArray liste = new JSONArray(p.getString("liste", "[]"));
        JSONArray yeni = new JSONArray();
        for (int k = 0; k < liste.length(); k++) if (!id.equals(liste.getJSONObject(k).getString("id"))) yeni.put(liste.get(k));
        yeni.put(new JSONObject().put("id", id).put("zaman", zamanMs).put("baslik", baslik).put("metin", metin));
        p.edit().putString("liste", yeni.toString()).apply();
        alarm(c, id, zamanMs, baslik, metin);
    }

    static int sayi(Context c) {
        try { return new JSONArray(c.getSharedPreferences(DEPO, Context.MODE_PRIVATE).getString("liste", "[]")).length(); }
        catch (Exception e) { return 0; }
    }

    private static void alarm(Context c, String id, long zamanMs, String baslik, String metin) {
        Intent i = new Intent(c, HatirlaticiAlici.class).putExtra("id", id).putExtra("baslik", baslik).putExtra("metin", metin);
        PendingIntent pi = PendingIntent.getBroadcast(c, id.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, zamanMs, pi);
    }

    private static void hepsiniKur(Context c) {
        try {
            JSONArray liste = new JSONArray(c.getSharedPreferences(DEPO, Context.MODE_PRIVATE).getString("liste", "[]"));
            long simdi = System.currentTimeMillis();
            for (int k = 0; k < liste.length(); k++) {
                JSONObject o = liste.getJSONObject(k);
                // Telefon kapalıyken zamanı geçen hatırlatıcı açılışta hemen gösterilir.
                alarm(c, o.getString("id"), Math.max(o.getLong("zaman"), simdi + 5000), o.getString("baslik"), o.getString("metin"));
            }
        } catch (Exception ignored) { }
    }

    private static void sil(Context c, String id) {
        try {
            SharedPreferences p = c.getSharedPreferences(DEPO, Context.MODE_PRIVATE);
            JSONArray liste = new JSONArray(p.getString("liste", "[]"));
            JSONArray yeni = new JSONArray();
            for (int k = 0; k < liste.length(); k++) if (!liste.getJSONObject(k).getString("id").equals(id)) yeni.put(liste.get(k));
            p.edit().putString("liste", yeni.toString()).apply();
        } catch (Exception ignored) { }
    }
}
