import { Component, StrictMode, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
// Claudian masaüstüyle aynı aileler, uygulamanın içinde (çevrimdışı da aynı görünür).
import "@fontsource/poppins/500.css";
import "@fontsource/poppins/600.css";
import "@fontsource/poppins/700.css";
import "@fontsource/lora/400.css";
import "@fontsource/lora/500.css";
import "@fontsource/jetbrains-mono/400.css";
import "@fontsource/jetbrains-mono/500.css";
import "./stil.css";
import { Uygulama } from "./Uygulama";

// Bir hata arayüzü düşürürse siyah ekran kalmasın: ne olduğu yazılır, uygulama yeniden açılabilir.
class HataSiniri extends Component<{ children: ReactNode }, { hata: Error | null }> {
  state = { hata: null as Error | null };
  static getDerivedStateFromError(hata: Error) {
    return { hata };
  }
  render() {
    if (!this.state.hata) return this.props.children;
    return (
      <main className="icerik" style={{ paddingTop: "calc(var(--ust) + 32px)" }}>
        <h1>Bir şey ters gitti</h1>
        <p className="aciklama">Kayıtların telefonda duruyor. Uygulamayı kapatıp yeniden aç; sorun sürerse bu yazıyı Kled'e gönder.</p>
        <pre className="hata" style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{String(this.state.hata?.stack ?? this.state.hata)}</pre>
        <button className="dugme birincil" style={{ marginTop: 16 }} onClick={() => location.reload()}>Yeniden aç</button>
      </main>
    );
  }
}

createRoot(document.getElementById("kok")!).render(
  <StrictMode>
    <HataSiniri>
      <Uygulama />
    </HataSiniri>
  </StrictMode>,
);
