import { useEffect, useState, useSyncExternalStore } from "react";
import { App as CapApp } from "@capacitor/app";
import { ArrowLeft, ArrowRight, Check, FileAudio, Mic, NotebookPen, Settings, Square } from "lucide-react";
import { durak, hat, YAZICILAR, type Oturum } from "./hat";
import { Halka } from "./Halka";
import { yerel, type Paylasim } from "./yerel";
import { Haptics, ImpactStyle } from "@capacitor/haptics";

type Sekme = "dinle" | "notlar" | "ayarlar";
type Akis = "ses" | null;

function useHat() {
  return useSyncExternalStore((d) => hat.abone(d), () => surum(), () => 0);
}
// useSyncExternalStore değişimi anlasın diye her yayında artan bir sayaç.
let _surum = 0;
hat.abone(() => void _surum++);
const surum = () => _surum;

const iki = (n: number) => String(n).padStart(2, "0");
function sure(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(s / 3600);
  return h ? `${h}:${iki(Math.floor(s / 60) % 60)}:${iki(s % 60)}` : `${iki(Math.floor(s / 60))}:${iki(s % 60)}`;
}
const saat = (iso: string) => {
  const d = new Date(iso);
  return `${iki(d.getHours())}:${iki(d.getMinutes())}`;
};
function gunAdi(iso: string) {
  const d = new Date(iso);
  const bugun = new Date();
  const dun = new Date(Date.now() - 864e5);
  if (d.toDateString() === bugun.toDateString()) return "Bugün";
  if (d.toDateString() === dun.toDateString()) return "Dün";
  return d.toLocaleDateString("tr-TR", { day: "numeric", month: "long", weekday: "long" });
}

// Ekran geçişlerinde animasyon yok (24.09.2026): Boran'ın telefonunda çıkış animasyonu tamamlanmadı, yeni ekran hiç
// açılmadı ve siyah ekran kaldı. Ekran anında değişir; hiçbir içerik bir animasyonun bitmesini beklemez.

export function Uygulama() {
  useHat();
  const [sekme, setSekme] = useState<Sekme>(() => (hat.hazir ? "dinle" : "ayarlar"));
  const [akis, setAkis] = useState<Akis>(null);

  useEffect(() => {
    void hat.baslat();
    const linkIsle = (url?: string | null) => {
      const kod = url ? new URL(url.replace("yuzuk://", "https://yuzuk/")).searchParams.get("kod") : null;
      if (kod) {
        setSekme("ayarlar");
        void hat.eslestir(kod);
      }
    };
    void CapApp.getLaunchUrl().then((r) => linkIsle(r?.url));
    const dinleyici = CapApp.addListener("appUrlOpen", (e) => linkIsle(e.url));
    const on = CapApp.addListener("resume", () => {
      void hat.ilerlet();
      void hat.baglantiDene();
    });
    // "Paylaş → Yüzük": açılışta bekleyen paylaşım bir kez alınır, uygulama açıkken gelenler dinlenir.
    const paylas = (p: Paylasim) => {
      hat.paylasimIsle(p);
      if (p.yol) setSekme("notlar");
    };
    void yerel.paylasimAl().then(paylas).catch(() => undefined);
    const paylasim = yerel.addListener("paylasim", paylas);
    return () => {
      void dinleyici.then((d) => d.remove());
      void on.then((d) => d.remove());
      void paylasim.then((d) => d.remove());
    };
  }, []);

  // Android Geri: önce açık akış kapanır, sonra Dinle'ye dönülür, en son uygulama arka plana gider.
  useEffect(() => {
    const geri = CapApp.addListener("backButton", () => {
      if (akis) {
        if (hat.sesKaydi === "kayitta") void hat.sesKaydiBitir(true);
        setAkis(null);
      } else if (sekme !== "dinle") setSekme("dinle");
      else void CapApp.minimizeApp();
    });
    return () => void geri.then((d) => d.remove());
  }, [akis, sekme]);

  useEffect(() => {
    if (sekme === "ayarlar") void hat.hesapYukle();
  }, [sekme]);

  // Her ekran en üstten açılır (önceki ekranın kaydırması taşınmaz).
  // Gövde süslü parantezli olmalı: yeni Chromium'da scrollTo bir Promise döndürüyor ve efektten dönen değer React'e
  // temizleme fonksiyonu sanılıyor ("l is not a function", 24.09.2026 Boran'ın telefonunda her ekranda çöktü).
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [sekme, akis]);

  const bekleyen = hat.oturumlar.filter((o) => o.durum !== "yazildi").length;

  return (
    <>
      <div className="kabuk">
        <header className="ust-cubuk">
          <span className="kelime-isareti" aria-label="claudian.app">claudian<span>.</span>app</span>
          <span className="alt-yazi">Yüzük</span>
        </header>
        <main className="icerik">
          <div key={akis ?? sekme}>
              {akis === "ses" ? <SesiniTanit kapat={() => setAkis(null)} />
                : sekme === "dinle" ? <Dinle git={setSekme} />
                : sekme === "notlar" ? <Notlar />
                : <Ayarlar ac={setAkis} />}
          </div>
        </main>
        {!akis && (
          <nav className="altbar" aria-label="Gezinme">
            <SekmeDugmesi etiket="Dinle" aktif={sekme === "dinle"} onClick={() => setSekme("dinle")} ikon={<Mic size={20} />}
              isaret={hat.kaydediyor && sekme !== "dinle"} />
            <SekmeDugmesi etiket="Notlar" aktif={sekme === "notlar"} onClick={() => setSekme("notlar")} ikon={<NotebookPen size={20} />}
              rozet={bekleyen || undefined} />
            <SekmeDugmesi etiket="Ayarlar" aktif={sekme === "ayarlar"} onClick={() => setSekme("ayarlar")} ikon={<Settings size={20} />}
              isaret={!hat.hazir} />
          </nav>
        )}
      </div>
    </>
  );
}

function SekmeDugmesi(p: { etiket: string; aktif: boolean; onClick: () => void; ikon: React.ReactNode; rozet?: number; isaret?: boolean }) {
  return (
    <button className={`sekme ${p.aktif ? "aktif" : ""}`} onClick={p.onClick} aria-current={p.aktif ? "page" : undefined}>
      <span className="sekme-ikon">
        {p.ikon}
        {p.rozet ? <span className="rozet">{p.rozet}</span> : p.isaret ? <span className="isaret" /> : null}
      </span>
      <span>{p.etiket}</span>
    </button>
  );
}

// ---------------------------------------------------------------- Dinle

function useSaniye(acik: boolean) {
  const [, tikla] = useState(0);
  useEffect(() => {
    if (!acik) return;
    const t = window.setInterval(() => tikla((x) => x + 1), 1000);
    return () => window.clearInterval(t);
  }, [acik]);
}

function Dinle({ git }: { git: (s: Sekme) => void }) {
  useSaniye(hat.kaydediyor);
  const son = hat.oturumlar[0];
  const bugun = new Date().toLocaleDateString("tr-TR", { weekday: "long", day: "numeric", month: "long" });
  const eksik = !hat.ayarlar.anahtar ? "Telefonu eşleştir ve Obsidian klasörünü seç." : "Obsidian klasörünü seç.";

  return (
    <section className="dinle">
      {!hat.hazir && (
        <button className="kurulum-satiri" onClick={() => git("ayarlar")}>
          <span>
            <strong>Kurulum bitmedi</strong>
            <span>{eksik} Kayıt yine de alınır; not kurulum bitince yazılır.</span>
          </span>
          <ArrowRight size={18} />
        </button>
      )}

      <div className="halka-alani">
        <Halka kayitta={hat.kaydediyor} seviye={hat.seviye} okuma={hat.okuma} etiket={hat.kaydediyor ? "Kaydı durdur" : "Dinlemeye başla"}
          onClick={() => {
            // Telefon dokunur dokunmaz cebe girer: başladığı ve durduğu dokunuşla da anlaşılsın.
            void Haptics.impact({ style: hat.kaydediyor ? ImpactStyle.Light : ImpactStyle.Medium }).catch(() => undefined);
            void (hat.kaydediyor ? hat.kayitDurdur() : hat.kayitBaslat());
          }}>
          {hat.kaydediyor ? (
            <>
              <span className="sayac">{sure(Date.now() - (hat.kayitBaslangic ?? Date.now()))}</span>
              <span className="kucuk">dinliyor</span>
              <Square size={16} fill="currentColor" style={{ color: "var(--kor)", marginTop: 6 }} />
            </>
          ) : (
            <>
              <Mic size={30} strokeWidth={1.6} />
              <span className="buyuk">Dinle</span>
              <span className="kucuk">{bugun}</span>
            </>
          )}
        </Halka>
      </div>
      <p className="aciklama halka-durum">
        {hat.kaydediyor
          ? "Ekranı kilitleyebilirsin. Durdurmak için halkaya dokun; not Obsidian'a kendiliğinden düşer."
          : "Halkaya dokun ve telefonu bırak. Durdurduğunda konuşmanın notu Obsidian'a yazılır."}
      </p>

      {hat.uyari && <p className="hata" role="alert">{hat.uyari}</p>}

      <button className="dugme genis" onClick={() => void hat.iceAktar()} disabled={hat.kaydediyor}>
        <FileAudio size={18} /> Dosyadan not çıkar
      </button>

      {son && <div className="liste bolum"><OturumSatiri o={son} /></div>}
    </section>
  );
}

// ---------------------------------------------------------------- Notlar

function Notlar() {
  const gruplar: { gun: string; o: Oturum[] }[] = [];
  for (const o of hat.oturumlar) {
    const g = gunAdi(o.baslangic);
    if (gruplar.at(-1)?.gun === g) gruplar.at(-1)!.o.push(o);
    else gruplar.push({ gun: g, o: [o] });
  }
  return (
    <section>
      <h1>Notlar</h1>
      <p className="aciklama">
        Her kayıt not olunca Obsidian'da <em>{hat.ayarlar.vaultAdi ?? "vault"}/{hat.ayarlar.altKlasor}</em> klasörüne yazılır; ses o an telefondan silinir.
      </p>
      {hat.oturumlar.length === 0 ? (
        <div className="bos">
          <p className="aciklama">Henüz kayıt yok. Dinle'deki halkaya dokunduğunda ilk kayıt burada belirir ve dört durağı geçer: telefonda, bulutta, yazılıyor, Obsidian'da.</p>
        </div>
      ) : (
        gruplar.map((g) => (
          <div key={g.gun}>
            <p className="gun">{g.gun}</p>
            <div className="liste">

                {g.o.map((o) => (
                  <div key={o.id}>
                    <OturumSatiri o={o} />
                  </div>
                ))}
            </div>
          </div>
        ))
      )}
    </section>
  );
}

const DURAKLAR = ["telefonda", "bulutta", "yazılıyor", "obsidian"];

function OturumSatiri({ o }: { o: Oturum }) {
  const [acik, setAcik] = useState(false);
  const hataVar = o.durum === "hata" || !!o.hata;
  const d = durak(o);
  const durumMetni = (() => {
    switch (o.durum) {
      case "yerel": return o.hata ? "gönderilemedi" : hat.hazir ? "gönderilecek" : "kurulum bekliyor";
      case "gonderiliyor": return "gönderiliyor";
      case "sunucuda": return (o.asama ?? "işleniyor").toLocaleLowerCase("tr");
      case "yazildi": return o.notAdi ? "obsidian'da" : "konuşma yok";
      default: return "hata";
    }
  })();
  const ad = o.baslik ?? (o.kaynak === "ice" ? "İçe aktarılan dosya" : "Kayıt");
  const meta = [
    o.kisi ? `${o.kisi} kişi` : null,
    o.senPayi !== undefined ? `sen %${Math.round(o.senPayi * 100)}` : null,
    o.hatirlatici ? `${o.hatirlatici} hatırlatıcı` : null,
    o.mahrem ? `${o.mahrem} cümle mahrem sayılıp çıkarıldı` : null,
    o.yazan ? (o.yedek ? `${o.yedek} çalışmadı, notu ${o.yazan} yazdı` : `notu ${o.yazan} yazdı`) : null,
  ].filter(Boolean);

  return (
    <div>
      <button className="oturum" onClick={() => setAcik((a) => !a)} aria-expanded={acik}>
        <span className="oturum-ust">
          <span className="mono">{saat(o.baslangic)}{o.sureSn ? ` · ${Math.max(1, Math.round(o.sureSn / 60))} dk` : ""}</span>
          <span className={`durum ${hataVar ? "hata-renk" : d === 3 && o.notAdi ? "kor" : d === 3 ? "soluk-renk" : ""}`}>{durumMetni}</span>
        </span>
        <span className="oturum-baslik" style={{ display: "block" }}>{ad}</span>
        {meta.length > 0 && <span className="oturum-meta" style={{ display: "block" }}>{meta.join(" · ")}</span>}
        {o.ozet && <span className="oturum-ozet" style={{ display: "block" }}>{o.ozet}</span>}
        {!(d === 3 && !hataVar) && (
          <span className="yol" aria-label={`Durum: ${durumMetni}`}>
            {DURAKLAR.map((ad, i) => (
              <span key={ad} className={`durak ${i < d ? "gecti" : i === d ? (hataVar ? "hata-durak" : "simdi") : ""}`}>
                <i />{ad}
              </span>
            ))}
          </span>
        )}
        {o.hata && <span className="oturum-hata" style={{ display: "block" }}>{o.hata}</span>}
      </button>
      {acik && (
        <div className="oturum-eylem">
          {o.durum !== "yazildi" && <button className="metin-dugme kor" onClick={() => void hat.tekrarDene(o)}>Tekrar dene</button>}
          <button className="metin-dugme" onClick={() => void hat.kaldir(o)}>Listeden kaldır</button>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- Ayarlar

function Ayarlar({ ac }: { ac: (a: Akis) => void }) {
  const [kod, setKod] = useState("");
  const a = hat.ayarlar;
  const eslesti = !!a.anahtar;
  const b = hat.baglanti;
  const iz = hat.sesIzi;

  return (
    <section>
      <h1>Ayarlar</h1>
      <p className="aciklama">Kayıt telefondan buluta, oradan bilgisayarındaki işlem düğümüne gider; not buraya döner ve Obsidian'a yazılır.</p>

      <div className="satirlar">
        <div className="satir">
          <div className="satir-bas">
            <h2>Bağlantı</h2>
            <span className={`etiket ${b.durum === "bagli" ? "" : "soluk"}`}>
              {!eslesti ? "eşleşmedi" : b.durum === "bagli" ? "bağlı" : b.durum === "bilinmiyor" ? "bakılıyor" : "ulaşılamıyor"}
            </span>
          </div>
          {eslesti ? (
            <>
              <p className="deger"><span className={`nokta ${b.durum === "bagli" ? "acik" : ""}`} />{a.sunucu.replace("https://", "")}</p>
              {b.metin && b.durum !== "bagli" && <p className="aciklama">{b.metin}</p>}
              <div className="satir-eylem">
                <button className="metin-dugme kor" onClick={() => void hat.baglantiDene()}>Bağlantıyı dene</button>
              </div>
            </>
          ) : (
            <>
              <p className="aciklama">Bilgisayarındaki Claudian'da Yüzük → Telefon uygulaması → Telefonu eşleştir. QR'ı okut ya da koddaki sekiz harfi yaz.</p>
              <div className="kod-satiri">
                <input className="girdi" value={kod} onChange={(e) => setKod(e.target.value.toUpperCase())} placeholder="ABCD2345"
                  autoCapitalize="characters" autoCorrect="off" spellCheck={false} aria-label="Eşleştirme kodu" />
                <button className="dugme birincil" disabled={kod.replace(/[^A-Z0-9]/g, "").length < 8} onClick={() => void hat.eslestir(kod)}>Eşleştir</button>
              </div>
            </>
          )}
          {hat.eslestirme && hat.eslestirme.durum !== "tamam" && (
            <p className={hat.eslestirme.durum === "hata" ? "hata" : "aciklama"}>{hat.eslestirme.metin}</p>
          )}
        </div>

        <div className="satir">
          <div className="satir-bas">
            <h2>Obsidian vault'u</h2>
            <span className={`etiket ${a.vault ? "" : "soluk"}`}>{a.vault ? "seçildi" : "seçilmedi"}</span>
          </div>
          {a.vault ? <p className="deger">{a.vaultAdi}/{a.altKlasor}</p>
            : <p className="aciklama">Telefondaki vault klasörünü seç (ör. <em>Claudian</em>). Notlar içindeki <em>{a.altKlasor}</em> klasörüne yazılır.</p>}
          <div className="satir-eylem">
            <button className={a.vault ? "metin-dugme kor" : "dugme birincil"} onClick={() => void hat.vaultSec()}>{a.vault ? "Değiştir" : "Klasörü seç"}</button>
          </div>
        </div>

        {a.vault && eslesti && (
          <div className="satir">
            <div className="satir-bas">
              <h2>Bilgisayarla eşitle</h2>
              <span className={`etiket ${hat.esitleme.durum === "bitti" ? "" : "soluk"}`}>
                {hat.esitleme.durum === "calisiyor" ? "eşitleniyor" : hat.esitleme.son ? `son: ${gunAdi(hat.esitleme.son).toLocaleLowerCase("tr")} ${saat(hat.esitleme.son)}` : "hiç eşitlenmedi"}
              </span>
            </div>
            <p className="aciklama">
              Telefondaki vault ile bilgisayarındaki vault karşılıklı eşitlenir. Hangi tarafta değiştiyse o kazanır; ikisi de değiştiyse
              daha yeni olan. Hiçbir not silinmez, üzerine yazılan sürüm bilgisayarda yedeklenir. Notlar buluttan akarak geçer, saklanmaz.
            </p>
            {hat.esitleme.ozet && (
              <p className="deger">
                bilgisayar {hat.esitleme.ozet.pc} not · telefon {hat.esitleme.ozet.tel} not
                {hat.esitleme.ozet.pcEnYeni || hat.esitleme.ozet.telEnYeni
                  ? ` · en yeni değişiklik ${hat.esitleme.ozet.pcEnYeni >= hat.esitleme.ozet.telEnYeni ? "bilgisayarda" : "telefonda"}` : ""}
              </p>
            )}
            {hat.esitleme.metin && (
              <p className={hat.esitleme.durum === "hata" ? "hata" : "aciklama"} style={{ marginTop: 6 }}>{hat.esitleme.metin}</p>
            )}
            <div className="satir-eylem">
              <button className="dugme" disabled={hat.esitleme.durum === "calisiyor"} onClick={() => void hat.esitle()}>
                {hat.esitleme.durum === "calisiyor" ? "Eşitleniyor…" : "Şimdi eşitle"}
              </button>
            </div>
          </div>
        )}

        <div className="satir">
          <div className="satir-bas">
            <h2>Sesin</h2>
            <span className={`etiket ${iz?.var ? "" : "soluk"}`}>{iz?.var ? "tanındı" : "tanıtılmadı"}</span>
          </div>
          <p className="aciklama">
            {iz?.var
              ? "Notlarda senin söylediklerin ayrı tutulur: verdiğin sözler, sorduğun sorular."
              : "Sesini bir kez tanıtırsan notlar kimin ne dediğini bilir ve senin söylediklerini ayırır."}
          </p>
          {iz?.var && iz.olusturuldu && (
            <p className="deger">{new Date(iz.olusturuldu).toLocaleDateString("tr-TR")} · {iz.konusma_sn} sn okuma</p>
          )}
          <div className="satir-eylem">
            <button className={iz?.var ? "metin-dugme kor" : "dugme"} disabled={!eslesti} onClick={() => ac("ses")}>
              {iz?.var ? "Yeniden tanıt" : "Sesini tanıt"}
            </button>
            {iz?.var && <button className="metin-dugme" onClick={() => void hat.sesIziSil()}>Ses izini sil</button>}
          </div>
        </div>

        <div className="satir">
          <h2>Notu yazan</h2>
          <div className="secim" role="radiogroup" aria-label="Notu yazan AI">
            {Object.entries(YAZICILAR).map(([id, ad]) => {
              const kurulu = !hat.kuruluYazicilar || hat.kuruluYazicilar.includes(id);
              return (
                <button key={id} role="radio" aria-checked={a.yazici === id} disabled={!kurulu}
                  className="secenek" onClick={() => hat.ayarla({ yazici: id })}>
                  {ad}{!kurulu && <small>kurulu değil</small>}
                </button>
              );
            })}
          </div>
          <p className="aciklama" style={{ marginTop: 10 }}>Konuşmanın tamamı bu araca gider, notu o yazar. Mahrem cümleler önce çıkarılır. Araç çalışmazsa kurulu olan diğeri yazar.</p>
        </div>

        {hat.hesap && (
          <div className="satir">
            <div className="satir-bas">
              <h2>Hesap</h2>
              <span className="etiket soluk mono">bu ay {hat.hesap.bu_ay.is_sayisi} kayıt · {hat.hesap.bu_ay.dakika} dk</span>
            </div>
            <div style={{ marginTop: 10 }}>
              {hat.hesap.cihazlar.map((c) => (
                <div className="cihaz" key={c.id}>
                  <span><b>{c.ad ?? "Cihaz"}</b>{c.bu ? " · bu telefon" : ""}</span>
                  {c.bu ? <span className="mono">{c.son_gorulme ? saat(c.son_gorulme) : ""}</span>
                    : <button className="metin-dugme" style={{ padding: "0 0 0 16px" }} onClick={() => void hat.cihazIptal(c.id)}>Kaldır</button>}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      <details className="gelismis">
        <summary>Gelişmiş</summary>
        <label htmlFor="sunucu">Bulut adresi</label>
        <input id="sunucu" className="girdi duz" defaultValue={a.sunucu} onBlur={(e) => hat.ayarla({ sunucu: e.target.value })} />
        <label htmlFor="alt">Alt klasör</label>
        <input id="alt" className="girdi duz" defaultValue={a.altKlasor} onBlur={(e) => hat.ayarla({ altKlasor: e.target.value.trim() || "Yüzük" })} />
      </details>

      <p className="dipnot">Ses yalnız not yazılana kadar telefonda durur; bulutta hiç saklanmaz. Ses izin yalnız bilgisayarında durur. Başkalarının özel bilgisi nota yazılmaz.</p>
    </section>
  );
}

// ---------------------------------------------------------------- Sesini tanıt

const EN_AZ_SN = 15;
const HEDEF_SN = 25;

function SesiniTanit({ kapat }: { kapat: () => void }) {
  useSaniye(hat.sesKaydi === "kayitta");
  const [bitti, setBitti] = useState(false);
  const gecen = hat.sesKaydi === "kayitta" && hat.kayitBaslangic ? (Date.now() - hat.kayitBaslangic) / 1000 : 0;

  useEffect(() => () => {
    if (hat.sesKaydi === "kayitta") void hat.sesKaydiBitir(true);
  }, []);

  const bitir = async () => {
    await hat.sesKaydiBitir();
    if (!hat.sesHatasi) setBitti(true);
  };

  if (bitti && hat.sesIzi?.var) {
    return (
      <section>
        <div className="sonuc-isareti"><Check size={30} /></div>
        <h1>Sesin tanındı</h1>
        <p className="aciklama">Bundan sonraki notlarda senin söylediklerin ayrı tutulur. Okuma kaydı silindi; yalnız ses izin kaldı.</p>
        <p className="deger mono" style={{ marginTop: 12, color: "var(--soluk)", fontSize: 12 }}>
          {hat.sesIzi.konusma_sn} sn konuşma · tutarlılık {Math.round((hat.sesIzi.tutarlilik ?? 0) * 100)}
        </p>
        {hat.sesIzi.uyari && <p className="hata">{hat.sesIzi.uyari}</p>}
        <div className="satir-eylem" style={{ marginTop: 24 }}>
          <button className="dugme birincil" onClick={kapat}>Tamam</button>
        </div>
      </section>
    );
  }

  return (
    <section>
      <div className="akis-bas">
        <button className="geri" onClick={kapat} aria-label="Geri"><ArrowLeft size={22} /></button>
      </div>
      <h1>Sesini tanıt</h1>
      <p className="aciklama">
        Aşağıdaki metni normal sesinle, sessiz bir yerde oku; yirmi beş saniye yeter. Kayıttan yalnız bir ses izi çıkarılır,
        kayıt hemen silinir. Ses izi bilgisayarında durur; istediğin an Ayarlar'dan silebilirsin.
      </p>
      <div className="okuma">
        <p>Sabah derse biraz geç kaldım; hoca konuyu anlatmaya başlamıştı bile. Arka sıraya oturup defterimi açtım ve tahtadaki şekli hızlıca çizdim.</p>
        <p>Öğle arasında Deniz'le kantinde buluştuk, hafta sonu için küçük bir plan yaptık. Akşam eve dönerken hem ödevimi hem de yarınki sunumu düşündüm.</p>
        <p>Unutmamak için kendime not aldım: perşembe saat üçte proje toplantısı var.</p>
      </div>

      <div className="ses-kaydi">
        <Halka kucuk kayitta={hat.sesKaydi === "kayitta"} seviye={hat.seviye} okuma={hat.okuma} ilerleme={Math.min(1, gecen / HEDEF_SN)}
          etiket={hat.sesKaydi === "kayitta" ? "Okumayı bitir" : "Okumaya başla"}
          onClick={() => void (hat.sesKaydi === "kayitta" ? (gecen >= EN_AZ_SN ? bitir() : undefined) : hat.sesKaydiBaslat())}>
          {hat.sesKaydi === "kayitta" ? <span className="sayac">{sure(gecen * 1000)}</span> : <Mic size={24} strokeWidth={1.6} />}
        </Halka>
        <div>
          {hat.sesKaydi === "yok" && <p className="aciklama">Halkaya dokun ve okumaya başla.</p>}
          {hat.sesKaydi === "kayitta" && (
            <p className="aciklama">{gecen < EN_AZ_SN ? `Oku; ${Math.ceil(EN_AZ_SN - gecen)} saniye daha.` : "Yeterli. Bitirmek için dokun ya da okumayı tamamla."}</p>
          )}
          {hat.sesKaydi === "isleniyor" && <p className="aciklama">Ses izi çıkarılıyor…</p>}
        </div>
      </div>
      {hat.sesKaydi === "kayitta" && gecen >= EN_AZ_SN && (
        <button className="dugme birincil genis" style={{ marginTop: 20 }} onClick={() => void bitir()} ref={(el) => el?.scrollIntoView({ block: "center", behavior: "smooth" })}>Bitti, sesimi tanıt</button>
      )}
      {hat.sesHatasi && <p className="hata" role="alert" ref={(el) => el?.scrollIntoView({ block: "center", behavior: "smooth" })}>{hat.sesHatasi}</p>}
    </section>
  );
}
