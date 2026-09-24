---
name: Yüzük
description: Claudian night desk on the phone; the record control is a ring that draws what it hears.
colors:
  zemin: "#0e0e10"
  yuzey: "#16161a"
  yuzey-2: "#1c1c21"
  cizgi: "#26262c"
  cizgi-2: "#33333b"
  yazi: "#f2f0eb"
  soluk: "#8d8a83"
  kor: "#d97757"
  kor-basili: "#c4663f"
  kor-yazi: "#0e0e10"
  hata-zemin: "#251a18"
  hata-cizgi: "#684235"
  hata-yazi: "#edb7a5"
typography:
  wordmark:
    fontFamily: "Poppins, Arial, sans-serif"
    fontSize: "22px"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.03em"
  headline:
    fontFamily: "Poppins, Arial, sans-serif"
    fontSize: "24px"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "-0.02em"
  title:
    fontFamily: "Poppins, Arial, sans-serif"
    fontSize: "15px"
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: "Lora, Georgia, serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.75
  reading:
    fontFamily: "Lora, Georgia, serif"
    fontSize: "17px"
    fontWeight: 400
    lineHeight: 1.85
  control:
    fontFamily: "Poppins, Arial, sans-serif"
    fontSize: "13px"
    fontWeight: 600
    lineHeight: 1
  meta:
    fontFamily: "Poppins, Arial, sans-serif"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: 1.6
  label:
    fontFamily: "\"JetBrains Mono\", ui-monospace, monospace"
    fontSize: "11px"
    fontWeight: 400
    lineHeight: 1
    letterSpacing: "1.5px"
  counter:
    fontFamily: "\"JetBrains Mono\", ui-monospace, monospace"
    fontSize: "34px"
    fontWeight: 500
    lineHeight: 1
    letterSpacing: "-0.02em"
    fontFeature: "tnum"
  data:
    fontFamily: "\"JetBrains Mono\", ui-monospace, monospace"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: 1.6
    fontFeature: "tnum"
rounded:
  control: "6px"
  indicator: "15px"
  round: "50%"
spacing:
  edge: "20px"
  row: "16px"
  row-settings: "18px"
  section: "30px"
  nav-height: "68px"
  touch: "48px"
components:
  button-secondary:
    backgroundColor: "{colors.yuzey-2}"
    textColor: "{colors.yazi}"
    typography: "{typography.control}"
    rounded: "{rounded.control}"
    padding: "0 18px"
    height: "48px"
  button-primary:
    backgroundColor: "{colors.kor}"
    textColor: "{colors.kor-yazi}"
    typography: "{typography.control}"
    rounded: "{rounded.control}"
    padding: "0 18px"
    height: "48px"
  button-primary-pressed:
    backgroundColor: "{colors.kor-basili}"
    textColor: "{colors.kor-yazi}"
  text-action:
    textColor: "{colors.soluk}"
    padding: "12px 0"
    height: "44px"
  text-action-ember:
    textColor: "{colors.kor}"
    padding: "12px 0"
    height: "44px"
  choice:
    backgroundColor: "{colors.yuzey}"
    textColor: "{colors.yazi}"
    rounded: "{rounded.control}"
    height: "48px"
  choice-selected:
    backgroundColor: "{colors.yuzey-2}"
    textColor: "{colors.yazi}"
  input-code:
    backgroundColor: "{colors.yuzey}"
    textColor: "{colors.yazi}"
    rounded: "{rounded.control}"
    padding: "0 12px"
    height: "48px"
  reading-panel:
    backgroundColor: "{colors.yuzey}"
    textColor: "{colors.yazi}"
    typography: "{typography.reading}"
    rounded: "{rounded.control}"
    padding: "18px"
  error-note:
    backgroundColor: "{colors.hata-zemin}"
    textColor: "{colors.hata-yazi}"
    rounded: "{rounded.control}"
    padding: "12px 14px"
  nav-bar:
    backgroundColor: "{colors.zemin}"
    textColor: "{colors.soluk}"
    height: "68px"
  nav-indicator-active:
    backgroundColor: "{colors.yuzey-2}"
    textColor: "{colors.kor}"
    rounded: "{rounded.indicator}"
    width: "56px"
    height: "30px"
---

# Design System: Yüzük

## Overview

**Creative North Star: "The Ring on the Night Desk"**

Yüzük inherits the Claudian desktop world. `C:\Users\Boran Birtanır\Desktop\Claudian_app\desktop\DESIGN.md` is the parent authority for palette, the three families, the `claudian.app` wordmark with its ember period, orange focus, and the rules against invented emblems, decorative gradients and hand-authored animation. This file does not restate or override those rules. It records only what the phone adds or adapts: the ring instrument, the four-stop pipeline track, the bottom navigation bar, touch sizes, edge-to-edge insets, haptics, and the use of a motion library. If the two files ever conflict on a shared rule, the desktop file wins, and this one gets updated to match.

On the phone the world is a near-black desk ruled with hairlines. There are no cards. The one piece of theatre is the ring, which is the record control. At rest it is a dim circle. While recording, its circumference turns ember and grows radial ticks drawn from the real microphone level. Older ticks step back counter-clockwise and fade, and the ring stays flat during silence. Everything else is quiet: Lora sentences say what is happening, Poppins names things, and JetBrains Mono carries time, counts, codes and paths.

Density is low and made for one hand. The page is a single column capped at 560px, with a 20px edge and a fixed three-tab bar at the bottom. Controls are 48px tall.

**Key Characteristics:**
- Inherited palette, families and wordmark. The phone adds no new brand colour. The error tones are a dimmed ember, not a new red.
- The ring is the record button, and its ember marks are data, not ornament.
- Hairline rows (#26262c) instead of cards. A raised surface appears only where something can be acted on or read aloud.
- Ember is reserved for live recording, the primary action, the active tab and "done" state.
- Edge to edge: status and navigation bars sit on the ground colour, and content respects the safe-area insets.

## Colors

The desktop night palette, used unchanged, with one ember accent plus a pressed tone and a dimmed-ember error family added for the phone.

### Primary
- **Ember** (kor): The only accent. It is used for the live ring (base circle and level ticks), the reading-progress arc, primary buttons, the active tab icon, the tab badge and dot, the "passed" stops on the pipeline track, positive status words ("obsidian'da", "bağlı", "seçildi"), the connected dot, the text caret and focus outlines.
- **Pressed Ember** (kor-basili): Only for the pressed state of a primary button. Android has no hover, so press is the only state change.
- **Ink on Ember** (kor-yazi): Text colour on ember fills. It equals the ground colour.

### Neutral
- **Night Ground** (zemin): The app background. It also fills the Android window, splash, status bar, navigation bar, launcher icon background and `theme-color`.
- **Desk Surface** (yuzey): Fill for actionable or readable surfaces: inputs, choice tiles, the setup-incomplete row, and the reading panel.
- **Raised Surface** (yuzey-2): Secondary buttons, the selected choice tile and the active-tab pill.
- **Hairline** (cizgi): Row dividers, the header rule, the nav-bar top rule and the reading-panel border.
- **Strong Hairline** (cizgi-2): Control borders, the idle ring circle, unreached track stops and connectors, and inactive dots.
- **Paper Text** (yazi): Primary text and icons.
- **Muted Text** (soluk): Lora explanations, metadata, inactive tabs, text actions and mono labels.

### Error
- **Error Ground / Error Rule / Error Text** (hata-zemin, hata-cizgi, hata-yazi): Used for the alert note, failed session text and the failed stop on the track. The hue is ember's own (about 39°), desaturated. Failure reads as a wounded ember, not as a second accent.

### Named Rules
**The One Ember Rule.** Ember marks only what is live, what is the next action, or what has arrived. Muted text never turns ember for emphasis, and emphasis inside a Lora sentence uses Paper Text instead.

**The Ground-to-Glass Rule.** The system bars, window, splash and icon background are all Night Ground. The app never shows a second dark colour behind the OS chrome.

## Typography

**Display / UI Font:** Poppins (with Arial)
**Body Font:** Lora (with Georgia)
**Data Font:** JetBrains Mono (with ui-monospace)

**Character:** Poppins names things and acts, Lora explains in full sentences, and Mono keeps the ledger. The split follows the desktop and holds on every screen.

### Hierarchy
- **Wordmark** (700, 22px, -0.03em): Only `claudian.app` with the ember period, in the header. It grows to 34px in the empty state.
- **Headline** (600, 24px, 1.2, balanced): One per screen title (Notlar, Ayarlar, Sesini tanıt).
- **Title** (600, 15px, 1.3): Settings row names and session titles (1.35 line height).
- **Body** (Lora 400, 14px, 1.75, max 62ch): Explanatory sentences in Muted Text. Settings rows use 13px. Footnotes use 12px at 1.8.
- **Reading** (Lora 400, 17px, 1.85): Only for the voice-enrolment passage the user reads aloud.
- **Control** (600, 13px): Button labels. Text actions and tab labels step down to 12px and 11px.
- **Meta** (Poppins 500, 11–12px): Session meta lines, status words and settings tags.
- **Label** (Mono 400, 11px, 1.5px tracking, uppercase): Data captions only. These are the product name beside the wordmark, day groupings in the note list ("BUGÜN"), and the date or "dinliyor" state inside the ring.
- **Counter** (Mono 500, 34px, tabular): The recording timer at the ring's centre. It is 18px in the small ring.
- **Data** (Mono 400, 12px, tabular): Times, durations, server host, vault path, codes (15px with 0.12em tracking in the pairing input) and usage counts.

### Named Rules
**The Three Voices Rule.** A sentence is Lora, a name or verb is Poppins, and a number, time, path or code is Mono. Don't set a full sentence in Mono or a timestamp in Lora.

**The 11px Floor Rule.** Functional text never goes below 11px. This is inherited from the desktop and holds for badges and tab labels.

## Layout

The layout is one column, `min(100%, 560px)`, centred, with a 20px horizontal edge. The header is a single row: wordmark on the left, mono product label on the right, a hairline rule below it, and top padding of `safe-area-inset-top + 14px`. Content bottom padding clears the tab bar: `68px + safe-area-inset-bottom + 28px`.

Insets come from Capacitor SystemBars in CSS mode (`--safe-area-inset-*`, falling back to `env()`), with `viewport-fit=cover`. The app draws edge to edge, and only the header and tab bar absorb the insets.

Rhythm: session rows use 16px vertical padding and settings rows use 18px. Sections are separated by 30px. Day groups open with 22px above their label. Row actions sit 22px apart horizontally. Choice tiles form a three-column grid with an 8px gap.

On the Dinle screen, the ring is centred at `min(68vw, 300px)`, followed by one centred Lora state sentence (max 34ch), one full-width secondary button, then the latest session. Full-screen flows (Sesini tanıt) hide the tab bar and open with a 48px back button.

## Elevation & Depth

The system is flat. Depth comes from tonal steps (Night Ground → Desk Surface → Raised Surface) and hairlines, never from drop shadows. The only `box-shadow` in the build is a 3px ember halo around the current pipeline stop, and it works as a state ring rather than elevation.

### Shadow Vocabulary
- **Current-stop halo** (`box-shadow: 0 0 0 3px color-mix(in srgb, #d97757 22%, transparent)`): Only on the pipeline stop that is in progress.

### Named Rules
**The Hairline-Not-Card Rule.** Lists and settings are rows separated by 1px Hairline. A filled surface appears only for something you type into, choose, tap to fix, or read aloud.

## Shapes

Controls, inputs, tiles, the reading panel and the error note use gently squared corners (6px), matching the desktop. The active-tab indicator is a 56×30 pill (15px radius), taken from the Material navigation bar and filled with Raised Surface instead of a tint. Everything that stands for the ring or a point on it is a true circle: the ring itself, track stops (9px), status dots (7px), the tab dot and the back button's press area. Focus outlines are 2px ember with a 3px offset and 6px radius.

## Components

### Buttons
- **Shape:** Gently squared (6px), at least 48px tall, 18px side padding, 8px icon gap.
- **Secondary (default):** Raised Surface fill with a Strong Hairline border and Paper Text. Pressing turns the border ember. On Dinle it is used full width for "Dosyadan not çıkar".
- **Primary:** Ember fill and border with Ink on Ember text. Pressed uses Pressed Ember. Use at most one primary per view: Eşleştir, Tamam, or "Bitti, sesimi tanıt".
- **Disabled:** 45% opacity.
- **Text action:** No fill, Muted Text, 12px Poppins 600, 44px tall. The ember variant is for the recovering action (Tekrar dene, Bağlantıyı dene, Değiştir). Pressing turns it Paper Text.

### Choice tiles
- **Style:** Desk Surface, Strong Hairline border, 6px, 48px tall, three per row. An optional Mono subline sits in Muted Text.
- **State:** The selected tile (`aria-checked`) gets an ember border on Raised Surface. The radiogroup is the only selection pattern.

### Inputs
- **Style:** Desk Surface, Strong Hairline border, 6px, 48px tall. Pairing codes use Mono 15px with 0.12em tracking. Plain fields use Mono 13px with no tracking. The caret is ember and the placeholder is Muted Text at 70%.
- **Focus:** The global ember focus outline.

### Navigation (bottom bar)
- **Style:** Fixed Material-style bar with three equal destinations (Dinle, Notlar, Ayarlar). Height is 68px plus the bottom inset. Night Ground with a Hairline top rule. 20px Lucide icons over 11px Poppins 600 labels.
- **Active:** The label turns Paper Text, and the icon sits in a Raised Surface pill with an ember stroke.
- **Signals:** An ember count badge (Mono 11px, 16px tall) shows pending sessions on Notlar. A 7px ember dot shows "recording elsewhere" on Dinle or "setup incomplete" on Ayarlar.
- The bar is hidden during full-screen flows, and system Back leaves the flow.

### Session row
The unit of the note list. The top line has the time and duration in Mono on the left and a status word on the right (Paper Text, ember when in Obsidian, Error Text on failure, muted when there was no conversation). Below it come a 15px Poppins title, a Poppins meta line joined with " · ", an optional Lora summary, and the pipeline track. There is no container, only a bottom hairline.

### Pipeline track (signature)
Four equal columns (telefonda · bulutta · yazılıyor · Obsidian'da). Each stop is a 9px circle joined by a 1px connector, with an 11px Poppins label.
- **Passed:** Ember-filled circle and ember connector.
- **Current:** Hollow circle with an ember border and the 3px ember halo, label in Paper Text.
- **Failed:** Error Rule circle on Error Ground, label in Error Text.
- **Pending:** Strong Hairline circle on Night Ground, muted label.

### Ring instrument (signature)
The record control is a `<button>` with a canvas behind a centred label stack. The whole ring is the tap target (`aria-pressed` reflects recording).
- **Idle:** A 1.5px Strong Hairline circle at 78% of the radius, holding a 30px mic icon, "Dinle" in 18px Poppins 600, and today's date as a mono Label.
- **Recording:** The circle turns 2px ember. Up to 120 radial ticks (30 s at one reading every 250 ms) are drawn outward from the circle. Tick length follows the real level. The newest tick is at 12 o'clock and older ticks step counter-clockwise, fading to 12% opacity. The centre shows the Mono counter, "dinliyor" as an ember Label, and a 16px ember stop square.
- **Reduced motion:** Ticks are replaced by one 3px ember arc proportional to the current level.
- **Small ring (voice enrolment):** 116px wide, 18px counter. A 4px ember progress arc from 12 o'clock shows how much of the target reading time has passed, and the base circle stays dim while the arc is present.
- **Haptics:** Starting a recording fires a Medium impact and stopping fires a Light impact (Capacitor Haptics). This confirms the tap for a phone that goes straight into a pocket.

### Setup row, error note, reading panel
- **Setup row:** A full-width button on Desk Surface with a Strong Hairline border and 6px radius. A Poppins title and Lora sentence sit on the left, with an ember arrow on the right. It appears only while setup is incomplete.
- **Error note:** Error Ground, Error Rule border, Error Text in 12px Poppins 500, 6px radius, announced with `role="alert"`.
- **Reading panel:** Desk Surface with a Hairline border, 18px padding, and Reading type in Paper Text.

### Motion
Motion uses the third-party `motion` library, and only for state transitions. Screen swaps fade from 6px below over 180 ms with ease-out `cubic-bezier(0.16, 1, 0.3, 1)`. Session rows fade in and out over 180 ms with layout animation. Motion follows the system reduced-motion setting (`MotionConfig reducedMotion="user"`). There is no page-load choreography and no CSS keyframes. The ring's ticks are a data plot redrawn per reading, not an animation. A Lottie slot for the "note written" moment remains open until a licensed asset exists.

## Do's and Don'ts

### Do:
- **Do** treat the Claudian desktop DESIGN.md as the parent. Take palette, families, wordmark and focus from it, and record only phone additions here.
- **Do** make the ring the record control, and keep its marks tied to the real microphone level (flat during silence).
- **Do** give every tappable element at least 48px (text actions 44px) and keep functional text at 11px or larger.
- **Do** paint the status bar, navigation bar, window, splash and launcher background in Night Ground, and pad only the header and tab bar with safe-area insets.
- **Do** show pipeline progress with the four-stop track on every session, and show failure in the error family on the stop where it happened.
- **Do** confirm start and stop of recording with a haptic impact.
- **Do** keep transitions at 150–250 ms ease-out through the motion library, and honour reduced motion.

### Don't:
- **Don't** add a red record circle, a second accent, or a separate error red. Failure uses the dimmed-ember error family.
- **Don't** wrap rows in cards or add drop shadows. Use hairlines and tonal steps.
- **Don't** use the mono uppercase Label as a kicker or eyebrow above a headline. It captions data only (product name, day, ring state).
- **Don't** invent an emblem for the app icon. The launcher icon is the `claudian.app` wordmark on Night Ground.
- **Don't** hand-author CSS keyframes or page-load animation.
- **Don't** fake level ticks or progress. If there is no reading, the ring stays still.
