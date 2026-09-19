# 01 — Colors

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §5 (Color System), §9 (Reactor States), §15 (Emotional Intelligence), §18 (Luxury Rules)
**Status:** Permanent — frozen by the Visual Bible. No hue outside this document may ever enter the system.

---

## 1. Governing Principle

**Color in JARVIS is a state language, not a decorative palette.** Each color maps to exactly one meaning system-wide. A color must never be reused for an unrelated meaning in any future screen. Every state, mood, and emergency signal already has a defined color or a defined way of deriving one — no new hues are ever invented (§5, §18).

The one underlying rule that governs everything below: **saturation and glow are earned by state and hierarchy, never applied uniformly.** A screen where every element glows equally has failed this system.

---

## 2. The Complete Palette

### 2.1 Core Palette (structural, always present)

| Token | Hex | Role | Ownership |
|---|---|---|---|
| `--void-black` | `#04060B` | All primary backgrounds | Layer 0 — Void |
| `--deep-space` | `#0A0E1A` | Panel/card fills | Layer 2 — Structure |
| `--ignition-white` | `#F4FBFF` | Core ignition point ONLY | Layer 4 — Core |
| `--jarvis-blue` | `#2E9BFF` | Idle state; primary brand accent | Layer 3/4 |
| `--steel` | `#8A94A6` | Metal chassis, base plates, terminal base text | Layer 2 |
| `--gunmetal` | `#1B2130` | Metal shadow/undertone | Layer 2 |

### 2.2 State Palette (exact mapping to the reactor states)

| Token | Hex | Meaning |
|---|---|---|
| `--state-idle` | `#2E9BFF` | System online, nominal |
| `--state-listening` | `#00D9C0` | Acquiring input |
| `--state-thinking` | `#F5A623` | Processing / analysis / research / planning / learning |
| `--state-building` | `#E8ECF2` | Producing output (near-white, deliberately least-saturated) |
| `--state-warning` | `#FF3B30` | Threat / attention required |
| `--state-critical` | `#FF1744` | Emergency, system must dominate the screen |
| `--state-sleep` | `#7C4DFF` | Deep rest, consciousness persisting |
| `--state-offline` | `#8A94A6` | Off / inert (Steel at ~15% brightness) |
| `--state-success` | `#34D399` | Confirmed task completion ONLY |

### 2.3 Semantic Groups (derived, not new hues)

State colors belong to **families** that share temperature and may transition into each other directly. Families are the only sanctioned basis for crossfade and extension — see §4 below and `18_ANIMATIONS.md` §3.

| Family | Members | Temperature | Direct-transition rule |
|---|---|---|---|
| **Blue / Nominal** | `idle` | Cool | Idle ↔ Sleep (cool) |
| **Teal / Input** | `listening` | Cool-neutral | Listening ↔ Idle |
| **Amber / Thinking** | `thinking`, `research`, `planning`, `learning` | Warm | Any amber member ↔ any other amber member directly |
| **Near-white / Building** | `building` | Neutral, minimum saturation | Building ↔ Thinking-family direct (facet flicker retained) |
| **Red / Alert** | `warning`, `critical` | Hot | Warning ↔ Critical (shared hot family) |
| **Violet / Sleep** | `sleep` | Cool, deepest | Sleep ↔ Idle |
| **Steel / Offline** | `offline` | Achromatic | Offline only from a neutral/Steel flash, never a direct blend from a saturated color |
| **Success** | `success` | Cool-green, reserved | Fires once as an event pulse, returns to state color |

**Rule:** thinking → building → thinking are direct. **Thinking → warning is never direct** — it must pass through a brief neutral/Steel flash, because a blended amber-red misreads as a new, undefined color (§12.3). Offline likewise enters only through neutral.

---

## 3. Color as State Language

### 3.1 Meaning is fixed and exclusive

- The reactor's color IS the system state. A consumer reading the current state color is reading the true state — color is never decorative chrome.
- No component may "borrow" a state color for a purely aesthetic purpose. An accent icon uses its subsystem color (§05_COMPONENTS.md §3.3), which is a structural assignment, not a mood.
- `success` (`#34D399`) is triggered **only** at confirmed task completion — a single bright clean event pulse, then return to state color. It is never an ambient "happy" idle behavior (§15.4).

### 3.2 Emotion is expressed as temperature, saturation, and tempo — never as new colors

All emotional nuance is a shift applied **on the current state color**, never a new hue (§15):

| Emotion | Color treatment | Motion treatment |
|---|---|---|
| Trust building | None — color never changes | Crossfade lengthens slightly; ambient particle motion becomes marginally more fluid |
| High confidence | None — reads as stillness | Breathing amplitude tightens (tighter ±% than baseline); cleaner facet pattern |
| Uncertainty | **Desaturate current state color by 10–15%** | Breathing amplitude widens/irregulars slightly |
| Positive outcome | Single `success` event pulse, then return | One clean event pulse |
| Curiosity | None | Elevated particle rate, occasionally longer exploratory orbits (still orbital) |
| Urgency | **None** — stays in current non-Warning color | Pulse tempo increases; always visually subordinate to true Warning/Critical |

### 3.3 Hard boundary (§15.7)

No emotional state may ever introduce:
- A color outside this palette;
- An asymmetric/organic shape deviation from the reactor's precise geometry;
- A bounce/elastic easing curve;
- Any anthropomorphic feature.

---

## 4. Color Transitions

Full specification lives in `08_MOTION.md` §5 and `18_ANIMATIONS.md` §3. Behavioral rules:

1. **Crossfade, never a hard cut** — state changes animate over 400–600ms (`--dur-state-change`).
2. **Crossfade through the shared family first** when one exists (§2.3).
3. **Disparate families crossfade through a neutral/Steel flash** (Thinking→Warning, any→Offline).
4. **No two visually-adjacent simultaneous state changes may animate at different speeds** — the interface must never feel like separately-coded widgets (§12.3).
5. **Glow color always matches the current state color exactly** — no glow is ever a generic "highlight white" (§4.1). The ignition point is the sole exception, and it is `--ignition-white`, not a glow.

---

## 5. Brightness, Saturation, and Glow Discipline

- **Glow intensity is tied to system state**, never to decoration or emphasis alone (§4.1).
- **Bloom** (soft light bleed beyond an edge) applies only to the brightest element on screen — the ignition point or one active accent icon. Bloom radius scales with the element's actual brightness value; it is never a flat full-scene filter (§4.2).
- **Every glow has a traceable energy source.** No ambient glow without a stated emitter. This is the enforcement mechanism behind "no decorative glow" (§18).
- The **building** color (`#E8ECF2`) is deliberately near-white and minimum-saturation — output is bright but emotionally quiet. Do not increase its saturation.

---

## 6. Accessibility

### 6.1 Contrast ratios

All text-bearing color combinations must meet these minimums, computed against the dark base surfaces:

| Use | Background | Foreground | Minimum contrast |
|---|---|---|---|
| `body` / `heading` | `--deep-space` | `--ignition-white`-family text (`#F4FBFF`) | 12:1 |
| `body` / `heading` | `--deep-space` | State colors when used as accent text | 4.5:1 |
| `label` (small-caps) | `--deep-space` | `--steel` (`#8A94A6`) | 4.5:1 |
| `caption` | `--deep-space` | `--steel` | 4.5:1 |
| `data-mono` | near-black panel | `--steel` base / state accent | 4.5:1 |
| Any text | Glass panel over `--void-black` | state or steel | 4.5:1 (verify at 70–85% transparency, worst-case backing) |

### 6.2 Color is never the sole channel

- Every state color that alerts is co-occurring with an explicit on-screen text description (§9.10). The color/motion alerts; the text states the specific information.
- Terminal output uses at most two colors: base `--steel` text and **one** state-color accent for flagged lines (§7.4). No rainbow.
- State changes are always also communicated by shape/motion (rings, particles, pulses), so a color-vision-impaired user is never locked out of meaning.

### 6.3 Derived alpha values (tokenized in `20_DESIGN_TOKENS.md`)

| Token | Value | Use |
|---|---|---|
| `--opacity-bloom` | 0.05 | Bloom/haze layer |
| `--opacity-hologram` | 0.15 | Hologram wireframes |
| `--opacity-glass` | 0.25 | Glass panels (i.e. 75% transparent) |
| `--opacity-crystal` | 0.50 | Crystal core shell |
| `--opacity-metal` | 1.0 | Metal (opaque, but always reflective) |
| `--accent-hairline` | 0.20 | State-color hairlines (borders, dividers at state color 20%) |

---

## 7. Absolute Prohibitions (§18)

- **No generic gradients.** Every color transition is a physically-motivated light falloff (§4.1) or a state crossfade — never a decorative linear/radial gradient.
- **No neon overload.** Saturation and glow are earned by state and hierarchy.
- **No color outside the defined palette** for any state, mood, or emergency signal. New meanings are assigned within the existing palette's temperature/saturation vocabulary.
- **No flat/filled colored buttons or solid opaque UI chrome** — glass + hairline state border instead.
- **No two simultaneous Layer-4 focal objects** (§08_MOTION / §05_COMPONENTS depth) on one screen.

---

## 8. Token Reference

Complete, machine-consumable token table in `20_DESIGN_TOKENS.md`. The color tokens named throughout this document (`--void-black`, `--state-*`, `--ignition-white`, `--steel`, `--gunmetal`) are the canonical references; this document is the meaning contract for those tokens.
