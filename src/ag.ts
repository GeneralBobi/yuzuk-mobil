/** Bir ağ isteği kuyruğu sonsuza kadar kilitlemez. Android WebView için AbortController. */
export async function agIstek(url: string, init: RequestInit = {}, sureMs = 35_000): Promise<Response> {
  const denetleyici = new AbortController();
  const zaman = setTimeout(() => denetleyici.abort(), sureMs);
  try {
    const r = await fetch(url, { ...init, signal: denetleyici.signal });
    // Zaman aşımı yalnız başlıklara değil gövdenin alınmasına da uygulanır.
    const govde = await r.arrayBuffer();
    return new Response([204, 205, 304].includes(r.status) ? null : govde, { status: r.status, statusText: r.statusText, headers: r.headers });
  } catch (e) {
    if (denetleyici.signal.aborted) throw new Error("Bağlantı zaman aşımına uğradı; yeniden denenecek.");
    throw e;
  } finally {
    clearTimeout(zaman);
  }
}
