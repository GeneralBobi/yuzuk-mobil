import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "app.claudian.yuzuk",
  appName: "Yüzük",
  webDir: "dist",
  android: { backgroundColor: "#0e0e10" },
  plugins: {
    // Kenardan kenara: sistem çubuklarının altı uygulamanın zemini; boşluklar --safe-area-inset-* ile.
    SystemBars: { insetsHandling: "css", style: "DARK", initialViewportFitValueHint: "cover" },
  },
};

export default config;
