package app.claudian.yuzuk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** WebView uyurken kabul edilmiş işleri teslim alır ve hatırlatıcıları kurar.
 * Android 15 dakikalık işi pil durumuna göre geciktirebilir; kesin saat vaadi yoktur.
 * Notlar yalnız kullanıcının seçtiği SAF vault'una, küçük teslim makbuzu uygulamanın özel deposuna yazılır. */
public class ArkaPlanIsci extends Worker {
    static final Object KILIT = new Object();
    static volatile boolean onPlanda;
    static final String DEPO = "yuzuk_arka_plan";
    static SharedPreferences depo(Context c) { return c.getSharedPreferences(DEPO, Context.MODE_PRIVATE); }
    public ArkaPlanIsci(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    static void kur(Context c, JSONObject ayar) throws Exception {
        synchronized (KILIT) {
            String eski = depo(c).getString("ayar", "{}");
            JSONObject e = new JSONObject(eski);
            SharedPreferences.Editor yaz = depo(c).edit().putString("ayar", ayar.toString());
            if (!e.optString("anahtar").equals(ayar.optString("anahtar"))
                    || !e.optString("sunucu").equals(ayar.optString("sunucu"))
                    || !e.optString("vault").equals(ayar.optString("vault"))) yaz.remove("teslimler");
            if (!yaz.commit()) throw new IOException("arka plan ayarı yazılamadı");
        }
        WorkManager wm = WorkManager.getInstance(c);
        if (ayar.optString("anahtar").isEmpty() || ayar.optString("vault").isEmpty()) {
            wm.cancelUniqueWork("yuzuk-esitle");
            wm.cancelUniqueWork("yuzuk-hemen");
            return;
        }
        Constraints kosul = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        wm.enqueueUniquePeriodicWork("yuzuk-esitle", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(ArkaPlanIsci.class, 15, TimeUnit.MINUTES).setConstraints(kosul).build());
    }

    static void hemen(Context c) {
        Constraints kosul = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager.getInstance(c).enqueueUniqueWork("yuzuk-hemen", ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(ArkaPlanIsci.class).setInitialDelay(20, TimeUnit.SECONDS).setConstraints(kosul).build());
    }

    @NonNull @Override public Result doWork() {
        Context c = getApplicationContext();
        long son = android.os.SystemClock.elapsedRealtime() + 7 * 60_000L;
        synchronized (KILIT) {
            if (onPlanda || isStopped()) return Result.success();
            try {
                JSONObject a = new JSONObject(depo(c).getString("ayar", "{}"));
                if (a.optString("anahtar").isEmpty() || !a.optString("sunucu").startsWith("https://")) return Result.success();
                JSONArray jobs = a.optJSONArray("isler");
                JSONObject teslimler = new JSONObject(depo(c).getString("teslimler", "{}"));
                for (Iterator<String> it = teslimler.keys(); it.hasNext() && !isStopped() && !onPlanda && android.os.SystemClock.elapsedRealtime() < son;) {
                    try { istek(a, "/v1/oturum/" + it.next(), "DELETE"); } catch (Exception ignored) { }
                }
                if (jobs != null) for (int n = 0; n < jobs.length() && !isStopped() && !onPlanda && android.os.SystemClock.elapsedRealtime() < son; n++) {
                    JSONObject o = jobs.getJSONObject(n);
                    String id = o.optString("sunucuId");
                    if (!id.matches("[0-9a-f]{32}")) continue;
                    // Kalıcı makbuz varsa not yeniden yazılmaz; DELETE bir önceki çalışmada kesilmiş olabilir.
                    if (teslimler.has(id)) { try { istek(a, "/v1/oturum/" + id, "DELETE"); } catch (Exception ignored) { } continue; }
                    try {
                        JSONObject j = new JSONObject(istek(a, "/v1/oturum/" + id, "GET"));
                        if (!"hazir".equals(j.optString("durum")) || !j.has("sonuc")) continue;
                        JSONObject s = j.getJSONObject("sonuc");
                        String md = s.optString("not_md").trim();
                        String yol = "", hash = "";
                        if (!md.isEmpty()) {
                            yol = s.optString("pc_notu");
                            String icerik;
                            if (!yol.isEmpty()) {
                                if (!yol.matches("Yüzük/[^/]+\\.md")) throw new IOException("geçersiz not yolu");
                                // Aynı dosya, aynı içerik: bilgisayar/telefon eşitlemesi kopya üretmez.
                                icerik = istek(a, "/v1/vault/dosya?surum=3&yol=" + URLEncoder.encode(yol, "UTF-8"), "GET");
                            } else {
                                JSONObject not = notMetni(a, o, s);
                                yol = not.getString("yol"); icerik = not.getString("icerik");
                            }
                            yaz(c, a.getString("vault"), yol, icerik);
                            hash = sha(icerik);
                            // Bilgisayarsız notun tarihli işleri kendi kaynağından kurulur.
                            if (s.optString("pc_notu").isEmpty()) kendiHatirlaticilari(c, o, s);
                        }
                        JSONObject makbuz = new JSONObject().put("id", o.getString("id")).put("sunucuId", id)
                                .put("yol", yol).put("hash", hash).put("baslik", s.optString("baslik", o.optString("baslik", "Not")))
                                .put("mahrem", s.optInt("mahrem", 0)).put("yazici", s.optString("yazici"));
                        teslimler.put(id, makbuz);
                        // Ses ya da sunucudaki sonuç ancak başarılı SAF yazısı ve kalıcı makbuzdan sonra silinir.
                        if (!depo(c).edit().putString("teslimler", teslimler.toString()).commit()) throw new IOException("makbuz yazılamadı");
                        sesSil(c, o.optString("yol"));
                        try { istek(a, "/v1/oturum/" + id, "DELETE"); } catch (Exception ignored) { }
                        bildir(c, id, makbuz.optString("baslik"), yol.isEmpty() ? "Not alınacak konuşma bulunamadı." : "Obsidian'a yazıldı.");
                    } catch (Exception ignored) { /* Sonuç ve telefondaki ses kalır; bir sonraki iş yeniden dener. */ }
                }
                hatirlaticilariCek(c, a);
                return Result.success();
            } catch (Exception e) { return Result.retry(); }
        }
    }

    static String istek(JSONObject a, String yol, String method) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(a.getString("sunucu").replaceAll("/+$", "") + yol).openConnection();
        h.setConnectTimeout(10_000); h.setReadTimeout(30_000); h.setInstanceFollowRedirects(false);
        h.setRequestMethod(method); h.setRequestProperty("Authorization", "Bearer " + a.getString("anahtar"));
        h.setRequestProperty("X-Yuzuk-Surum", BuildConfig.VERSION_NAME);
        try {
            if (h.getResponseCode() != 200) throw new IOException("HTTP " + h.getResponseCode());
            try (InputStream in = h.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) { if (out.size() + n > 2_000_000) throw new IOException("yanıt büyük"); out.write(b, 0, n); }
                return out.toString("UTF-8");
            }
        } finally { h.disconnect(); }
    }

    static void yaz(Context c, String agac, String yol, String metin) throws Exception {
        String[] parts = yol.split("/", -1);
        for (String p : parts) if (p.isEmpty() || p.startsWith(".") || p.contains("\\")) throw new IOException("geçersiz yol");
        if (!yol.endsWith(".md")) throw new IOException("yalnız Markdown");
        DocumentFile d = DocumentFile.fromTreeUri(c, Uri.parse(agac));
        if (d == null || !d.canWrite() || d.findFile(".obsidian") == null) throw new IOException("vault erişimi yok");
        for (int k = 0; k < parts.length - 1; k++) {
            DocumentFile alt = d.findFile(parts[k]);
            d = alt == null ? d.createDirectory(parts[k]) : alt;
            if (d == null || !d.isDirectory()) throw new IOException("klasör açılamadı");
        }
        String ad = parts[parts.length - 1];
        DocumentFile f = d.findFile(ad);
        if (f == null) f = d.createFile("application/octet-stream", ad);
        if (f == null) throw new IOException("dosya açılamadı");
        try (OutputStream out = c.getContentResolver().openOutputStream(f.getUri(), "wt")) {
            if (out == null) throw new IOException("yazma erişimi yok");
            out.write(metin.getBytes(StandardCharsets.UTF_8));
        }
        // Bazı SAF sağlayıcıları kısmi yazıyı sessizce kabul edebilir; makbuzdan önce geri okunur.
        try (InputStream in = c.getContentResolver().openInputStream(f.getUri()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("yazılan not okunamadı");
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) != -1) {
                if (out.size() + n > 2_000_000) throw new IOException("yazılan not büyük");
                out.write(b, 0, n);
            }
            if (!sha(out.toString("UTF-8")).equals(sha(metin))) throw new IOException("not yazımı doğrulanamadı");
        }
    }

    static JSONObject notMetni(JSONObject a, JSONObject o, JSONObject s) throws Exception {
        Date bas = Date.from(java.time.Instant.parse(o.getString("baslangic")));
        String tarih = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(bas);
        String saat = new SimpleDateFormat("HH:mm", Locale.ROOT).format(bas);
        String baslik = s.optString("baslik", o.optString("baslik", "Not")).trim();
        String ad = tarih + " " + saat.replace(":", "") + " " + baslik.replaceAll("[\\\\/:*?\"<>|#^\\[\\]]", " ").replaceAll("\\s+", " ").trim() + ".md";
        String alt = a.optString("altKlasor", "Yüzük");
        StringBuilder md = new StringBuilder("---\ntags: [yüzük]\ntür: log\ngüncellenme: " + tarih + "\nkaynak: ses\ntarih: " + tarih + " " + saat + "\n---\n\n# " + baslik + "\n\n" + s.getString("not_md").trim() + "\n");
        JSONArray hats = s.optJSONArray("hatirlaticilar");
        if (hats != null && hats.length() > 0) {
            md.append("\n## Hatırlatıcılar\n");
            for (int k = 0; k < hats.length(); k++) {
                JSONObject h = hats.getJSONObject(k);
                md.append("- [ ] ").append(h.getString("metin"));
                if (!saat(h).isEmpty()) md.append(" · saat ").append(saat(h));
                md.append(" 📅 ").append(h.getString("tarih")).append("\n");
            }
        }
        return new JSONObject().put("yol", alt + "/" + ad).put("icerik", md.toString());
    }

    static void kendiHatirlaticilari(Context c, JSONObject o, JSONObject s) throws Exception {
        JSONArray liste = s.optJSONArray("hatirlaticilar");
        if (liste == null) return;
        for (int k = 0; k < liste.length(); k++) {
            JSONObject h = liste.getJSONObject(k);
            String saat = saat(h);
            long zaman = tarih(h.getString("tarih"), saat.isEmpty() ? "09:00" : saat);
            if (!saat.isEmpty()) zaman -= 30 * 60_000L;
            if (zaman > System.currentTimeMillis()) HatirlaticiAlici.kur(c, o.getString("id") + "-" + k, zaman, h.getString("metin"), h.getString("tarih"));
        }
    }

    static String saat(JSONObject h) { return h.isNull("saat") ? "" : h.optString("saat"); }

    static long tarih(String tarih, String saat) throws Exception {
        if (!tarih.matches("\\d{4}-\\d{2}-\\d{2}") || !saat.matches("([01]\\d|2[0-3]):[0-5]\\d")) throw new IOException("geçersiz tarih");
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT); fmt.setLenient(false);
        return fmt.parse(tarih + " " + saat).getTime();
    }

    static void hatirlaticilariCek(Context c, JSONObject a) throws Exception {
        // İnternet yokken basılmış bildirim düğmeleri uygulamanın açılmasını beklemez.
        HatirlaticiAlici.bekleyenleriGonder(c);
        JSONObject j = new JSONObject(istek(a, "/v1/hatirlaticilar", "GET"));
        JSONArray gelen = j.getJSONArray("hatirlaticilar"), liste = new JSONArray();
        String bugun = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        for (int k = 0; k < gelen.length(); k++) {
            try {
                JSONObject h = gelen.getJSONObject(k);
                if (!h.optString("id").matches("[0-9a-f]{16}")) continue;
                String t = h.getString("tarih"), saat = h.optString("saat");
                if (saat.isEmpty() || saat.equals("null")) saat = "09:00";
                String metin = h.getString("metin").trim(); if (metin.isEmpty()) continue;
                liste.put(new JSONObject().put("id", h.getString("id")).put("zamanMs", tarih(t, saat)).put("bugun", t.equals(bugun))
                        .put("baslik", metin).put("metin", t + " " + saat));
            } catch (Exception ignored) { }
        }
        HatirlaticiAlici.esitle(c, liste);
    }

    static String sha(String s) throws Exception {
        byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder h = new StringBuilder(); for (byte v : b) h.append(String.format("%02x", v)); return h.toString();
    }
    static void sesSil(Context c, String yol) throws Exception {
        File f = new File(yol).getCanonicalFile();
        if (f.getPath().startsWith(c.getFilesDir().getCanonicalPath() + File.separator)
                || f.getPath().startsWith(c.getCacheDir().getCanonicalPath() + File.separator)) f.delete();
    }
    static void bildir(Context c, String id, String baslik, String metin) {
        NotificationManager nm = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("not", "Hazır notlar", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent ac = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        try { nm.notify(id.hashCode(), new Notification.Builder(c, "not").setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Not hazır · " + baslik).setContentText(metin).setContentIntent(ac).setAutoCancel(true).build()); }
        catch (SecurityException ignored) { /* Bildirim izni yoksa yazılmış not ve makbuz kalır. */ }
    }
}
