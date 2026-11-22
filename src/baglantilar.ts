/** 0.15.0 (22.11.2026, Boran): "Bilgisayarla dediğimizde bağlı olduğumuz yerler, yalnız telefonda dediğimizde bağlı
 *  olduğumuz yerler görünsün" — Claudian'ın "AI bağlantıları" ekranı gibi. Her mod için kart listesi; seçili mod
 *  dışındakiler de görüntülenebilir (geçmeden önce neyin eksik olduğu görülsün). Saf fonksiyon: test edilir. */
import type { Mod } from "./mod.ts";

export type KartDurumu = "hazir" | "eksik" | "bekliyor";
export interface Kart { ad: string; durum: KartDurumu; satir: string; ipucu?: string }

export interface BaglantiOzeti {
  eslesti: boolean;
  bilgisayar: "bagli" | "kapali" | "onay" | "anahtar" | "bilinmiyor";
  bilgisayarEtiket: string;
  vault: string | null;
  yazici: string;
  kuruluYazicilar: string[] | null;
  yaziciDurum: Record<string, string> | null;
  sesIzi: boolean;
  model: { hazir: boolean; indiriliyor: boolean; ad: string; oran?: number; uyumsuz?: boolean } | null;
  dogrudan: { acik: boolean; baglanti: string; izinler: { ai: string }[] } | null;
  bulut: "bagli" | "kapali" | "hesapsiz" | "bilinmiyor";
}

const AI: Record<string, string> = { claude: "Claude", codex: "ChatGPT", gemini: "Antigravity", chatgpt: "ChatGPT" };
const MODEL_ADI: Record<string, string> = { small: "small · 190 MB", base: "base · 60 MB" };

function vaultKarti(o: BaglantiOzeti, yer: string): Kart {
  return o.vault ? { ad: "Obsidian vault", durum: "hazir", satir: `Seçili · ${yer}`, ipucu: o.vault }
    : { ad: "Obsidian vault", durum: "eksik", satir: "Seçilmedi", ipucu: "Ayarlar → Obsidian vault → Klasörü seç" };
}

/** Telefonun AI bağlantıları (üç modda aynı MCP adresi): Claude ve ChatGPT bu adrese bağlanır. */
function aiKartlari(o: BaglantiOzeti): Kart[] {
  return (["claude", "chatgpt"] as const).map((ai): Kart => {
    const d = o.dogrudan;
    if (!d?.acik) return { ad: AI[ai], durum: "eksik", satir: "Bağlı değil", ipucu: "AI'lara doğrudan bağlan → Aç" };
    if (!d.izinler.some((g) => g.ai === ai)) return { ad: AI[ai], durum: "eksik", satir: "Bağlı değil", ipucu: `${AI[ai]}'a bağla → adresi yapıştır` };
    if (d.baglanti !== "cevrimici") return { ad: AI[ai], durum: "bekliyor", satir: "Bağlı · telefon çevrim dışı" };
    return { ad: AI[ai], durum: "hazir", satir: "Bağlı · kullanıma hazır" };
  });
}

function modelKarti(o: BaglantiOzeti, yedek: boolean): Kart {
  const m = o.model;
  const ad = yedek ? "Telefon modeli (yedek)" : "Konuşma modeli";
  if (!m) return { ad, durum: "eksik", satir: "Bakılamadı" };
  if (m.uyumsuz) return { ad, durum: yedek ? "bekliyor" : "eksik", satir: "Bu telefonda çalışmaz", ipucu: yedek ? undefined : "Bulutla ya da Bilgisayarla kullan" };
  if (m.indiriliyor) return { ad, durum: "bekliyor", satir: "İniyor" };
  if (!m.hazir) return { ad, durum: yedek ? "bekliyor" : "eksik", satir: "İnmedi",
    ipucu: yedek ? "İsteğe bağlı: internet yokken de çalışsın" : "Ayarlar → Telefonda yazıya dökme → İndir" };
  const hiz = m.oran === undefined ? "" : m.oran <= 1 ? " · konuşmaya yetişiyor" : " · yavaş";
  return { ad, durum: m.oran !== undefined && m.oran > 1 && !yedek ? "bekliyor" : "hazir", satir: `Hazır · ${MODEL_ADI[m.ad] ?? m.ad}${hiz}` };
}

export function baglantiKartlari(mod: Mod, o: BaglantiOzeti): Kart[] {
  if (mod === "pc") {
    const ad = "Claudian (bilgisayar)";
    const pc: Kart = !o.eslesti ? { ad, durum: "eksik", satir: "Eşleşmedi", ipucu: "Claudian → Yüzük → Telefonu bağla" }
      : o.bilgisayar === "bagli" ? { ad, durum: "hazir", satir: "Bağlı · kullanıma hazır" }
      : { ad, durum: o.bilgisayar === "onay" || o.bilgisayar === "bilinmiyor" ? "bekliyor" : "eksik", satir: `Eşleşti · ${o.bilgisayarEtiket}` };
    const kurulu = !o.kuruluYazicilar || o.kuruluYazicilar.includes(o.yazici);
    const neden = o.yaziciDurum?.[o.yazici] === "giris" ? "bilgisayarda giriş gerekli" : "bilgisayarda kurulu değil";
    const yazici: Kart = { ad: `Notu yazan · ${AI[o.yazici] ?? o.yazici}`,
      durum: !o.eslesti ? "eksik" : !kurulu ? "eksik" : o.bilgisayar === "bagli" ? "hazir" : "bekliyor",
      satir: !o.eslesti ? "Bilgisayar bekleniyor" : kurulu ? "Seçili · bilgisayarda" : `Seçili · ${neden}` };
    const ses: Kart = { ad: "Konuşmacı ayrımı", durum: o.sesIzi ? "hazir" : "bekliyor", satir: o.sesIzi ? "Sesin tanındı" : "Sesin tanıtılmadı",
      ipucu: o.sesIzi ? undefined : "Ayarlar → Sesin → Sesini tanıt" };
    return [pc, yazici, vaultKarti(o, "bilgisayarla eşitlenir"), ses];
  }
  if (mod === "mobil") return [modelKarti(o, false), vaultKarti(o, "telefonda"), ...aiKartlari(o)];
  const ad = "Claudian bulutu · beta";
  const bulut: Kart = o.bulut === "hesapsiz"
    ? { ad, durum: "eksik", satir: "Hesap yok", ipucu: "Bulut için bir kez eşleştirme gerekir (Claudian → Yüzük → Telefonu bağla)" }
    : o.bulut === "bagli" ? { ad, durum: "hazir", satir: "Bağlı · konuşma bulutta metne döner" }
    : o.bulut === "bilinmiyor" ? { ad, durum: "bekliyor", satir: "Bakılıyor" }
    : { ad, durum: "eksik", satir: "Ulaşılamıyor · telefon modeli devrede" };
  return [bulut, modelKarti(o, true), vaultKarti(o, "telefonda"), ...aiKartlari(o)];
}
