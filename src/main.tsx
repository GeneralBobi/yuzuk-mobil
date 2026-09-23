import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource-variable/inter";
import "@fontsource/source-serif-4/500.css";
import "@fontsource/source-serif-4/600.css";
import "./stil.css";
import { Uygulama } from "./Uygulama";

createRoot(document.getElementById("kok")!).render(
  <StrictMode>
    <Uygulama />
  </StrictMode>,
);
