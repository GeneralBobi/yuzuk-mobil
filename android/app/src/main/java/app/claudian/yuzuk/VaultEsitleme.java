package app.claudian.yuzuk;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/**
 * Telefon vault'unun bilgisayarla eşitlenmesi, uygulama kapalıyken (0.11.9, 09.10.2026).
 * Boran: "bu buton yine kalsın ancak senkronizasyonlar kayıt sonrası yapılsın". Arka planda not teslim alınınca
 * yalnız not dosyası yazılıyordu; bilgisayarın aynı kayıttan güncellediği Kontrol Paneli ve Hatırlatıcılar için
 * Eşitle'ye elle basmak gerekiyordu. Kurallar hat.ts → esitle() ile birebir aynıdır; iki taraf aynı izi (depoda
 * "esitleme_izi") kullanır ve aynı anda çalışmaz (MESGUL).
 */
final class VaultEsitleme {
    private VaultEsitleme() { }

    /** Uygulamanın Eşitle'si ile arka plan aynı anda vault'a yazmaz. Herhangi bir iş parçacığı bırakabilir. */
    static final Semaphore MESGUL = new Semaphore(1);

    static final class Dosya {
        final String yol; final long mtime; final DocumentFile f;
        Dosya(String yol, long mtime, DocumentFile f) { this.yol = yol; this.mtime = mtime; this.f = f; }
    }

    /** Arka planda not teslim alındı: eşitleme gerekiyor (başarılı bir eşitleme bayrağı indirir). */
    static void gerekli(Context c) {
        synchronized (ArkaPlanIsci.KILIT) { ArkaPlanIsci.depo(c).edit().putBoolean("esitleme_gerek", true).commit(); }
    }

    static boolean gerekliMi(Context c) { return ArkaPlanIsci.depo(c).getBoolean("esitleme_gerek", false); }

    static JSONObject izOku(Context c) {
        try { return new JSONObject(ArkaPlanIsci.depo(c).getString("esitleme_izi", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static void izYaz(Context c, JSONObject iz, boolean tamam, String metin) throws IOException {
        synchronized (ArkaPlanIsci.KILIT) {
            android.content.SharedPreferences.Editor e = ArkaPlanIsci.depo(c).edit().putString("esitleme_izi", iz.toString());
            if (tamam) e.putBoolean("esitleme_gerek", false)
                    .putLong("esitleme_zaman", System.currentTimeMillis()).putString("esitleme_metin", metin == null ? "" : metin);
            if (!e.commit()) throw new IOException("eşitleme izi yazılamadı");
        }
    }

    /** Tam eşitleme. Döner: kısa sonuç metni. Uygulama öne gelirse (dur) işlenen dosyaların izi korunarak bırakılır. */
    static String esitle(Context c, JSONObject a, BooleanSupplier dur) throws Exception {
        if (!MESGUL.tryAcquire()) return null; // uygulama şu an eşitliyor
        JSONObject iz = izOku(c), yeniIz = new JSONObject();
        try {
            String agac = a.optString("vault");
            DocumentFile kok = DocumentFile.fromTreeUri(c, Uri.parse(agac));
            if (kok == null || !kok.canWrite() || kok.findFile(".obsidian") == null) throw new IOException("vault erişimi yok");
            JSONObject l = new JSONObject(http(a, "GET", "/v1/vault/liste?surum=3", null));
            Map<String, JSONObject> pc = new HashMap<>();
            JSONArray ld = l.getJSONArray("dosyalar");
            for (int i = 0; i < ld.length(); i++) pc.put(ld.getJSONObject(i).getString("yol"), ld.getJSONObject(i));
            Set<String> silinen = new HashSet<>();
            JSONArray ls = l.optJSONArray("silinen");
            for (int i = 0; ls != null && i < ls.length(); i++) silinen.add(ls.getString(i));
            Map<String, Dosya> tel = new HashMap<>();
            dolas(kok, "", tel);
            Map<String, String> telSha = new HashMap<>(), telIcerik = new HashMap<>();
            for (Dosya d : tel.values()) {
                if (dur.getAsBoolean()) { izYaz(c, birlestir(iz, yeniIz), false, null); return null; }
                String s = oku(c, d.f);
                telIcerik.put(d.yol, s); telSha.put(d.yol, ArkaPlanIsci.sha(s));
            }
            String alt = a.optString("altKlasor", "Yüzük") + "/";
            Set<String> pcIzleri = new HashSet<>();
            for (JSONObject p : pc.values()) pcIzleri.add(p.getString("sha"));
            int indirilen = 0, gonderilen = 0, silindi = 0;
            TreeSet<String> yollar = new TreeSet<>(pc.keySet()); yollar.addAll(tel.keySet());
            for (String yol : yollar) {
                if (dur.getAsBoolean()) { izYaz(c, birlestir(iz, yeniIz), false, null); return null; }
                JSONObject p = pc.get(yol); Dosya t = tel.get(yol); String ts = telSha.get(yol);
                if (p != null && t == null) { indir(c, a, agac, yol); indirilen++; yeniIz.put(yol, p.getString("sha")); continue; }
                if (t != null && p == null && silinen.contains(yol)) { sil(kok, yol); silindi++; continue; }
                if (t != null && p == null && ((yol.startsWith(alt) && pc.containsKey(yol.substring(alt.length()))) || pcIzleri.contains(ts))) {
                    JSONObject asil = yol.startsWith(alt) ? pc.get(yol.substring(alt.length())) : null;
                    if (asil != null && asil.getString("sha").equals(ts)) sil(kok, yol); // yanlış köke inmiş birebir kopya
                    continue;
                }
                if (t != null && p == null) { gonder(a, yol, telIcerik.get(yol), false); gonderilen++; yeniIz.put(yol, ts); continue; }
                if (p == null) continue;
                String ps = p.getString("sha");
                if (ps.equals(ts)) { yeniIz.put(yol, ps); continue; }
                String eski = iz.optString(yol, null);
                boolean pcDegisti = !ps.equals(eski), telDegisti = !ts.equals(eski);
                if (eski == null) { gonder(a, yol, telIcerik.get(yol), true); indir(c, a, agac, yol); indirilen++; yeniIz.put(yol, ps); }
                else if (pcDegisti && !telDegisti) { indir(c, a, agac, yol); indirilen++; yeniIz.put(yol, ps); }
                else if (telDegisti && !pcDegisti) { gonder(a, yol, telIcerik.get(yol), false); gonderilen++; yeniIz.put(yol, ts); }
                else if (p.optLong("mtime") >= t.mtime) { gonder(a, yol, telIcerik.get(yol), true); indir(c, a, agac, yol); indirilen++; yeniIz.put(yol, ps); }
                else { gonder(a, yol, telIcerik.get(yol), false); gonderilen++; yeniIz.put(yol, ts); }
            }
            String metin = (indirilen + gonderilen > 0 ? indirilen + " geldi, " + gonderilen + " gitti." : "İki taraf aynı.")
                    + (silindi > 0 ? " " + silindi + " silindi." : "");
            izYaz(c, yeniIz, true, metin);
            return metin;
        } catch (Exception e) {
            izYaz(c, birlestir(iz, yeniIz), false, null);
            throw e;
        } finally {
            MESGUL.release();
        }
    }

    private static JSONObject birlestir(JSONObject a, JSONObject b) throws Exception {
        JSONObject r = new JSONObject(a.toString());
        for (Iterator<String> it = b.keys(); it.hasNext();) { String k = it.next(); r.put(k, b.get(k)); }
        return r;
    }

    /** .md ve .canvas; gizli klasörler (.obsidian, .trash…) dışarıda — YuzukEklentisi.vaultListe ile aynı. */
    static void dolas(DocumentFile dizin, String onek, Map<String, Dosya> liste) {
        for (DocumentFile f : dizin.listFiles()) {
            String ad = f.getName();
            if (ad == null || ad.startsWith(".")) continue;
            if (f.isDirectory()) dolas(f, onek + ad + "/", liste);
            else if (ad.endsWith(".md") || ad.endsWith(".canvas")) liste.put(onek + ad, new Dosya(onek + ad, f.lastModified(), f));
        }
    }

    static String oku(Context c, DocumentFile f) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(f.getUri()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("okunamadı: " + f.getName());
            byte[] b = new byte[1 << 16]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static void indir(Context c, JSONObject a, String agac, String yol) throws Exception {
        String icerik = http(a, "GET", "/v1/vault/dosya?surum=3&yol=" + URLEncoder.encode(yol, "UTF-8"), null);
        yazDosya(c, agac, yol, icerik);
    }

    private static void gonder(JSONObject a, String yol, String icerik, boolean yedek) throws Exception {
        http(a, "PUT", "/v1/vault/dosya?surum=3&yol=" + URLEncoder.encode(yol, "UTF-8") + (yedek ? "&yedek=1" : ""), icerik);
    }

    /** Ara klasörleri açar, yazar ve geri okuyarak doğrular (bazı SAF sağlayıcıları kısmi yazıyı sessizce kabul eder). */
    static void yazDosya(Context c, String agac, String yol, String metin) throws Exception {
        String[] parts = yol.split("/", -1);
        for (String p : parts) if (p.isEmpty() || p.startsWith(".") || p.contains("\\")) throw new IOException("geçersiz yol");
        if (!yol.endsWith(".md") && !yol.endsWith(".canvas")) throw new IOException("yalnız .md ve .canvas");
        DocumentFile d = DocumentFile.fromTreeUri(c, Uri.parse(agac));
        if (d == null || !d.canWrite()) throw new IOException("vault erişimi yok");
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
        if (!ArkaPlanIsci.sha(oku(c, f)).equals(ArkaPlanIsci.sha(metin))) throw new IOException("yazım doğrulanamadı: " + yol);
    }

    /** Bilgisayarda silinen dosya ve boşalan klasörleri (vault kökü hariç). */
    private static void sil(DocumentFile kok, String yol) {
        String[] p = yol.split("/");
        List<DocumentFile> zincir = new ArrayList<>();
        DocumentFile d = kok;
        for (int i = 0; i < p.length - 1 && d != null; i++) { d = d.findFile(p[i]); zincir.add(d); }
        DocumentFile f = d != null ? d.findFile(p[p.length - 1]) : null;
        if (f != null && f.isFile()) f.delete();
        for (int i = zincir.size() - 1; i >= 0; i--) {
            DocumentFile z = zincir.get(i);
            if (z == null || z.listFiles().length > 0 || !z.delete()) break;
        }
    }

    static String http(JSONObject a, String yontem, String yol, String govde) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(a.getString("sunucu").replaceAll("/+$", "") + yol).openConnection();
        try {
            h.setConnectTimeout(10_000); h.setReadTimeout(30_000); h.setInstanceFollowRedirects(false);
            h.setRequestMethod(yontem);
            h.setRequestProperty("Authorization", "Bearer " + a.getString("anahtar"));
            h.setRequestProperty("X-Yuzuk-Surum", BuildConfig.VERSION_NAME);
            if (govde != null) {
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "text/markdown; charset=utf-8");
                try (OutputStream out = h.getOutputStream()) { out.write(govde.getBytes(StandardCharsets.UTF_8)); }
            }
            int k = h.getResponseCode();
            if (k != 200) throw new IOException(yontem + " " + yol + " → HTTP " + k);
            try (InputStream in = h.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) { if (out.size() + n > 8_000_000) throw new IOException("yanıt büyük"); out.write(b, 0, n); }
                return out.toString("UTF-8");
            }
        } finally { h.disconnect(); }
    }
}
