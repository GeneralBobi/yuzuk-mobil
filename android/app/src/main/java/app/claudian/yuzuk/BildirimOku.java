package app.claudian.yuzuk;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;

import com.getcapacitor.JSObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 0.12.0 (08.11.2026, Boran): bildirime dokununca uygulama açılır ve bildirimin tam metni okunur.
 * Önceden uygulamanın kendi bildirimi ("bildir") dokunulunca hiçbir şey yapmıyordu; hatırlatıcı ise uygulamayı
 * ana ekranda açıyor ve metni hiçbir yerde göstermiyordu. Ayrı bir bildirimler bölümü yok: dokunulan bildirim
 * tek bir okuma sayfasında açılır, kapanınca iz bırakmaz.
 *
 * 21.11.2026 (güvenlik raporu): MainActivity başlatıcı olduğu için başka bir uygulama bu eylemle Yüzük'ü açıp kendi
 * metnini "bildirim" diye gösterebiliyordu. Niyet artık uygulamanın kendi gizli anahtarıyla imzalı; imzası tutmayan
 * niyet okunmaz.
 */
final class BildirimOku {
    static final String EYLEM = "app.claudian.yuzuk.BILDIRIM_OKU";

    private BildirimOku() { }

    /** Her bildirimin kendi niyeti olsun diye istek kodu anahtardan türer; aksi hâlde son bildirimin metni hepsine yazılırdı. */
    static PendingIntent niyet(Context c, String anahtar, String baslik, String metin, long zaman) {
        Intent i = new Intent(c, MainActivity.class).setAction(EYLEM)
                .putExtra("baslik", baslik).putExtra("metin", metin).putExtra("zaman", zaman)
                .putExtra("imza", imza(c, baslik, metin, zaman))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, ("oku|" + anahtar).hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Dokunulmuş bildirimin içeriği; değilse ya da imzası tutmuyorsa null. Aynı niyet döndürmede ikinci kez okunmasın diye eylem silinir. */
    static JSObject oku(Context c, Intent i) {
        if (i == null || !EYLEM.equals(i.getAction())) return null;
        String baslik = i.getStringExtra("baslik"), metin = i.getStringExtra("metin");
        long zaman = i.getLongExtra("zaman", 0L);
        String gelen = i.getStringExtra("imza");
        i.setAction(Intent.ACTION_MAIN);
        if (gelen == null || !MessageDigest.isEqual(gelen.getBytes(StandardCharsets.US_ASCII),
                imza(c, baslik, metin, zaman).getBytes(StandardCharsets.US_ASCII))) return null;
        JSObject r = new JSObject();
        r.put("baslik", baslik);
        r.put("metin", metin);
        r.put("zaman", zaman > 0 ? zaman : System.currentTimeMillis());
        return r;
    }

    static String imza(Context c, String baslik, String metin, long zaman) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(anahtar(c), "HmacSHA256"));
            String girdi = (baslik == null ? "" : baslik) + "\u0000" + (metin == null ? "" : metin) + "\u0000" + zaman;
            return Base64.encodeToString(mac.doFinal(girdi.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** Uygulamaya özel, ilk kullanımda üretilen 32 baytlık anahtar. Uygulama dışına çıkmaz. */
    private static synchronized byte[] anahtar(Context c) {
        SharedPreferences p = c.getSharedPreferences("bildirim_imza", Context.MODE_PRIVATE);
        String k = p.getString("k", null);
        if (k == null) {
            byte[] b = new byte[32];
            new SecureRandom().nextBytes(b);
            k = Base64.encodeToString(b, Base64.NO_WRAP);
            p.edit().putString("k", k).commit();
        }
        return Base64.decode(k, Base64.NO_WRAP);
    }
}
