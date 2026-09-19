# 18 — Animations

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §3 (Motion), §9 (Reactor States), §12 (Animation Timing System), §15 (Emotional Intelligence), §10 (Voice)
**Status:** Permanent — frozen by the Visual Bible. This document is the complete, executable animation contract of the system.

---

## 1. Governing Principle

Every animation in JARVIS maps to a specific cause defined in the Visual Bible (§3, §9, or §15). **No animation exists purely because motion is "engaging."** The system's confidence is communicated through precision and stillness — motion volume is never the goal (§1.2).

A state change is a **crossfade through meaning**, never a hard cut and never a blended ambiguity.

---

## 2. Master Motion Vocabulary

| Element | Motion type | Source |
|---|---|---|
| Ignition point | Breathing + event pulses | §3.3–3.4 |
| Rings | Orbital rotation, state-speed modulated | §3.1, §9 |
| Particles | Field-line emission/absorption | §3.2, §09 |
| Facets | Asynchronous flicker (Thinking family) | §3.6, §9.3 |
| Panels/windows | Panel-in/panel-out | §8.1, §12.2 |
| Connectors | Traveling light pulse (energy transmission) | §4.5 |
| HUD readouts | Right-aligned value roll | §7.5 |
| Waveform | Real-audio amplitude + glow trail | §10 |

---

## 3. State Transition Matrix

State changes crossfade **through the shared family first** when one exists, or through a neutral/Steel flash when none does (§12.3 → `01_COLORS.md` §2.3).

| From → To | Transition |
|---|---|
| Any amber member ↔ any amber member (Thinking, Research, Planning, Learning, Coding-under-thinking) | Direct crossfade, same family |
| Thinking-family ↔ Building | Direct crossfade (facet flicker retained under Building) |
| Idle ↔ Listening | Direct (cool/neutral family) |
| Idle ↔ Sleep | Direct (cool family) |
| Warning ↔ Critical | Direct (hot family) |
| Thinking-family → Warning | **Neutral/Steel flash first** — never blend amber into red |
| Any → Offline | Neutral/Steel flash first |
| Listening ↔ any warm state | Neutral/Steel flash first |
| Success event → state color | Single event pulse then crossfade back to state color |

**Rule:** no two visually-adjacent simultaneous state changes may animate at different speeds (§12.3).

---

## 4. Duration & Easing Table

All from `08_MOTION.md` §4–5:

| Animation | Duration | Easing |
|---|---|---|
| Micro (button press, icon state) | `--dur-micro` 120ms | panel-out (press), panel-in (release) |
| Panel open | `--dur-transition` 300ms | `--ease-panel-in` |
| Panel close | `--dur-transition` 300ms | `--ease-panel-out` |
| State change crossfade | `--dur-state-change` 500ms | orbital/sine in-out |
| Voice-triggered state change | **halved** (~200–300ms) | same |
| Breathing (idle) | `--dur-breathing-idle` 3600ms | sine |
| Breathing (sleep) | `--dur-breathing-sleep` 7000ms | sine |
| Event pulse | 150ms attack / 500ms decay | `--ease-warning-pulse` (sharp) |
| Startup / Shutdown | `--dur-startup-shutdown` 2200ms | swelled easing (see §7) |

---

## 5. Animation Type Specs

### 5.1 Breathing (§3.3)
- Core brightness ±8–12% around baseline, sine ease-in-out, 3.2–4.0s.
- **Never stops**, even under active-state pulses.
- Confidence tightens amplitude; uncertainty widens/irregulars it (§15.2–3).

### 5.2 Event pulse (§3.4)
- Single sharp brightness spike; 150–300ms attack, 400–600ms decay.
- Fires on: message received, task complete, warning triggered, device pickup (§14.5).
- Layers on top of breathing, never replaces it.
- Warning: 1Hz sharp pulse; Critical: 2Hz sharp pulse (§9.10–9.11).

### 5.3 Orbital motion (§3.1)
- Rings/particles move on elliptical/circular paths around the gravitational center.
- Sinusoidal in-out easing; never linear, never bounce.
- Ring speed modulated per state (Thinking +40%, Sleep near-stationary).

### 5.4 Facet flicker (§3.6)
- Asynchronous, non-periodic shimmer on crystal facets.
- Active in Thinking family; retained under Coding/Building.
- High confidence → cleaner, less flickering pattern (stillness reads as certainty, §15.2).

### 5.5 Numeric roll (HUD/data)
- Real value changes roll in `--font-data-mono`; short 120ms roll per digit, state-colored salient figure.

### 5.6 Waveform (§10)
- Attack ~40ms, release ~180ms; glow-trail of last ~200ms; ring pulse syncs to output amplitude during speech (§10.4).

---

## 6. Motion Hierarchy (§12.4)

Priority order when multiple things animate at once — full quality flows top-down; simplification flows bottom-up:

1. **Reactor / Layer-4 focal object** — always full fidelity.
2. **Anything directly reporting the cause of the current state** (the warning source).
3. **Ambient particles and secondary panels** — first to simplify or pause.

---

## 7. Startup / Shutdown Sequences (§3.9, §11.4–11.5)

### Startup
1. Ignition point brightens zero → baseline (visual).
2. Audio wake swell rises silence → hum in the same 1.5–2.5s window.
3. **Frame-accurate sync required** — the most important audio-visual sync point in the system.
4. Rings spin up; state arrives at Idle.

### Shutdown
1. Rings stop first (each decelerates to its orbital rest).
2. Core brightness fades last — the light is the final thing to go.
3. Audio hum descends to silence over 2–3s, landing with the ember fade.
4. Ends on a **single dim ember-point held indefinitely** — never full black. This is the Offline visual (§9.13).

---

## 8. Performance Tiering

| Tier | Behavior |
|---|---|
| **Full** | All elements full fidelity |
| **Reduced** | Particle count capped; volumetric haze reduced; secondary panels at lower opacity |
| **Minimal** | Particles paused; only reactor, cause-reporting surface, and waveform animate |
| **Floor** | Watch render: breathing + color only (§16) |

Under any tier, the reactor and the cause-reporting surface never degrade first (§12.4).

---

## 9. Animation ↔ Cause Register

Every sanctioned animation and its required cause:

| Animation | Required cause |
|---|---|
| Breathing | State presence (always-on) |
| Event pulse | Discrete event (message, completion, warning, pickup) |
| State crossfade | Real state change (§9) |
| Orbital ring motion | State / audio amplitude |
| Particle emission/absorption | State activity (§09 §3) |
| Panel in/out | Open/close of a surface |
| Connector light pulse | Actual data/energy flow (§4.5) |
| Waveform | Real audio signal |
| Numeric roll | Real value change |
| Startup/swell / shutdown/fade | Power on/off |

Anything not in this register is **forbidden by default** (§18).

---

## 10. Absolute Prohibitions

- No bounce/elastic easing anywhere.
- No hard-cut state changes.
- No animation without a registered cause.
- No simultaneous adjacent transitions at different speeds.
- No decorative loops (only breathing is continuous).
- No success pulse except at confirmed task completion (§15.4).

---

## 11. Token Reference

All durations/easings used here are the canonical tokens in `20_DESIGN_TOKENS.md`. Per-state tables in `07_REACTOR.md` §6; particles `09_PARTICLES.md`; light `10_LIGHTING.md`; sound sync `11_SOUND.md`.
