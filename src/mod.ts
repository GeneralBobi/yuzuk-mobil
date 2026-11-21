/** 0.14.0 (21.11.2026, Boran): Yüzük iki biçimde çalışır. "pc": not bilgisayardaki Claudian'da yazılır (eşleştirme).
 *  "mobil": bilgisayar yok; konuşma telefonda metne döner, notu kullanıcının telefondaki Claude/ChatGPT'si yazar. */
export type Mod = "pc" | "mobil";

/** Tanımsız = ilk açılışta "Notlarını kim yazsın?" sorulur.
 *  0.14.0 (Boran, 21.11): eşleşmiş eski kurulumlar da bir kez sorulur. Önceden sessizce PC yolunda kalıyorlardı;
 *  bilgisayar onayı hiç gelmemiş telefon ("onay bekliyor") tam mobile geçebileceğini hiç görmüyordu. */
export function baslangicModu(a: { mod?: unknown; anahtar?: string }): Mod | undefined {
  if (a.mod === "pc" || a.mod === "mobil") return a.mod;
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
