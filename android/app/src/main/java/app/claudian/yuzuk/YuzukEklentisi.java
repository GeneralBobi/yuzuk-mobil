package app.claudian.yuzuk;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import androidx.activity.result.ActivityResult;
import androidx.documentfile.provider.DocumentFile;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.UUID;

/**
 * Web katmanının yapamadığı işler: kilitli ekranda kayıt, Obsidian klasörüne yazma (SAF — Android'de başka
 * uygulamanın klasörüne yazmanın Play kurallarına uygun tek yolu), büyük ses dosyasını akışla yükleme, bildirim.
 */
@CapacitorPlugin(
        name = "Yuzuk",
        permissions = {
                @Permission(alias = "mikrofon", strings = {Manifest.permission.RECORD_AUDIO}),
                @Permission(alias = "bildirim", strings = {"android.permission.POST_NOTIFICATIONS"})
        })
public class YuzukEklentisi extends Plugin {

    // ------------------------------------------------ izin

    @PluginMethod
    public void izinIste(PluginCall call) {
        if (getPermissionState("mikrofon") == PermissionState.GRANTED
                && (Build.VERSION.SDK_INT < 33 || getPermissionState("bildirim") == PermissionState.GRANTED)) {
            call.resolve(sonuc("mikrofon", true));
            return;
        }
        String[] istenen = Build.VERSION.SDK_INT >= 33 ? new String[]{"mikrofon", "bildirim"} : new String[]{"mikrofon"};
        requestPermissionForAliases(istenen, call, "izinSonucu");
    }

    @PermissionCallback
    private void izinSonucu(PluginCall call) {
        // Mikrofon şart; bildirim reddedilse de kayıt çalışır.
        call.resolve(sonuc("mikrofon", getPermissionState("mikrofon") == PermissionState.GRANTED));
    }

    /** Yalnız bildirim izni (Android 13+): bilgisayardan hatırlatıcı gelen ama hiç kayıt almamış telefon için. */
    @PluginMethod
    public void bildirimIzni(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33 || getPermissionState("bildirim") == PermissionState.GRANTED) {
            call.resolve(sonuc("verildi", true));
            return;
        }
        requestPermissionForAlias("bildirim", call, "bildirimSonucu");
    }

    @PermissionCallback
    private void bildirimSonucu(PluginCall call) {
        call.resolve(sonuc("verildi", getPermissionState("bildirim") == PermissionState.GRANTED));
    }

    // ------------------------------------------------ kayıt

    @PluginMethod
    public void kayitBaslat(PluginCall call) {
        File hedef = new File(getContext().getFilesDir(), "kayit_" + System.currentTimeMillis() + ".m4a");
        Intent i = new Intent(getContext(), KayitServisi.class).putExtra(KayitServisi.YOL, hedef.getAbsolutePath());
        getContext().startForegroundService(i);
        // Servis MediaRecorder'ı başlatana kadar kısa bekle; durumu çağıran kontrol eder.
        long son = System.currentTimeMillis() + 1500;
        while (!KayitServisi.calisiyor && KayitServisi.sonHata == null && System.currentTimeMillis() < son) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) { }
        }
        call.resolve(sonuc("yol", hedef.getAbsolutePath()));
    }

    @PluginMethod
    public void kayitDurdur(PluginCall call) {
        String yol = KayitServisi.yol;
        long bas = KayitServisi.baslangicMs;
        getContext().stopService(new Intent(getContext(), KayitServisi.class));
        long son = System.currentTimeMillis() + 1500;
        while (KayitServisi.calisiyor && System.currentTimeMillis() < son) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) { }
        }
        JSObject r = new JSObject();
        File f = yol != null ? new File(yol) : null;
        r.put("yol", f != null && f.exists() && f.length() > 2000 ? yol : null);
        r.put("sureSn", bas > 0 ? (System.currentTimeMillis() - bas) / 1000 : 0);
        KayitServisi.yol = null;
        call.resolve(r);
    }

    @PluginMethod
    public void kayitDurumu(PluginCall call) {
        JSObject r = new JSObject();
        r.put("calisiyor", KayitServisi.calisiyor);
        r.put("baslangicMs", KayitServisi.baslangicMs);
        r.put("seviye", KayitServisi.seviye());
        r.put("hata", KayitServisi.sonHata);
        call.resolve(r);
    }

    // ------------------------------------------------ canlı kayıt: ses dosyası yok (27.09.2026)

    @PluginMethod
    public void canliBaslat(PluginCall call) {
        if (CanliKayitServisi.calisiyor || CanliKayitServisi.isleniyor) {
            call.reject("Önceki kayıt hâlâ metne çevriliyor; biraz bekle.");
            return;
        }
        File dokum = new File(getContext().getFilesDir(), "dokum_" + System.currentTimeMillis() + ".jsonl");
        Intent i = new Intent(getContext(), CanliKayitServisi.class)
                .putExtra("sunucu", call.getString("sunucu"))
                .putExtra("anahtar", call.getString("anahtar"))
                .putExtra("dil", call.getString("dil"))
                .putExtra("testPcm", BuildConfig.DEBUG ? call.getString("testPcm") : null)
                .putExtra("dokum", dokum.getAbsolutePath());
        getContext().startForegroundService(i);
        long son = System.currentTimeMillis() + 2000;
        while (!CanliKayitServisi.calisiyor && CanliKayitServisi.sonHata == null && System.currentTimeMillis() < son) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) { }
        }
        if (!CanliKayitServisi.calisiyor) {
            call.reject(CanliKayitServisi.sonHata != null ? CanliKayitServisi.sonHata : "kayıt başlamadı");
            return;
        }
        call.resolve(sonuc("dokum", dokum.getAbsolutePath()));
    }

    @PluginMethod
    public void canliDurdur(PluginCall call) {
        long bas = CanliKayitServisi.baslangicMs;
        CanliKayitServisi.durdur(getContext());
        JSObject r = new JSObject();
        r.put("dokum", CanliKayitServisi.dokum);
        r.put("sureSn", bas > 0 ? (System.currentTimeMillis() - bas) / 1000 : 0);
        call.resolve(r);
    }

    @PluginMethod
    public void canliDurumu(PluginCall call) {
        JSObject r = new JSObject();
        r.put("calisiyor", CanliKayitServisi.calisiyor);
        r.put("isleniyor", CanliKayitServisi.isleniyor);
        r.put("baslangicMs", CanliKayitServisi.baslangicMs);
        r.put("seviye", CanliKayitServisi.seviye);
        r.put("hata", CanliKayitServisi.sonHata);
        r.put("gonderilen", CanliKayitServisi.gonderilen);
        r.put("yerelde", CanliKayitServisi.yerelde);
        r.put("bekleyen", CanliKayitServisi.bekleyen);
        r.put("cumle", CanliKayitServisi.cumle);
        r.put("durum", CanliKayitServisi.sonDurum);
        r.put("dokum", CanliKayitServisi.dokum);
        call.resolve(r);
    }

    /** Döküm dosyası (JSON satırları) → sunucunun beklediği tek JSON: {dil, sure_sn, cumleler}. Yalnız metin. */
    @PluginMethod
    public void dokumOku(PluginCall call) {
        String yol = call.getString("yol");
        if (yol == null || !yol.startsWith(getContext().getFilesDir().getAbsolutePath())) {
            call.reject("geçersiz yol");
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(yol), StandardCharsets.UTF_8))) {
            org.json.JSONArray cumleler = new org.json.JSONArray();
            Object dil = JSONObject.NULL;
            double sure = 0;
            boolean bitti = false;
            String s;
            while ((s = r.readLine()) != null) {
                if (s.trim().isEmpty()) continue;
                JSONObject j = new JSONObject(s);
                String tur = j.optString("tur", "");
                if (tur.equals("oturum")) continue;
                if (tur.equals("son")) { bitti = true; sure = j.optDouble("sure_sn", 0); dil = j.opt("dil"); continue; }
                cumleler.put(j);
                sure = Math.max(sure, j.optDouble("son", 0));
            }
            JSONObject d = new JSONObject().put("dil", dil).put("sure_sn", sure).put("cumleler", cumleler);
            JSObject o = new JSObject();
            o.put("dokum", d.toString());
            o.put("bitti", bitti);
            o.put("cumle", cumleler.length());
            call.resolve(o);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void modelDurumu(PluginCall call) {
        JSObject r = new JSObject();
        r.put("hazir", YerelWhisper.hazir(getContext()));
        r.put("indiriliyor", YerelWhisper.indiriliyor);
        r.put("indirilen", YerelWhisper.indirilen);
        r.put("boyut", YerelWhisper.BOYUT);
        r.put("hata", YerelWhisper.indirmeHatasi);
        call.resolve(r);
    }

    @PluginMethod
    public void modelIndir(PluginCall call) {
        YerelWhisper.indir(getContext());
        call.resolve();
    }

    @PluginMethod
    public void modelSil(PluginCall call) {
        YerelWhisper.sil(getContext());
        call.resolve();
    }

    // ------------------------------------------------ dosya

    @PluginMethod
    public void dosyaSec(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/*"});
        startActivityForResult(call, i, "dosyaSecildi");
    }

    @ActivityCallback
    private void dosyaSecildi(PluginCall call, ActivityResult sonuc) {
        Uri uri = sonuc.getData() != null ? sonuc.getData().getData() : null;
        if (uri == null) {
            JSObject r = new JSObject();
            r.put("yol", null);
            r.put("ad", null);
            call.resolve(r);
            return;
        }
        new Thread(() -> {
            try {
                call.resolve(kopyala(uri));
            } catch (Exception e) {
                call.reject("Dosya okunamadı: " + e.getMessage());
            }
        }).start();
    }

    /** İçerik adresindeki dosyayı uygulamanın kendi klasörüne kopyalar: {yol, ad}. */
    private JSObject kopyala(Uri uri) throws Exception {
        String ad = gorunenAd(uri);
        String uzanti = ad != null && ad.contains(".") ? ad.substring(ad.lastIndexOf('.')) : ".m4a";
        File hedef = new File(getContext().getFilesDir(), "ice_" + System.currentTimeMillis() + uzanti);
        try (InputStream in = getContext().getContentResolver().openInputStream(uri);
             OutputStream out = new java.io.FileOutputStream(hedef)) {
            byte[] b = new byte[1 << 16];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
        JSObject r = new JSObject();
        r.put("yol", hedef.getAbsolutePath());
        r.put("ad", ad);
        return r;
    }

    // ------------------------------------------------ başka uygulamadan paylaşım

    /** WhatsApp sesli mesajı, ses kaydedici, video: "Paylaş → Claudian" ile gelen dosya not olur. */
    private JSObject bekleyenPaylasim = null;

    @Override
    public void load() {
        paylasimIsle(getActivity().getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        paylasimIsle(intent);
    }

    private void paylasimIsle(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) return;
        intent.setAction(Intent.ACTION_MAIN); // aynı paylaşım ikinci kez işlenmesin (döndürme, yeniden oluşturma)
        new Thread(() -> {
            try {
                JSObject r = kopyala(uri);
                bekleyenPaylasim = r;
                notifyListeners("paylasim", r, true);
            } catch (Exception e) {
                JSObject r = new JSObject();
                r.put("hata", "Paylaşılan dosya okunamadı: " + e.getMessage());
                notifyListeners("paylasim", r, true);
            }
        }).start();
    }

    /** Açılışta bekleyen paylaşımı bir kez verir (dinleyici kurulmadan önce gelmiş olabilir). */
    @PluginMethod
    public void paylasimAl(PluginCall call) {
        JSObject r = bekleyenPaylasim != null ? bekleyenPaylasim : new JSObject();
        bekleyenPaylasim = null;
        call.resolve(r);
    }

    private String gorunenAd(Uri uri) {
        try (Cursor c = getContext().getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return null;
    }

    @PluginMethod
    public void dosyaVarMi(PluginCall call) {
        String yol = call.getString("yol");
        call.resolve(sonuc("var", yol != null && new File(yol).exists()));
    }

    @PluginMethod
    public void dosyaSil(PluginCall call) {
        String yol = call.getString("yol");
        if (yol != null && yol.startsWith(getContext().getFilesDir().getAbsolutePath())) new File(yol).delete();
        call.resolve();
    }

    // ------------------------------------------------ yükleme

    /** Çok parçalı form, dosya belleğe alınmadan akışla gönderilir (40 dk ≈ 14 MB). */
    @PluginMethod
    public void yukle(PluginCall call) {
        String yol = call.getString("yol");
        String adres = call.getString("url");
        String anahtar = call.getString("anahtar");
        JSObject alanlar = call.getObject("alanlar", new JSObject());
        new Thread(() -> {
            HttpURLConnection b = null;
            try {
                File f = new File(yol);
                if (f.length() > 95_000_000L) {  // bulutun istek sınırı: yarıda kesilmesin, açıkça söylensin
                    JSObject r = new JSObject();
                    r.put("durum", 413);
                    r.put("govde", "{\"detail\": \"Dosya " + (f.length() / 1_000_000L) + " MB; bulut en çok 95 MB kabul ediyor. Videonun yalnız sesini ya da daha kısa bir parçasını gönder.\"}");
                    call.resolve(r);
                    return;
                }
                String sinir = "----yuzuk" + UUID.randomUUID().toString().replace("-", "");
                b = (HttpURLConnection) new URL(adres).openConnection();
                b.setRequestMethod("POST");
                b.setDoOutput(true);
                b.setConnectTimeout(20000);
                b.setReadTimeout(120000);
                b.setChunkedStreamingMode(1 << 16);
                b.setRequestProperty("Authorization", "Bearer " + anahtar);
                b.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + sinir);
                try (OutputStream out = b.getOutputStream()) {
                    Iterator<String> it = alanlar.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        yaz(out, "--" + sinir + "\r\nContent-Disposition: form-data; name=\"" + k + "\"\r\n\r\n" + alanlar.getString(k) + "\r\n");
                    }
                    yaz(out, "--" + sinir + "\r\nContent-Disposition: form-data; name=\"ses\"; filename=\"" + f.getName()
                            + "\"\r\nContent-Type: application/octet-stream\r\n\r\n");
                    try (InputStream in = new FileInputStream(f)) {
                        byte[] tampon = new byte[1 << 16];
                        int n;
                        while ((n = in.read(tampon)) > 0) out.write(tampon, 0, n);
                    }
                    yaz(out, "\r\n--" + sinir + "--\r\n");
                }
                int durum = b.getResponseCode();
                InputStream govde = durum < 400 ? b.getInputStream() : b.getErrorStream();
                StringBuilder sb = new StringBuilder();
                if (govde != null) {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(govde, StandardCharsets.UTF_8))) {
                        String s;
                        while ((s = r.readLine()) != null) sb.append(s);
                    }
                }
                JSObject r = new JSObject();
                r.put("durum", durum);
                r.put("govde", sb.toString());
                call.resolve(r);
            } catch (Exception e) {
                call.reject(e.getMessage() != null ? e.getMessage() : e.toString());
            } finally {
                if (b != null) b.disconnect();
            }
        }).start();
    }

    private static void yaz(OutputStream out, String s) throws java.io.IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------ Obsidian klasörü

    @PluginMethod
    public void klasorSec(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, i, "klasorSecildi");
    }

    @ActivityCallback
    private void klasorSecildi(PluginCall call, ActivityResult sonuc) {
        Uri uri = sonuc.getData() != null ? sonuc.getData().getData() : null;
        JSObject r = new JSObject();
        if (uri != null) {
            getContext().getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            DocumentFile d = DocumentFile.fromTreeUri(getContext(), uri);
            r.put("agac", uri.toString());
            r.put("ad", d != null ? d.getName() : null);
        } else {
            r.put("agac", null);
            r.put("ad", null);
        }
        call.resolve(r);
    }

    /** yol: "Yüzük/2026-09-23 1300 Başlık.md" — ara klasörler yoksa açılır. ekle=true ise sona eklenir. */
    @PluginMethod
    public void dosyaYaz(PluginCall call) {
        try {
            DocumentFile dizin = DocumentFile.fromTreeUri(getContext(), Uri.parse(call.getString("agac")));
            if (dizin == null || !dizin.canWrite()) throw new IllegalStateException("Obsidian klasörüne erişim yok; Ayarlar'dan yeniden seç");
            String[] parcalar = call.getString("yol").split("/");
            for (int i = 0; i < parcalar.length - 1; i++) {
                if (parcalar[i].isEmpty()) continue;
                DocumentFile alt = dizin.findFile(parcalar[i]);
                dizin = alt != null && alt.isDirectory() ? alt : dizin.createDirectory(parcalar[i]);
                if (dizin == null) throw new IllegalStateException("klasör açılamadı: " + parcalar[i]);
            }
            String ad = parcalar[parcalar.length - 1];
            DocumentFile var = dizin.findFile(ad);
            // yoksa: yalnız dosya yoksa yaz (vault iskeleti; kullanıcının ya da masaüstünün dosyasına dokunulmaz)
            if (Boolean.TRUE.equals(call.getBoolean("yoksa", false)) && var != null) {
                JSObject r = new JSObject();
                r.put("ad", ad);
                r.put("vardi", true);
                call.resolve(r);
                return;
            }
            // octet-stream: sağlayıcı ".md" adına kendi uzantısını eklemesin
            DocumentFile dosya = var != null ? var : dizin.createFile("application/octet-stream", ad);
            if (dosya == null) throw new IllegalStateException("dosya açılamadı: " + ad);
            String kip = Boolean.TRUE.equals(call.getBoolean("ekle", false)) && var != null ? "wa" : "wt";
            try (OutputStream out = getContext().getContentResolver().openOutputStream(dosya.getUri(), kip)) {
                out.write(call.getString("icerik", "").getBytes(StandardCharsets.UTF_8));
            }
            call.resolve(sonuc("ad", dosya.getName() != null ? dosya.getName() : ad));
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    // ------------------------------------------------ vault eşitleme

    /** Vault'taki .md ve .canvas dosyaları: {yol, boyut, mtime}. Gizli klasörler (.obsidian, .trash…) dışarıda. */
    @PluginMethod
    public void vaultListe(PluginCall call) {
        new Thread(() -> {
            try {
                DocumentFile kok = DocumentFile.fromTreeUri(getContext(), Uri.parse(call.getString("agac")));
                if (kok == null || !kok.canRead()) throw new IllegalStateException("Obsidian klasörüne erişim yok; Ayarlar'dan yeniden seç");
                com.getcapacitor.JSArray liste = new com.getcapacitor.JSArray();
                dolas(kok, "", liste);
                JSObject r = new JSObject();
                r.put("dosyalar", liste);
                call.resolve(r);
            } catch (Exception e) {
                call.reject(e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }).start();
    }

    private void dolas(DocumentFile dizin, String onek, com.getcapacitor.JSArray liste) {
        for (DocumentFile f : dizin.listFiles()) {
            String ad = f.getName();
            if (ad == null || ad.startsWith(".")) continue;
            if (f.isDirectory()) {
                dolas(f, onek + ad + "/", liste);
            } else if (ad.endsWith(".md") || ad.endsWith(".canvas")) {
                JSObject d = new JSObject();
                d.put("yol", onek + ad);
                d.put("boyut", f.length());
                d.put("mtime", f.lastModified());
                liste.put(d);
            }
        }
    }

    @PluginMethod
    public void vaultOku(PluginCall call) {
        try {
            DocumentFile dizin = DocumentFile.fromTreeUri(getContext(), Uri.parse(call.getString("agac")));
            String[] parcalar = call.getString("yol").split("/");
            for (int i = 0; i < parcalar.length - 1 && dizin != null; i++) dizin = dizin.findFile(parcalar[i]);
            DocumentFile f = dizin != null ? dizin.findFile(parcalar[parcalar.length - 1]) : null;
            if (f == null) throw new IllegalStateException("dosya yok: " + call.getString("yol"));
            java.io.ByteArrayOutputStream tampon = new java.io.ByteArrayOutputStream();
            try (InputStream in = getContext().getContentResolver().openInputStream(f.getUri())) {
                byte[] b = new byte[1 << 16];
                int n;
                while ((n = in.read(b)) > 0) tampon.write(b, 0, n);
            }
            JSObject r = new JSObject();
            r.put("icerik", tampon.toString("UTF-8"));
            call.resolve(r);
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    // ------------------------------------------------ bildirim

    @PluginMethod
    public void bildir(PluginCall call) {
        NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel("not") == null) {
            nm.createNotificationChannel(new NotificationChannel("not", "Hazır notlar", NotificationManager.IMPORTANCE_DEFAULT));
        }
        String metin = call.getString("metin", "");
        Notification n = new Notification.Builder(getContext(), "not")
                .setContentTitle(call.getString("baslik", "Claudian"))
                .setContentText(metin)
                .setStyle(new Notification.BigTextStyle().bigText(metin))
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setAutoCancel(true)
                .build();
        nm.notify((int) (System.currentTimeMillis() % 100000), n);
        call.resolve();
    }

    // ------------------------------------------------ hatırlatıcı

    /** Nottaki tarihli işi telefonda kurar; zamanı geçmişse kurmaz. */
    @PluginMethod
    public void hatirlaticiKur(PluginCall call) {
        try {
            long zaman = call.getLong("zamanMs", 0L);
            if (zaman <= System.currentTimeMillis()) { call.resolve(sonuc("kuruldu", false)); return; }
            HatirlaticiAlici.kur(getContext(), call.getString("id"), zaman, call.getString("baslik", "Claudian"), call.getString("metin", ""));
            call.resolve(sonuc("kuruldu", true));
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    /** Bilgisayardaki vault'un açık hatırlatıcıları: tam liste gelir, telefondaki "pc-" kümesi ona eşitlenir. */
    @PluginMethod
    public void hatirlaticiEsitle(PluginCall call) {
        try {
            call.resolve(sonuc("kurulan", HatirlaticiAlici.esitle(getContext(), call.getArray("liste", new JSArray()))));
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    @PluginMethod
    public void hatirlaticiSayisi(PluginCall call) {
        call.resolve(sonuc("sayi", HatirlaticiAlici.sayi(getContext())));
    }

    private static JSObject sonuc(String k, Object v) {
        JSObject r = new JSObject();
        r.put(k, v);
        return r;
    }
}
