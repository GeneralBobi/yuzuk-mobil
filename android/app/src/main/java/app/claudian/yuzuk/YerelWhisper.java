package app.claudian.yuzuk;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Çevrimdışı yazıya dökme (27.09.2026). Bağlantı yokken ses parçası telefonda whisper.cpp ile metne çevrilir;
 * parça bellekten atılır, geriye yalnız metin kalır. Model bir kez indirilir (Wi-Fi önerilir) ve bütünlüğü
 * SHA-256 ile doğrulanır; doğrulanmayan dosya kullanılmaz.
 */
public final class YerelWhisper {
    /** 0.15.0 (22.11.2026, Boran): iki model. "small" doğruluk için (≈190 MB), "base" yavaş telefon için (≈60 MB).
     *  Hangisinin kullanılacağına ilk kullanımdaki hız testi karar verir; kullanıcı Ayarlar'dan değiştirebilir. */
    static final class Model {
        final String ad, dosya, sha256; final long boyut;
        Model(String ad, String dosya, String sha256, long boyut) { this.ad = ad; this.dosya = dosya; this.sha256 = sha256; this.boyut = boyut; }
        String adres() { return "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/" + dosya; }
    }
    static final Model KUCUK = new Model("base", "ggml-base-q5_1.bin", "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898", 59707625L);
    static final Model BUYUK = new Model("small", "ggml-small-q5_1.bin", "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb", 190085487L);
    /** 0.15.0 (Boran, 22.11): güçlü telefonlar için large-v3-turbo (q5_0, ≈574 MB). Türkçede small'dan belirgin biçimde iyi. */
    static final Model TURBO = new Model("turbo", "ggml-large-v3-turbo-q5_0.bin", "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2", 574041195L);

    static volatile long indirilen = 0L;
    static volatile boolean indiriliyor = false;
    static volatile String indirmeHatasi = null;
    static volatile String indirilenModel = null;

    private static boolean kutuphane = false;
    private static long ctx = 0L;
    private static String ctxModel = null;

    static native long yukle(String yol);
    static native void birak(long ctx);
    static native String coz(long ctx, float[] ornekler, String dil);

    private YerelWhisper() { }

    private static android.content.SharedPreferences depo(Context c) { return c.getSharedPreferences("model", Context.MODE_PRIVATE); }

    private static Boolean uyumluMu = null;

    /** Kütüphane armv8.2-a+fp16+dotprod için derlenir (CMakeLists). Bu komutları bilmeyen işlemcide (eski ARMv8.0 çekirdekler,
     *  çeviriyle çalışan emülatör) model yüklenince uygulama SIGILL ile kapanır. 0.15.0: önce /proc/cpuinfo'ya bakılır;
     *  destek yoksa yerel model hiç çalıştırılmaz, bulut ya da bilgisayar önerilir. */
    static synchronized boolean uyumlu() {
        if (uyumluMu != null) return uyumluMu;
        boolean dp = false, hp = false;
        try (java.io.BufferedReader b = new java.io.BufferedReader(new java.io.FileReader("/proc/cpuinfo"))) {
            String satir;
            while ((satir = b.readLine()) != null) {
                if (!satir.startsWith("Features")) continue;
                java.util.List<String> f = java.util.Arrays.asList(satir.substring(satir.indexOf(':') + 1).trim().split(" +"));
                dp = f.contains("asimddp");
                hp = f.contains("fphp") && f.contains("asimdhp");
                break;
            }
        } catch (Exception ignored) { }
        uyumluMu = dp && hp;
        return uyumluMu;
    }

    static Model model(String ad) { return "base".equals(ad) ? KUCUK : "turbo".equals(ad) ? TURBO : BUYUK; }

    /** Seçili model (varsayılan small; 0.14.0 kurulumları small indirmişti). */
    static Model secili(Context c) { return model(depo(c).getString("secili", "small")); }

    static void sec(Context c, String ad) {
        depo(c).edit().putString("secili", model(ad).ad).putBoolean("hizSuruyor", false).remove("hiz").apply();
        synchronized (YerelWhisper.class) { if (ctx != 0L) { birak(ctx); ctx = 0L; ctxModel = null; } }
    }

    static File dosya(Context c, Model m) {
        return new File(new File(c.getFilesDir(), "modeller"), m.dosya);
    }

    static File dosya(Context c) { return dosya(c, secili(c)); }

    static boolean hazir(Context c, Model m) {
        File f = dosya(c, m);
        return f.exists() && f.length() == m.boyut;
    }

    static boolean hazir(Context c) { return hazir(c, secili(c)); }

    /** Son hız testi: {model, oran, sure_ms, zaman} ya da null. oran = çözme süresi / ses süresi (1'den büyükse geride kalır). */
    static org.json.JSONObject hiz(Context c) {
        try {
            if (!uyumlu()) return new org.json.JSONObject().put("model", secili(c).ad).put("oran", 99).put("uyumsuz", true);
            // Test sırasında uygulama kapandıysa (yerel kod çöktü) bir daha otomatik denenmez; çok yavaş sayılır.
            if (depo(c).getBoolean("hizSuruyor", false) && !testte)
                return new org.json.JSONObject().put("model", secili(c).ad).put("oran", 99).put("cokme", true);
            String h = depo(c).getString("hiz", null);
            return h == null ? null : new org.json.JSONObject(h);
        } catch (Exception e) { return null; }
    }

    private static volatile boolean testte = false;

    /** 10 saniyelik deneme sesiyle seçili modelin hızını ölçer. Konuşma değil, kısık gürültülü bir ses dalgası: whisper'ın
     *  kodlayıcısı her parçada aynı işi yapar, ölçülen süre gerçek kayda yakındır. Sonuç saklanır. */
    static synchronized org.json.JSONObject hizTesti(Context c) throws Exception {
        if (!hazir(c)) throw new Exception("model hazır değil");
        if (!uyumlu()) throw new Exception("bu işlemci telefonda yazıya dökmeyi desteklemiyor");
        depo(c).edit().putBoolean("hizSuruyor", true).commit();
        testte = true;
        float[] o = new float[16000 * 10];
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < o.length; i++) {
            double t = i / 16000.0;
            o[i] = (float) (0.08 * Math.sin(2 * Math.PI * (180 + 40 * Math.sin(2 * Math.PI * 0.7 * t)) * t) * (0.6 + 0.4 * Math.sin(2 * Math.PI * 3 * t)) + 0.01 * r.nextGaussian());
        }
        metin(c, new float[16000], "tr"); // modeli belleğe yükler; yükleme süresi teste girmez
        long bas = System.nanoTime();
        String s = metin(c, o, "tr");
        long ms = (System.nanoTime() - bas) / 1_000_000L;
        if (s == null) { testte = false; depo(c).edit().putBoolean("hizSuruyor", false).commit(); throw new Exception("model çalıştırılamadı"); }
        org.json.JSONObject j = new org.json.JSONObject().put("model", secili(c).ad).put("sure_ms", ms)
                .put("oran", Math.round(ms / 100.0) / 100.0).put("zaman", System.currentTimeMillis());
        depo(c).edit().putString("hiz", j.toString()).putBoolean("hizSuruyor", false).commit();
        testte = false;
        return j;
    }

    /** Parçayı metne çevirir. Model yoksa ya da yüklenemezse null: çağıran parçayı bekletir. */
    static synchronized String metin(Context c, float[] ornekler, String dil) {
        if (!hazir(c) || !uyumlu()) return null;
        try {
            if (!kutuphane) {
                System.loadLibrary("yuzukwhisper");
                kutuphane = true;
            }
            String ad = secili(c).ad;
            if (ctx != 0L && !ad.equals(ctxModel)) { birak(ctx); ctx = 0L; }
            if (ctx == 0L) { ctx = yukle(dosya(c).getAbsolutePath()); ctxModel = ad; }
            if (ctx == 0L) return null;
            return coz(ctx, ornekler, dil);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Model indirme; kısmi dosya .part olarak kalır ve doğrulanınca yerine konur. */
    static void indir(Context c) { indir(c, secili(c)); }

    static void indir(Context c, Model m) {
        if (indiriliyor || hazir(c, m)) return;
        indirilenModel = m.ad;
        indiriliyor = true;
        indirmeHatasi = null;
        indirilen = 0L;
        new Thread(() -> {
            File hedef = dosya(c, m);
            File kismi = new File(hedef.getParentFile(), m.dosya + ".part");
            try {
                hedef.getParentFile().mkdirs();
                HttpURLConnection b = (HttpURLConnection) new URL(m.adres()).openConnection();
                b.setConnectTimeout(15000);
                b.setReadTimeout(60000);
                b.setInstanceFollowRedirects(true);
                if (b.getResponseCode() != 200) throw new Exception("indirme " + b.getResponseCode());
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream in = b.getInputStream(); FileOutputStream out = new FileOutputStream(kismi)) {
                    byte[] tampon = new byte[1 << 16];
                    int n;
                    while ((n = in.read(tampon)) > 0) {
                        out.write(tampon, 0, n);
                        md.update(tampon, 0, n);
                        indirilen += n;
                    }
                }
                StringBuilder s = new StringBuilder();
                for (byte x : md.digest()) s.append(String.format("%02x", x));
                if (!m.sha256.equals(s.toString()) || kismi.length() != m.boyut) {
                    kismi.delete();
                    throw new Exception("model doğrulanamadı; silindi");
                }
                if (!kismi.renameTo(hedef)) throw new Exception("model yerine konamadı");
            } catch (Exception e) {
                indirmeHatasi = e.getMessage() != null ? e.getMessage() : e.toString();
            } finally {
                indiriliyor = false;
            }
        }, "yuzuk-model").start();
    }

    /** Bütün modelleri siler (PC moduna geçişte yer açılır). */
    static void sil(Context c) {
        synchronized (YerelWhisper.class) {
            if (ctx != 0L) { birak(ctx); ctx = 0L; ctxModel = null; }
        }
        dosya(c, KUCUK).delete();
        dosya(c, BUYUK).delete();
        dosya(c, TURBO).delete();
        depo(c).edit().remove("hiz").apply();
    }

    /** Seçili olmayan modeli siler (küçük modele geçince 190 MB geri gelir). */
    static void digeriniSil(Context c) {
        Model m = secili(c);
        for (Model x : new Model[]{KUCUK, BUYUK, TURBO}) if (x != m) dosya(c, x).delete();
    }

    /** Test ve teşhis için: dosyanın SHA-256'sı. */
    static String ozet(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] t = new byte[1 << 16];
            int n;
            while ((n = in.read(t)) > 0) md.update(t, 0, n);
        }
        StringBuilder s = new StringBuilder();
        for (byte x : md.digest()) s.append(String.format("%02x", x));
        return s.toString();
    }
}
