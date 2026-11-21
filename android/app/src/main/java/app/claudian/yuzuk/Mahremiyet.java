package app.claudian.yuzuk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mahremiyet kural katmanı, telefonda (21.11.2026). Bilgisayardaki motorun `mahremiyet.py` dosyasının birebir Java
 * karşılığı (laya-kapi, hatirlat-1.10.1). 26.09.2026 anlaşması: mahremiyet kuralları kullanıcının değiştirebileceği
 * notlarda değil, yerelde çalışan ve denetlenebilir kodda uygulanır. Tam mobil modda döküm AI'a gitmeden önce bu
 * katmandan geçer; kural tetiklenen cümle hiç gönderilmez.
 *
 * - numara : kimlik, kart, IBAN, telefon; değeri söylenen şifre — rakamla ya da sözle.
 * - özel   : sağlık/aile/para/disiplin kelimesi + başka birine işaret (akraba, kesme işaretli isim, hitap). Kişinin
 *            kendi randevusu ("doktor randevum", "ilacımı") kaydedilir.
 *
 * Python'daki desenler aynen taşındı; Java'da Türkçe harflerin \w ve \b içinde sayılması için UNICODE_CHARACTER_CLASS.
 */
final class Mahremiyet {
    private Mahremiyet() { }

    private static final int B = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
    private static final int U = Pattern.UNICODE_CHARACTER_CLASS;

    private static final String SAYI = "(sıfır|bir|iki|üç|dört|beş|altı|yedi|sekiz|dokuz|on)";
    private static final Pattern NUMARA = Pattern.compile(
        "(şifre|parola|pin)\\w*[\\s:,]+(\\S+\\s+){0,4}?(\\d|harfle|" + SAYI + "\\b)|"
        + "tc kimlik|kimlik numara|kart numara|\\biban\\b|hesap numara|telefon numara|\\bcvv\\b|son kullanma|"
        + "\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}|\\b\\d{11}\\b|\\bTR\\d{2}|"
        + "(\\b" + SAYI + "\\b[ ,]+){4,}", B);

    private static final Pattern OZEL = Pattern.compile(
        "ameliyat|kanser|hamile|tahlil|teşhis|hastalı|hastane|depresyon|tedavi|şekeri|sağlık rapor|ilaç doz|"
        + "sağlık durum|rapor aldı|raporlu|kemo|radyoterapi|psikiyatr|psikolog|terapi|intihar|yoğun bakım|"
        + "boşan|evliliğ|haciz|\\bborc|\\bborç|maaş|\\bzam\\b|disiplin|soruşturma|not ortalama|dersten kal|"
        + "performans değerlendirme|\\badres", B);

    private static final Pattern BASKASI = Pattern.compile(
        "(?i:\\b(annem|annemi|annemin|annenin|anneme|babam|babamı|babamın|babanın|kardeşim|kardeşimi|kardeşimin|halam|halamın|teyzem|dayım|amcam|"
        + "eşim|eşimin|arkadaşım|arkadaşımın|çocuğun|çocuğu|komşu|müdür|öğrenci|ailesi|yan koltuk|adamın|kadının|doktor|onun|onların)\\b)|"
        + "\\b[A-ZÇĞİÖŞÜ][a-zçğıöşü]+'(ın|in|un|ün|nın|nin|nun|nün|a|e|ya|ye|ı|i|u|ü)\\b|"
        + "^(?!(?:Olur|Tamam|Tamamdır|Evet|Hayır|Peki|Hadi|Haydi|Aa|Ya|Yani|Neyse|Tabii|Tabi|Bak|Şimdi|Sonra|Ayrıca|"
        + "Ama|Fakat|Ancak|Bence|Aslında|Birincisi|İkincisi|Üçüncüsü|Önce|Merhaba|Selam|Günaydın|Arkadaşlar|Vay|Eee)\\b)"
        + "[A-ZÇĞİÖŞÜ][a-zçğıöşü]+(?<!lar)(?<!ler),|"
        + "\\w+(mış|miş|muş|müş)\\b|\\bkullandı\\b", U);

    private static final Pattern KENDI = Pattern.compile("\\b(benim|randevum|ilacım|ilacımı|kontrolüm|kendime|tansiyonum|borcumu|adresim\\w*)\\b", B);
    private static final Pattern KENDI_OZEL = Pattern.compile("\\bborcumu|\\bkredi kartı", B);

    /** Bir kural tetiklenirse nedeni ("numara" | "ozel"), yoksa null. */
    static String kural(String metin) {
        if (NUMARA.matcher(metin).find()) return "numara";
        if (KENDI_OZEL.matcher(metin).find()) return "ozel";
        if (OZEL.matcher(metin).find() && BASKASI.matcher(metin).find() && !KENDI.matcher(metin).find()) return "ozel";
        return null;
    }

    // İkinci kontrol: AI bir numarayı yine de nota taşırsa not vault'a yazılmadan önce çıkarılır.
    private static final Pattern[] NOT_TEMIZLE = {
        Pattern.compile("\\bTR\\d{2}(?:[ ]?\\d{4}){5}(?:[ ]?\\d{1,2})?\\b", B),
        Pattern.compile("\\b(?:\\d{4}[ -]?){3}\\d{1,4}\\b", U),
        Pattern.compile("(?<![\\d.,])\\d{11}(?!\\d|[.,]\\d)", U),
        Pattern.compile("(?:\\+90[\\s-]?)?\\(?0?5\\d{2}\\)?[\\s-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}\\b", U),
    };
    private static final Pattern SIFRE_DEGERI = Pattern.compile(
        "\\b((?:şifre|parola|pin)\\w*(?:\\s+(?:kodu|numarası))?\\s*(?:[:=]\\s*|(?=[^\\s,.;]*\\d)))([^\\s,.;]+)", B);
    private static final java.util.Set<String> SIFRE_DEGIL = new java.util.HashSet<>(java.util.Arrays.asList(
        "değiştir", "değiştirmek", "sıfırla", "unuttum", "gerekli", "yok", "ve", "ile"));

    /** Notun içindeki numara ve şifre değerlerini "[çıkarıldı]" yapar. */
    static String notTemizle(String metin) {
        String m = metin;
        for (Pattern p : NOT_TEMIZLE) m = p.matcher(m).replaceAll("[çıkarıldı]");
        Matcher s = SIFRE_DEGERI.matcher(m);
        StringBuilder out = new StringBuilder();
        while (s.find()) {
            String deger = s.group(2);
            String yerine = SIFRE_DEGIL.contains(deger.toLowerCase(java.util.Locale.forLanguageTag("tr"))) ? s.group(0) : s.group(1) + "[çıkarıldı]";
            s.appendReplacement(out, Matcher.quoteReplacement(yerine));
        }
        s.appendTail(out);
        return out.toString();
    }
}
