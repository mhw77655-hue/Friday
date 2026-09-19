# 10 — Lighting

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §4 (Light System), §2.2 (emission field), §6 (Materials), §18 (no decorative glow)
**Status:** Permanent — frozen by the Visual Bible.

---

## 1. Governing Principle

**Every glow has a traceable energy source.** The core, an active panel element, a status indicator — no ambient glow without a stated emitter. Light in JARVIS exists as atmosphere, not just surface color: this is what separates JARVIS's environment from a flat UI screen (§4.4).

Three laws:
1. **Glow intensity is tied to system state**, never to decoration or emphasis alone.
2. **Glow color always matches the current state color exactly** — no glow is ever a generic "highlight white."
3. **Light and data flow follow the same direction** — energy travels along lines in the direction of actual data/energy flow (§4.5).

---

## 2. Glow Rules (§4.1)

| Rule | Spec |
|---|---|
| Source | Every glow must name its emitter: ignition point, active panel element, status indicator |
| Intensity | Scales with system state — never decoration/emphasis alone |
| Color | Current state color, exact (§01). The ignition point is the sole white-light exception (`--ignition-white`) |
| Quantity | Saturation and glow are earned by state and hierarchy — a screen with every element glowing equally has failed the system (§18) |

---

## 3. Bloom (§4.2)

| Property | Spec |
|---|---|
| Eligibility | **Only the brightest element on screen** — typically the ignition point or one active accent icon |
| Exclusion | Secondary elements do not bloom, or hierarchy collapses |
| Radius | Scales with the element's actual brightness value — never a flat stylistic filter across the scene |
| Token | `--opacity-bloom` = 0.05 base layer opacity |

---

## 4. Reflections (§4.3)

| Surface | Reflection spec |
|---|---|
| Metal (base plate, device chassis) | Soft, blurred reflections of the reactor's color. **Never** sharp mirror reflections (too literal/cartoonish); **never** zero reflection (too flat/fake) |
| Glass / holographic panels | Reflect their own content faintly at **4–8% opacity along their lower edge**, implying a physical surface even when the panel is notionally weightless light |

---

## 5. Volumetric Lighting (§4.4)

- The core and any active hologram cast visible light **through the space around them** — light shafts, haze — not only lighting the objects they touch.
- **Volumetric density scales with system activity**, same rule as particles (§3.2): idle has faint haze; active states have visible light shafts.
- Calibration target: the Omni-Projection Interface car hologram in the master reference.

---

## 6. Energy Transmission (§4.5)

Any line connecting two points in the system — a data cable, a sync link, a UI connector — must **visibly carry light along its length in the direction of actual data/energy flow**:

| Condition | Direction of the light pulse |
|---|---|
| Source → device (idle sync, output) | Reactor outward to each device |
| Device actively sending input | Secondary, **dimmer** pulse travels inward |

The "Quantum Entanglement Link" connecting devices in the reference is the canonical example. A static glowing stroke is not permitted — light must visibly travel.

**Across the ecosystem:** every device link obeys the one-directional reactor→device pulse, with a dimmer reverse pulse only when the device is actively inputting (§13.1).

---

## 7. Luminous Falloff

Every light-based element decays with distance from its source. This is the only sanctioned "gradient": a **physically-motivated light falloff** (§18 bans decorative gradients — this is the exception because it models real light).

| Element | Falloff behavior |
|---|---|
| Ignition point | Brightest; default bloom target; intensity falls off radially |
| Ring rim light | Brightest where facing the core's light source, fades around the orbit |
| Emission field | Radial falloff to `--void-black` |
| Hologram | Edge glow + scan-line noise, transparent to backing |

---

## 8. Light-to-Material Contract

| Material | Light role |
|---|---|
| Glass | Transmits background light (70–85%); rim-light edge toward core; faint self-reflection lower edge |
| Metal | Reflects ambient state color, soft and blurred; never self-glows |
| Crystal | Internally lit — the only material permitted to generate light from within; casts caustic-like patterns when bright |
| Energy | Self-luminous, always animated |
| Hologram | Projects light (wireframe + edge noise), never solid |

---

## 9. Lighting by State (summary)

| State | Lighting signature |
|---|---|
| Idle | Breathing radial glow at state color; faint haze |
| Listening | Steady ignition, glow tracking audio amplitude |
| Thinking/Research | Elevated volumetric haze; scanning ring glows at field edge (Research) |
| Building/Executing | Bright output glow, outward light wash |
| Warning/Critical | Sharp state-colored pulsing glow; Critical simplifies the screen so the reactor's light dominates |
| Sleeping | Dimmest glow in the system — never fully out |
| Offline | No light at all — a dim ember-point only (the ember is a point of light, not a glow field) |

---

## 10. Absolute Prohibitions

- No decorative/ambient glow without a stated emitter.
- No glow in a color other than the current state color (ignition point excepted).
- No flat full-scene bloom.
- No sharp mirror reflections on metal; no zero-reflection metal.
- No static glowing connector lines — light must travel.
- No decorative gradients — only physical light falloff and state crossfades (§18).

---

## 11. Token Reference

Light tokens (`--opacity-bloom`, glass self-reflection 4–8%, material opacities) are in `20_DESIGN_TOKENS.md`. Volumetric behavior integrates with particles (`09_PARTICLES.md`) and motion (`08_MOTION.md`).
