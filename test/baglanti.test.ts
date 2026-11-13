import { test } from "node:test";
import assert from "node:assert/strict";
import { eslesmeMetni, saglikSonucu } from "../src/baglanti.ts";

test("onay bekleyen cihaz 'ulaşılamıyor' değil 'onay bekliyor' görünür", () => {
  const b = saglikSonucu(503, { detail: "Cihazın onay bekliyor.", tur: "onay" });
  assert.equal(b.durum, "onay");
  assert.equal(b.etiket, "onay bekliyor");
  assert.equal(b.metin, "Cihazın onay bekliyor.");
  assert.equal(saglikSonucu(403, { tur: "onay" }).durum, "onay");
});

test("kapalı bilgisayar, pil ve erişim ayrı etiket alır", () => {
  assert.equal(saglikSonucu(503, { tur: "dugum" }).etiket, "bilgisayar kapalı");
  assert.equal(saglikSonucu(503, { tur: "musait" }).etiket, "bilgisayar pilde");
  assert.equal(saglikSonucu(503, { tur: "access" }).etiket, "ulaşılamıyor");
  assert.equal(saglikSonucu(503, {}).durum, "kapali");
});

test("anahtar reddi, bulut hatası ve bağlı durum", () => {
  assert.equal(saglikSonucu(401).durum, "anahtar");
  assert.equal(saglikSonucu(500).etiket, "bulut yanıt vermiyor");
  assert.deepEqual(saglikSonucu(200, { konusmaci: true }), { durum: "bagli", etiket: "bağlı", metin: "Bağlı · konuşmacılar ayrılıyor" });
});

test("eşleşme cevabı onay durumunu söyler", () => {
  assert.equal(eslesmeMetni("bekliyor"), "Eşleşti · onay bekliyor");
  assert.equal(eslesmeMetni("onayli"), "Eşleşti");
});
