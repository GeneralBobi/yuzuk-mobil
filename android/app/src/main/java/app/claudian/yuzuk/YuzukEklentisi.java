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
import android.provider.Settings;

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
import java.security.MessageDigest;
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
    /** Sistem hesap seçicisi. JWT yalnız geçici dönüş değeridir; diske veya tercihlere yazılmaz. */
    @PluginMethod
    public void googleGiris(PluginCall call) {
        String client = call.getString("clientId", ""), nonce = call.getString("nonce", "");
        if (!client.endsWith(".apps.googleusercontent.com") || !nonce.matches("[a-f0-9]{64}")) { call.reject("Google giriş yapılandırması geçersiz"); return; }
        getActivity().runOnUiThread(() -> {
            try {
                com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption option =
                        new com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(client).setNonce(nonce).build();
                androidx.credentials.GetCredentialRequest req = new androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(option).build();
                androidx.credentials.CredentialManager.create(getContext()).getCredentialAsync(getActivity(), req, new android.os.CancellationSignal(),
                        androidx.core.content.ContextCompat.getMainExecutor(getContext()),
                        new androidx.credentials.CredentialManagerCallback<androidx.credentials.GetCredentialResponse, androidx.credentials.exceptions.GetCredentialException>() {
                            @Override public void onResult(androidx.credentials.GetCredentialResponse r) {
                                try {
                                    androidx.credentials.Credential cr = r.getCredential();
                                    if (!(cr instanceof androidx.credentials.CustomCredential)
                                            || !cr.getType().equals(com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
                                        call.reject("Google kimliği alınamadı"); return;
                                    }
                                    String token = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(cr.getData()).getIdToken();
                                    call.resolve(sonuc("idToken", token));
                                } catch (Exception e) { call.reject("Google kimliği okunamadı"); }
                            }
                            @Override public void onError(androidx.credentials.exceptions.GetCredentialException e) { call.reject("Google girişi tamamlanmadı"); }
                        });
            } catch (Exception e) { call.reject("Google girişi başlatılamadı"); }
        });
    }

    @PluginMethod
    public void arkaPlanKur(PluginCall call) {
        new Thread(() -> {
            try { ArkaPlanIsci.kur(getContext(), call.getObject("ayar", new JSObject())); call.resolve(); }
            catch (Exception e) { call.reject("Arka plan eşitlemesi kurulamadı"); }
        }).start();
    }
    @PluginMethod
    public void arkaPlanOku(PluginCall call) {
        new Thread(() -> {
            synchronized (ArkaPlanIsci.KILIT) {
                try { call.resolve(new JSObject().put("teslimler", new JSObject(ArkaPlanIsci.depo(getContext()).getString("teslimler", "{}")))
                        .put("yuklemeler", new JSObject(ArkaPlanIsci.depo(getContext()).getString("yuklemeler", "{}")))); }
                catch (Exception e) { call.reject("Teslim kayıtları okunamadı"); }
            }
        }).start();
    }
    @PluginMethod
    public void arkaPlanOnayla(PluginCall call) {
        new Thread(() -> {
            synchronized (ArkaPlanIsci.KILIT) {
                try {
                    JSONObject v = new JSONObject(ArkaPlanIsci.depo(getContext()).getString("teslimler", "{}"));
                    JSArray ids = call.getArray("ids", new JSArray());
                    JSONObject a = new JSONObject(ArkaPlanIsci.depo(getContext()).getString("ayar", "{}"));
                    JSArray kalan = new JSArray();
                    org.json.JSONArray jobs = a.optJSONArray("isler");
                    java.util.Set<String> alinan = new java.util.HashSet<>();
                    for (int n = 0; n < ids.length(); n++) if (v.has(ids.getString(n))) alinan.add(ids.getString(n));
                    if (jobs != null) for (int n = 0; n < jobs.length(); n++) {
                        JSONObject job = jobs.getJSONObject(n);
                        if (!alinan.contains(job.optString("sunucuId"))) kalan.put(job);
                    }
                    a.put("isler", kalan);
                    for (int n = 0; n < ids.length(); n++) {
                        String id = ids.getString(n);
                        if (!v.has(id) || !id.matches("[0-9a-f]{32}")) continue;
                        try { ArkaPlanIsci.istek(a, "/v1/oturum/" + id, "DELETE"); v.remove(id); } catch (Exception ignored) { }
                    }
                    if (!ArkaPlanIsci.depo(getContext()).edit().putString("teslimler", v.toString()).putString("ayar", a.toString()).commit()) throw new IllegalStateException();
                    call.resolve();
                } catch (Exception e) { call.reject("Teslim kayıtları güncellenemedi"); }
            }
        }).start();
    }

    // ------------------------------------------------ kenar boşlukları (0.8.0)

    /**
     * Capacitor'un SystemBars eklentisi sistem çubuğu boşluklarını --safe-area-inset-* olarak sayfaya yazar, ama bunu
     * yalnız boşluklar değiştiğinde yapar. İlk değer sayfa yüklenmeden önce gelirse yazıldığı belge değişir ve değer
     * kaybolur; CSS o zaman env() yedeğine düşer ve eski WebView'lerde boşluk iki kez uygulanır (Hanne'nin üç tuşlu
     * gezinmesinde alt menü aşağıya oturmayıp yukarıda boşluk bıraktı). Sayfa hazır olunca boşluklar yeniden istenir.
     */
    @PluginMethod
    public void kenarlariYenile(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            androidx.core.view.ViewCompat.requestApplyInsets(getActivity().getWindow().getDecorView());
            call.resolve();
        });
    }

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
        // 0.11.5: kaydedici yeniden başladıysa birden çok parça vardır; tek dosyada birleşir.
        String tam = yol != null ? KayitServisi.birlestir() : null;
        File f = tam != null ? new File(tam) : null;
        boolean var = f != null && f.exists() && f.length() > 2000;
        r.put("yol", var ? tam : null);
        r.put("sureSn", bas > 0 ? (System.currentTimeMillis() - bas) / 1000 : 0);
        r.put("sesSn", var ? KayitServisi.sesSuresiSn(tam) : 0);
        r.put("kesinti", KayitServisi.kesinti);
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

    /** WhatsApp sesli mesajı, ses kaydedici, video: "Paylaş → Yüzük" ile gelen dosya not olur. */
    private JSObject bekleyenPaylasim = null;

    @Override
    public void load() {
        paylasimIsle(getActivity().getIntent());
        bildirimIsle(getActivity().getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        paylasimIsle(intent);
        bildirimIsle(intent);
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

    /** Çok parçalı form, dosya belleğe alınmadan akışla gönderilir (40 dk ≈ 14 MB).
     *  0.11.8: aynı dosyayı arka plan teslimi gönderiyorsa ikinci kopya gitmez (409); başarılı yükleme kalıcı olarak
     *  "yuklemeler"e yazılır ki uygulama kapanınca arka plan aynı kaydı yeniden yüklemesin. */
    @PluginMethod
    public void yukle(PluginCall call) {
        String yol = call.getString("yol");
        String adres = call.getString("url");
        String anahtar = call.getString("anahtar");
        String id = call.getString("id", "");
        JSObject alanlar = call.getObject("alanlar", new JSObject());
        new Thread(() -> {
            String kanonik = new File(yol).getAbsolutePath();
            if (!ArkaPlanIsci.YUKLENEN.add(kanonik)) {
                JSObject r = new JSObject();
                r.put("durum", 409);
                r.put("govde", "{\"detail\": \"Arka planda gönderiliyor\"}");
                call.resolve(r);
                return;
            }
            try {
                String[] sonuc = akisYukle(new File(yol), adres, anahtar, alanlar);
                int durum = Integer.parseInt(sonuc[0]);
                if (durum == 200 && !id.isEmpty()) {
                    try { ArkaPlanIsci.yuklemeKaydet(getContext(), id, new JSONObject(sonuc[1]).optString("id")); } catch (Exception ignored) { }
                }
                JSObject r = new JSObject();
                r.put("durum", durum);
                r.put("govde", sonuc[1]);
                call.resolve(r);
            } catch (Exception e) {
                call.reject(e.getMessage() != null ? e.getMessage() : e.toString());
            } finally {
                ArkaPlanIsci.YUKLENEN.remove(kanonik);
            }
        }).start();
    }

    /** Uygulama ve arka plan teslimi aynı yolu kullanır. Döner: {durum kodu, gövde}. */
    static String[] akisYukle(File f, String adres, String anahtar, JSONObject alanlar) throws Exception {
        if (f.length() > 95_000_000L) {  // bulutun istek sınırı: yarıda kesilmesin, açıkça söylensin
            return new String[]{"413", "{\"detail\": \"Dosya " + (f.length() / 1_000_000L) + " MB; bulut en çok 95 MB kabul ediyor. Videonun yalnız sesini ya da daha kısa bir parçasını gönder.\"}"};
        }
        HttpURLConnection b = null;
        try {
            String sinir = "----yuzuk" + UUID.randomUUID().toString().replace("-", "");
            b = (HttpURLConnection) new URL(adres).openConnection();
            b.setRequestMethod("POST");
            b.setDoOutput(true);
            b.setConnectTimeout(20000);
            b.setReadTimeout(120000);
            b.setChunkedStreamingMode(1 << 16);
            b.setRequestProperty("Authorization", "Bearer " + anahtar);
            b.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + sinir);
            // 0.11.8: bulut yükleme süresini dosya boyuna göre ayarlar (akışlı gövdede Content-Length yok).
            b.setRequestProperty("X-Yuzuk-Boyut", String.valueOf(f.length()));
            b.setRequestProperty("X-Yuzuk-Surum", BuildConfig.VERSION_NAME);
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
            return new String[]{String.valueOf(durum), sb.toString()};
        } finally {
            if (b != null) b.disconnect();
        }
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
            // 0.11.1: vault kökü içinde .obsidian klasörü olandır; alt klasör seçilirse eşitleme vault'u onun içine çoğaltır.
            r.put("obsidian", d != null && obsidianVar(d));
        } else {
            r.put("agac", null);
            r.put("ad", null);
        }
        call.resolve(r);
    }

    private static boolean obsidianVar(DocumentFile kok) {
        DocumentFile o = kok.findFile(".obsidian");
        return o != null && o.isDirectory();
    }

    /** 0.11.1: seçili klasör bir Obsidian vault'unun kökü mü (.obsidian klasörü var mı). */
    @PluginMethod
    public void vaultMi(PluginCall call) {
        new Thread(() -> {
            DocumentFile kok = DocumentFile.fromTreeUri(getContext(), Uri.parse(call.getString("agac", "")));
            call.resolve(sonuc("obsidian", kok != null && kok.canRead() && obsidianVar(kok)));
        }).start();
    }

    /** 0.11.1: eşitlemenin yanlış köke indirdiği kopyayı siler; boşalan klasörler de kalkar (vault kökü kalır). */
    @PluginMethod
    public void vaultSil(PluginCall call) {
        new Thread(() -> {
            try {
                DocumentFile kok = DocumentFile.fromTreeUri(getContext(), Uri.parse(call.getString("agac")));
                String[] parcalar = call.getString("yol").split("/");
                java.util.ArrayList<DocumentFile> zincir = new java.util.ArrayList<>();
                DocumentFile dizin = kok;
                for (int i = 0; i < parcalar.length - 1 && dizin != null; i++) { dizin = dizin.findFile(parcalar[i]); zincir.add(dizin); }
                DocumentFile f = dizin != null ? dizin.findFile(parcalar[parcalar.length - 1]) : null;
                boolean silindi = f != null && f.isFile() && f.delete();
                for (int i = zincir.size() - 1; i >= 0; i--) {
                    DocumentFile d = zincir.get(i);
                    if (d == null || d.listFiles().length > 0 || !d.delete()) break;
                }
                call.resolve(sonuc("silindi", silindi));
            } catch (Exception e) {
                call.reject(e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }).start();
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

    /** 0.11.9: eşitleme izi uygulama ve arka plan arasında ortak (önceden WebView'ın localStorage'ındaydı). */
    @PluginMethod
    public void esitlemeIzi(PluginCall call) {
        new Thread(() -> {
            try {
                android.content.SharedPreferences d = ArkaPlanIsci.depo(getContext());
                JSObject r = new JSObject();
                r.put("iz", new JSObject(d.getString("esitleme_izi", "{}")));
                r.put("varmi", d.contains("esitleme_izi"));
                r.put("gerek", d.getBoolean("esitleme_gerek", false));
                r.put("zaman", d.getLong("esitleme_zaman", 0));
                r.put("metin", d.getString("esitleme_metin", ""));
                call.resolve(r);
            } catch (Exception e) { call.reject("eşitleme izi okunamadı"); }
        }).start();
    }

    @PluginMethod
    public void esitlemeIziYaz(PluginCall call) {
        new Thread(() -> {
            try {
                VaultEsitleme.izYaz(getContext(), call.getObject("iz", new JSObject()), Boolean.TRUE.equals(call.getBoolean("tamam", false)), call.getString("metin", ""));
                call.resolve();
            } catch (Exception e) { call.reject("eşitleme izi yazılamadı"); }
        }).start();
    }

    /** al=true: arka plan eşitlemiyorsa kilidi alır (alindi=true); al=false: bırakır. */
    @PluginMethod
    public void esitlemeKilit(PluginCall call) {
        if (Boolean.TRUE.equals(call.getBoolean("al", true))) call.resolve(sonuc("alindi", VaultEsitleme.MESGUL.tryAcquire()));
        else { VaultEsitleme.MESGUL.release(); call.resolve(); }
    }

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
        String baslik = call.getString("baslik", "Yüzük");
        long zaman = System.currentTimeMillis();
        int kimlik = (int) (zaman % 100000);
        Notification n = new Notification.Builder(getContext(), "not")
                .setContentTitle(baslik)
                .setContentText(metin)
                .setStyle(new Notification.BigTextStyle().bigText(metin))
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                // 0.12.0: dokununca uygulama açılır, metin okuma kutusunda tam görünür (önceden dokunma hiçbir şey yapmıyordu).
                .setContentIntent(BildirimOku.niyet(getContext(), "not|" + kimlik, baslik, metin, zaman))
                .setAutoCancel(true)
                .build();
        nm.notify(kimlik, n);
        call.resolve();
    }

    /** 0.12.0: dokunulan bildirim; uygulama kapalıyken açıldıysa dinleyici kurulmadan gelmiştir, bir kez verilir. */
    private JSObject bekleyenBildirim = null;

    private void bildirimIsle(Intent intent) {
        JSObject r = BildirimOku.oku(intent);
        if (r == null) return;
        bekleyenBildirim = r;
        notifyListeners("bildirim", r, true);
    }

    // ------------------------------------------------ AI'lara doğrudan bağlan (0.13.0)

    /** Durum; sır dışarı çıkmaz. */
    @PluginMethod
    public void dogrudanDurum(PluginCall call) {
        try { call.resolve(JSObject.fromJSONObject(DogrudanCihaz.gorunum(getContext()))); }
        catch (Exception e) { call.reject(e.getMessage() != null ? e.getMessage() : e.toString()); }
    }

    /** {acik?, yazma?, vault?, pencere?}: aç/kapat, yazma izni, vault, 5 dakikalık onaysız bağlanma penceresi. */
    @PluginMethod
    public void dogrudanAyarla(PluginCall call) {
        try {
            Context c = getContext();
            Boolean acik = call.getBoolean("acik");
            if (Boolean.TRUE.equals(acik) || Boolean.TRUE.equals(call.getBoolean("pencere", false))) DogrudanCihaz.hazirla(c, call.getString("vault"));
            DogrudanCihaz.ayarla(c, acik, call.getBoolean("yazma"), call.getString("vault"), Boolean.TRUE.equals(call.getBoolean("pencere", false)));
            boolean simdi = DogrudanCihaz.durum(c).optBoolean("acik");
            if (simdi && !HafizaServisi.calisiyor) HafizaServisi.baslat(c);
            if (!simdi && HafizaServisi.calisiyor) HafizaServisi.durdur(c);
            call.resolve(JSObject.fromJSONObject(DogrudanCihaz.gorunum(c)));
        } catch (Exception e) { call.reject(e.getMessage() != null ? e.getMessage() : e.toString()); }
    }

    @PluginMethod
    public void dogrudanKarar(PluginCall call) {
        try {
            DogrudanCihaz.karar(getContext(), call.getString("id", ""), Boolean.TRUE.equals(call.getBoolean("evet", false)));
            call.resolve(JSObject.fromJSONObject(DogrudanCihaz.gorunum(getContext())));
        } catch (Exception e) { call.reject(e.getMessage() != null ? e.getMessage() : e.toString()); }
    }

    @PluginMethod
    public void dogrudanIzinKaldir(PluginCall call) {
        try {
            DogrudanCihaz.izinKaldir(getContext(), call.getString("id", ""));
            call.resolve(JSObject.fromJSONObject(DogrudanCihaz.gorunum(getContext())));
        } catch (Exception e) { call.reject(e.getMessage() != null ? e.getMessage() : e.toString()); }
    }

    @PluginMethod
    public void bildirimAl(PluginCall call) {
        JSObject r = bekleyenBildirim != null ? bekleyenBildirim : new JSObject();
        bekleyenBildirim = null;
        call.resolve(r);
    }

    // ------------------------------------------------ hatırlatıcı

    /** Nottaki tarihli işi telefonda kurar; zamanı geçmişse kurmaz. */
    @PluginMethod
    public void hatirlaticiKur(PluginCall call) {
        try {
            long zaman = call.getLong("zamanMs", 0L);
            if (zaman <= System.currentTimeMillis()) { call.resolve(sonuc("kuruldu", false)); return; }
            HatirlaticiAlici.kur(getContext(), call.getString("id"), zaman, call.getString("baslik", "Yüzük"), call.getString("metin", ""));
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

    /** 1.5.0: bildirim düğmeleri uygulama açılmadan buluta gidebilsin diye adres ve cihaz anahtarı. */
    @PluginMethod
    public void hatirlaticiBaglanti(PluginCall call) {
        HatirlaticiAlici.baglanti(getContext(), call.getString("sunucu", ""), call.getString("anahtar", ""));
        call.resolve();
    }

    /** Ağ yokken basılmış "Yaptım / Yarın tekrar" düğmeleri: uygulama gönderir. */
    @PluginMethod
    public void hatirlaticiIslemleri(PluginCall call) {
        try {
            JSObject r = new JSObject();
            r.put("islemler", new JSArray(HatirlaticiAlici.islemleriAl(getContext()).toString()));
            call.resolve(r);
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    /** 1.6.0: eşleşmede gönderilen cihaz izi. ANDROID_ID uygulamanın imza anahtarına bağlıdır, kaldırıp kurunca
     *  değişmez; buluta ham hâli değil "yuzuk:" önekli SHA-256'sı gider. Aynı telefonun eski satırı böylece iptal olur. */
    @PluginMethod
    public void cihazIzi(PluginCall call) {
        try {
            String id = Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            if (id == null || id.isEmpty()) { call.resolve(new JSObject()); return; }
            byte[] o = MessageDigest.getInstance("SHA-256").digest(("yuzuk:" + id).getBytes(StandardCharsets.UTF_8));
            StringBuilder h = new StringBuilder();
            for (byte b : o) h.append(String.format("%02x", b));
            call.resolve(sonuc("izi", h.toString()));
        } catch (Exception e) {
            call.resolve(new JSObject());
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
