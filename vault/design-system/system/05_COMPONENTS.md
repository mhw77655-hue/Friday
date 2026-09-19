# 05 — Components

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §8 (Interface Language), §17 (Component Library), §8.6 (Depth System)
**Status:** Permanent — frozen by the Visual Bible. Every component inherits Materials (§02), Depth (§08_MOTION), and Typography (§03) by default; only deviations are noted here.

---

## 1. Governing Principle

Every component is a **satellite of the reactor.** Complexity lives in the object and the data; simplicity lives in the frame. Panels are sparse, labeled plainly, and never compete with the focal object for attention (§1.3).

Three system-wide inheritance rules:
1. **Depth:** every surface sits at exactly one of the five layers (§8.6) — only one Layer-4 focal object per screen.
2. **Material:** every surface belongs to exactly one of the five materials (§02).
3. **Typography:** every panel uses the three-tier hierarchy (§03 §4).

---

## 2. The Five-Layer Depth System (§8.6)

| Layer | Contents | Depth cue |
|---|---|---|
| 0 — Void | Background | Void Black, no light |
| 1 — Ambient | Bloom/haze, background particles | Softest focus, lowest opacity |
| 2 — Structure | Base plates, chassis, panel backgrounds | Sharp but dim |
| 3 — Content | Text, icons, data, panel foregrounds | Full sharpness, full brightness |
| 4 — Core/Focus | The reactor, or the single active focal object | Brightest, sharpest, only bloom-eligible layer |

**Hard rule:** Only one object may occupy Layer 4 per screen. This structurally prevents two competing focal points and enforces "invisible until needed" (§1.5). Only Critical state (§9.11) may forcibly simplify the whole screen around the reactor.

---

## 3. Containers

### 3.1 Windows (§8.1)

Full-screen or large sub-views.
- **Material:** Glass (§02 §2.1).
- **Frame:** thin hairline border in the current state color at ~20% opacity — **never a solid filled border**.
- **Depth:** occupies Layer 3 as a region; never competes for Layer 4.
- **Transition:** panel-in/panel-out easings over `--dur-transition` (see §08_MOTION).

### 3.2 Panels (§8.2) — the primary content unit

- **Shape:** rectangular, generous internal padding (≥ 16px, `--space-4`).
- **No drop shadows** — depth is expressed via light/glow, not shadows.
- **One accent icon per panel**, top-left, matching the panel's subsystem color (single-accent-color discipline, §1.3).
- **Typography:** the strict three-tier hierarchy (§03 §4).
- **Nesting:** **panels never nest more than one level deep** — a panel may contain a data row or small chart, never another full panel.
- **Depth:** Layer 3 (panel background at Layer 2, content at Layer 3).

### 3.3 Cards (§8.3, §17.7)

Smaller, self-contained, tappable/interactive units (ecosystem device row, material-language swatches, selectable grids).
- **Structure:** key visual (icon, swatch, or thumbnail) **centered above** a two-line label/description pair.
- **Typography:** panel hierarchy at reduced scale — label tier + one body line (two tiers, not three).
- **Material:** Glass.
- **Selection:** selected card's hairline border + accent shift to the subsystem/state color; nothing else changes shape or elevates.

---

## 4. Floating Elements (§8.4)

Any element not anchored to the base grid — holographic callouts, in-space labels.
- **Always include a faint anchor line** back to the real-world/data point they describe. A floating element with no visible relationship is forbidden.
- **Slight independent drift:** a few px of slow, looping motion reinforcing a lightly perturbed 3D field, not a pinned flat-2D layer.
- **Depth:** Layer 3 (floating), never Layer 4.
- **Material:** Hologram wireframe + sparse Glass data callout.

---

## 5. Interactive Components

### 5.1 Buttons (§17.1)
- **Material:** Glass.
- **Border:** hairline border in state color at 20% opacity.
- **Label:** `--font-label` or `--font-body`.
- **Press feedback:** micro-duration brightness pulse (§12.1) in state color — **no shape change, no shadow**.
- **Never filled/solid.** A filled button reads as flat-UI and breaks material consistency.

### 5.2 Inputs (§17.2)
- **Material:** Glass.
- **Border:** **bottom-hairline only** (no full box border) in `--steel`, brightening to state color on focus.
- **Caret:** pulses at the ambient breathing rate (§3.3) — **not** a generic browser blink rate. Even the text cursor obeys the reactor's heartbeat.
- **Placeholder:** `--steel` caption-weight text; never a styled illustration.

### 5.3 HUD (§17.3)
- Floating, anchored `data-mono` readouts per §7.5 and §8.4.
- **Always Layer 3, never Layer 4** — HUD data supports the focal object, it is never the focal object itself.
- Right-aligned to its data point, with a thin connecting line back.

### 5.4 Notifications (§17.4)
- **Material:** Glass panel.
- **Entry:** slides in from a screen edge over `--dur-transition`.
- **Content:** icon + two-tier text (label + one line — reduced from the three-tier hierarchy).
- **Sound:** the appropriate Notification sound (§11, see `11_SOUND.md` §4).
- **Dismiss:** auto-dismiss fades via `--ease-panel-out`; manual dismiss slides out.

### 5.5 Dialogs (§17.5)
- **Full Window treatment** (§8.1) — glass, hairline state border.
- **If the dialog concerns a system action** (confirmation, warning): it **always centers a state-colored reactor mini-render at its top**. Dialogs are never purely typographic when a state is involved.
- Buttons inside a dialog follow §5.1 (the confirmation is the state-colored button).

---

## 6. Data Surfaces

### 6.1 Maps / Spatial Views (§17.6)
- **Material:** Hologram — wireframe terrain/paths, sparse glass data callouts anchored via §4.
- **Never rendered as a flat opaque map image**; always reads as a JARVIS-native projection.
- Terrain/route lines follow the energy-transmission rule (§4.5): visible light flow along paths.

### 6.2 Charts (§17.8)
- **Axis labels:** `--font-data-mono`.
- **Series:** a single state-colored line/series by default. Multi-series only when explicitly comparative, and even then limited to **state-family colors** — never arbitrary chart-library colors.
- **Background:** always transparent Glass, never a filled plot area.
- **No fake telemetry (§18):** every number represents a real value. If a real value is unavailable, the field is visibly empty/pending — never faked.

### 6.3 Terminal / Log Views (§7.4)
- `data-mono` on a near-black panel (`--deep-space`).
- Thin single-pixel **state-colored left border** indicating log severity.
- Max two colors: `--steel` base text + one state accent for flagged lines.

---

## 7. The Assistant Overlay (§17.9)

The conversational surface itself. **This is the one component that may combine Reactor + Waveform + Text simultaneously as a fixed trio** — everywhere else these three elements are used independently per context.

| Position | Element |
|---|---|
| Fixed, anchored | Reactor mini-render (current state) |
| Directly beneath it (when active) | Voice Waveform (§12_VOICE_VISUALIZER) |
| Beneath both | Response text in `--font-body` |

---

## 8. Component Availability by Context

Progressive disclosure governs density (§8.6, §13). The same components exist everywhere; their *count* and *size* scale with the device, never their design:

| Context | Panels visible | Depth layers active | HUD/terminal |
|---|---|---|---|
| Watch | 0 (state via color + breathing) | 0–2 | No |
| Phone | 1 primary at a time | 0–4 on demand | Limited |
| Tablet | 2–3 in a defined grid | All 0–4 | Available |
| Desktop | Unrestricted (multi-window) | All; multiple Layer-3 regions allowed | Full |
| AR/VR | Spatial panels at near/mid/far | Volumetric | Spatial HUD |

Per-device specs: `13_PHONE_UI.md` … `17_AR_UI.md`.

---

## 9. Depth-by-Role Enforcement Table

| Component | Layer |
|---|---|
| Reactor (when present) | 4 |
| Active focal hologram object | 4 (sole focal object) |
| Panel/window/card/notification content | 3 |
| Panel/window/card/notification backgrounds | 2 |
| Base plates, chassis, device bodies | 2 |
| Bloom, haze, background particles | 1 |
| Background | 0 |

---

## 10. Absolute Prohibitions (component-level)

- No filled/solid buttons or opaque UI chrome.
- No nested panels beyond one level.
- No floating element without an anchor line.
- No more than one accent color per panel.
- No two simultaneous Layer-4 focal objects.
- No fake telemetry in any chart, readout, or card.
- No drop shadows on any container.

---

## 11. Token Reference

Component behavior relies on the tokens in `20_DESIGN_TOKENS.md` (durations, easings, opacity, spacing, type). This document is the assembly contract.
