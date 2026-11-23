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
    static final String MODEL = "ggml-small-q5_1.bin";
    static final String ADRES = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/" + MODEL;
    static final String SHA256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb";
    static final long BOYUT = 190085487L;

    static volatile long indirilen = 0L;
    static volatile boolean indiriliyor = false;
    static volatile String indirmeHatasi = null;

    private static boolean kutuphane = false;
    private static long ctx = 0L;

    static native long yukle(String yol);
    static native void birak(long ctx);
    static native String coz(long ctx, float[] ornekler, String dil);

    private YerelWhisper() { }

    static File dosya(Context c) {
        return new File(new File(c.getFilesDir(), "modeller"), MODEL);
    }

    static boolean hazir(Context c) {
        File f = dosya(c);
        return f.exists() && f.length() == BOYUT;
    }

    private static Boolean uyumluMu = null;

    /** 0.15.4 (0.15.0'dan): kütüphane armv8.2-a+fp16+dotprod için derlenir. Bu komutları bilmeyen işlemcide (eski ARMv8.0
     *  çekirdekler) model yüklenince uygulama SIGILL ile kapanır; bu catch ile yakalanamaz. Önce /proc/cpuinfo'ya bakılır;
     *  destek yoksa yerel model hiç çalıştırılmaz, parça bilgisayarı bekler. */
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

    /** Parçayı metne çevirir. Model yoksa, işlemci desteklemiyorsa ya da yüklenemezse null: çağıran parçayı bekletir. */
    static synchronized String metin(Context c, float[] ornekler, String dil) {
        if (!hazir(c) || !uyumlu()) return null;
        try {
            if (!kutuphane) {
                System.loadLibrary("yuzukwhisper");
                kutuphane = true;
            }
            if (ctx == 0L) ctx = yukle(dosya(c).getAbsolutePath());
            if (ctx == 0L) return null;
            return coz(ctx, ornekler, dil);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Model indirme; kısmi dosya .part olarak kalır ve doğrulanınca yerine konur. */
    static void indir(Context c) {
        if (indiriliyor || hazir(c)) return;
        indiriliyor = true;
        indirmeHatasi = null;
        indirilen = 0L;
        new Thread(() -> {
            File hedef = dosya(c);
            File kismi = new File(hedef.getParentFile(), MODEL + ".part");
            try {
                hedef.getParentFile().mkdirs();
                HttpURLConnection b = (HttpURLConnection) new URL(ADRES).openConnection();
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
                if (!SHA256.equals(s.toString()) || kismi.length() != BOYUT) {
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

    static void sil(Context c) {
        synchronized (YerelWhisper.class) {
            if (ctx != 0L) { birak(ctx); ctx = 0L; }
        }
        dosya(c).delete();
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
