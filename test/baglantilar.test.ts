import { test } from "node:test";
import assert from "node:assert/strict";
import { baglantiKartlari, type BaglantiOzeti } from "../src/baglantilar.ts";
import { baslangicModu, hizYorumu, MOD_ADI } from "../src/mod.ts";

const taban: BaglantiOzeti = { eslesti: false, bilgisayar: "bilinmiyor", bilgisayarEtiket: "bakılıyor", vault: null, yazici: "claude",
  kuruluYazicilar: null, yaziciDurum: null, sesIzi: false, model: null, dogrudan: null, bulut: "hesapsiz" };

test("her mod kendi bağlı olduğu yerleri listeler", () => {
  assert.deepEqual(baglantiKartlari("pc", taban).map((k) => k.ad), ["Claudian (bilgisayar)", "Notu yazan · Claude", "Obsidian vault", "Konuşmacı ayrımı"]);
  assert.deepEqual(baglantiKartlari("mobil", taban).map((k) => k.ad), ["Konuşma modeli", "Obsidian vault", "Claude", "ChatGPT"]);
  assert.deepEqual(baglantiKartlari("bulut", taban).map((k) => k.ad), ["Claudian bulutu · beta", "Telefon modeli (yedek)", "Obsidian vault", "Claude", "ChatGPT"]);
});

test("onay bekleyen bilgisayar 'bekliyor' görünür, nedeni yazılır", () => {
  const k = baglantiKartlari("pc", { ...taban, eslesti: true, bilgisayar: "onay", bilgisayarEtiket: "onay bekliyor" })[0];
  assert.equal(k.durum, "bekliyor");
  assert.equal(k.satir, "Eşleşti · onay bekliyor");
});

test("AI ancak telefonun adresine izin verdiyse bağlı sayılır", () => {
  const d = { acik: true, baglanti: "cevrimici", izinler: [{ ai: "claude" }] };
  const k = baglantiKartlari("mobil", { ...taban, dogrudan: d });
  assert.equal(k[2].durum, "hazir");
  assert.equal(k[3].durum, "eksik");
});

test("yavaş telefon modeli tam mobilde uyarı, bulutta yedek olarak hazır", () => {
  const model = { hazir: true, indiriliyor: false, ad: "small", oran: 1.6 };
  assert.equal(baglantiKartlari("mobil", { ...taban, model })[0].durum, "bekliyor");
  assert.equal(baglantiKartlari("bulut", { ...taban, model })[1].durum, "hazir");
});

test("0.15.0: bulut modu saklanır ve geri okunur", () => {
  assert.equal(baslangicModu({ mod: "bulut" }), "bulut");
  assert.equal(MOD_ADI.bulut, "bulut · beta");
});

test("0.15.0 hız testi: hızlı telefonda öneri yok", () => {
  assert.equal(hizYorumu(0.3, "small").oneri, null);
  assert.equal(hizYorumu(0.3, "small").durum, "hizli");
  assert.equal(hizYorumu(0.9, "small").durum, "yeterli");
});

test("0.15.0 hız testi: yavaşsa önce küçük model, o da yavaşsa bilgisayar/bulut", () => {
  const a = hizYorumu(1.6, "small");
  assert.equal(a.oneri, "base");
  assert.match(a.metin, /16 saniyede/);
  assert.match(a.metin, /60 MB/);
  assert.equal(hizYorumu(1.6, "base").oneri, "pc");
});

test("0.15.0: işlemci desteklemiyorsa ya da test çöktüyse öneri", () => {
  assert.equal(hizYorumu(99, "small", { uyumsuz: true }).oneri, "pc");
  assert.equal(hizYorumu(99, "small", { cokme: true }).oneri, "base");
  assert.equal(hizYorumu(99, "base", { cokme: true }).oneri, "pc");
});

test("0.15.0: desteklemeyen işlemcide model kartı bunu söyler", () => {
  const model = { hazir: true, indiriliyor: false, ad: "small", uyumsuz: true };
  assert.equal(baglantiKartlari("mobil", { ...taban, model })[0].satir, "Bu telefonda çalışmaz");
});

test("0.15.0: small'ı çok hızlı çeviren telefona large-v3-turbo önerilir; turbo ağırsa small'a dönülür", () => {
  assert.equal(hizYorumu(0.12, "small").oneri, "turbo");
  assert.equal(hizYorumu(0.35, "small").oneri, null);
  assert.equal(hizYorumu(0.4, "turbo").oneri, null);
  assert.equal(hizYorumu(1.4, "turbo").oneri, "small");
  assert.equal(hizYorumu(99, "turbo", { cokme: true }).oneri, "small");
});
