// Kayıt → sunucu → Obsidian hattı. Arayüz yalnız bunu okur.
//
// Bir oturumun ömrü:  yerel → gonderiliyor → sunucuda (sırada / işleniyor) → yazildi
//                                                                         ↘ hata (tekrar denenebilir)
// Ses telefonda not Obsidian'a yazılana kadar durur; sunucu hata verirse yeniden gönderilebilir.
// Not yazılınca hem telefondaki ses hem sunucudaki sonuç silinir.

import { yerel, type Paylasim } from "./yerel";
import protokol from "./vault/protokol.md?raw";

// Telefon yalnız bulutu bilir (24.09.2026): bulut işi hesabın işlem düğümüne yönlendirir.
export const VARSAYILAN_SUNUCU = "https://yuzuk-api.claudian.app";

export type Durum = "yerel" | "gonderiliyor" | "sunucuda" | "yazildi" | "hata";

export interface Oturum {
  id: string;
  yol: string;
  baslangic: string; // ISO
  baslik?: string;
  kaynak: "kayit" | "ice";
  durum: Durum;
  sunucuId?: string;
  asama?: string;
  hata?: string;
  notAdi?: string;
  sureSn?: number;
  ozet?: string; // notun ilk başlıkları, listede gösterilir
  hatirlatici?: number; // telefonda kurulan hatırlatıcı sayısı
  kisi?: number; // ortamda konuşan kişi sayısı (ses izinden)
  senPayi?: number; // konuşmanın kullanıcıya ait oranı, 0..1 (ses izi kayıtlıysa)
  yazan?: string; // notu yazan araç (yedeğe geçtiyse o)
  yedek?: string; // seçilen ama çalışmayan araç
  mahrem?: number; // mahrem sayılıp nota gönderilmeyen cümle sayısı (görünür olsun diye)
}

export interface SesIzi {
  var: boolean;
  olusturuldu?: string;
  konusma_sn?: number;
  tutarlilik?: number;
  uyari?: string | null;
}

export interface HesapBilgisi {
  hesap: string;
  cihaz: string;
  cihazlar: { id: string; ad: string | null; son_gorulme: string | null; bu: boolean }[];
  bu_ay: { is_sayisi: number; dakika: number };
}

/** Oturumun dört duraklı yolu: telefonda · bulutta · yazılıyor · Obsidian'da. */
export function durak(o: Oturum): number {
  if (o.durum === "yazildi") return 3;
  if (o.durum === "sunucuda") return o.asama === "Sırada" ? 1 : 2;
  if (o.durum === "gonderiliyor") return 1;
  return 0;
}

export interface Ayarlar {
  sunucu: string;
  anahtar: string;
  vault?: string;
  vaultAdi?: string;
  altKlasor: string;
  yazici: string; // notu yazan AI: claude | codex | gemini
}

export const YAZICILAR: Record<string, string> = { claude: "Claude", codex: "ChatGPT", gemini: "Gemini" };

const ASAMA: Record<string, string> = {
  sirada: "Sırada",
  "modeller yukleniyor": "Modeller hazırlanıyor",
  "yaziya dokuluyor": "Yazıya dökülüyor",
  "not yaziliyor": "Not yazılıyor",
  "konusmacilar ayriliyor": "Konuşmacılar ayrılıyor",
};

type Dinleyici = () => void;

function oku<T>(anahtar: string, varsayilan: T): T {
  try {
    const ham = localStorage.getItem(anahtar);
    return ham ? (JSON.parse(ham) as T) : varsayilan;
  } catch {
    return varsayilan;
  }
}

class Hat {
  ayarlar: Ayarlar = { ...{ sunucu: VARSAYILAN_SUNUCU, anahtar: "", altKlasor: "Yüzük", yazici: "claude" }, ...oku<Partial<Ayarlar>>("ayarlar", {}) };
  kuruluYazicilar: string[] | null = null; // sunucunun bilgisayarında kurulu olanlar
  oturumlar: Oturum[] = oku("oturumlar", []);
  kaydediyor = false;
  kayitBaslangic: number | null = null;
  seviye = 0;
  okuma = 0; // her 250 ms'lik düzey okumasında artar: halka aynı düzeyi de yeni bir çizgi olarak işler
  uyari: string | null = null;
  eslestirme: { durum: "bekliyor" | "tamam" | "hata"; metin: string } | null = null;
  sesIzi: SesIzi | null = null;
  sesKaydi: "yok" | "kayitta" | "isleniyor" = "yok";
  sesHatasi: string | null = null;
  hesap: HesapBilgisi | null = null;
  baglanti: { durum: "bilinmiyor" | "bagli" | "kapali" | "anahtar"; metin: string } = { durum: "bilinmiyor", metin: "" };

  private dinleyiciler = new Set<Dinleyici>();
  private yoklama: number | null = null;
  private ilerliyor = false;

  get hazir() {
    return !!(this.ayarlar.sunucu && this.ayarlar.anahtar && this.ayarlar.vault);
  }

  abone(d: Dinleyici) {
    this.dinleyiciler.add(d);
    return () => this.dinleyiciler.delete(d);
  }

  private yayinla() {
    for (const d of this.dinleyiciler) d();
  }

  private sakla() {
    localStorage.setItem("oturumlar", JSON.stringify(this.oturumlar));
    localStorage.setItem("ayarlar", JSON.stringify(this.ayarlar));
    this.yayinla();
  }

  async baslat() {
    const d = await yerel.kayitDurumu().catch(() => null);
    if (d?.calisiyor) {
      this.kaydediyor = true;
      this.kayitBaslangic = d.baslangicMs;
      this.seviyeIzle();
    }
    // Uygulama kapanırken "gönderiliyor"da kalan oturum yeniden gönderilir.
    for (const o of this.oturumlar) if (o.durum === "gonderiliyor") o.durum = "yerel";
    this.sakla();
    void this.ilerlet();
    void this.baglantiDene().then(() => Promise.all([this.sesIziYukle(), this.hesapYukle()]));
    void this.iskeletKur();
  }

  // ---------------- ayarlar ----------------

  ayarla(yeni: Partial<Ayarlar>) {
    this.ayarlar = { ...this.ayarlar, ...yeni };
    if (this.ayarlar.sunucu) this.ayarlar.sunucu = this.ayarlar.sunucu.trim().replace(/\/+$/, "");
    this.sakla();
    void this.ilerlet();
  }

  async vaultSec() {
    const r = await yerel.klasorSec();
    if (r.agac) {
      this.ayarla({ vault: r.agac, vaultAdi: r.ad ?? "vault" });
      await this.iskeletKur();
    }
  }

  // ---------------- vault eşitleme (telefon ↔ bilgisayar, bulut üzerinden) ----------------
  //
  // Kurallar: her iki tarafın notlarının SHA-256 izi alınır ve son eşitlemedeki izle karşılaştırılır.
  //  - yalnız bir tarafta olan not öbür tarafa kopyalanır (silme hiçbir yöne taşınmaz);
  //  - yalnız bir taraf değiştiyse o taraf kazanır;
  //  - iki taraf da değiştiyse daha yeni olan kazanır, kaybeden sürüm bilgisayarda .claudian/esitleme-yedek'e girer.
  // Notlar Worker'dan akarak geçer, bulutta saklanmaz. Yalnız hesabın kendi bilgisayarıyla eşitlenir.

  esitleme: { durum: "yok" | "calisiyor" | "bitti" | "hata"; metin: string; son?: string;
    ozet?: { pc: number; tel: number; pcEnYeni: number; telEnYeni: number; indirilen: number; gonderilen: number;
      cakisma: number; ayni: number } } = (() => {
    const e = oku<Hat["esitleme"]>("esitleme", { durum: "yok", metin: "" });
    // Uygulama eşitleme sürerken kapandıysa takılı kalmasın.
    return e.durum === "calisiyor" ? { ...e, durum: "hata" as const, metin: "Önceki eşitleme yarıda kaldı; yeniden dene." } : e;
  })();

  private async sha(metin: string) {
    const b = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(metin));
    return [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join("");
  }

  async esitle() {
    const agac = this.ayarlar.vault;
    if (!agac || !this.ayarlar.anahtar || this.esitleme.durum === "calisiyor") return;
    const guncelle = (e: Partial<Hat["esitleme"]>) => {
      this.esitleme = { ...this.esitleme, ...e };
      localStorage.setItem("esitleme", JSON.stringify(this.esitleme));
      this.yayinla();
    };
    guncelle({ durum: "calisiyor", metin: "Bilgisayardaki notlar okunuyor…" });
    const kok = `${this.ayarlar.sunucu}/v1/vault`;
    const iz: Record<string, string> = oku("esitleme-izi", {});
    const yeniIz: Record<string, string> = {};
    try {
      const r = await fetch(`${kok}/liste`, { headers: this.basliklar });
      if (!r.ok) throw new Error(((await r.json().catch(() => ({}))) as { detail?: string }).detail ?? `Bulut ${r.status}`);
      const pc = new Map(((await r.json()).dosyalar as { yol: string; sha: string; mtime: number }[]).map((d) => [d.yol, d]));
      guncelle({ metin: "Telefondaki notlar okunuyor…" });
      const tel = new Map<string, { yol: string; mtime: number; sha: string; icerik: string }>();
      for (const d of (await yerel.vaultListe({ agac })).dosyalar) {
        const icerik = (await yerel.vaultOku({ agac, yol: d.yol })).icerik;
        tel.set(d.yol, { ...d, icerik, sha: await this.sha(icerik) });
      }
      const say = { indirilen: 0, gonderilen: 0, cakisma: 0, ayni: 0 };
      const indir = async (yol: string) => {
        const g = await fetch(`${kok}/dosya?yol=${encodeURIComponent(yol)}`, { headers: this.basliklar });
        if (!g.ok) throw new Error(`${yol} alınamadı (${g.status})`);
        await yerel.dosyaYaz({ agac, yol, icerik: await g.text(), ekle: false });
        say.indirilen++;
      };
      const gonder = async (yol: string, icerik: string, yedek = false) => {
        const p = await fetch(`${kok}/dosya?yol=${encodeURIComponent(yol)}${yedek ? "&yedek=1" : ""}`, {
          method: "PUT", headers: { ...this.basliklar, "Content-Type": "text/markdown; charset=utf-8" }, body: icerik });
        if (!p.ok) throw new Error(`${yol} gönderilemedi (${p.status})`);
        if (!yedek) say.gonderilen++;
      };
      const yollar = [...new Set([...pc.keys(), ...tel.keys()])].sort();
      for (const [i, yol] of yollar.entries()) {
        guncelle({ metin: `Eşitleniyor · ${i + 1}/${yollar.length}` });
        const p = pc.get(yol), t = tel.get(yol);
        if (p && !t) { await indir(yol); yeniIz[yol] = p.sha; continue; }
        if (t && !p) { await gonder(yol, t.icerik); yeniIz[yol] = t.sha; continue; }
        if (!p || !t) continue;
        if (p.sha === t.sha) { say.ayni++; yeniIz[yol] = p.sha; continue; }
        const pcDegisti = p.sha !== iz[yol], telDegisti = t.sha !== iz[yol];
        if (pcDegisti && !telDegisti) { await indir(yol); yeniIz[yol] = p.sha; }
        else if (telDegisti && !pcDegisti) { await gonder(yol, t.icerik); yeniIz[yol] = t.sha; }
        else {
          say.cakisma++;
          if (p.mtime >= t.mtime) { await gonder(yol, t.icerik, true); await indir(yol); yeniIz[yol] = p.sha; }
          else { await gonder(yol, t.icerik); yeniIz[yol] = t.sha; }
        }
      }
      localStorage.setItem("esitleme-izi", JSON.stringify(yeniIz));
      const enYeni = (m: Map<string, { mtime: number }>) => Math.max(0, ...[...m.values()].map((d) => d.mtime));
      guncelle({ durum: "bitti", son: new Date().toISOString(),
        metin: say.indirilen + say.gonderilen ? `${say.indirilen} not bilgisayardan geldi, ${say.gonderilen} not bilgisayara gitti.` : "İki taraf zaten aynı.",
        ozet: { pc: pc.size, tel: tel.size, pcEnYeni: enYeni(pc), telEnYeni: enYeni(tel), ...say } });
    } catch (e) {
      localStorage.setItem("esitleme-izi", JSON.stringify({ ...iz, ...yeniIz }));
      guncelle({ durum: "hata", metin: (e as Error).message });
    }
  }

  /** Telefondaki vault'un iskeleti yalnız protokoldür: başlık nasıl açılır, ne nasıl yazılır. Bölümler bilgi geldikçe
   *  oluşur; boş şablon not açılmaz. Dosya zaten varsa (masaüstü kurmuş ya da kullanıcı değiştirmiş) dokunulmaz. */
  async iskeletKur() {
    if (!this.ayarlar.vault) return;
    await yerel.dosyaYaz({ agac: this.ayarlar.vault, yol: "Vault Protokolü.md", icerik: protokol, ekle: false, yoksa: true })
      .catch(() => undefined);
  }

  /** Tek kullanımlık kodla sunucudan anahtarı alır. Kod bir kez kullanılınca biter. */
  private sonKod: string | null = null;

  async eslestir(kod: string) {
    const temiz = kod.trim().toUpperCase().replace(/[^A-Z0-9]/g, "");
    // Aynı kurulum bağlantısı açılışta iki yoldan gelir (getLaunchUrl + appUrlOpen); kod tek kullanımlık.
    if (temiz === this.sonKod) return;
    this.sonKod = temiz;
    this.eslestirme = { durum: "bekliyor", metin: "Eşleştiriliyor…" };
    this.yayinla();
    try {
      const r = await fetch(`${this.ayarlar.sunucu || VARSAYILAN_SUNUCU}/v1/eslestir`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ kod: temiz, ad: "Android telefon" }),
      });
      if (r.status === 403) throw new Error("Kod geçersiz ya da kullanılmış");
      if (!r.ok) throw new Error(`Sunucu ${r.status}`);
      const j = await r.json();
      this.ayarla({ sunucu: j.sunucu, anahtar: j.anahtar });
      this.eslestirme = { durum: "tamam", metin: "Eşleşti" };
      void this.baglantiDene();
    } catch (e) {
      this.eslestirme = { durum: "hata", metin: `Eşleşmedi: ${(e as Error).message}` };
    }
    this.yayinla();
  }

  async baglantiDene(): Promise<string> {
    const sonuc = (durum: Hat["baglanti"]["durum"], metin: string) => {
      this.baglanti = { durum, metin };
      this.yayinla();
      return metin;
    };
    if (!this.ayarlar.anahtar) return sonuc("bilinmiyor", "");
    try {
      const r = await fetch(`${this.ayarlar.sunucu}/v1/saglik`, { headers: this.basliklar });
      if (r.status === 401) return sonuc("anahtar", "Bu telefonun anahtarı artık geçmiyor. Masaüstünden yeniden eşleştir.");
      if (r.status === 503) return sonuc("kapali", ((await r.json().catch(() => ({}))) as { detail?: string }).detail ?? "İşlem düğümü çevrim dışı. Kayıtlar telefonda bekler.");
      if (!r.ok) return sonuc("kapali", `Bulut ${r.status} döndü. Kayıtlar telefonda bekler.`);
      const j = await r.json();
      this.kuruluYazicilar = Object.keys(j.yazicilar ?? {});
      if (typeof j.profil === "boolean" && !this.sesIzi) this.sesIzi = { var: j.profil };
      return sonuc("bagli", j.konusmaci ? "Bağlı · konuşmacılar ayrılıyor" : "Bağlı");
    } catch {
      return sonuc("kapali", "Buluta ulaşılamadı. İnternet yok gibi; kayıtlar telefonda bekler.");
    }
  }

  // ---------------- hesap ----------------

  async hesapYukle() {
    if (!this.ayarlar.anahtar) return;
    try {
      const r = await fetch(`${this.ayarlar.sunucu}/v1/hesap`, { headers: this.basliklar });
      if (r.ok) {
        this.hesap = await r.json();
        this.yayinla();
      }
    } catch {
      /* çevrimdışı: son bilinen hâl kalır */
    }
  }

  async cihazIptal(id: string) {
    const r = await fetch(`${this.ayarlar.sunucu}/v1/cihaz/${id}`, { method: "DELETE", headers: this.basliklar }).catch(() => null);
    if (r?.ok) await this.hesapYukle();
  }

  // ---------------- ses izi ("sesini tanıt") ----------------

  async sesIziYukle() {
    if (!this.ayarlar.anahtar) return;
    try {
      const r = await fetch(`${this.ayarlar.sunucu}/v1/profil`, { headers: this.basliklar });
      if (r.ok) {
        this.sesIzi = await r.json();
        this.yayinla();
      }
    } catch {
      /* çevrimdışı */
    }
  }

  async sesKaydiBaslat() {
    this.sesHatasi = null;
    if (this.kaydediyor) {
      this.sesHatasi = "Önce süren kaydı durdur.";
      this.yayinla();
      return;
    }
    const izin = await yerel.izinIste();
    if (!izin.mikrofon) {
      this.sesHatasi = "Mikrofon izni verilmedi.";
      this.yayinla();
      return;
    }
    try {
      await yerel.kayitBaslat();
      const d = await yerel.kayitDurumu();
      if (!d.calisiyor) throw new Error(d.hata ?? "kayıt başlamadı");
      this.sesKaydi = "kayitta";
      this.kayitBaslangic = d.baslangicMs;
      this.seviyeIzle();
    } catch (e) {
      this.sesHatasi = (e as Error).message;
    }
    this.yayinla();
  }

  /** Okuma kaydı oturum olmaz: yalnız ses izi çıkarılsın diye gider, sonra telefondan da silinir. */
  async sesKaydiBitir(vazgec = false) {
    const r = await yerel.kayitDurdur();
    this.kayitBaslangic = null;
    this.seviye = 0;
    if (vazgec || !r.yol) {
      if (r.yol) await yerel.dosyaSil({ yol: r.yol }).catch(() => undefined);
      this.sesKaydi = "yok";
      this.yayinla();
      return;
    }
    this.sesKaydi = "isleniyor";
    this.yayinla();
    try {
      const y = await yerel.yukle({ yol: r.yol, url: `${this.ayarlar.sunucu}/v1/profil`, anahtar: this.ayarlar.anahtar, alanlar: {} });
      const j = JSON.parse(y.govde || "{}");
      if (y.durum !== 200) throw new Error(j.detail ?? `Bulut ${y.durum}`);
      this.sesIzi = j;
    } catch (e) {
      this.sesHatasi = (e as Error).message;
    } finally {
      await yerel.dosyaSil({ yol: r.yol }).catch(() => undefined);
      this.sesKaydi = "yok";
      this.yayinla();
    }
  }

  async sesIziSil() {
    const r = await fetch(`${this.ayarlar.sunucu}/v1/profil`, { method: "DELETE", headers: this.basliklar }).catch(() => null);
    if (r?.ok) this.sesIzi = { var: false };
    this.yayinla();
  }

  private get basliklar() {
    return { Authorization: `Bearer ${this.ayarlar.anahtar}` };
  }

  // ---------------- kayıt ----------------

  async kayitBaslat() {
    this.uyari = null;
    if (this.sesKaydi !== "yok") return;
    const izin = await yerel.izinIste();
    if (!izin.mikrofon) {
      this.uyari = "Mikrofon izni verilmedi. Android ayarlarından Yüzük'e mikrofon izni ver.";
      this.yayinla();
      return;
    }
    try {
      await yerel.kayitBaslat();
      const d = await yerel.kayitDurumu();
      if (!d.calisiyor) throw new Error(d.hata ?? "bilinmeyen hata");
      this.kaydediyor = true;
      this.kayitBaslangic = d.baslangicMs;
      this.seviyeIzle();
    } catch (e) {
      this.uyari = `Kayıt başlamadı: ${(e as Error).message}`;
    }
    this.yayinla();
  }

  private seviyeIzle() {
    const tik = async () => {
      if (!this.kaydediyor && this.sesKaydi !== "kayitta") {
        this.seviye = 0;
        return;
      }
      const d = await yerel.kayitDurumu().catch(() => null);
      this.seviye = d?.seviye ?? 0;
      this.okuma++;
      this.yayinla();
      window.setTimeout(tik, 250);
    };
    void tik();
  }

  async kayitDurdur() {
    const r = await yerel.kayitDurdur();
    const bas = this.kayitBaslangic ?? Date.now();
    this.kaydediyor = false;
    this.kayitBaslangic = null;
    if (r.yol) {
      this.oturumlar.unshift({ id: String(bas), yol: r.yol, baslangic: new Date(bas).toISOString(), kaynak: "kayit",
        durum: "yerel", sureSn: r.sureSn });
      this.sakla();
      void this.ilerlet();
    } else {
      this.uyari = "Kayıt boş çıktı.";
      this.yayinla();
    }
  }

  async iceAktar() {
    this.uyari = null;
    const r = await yerel.dosyaSec();
    if (r.yol) this.dosyaEkle(r.yol, r.ad);
  }

  /** Seçilen ya da başka uygulamadan paylaşılan dosya bir oturum olur ve hatta girer. */
  dosyaEkle(yol: string, ad?: string | null) {
    if (this.oturumlar.some((o) => o.yol === yol)) return;
    const an = Date.now();
    const baslik = ad ? ad.replace(/\.[^.]+$/, "") : undefined;
    this.oturumlar.unshift({ id: String(an), yol, baslangic: new Date(an).toISOString(), baslik, kaynak: "ice", durum: "yerel" });
    this.sakla();
    void this.ilerlet();
  }

  paylasimIsle(p: Paylasim) {
    if (p.hata) {
      this.uyari = p.hata;
      this.yayinla();
    } else if (p.yol) this.dosyaEkle(p.yol, p.ad);
  }

  // ---------------- hat ----------------

  /** Bekleyen her oturumu bir adım ileri taşır: gönder, durum sor, hazırsa Obsidian'a yaz. */
  async ilerlet() {
    if (this.ilerliyor || !this.hazir) return;
    this.ilerliyor = true;
    try {
      for (const o of this.oturumlar.filter((o) => o.durum === "yerel")) await this.gonder(o);
      for (const o of this.oturumlar.filter((o) => o.durum === "sunucuda")) await this.sor(o);
    } finally {
      this.ilerliyor = false;
    }
    if (this.yoklama) window.clearTimeout(this.yoklama);
    if (this.oturumlar.some((o) => o.durum === "sunucuda")) this.yoklama = window.setTimeout(() => void this.ilerlet(), 5000);
  }

  async tekrarDene(o: Oturum) {
    const sesVar = (await yerel.dosyaVarMi({ yol: o.yol })).var;
    o.hata = undefined;
    if (sesVar) {
      o.sunucuId = undefined;
      o.durum = "yerel";
    } else {
      o.durum = "sunucuda";
    }
    this.sakla();
    void this.ilerlet();
  }

  async kaldir(o: Oturum) {
    await yerel.dosyaSil({ yol: o.yol }).catch(() => undefined);
    this.oturumlar = this.oturumlar.filter((x) => x !== o);
    this.sakla();
  }

  private async gonder(o: Oturum) {
    o.durum = "gonderiliyor";
    o.hata = undefined;
    this.yayinla();
    try {
      const alanlar: Record<string, string> = { baslangic: o.baslangic, yazici: this.ayarlar.yazici || "claude" };
      if (o.baslik) alanlar.baslik = o.baslik;
      const r = await yerel.yukle({ yol: o.yol, url: `${this.ayarlar.sunucu}/v1/oturum`, anahtar: this.ayarlar.anahtar, alanlar });
      if (r.durum !== 200) {
        let neden = r.govde.slice(0, 160);
        try {
          neden = JSON.parse(r.govde).detail ?? neden;
        } catch {
          /* düz metin */
        }
        if (r.durum === 413 || r.durum === 415) {
          // Tekrar denemek düzeltmez (dosya çok büyük ya da biçim desteklenmiyor): oturum hata olarak durur.
          o.durum = "hata";
          o.hata = neden;
          this.sakla();
          return;
        }
        throw new Error(r.durum === 503 ? neden : `Bulut ${r.durum}: ${neden}`);
      }
      o.sunucuId = JSON.parse(r.govde).id;
      o.durum = "sunucuda";
      o.asama = "Sırada";
    } catch (e) {
      o.durum = "yerel";
      o.hata = `Gönderilemedi — ${(e as Error).message}`;
    }
    this.sakla();
  }

  private async sor(o: Oturum) {
    try {
      const r = await fetch(`${this.ayarlar.sunucu}/v1/oturum/${o.sunucuId}`, { headers: this.basliklar });
      if (r.status === 404) {
        o.durum = "hata";
        o.hata = "Sunucu bu kaydı artık tanımıyor";
        this.sakla();
        return;
      }
      if (!r.ok) throw new Error(`Sunucu ${r.status}`);
      const j = await r.json();
      if (j.durum === "hazir") {
        if (j.sonuc?.not_md?.trim()) {
          await this.obsidianaYaz(o, j.sonuc);
        } else {
          // Konuşma yoksa vault'a boş dosya yazılmaz.
          o.ozet = "Not alınacak konuşma bulunamadı; vault'a bir şey yazılmadı.";
          o.baslik = o.baslik ?? "Sessiz kayıt";
        }
        await fetch(`${this.ayarlar.sunucu}/v1/oturum/${o.sunucuId}`, { method: "DELETE", headers: this.basliklar }).catch(() => undefined);
        o.durum = "yazildi";
        o.asama = undefined;
        o.hata = undefined;
        await yerel.dosyaSil({ yol: o.yol }).catch(() => undefined); // not yazıldı: ses hiçbir yerde kalmaz
      } else if (j.durum === "hata") {
        o.durum = "hata";
        o.hata = j.hata ?? "Sunucuda hata";
      } else {
        o.asama = ASAMA[j.asama ?? j.durum] ?? j.durum;
        o.hata = undefined;
      }
    } catch (e) {
      o.hata = `Bağlantı: ${(e as Error).message}`; // sunucuda kalır, sonraki yoklamada tekrar sorulur
    }
    this.sakla();
  }

  // ---------------- Obsidian ----------------

  private async obsidianaYaz(o: Oturum, s: { baslik?: string; not_md?: string; hatirlaticilar?: { tarih: string; saat?: string; metin: string }[];
    konusmaci?: { sayi?: number; sen?: boolean; sureler?: Record<string, number> }; yazici?: string; istenen_yazici?: string; mahrem?: number }) {
    o.mahrem = s.mahrem || undefined;
    const k = s.konusmaci;
    if (k?.sayi) {
      o.kisi = k.sayi;
      const toplam = Object.values(k.sureler ?? {}).reduce((a, b) => a + b, 0);
      if (k.sen && toplam) o.senPayi = (k.sureler?.["Sen"] ?? 0) / toplam;
    }
    o.yazan = s.yazici ? (YAZICILAR[s.yazici] ?? s.yazici) : undefined;
    // Seçilen araç çalışmadıysa not yine yazılır; kimin yerine kimin yazdığı görünür kalır.
    o.yedek = s.istenen_yazici ? (YAZICILAR[s.istenen_yazici] ?? s.istenen_yazici) : undefined;
    const b = new Date(o.baslangic);
    const iki = (n: number) => String(n).padStart(2, "0");
    const tarih = `${b.getFullYear()}-${iki(b.getMonth() + 1)}-${iki(b.getDate())}`;
    const saat = `${iki(b.getHours())}:${iki(b.getMinutes())}`;
    const baslik = s.baslik?.trim() || o.baslik || "Not";
    const temiz = baslik.replace(/[\\/:*?"<>|#^[\]]/g, " ").replace(/\s+/g, " ").trim();
    const ad = `${tarih} ${iki(b.getHours())}${iki(b.getMinutes())} ${temiz}.md`;
    const hat = s.hatirlaticilar ?? [];
    // Not anatomisi Vault Protokolü'ndeki gibi: tags, tür, güncellenme (masaüstündeki Yüzük notlarıyla aynı).
    let not = `---\ntags: [yüzük]\ntür: log\ngüncellenme: ${tarih}\nkaynak: yüzük\ntarih: ${tarih} ${saat}\n---\n\n# ${baslik}\n\n`;
    not += s.not_md?.trim() ? s.not_md.trim() + "\n" : "_Bu oturumda not alınacak bir şey bulunmadı._\n";
    if (hat.length) not += `\n## Hatırlatıcılar\n${hat.map((h) => `- [ ] ${h.metin}${h.saat ? ` · saat ${h.saat}` : ""} 📅 ${h.tarih}`).join("\n")}\n`;
    const alt = this.ayarlar.altKlasor || "Yüzük";
    const yazilan = await yerel.dosyaYaz({ agac: this.ayarlar.vault!, yol: `${alt}/${ad}`, icerik: not, ekle: false });
    o.notAdi = yazilan.ad;
    o.baslik = baslik;
    // Listede notun kendi özeti görünür: "Özet" başlığı altındaki ilk cümle; yoksa bölüm başlıkları.
    const satirlar = (s.not_md ?? "").split("\n");
    const ozetBasi = satirlar.findIndex((l) => /^#{2,4}\s*Özet/i.test(l));
    const ilk = ozetBasi >= 0 ? satirlar.slice(ozetBasi + 1).find((l) => l.trim() && !l.startsWith("#")) : undefined;
    o.ozet = ilk
      ? ilk.replace(/^[-*]\s*/, "").replace(/\*\*/g, "").split(/(?<=[.!?])\s/)[0].slice(0, 180)
      : satirlar.filter((l) => l.startsWith("### ")).map((l) => l.slice(4)).slice(0, 3).join(" · ");
    if (hat.length) {
      const satirlar = hat.map((h) => `- [ ] ${h.metin}${h.saat ? ` · saat ${h.saat}` : ""} 📅 ${h.tarih} · [[${ad.replace(/\.md$/, "")}]]\n`).join("");
      await yerel.dosyaYaz({ agac: this.ayarlar.vault!, yol: `${alt}/Yüzük Hatırlatıcıları.md`, icerik: satirlar, ekle: true });
    }
    // Tarihli işler telefonda hatırlatıcı olur: saat söylendiyse 30 dk önce, söylenmediyse o gün sabah 09:00.
    let kurulan = 0;
    for (const [k, h] of hat.entries()) {
      const [y, a, g] = h.tarih.split("-").map(Number);
      if (!y || !a || !g) continue;
      const [ss, dd] = (h.saat ?? "").split(":").map(Number);
      const saatli = Number.isInteger(ss) && ss >= 0 && ss < 24;
      const zaman = saatli ? new Date(y, a - 1, g, ss, dd || 0, 0).getTime() - 30 * 60e3 : new Date(y, a - 1, g, 9, 0, 0).getTime();
      const r = await yerel.hatirlaticiKur({ id: `${o.id}-${k}`, zamanMs: zaman, baslik: h.metin,
        metin: `${baslik} · ${h.tarih}${saatli ? ` ${h.saat}` : ""}` }).catch(() => null);
      if (r?.kuruldu) kurulan++;
    }
    o.hatirlatici = kurulan;
    await yerel.bildir({
      baslik: `Not hazır · ${baslik}`,
      metin: hat.length ? `${hat.length} hatırlatıcı: ${hat.map((h) => `${h.tarih} ${h.metin}`).join(" · ")}` : `Obsidian → ${alt}`,
    });
  }
}

export const hat = new Hat();
