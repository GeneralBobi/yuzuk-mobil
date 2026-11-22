/** 0.14.0 (21.11.2026, Boran): Yüzük iki biçimde çalışır. "pc": not bilgisayardaki Claudian'da yazılır (eşleştirme).
 *  "mobil": bilgisayar yok; konuşma telefonda metne döner, notu kullanıcının telefondaki Claude/ChatGPT'si yazar. */
export type Mod = "pc" | "mobil" | "bulut";
/** 0.15.0 (22.11.2026, Boran): üçüncü yol "bulut" [beta]. Not yine telefonda, kullanıcının AI'ıyla yazılır (mobil gibi);
 *  yalnız konuşma telefonda değil, Claudian bulutunun GPU'sunda metne döner. Telefon modeli varsa yedek olarak kullanılır. */
export const MOD_ADI: Record<Mod, string> = { pc: "bilgisayar", mobil: "yalnız telefon", bulut: "bulut · beta" };

/** Tanımsız = ilk açılışta "Notlarını kim yazsın?" sorulur.
 *  0.14.0 (Boran, 21.11): eşleşmiş eski kurulumlar da bir kez sorulur. Önceden sessizce PC yolunda kalıyorlardı;
 *  bilgisayar onayı hiç gelmemiş telefon ("onay bekliyor") tam mobile geçebileceğini hiç görmüyordu. */
export function baslangicModu(a: { mod?: unknown; anahtar?: string }): Mod | undefined {
  if (a.mod === "pc" || a.mod === "mobil" || a.mod === "bulut") return a.mod;
  return undefined;
}

/** Tam mobilde notu yazan AI'a giden hazır istem. Araç adları telefonun MCP'sindekiyle aynı (MobilKayitlar.java). */
export const MOBIL_ISTEM = "Claudian'daki Yüzük kaydımı nota çevir: yuzuk_pending_recordings ile bekleyenleri bul, yuzuk_read_recording ile oku, yuzuk_save_note ile Obsidian'a yaz.";

export const AI_ADRESI: Record<"claude" | "chatgpt", (istem: string) => string> = {
  claude: (q) => `https://claude.ai/new?q=${encodeURIComponent(q)}`,
  chatgpt: (q) => `https://chatgpt.com/?q=${encodeURIComponent(q)}`,
};

/** Notun frontmatter'ındaki "sürüm:" değeri; yoksa null. */
export function protokolSurumu(metin: string): string | null {
  const m = /^---[\s\S]*?^sürüm:\s*([0-9]+(?:\.[0-9]+){0,2})\s*$/m.exec(metin);
  return m ? m[1] : null;
}

/** a, b'den eski mi (1.2 < 1.10). Okunamayan sürüm eski sayılmaz: bilinmeyen dosyanın üstüne yazılmaz. */
export function surumEski(a: string | null, b: string | null): boolean {
  if (!a || !b) return false;
  const x = a.split(".").map(Number), y = b.split(".").map(Number);
  for (let i = 0; i < 3; i++) { const d = (x[i] ?? 0) - (y[i] ?? 0); if (d) return d < 0; }
  return false;
}

/** 0.15.0 hız testi yorumu. oran = 10 sn sesi çözme süresi / 10 sn. 1'in üstünde telefon konuşmanın gerisinde kalır;
 *  kayıt bitince parçalar yine metne döner ama not geç hazırlanır. Sıra (Boran, 22.11): yavaşsa önce küçük model,
 *  o da yetmezse bilgisayar ya da bulut. */
export type HizOnerisi = { durum: "hizli" | "yeterli" | "yavas"; oneri: "base" | "pc" | null; metin: string };
export const HIZ_ESIK = { hizli: 0.5, yeterli: 1.0 } as const;

export function hizYorumu(oran: number, model: "small" | "base", ek: { uyumsuz?: boolean; cokme?: boolean } = {}): HizOnerisi {
  if (ek.uyumsuz) return { durum: "yavas", oneri: "pc", metin: "Bu telefonun işlemcisi konuşmayı telefonda metne çeviremiyor. Bulutla ya da bilgisayarla kullan." };
  if (ek.cokme && model === "small") return { durum: "yavas", oneri: "base", metin: "Hız testi yarıda kaldı; bu model telefona ağır geldi. Daha küçük model (60 MB) önerilir." };
  if (ek.cokme) return { durum: "yavas", oneri: "pc", metin: "Küçük model de telefonda çalışamadı. Bulutla ya da bilgisayarla kullan." };
  const kat = oran > 0 ? Math.round((1 / oran) * 10) / 10 : 0;
  if (oran <= HIZ_ESIK.hizli) return { durum: "hizli", oneri: null, metin: `Telefonun hızlı: konuşmayı ${kat}× hızla metne çeviriyor.` };
  if (oran <= HIZ_ESIK.yeterli) return { durum: "yeterli", oneri: null, metin: "Telefonun yetiyor: konuşmaya yetişiyor." };
  const geri = `10 saniyelik konuşmayı ${Math.round(oran * 10)} saniyede çeviriyor; uzun kayıtta not geç hazırlanır.`;
  if (model === "small") return { durum: "yavas", oneri: "base", metin: `${geri} Daha küçük model (60 MB) önerilir.` };
  return { durum: "yavas", oneri: "pc", metin: `${geri} Küçük model de yetmiyor; bilgisayarla ya da bulutla kullanmak önerilir.` };
}
