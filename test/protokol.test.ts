import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { protokolSurumu, surumEski } from "../src/protokol.ts";

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
