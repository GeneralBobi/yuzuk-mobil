import { useEffect, useState, useSyncExternalStore } from "react";
import { App as CapApp } from "@capacitor/app";
import {
  AlertCircle, Check, CircleDashed, FileAudio, FolderOpen, Link2, Mic, NotebookPen, RotateCw, Settings, Square, Trash2, UploadCloud,
} from "lucide-react";
import { hat, type Oturum } from "./hat";

type Sekme = "dinle" | "notlar" | "ayarlar";

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
function zaman(iso: string) {
  const d = new Date(iso);
  const bugun = new Date();
  const gun = d.toDateString() === bugun.toDateString() ? "Bugün" : `${iki(d.getDate())}.${iki(d.getMonth() + 1)}`;
  return `${gun} ${iki(d.getHours())}:${iki(d.getMinutes())}`;
}

export function Uygulama() {
  useHat();
  const [sekme, setSekme] = useState<Sekme>(() => (hat.hazir ? "dinle" : "ayarlar"));

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
    const on = CapApp.addListener("resume", () => void hat.ilerlet());
    return () => {
      void dinleyici.then((d) => d.remove());
      void on.then((d) => d.remove());
    };
  }, []);

  return (
    <div className="kabuk">
      <main className="icerik">
        {sekme === "dinle" && <Dinle git={setSekme} />}
        {sekme === "notlar" && <Notlar />}
        {sekme === "ayarlar" && <Ayarlar />}
      </main>
      <nav className="altbar" aria-label="Gezinme">
        <SekmeDugmesi etiket="Dinle" aktif={sekme === "dinle"} onClick={() => setSekme("dinle")} ikon={<Mic size={20} />} />
        <SekmeDugmesi etiket="Notlar" aktif={sekme === "notlar"} onClick={() => setSekme("notlar")} ikon={<NotebookPen size={20} />}
          rozet={hat.oturumlar.filter((o) => o.durum !== "yazildi").length || undefined} />
        <SekmeDugmesi etiket="Ayarlar" aktif={sekme === "ayarlar"} onClick={() => setSekme("ayarlar")} ikon={<Settings size={20} />}
          nokta={!hat.hazir} />
      </nav>
    </div>
  );
}

function SekmeDugmesi(p: { etiket: string; aktif: boolean; onClick: () => void; ikon: React.ReactNode; rozet?: number; nokta?: boolean }) {
  return (
    <button className={`sekme ${p.aktif ? "aktif" : ""}`} onClick={p.onClick} aria-current={p.aktif ? "page" : undefined}>
      <span className="sekme-ikon">
        {p.ikon}
        {p.rozet ? <span className="rozet">{p.rozet}</span> : p.nokta ? <span className="nokta" /> : null}
      </span>
      <span>{p.etiket}</span>
    </button>
  );
}

// ---------------------------------------------------------------- Dinle

function Dinle({ git }: { git: (s: Sekme) => void }) {
  const [, tikla] = useState(0);
  useEffect(() => {
    if (!hat.kaydediyor) return;
    const t = window.setInterval(() => tikla((x) => x + 1), 1000);
    return () => window.clearInterval(t);
  }, [hat.kaydediyor]);

  const son = hat.oturumlar[0];
  const bugun = new Date().toLocaleDateString("tr-TR", { weekday: "long", day: "numeric", month: "long" });

  return (
    <section className="dinle">
      <header className="sayfa-basi">
        <p className="ust-yazi">{bugun}</p>
        <h1>Yüzük</h1>
      </header>

      {!hat.hazir && (
        <button className="kart uyari-kart" onClick={() => git("ayarlar")}>
          <AlertCircle size={18} />
          <span>
            <strong>Kurulum bitmedi.</strong> {hat.ayarlar.anahtar ? "Obsidian klasörünü seç." : "Telefonu eşleştir ve Obsidian klasörünü seç."}{" "}
            Kayıt yapabilirsin; notlar kurulum bitince yazılır.
          </span>
        </button>
      )}

      <div className="kaydedici">
        <button className={`kayit-dugmesi ${hat.kaydediyor ? "kayitta" : ""}`}
          onClick={() => void (hat.kaydediyor ? hat.kayitDurdur() : hat.kayitBaslat())}
          aria-label={hat.kaydediyor ? "Kaydı durdur" : "Dinlemeye başla"}>
          {hat.kaydediyor ? <Square size={34} fill="currentColor" /> : <Mic size={40} />}
        </button>
        <div className="kayit-bilgi">
          {hat.kaydediyor ? (
            <>
              <span className="sayac">{sure(Date.now() - (hat.kayitBaslangic ?? Date.now()))}</span>
              <span className="seviye" aria-hidden><span style={{ width: `${Math.round(Math.min(1, hat.seviye * 1.6) * 100)}%` }} /></span>
              <span className="ikincil">Dinliyor. Ekranı kilitleyebilirsin.</span>
            </>
          ) : (
            <>
              <span className="baslik-kucuk">Dinlemeye başla</span>
              <span className="ikincil">Durdurduğunda not Obsidian'a düşer.</span>
            </>
          )}
        </div>
      </div>

      {hat.uyari && <p className="hata-metni">{hat.uyari}</p>}

      <button className="dugme ikincil-dugme" onClick={() => void hat.iceAktar()} disabled={hat.kaydediyor}>
        <FileAudio size={18} /> Ses ya da video dosyasından not çıkar
      </button>

      {son && (
        <div className="bolum">
          <h2 className="bolum-basligi">Son oturum</h2>
          <OturumKarti o={son} />
        </div>
      )}
    </section>
  );
}

// ---------------------------------------------------------------- Notlar

function Notlar() {
  return (
    <section>
      <header className="sayfa-basi">
        <h1>Notlar</h1>
        <p className="ikincil">Her oturum not olunca Obsidian'da <em>{hat.ayarlar.altKlasor}</em> klasörüne yazılır.</p>
      </header>
      {hat.oturumlar.length === 0 ? (
        <p className="bos">Henüz oturum yok. Dinle sekmesinden bir kayıt başlat.</p>
      ) : (
        <div className="liste">{hat.oturumlar.map((o) => <OturumKarti key={o.id} o={o} />)}</div>
      )}
    </section>
  );
}

function OturumKarti({ o }: { o: Oturum }) {
  const [menu, setMenu] = useState(false);
  const durum = (() => {
    switch (o.durum) {
      case "yerel":
        return { ikon: <CircleDashed size={16} />, metin: o.hata ?? (hat.hazir ? "Gönderilecek" : "Kurulum bekleniyor"), ton: o.hata ? "hata" : "" };
      case "gonderiliyor":
        return { ikon: <UploadCloud size={16} />, metin: "Gönderiliyor", ton: "" };
      case "sunucuda":
        return { ikon: <CircleDashed size={16} />, metin: o.hata ?? o.asama ?? "İşleniyor", ton: o.hata ? "uyari" : "" };
      case "yazildi":
        return { ikon: <Check size={16} />, metin: o.notAdi ? "Obsidian'a yazıldı" : "Konuşma bulunamadı", ton: o.notAdi ? "tamam" : "" };
      default:
        return { ikon: <AlertCircle size={16} />, metin: o.hata ?? "Hata", ton: "hata" };
    }
  })();
  const ad = o.baslik ?? (o.kaynak === "ice" ? "İçe aktarılan dosya" : "Kayıt");
  return (
    <article className="kart oturum" onClick={() => setMenu((m) => !m)}>
      <div className="oturum-ust">
        <span className="oturum-zaman">{zaman(o.baslangic)}{o.sureSn ? ` · ${Math.max(1, Math.round(o.sureSn / 60))} dk` : ""}</span>
        <span className={`durum ${durum.ton}`}>{durum.ikon}{durum.metin}</span>
      </div>
      <h3 className="oturum-baslik">{ad}</h3>
      {o.ozet && <p className="oturum-ozet">{o.ozet}</p>}
      {menu && (
        <div className="oturum-eylem" onClick={(e) => e.stopPropagation()}>
          {o.durum !== "yazildi" && (
            <button className="dugme kucuk" onClick={() => void hat.tekrarDene(o)}><RotateCw size={15} /> Tekrar dene</button>
          )}
          <button className="dugme kucuk sessiz" onClick={() => void hat.kaldir(o)}><Trash2 size={15} /> Listeden kaldır</button>
        </div>
      )}
    </article>
  );
}

// ---------------------------------------------------------------- Ayarlar

function Ayarlar() {
  const [kod, setKod] = useState("");
  const [test, setTest] = useState<string | null>(null);
  const [gelismis, setGelismis] = useState(false);
  const a = hat.ayarlar;
  const eslesti = !!a.anahtar;

  return (
    <section>
      <header className="sayfa-basi">
        <h1>Ayarlar</h1>
      </header>

      <ol className="adimlar">
        <li className={eslesti ? "bitti" : ""}>
          <div className="adim-basi"><Link2 size={18} /><h2>Eşleştirme</h2>{eslesti && <Check size={16} className="tik" />}</div>
          <p className="ikincil">
            {eslesti ? `Bu telefon ${a.sunucu.replace("https://", "")} ile eşleşti.` : "Kurulum bağlantısındaki tek kullanımlık kodu gir."}
          </p>
          <div className="satir">
            <input className="girdi" value={kod} onChange={(e) => setKod(e.target.value.toUpperCase())} placeholder="ABCD-2345"
              autoCapitalize="characters" autoCorrect="off" spellCheck={false} />
            <button className="dugme" disabled={kod.replace(/[^A-Z0-9]/g, "").length < 8} onClick={() => void hat.eslestir(kod)}>Eşleştir</button>
          </div>
          {hat.eslestirme && <p className={`ikincil ${hat.eslestirme.durum === "hata" ? "hata-metni" : ""}`}>{hat.eslestirme.metin}</p>}
        </li>

        <li className={a.vault ? "bitti" : ""}>
          <div className="adim-basi"><FolderOpen size={18} /><h2>Obsidian vault'u</h2>{a.vault && <Check size={16} className="tik" />}</div>
          <p className="ikincil">Telefondaki vault klasörünü seç (ör. <em>Claudian</em>). Notlar içindeki <em>{a.altKlasor}</em> klasörüne yazılır.</p>
          <button className="dugme ikincil-dugme" onClick={() => void hat.vaultSec()}>
            <FolderOpen size={17} /> {a.vault ? `${a.vaultAdi} · değiştir` : "Klasörü seç"}
          </button>
        </li>
      </ol>

      <div className="bolum">
        <button className="dugme ikincil-dugme" onClick={async () => setTest(await hat.baglantiDene())} disabled={!eslesti}>
          Bağlantıyı dene
        </button>
        {test && <p className="ikincil">{test}</p>}
      </div>

      <button className="baglanti" onClick={() => setGelismis((g) => !g)}>{gelismis ? "Gelişmişi gizle" : "Gelişmiş"}</button>
      {gelismis && (
        <div className="gelismis">
          <label>Sunucu adresi<input className="girdi" defaultValue={a.sunucu} onBlur={(e) => hat.ayarla({ sunucu: e.target.value })} /></label>
          <label>Alt klasör<input className="girdi" defaultValue={a.altKlasor} onBlur={(e) => hat.ayarla({ altKlasor: e.target.value.trim() || "Yüzük" })} /></label>
        </div>
      )}

      <p className="dipnot">Ses kaydı yalnız not yazılana kadar telefonda durur. Başkalarının özel bilgisi nota yazılmaz.</p>
    </section>
  );
}
