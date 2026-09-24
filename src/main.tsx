import { StrictMode } from "react";
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

createRoot(document.getElementById("kok")!).render(
  <StrictMode>
    <Uygulama />
  </StrictMode>,
);
