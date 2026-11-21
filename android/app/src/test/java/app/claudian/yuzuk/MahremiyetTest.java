package app.claudian.yuzuk;

import static org.junit.Assert.*;
import org.junit.Test;

/** Telefon kural katmanı, bilgisayardaki mahremiyet.py ile aynı davranmalı (21.11.2026). */
public class MahremiyetTest {
    @Test public void numaraCumlesiGitmez() {
        assertEquals("numara", Mahremiyet.kural("IBAN'ım TR33 0006 1005 1978 6457 8413 26, oraya at"));
        assertEquals("numara", Mahremiyet.kural("kart 4111 1111 1111 1111 son kullanma ayın sonu"));
        assertEquals("numara", Mahremiyet.kural("telefonun şifresi 4412"));
        assertEquals("numara", Mahremiyet.kural("kimlik numaram şu"));
    }

    @Test public void baskasininOzeliGitmez() {
        assertEquals("ozel", Mahremiyet.kural("Kerem'in annesi ameliyat oldu"));
        assertEquals("ozel", Mahremiyet.kural("babamın tahlil sonuçları kötü çıkmış"));
        assertEquals("ozel", Mahremiyet.kural("kredi kartı borcumu ödeyemedim"));
    }

    @Test public void kendiIsiVeDersGider() {
        assertNull(Mahremiyet.kural("doktor randevum perşembe saat üçte"));
        assertNull(Mahremiyet.kural("akşam annemi aramam lazım"));
        assertNull(Mahremiyet.kural("Olur, gelirim, adresi atarsın"));
        assertNull(Mahremiyet.kural("Third normal form removes transitive dependencies."));
        assertNull(Mahremiyet.kural("Ödev 26 Kasım'a kadar, sayfa 120-135."));
        assertNull(Mahremiyet.kural("şifreler hash'lenmeden saklanmamalı"));
    }

    @Test public void notTemizlenir() {
        assertEquals("IBAN: [çıkarıldı]", Mahremiyet.notTemizle("IBAN: TR33 0006 1005 1978 6457 8413 26"));
        assertEquals("şifre: [çıkarıldı] idi", Mahremiyet.notTemizle("şifre: kedi2024 idi"));
        assertEquals("parolayı paylaşmayacağım", Mahremiyet.notTemizle("parolayı paylaşmayacağım"));
        assertEquals("Teslim 2026-11-26, saat 15:00.", Mahremiyet.notTemizle("Teslim 2026-11-26, saat 15:00."));
    }
}
