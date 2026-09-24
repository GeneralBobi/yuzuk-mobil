---
version: 1
slug: "src-uygulama-tsx"
primary_target: "src/Uygulama.tsx"
related_targets: ["src/stil.css"]
---

# Yüzük mobil — surface brief

Scope: the whole Android app (Dinle, Notlar, Ayarlar, "Sesini tanıt" flow, pairing). Visitor mode: **Operate**.
Audience/job: Boran and 12 beta testers start a recording in one tap in a lecture, meeting or street, pocket the
phone, and later find the note in Obsidian. Task frequency: several times a day, seconds per visit.
Constraints: Claudian desktop world is pinned (desktop/DESIGN.md): #0e0e10 ground, #16161a / #1c1c21 surfaces,
#26262c lines, #f2f0eb text, #8d8a83 muted, #d97757 accent; Poppins 600/700, Lora 400/500, JetBrains Mono;
`claudian.app` wordmark with orange period; no invented emblem, no decorative gradient. Android: bottom navigation
bar (3 destinations), system Back works, edge-to-edge insets, 48 dp targets.
Unattended run: Boran asked Kled to decide alone (24.09.2026); the dealt lead was taken without a decision page.

## Direction contract

THESIS: The phone is a stand-in for the ring, so the record control is a ring. The category default is a red
circle button over a list; this surface refuses it: the ring is the instrument, and while listening its
circumference becomes a live radial trace of the real microphone level.

OWN-WORLD: Claudian night desk. Near-black ground, one ember accent reserved for the ring's live arc, primary
actions and state. Poppins for labels and titles, Lora for the explanatory sentences (the desktop's voice),
JetBrains Mono for time, durations, codes and counts. Hairline #26262c rules instead of cards; one raised surface
only where something is actionable.

STORY: Open → the ring waits in the middle of the Dinle screen with today's date. Tap → the ring ignites and draws
what it hears; timer in mono at its center. Stop → the session drops into the list below and walks a four-stop
track (telefonda · bulutta · yazılıyor · Obsidian'da). Later the note line reads "2 kişi · sen %30 · ChatGPT".

FIRST VIEWPORT: Header: `claudian.app` wordmark left, mono caption YÜZÜK right. Center: ring ~68% of screen width,
tap target is the whole ring. Under it one Lora sentence of state. Below: "Dosyadan not çıkar" as an outlined
button, then the latest session with its track. Bottom navigation bar Dinle / Notlar / Ayarlar.

FORM: Ring instrument (my list #7 of 7; fused with #4 full-attention live state and #5 pipeline track). Seed e922b041.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

Signature interaction: the live radial level trace on the ring (canvas, driven by the native recorder's level
reading every 250 ms; reduced motion → a static filled arc proportional to the current level).
Motion grammar: third-party `motion` library for state transitions only, 150–250 ms ease-out; no page-load
choreography; Lottie slot for the "note written" moment once a LottieFiles asset is licensed (LottieFiles MCP
needs Boran's sign-in).
