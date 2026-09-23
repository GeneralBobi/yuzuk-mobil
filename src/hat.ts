// Kayıt → sunucu → Obsidian hattı. Arayüz yalnız bunu okur.
//
// Bir oturumun ömrü:  yerel → gonderiliyor → sunucuda (sırada / işleniyor) → yazildi
//                                                                         ↘ hata (tekrar denenebilir)
// Ses telefonda not Obsidian'a yazılana kadar durur; sunucu hata verirse yeniden gönderilebilir.
// Not yazılınca hem telefondaki ses hem sunucudaki sonuç silinir.

import { yerel } from "./yerel";

export const VARSAYILAN_SUNUCU = "https://yuzuk.claudian.app";

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
}

export interface Ayarlar {
  sunucu: string;
  anahtar: string;
  vault?: string;
  vaultAdi?: string;
  altKlasor: string;
}

const ASAMA: Record<string, string> = {
  sirada: "Sırada",
  "modeller yukleniyor": "Modeller hazırlanıyor",
  "yaziya dokuluyor": "Yazıya dökülüyor",
  "not yaziliyor": "Not yazılıyor",
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
  ayarlar: Ayarlar = oku("ayarlar", { sunucu: VARSAYILAN_SUNUCU, anahtar: "", altKlasor: "Yüzük" });
  oturumlar: Oturum[] = oku("oturumlar", []);
  kaydediyor = false;
  kayitBaslangic: number | null = null;
  seviye = 0;
  uyari: string | null = null;
  eslestirme: { durum: "bekliyor" | "tamam" | "hata"; metin: string } | null = null;

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
    if (r.agac) this.ayarla({ vault: r.agac, vaultAdi: r.ad ?? "vault" });
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
        body: JSON.stringify({ kod: temiz }),
      });
      if (r.status === 403) throw new Error("Kod geçersiz ya da kullanılmış");
      if (!r.ok) throw new Error(`Sunucu ${r.status}`);
      const j = await r.json();
      this.ayarla({ sunucu: j.sunucu, anahtar: j.anahtar });
      this.eslestirme = { durum: "tamam", metin: "Eşleşti" };
    } catch (e) {
      this.eslestirme = { durum: "hata", metin: `Eşleşmedi: ${(e as Error).message}` };
    }
    this.yayinla();
  }

  async baglantiDene(): Promise<string> {
    try {
      const r = await fetch(`${this.ayarlar.sunucu}/v1/saglik`, { headers: this.basliklar });
      if (r.status === 401) return "Anahtar geçersiz";
      if (!r.ok) return `Sunucu ${r.status} döndü`;
      const j = await r.json();
      return `Bağlı · karar modeli ${j.laya} · yazıya dökme ${j.stt}`;
    } catch {
      return "Sunucuya ulaşılamadı. Bilgisayar kapalı ya da internet yok; kayıtlar telefonda bekler.";
    }
  }

  private get basliklar() {
    return { Authorization: `Bearer ${this.ayarlar.anahtar}` };
  }

  // ---------------- kayıt ----------------

  async kayitBaslat() {
    this.uyari = null;
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
      if (!this.kaydediyor) {
        this.seviye = 0;
        return;
      }
      const d = await yerel.kayitDurumu().catch(() => null);
      this.seviye = d?.seviye ?? 0;
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
    if (!r.yol) return;
    const an = Date.now();
    const baslik = r.ad ? r.ad.replace(/\.[^.]+$/, "") : undefined;
    this.oturumlar.unshift({ id: String(an), yol: r.yol, baslangic: new Date(an).toISOString(), baslik, kaynak: "ice", durum: "yerel" });
    this.sakla();
    void this.ilerlet();
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
      const alanlar: Record<string, string> = { baslangic: o.baslangic };
      if (o.baslik) alanlar.baslik = o.baslik;
      const r = await yerel.yukle({ yol: o.yol, url: `${this.ayarlar.sunucu}/v1/oturum`, anahtar: this.ayarlar.anahtar, alanlar });
      if (r.durum !== 200) throw new Error(`Sunucu ${r.durum}: ${r.govde.slice(0, 160)}`);
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

  private async obsidianaYaz(o: Oturum, s: { baslik?: string; not_md?: string; hatirlaticilar?: { tarih: string; metin: string }[] }) {
    const b = new Date(o.baslangic);
    const iki = (n: number) => String(n).padStart(2, "0");
    const tarih = `${b.getFullYear()}-${iki(b.getMonth() + 1)}-${iki(b.getDate())}`;
    const saat = `${iki(b.getHours())}:${iki(b.getMinutes())}`;
    const baslik = s.baslik?.trim() || o.baslik || "Not";
    const temiz = baslik.replace(/[\\/:*?"<>|#^[\]]/g, " ").replace(/\s+/g, " ").trim();
    const ad = `${tarih} ${iki(b.getHours())}${iki(b.getMinutes())} ${temiz}.md`;
    const hat = s.hatirlaticilar ?? [];
    let not = `---\nkaynak: yüzük\ntarih: ${tarih} ${saat}\ntags: [yüzük]\n---\n\n# ${baslik}\n\n`;
    not += s.not_md?.trim() ? s.not_md.trim() + "\n" : "_Bu oturumda not alınacak bir şey bulunmadı._\n";
    if (hat.length) not += `\n## Hatırlatıcılar\n${hat.map((h) => `- [ ] ${h.metin} 📅 ${h.tarih}`).join("\n")}\n`;
    const alt = this.ayarlar.altKlasor || "Yüzük";
    const yazilan = await yerel.dosyaYaz({ agac: this.ayarlar.vault!, yol: `${alt}/${ad}`, icerik: not, ekle: false });
    o.notAdi = yazilan.ad;
    o.baslik = baslik;
    o.ozet = (s.not_md ?? "").split("\n").filter((l) => l.startsWith("### ")).map((l) => l.slice(4)).slice(0, 3).join(" · ");
    if (hat.length) {
      const satirlar = hat.map((h) => `- [ ] ${h.metin} 📅 ${h.tarih} · [[${ad.replace(/\.md$/, "")}]]\n`).join("");
      await yerel.dosyaYaz({ agac: this.ayarlar.vault!, yol: `${alt}/Yüzük Hatırlatıcıları.md`, icerik: satirlar, ekle: true });
    }
    // Tarihli işler telefonda hatırlatıcı olur: o gün sabah 09:00 (saat notta yazıyorsa o gün yine sabah uyarır).
    let kurulan = 0;
    for (const [k, h] of hat.entries()) {
      const [y, a, g] = h.tarih.split("-").map(Number);
      if (!y || !a || !g) continue;
      const zaman = new Date(y, a - 1, g, 9, 0, 0).getTime();
      const r = await yerel.hatirlaticiKur({ id: `${o.id}-${k}`, zamanMs: zaman, baslik: h.metin, metin: `${baslik} · ${h.tarih}` }).catch(() => null);
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
