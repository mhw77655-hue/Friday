# 20 — Design Tokens

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §16 (Design Tokens), plus the token sets established by `01_COLORS`–`19_ICONS`
**Status:** Permanent. This is the machine-consumable token registry of the entire system. Every value below is the canonical reference — no subsystem may redefine or drift from these.

---

## 1. Token Registry

### 1.1 Color — Core

| Token | Value | Use |
|---|---|---|
| `--void-black` | `#04060B` | All primary backgrounds |
| `--deep-space` | `#0A0E1A` | Panel/card fills |
| `--ignition-white` | `#F4FBFF` | Reactor ignition point ONLY |
| `--jarvis-blue` | `#2E9BFF` | Idle state, primary brand accent |
| `--steel` | `#8A94A6` | Metal chassis, base plates, terminal base text |
| `--gunmetal` | `#1B2130` | Metal shadow/undertone |

### 1.2 Color — State

| Token | Value | Meaning |
|---|---|---|
| `--state-idle` | `#2E9BFF` | System online, nominal |
| `--state-listening` | `#00D9C0` | Acquiring input |
| `--state-thinking` | `#F5A623` | Processing |
| `--state-building` | `#E8ECF2` | Producing output |
| `--state-warning` | `#FF3B30` | Threat / attention |
| `--state-critical` | `#FF1744` | Emergency |
| `--state-sleep` | `#7C4DFF` | Deep rest |
| `--state-offline` | `#8A94A6` | Off / inert (Steel @ ~15% brightness) |
| `--state-success` | `#34D399` | Confirmed completion ONLY |

### 1.3 Color — Derived / Semantic

| Token | Value | Use |
|---|---|---|
| `--accent-hairline` | state color @ 0.20 opacity | Hairlines, borders, dividers at state color |
| `--text-primary` | `#ECEDF1` | Headline/tier-2 text — distinct near-white, NEVER `#F4FBFF` (§5.6) |
| `--text-secondary` | `#8A94A6` | Body/tier-3, captions, labels |
| `--terminal-text` | `#8A94A6` | Terminal base text |
| `--terminal-accent` | current state color | Terminal flagged lines (one accent max) |

---

### 1.4 Typography

| Token | Value | Use |
|---|---|---|
| `--font-display-xl` | 48px | Wordmark, splash |
| `--font-display` | 32px | Screen titles |
| `--font-heading` | 20px | Panel/section titles |
| `--font-label` | 12px | Small-caps labels — uppercase, +0.12em tracking |
| `--font-body` | 14px | Descriptions, primary reading |
| `--font-caption` | 11px | Secondary/supporting |
| `--font-data-mono` | 12px | Telemetry, HUD, terminal |

**Line-heights** (all multiples of 4px): display-xl 56px, display 40px, heading 28px, label 16px, body 20px, caption 16px, data-mono 16px.

---

### 1.5 Spacing

| Token | Value | Use |
|---|---|---|
| `--space-1` | 4px | Hairline-to-element gaps |
| `--space-2` | 8px | Tight inner gaps |
| `--space-3` | 12px | Element clusters inside cards |
| `--space-4` | 16px | Standard panel padding |
| `--space-6` | 24px | Primary panel padding, section gaps |
| `--space-8` | 32px | Panel gutters, screen margins |
| `--space-12` | 48px | Major section breaks |
| `--space-16` | 64px | Large-canvas outer margins |

**Hairline:** 1px stroke (the single non-4px value — a stroke, not a spacing unit).

---

### 1.6 Motion — Duration

| Token | Value | Use |
|---|---|---|
| `--dur-micro` | 120ms | Button press, icon state |
| `--dur-transition` | 300ms | Panel open/close |
| `--dur-state-change` | 500ms | Reactor state crossfade (400–600ms window) |
| `--dur-breathing-idle` | 3600ms | Idle breathing (3.2–4.0s window) |
| `--dur-breathing-sleep` | 7000ms | Sleep breathing (6–8s window) |
| `--dur-event-pulse-attack` | 150ms | Event pulse attack (150–300ms window) |
| `--dur-event-pulse-decay` | 500ms | Event pulse decay (400–600ms window) |
| `--dur-startup-shutdown` | 2200ms | Startup/shutdown (1.5–3.0s window) |

**Voice-response state change:** halved from `--dur-state-change` to ~200–300ms (§14.4).

### 1.7 Motion — Easing

| Token | Curve | Use |
|---|---|---|
| `--ease-orbital` | `cubic-bezier(0.45, 0, 0.55, 1)` | Orbital motion — rings, particles |
| `--ease-panel-in` | `cubic-bezier(0.16, 1, 0.3, 1)` | Panel entry |
| `--ease-panel-out` | `cubic-bezier(0.7, 0, 0.84, 0)` | Panel exit |
| `--ease-warning-pulse` | `cubic-bezier(0.9, 0, 1, 1)` | Warning/Critical pulses |

---

### 1.8 Depth

| Token | Value | Use |
|---|---|---|
| `--layer-0-void` | 0 | Background |
| `--layer-1-ambient` | 1 | Bloom/haze, particles |
| `--layer-2-structure` | 2 | Base plates, panel backgrounds |
| `--layer-3-content` | 3 | Text, icons, data, panel foregrounds |
| `--layer-4-core` | 4 | Reactor / single focal object (one per screen) |

---

### 1.9 Material Opacity

| Token | Value | Use |
|---|---|---|
| `--opacity-bloom` | 0.05 | Ambient bloom/haze |
| `--opacity-hologram` | 0.15 | Hologram wireframes (base) |
| `--opacity-glass` | 0.25 | Glass panels (75% transparent) |
| `--opacity-crystal` | 0.50 | Crystal core shell |
| `--opacity-metal` | 1.0 | Metal (opaque, always reflective) |

**Derived:** Glass self-reflection along lower edge: 0.04–0.08. Hologram wireframe transparency: 0.80–0.90. Bloom/haze: ~0.95 transparency.

---

### 1.10 Sound

| Token | Value | Use |
|---|---|---|
| `--audio-hum-db` | −36dB | Baseline ambient reactor hum |
| `--audio-ui-headroom` | 6dB | Max UI sound above hum |
| `--audio-startup-ms` | 1500–2500ms | Wake swell (frame-accurate with ignition) |
| `--audio-shutdown-ms` | 2000–3000ms | Shutdown descent (frame-accurate with ember fade) |

---

## 2. Canonical Numbering

Tokens reference each other across the system docs by these names. A subsystem consuming the Design System consumes the tokens named here and the behavioral contracts in their source docs (`01_COLORS`…`19_ICONS`).

## 3. Update Policy

This registry may only be changed by amending the source of truth (`JARVIS_VISUAL_BIBLE.md` §16 and the frozen rules it establishes). No new token is invented for a one-off screen; new needs are met by composing existing tokens. Drift from these values is a spec violation, not a style choice.

## 4. Token Coverage Map

| Token group | Source docs |
|---|---|
| Colors | `01_COLORS.md` |
| Materials / opacity | `02_MATERIALS.md` |
| Typography | `03_TYPOGRAPHY.md` |
| Spacing | `04_SPACING.md` |
| Components / depth | `05_COMPONENTS.md` |
| HUD | `06_HUD.md` |
| Reactor | `07_REACTOR.md` |
| Motion / durations / easing | `08_MOTION.md`, `18_ANIMATIONS.md` |
| Particles | `09_PARTICLES.md` |
| Lighting | `10_LIGHTING.md` |
| Sound | `11_SOUND.md` |
| Voice visualizer | `12_VOICE_VISUALIZER.md` |
| Device contexts | `13_PHONE_UI`–`17_AR_UI` |
| Icons | `19_ICONS.md` |
