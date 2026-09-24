// Halka: kayıt denetimi yüzüğün kendisi. Dinlerken çemberin üstüne mikrofonun gerçek ses düzeyi
// (yerel kaydedicinin 250 ms'lik okuması) ışınsal çizgiler olarak işlenir: en yeni okuma tepede,
// çentikler saat yönünde bir dakikalık turda dizilir, ibre dönmeye devam eder. Süs değil, veri: sessizlikte her okuma kısa bir çentik bırakır.
import { useEffect, useRef } from "react";

// Renkler stil.css'teki belirteçlerden okunur; tek kaynak orası.
const belirtec = (ad: string, yedek: string) =>
  (typeof getComputedStyle !== "undefined" && getComputedStyle(document.documentElement).getPropertyValue(ad).trim()) || yedek;
// Saat gibi: bir tur bir dakika (250 ms'de bir okuma, 240 çentik). İbre dönmeye devam eder; eski turun çentikleri
// yenileriyle üzerine yazılır. 24.09.2026: eski iz 30 sn'de dolup duruyordu ve kayıt 30 sn'de bitiyormuş gibi görünüyordu.
const OKUMA = 240;

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
  const gecmis = useRef<{ no: number; d: number }[]>([]);
  const sayac = useRef(0);

  // Her yeni okumada geçmişe bir değer eklenir (hat 250 ms'de bir yayın yapar).
  useEffect(() => {
    if (!p.kayitta) {
      gecmis.current = [];
      sayac.current = 0;
      return;
    }
    gecmis.current.push({ no: sayac.current++, d: Math.min(1, Math.max(0, p.seviye * 1.8)) });
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
    // "Animasyonları kaldır" açıkken de çentikler çizilir: bunlar süs değil, canlı veri (ses düzeyi ve geçen süre).
    // 24.09.2026: önceki "azaltılmış hareket" kipi yalnız anlık düzeyi gösteriyordu; animasyonu kapalı telefonda
    // halka boş görünüyordu.
    const adim = (Math.PI * 2) / OKUMA;
    for (let i = 0; i < g.length; i++) {
      const yas = g.length - 1 - i; // 0 = en yeni
      const aci = -Math.PI / 2 + (g[i].no % OKUMA) * adim; // saat yönünde, tepeden başlar
      const boy = 2 + g[i].d * uzun;
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
    // İbre: şu anki okumanın yeri; kayıt sürdükçe dönmeye devam eder.
    const bas = g[g.length - 1];
    if (bas) {
      const aci = -Math.PI / 2 + (bas.no % OKUMA) * adim;
      c.beginPath();
      c.arc(m + Math.cos(aci) * r, m + Math.sin(aci) * r, p.kucuk ? 3 : 5, 0, Math.PI * 2);
      c.fillStyle = KOR;
      c.fill();
    }
  });

  return (
    <button className={`halka ${p.kayitta ? "kayitta" : ""}`} onClick={p.onClick} aria-label={p.etiket} aria-pressed={p.kayitta}>
      <canvas ref={tuval} aria-hidden />
      <span className="halka-ic">{p.children}</span>
    </button>
  );
}
