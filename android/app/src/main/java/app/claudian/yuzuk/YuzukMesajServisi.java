package app.claudian.yuzuk;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 05.10.2026 (0.12.0): bulutun zili. Push gövdesi yalnız "tur" taşır (esitle | not); not ya da hatırlatıcı metni
 * gelmez. Zil çalınca arka plan işi hemen çalışır: hazır notu alıp Obsidian'a yazar, hatırlatıcı listesini yeniden
 * kurar (yeni kalem eklenir, silinen kalemin alarmı iptal olur). Firebase yapılandırması olmayan derlemede bu sınıf
 * hiç çağrılmaz ve uygulama 15 dakikalık yoklamayla çalışmayı sürdürür.
 */
public class YuzukMesajServisi extends FirebaseMessagingService {
    static final String DEPO = "yuzuk_push";

    @Override public void onMessageReceived(@NonNull RemoteMessage m) {
        String tur = m.getData().get("tur");
        if ("esitle".equals(tur) || "not".equals(tur)) simdi(getApplicationContext());
    }

    @Override public void onNewToken(@NonNull String token) {
        getApplicationContext().getSharedPreferences(DEPO, Context.MODE_PRIVATE).edit().putString("token", token).putBoolean("gonderildi", false).apply();
        new Thread(() -> kaydet(getApplicationContext())).start();
    }

    /** Push'ta gecikmesiz, hızlandırılmış iş: kota biterse Android onu normal işe çevirir, iş kaybolmaz. */
    static void simdi(Context c) {
        Constraints kosul = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager.getInstance(c).enqueueUniqueWork("yuzuk-push", ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(ArkaPlanIsci.class).setConstraints(kosul)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build());
    }

    static boolean firebaseVar(Context c) {
        try { return !FirebaseApp.getApps(c).isEmpty() || FirebaseApp.initializeApp(c) != null; }
        catch (Exception e) { return false; }
    }

    /** Uygulama her açılışta ve arka plan ayarı değişince çağırır: anahtar alınır ve bu cihazın hesabına kaydedilir. */
    static void baslat(Context c) {
        if (!firebaseVar(c)) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> {
                if (t == null) return;
                android.content.SharedPreferences p = c.getSharedPreferences(DEPO, Context.MODE_PRIVATE);
                if (!t.equals(p.getString("token", null))) p.edit().putString("token", t).putBoolean("gonderildi", false).apply();
                new Thread(() -> kaydet(c)).start();
            });
        } catch (Exception ignored) { /* Play hizmetleri yoksa push yok; yoklama sürer. */ }
    }

    /** Anahtar bulut adresiyle eşleşmiş cihaz anahtarıyla gönderilir; eşleşme yoksa ya da ağ yoksa sonra yeniden denenir. */
    static synchronized void kaydet(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences(DEPO, Context.MODE_PRIVATE);
        String token = p.getString("token", null);
        if (token == null) return;
        HttpURLConnection h = null;
        try {
            JSONObject a = new JSONObject(ArkaPlanIsci.depo(c).getString("ayar", "{}"));
            String sunucu = a.optString("sunucu"), anahtar = a.optString("anahtar");
            if (anahtar.isEmpty() || !sunucu.startsWith("https://")) return;
            String imza = sunucu + "|" + anahtar.hashCode() + "|" + token;
            if (p.getBoolean("gonderildi", false) && imza.equals(p.getString("imza", null))) return;
            h = (HttpURLConnection) new URL(sunucu.replaceAll("/+$", "") + "/v1/cihaz/push").openConnection();
            h.setRequestMethod("POST"); h.setConnectTimeout(10_000); h.setReadTimeout(20_000); h.setDoOutput(true);
            h.setInstanceFollowRedirects(false);
            h.setRequestProperty("Authorization", "Bearer " + anahtar);
            h.setRequestProperty("Content-Type", "application/json");
            h.setRequestProperty("X-Yuzuk-Surum", BuildConfig.VERSION_NAME);
            try (OutputStream o = h.getOutputStream()) { o.write(new JSONObject().put("token", token).toString().getBytes(StandardCharsets.UTF_8)); }
            if (h.getResponseCode() == 200) p.edit().putBoolean("gonderildi", true).putString("imza", imza).apply();
        } catch (Exception ignored) {
        } finally { if (h != null) h.disconnect(); }
    }
}
