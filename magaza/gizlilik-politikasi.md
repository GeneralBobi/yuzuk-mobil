# Yüzük Gizlilik Politikası — TASLAK

> Taslak, 24.09.2026. Hukuki gözden geçirme yapılmadı; yayından önce bir hukukçuya (KVKK ve GDPR) okutulmalı.
> Köşeli parantezli alanlar Boran tarafından doldurulacak.

**Sorumlu:** [ad / şirket], [adres], iletişim: [e-posta]

## Yüzük ne yapar

Yüzük, senin başlattığın kayıtlardan Obsidian'a not yazan bir uygulamadır. Kayıt yalnız sen halkaya dokunduğunda başlar
ve telefonun bildirim alanında kayıt sürdükçe görünür bir bildirim durur.

## Hangi veriler, nerede, ne kadar süre

| Veri | Nereye gider | Ne kadar kalır |
|---|---|---|
| Ses kaydı | Telefonundan Yüzük bulut katmanına (yuzuk-api.claudian.app), oradan kaydı işleyen sunucuya akarak geçer | Telefonda not yazılana kadar; işleyen sunucuda yazıya dökülene kadar. Bulut katmanında hiç yazılmaz. |
| Konuşma metni | İşleyen sunucuda oluşur; mahrem cümleler (başkalarının sağlık, aile, para bilgisi; kimlik, kart, şifre; "kaydetme" isteği ve sonrası) çıkarılır; kalanı notu yazması için seçtiğin yapay zekâ sağlayıcısına gönderilir | Sunucuda not telefona teslim edilene kadar (en çok 7 gün). Sağlayıcının saklama koşulları: [Anthropic / OpenAI / Google bağlantıları] |
| Not | Telefonundaki Obsidian klasörüne yazılır | Senin kontrolünde |
| Ses izi (isteğe bağlı) | "Sesini tanıt" ile 20–30 sn okuduğun kayıttan çıkarılan sayı dizisi; işleyen sunucuda durur. Okuma kaydı hemen silinir. | Sen silene kadar (Ayarlar → Ses izini sil) |
| Hesap ve cihaz bilgisi | Bulut katmanı veritabanı: hesap kimliği, cihaz adı, eşleştirme anahtarının özeti, kayıtların zamanı ve süresi | Hesap silinene kadar |

Ses izi biyometrik veridir. Yalnız açık onayınla ("Sesini tanıt" akışını başlatman) oluşturulur ve konuşmacıları ayırmak
dışında hiçbir amaçla kullanılmaz.

## Çevrendeki kişiler

Yüzük çevrendeki konuşmayı kaydeder. Kayıt yaptığın ortamdaki kişilere bunu söylemek ve bulunduğun ülkenin kayıt
kurallarına uymak senin sorumluluğundadır. [Hukuki metin: KKTC / TR / AB için ayrı değerlendirme.]

## Paylaşım

Verilerini satmayız ve reklam için kullanmayız. Konuşma metni yalnız notu yazması için seçtiğin yapay zekâ
sağlayıcısına gönderilir. Altyapı sağlayıcımız Cloudflare'dir (bulut katmanı ve veritabanı).

## Hakların

Verilerine erişme, düzeltme, silme ve işlemeye itiraz etme hakkın var. Ses izini ve cihazlarını uygulamadan
silebilirsin; hesabının tamamen silinmesi için [e-posta] adresine yaz.

## Değişiklikler

Bu politika değişirse uygulamada ve bu sayfada duyurulur. Son güncelleme: [tarih].
