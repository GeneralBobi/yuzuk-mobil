// 0.15.0 (22.11.2026, Boran): "Bağlantılar". Claudian masaüstündeki "AI bağlantıları" ekranının telefondaki karşılığı.
// Üç sekme: bilgisayarla · yalnız telefonla · bulutla. Seçili mod açılır ama öteki modların neye bağlı olacağı da
// görülebilir. Altta tek MCP adresi: telefonun kendi adresi üç modda da aynıdır, mod değişince AI'daki bağlayıcı değişmez.
import { useEffect, useState } from "react";
import { Check, Copy } from "lucide-react";
import { hat } from "./hat";
import { yerel, type DogrudanDurum } from "./yerel";
import { baglantiKartlari, type Kart } from "./baglantilar";
import type { Mod } from "./mod";

const SEKMELER: { mod: Mod; ad: string }[] = [
  { mod: "pc", ad: "Bilgisayarla" },
  { mod: "mobil", ad: "Yalnız telefonla" },
  { mod: "bulut", ad: "Bulutla" },
];

function KartGorunumu({ k }: { k: Kart }) {
  return (
    <div className={`bag-kart ${k.durum}`}>
      <div className="bag-kart-bas">
        <strong>{k.ad}</strong>
        <span className={`bag-isaret ${k.durum}`} aria-label={k.durum === "hazir" ? "hazır" : k.durum === "bekliyor" ? "bekliyor" : "eksik"}>
          {k.durum === "hazir" ? <Check size={14} strokeWidth={2.4} /> : "!"}
        </span>
      </div>
      <span className="bag-satir">{k.satir}</span>
      {k.ipucu && k.durum !== "hazir" && <span className="bag-ipucu">{k.ipucu}</span>}
    </div>
  );
}

export function Baglantilar() {
  const secili = hat.ayarlar.mod ?? "mobil";
  const [sekme, setSekme] = useState<Mod>(secili);
  const [d, setD] = useState<DogrudanDurum | null>(null);
  const [kopya, setKopya] = useState(false);
  useEffect(() => setSekme(secili), [secili]);
  useEffect(() => {
    let bitti = false;
    const yenile = () => yerel.dogrudanDurum().then((x) => { if (!bitti) setD(x); }).catch(() => undefined);
    void yenile();
    const t = setInterval(yenile, 4000);
    return () => { bitti = true; clearInterval(t); };
  }, []);

  const a = hat.ayarlar;
  const m = hat.model;
  const kartlar = baglantiKartlari(sekme, {
    eslesti: !!a.anahtar,
    bilgisayar: hat.baglanti.durum,
    bilgisayarEtiket: hat.baglanti.etiket,
    vault: a.vault ? `${a.vaultAdi ?? "vault"}/${a.altKlasor}` : null,
    yazici: a.yazici || "claude",
    kuruluYazicilar: hat.kuruluYazicilar,
    yaziciDurum: hat.yaziciDurum,
    sesIzi: !!hat.sesIzi?.var,
    model: m ? { hazir: m.hazir, uyumsuz: m.uyumlu === false, indiriliyor: m.indiriliyor, ad: m.model ?? "small", oran: m.hiz && m.hiz.model === m.model ? m.hiz.oran : undefined } : null,
    dogrudan: d ? { acik: d.acik, baglanti: d.baglanti, izinler: d.izinler } : null,
    bulut: !a.anahtar ? "hesapsiz" : hat.bulutDurumu,
  });
  const adres = d?.adresler.claude ?? null;

  const kopyala = async () => {
    if (!adres) return;
    try { await navigator.clipboard.writeText(adres); setKopya(true); window.setTimeout(() => setKopya(false), 2000); } catch { /* pano yok */ }
  };

  return (
    <div className="satir">
      <div className="satir-bas">
        <h2>Bağlantılar</h2>
        <span className="etiket soluk">{sekme === secili ? "şu an kullanılan" : "geçersen"}</span>
      </div>
      <div className="bag-sekmeler" role="tablist" aria-label="Mod">
        {SEKMELER.map((s) => (
          <button key={s.mod} role="tab" aria-selected={sekme === s.mod} className="bag-sekme" onClick={() => {
            setSekme(s.mod);
            if (s.mod === "bulut") void hat.bulutDene();
            if (s.mod !== "pc" && !hat.model) void hat.modelYukle();
          }}>
            {s.ad}{s.mod === "bulut" && <small> beta</small>}
          </button>
        ))}
      </div>
      <div className="bag-kartlar">
        {kartlar.map((k) => <KartGorunumu key={k.ad} k={k} />)}
      </div>
      <div className="bag-adres">
        <span className="etiket-satir">MCP adresi · üç modda aynı</span>
        {adres ? (
          <>
            <p className="deger mono">{adres.replace("https://", "")}</p>
            <button className="metin-dugme kor" onClick={() => void kopyala()}>
              <Copy size={13} style={{ verticalAlign: "-2px", marginRight: 6 }} />{kopya ? "Kopyalandı" : "Adresi kopyala"}
            </button>
          </>
        ) : (
          <p className="aciklama">Aşağıda "AI'lara doğrudan bağlan" açılınca adres burada görünür. Mod değiştirince Claude ya da ChatGPT'deki bağlayıcıyı değiştirmen gerekmez.</p>
        )}
      </div>
      {sekme !== secili && (
        <div className="satir-eylem">
          <button className="dugme" onClick={() => hat.modSec(sekme)}>{SEKMELER.find((s) => s.mod === sekme)?.ad} kullan</button>
        </div>
      )}
    </div>
  );
}
