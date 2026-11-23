/** 0.15.5 (0.14.0'dan, Boran 23.11.2026): telefondaki Vault Protokolü'nün sürümünü okur ve karşılaştırır. */

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
