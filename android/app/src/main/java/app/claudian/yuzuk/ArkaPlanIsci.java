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
    /** 0.11.8: aynı anda tek teslim turu (WorkManager ya da TeslimServisi). KILIT yalnız depo okuma/yazmayı korur;
     *  ağ beklenirken tutulmaz, uygulamanın köprü çağrıları turun bitmesini beklemez. */
    static final java.util.concurrent.locks.ReentrantLock CALISMA = new java.util.concurrent.locks.ReentrantLock();
    /** Şu an yüklenen ses dosyaları (uygulama ya da arka plan): aynı kayıt iki kez gönderilmez. */
    static final Set<String> YUKLENEN = Collections.synchronizedSet(new HashSet<>());
    static volatile boolean onPlanda;
    /** Son turda kayıt gönderilemedi (bilgisayar kapalı/pilde, bağlantı yok): servis daha seyrek dener. */
    static volatile boolean engel;
    static final String DEPO = "yuzuk_arka_plan";
    static SharedPreferences depo(Context c) { return c.getSharedPreferences(DEPO, Context.MODE_PRIVATE); }
    public ArkaPlanIsci(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    interface Durum { void yaz(String metin); }

    static void kur(Context c, JSONObject ayar) throws Exception {
        synchronized (KILIT) {
            String eski = depo(c).getString("ayar", "{}");
            JSONObject e = new JSONObject(eski);
            SharedPreferences.Editor yaz = depo(c).edit().putString("ayar", ayar.toString());
            boolean hesapDegisti = !e.optString("anahtar").equals(ayar.optString("anahtar"))
                    || !e.optString("sunucu").equals(ayar.optString("sunucu"))
                    || !e.optString("vault").equals(ayar.optString("vault"));
            if (hesapDegisti) yaz.remove("teslimler").remove("yuklemeler").remove("esitleme_izi").remove("esitleme_gerek");
            else {
                // Uygulama arka planda yüklenen kaydı kendi listesine işledikten sonra (artık "gönderilecek" değil)
                // yükleme makbuzu düşer; işlenmeden düşerse aynı kayıt ikinci kez yüklenebilirdi.
                JSONObject y = new JSONObject(depo(c).getString("yuklemeler", "{}")), kalan = new JSONObject();
                Set<String> bekleyen = new HashSet<>();
                JSONArray g = ayar.optJSONArray("gonderilecek");
                for (int n = 0; g != null && n < g.length(); n++) bekleyen.add(g.getJSONObject(n).optString("id"));
                for (Iterator<String> it = y.keys(); it.hasNext();) { String k = it.next(); if (bekleyen.contains(k)) kalan.put(k, y.get(k)); }
                yaz.putString("yuklemeler", kalan.toString());
            }
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

    static void yuklemeKaydet(Context c, String yerelId, String sunucuId) throws Exception {
        if (!sunucuId.matches("[0-9a-f]{32}")) return;
        synchronized (KILIT) {
            JSONObject y = new JSONObject(depo(c).getString("yuklemeler", "{}"));
            y.put(yerelId, sunucuId);
            if (!depo(c).edit().putString("yuklemeler", y.toString()).commit()) throw new IOException("yükleme makbuzu yazılamadı");
        }
    }

    /** Uygulamanın listesinde arka planda iş bekliyor mu (gönderilecek kayıt ya da sonucu beklenen iş). */
    static boolean bekleyenVar(Context c) {
        try {
            JSONObject a = new JSONObject(depo(c).getString("ayar", "{}"));
            if (a.optString("anahtar").isEmpty() || a.optString("vault").isEmpty()) return false;
            JSONArray g = a.optJSONArray("gonderilecek"), i = a.optJSONArray("isler");
            return (g != null && g.length() > 0) || (i != null && i.length() > 0);
        } catch (Exception e) { return false; }
    }

    @NonNull @Override public Result doWork() {
        Context c = getApplicationContext();
        if (onPlanda || isStopped()) return Result.success();
        if (!CALISMA.tryLock()) return Result.success(); // TeslimServisi zaten çalışıyor
        try {
            tur(c, android.os.SystemClock.elapsedRealtime() + 7 * 60_000L, () -> isStopped() || onPlanda, m -> { });
            return Result.success();
        } catch (Exception e) { return Result.retry(); }
        finally { CALISMA.unlock(); }
    }

    /** Bir teslim turu (0.11.8). Telefonda bekleyen kayıtları buluta yükler, sonucu hazır olanları Obsidian'a yazar,
     *  hatırlatıcıları çeker. Döner: hâlâ bekleyen iş sayısı; ayar yoksa -1. Çağıran CALISMA kilidini tutar. */
    static int tur(Context c, long son, java.util.function.BooleanSupplier dur, Durum durum) throws Exception {
        JSONObject a, teslimler, yuklemeler;
        synchronized (KILIT) {
            a = new JSONObject(depo(c).getString("ayar", "{}"));
            teslimler = new JSONObject(depo(c).getString("teslimler", "{}"));
            yuklemeler = new JSONObject(depo(c).getString("yuklemeler", "{}"));
        }
        if (a.optString("anahtar").isEmpty() || !a.optString("sunucu").startsWith("https://")) return -1;
        YuzukMesajServisi.kaydet(c); // push anahtarı ilk denemede gidemediyse burada yeniden denenir
        int bekleyen = 0;
        for (Iterator<String> it = teslimler.keys(); it.hasNext() && !dur.getAsBoolean() && android.os.SystemClock.elapsedRealtime() < son;) {
            try { istek(a, "/v1/oturum/" + it.next(), "DELETE"); } catch (Exception ignored) { }
        }
        // 1) Telefonda bekleyen kayıtlar: uygulama kapalıyken de gönderilir (06.10.2026: kayıt durdurulup uygulamadan
        //    çıkılınca bilgisayar açık olduğu hâlde uygulama yeniden açılana kadar hiçbir şey gitmiyordu).
        JSONArray gon = a.optJSONArray("gonderilecek");
        engel = false;
        // Büyük kayıt akıtılmadan önce bulut sorulur (D1'den, düğüme gitmeden): bilgisayar kapalıyken ya da pildeyken
        // 40 MB her turda baştan yüklenmez. 401/403 kalıcıdır (eşleşme iptal): tur kaydı bekletmez, servis kapanır.
        int yuklenecek = 0;
        for (int n = 0; gon != null && n < gon.length(); n++) if (!yuklemeler.has(gon.getJSONObject(n).optString("id"))) yuklenecek++;
        boolean gonder = true;
        if (yuklenecek > 0) {
            int k = kabulKodu(a);
            if (k == 401 || k == 403) gonder = false;
            else if (k != 200 && k != 404) { gonder = false; engel = true; bekleyen += yuklenecek; durum.yaz("Bilgisayar hazır değil; kayıt telefonda bekliyor"); }
        }
        for (int n = 0; gonder && gon != null && n < gon.length(); n++) {
            JSONObject g = gon.getJSONObject(n);
            String lid = g.optString("id");
            if (lid.isEmpty() || yuklemeler.has(lid)) continue;
            File f = new File(g.optString("yol"));
            if (!f.isFile()) continue;
            if (dur.getAsBoolean() || android.os.SystemClock.elapsedRealtime() >= son) { bekleyen++; continue; }
            String kanonik = f.getAbsolutePath();
            // 0.11.9: turun başındaki görüntü eskimiş olabilir — uygulama bu arada yüklemeyi bitirmiş (makbuz yazılmış ya da
            // kayıt listesinden "gönderilecek" çıkmış) olabilir. Karar taze depodan ve YUKLENEN'e eklemeyle aynı kilitte
            // verilir; 09.10 emülatör testinde aynı kayıt iki kez yüklenmişti.
            synchronized (KILIT) {
                JSONObject tazeY = new JSONObject(depo(c).getString("yuklemeler", "{}"));
                boolean halaBekliyor = false;
                JSONArray tazeG = new JSONObject(depo(c).getString("ayar", "{}")).optJSONArray("gonderilecek");
                for (int m = 0; tazeG != null && m < tazeG.length(); m++) if (lid.equals(tazeG.getJSONObject(m).optString("id"))) halaBekliyor = true;
                if (tazeY.has(lid)) { yuklemeler.put(lid, tazeY.getString(lid)); continue; }
                if (!halaBekliyor) continue;
                if (!YUKLENEN.add(kanonik)) { bekleyen++; continue; } // uygulama şu an gönderiyor
            }
            try {
                durum.yaz("Kayıt buluta gönderiliyor");
                JSONObject alanlar = new JSONObject().put("baslangic", g.optString("baslangic")).put("yazici", a.optString("yazici", "claude"));
                if (!g.optString("baslik").isEmpty()) alanlar.put("baslik", g.optString("baslik"));
                String[] r = YuzukEklentisi.akisYukle(f, a.getString("sunucu").replaceAll("/+$", "") + "/v1/oturum", a.getString("anahtar"), alanlar);
                if ("200".equals(r[0])) {
                    String sid = new JSONObject(r[1]).optString("id");
                    yuklemeKaydet(c, lid, sid);
                    yuklemeler.put(lid, sid);
                } else if (!"413".equals(r[0]) && !"415".equals(r[0]) && !"401".equals(r[0]) && !"403".equals(r[0])) {
                    bekleyen++; engel = true; // 413/415/401/403: tekrar düzeltmez, uygulama gösterir
                }
            } catch (Exception e) {
                bekleyen++; engel = true; // bağlantı yok ya da bilgisayar kapalı: ses telefonda kalır
            } finally {
                YUKLENEN.remove(kanonik);
            }
        }
        // 2) Bulutta kabul edilmiş işler: uygulamanın gönderdikleri ve az önce arka planda gönderilenler.
        List<JSONObject> takip = new ArrayList<>();
        JSONArray jobs = a.optJSONArray("isler");
        for (int n = 0; jobs != null && n < jobs.length(); n++) takip.add(jobs.getJSONObject(n));
        for (int n = 0; gon != null && n < gon.length(); n++) {
            JSONObject g = gon.getJSONObject(n);
            if (yuklemeler.has(g.optString("id"))) takip.add(new JSONObject(g.toString()).put("sunucuId", yuklemeler.getString(g.optString("id"))));
        }
        for (JSONObject o : takip) {
            if (dur.getAsBoolean() || android.os.SystemClock.elapsedRealtime() >= son) { bekleyen++; continue; }
            {
                {
                    String id = o.optString("sunucuId");
                    if (!id.matches("[0-9a-f]{32}")) continue;
                    // Kalıcı makbuz varsa not yeniden yazılmaz; DELETE bir önceki çalışmada kesilmiş olabilir.
                    if (teslimler.has(id)) { try { istek(a, "/v1/oturum/" + id, "DELETE"); } catch (Exception ignored) { } continue; }
                    try {
                        JSONObject j = new JSONObject(istek(a, "/v1/oturum/" + id, "GET"));
                        String d = j.optString("durum");
                        if ("hata".equals(d)) continue; // uygulama açılınca gösterir ve kendiliğinden bir kez yeniden dener
                        if (!"hazir".equals(d) || !j.has("sonuc")) {
                            bekleyen++;
                            durum.yaz("not yaziliyor".equals(j.optString("asama")) ? "Not yazılıyor" : "Kayıt bilgisayarda işleniyor");
                            continue;
                        }
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
                        synchronized (KILIT) {
                            JSONObject t = new JSONObject(depo(c).getString("teslimler", "{}"));
                            t.put(id, makbuz);
                            if (!depo(c).edit().putString("teslimler", t.toString()).commit()) throw new IOException("makbuz yazılamadı");
                        }
                        sesSil(c, o.optString("yol"));
                        try { istek(a, "/v1/oturum/" + id, "DELETE"); } catch (Exception ignored) { }
                        bildir(c, id, makbuz.optString("baslik"), yol.isEmpty() ? "Not alınacak konuşma bulunamadı." : "Obsidian'a yazıldı.");
                        if (!yol.isEmpty()) {
                            // 0.11.9: telefondaki not bilgisayarınkiyle aynı; izi işlenir, ardından vault eşitlenir.
                            synchronized (KILIT) {
                                JSONObject iz = VaultEsitleme.izOku(c);
                                iz.put(yol, hash);
                                depo(c).edit().putString("esitleme_izi", iz.toString()).putBoolean("esitleme_gerek", true).commit();
                            }
                        }
                    } catch (Exception e) {
                        bekleyen++; /* Sonuç ve telefondaki ses kalır; bir sonraki tur yeniden dener. */
                    }
                }
            }
        }
        try { hatirlaticilariCek(c, a); } catch (Exception ignored) { }
        // 0.11.9 (09.10.2026, Boran): not arka planda yazıldıysa bilgisayarın aynı kayıtla güncellediği notlar da
        // (Kontrol Paneli, Hatırlatıcılar…) Eşitle'ye basmadan telefona gelir. Uygulama öne gelirse yarıda bırakılır;
        // bayrak kalır ve uygulama açılışta eşitler.
        if (VaultEsitleme.gerekliMi(c) && !dur.getAsBoolean() && !a.optString("vault").isEmpty()) {
            try { durum.yaz("Obsidian eşitleniyor"); VaultEsitleme.esitle(c, a, dur); } catch (Exception ignored) { }
        }
        return bekleyen;
    }

    /** GET /v1/kabul durum kodu; ağ yoksa -1. */
    static int kabulKodu(JSONObject a) {
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(a.getString("sunucu").replaceAll("/+$", "") + "/v1/kabul").openConnection();
            h.setConnectTimeout(10_000); h.setReadTimeout(20_000); h.setInstanceFollowRedirects(false);
            h.setRequestProperty("Authorization", "Bearer " + a.getString("anahtar"));
            h.setRequestProperty("X-Yuzuk-Surum", BuildConfig.VERSION_NAME);
            return h.getResponseCode();
        } catch (Exception e) {
            return -1;
        } finally { if (h != null) h.disconnect(); }
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
                md.append(" 📅 ").append(h.getString("tarih"));
                JSONArray anlar = h.optJSONArray("hatirlat");
                if (anlar != null && anlar.length() > 0) {
                    md.append(" ⏰ ");
                    for (int j = 0; j < anlar.length(); j++) md.append(j > 0 ? ", " : "").append(anlar.getString(j));
                }
                md.append("\n");
            }
        }
        return new JSONObject().put("yol", alt + "/" + ad).put("icerik", md.toString());
    }

    static void kendiHatirlaticilari(Context c, JSONObject o, JSONObject s) throws Exception {
        JSONArray liste = s.optJSONArray("hatirlaticilar");
        if (liste == null) return;
        // 0.12.0: sabit "yarım saat önce" yok. Kalem kendi vaktinde, ayrıca yazıcının seçtiği her "hatirlat" anında çalar.
        long simdi = System.currentTimeMillis();
        for (int k = 0; k < liste.length(); k++) {
            JSONObject h = liste.getJSONObject(k);
            String saat = saat(h);
            long vakit = tarih(h.getString("tarih"), saat.isEmpty() ? "09:00" : saat);
            String gun = h.getString("tarih") + (saat.isEmpty() ? "" : " " + saat);
            if (vakit > simdi) HatirlaticiAlici.kur(c, o.getString("id") + "-" + k, vakit, h.getString("metin"), gun);
            JSONArray anlar = h.optJSONArray("hatirlat");
            for (int j = 0; anlar != null && j < anlar.length(); j++) {
                String[] p = anlar.getString(j).split(" ");
                if (p.length != 2) continue;
                long an;
                try { an = tarih(p[0], p[1]); } catch (Exception e) { continue; }
                if (an > simdi && an < vakit) HatirlaticiAlici.kur(c, o.getString("id") + "-" + k + "-" + j, an, h.getString("metin"), gun);
            }
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
        PendingIntent ac = BildirimOku.niyet(c, "hazir|" + id, "Not hazır · " + baslik, metin, System.currentTimeMillis());
        try { nm.notify(id.hashCode(), new Notification.Builder(c, "not").setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Not hazır · " + baslik).setContentText(metin)
                .setStyle(new Notification.BigTextStyle().bigText(metin)).setContentIntent(ac).setAutoCancel(true).build()); }
        catch (SecurityException ignored) { /* Bildirim izni yoksa yazılmış not ve makbuz kalır. */ }
    }
}
