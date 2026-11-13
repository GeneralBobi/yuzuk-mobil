# App Store hazırlığı — Yüzük (iOS)

Durum: 13.11.2026. iOS projesi (`ios/`) ve Swift katmanı yazıldı; **Mac'te henüz derlenmedi.** İlk doğrulama
`.github/workflows/ios.yml` içindeki simülatör derlemesidir (dal GitHub'a gönderilince çalışır). Hesap, ödeme ve
gönderim Boran'da.

## Hazır olanlar

| Parça | Nerede | Not |
|---|---|---|
| Xcode projesi | `ios/App/App.xcodeproj` | Capacitor 8, Swift Package Manager; CocoaPods yok |
| Yerel katman | `ios/App/App/YuzukEklentisi.swift` | Kayıt, dosya seçme, vault klasörü, yükleme, hatırlatıcı |
| Paket kimliği | `app.claudian.yuzuk` | Android ile aynı |
| En düşük sürüm | iOS 15 | |
| İzin metni | `Info.plist` → `NSMicrophoneUsageDescription` | Türkçe; İngilizce için `InfoPlist.strings` eklenmeli |
| Arka planda kayıt | `UIBackgroundModes: audio` | Ekran kilitliyken kayıt sürer |
| Gizlilik bildirimi | `ios/App/App/PrivacyInfo.xcprivacy` | İzleme yok; ses ve cihaz kimliği, yalnız uygulama işlevi için |
| Şifreleme beyanı | `ITSAppUsesNonExemptEncryption = false` | Yalnız HTTPS |
| Derleme ve TestFlight | `.github/workflows/ios.yml` | Sırlar eklenince arşivler ve yükler |

## Bu sürümde iOS'ta olmayanlar

| Özellik | Android'de | iOS'ta |
|---|---|---|
| Telefonda yazıya dökme (bilgisayar kapalıyken) | whisper.cpp | Yok; kayıt telefonda bekler, bilgisayar açılınca gider |
| Uygulama kapalıyken teslim | Arka plan işçisi | Yok; uygulama öne gelince gönderir ve alır |
| Bildirimde "Yaptım / Yarın" düğmeleri | Var | Yok |
| "Paylaş → Yüzük" | Var | Yok (Share Extension ayrı hedef ister) |
| Google ile giriş | Test ortamında | Yok |

## Gerçek cihazda denenmesi gerekenler

1. Dosyalar'da Obsidian vault klasörünü ("iPhone'umda / Obsidian / …" ya da iCloud Drive) seçip not yazabilmek.
   Seçilemiyorsa notlar uygulamanın kendi klasörüne yazılır ve Obsidian'dan o klasör vault olarak açılır.
2. Ekran kilitliyken ve başka uygulama öndeyken 60+ dakikalık kayıt.
3. Telefon görüşmesi ya da Siri kesintisinden sonra kaydın durumu (kesinti bildirimi henüz işlenmiyor).
4. 100 MB üstü kaydın yüklenmesi sırasında uygulamanın arka plana alınması.

## Boran'ın yapması gerekenler

1. **Apple Developer Program** (yıllık 99 USD): developer.apple.com/programs/enroll. Aynı hesap Mac uygulamasının
   noter onayını da açar.
2. **App Store Connect'te uygulama kaydı:** ad "Yüzük", paket `app.claudian.yuzuk`, birincil dil Türkçe.
3. **Sertifika ve profil:** "Apple Distribution" sertifikası (.p12) ve App Store profili; ikisi de base64 olarak
   GitHub sırlarına (`ios.yml` başındaki adlarla).
4. **App Store Connect API anahtarı** (Users and Access → Integrations): `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8`.
5. **Ekran görüntüleri:** 6,9" (1320×2868) en az 3 adet; simülatörden alınabilir.
6. **Gizlilik politikası adresi:** claudian.app/yuzuk/gizlilik · **Destek adresi:** claudian.app
7. **Hesap silme:** uygulama içinde var (Ayarlar → Hesabımı sil); inceleme notunda belirtilir.

## Mağaza metni (taslak)

- **Ad:** Yüzük
- **Alt başlık (30):** Konuşmayı nota çevirir
- **Kategori:** Verimlilik · ikincil: Eğitim
- **Yaş:** 17+ önerilir (politika 16 yaş altını dışlıyor; App Store'da en yakın kademe)
- **Anahtar kelimeler (100):** ders notu,ses kaydı,toplantı notu,obsidian,transkript,not alma,öğrenci,özet
- **Açıklama:**

  Yüzük dersi, toplantıyı ya da sohbeti kaydeder ve düzenli bir nota çevirir. Not, kendi Obsidian klasörüne yazılır.

  Ses kendi bilgisayarında yazıya dökülür ve notu kendi yapay zekâ hesabın yazar. Ses kaydı not yazıldıktan sonra
  telefondan silinir; bulutta saklanmaz.

  Kullanmak için Windows bilgisayarında Claudian kurulu olmalıdır.

## Gizlilik etiketleri (App Privacy)

| Veri | Toplanıyor mu | Kimliğe bağlı | İzleme | Amaç |
|---|---|---|---|---|
| Ses verisi | Evet | Evet | Hayır | Uygulama işlevi |
| Cihaz kimliği (özetlenmiş) | Evet | Evet | Hayır | Uygulama işlevi |
| Diğer her şey | Hayır | | | |

## İnceleme notu (App Review'a yazılacak)

- Kayıt yalnız kullanıcı düğmeye basınca başlar; kayıt sürerken sistemin mikrofon göstergesi görünür.
- Uygulama, kullanıcının kendi bilgisayarındaki Claudian ile eşleşmeden not üretmez. İnceleme için bir eşleştirme
  kodu ve çalışan bir test düğümü verilmelidir; yoksa inceleyen kişi uygulamayı deneyemez ve başvuru reddedilir
  (kural 2.1). Bu, gönderimden önce çözülmesi gereken en önemli nokta.
- Başkasının sesini kaydetme sorumluluğu kullanıcıdadır; ilk açılıştaki rıza metni inceleme notunda belirtilir.
