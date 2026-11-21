package app.claudian.yuzuk;

import static org.junit.Assert.*;
import org.junit.Test;

/** Tam mobil modun saf yardımcıları (21.11.2026): döküm süzgeci, not adı, özet, saat. */
public class MobilKayitlarTest {
    @Test public void suzgecNumaralariGizler() {
        assertEquals("kart [gizlendi] bitti", MobilKayitlar.suz("kart 4111 1111 1111 1111 bitti"));
        assertEquals("ara beni [gizlendi]", MobilKayitlar.suz("ara beni 0532 123 45 67"));
        assertEquals("kimlik [gizlendi] tamam", MobilKayitlar.suz("kimlik 12345678901 tamam"));
        assertEquals("yaz [gizlendi] adresine", MobilKayitlar.suz("yaz ali.veli@ornek.com adresine"));
        assertEquals("iban [gizlendi]", MobilKayitlar.suz("iban TR33 0006 1005 1978 6457 8413 26"));
    }

    @Test public void suzgecDersMetninaDokunmaz() {
        String ders = "Third normal form removes transitive dependencies; ödev 26 Kasım, sayfa 120-135, 2026 yılı.";
        assertEquals(ders, MobilKayitlar.suz(ders));
    }

    @Test public void notAdiDosyaSistemineUygun() {
        assertEquals("Veritabanı dersi normalizasyon", MobilKayitlar.temizBaslik("Veritabanı dersi: normalizasyon"));
        assertEquals("Not", MobilKayitlar.temizBaslik("  /:*?  "));
    }

    @Test public void ozetIlkCumle() {
        assertEquals("İlk cümle.", MobilKayitlar.ozet("## Özet\nİlk cümle. İkinci cümle.\n## Konu\nx"));
        assertEquals("", MobilKayitlar.ozet("## Konu\nx"));
        assertEquals("English summary line.", MobilKayitlar.ozet("## Summary\nEnglish summary line. More.\n"));
    }

    @Test public void saatYalnizGecerli() {
        assertTrue("09:30".matches(MobilKayitlar.SAAT));
        assertTrue("23:59".matches(MobilKayitlar.SAAT));
        assertFalse("24:00".matches(MobilKayitlar.SAAT));
        assertFalse("99:99".matches(MobilKayitlar.SAAT));
    }
}
