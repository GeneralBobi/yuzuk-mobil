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

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * Notlardaki tarihli işler telefonda hatırlatıcı olur. AlarmManager kurulan alarmı telefon yeniden
 * başlayınca unutur; bu yüzden her hatırlatıcı ayrıca SharedPreferences'ta durur ve açılışta yeniden kurulur.
 * Kesin zamanlı alarm izni istenmez: sabah 09:00 civarı yeterli, pil dostu.
 * İki kaynak var: telefonun işlediği notlar (id: oturum-sıra) ve bilgisayardaki vault (id: "pc-" + düğümün kimliği).
 *
 * 1.5.0: vault'tan gelen hatırlatıcının bildiriminde "Yaptım" ve "Yarın tekrar" düğmeleri var. Düğme uygulamayı açmaz:
 * istek buradan buluta (oradan sahibin bilgisayarına) gider, satırı bilgisayardaki Claudian yazar. İnternet yoksa
 * istek sıraya girer ve uygulama açılınca gönderilir. "Yarın tekrar" telefonda hemen yarının aynı saatine kurulur.
 */
public class HatirlaticiAlici extends BroadcastReceiver {
    static final String KANAL = "hatirlatici";
    static final String DEPO = "yuzuk_hatirlaticilar";
    static final String ONEK_PC = "pc-";
    static final String ONEK_ERTELE = "ertele-";
    static final String ISLEM = "app.claudian.yuzuk.HATIRLATICI_ISLEM";
    private static final long SAAT = 3600_000L;

    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) || "android.intent.action.MY_PACKAGE_REPLACED".equals(i.getAction())) {
            hepsiniKur(c);
            return;
        }
        if (ISLEM.equals(i.getAction())) {
            islemYap(c, i);
            return;
        }
        String id = i.getStringExtra("id");
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(KANAL) == null) {
            nm.createNotificationChannel(new NotificationChannel(KANAL, "Hatırlatıcılar", NotificationManager.IMPORTANCE_HIGH));
        }
        PendingIntent ac = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        String metin = i.getStringExtra("metin");
        String baslik = i.getStringExtra("baslik");
        long zaman = i.getLongExtra("zaman", System.currentTimeMillis());
        Notification.Builder nb = new Notification.Builder(c, KANAL)
                .setContentTitle(baslik)
                .setContentText(metin)
                .setStyle(new Notification.BigTextStyle().bigText(metin))
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentIntent(ac)
                .setAutoCancel(true);
        // Düğmeler yalnız vault'taki kalemde: satır bilgisayarda değişir. Ertelenmiş kalem de vault kalemidir.
        String vaultKimligi = vaultKimligi(id);
        if (vaultKimligi != null) {
            nb.addAction(new Notification.Action.Builder(null, "Yaptım", islemNiyeti(c, id, "yapildi", baslik, metin, zaman)).build());
            nb.addAction(new Notification.Action.Builder(null, "Yarın tekrar", islemNiyeti(c, id, "yarin", baslik, metin, zaman)).build());
        }
        nm.notify(id != null ? id.hashCode() : 0, nb.build());
        sil(c, id);
        gosterildi(c, id, i.getStringExtra("baslik"), i.getLongExtra("zaman", System.currentTimeMillis()));
    }

    /** "pc-<kimlik>" ya da "ertele-pc-<kimlik>" → vault kimliği; telefonun kendi notundan kurulan kalemde null. */
    static String vaultKimligi(String id) {
        if (id == null) return null;
        String s = id.startsWith(ONEK_ERTELE) ? id.substring(ONEK_ERTELE.length()) : id;
        if (!s.startsWith(ONEK_PC)) return null;
        s = s.substring(ONEK_PC.length());
        return s.matches("[0-9a-f]{16}") ? s : null;
    }

    private static PendingIntent islemNiyeti(Context c, String id, String islem, String baslik, String metin, long zaman) {
        Intent i = new Intent(c, HatirlaticiAlici.class).setAction(ISLEM).putExtra("id", id).putExtra("islem", islem)
                .putExtra("baslik", baslik).putExtra("metin", metin).putExtra("zaman", zaman);
        return PendingIntent.getBroadcast(c, (id + "|" + islem).hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void islemYap(Context c, Intent i) {
        String id = i.getStringExtra("id"), islem = i.getStringExtra("islem");
        String kimlik = vaultKimligi(id);
        if (kimlik == null || !("yapildi".equals(islem) || "yarin".equals(islem))) return;
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id.hashCode());
        if ("yarin".equals(islem)) {
            // Telefonda hemen: yarın aynı saat. Ayrı kimlikle kurulur ki bilgisayarla eşitleme onu iptal etmesin;
            // vault yeni tarihi gönderdiğinde aynı metin ve saat olduğu için ikinci kez çalmaz (telefondaVar).
            Calendar eski = Calendar.getInstance();
            eski.setTimeInMillis(i.getLongExtra("zaman", System.currentTimeMillis()));
            Calendar yarin = Calendar.getInstance();
            yarin.add(Calendar.DAY_OF_MONTH, 1);
            yarin.set(Calendar.HOUR_OF_DAY, eski.get(Calendar.HOUR_OF_DAY));
            yarin.set(Calendar.MINUTE, eski.get(Calendar.MINUTE));
            yarin.set(Calendar.SECOND, 0);
            try {
                kur(c, ONEK_ERTELE + ONEK_PC + kimlik, yarin.getTimeInMillis(), i.getStringExtra("baslik"), i.getStringExtra("metin"));
            } catch (Exception ignored) { }
        }
        final PendingResult sonuc = goAsync();
        new Thread(() -> {
            try {
                if (!gonder(c, kimlik, islem)) kuyruga(c, kimlik, islem);
            } finally {
                sonuc.finish();
            }
        }).start();
    }

    /** Bulut adresi ve cihaz anahtarı: uygulama her hatırlatıcı eşitlemesinde yazar (boş anahtar = eşleşme yok). */
    static void baglanti(Context c, String sunucu, String anahtar) {
        SharedPreferences.Editor e = c.getSharedPreferences(DEPO, Context.MODE_PRIVATE).edit();
        if (anahtar == null || anahtar.isEmpty() || sunucu == null || !sunucu.startsWith("https://")) e.remove("sunucu").remove("anahtar");
        else e.putString("sunucu", sunucu).putString("anahtar", anahtar);
        e.apply();
    }

    /** true: istek bitti (işlendi ya da artık anlamsız); false: ağ yok ya da bulut geçici hata verdi, yeniden denenmeli. */
    static boolean gonder(Context c, String kimlik, String islem) {
        SharedPreferences p = c.getSharedPreferences(DEPO, Context.MODE_PRIVATE);
        String sunucu = p.getString("sunucu", null), anahtar = p.getString("anahtar", null);
        if (sunucu == null || anahtar == null) return false;
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(sunucu.replaceAll("/+$", "") + "/v1/hatirlaticilar/" + kimlik).openConnection();
            h.setRequestMethod("POST");
            h.setConnectTimeout(10_000);
            h.setReadTimeout(30_000);
            h.setDoOutput(true);
            h.setRequestProperty("Authorization", "Bearer " + anahtar);
            h.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = h.getOutputStream()) {
                o.write(new JSONObject().put("islem", islem).toString().getBytes(StandardCharsets.UTF_8));
            }
            int kod = h.getResponseCode();
            return kod < 500 && kod != 401 && kod != 429;
        } catch (Exception e) {
            return false;
        } finally {
            if (h != null) h.disconnect();
        }
    }

    private static synchronized void kuyruga(Context c, String kimlik, String islem) {
        try {
            SharedPreferences p = c.getSharedPreferences(DEPO, Context.MODE_PRIVATE);
            JSONArray k = new JSONArray(p.getString("islemler", "[]"));
            k.put(new JSONObject().put("kimlik", kimlik).put("islem", islem).put("an", System.currentTimeMillis()));
            p.edit().putString("islemler", k.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Sırada bekleyen düğme işlemleri; alınınca sıradan çıkar (uygulama gönderemezse kendi tarafında tutar). */
    static synchronized JSONArray islemleriAl(Context c) {
        SharedPreferences p = c.getSharedPreferences(DEPO, Context.MODE_PRIVATE);
        try {
            JSONArray k = new JSONArray(p.getString("islemler", "[]"));
            p.edit().remove("islemler").apply();
            return k;
        } catch (Exception e) {
            return new JSONArray();
        }
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
        Intent i = new Intent(c, HatirlaticiAlici.class).putExtra("id", id).putExtra("baslik", baslik).putExtra("metin", metin)
                .putExtra("zaman", zamanMs);
        PendingIntent pi = PendingIntent.getBroadcast(c, id.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, zamanMs, pi);
    }

    private static void iptal(Context c, String id) {
        PendingIntent pi = PendingIntent.getBroadcast(c, id.hashCode(), new Intent(c, HatirlaticiAlici.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        if (pi == null) return;
        ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(pi);
        pi.cancel();
    }

    /**
     * Bilgisayardan gelen tam listeyle telefondaki "pc-" hatırlatıcıları aynı yapar. Listede olmayan (yapıldı, silindi)
     * iptal edilir. Aynı id tek alarmdır, yeniden kurmak yalnız günceller; gösterilmiş olan bir daha gösterilmez.
     * Zamanı geçmiş ama bugüne ait olan (ör. 09:00'dan sonra eklenen) bir kez hemen gösterilir. Telefonun kendi notundan
     * kurduğu aynı iş (aynı metin, yakın zaman) ikinci kez çalmaz. Kurulan sayısını döndürür.
     */
    static int esitle(Context c, JSONArray gelen) throws Exception {
        SharedPreferences p = c.getSharedPreferences(DEPO, Context.MODE_PRIVATE);
        JSONArray liste = new JSONArray(p.getString("liste", "[]"));
        JSONArray gosterilen = new JSONArray(p.getString("gosterilen", "[]"));
        long simdi = System.currentTimeMillis();
        JSONArray kalan = new JSONArray();
        Set<String> eski = new HashSet<>(), kurulan = new HashSet<>(), gorulen = new HashSet<>();
        for (int k = 0; k < liste.length(); k++) {
            JSONObject o = liste.getJSONObject(k);
            if (o.getString("id").startsWith(ONEK_PC)) eski.add(o.getString("id"));
            else kalan.put(o);
        }
        for (int k = 0; k < gosterilen.length(); k++) gorulen.add(gosterilen.getJSONObject(k).getString("id"));
        for (int k = 0; k < gelen.length(); k++) {
            JSONObject o = gelen.getJSONObject(k);
            String id = ONEK_PC + o.getString("id");
            long zaman = o.getLong("zamanMs");
            String baslik = o.getString("baslik");
            if (gorulen.contains(id) || kurulan.contains(id) || telefondaVar(kalan, gosterilen, baslik, zaman)) continue;
            long kurulacak = zaman;
            if (zaman <= simdi) {
                if (!o.optBoolean("bugun")) continue;
                kurulacak = simdi + 5000;
            }
            kalan.put(new JSONObject().put("id", id).put("zaman", kurulacak).put("baslik", baslik).put("metin", o.getString("metin")));
            alarm(c, id, kurulacak, baslik, o.getString("metin"));
            kurulan.add(id);
        }
        for (String id : eski) if (!kurulan.contains(id)) iptal(c, id);
        p.edit().putString("liste", kalan.toString()).apply();
        return kurulan.size();
    }

    /** Telefonun kendi kurduğu (ya da gösterdiği) hatırlatıcılarda aynı iş var mı: aynı metin, 20 saatten yakın zaman. */
    private static boolean telefondaVar(JSONArray kalan, JSONArray gosterilen, String baslik, long zaman) throws Exception {
        for (JSONArray a : new JSONArray[]{kalan, gosterilen}) {
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                if (!o.getString("id").startsWith(ONEK_PC) && baslik.trim().equalsIgnoreCase(o.optString("baslik", "").trim())
                        && Math.abs(o.optLong("zaman") - zaman) < 20 * SAAT) return true;
            }
        }
        return false;
    }

    /** Gösterilenler üç gün hatırlanır: aynı hatırlatıcı bir sonraki eşitlemede yeniden çalmasın. */
    private static void gosterildi(Context c, String id, String baslik, long zaman) {
        if (id == null) return;
        try {
            SharedPreferences p = c.getSharedPreferences(DEPO, Context.MODE_PRIVATE);
            JSONArray eski = new JSONArray(p.getString("gosterilen", "[]"));
            JSONArray yeni = new JSONArray();
            long simdi = System.currentTimeMillis();
            for (int k = 0; k < eski.length(); k++) if (eski.getJSONObject(k).optLong("an") > simdi - 72 * SAAT) yeni.put(eski.get(k));
            yeni.put(new JSONObject().put("id", id).put("baslik", baslik != null ? baslik : "").put("zaman", zaman).put("an", simdi));
            p.edit().putString("gosterilen", yeni.toString()).apply();
        } catch (Exception ignored) { }
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
