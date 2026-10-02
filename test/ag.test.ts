import { test } from "node:test";
import assert from "node:assert/strict";
import { agIstek } from "../src/ag.ts";

test("bekleyen başlık isteği zaman aşımıyla serbest kalır", async () => {
  const eski = globalThis.fetch;
  globalThis.fetch = async (_url, init) => new Promise((_resolve, reject) => {
    init?.signal?.addEventListener("abort", () => reject(new Error("aborted")), { once: true });
  });
  try { await assert.rejects(agIstek("https://example.test", {}, 10), /zaman aşımı/); }
  finally { globalThis.fetch = eski; }
});

test("başlık geldikten sonra bekleyen gövde de zaman aşımına dahildir", async () => {
  const eski = globalThis.fetch;
  globalThis.fetch = async (_url, init) => new Response(new ReadableStream({
    start(c) { init?.signal?.addEventListener("abort", () => c.error(new Error("aborted")), { once: true }); }
  }));
  try { await assert.rejects(agIstek("https://example.test", {}, 10), /zaman aşımı/); }
  finally { globalThis.fetch = eski; }
});

test("hata gövdesi ve boş başarılı yanıt korunur", async () => {
  const eski = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response('{"detail":"Kota doldu"}', { status: 429 });
    const r = await agIstek("https://example.test");
    assert.equal(r.status, 429); assert.equal((await r.json()).detail, "Kota doldu");
    globalThis.fetch = async () => new Response(null, { status: 204 });
    assert.equal((await agIstek("https://example.test")).status, 204);
  } finally { globalThis.fetch = eski; }
});
