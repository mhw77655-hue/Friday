# 08 — Motion

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §3 (Motion), §12 (Animation Timing System), §15 (Emotional Intelligence)
**Status:** Permanent — frozen by the Visual Bible.

---

## 1. Governing Principle

**All ambient motion in the JARVIS system is orbital.** Rings, particles, and data elements move along elliptical or circular paths around a gravitational center — never linear, never bouncing. This is the single most important motion rule in the entire system: it is what makes JARVIS feel like physics rather than animation (§3.1).

Two sub-laws govern everything:
1. **Every animation must map to a specific cause** defined in §3, §9, or §15 of the Visual Bible. No animation exists purely because motion is "engaging" (§18).
2. **Confidence is communicated through precision and stillness, not motion volume.** JARVIS does not perform enthusiasm (§1.2).

---

## 2. The Breathing Pulse (§3.3)

The reactor's resting animation — a slow, continuous breathing pulse:

| Property | Spec |
|---|---|
| Brightness oscillation | ±8–12% around baseline |
| Cycle length | 3.2–4.0s (`--dur-breathing-idle` = 3600ms nominal) |
| Easing | Sine (ease-in-out) — **never linear** |
| Continuity | **Never stops** — continues underneath whatever faster pulse a state adds |

**Sleeping breathing:** 6–8s cycle (`--dur-breathing-sleep` = 7000ms nominal), slowest possible (§9.12).

---

## 3. Pulse Timing (§3.4)

Two pulse tempos exist system-wide and **must never be mixed arbitrarily**:

| Pulse | Trigger | Spec |
|---|---|---|
| **Ambient pulse** (breathing) | Always | Always slow (3–4s) |
| **Event pulse** | Discrete events — message received, task complete, warning triggered | Single sharp spike: 150–300ms attack (`--dur-event-pulse-attack` = 150ms), 400–600ms decay (`--dur-event-pulse-decay` = 500ms) |

**Rule:** event pulses **layer on top of, never replace**, the ambient breathing.

**Warning pulse tempo:** sharp 1Hz (§9.10). **Critical pulse tempo:** sharp 2Hz (§9.11). Both use the sharp ease-out `--ease-warning-pulse`.

---

## 4. Duration System (§12.1)

| Motion class | Duration | Token |
|---|---|---|
| Micro (button press, icon state) | 100–150ms | `--dur-micro` (120ms) |
| UI transition (panel open/close) | 250–350ms | `--dur-transition` (300ms) |
| State change (reactor color/state swap) | 400–600ms — crossfade, never hard cut | `--dur-state-change` (500ms) |
| Ambient breathing cycle (Idle) | 3200–4000ms | `--dur-breathing-idle` (3600ms) |
| Deep sleep breathing cycle | 6000–8000ms | `--dur-breathing-sleep` (7000ms) |
| Event pulse | 150ms attack / 400–600ms decay | `--dur-event-pulse-attack` / `--dur-event-pulse-decay` |
| Startup / Shutdown | 1500–3000ms | `--dur-startup-shutdown` (2200ms) |

---

## 5. Easing (§12.2)

| Curve | Applies to | Token |
|---|---|---|
| Sinusoidal in-out | Orbital motion — rings, particles | `--ease-orbital` `cubic-bezier(0.45, 0, 0.55, 1)` |
| Ease-out | UI panel entry | `--ease-panel-in` `cubic-bezier(0.16, 1, 0.3, 1)` |
| Ease-in | UI panel exit | `--ease-panel-out` `cubic-bezier(0.7, 0, 0.84, 0)` |
| Sharp ease-out | Warning/Critical pulses | `--ease-warning-pulse` `cubic-bezier(0.9, 0, 1, 1)` |

**Absolute prohibition:** **no bounce/elastic easing anywhere** — it contradicts the field-physics model (§12.2). Warning/Critical use sharp ease-out **only** (no ease-in): urgency reads through attack speed, not a smoothed approach.

---

## 6. Transition Rules (§12.3)

1. **State-to-state color transitions crossfade through their shared family first** if one exists. (See family table, `01_COLORS.md` §2.3.)
2. **Disparate families pass through a brief neutral/Steel flash** (e.g., Thinking → Warning; any → Offline) rather than blending directly — a blended amber-red misreads as a new, undefined color.
3. **No two simultaneous state changes may animate at different speeds if they are visually adjacent** — this is what prevents the interface from feeling like separately-coded, disconnected widgets.

---

## 7. Motion Hierarchy (§12.4)

When multiple things animate at once, full-quality motion goes in this priority order; lower tiers simplify or pause under performance constraints:

1. **The reactor / current Layer 4 focal object** — always full fidelity.
2. **Anything directly reporting the cause of the current state** (e.g., the specific warning source).
3. **Ambient/background particles and secondary panels** — first to simplify or pause.

---

## 8. State-Specific Motion (summary)

Full per-state specs in `18_ANIMATIONS.md`. Motion changes per state are strictly: ring speed, particle direction/density, facet flicker, pulse tempo, and breathing amplitude. (Table in `07_REACTOR.md` §6.)

---

## 9. Emotional Motion (§15)

All emotional nuance is expressed as **temperature, saturation, and tempo shifts on the current state** — never new colors, characters, or expressions (§15). Motion vocabulary:

| Emotion | Motion treatment |
|---|---|
| Trust building | Longer crossfades; marginally more fluid ambient particles |
| High confidence | Tighter breathing ±% range; cleaner, less flickering facet pattern — **confidence reads as stillness** |
| Uncertainty | Wider/irregular breathing amplitude; state color desaturated 10–15% |
| Curiosity | Elevated particle rate; occasional longer exploratory orbits (still orbital) |
| Urgency | Increased pulse tempo in current (non-Warning) color — subordinate to true Warning/Critical |

---

## 10. Startup / Shutdown Sequences (§3.9, §11.4–11.5)

- **Startup:** ignition point brightens from zero to baseline; rings spin up; light and audio wake-swell land **frame-accurate** (§11.4 → `11_SOUND.md`).
- **Shutdown:** rings stop first, then core brightness fades last — the light is the final thing to go, like an ignition point extinguishing rather than a screen turning off. Ends on a single dim ember-point held indefinitely (the Offline visual, §9.13), **never full black**. Audio descends to silence in the same 2–3s window (§11.5).

---

## 11. Performance

- Under constraint, simplify in §7 priority order (motion hierarchy), never the reactor first.
- Background particles and secondary panels pause before any state animation degrades.
- Breathing and event pulses are the only required-always motions; everything else is eligible for tier-based simplification.

---

## 12. Absolute Prohibitions

- No linear ambient motion; no bounce/elastic anywhere.
- No animation without a specified cause (§18).
- No two visually-adjacent simultaneous transitions at different speeds.
- No mixing the two pulse tempos arbitrarily.
- No hard-cut state changes — always crossfade (or crossfade-through-neutral).

---

## 13. Token Reference

Duration + easing tokens declared in `20_DESIGN_TOKENS.md`. Full sequence specs and per-state animation tables in `18_ANIMATIONS.md`; particle rules in `09_PARTICLES.md`; light behavior in `10_LIGHTING.md`.
