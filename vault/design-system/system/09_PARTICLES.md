# 09 — Particles

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §3.2 (Particle Systems), §2.7 (gravitational-lens field), §9 (state-specific density), §5.3/§15 (emotional modulation)
**Status:** Permanent — frozen by the Visual Bible.

---

## 1. Governing Principle

Particles are **energy**, not confetti. They are emitted by the core along field lines, curve per the gravitational-lens rule, scale with system activity, and always carry the current state color. They exist to make the reactor's field visible — never as decorative ambiance.

Three laws:
1. **Particles always curve** — emitted from the core outward along field lines, bending as they pass near the core. Never scatter randomly, never travel in straight lines.
2. **Density is a symptom of activity** — more activity, more particles; less activity, fewer. Idle has almost none; thinking/research has the most.
3. **Every particle is state-colored** — `Energy` material, current state color, no exception.

---

## 2. Emission Model (§3.2)

| Property | Spec |
|---|---|
| Emitter | The reactor core (ignition point / emission field register, §2.1) |
| Trajectory | Outward along field lines, curved by the gravitational lens (§2.7) |
| Path geometry | Elliptical/circular segments around the core — orbital, never linear (§3.1) |
| Lifecycle | Fade in over first ~15% of life; fade out over last ~25%; **never pop in/out instantly** |

### Lifecycle curve

```
opacity
  1 ┤                          ┌───┐
    │                      ┌───┘   └───┐
    │                  ┌───┘           └───
  0 ┤──────────────────┘                  └────
    └────────────┬──────────────┬──────────────
               15%           75%          100%   life
```

---

## 3. Density by State

| State | Particle density |
|---|---|
| Idle | Minimal-to-none |
| Listening | Sparse, drifting outward |
| Thinking / Analysis | Elevated; pull inward |
| Research | Denser than base Thinking (active external search) |
| Coding | Thinking density; optionally one ring carries a `data-mono` character-stream |
| Building / Executing | Thinking speed, particles move **outward** (output produced) |
| Learning | Spiral inward, visibly absorbed into the crystal core — a slow, deliberate arc (~2s per particle) |
| Planning | Thinking density |
| Warning / Critical | Do not add decorative density — the alert reads through color and pulse, not more particles |
| Sleeping | None |
| Offline | None |

---

## 4. State Direction Model

Particle direction encodes meaning system-wide:

| Direction | Meaning |
|---|---|
| **Inward** (toward core) | Consuming input — thinking, analyzing, learning |
| **Outward** (from core) | Producing output — building, executing, speaking |
| **Drift outward, sparse** | Acquiring input — listening |
| **Spiral inward, absorbed** | Learning — deliberate absorption into the crystal |

This is the visual grammar of "input in / output out" and must never be scrambled.

---

## 5. Emotional Modulation (§15)

Particle motion is one of the two sanctioned channels (with tempo) for subtle emotional nuance — **always on the current state color, never new hues or geometry**:

| Emotion | Particle treatment |
|---|---|
| Trust building | Marginally more fluid ambient motion (relaxing) |
| Curiosity | Elevated rate; occasional longer, more exploratory orbital paths before returning — still fully orbital, never erratic |
| High confidence | Steadier, cleaner pattern (reads as stillness) |
| Uncertainty | Slightly wider/irregular motion, matching the widened breathing amplitude |

---

## 6. Rendering Contract

| Property | Spec |
|---|---|
| Material | Energy (§02 §2.4) — pure light, no solid geometry |
| Color | Current state color, exact (§01). Never "highlight white" |
| Size | Point-scale; size varies only with distance/perceived depth, never as decoration |
| Opacity | Governed by the lifecycle curve above; peak ≤ 0.9 |
| Blend | Additive/light-based — particles emit light, they do not sit on top as painted dots |
| Bloom | Never per-particle bloom; only the brightest single element blooms (§4.2) |

---

## 7. Performance

- **Particle count is the first ambient simplification** under constraint (motion hierarchy §12.4 → §08_MOTION §7): particles pause/reduce before the reactor's state animation degrades.
- Density caps scale per device context (§13.1): watch = none, phone/tablet/desktop = increasing but always bounded; under a frame-budget miss, reduce count before reducing fidelity of remaining particles.

---

## 8. Absolute Prohibitions

- No random scatter — particles curve along field lines, always.
- No particles in Idle beyond the minimal field.
- No particle colors outside the state palette.
- No pop-in/pop-out — lifecycle fades are mandatory.
- No decorative particle storms in Warning/Critical.
- No particles where a real data readout belongs (no fake telemetry — §18).

---

## 9. Token Reference

Particle timing (lifecycle fade percentages) is behavioral; density budgets are per-device. All color/duration tokens referenced here are declared in `20_DESIGN_TOKENS.md`; full motion integration in `08_MOTION.md` / `18_ANIMATIONS.md`.
