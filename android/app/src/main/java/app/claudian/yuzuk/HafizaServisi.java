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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 0.13.0: "AI'lara doğrudan bağlan" açıkken telefonu relay.claudian.app'te çevrimiçi tutan ön plan servisi.
 * WebView arka planda uyuduğu için relay döngüsü ve cevaplar burada, Java'da çalışır (DogrudanCihaz, DogrudanHafiza).
 * Görünür bildirim Android'in şartıdır; kullanıcı bağlantıyı Yüzük → Ayarlar'dan kapatınca servis durur.
 */
public class HafizaServisi extends Service {
    static final String KANAL = "hafiza";
    static final int BILDIRIM = 5;
    static volatile boolean calisiyor;
    static volatile String baglanti = "kapali";
    static volatile String sonHata = null;
    private volatile boolean durdur;
    private Thread is;

    static void baslat(Context c) {
        try {
            Intent i = new Intent(c, HafizaServisi.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception e) {
            sonHata = "Arka plan servisi açılamadı: " + e.getMessage();
        }
    }

    static void durdur(Context c) { c.stopService(new Intent(c, HafizaServisi.class)); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Notification n = bildirim(this, "Claudian hafızası AI'lara açık", "Claude ve ChatGPT bu telefondaki notlarına ulaşabilir.");
            if (Build.VERSION.SDK_INT >= 29) startForeground(BILDIRIM, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(BILDIRIM, n);
        } catch (Exception e) {
            sonHata = "Arka plan servisi açılamadı: " + e.getMessage();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!DogrudanCihaz.durum(this).optBoolean("acik")) { stopSelf(); return START_NOT_STICKY; }
        if (calisiyor) return START_STICKY;
        calisiyor = true;
        durdur = false;
        is = new Thread(this::dongu, "hafiza-relay");
        is.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        durdur = true;
        RelaySoketi s = soket;
        if (s != null) s.close();
        if (is != null) is.interrupt();
        calisiyor = false;
        baglanti = "kapali";
        super.onDestroy();
    }

    // Android 15: dataSync servisinin günlük süresi dolarsa sistem bunu çağırır.
    @Override
    public void onTimeout(int startId, int fgsType) {
        sonHata = "Android arka plan süresi doldu; Yüzük'ü açınca bağlantı yeniden başlar.";
        stopSelf();
    }

    /** 0.15.2: relay'in soket yolunu desteklemediği an; on dakika uzun yoklamayla devam edilir, sonra yeniden denenir. */
    private long soketsiz = 0L;
    private volatile RelaySoketi soket;

    /**
     * 0.15.2 (22.11.2026): relay'e tek WebSocket; relay boştayken uyur, günlük kotası dolmaz. AI'ın isteği soketten gelir,
     * cevap soketten döner. Sessizlikte 25 sn'de bir "ping" gider (Cloudflare kendisi cevaplar); 75 sn hiçbir şey gelmezse
     * bağlantı yeniden kurulur. true: soket hazır olduktan sonra kapandı (yeniden bağlanılır); false: soket desteklenmiyor.
     */
    private boolean soketleCalis(String sir, String taban) throws Exception {
        RelaySoketi ws;
        try { ws = RelaySoketi.baglan(taban.replaceFirst("^http", "ws") + "/__device/ws", 25_000); }
        catch (java.io.IOException e) { if (String.valueOf(e.getMessage()).startsWith("desteklenmiyor")) return false; throw e; }
        soket = ws;
        try {
            ws.gonder(new JSONObject().put("tur", "giris").put("anahtar", sir).toString());
            long sonGelen = System.currentTimeMillis();
            boolean hazir = false;
            while (!durdur) {
                String m;
                try { m = ws.oku(); }
                catch (java.net.SocketTimeoutException t) {
                    if (System.currentTimeMillis() - sonGelen > 75_000) return hazir;
                    ws.gonder("ping");
                    continue;
                }
                if (m == null) return hazir;
                sonGelen = System.currentTimeMillis();
                if ("pong".equals(m)) continue;
                JSONObject j;
                try { j = new JSONObject(m); } catch (Exception e) { continue; }
                if ("hazir".equals(j.optString("tur"))) {
                    hazir = true;
                    baglanti = "cevrimici";
                    sonHata = null;
                    continue;
                }
                if ("istek".equals(j.optString("tur"))) {
                    final JSONObject istek = j;
                    new Thread(() -> {
                        try {
                            JSONObject y = DogrudanCihaz.isle(this, istek);
                            y.put("tur", "cevap").put("id", istek.getString("id"));
                            ws.gonder(y.toString());
                        } catch (Exception ignored) { }
                    }, "hafiza-istek").start();
                }
            }
            return hazir;
        } finally {
            soket = null;
            ws.close();
        }
    }

    private void dongu() {
        int deneme = 0;
        while (!durdur) {
            try {
                JSONObject d = DogrudanCihaz.durum(this);
                if (!d.optBoolean("acik")) break;
                String sir = d.getString("sir"), taban = DogrudanCihaz.taban(d);
                if (soketsiz == 0L || System.currentTimeMillis() - soketsiz > 600_000L) {
                    soketsiz = 0L;
                    baglanti = "baglaniyor";
                    if (soketleCalis(sir, taban)) { deneme = 0; continue; }
                    if (durdur) break;
                    soketsiz = System.currentTimeMillis();
                }
                if (!"cevrimici".equals(baglanti)) {
                    baglanti = "baglaniyor";
                    istek(taban + "/__device/connect", sir, "{}", 15_000);
                }
                String cevap = istek(taban + "/__device/poll", sir, "{}", 40_000);
                baglanti = "cevrimici";
                sonHata = null;
                deneme = 0;
                JSONArray liste = new JSONObject(cevap).optJSONArray("requests");
                if (liste == null) continue;
                for (int i = 0; i < liste.length(); i++) {
                    JSONObject r = liste.getJSONObject(i);
                    JSONObject y = DogrudanCihaz.isle(this, r);
                    y.put("id", r.getString("id"));
                    try { istek(taban + "/__device/reply", sir, y.toString(), 15_000); } catch (Exception ignored) { }
                }
            } catch (Exception e) {
                if (durdur) break;
                baglanti = "cevrimdisi";
                sonHata = e.getMessage();
                long bekle = Math.min(3000L << Math.min(deneme++, 5), 60_000L);
                try { Thread.sleep((long) (bekle * (0.75 + Math.random() * 0.5))); } catch (InterruptedException x) { break; }
            }
        }
        calisiyor = false;
        baglanti = "kapali";
        stopSelf();
    }

    private static String istek(String adres, String sir, String govde, int sure) throws Exception {
        HttpURLConnection b = (HttpURLConnection) new URL(adres).openConnection();
        b.setRequestMethod("POST");
        b.setConnectTimeout(15_000);
        b.setReadTimeout(sure);
        b.setInstanceFollowRedirects(false);
        b.setDoOutput(true);
        b.setRequestProperty("Authorization", "Bearer " + sir);
        b.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = b.getOutputStream()) { o.write(govde.getBytes(StandardCharsets.UTF_8)); }
        int kod = b.getResponseCode();
        InputStream in = kod >= 400 ? b.getErrorStream() : b.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (in != null) try (InputStream x = in) { byte[] t = new byte[8192]; int n; while ((n = x.read(t)) > 0) out.write(t, 0, n); }
        b.disconnect();
        if (kod == 410) return "{}"; // cevap gecikti; relay isteği düşürmüş
        if (kod >= 300) throw new Exception("relay " + kod);
        return out.toString("UTF-8");
    }

    static Notification bildirim(Context c, String baslik, String metin) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(KANAL) == null)
            nm.createNotificationChannel(new NotificationChannel(KANAL, "AI bağlantısı", NotificationManager.IMPORTANCE_LOW));
        Intent ac = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        PendingIntent pi = PendingIntent.getActivity(c, 5, ac, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c, KANAL).setContentTitle(baslik).setContentText(metin)
            .setStyle(new Notification.BigTextStyle().bigText(metin)).setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pi).setOngoing(true).build();
    }

    /** Onay penceresi dışında gelen bağlantı isteği: kullanıcı Yüzük'ü açıp kodu onaylasın. */
    static void istekBildir(Context c, String ad, String kod) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("baglanti") == null)
                nm.createNotificationChannel(new NotificationChannel("baglanti", "AI bağlantı istekleri", NotificationManager.IMPORTANCE_HIGH));
            Intent ac = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            PendingIntent pi = PendingIntent.getActivity(c, 6, ac, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String metin = ad + " bağlanmak istiyor · kod " + kod;
            nm.notify(6, new Notification.Builder(c, "baglanti").setContentTitle("Yüzük'te onayla").setContentText(metin)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done).setContentIntent(pi).setAutoCancel(true).build());
        } catch (Exception ignored) { }
    }
}
