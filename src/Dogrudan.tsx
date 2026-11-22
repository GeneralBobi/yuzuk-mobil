// 0.13.0 (15.11.2026): "AI'lara doğrudan bağlan". Eşleştirmenin yerine geçmez; ikinci bir yoldur.
// Telefon kendisi Claudian cihazı olur: Claude ve ChatGPT relay.claudian.app üzerinden telefondaki vault'a bağlanır.
// İşi Android tarafı yapar (HafizaServisi, DogrudanCihaz); bu ekran yalnız açar, kapatır ve durumu gösterir.
import { useEffect, useState } from "react";
import { hat } from "./hat";
import { Check } from "lucide-react";
import { yerel, type DogrudanDurum } from "./yerel";

/** Ham hata ("relay 500") kullanıcıya teknik kod olarak gösterilmez. Servis kendisi yeniden dener. */
export function hataMetni(h: string): string {
  const m = /^relay (\d{3})$/.exec(h.trim());
  if (m) {
    const k = Number(m[1]);
    if (k === 401) return "Bağlantı anahtarı reddedildi. Kapatıp yeniden aç.";
    if (k === 429) return "Bağlantı yoğun; birazdan yeniden denenecek.";
    if (k >= 500) return "Claudian bağlantı sunucusuna şu an ulaşılamıyor; telefon kendisi yeniden deniyor.";
    return `Bağlantı kurulamadı (${k}); yeniden deneniyor.`;
  }
  if (/Unable to resolve host|UnknownHost|timeout|timed out|failed to connect/i.test(h)) return "İnternet yok ya da zayıf; bağlanınca kendiliğinden devam eder.";
  return h;
}

const AI_ADI: Record<string, string> = { claude: "Claude", chatgpt: "ChatGPT" };
const BAGLAYICI_SAYFASI: Record<string, string> = {
  claude: "https://claude.ai/settings/connectors",
  chatgpt: "https://chatgpt.com/#settings/Connectors",
};

export function Dogrudan() {
  const [d, setD] = useState<DogrudanDurum | null>(null);
  const [hata, setHata] = useState<string | null>(null);
  const [kopyalandi, setKopyalandi] = useState<string | null>(null);
  const vault = hat.ayarlar.vault;

  useEffect(() => {
    let bitti = false;
    const yenile = () => yerel.dogrudanDurum().then((x) => { if (!bitti) setD(x); }).catch(() => undefined);
    // Açık bırakılmış bağlantının servisi uygulama açılınca yeniden başlar.
    void yerel.dogrudanAyarla(vault ? { vault } : {}).then((x) => { if (!bitti) setD(x); }).catch(() => undefined);
    const t = setInterval(yenile, 2500);
    return () => { bitti = true; clearInterval(t); };
  }, [vault]);

  const yap = async (f: () => Promise<DogrudanDurum>) => {
    setHata(null);
    try { setD(await f()); } catch (e) { setHata(e instanceof Error ? e.message : String(e)); }
  };

  const bagla = async (ai: string) => {
    if (!d?.adresler[ai as "claude"]) return;
    await yap(() => yerel.dogrudanAyarla({ acik: true, vault, pencere: true }));
    const adres = (await yerel.dogrudanDurum()).adresler[ai as "claude"] ?? "";
    try { await navigator.clipboard.writeText(adres); setKopyalandi(ai); } catch { setKopyalandi(null); }
    window.open(BAGLAYICI_SAYFASI[ai], "_blank");
  };

  const kopyala = async (ai: string, adres: string) => {
    try { await navigator.clipboard.writeText(adres); setKopyalandi(ai); } catch { /* pano yok */ }
  };

  const acik = !!d?.acik;
  const etiket = !acik ? "kapalı" : d?.baglanti === "cevrimici" ? "açık" : d?.baglanti === "cevrimdisi" ? "bağlantı yok" : "bağlanıyor";

  return (
    <div className="satir">
      <div className="satir-bas">
        <h2>AI'lara doğrudan bağlan</h2>
        <span className={`etiket ${acik && d?.baglanti === "cevrimici" ? "" : "soluk"}`}>{etiket}</span>
      </div>
      <p className="aciklama">Bilgisayar gerekmez. Claude ve ChatGPT bu telefondaki Obsidian klasörüne bağlanır. Adres üç modda da aynıdır.</p>
      {!vault ? (
        <p className="aciklama">Önce yukarıdan Obsidian klasörünü seç.</p>
      ) : !acik ? (
        <div className="satir-eylem">
          <button className="dugme birincil" onClick={() => void yap(() => yerel.dogrudanAyarla({ acik: true, vault }))}>Aç</button>
        </div>
      ) : (
        <>
          {/* 0.15.0 (Boran, 22.11): Claudian masaüstündeki "AI bağlantıları" gibi: her AI bir kart, durum tek bakışta. */}
          <div className="bag-kartlar">
            {(["claude", "chatgpt"] as const).map((ai) => {
              const izin = d?.izinler.find((g) => g.ai === ai);
              const cevrimici = d?.baglanti === "cevrimici";
              const durum = !izin ? "eksik" : cevrimici ? "hazir" : "bekliyor";
              return (
                <div key={ai} className={`bag-kart ${durum}`}>
                  <div className="bag-kart-bas">
                    <strong>{AI_ADI[ai]}</strong>
                    <span className={`bag-isaret ${durum}`}>{durum === "hazir" ? <Check size={14} strokeWidth={2.4} /> : "!"}</span>
                  </div>
                  <span className="bag-satir soluk-mono">{izin ? "Seçili · Bağlı" : "Bağlı değil"}</span>
                  <span className={`bag-durum ${durum}`}>{!izin ? "Kurulum tamamlanmadı" : cevrimici ? "Kullanıma hazır" : "Telefon bağlanıyor"}</span>
                  {izin && <span className="bag-ipucu">{izin.kapsam.includes("write") ? "Okur ve yazar" : "Yalnız okur"}</span>}
                  <div className="bag-eylem">
                    {!izin && d?.adresler[ai] && <button className="dugme kucuk" onClick={() => void bagla(ai)}>Bağla</button>}
                    {d?.adresler[ai] && <button className="metin-dugme kor" onClick={() => void kopyala(ai, d.adresler[ai]!)}>{kopyalandi === ai ? "Kopyalandı" : "Adresi kopyala"}</button>}
                    {izin && <button className="metin-dugme" onClick={() => void yap(() => yerel.dogrudanIzinKaldir({ id: izin.id }))}>Kaldır</button>}
                  </div>
                </div>
              );
            })}
          </div>
          {d && d.pencere > 0 && (
            <p className="aciklama">Adres kopyalandı. {AI_ADI[kopyalandi ?? "claude"] ?? "AI"} ayarlarında "Özel bağlayıcı ekle"ye yapıştır; {Math.ceil(d.pencere / 60000)} dk içinde onay sormadan bağlanır.</p>
          )}
          {d?.bekleyen.map((b) => (
            <div key={b.id} className="uyari-kutu" role="alert">
              <p className="aciklama"><b>{b.ad}</b> bağlanmak istiyor · kod <span className="mono">{b.kod}</span> · {b.hedef}</p>
              <div className="satir-eylem">
                <button className="dugme birincil" onClick={() => void yap(() => yerel.dogrudanKarar({ id: b.id, evet: true }))}>Onayla</button>
                <button className="metin-dugme" onClick={() => void yap(() => yerel.dogrudanKarar({ id: b.id, evet: false }))}>Reddet</button>
              </div>
            </div>
          ))}
          {d?.hata && d.baglanti !== "cevrimici" && <p className="aciklama">{hataMetni(d.hata)}</p>}
          <div className="satir-eylem">
            <button className="metin-dugme kor" onClick={() => void yap(() => yerel.dogrudanAyarla({ yazma: !d?.yazma }))}>
              {d?.yazma ? "Yazmayı kapat" : "Yazmaya izin ver"}
            </button>
            <button className="metin-dugme" onClick={() => void yap(() => yerel.dogrudanAyarla({ acik: false }))}>Kapat</button>
          </div>
        </>
      )}
      {hata && <p className="hata">{hata}</p>}
    </div>
  );
}
