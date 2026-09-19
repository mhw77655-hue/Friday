# 03 — Typography

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §7 (Typography)
**Status:** Permanent — frozen by the Visual Bible.

---

## 1. Governing Principle

Typography in JARVIS has exactly three voices, each with a fixed job:

1. **Display / Wordmark** — wide-tracked, geometric, thin-to-regular weight sans. Technical, no humanist warmth. Reserved for the product wordmark and top-level screen titles **only**.
2. **UI / Body** — clean geometric-grotesque sans, regular/medium weight. Labels, descriptions, buttons. Must remain legible at small sizes on glass panel backgrounds.
3. **Technical / Data** — monospace. Any raw system output: telemetry, coordinates, logs, code, timestamps. **Monospace is the system's visual signal for "this is real machine data"** and must never be used for conversational or marketing text.

---

## 2. Font Stack Specification

| Voice | Stack (platform-agnostic) | Weight range |
|---|---|---|
| Display | Geometric sans, wide-tracked; reference is the "JARVIS" wordmark letterforms | Thin (100) – Regular (400) |
| UI / Body | Geometric-grotesque sans | Regular (400) / Medium (500) |
| Technical / Data | Monospace | Regular (400) |

**Implementation rules:**
- The three voices may map to distinct font families, but they must be visually coherent — the sans voices share geometric construction so the system reads as one family of type, not three unrelated fonts.
- Letterforms are evenly spaced and technical; **no humanist warmth** (no calligraphic curves, no high-contrast serifs) in the display voice.
- Never substitute serif or decorative type anywhere in the system.

---

## 3. Type Scale (base 4px)

| Token | Size | Voice | Usage |
|---|---|---|---|
| `--font-display-xl` | 48px | Display | Wordmark, splash |
| `--font-display` | 32px | Display | Screen titles |
| `--font-heading` | 20px | UI | Panel/section titles |
| `--font-label` | 12px | UI | Small-caps section labels — **uppercase, tracking +0.12em** |
| `--font-body` | 14px | UI | Descriptions, primary reading text |
| `--font-caption` | 11px | UI | Secondary/supporting text |
| `--font-data-mono` | 12px | Data (monospace) | Telemetry, HUD readouts |

The scale is **strictly 4px-based** — no intermediate sizes. If a size is needed, it is one of these tokens, never an arbitrary value.

---

## 4. The Three-Tier Panel Hierarchy (§7.3)

Every panel in the system uses a **strict three-tier text hierarchy, always in this order and this only**:

1. **Small-caps label** — *what this is.* Always the state/subsystem's plain name, always uppercase, always tracked wide (`--font-label`). Dim, structural.
2. **Short title/value** — *the headline fact.* Sentence case, brightest text weight in the group (`--font-heading`).
3. **Supporting description** — *one or two short declarative sentences, never a paragraph.* Dimmer, regular weight (`--font-body`).

**Hard rule:** No panel in the system may exceed this three-tier structure. If more information is needed, it belongs in a secondary/expanded view — never a denser primary panel.

### Example (from the reference panels)

```
CORE STATES          ← tier 1: small-caps label (uppercase, tracked)
Idle                 ← tier 2: short title, brightest
System online,       ← tier 3: one-line supporting description
nominal
```

---

## 5. Terminal Typography (§7.4)

Any raw system/debug output — logs, code diffs, error traces:

- Rendered in `--font-data-mono`.
- On a **near-black panel** (base `--deep-space`, never a mid-tone).
- With a **thin single-pixel state-colored left border** indicating the log's severity/state color (§5).
- **No syntax-highlighting rainbow.** Terminal output uses at most two colors:
  - Base text: `--steel` (`#8A94A6`)
  - Flagged lines: **one** state-color accent

---

## 6. HUD Typography (§7.5)

Spatial/HUD data (the "3D Spatial Logic," "Real-Time Data Stream" panels):

- `--font-data-mono` **exclusively**.
- **Right-aligned to its associated data point**, never centered.
- A **thin connecting line** runs back to the element it labels — labels float *near*, never *inside*, the object they describe (§8.4).
- HUD readouts always sit at Layer 3 — they support the focal object, they are never the focal object themselves (§17.3).

---

## 7. Text Color Contract

| Tier / voice | Color | Notes |
|---|---|---|
| Display / wordmark | `--ignition-white`-family (`#F4FBFF`) | On void/deep-space |
| Heading (headline fact) | Brightest in group — near-white | Tier 2 |
| Body / description | Dimmer — `--steel`-toward-white | Tier 3 |
| Label (small-caps) | `--steel` | Structural, quiet |
| Caption | `--steel` | Secondary |
| data-mono | `--steel` base; one state accent for flags | Terminal + HUD |
| Accent text on accent | Current state color at ≥4.5:1 | Only where color carries meaning |

**Rule:** text never appears in decorative hues. If a value is meaningful it may use its state color; otherwise it is white/steel family. Contrast minimums in `01_COLORS.md` §6.

---

## 8. Spacing of Type

- Line-height guidance lives with the spacing scale (`04_SPACING.md` §3) — display/heading use generous leading to keep the glass panels airy.
- Small-caps labels are tracked +0.12em and are the quietest element on any panel — they never shout.
- Text on Glass panels: ensure the panel's transparency (70–85%) never drops legibility below the §6.1 contrast floor when composited over `--void-black`.

---

## 9. Absolute Prohibitions

- No monospace for conversational or marketing text.
- No display face for body text or labels.
- No new type sizes outside the 4px scale.
- No panel with more than the three-tier hierarchy.
- No colored text outside the `01_COLORS.md` palette.
- No syntax-highlighting rainbow anywhere, including code views.

---

## 10. Token Reference

Type tokens (`--font-display-xl` … `--font-data-mono`) are defined in `20_DESIGN_TOKENS.md`. This document is the semantic contract: which voice, which job, which tier, which color.
