# Google Play hazırlığı — Yüzük

Durum: 24.09.2026. Kod tarafı hazır; hesap, ödeme ve yasal onay Boran'da.

## Hazır olanlar

| Parça | Nerede | Not |
|---|---|---|
| Play paketi (AAB) | `android/app/build/outputs/bundle/release/app-release.aab` | `gradlew bundleRelease -Pplay`; yükleme anahtarıyla imzalı |
| Yükleme anahtarı | `android/yuzuk-yukleme.jks` + `android/keystore.properties` | **Git'e girmez. Bir kopyasını parola yöneticisine ya da şifreli bir yedeğe koy.** Kaybolursa Play Console'dan yükleme anahtarı sıfırlama istenir (Play App Signing açıkken mümkün). |
| Hedef SDK | 36 (Android 16) | Play'in 2026 şartını karşılıyor |
| Uygulama kimliği | `app.claudian.yuzuk` | Değişmez; Play'e bir kez yüklendikten sonra sabit |
| Sürüm | 0.5.0 (versionCode 5000) | Her yüklemede versionCode artmalı |
| Simge | `claudian.app` kelime işareti, #0e0e10 zemin | 512×512 mağaza simgesi: `magaza/simge-512.png` |
| Ekran görüntüleri | `.impeccable/review/phone-*.png` (1080×2400) | Play en az 2 telefon görüntüsü ister; bu klasördekiler kullanılabilir |

## Boran'ın yapması gerekenler

1. **Geliştirici hesabı** (25 $, bir kez): play.google.com/console. Kişisel hesap için kimlik doğrulaması istenir.
2. **Kapalı test şartı**: 2023 sonrası açılan kişisel hesaplarda, üretime çıkmadan önce **en az 12 test kullanıcısının 14 gün boyunca**
   kapalı teste katılmış olması gerekir. Planladığın "Boran + 12" beta bu şartı tam karşılıyor: 12 kişinin Gmail adresi
   yeterli. (Şartın güncel hâlini Console'da doğrula.)
3. **Gizlilik politikası adresi**: `magaza/gizlilik-politikasi.md` taslak. Hukuki gözden geçirmeden sonra claudian.app'te
   bir sayfaya konmalı (ör. `claudian.app/yuzuk/gizlilik`). Yayına ben koymadım.
4. **Veri güvenliği formu**: aşağıdaki cevaplar taslak; politika metniyle aynı olmalı.
5. **Hassas izin beyanları**: mikrofon ve ön plan hizmeti (mikrofon) için Console'da gerekçe ve kısa bir video istenebilir
   (kilitli ekranda kaydın sürdüğünü gösteren 20–30 sn'lik ekran kaydı).

## İzinler ve gerekçeleri

| İzin | Neden |
|---|---|
| `RECORD_AUDIO` | Kullanıcı halkaya dokunduğunda konuşmayı kaydeder. Kayıt yalnız kullanıcı başlatınca, bildirim görünürken yapılır. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Ekran kilitliyken kayıt sürer; sürekli bildirim kullanıcıya kaydın açık olduğunu söyler. |
| `POST_NOTIFICATIONS` | "Not hazır" bildirimi ve hatırlatıcılar. |
| `RECEIVE_BOOT_COMPLETED` | Telefon yeniden başlayınca kurulu hatırlatıcılar geri kurulur. |
| `INTERNET` | Kaydın buluta (yuzuk-api.claudian.app) gönderilmesi ve notun alınması. |
| `VIBRATE` (Haptics eklentisi) | Kayıt başladı/durdu dokunsal geri bildirimi. |

## Veri güvenliği formu (taslak cevaplar)

| Veri türü | Toplanıyor mu? | Paylaşılıyor mu? | Not |
|---|---|---|---|
| Ses kaydı | Evet, geçici | Hayır | Bulut katmanından işlem düğümüne akarak geçer; yazıya dökülünce silinir. Bulutta saklanmaz. |
| Konuşma metni (döküm) | Evet, geçici | **Evet — kullanıcının seçtiği AI sağlayıcısına** (Anthropic / OpenAI / Google) | Mahrem cümleler önceden çıkarılır. Bugünkü beta düğümünde kullanıcının kendi hesabı kullanılıyor; bulut GPU düğümüne geçince sağlayıcı sözleşmesi ve bu satır yeniden değerlendirilmeli. |
| Ses izi (biyometrik) | Evet, isteğe bağlı | Hayır | Yalnız "Sesini tanıt" açılırsa; işlem düğümünde durur, kullanıcı Ayarlar'dan siler. **Biyometrik veri: KVKK/GDPR açık rıza metni gerekir.** |
| Cihaz kimliği (uygulama içi) | Evet | Hayır | Eşleştirme anahtarı; yalnız özeti saklanır. |
| Kullanım (kayıt sayısı, süre) | Evet | Hayır | Hesap ekranındaki aylık kullanım. |
| Veriler şifreli aktarılıyor mu? | Evet (HTTPS) | | |
| Kullanıcı silme isteyebilir mi? | Evet | | Cihaz iptali ve ses izi silme uygulamada; hesap silme için e-posta yolu tanımlanmalı. |

## Mağaza metni (taslak)

**Kısa açıklama (TR, ≤80):** Konuşulanı dinler, anlamını Obsidian'a not olarak yazar.

**Kısa açıklama (EN, ≤80):** Listens to the conversation and writes what it meant into Obsidian.

**Uzun açıklama (TR):**
Yüzük, ders, toplantı ya da sohbet sırasında telefonunu cebine koyduğunda dinler ve konuşmanın anlamını senin
Obsidian vault'una not olarak yazar: ne konuşuldu, kimler vardı, neyin altı çizildi, sen neye söz verdin.
Cümleleri kopyalamaz; "selam ben Nil" nota "Nil ile tanıştın" olarak girer. Sesini bir kez tanıtırsan notlar senin
söylediklerini ayrı tutar. Tarihli işler telefonunda hatırlatıcı olur. Başkalarının özel bilgisi nota yazılmaz; ses
not yazılınca silinir. Notu hangi yapay zekânın yazacağını sen seçersin.

## Yayın sırası önerisi

1. Hesap → kapalı test kanalı ("beta") → AAB yükle → 12 test kullanıcısı.
2. 14 gün boyunca gerçek kullanım; hata ve not kalitesi geri bildirimi.
3. Gizlilik politikası yayında + veri güvenliği formu → üretime başvuru.
