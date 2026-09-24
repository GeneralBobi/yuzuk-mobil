# Product

<!-- impeccable:product-schema 1 -->

> Kaynak: Boran'ın 23–24.09.2026 oturumlarındaki kararları (Claudian vault: Yüzük.md, Kararlar.md). Boran bu
> oturumda "kendin kararlar alarak ilerle" dedi; init görüşmesi yapılmadı. **[çıkarım]** işaretli satırlar
> kararlardan türetildi, Boran tarafından ayrıca onaylanmadı.

## Platform

android

Capacitor 8 kabuğu içinde web arayüzü (React + Vite); Google Play'e Android uygulaması olarak çıkar. Yerel
işler (kilitli ekranda kayıt, Obsidian klasörüne yazma, bildirim, hatırlatıcı) Java eklentisinde.

## Users

Birincil kullanıcı Boran: öğrenci ve kurucu; derste, toplantıda, sokakta, arkadaşlarıyla konuşurken telefonu
cebinden çıkarıp kaydı başlatır ve telefonu bırakır. Beta: Boran + 12 test kullanıcısı; hedef küresel pazar.
Kullanıcıların çoğunda güçlü bilgisayar olmayacağı varsayılır.

## Product Purpose

Çevrede konuşulanı dinler ve Obsidian'a **anlam taşıyan** bir not yazar: ne konuşuldu, kimler vardı, neyin altı
çizildi, sen ne söz verdin; tarihli işler telefonda hatırlatıcı olur. Asıl ürün not alan bir yüzük; donanım
gelene kadar kaydı telefon yapar. Uygulama sohbet değil, pasif bir not tutucudur.

## Positioning

Cümle kopyalamaz, olayı yazar ("selam ben Nil" → "Nil ile tanıştın"). Konuşmacıları ayırır ve kullanıcının
kendi sesini tanır. Mahrem olan nota girmez, ses saklanmaz. Notu kullanıcının seçtiği AI yazar
(Claude / ChatGPT / Gemini) ve not kullanıcının kendi Obsidian vault'una düşer.

## Operating Context

- Kayıt: uygulama içi mikrofon (kilitli ekranda sürer) ya da mp3/mp4 içe aktarma.
- Hat: telefon → yuzuk-api.claudian.app (bulut) → işlem düğümü (bugün Boran'ın bilgisayarı) → not telefona
  döner → telefondaki Obsidian vault klasörüne yazılır → hatırlatıcı bildirimi.
- Kurulum: masaüstündeki Claudian'dan QR / tek kullanımlık kod ile eşleşme, vault klasörü seçimi,
  isteğe bağlı "sesini tanıt".

## Capabilities and Constraints

- Kayıt, içe aktarma, oturum listesi (durumlar: telefonda bekliyor, gönderiliyor, işleniyor, Obsidian'da, hata).
- Notu yazan seçimi; konuşmacı sayısı ve kullanıcının payı sonuçta gelir.
- Ses izi: kullanıcı 20–30 sn okur; yalnız sayı dizisi düğümde saklanır; kullanıcı her an silebilir.
- Hesap: cihazlar ve bu ayın kullanımı buluttan okunur; cihaz iptal edilebilir.
- Açık kayıt (bilgisayarsız kullanım) henüz kapalı. **[çıkarım]** arayüz bunu "yakında" diye göstermez, hiç söz etmez.
- Widget ve yüzük donanımı sonraki iş.

## Brand Commitments

- Görsel otorite Claudian masaüstü uygulaması ve claudian.app kelime işareti (turuncu nokta). Boran
  24.09.2026: mobil uygulama masaüstünün tonunda ve tasarımında olmalı. Eski krem/yeşil tasarımlar reddedildi.
- Ayrı amblem, yeni yazı ailesi ya da palet icat edilmez.
- Animasyon: LottieFiles tercih; zorunlu değil, üçüncü taraf kaynaklar da kullanılabilir. Tasarım
  becerisiyle elde animasyon yazılmaz.

## Evidence on Hand

- Gerçek uçtan uca testler: IELTS kaydı (2 kişi, aday "Sen" olarak ayrıldı), Selçuk Şirin söyleşisi.
- Kullanıcı yorumu, müşteri, fiyat, istatistik yok; uydurulmaz.

## Product Principles

1. Kayıt bir dokunuş uzakta; gerisi arka planda olur.
2. Kullanıcı notun nereye gittiğini ve hangi aşamada olduğunu her an görür.
3. Mahremiyet sözü arayüzde somut: ses ne zaman silinir, ses izi nerede durur, kim yazar.
4. Hata olduğunda kayıt kaybolmaz; telefonda bekler ve tekrar denenir.

## Accessibility & Inclusion

Türkçe arayüz. **[çıkarım]** Dokunma hedefleri ≥48 dp, sistem yazı boyutuna uyum, işlevsel metin ≥11 px (masaüstü kuralı), turuncu ve görünür klavye odağı.
