package app.claudian.yuzuk;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.getcapacitor.JSObject;

/**
 * 0.12.0 (08.11.2026, Boran): bildirime dokununca uygulama açılır ve bildirimin tam metni okunur.
 * Önceden uygulamanın kendi bildirimi ("bildir") dokunulunca hiçbir şey yapmıyordu; hatırlatıcı ise uygulamayı
 * ana ekranda açıyor ve metni hiçbir yerde göstermiyordu. Ayrı bir bildirimler bölümü yok: dokunulan bildirim
 * tek bir okuma kutusunda açılır, kapanınca iz bırakmaz.
 */
final class BildirimOku {
    static final String EYLEM = "app.claudian.yuzuk.BILDIRIM_OKU";

    private BildirimOku() { }

    /** Her bildirimin kendi niyeti olsun diye istek kodu anahtardan türer; aksi hâlde son bildirimin metni hepsine yazılırdı. */
    static PendingIntent niyet(Context c, String anahtar, String baslik, String metin, long zaman) {
        Intent i = new Intent(c, MainActivity.class).setAction(EYLEM)
                .putExtra("baslik", baslik).putExtra("metin", metin).putExtra("zaman", zaman)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, ("oku|" + anahtar).hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Dokunulmuş bildirimin içeriği; değilse null. Aynı niyet döndürmede ikinci kez okunmasın diye eylem silinir. */
    static JSObject oku(Intent i) {
        if (i == null || !EYLEM.equals(i.getAction())) return null;
        JSObject r = new JSObject();
        r.put("baslik", i.getStringExtra("baslik"));
        r.put("metin", i.getStringExtra("metin"));
        r.put("zaman", i.getLongExtra("zaman", System.currentTimeMillis()));
        i.setAction(Intent.ACTION_MAIN);
        return r;
    }
}
