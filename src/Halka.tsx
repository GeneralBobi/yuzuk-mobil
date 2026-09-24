// Halka: kayıt denetimi yüzüğün kendisi. Dinlerken çemberin üstüne mikrofonun gerçek ses düzeyi
// (yerel kaydedicinin 250 ms'lik okuması) ışınsal çizgiler olarak işlenir: en yeni okuma tepede,
// eskiler saat yönünün tersine kayar ve söner. Süs değil, veri: sessizlikte her okuma kısa bir çentik bırakır.
import { useEffect, useRef } from "react";

// Renkler stil.css'teki belirteçlerden okunur; tek kaynak orası.
const belirtec = (ad: string, yedek: string) =>
  (typeof getComputedStyle !== "undefined" && getComputedStyle(document.documentElement).getPropertyValue(ad).trim()) || yedek;
const OKUMA = 120; // 30 sn geçmiş (250 ms'de bir)

export function Halka(p: {
  kayitta: boolean;
  seviye: number; // 0..1
  okuma: number; // okuma sayacı: her yeni okuma bir çizgi (sessizlik de zamanı ilerletir)
  onClick: () => void;
  etiket: string;
  children: React.ReactNode;
  kucuk?: boolean;
  ilerleme?: number; // 0..1, verilirse çevrede ilerleme yayı (ses tanıtma)
}) {
  const tuval = useRef<HTMLCanvasElement>(null);
  const gecmis = useRef<number[]>([]);
  const azHareket = useRef(typeof matchMedia !== "undefined" && matchMedia("(prefers-reduced-motion: reduce)").matches);

  // Her yeni okumada geçmişe bir değer eklenir (hat 250 ms'de bir yayın yapar).
  useEffect(() => {
    if (!p.kayitta) {
      gecmis.current = [];
      return;
    }
    gecmis.current.push(Math.min(1, Math.max(0, p.seviye * 1.8)));
    if (gecmis.current.length > OKUMA) gecmis.current.shift();
  }, [p.okuma, p.kayitta]);

  useEffect(() => {
    const t = tuval.current;
    if (!t) return;
    const oran = window.devicePixelRatio || 1;
    const boyut = t.getBoundingClientRect().width;
    t.width = t.height = Math.round(boyut * oran);
    const c = t.getContext("2d")!;
    const KOR = belirtec("--kor", "#d97757");
    const CIZGI = belirtec("--cizgi-2", "#33333b");
    c.setTransform(oran, 0, 0, oran, 0, 0);
    const m = boyut / 2;
    const r = m * (p.kucuk ? 0.8 : 0.78);
    c.clearRect(0, 0, boyut, boyut);
    c.lineCap = "round";

    // Taban çember
    c.beginPath();
    c.arc(m, m, r, 0, Math.PI * 2);
    // İlerleme yayı çizilecekse taban sönük kalır; yay tek başına ne kadar kaldığını söyler.
    const tabanKor = p.kayitta && p.ilerleme === undefined;
    c.strokeStyle = tabanKor ? KOR : CIZGI;
    c.lineWidth = tabanKor ? 2 : 1.5;
    c.stroke();

    if (p.ilerleme !== undefined && p.ilerleme > 0) {
      c.beginPath();
      c.arc(m, m, r, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * Math.min(1, p.ilerleme));
      c.strokeStyle = KOR;
      c.lineWidth = 4;
      c.stroke();
    }

    if (!p.kayitta) return;
    const g = gecmis.current;
    const uzun = m - r - 4;
    if (azHareket.current) {
      // Azaltılmış hareket: yalnız şu anki düzey, çember üstünde duran bir yay.
      const son = g[g.length - 1] ?? 0;
      c.beginPath();
      c.arc(m, m, r + 3, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * son);
      c.strokeStyle = KOR;
      c.lineWidth = 3;
      c.stroke();
      return;
    }
    for (let i = 0; i < g.length; i++) {
      const yas = g.length - 1 - i; // 0 = en yeni
      const aci = -Math.PI / 2 - (yas / OKUMA) * Math.PI * 2;
      const boy = 2 + g[i] * uzun;
      const x1 = m + Math.cos(aci) * (r + 3), y1 = m + Math.sin(aci) * (r + 3);
      const x2 = m + Math.cos(aci) * (r + 3 + boy), y2 = m + Math.sin(aci) * (r + 3 + boy);
      c.beginPath();
      c.moveTo(x1, y1);
      c.lineTo(x2, y2);
      c.globalAlpha = Math.max(0.12, 1 - yas / OKUMA);
      c.strokeStyle = KOR;
      c.lineWidth = Math.max(1.5, (Math.PI * 2 * r) / OKUMA * 0.45);
      c.stroke();
    }
    c.globalAlpha = 1;
  });

  return (
    <button className={`halka ${p.kayitta ? "kayitta" : ""}`} onClick={p.onClick} aria-label={p.etiket} aria-pressed={p.kayitta}>
      <canvas ref={tuval} aria-hidden />
      <span className="halka-ic">{p.children}</span>
    </button>
  );
}
