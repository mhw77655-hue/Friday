# 19 — Icons

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §8.2 (one accent icon per panel, subsystem color), §5 (state-color language), §4.1 (glow tied to state), §1.3 (single-accent-color discipline), §18 (no decorative glyphs/holograms)
**Status:** Permanent. The icon language is derived from the Visual Bible's hairline + restraint + state-color rules and is an extension of them, not a new system.

---

## 1. Governing Principle

Icons in JARVIS are **precision line-art, not mascots.** They inherit the system's two signature formalisms: **hairline strokes** (the same 1px logic as dividers and borders) and **single-accent-color discipline** (one accent per panel, matching the panel's subsystem/state color).

Three laws:
1. **Icons are drawn in hairline strokes** — never filled, never solid chrome (§18: no flat/filled UI).
2. **An icon carries color only when the color has meaning** — subsystem/state color for status-bearing glyphs; `--steel` for neutral structure. Never both on one icon.
3. **No anthropomorphic iconography** — no faces, eyes, expressions (§15.7). JARVIS's only "character" is the reactor, and the reactor is never reduced to an icon (it is rendered as a miniature reactor, §07 §8).

---

## 2. Stroke & Grid

| Property | Spec |
|---|---|
| Stroke width | Hairline — 1px at 1x scale; scale proportionally (1.5px at 2x, 2px at 3x) |
| Grid | Icons snap to the 4px base grid; standard glyph canvas is 24×24px (6 × 4px) |
| Corner language | Precise angles and orbital curves; **no bouncy/organic curves** (§2.7, §15.7) |
| Optical weight | Consistent hairline weight across a set — mixing 1px and 3px strokes in one set is a spec error |
| Opacity | `--steel` icons at 0.75 base; state-colored icons at full accent value |

**Rule:** icons are monochrome line-art by default. A panel's **one accent icon** (top-left, §8.2) is the only color-bearing glyph in that panel.

---

## 3. Color Semantics

| Color | Icon meaning |
|---|---|
| `--steel` | Structural/neutral action or data (nav, generic buttons) |
| Subsystem color | The panel's subsystem accent icon (§8.2) |
| Current state color | Status-bearing glyphs that report system state — the icon glows only as a status indicator, tied to actual state (§4.1), never as decoration |
| `--ignition-white` | Reserved for the reactor ignition point only — **never** an icon fill (§5.6) |

**Rule:** an icon never glows on its own; only a status indicator tied to real state may carry state-colored glow (and only the single brightest element blooms, §4.2).

---

## 4. Icon Semantics by Family

| Family | Style | Example glyphs |
|---|---|---|
| **Structural / Navigation** | Hairline, `--steel` | home, back, settings, panel-close |
| **Subsystem accent** | Hairline, subsystem color, top-left of panel | energy matrix, quantum core, neural interface, presence |
| **Status / State** | Hairline, current state color; may carry state-tied glow | online, listening, processing, warning, critical, offline, success |
| **Data / Connector** | Hairline + traveling light pulse per §4.5 | sync link, data flow, transmission — the connector carries light in the direction of real flow |
| **Device / Ecosystem** | Hairline, device body treated as Metal (§02 §2.2) | watch, phone, tablet, desktop, glasses, display |

---

## 5. Status Icon Behavior

- A status icon's **glow/color follows the current state color exactly** — it is a readout, not a decoration.
- Warning/Critical icons pulse at the state's tempo (1Hz / 2Hz) **and always co-occur with an explicit text description** (§9.10) — the icon alerts, the text informs.
- Success icon appears **only** at confirmed task completion, as part of the success event pulse (§15.4), then returns to the state's icon set.

---

## 6. Reactor Representations

- The reactor is never an "icon" in the mascot sense. Where a small reactor is needed (dialogs §17.5, assistant overlay §17.9, infrastructure status §19.4), render a **literal miniature reactor** — ignition point + at least one ring + state color — using the same state/motion language (§07 §8).
- At the smallest sizes (watch floor, §16), the reactor reduces to ignition point + breathing; the state color carries the meaning.

---

## 7. Size Scale

| Use | Canvas | Stroke |
|---|---|---|
| Panel accent icon | 24px | 1.5px (for legibility at 2x) |
| Button / input icon | 20px | 1px |
| Card thumbnail | 40px | 2px |
| Status indicator | 16px | 1px |
| Ecosystem device row | 32px | 1.5px |
| Watch floor | 12px | 0.75px (single glyph max) |

---

## 8. Animation Rules for Icons

- Icons use `--dur-micro` (120ms) transitions for state change; the press/entrance is a brightness pulse, **no bounce** (§12.2).
- Connector icons animate their light pulse at actual data-flow direction/speed (§4.5).
- No idle spinning/wagging icons — energy is always a real signal (§18: no meaningless animation).

---

## 9. Absolute Prohibitions

- No filled/solid icons.
- No multicolor icons (one accent per icon; one accent per panel).
- No anthropomorphic iconography.
- No icon glow without a real state cause.
- No decorative connector pulses (light only travels with real flow).
- No icon substituting for a stated text description in alerts.

---

## 10. Token Reference

Icon behavior uses the system tokens in `20_DESIGN_TOKENS.md` (colors, `--dur-micro`, hairlines). The reactor mini-render rule is specified in `07_REACTOR.md` §8.
