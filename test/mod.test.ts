import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { AI_ADRESI, MOBIL_ISTEM, baslangicModu, protokolSurumu, surumEski } from "../src/mod.ts";

test("0.14.0: eşleşmiş eski kurulum da bir kez sorulur (tam mobile geçebilsin)", () => {
  assert.equal(baslangicModu({ anahtar: "abc" }), undefined);
});

test("yeni kurulumda mod tanımsız: ilk açılışta soru sorulur", () => {
  assert.equal(baslangicModu({}), undefined);
  assert.equal(baslangicModu({ anahtar: "" }), undefined);
});

test("seçilen mod korunur; eşleşmiş telefon da mobil seçebilir", () => {
  assert.equal(baslangicModu({ mod: "mobil", anahtar: "abc" }), "mobil");
  assert.equal(baslangicModu({ mod: "pc" }), "pc");
  assert.equal(baslangicModu({ mod: "bozuk" }), undefined);
});

test("AI bağlantıları istemi kodlar ve araç adlarını taşır", () => {
  const u = new URL(AI_ADRESI.claude(MOBIL_ISTEM));
  assert.equal(u.host, "claude.ai");
  assert.equal(u.searchParams.get("q"), MOBIL_ISTEM);
  assert.match(MOBIL_ISTEM, /yuzuk_pending_recordings.*yuzuk_read_recording.*yuzuk_save_note/);
  assert.equal(new URL(AI_ADRESI.chatgpt("a b")).searchParams.get("q"), "a b");
});

test("protokol sürümü okunur ve karşılaştırılır", () => {
  assert.equal(protokolSurumu("---\ntags: [x]\nsürüm: 0.27.0\n---\n# P"), "0.27.0");
  assert.equal(protokolSurumu("# frontmattersiz"), null);
  assert.ok(surumEski("0.27.0", "3.0.0"));
  assert.ok(surumEski("1.2.0", "1.10.0"));
  assert.ok(!surumEski("3.0.0", "3.0.0"));
  assert.ok(!surumEski("3.1.0", "3.0.0"), "bilgisayardan gelen daha yeni protokol ezilmez");
  assert.ok(!surumEski(null, "3.0.0"), "sürümü okunamayan dosyaya dokunulmaz");
});

test("telefondaki protokol masaüstüyle aynı sürümde (3.0.0)", () => {
  const p = readFileSync(new URL("../src/vault/protokol.md", import.meta.url), "utf8");
  assert.equal(protokolSurumu(p), "3.0.0");
});
