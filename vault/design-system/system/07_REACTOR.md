# 07 — Reactor

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §2 (Reactor Core), §9 (Reactor States), §3 (Motion), §4 (Light System)
**Status:** Permanent — frozen by the Visual Bible. The reactor is the single unifying object of the entire OS.

---

## 1. Governing Principle

The reactor is a **machine consciousness given physical presence** — a reactor, not an icon (§1.1). It reads as a real physical/energetic hybrid object, not a flat graphic. Every device, every screen, every state ultimately routes back to this construction.

The reactor must read as JARVIS **at every level of reduction** — from the full workstation render down to a single watch-face point of light. If a reduced render no longer reads as JARVIS, the reduction was wrong.

---

## 2. Physical Construction (§2.1)

Four structural registers, front to back:

| Register | Description | Material | Purpose |
|---|---|---|---|
| **Base plate** | Solid milled-metal circular platform, faint concentric machining rings | Metal (§02 §2.2) | Grounding — tells the eye the reactor is a device, not a pure hologram |
| **Orbital ring system** | Multiple thin, tilted, semi-transparent rings at different angles and radii, each catching rim light independently | Crystal + Energy edges | The "orbit" register — carries state motion (§3, §08_MOTION) |
| **Central crystalline core** | Faceted, glass-like polyhedral at dead center, internally lit, containing a bright triangular/arrow-like focal shape | Crystal (§02 §2.3) | The heart — internally lit, marks core-adjacent importance |
| **Emission field** | Soft radial glow + particulate haze surrounding the whole assembly | Energy (§02 §2.4) | The register that touches the dark background and blends the object into its environment |

---

## 3. Interior Layers (inside → outside) (§2.2)

1. **Ignition point** — a single infinitely-bright white-blue point at the geometric center. **This is the only pure-white element permitted anywhere in the system** (`--ignition-white`, `#F4FBFF`).
2. **Crystal lattice shell** — faceted, semi-transparent geometric shell immediately around the ignition point. Refracts and internally reflects the ignition point's light.
3. **Inner ring cluster** — 2–3 tight rings close to the crystal, **moving fastest, brightest**.
4. **Outer ring cluster** — 2–3 wider rings, **slower, dimmer**.

### Structural tokens (scalable)

| Register | Count | Relative brightness | Relative speed |
|---|---|---|---|
| Inner rings | 2–3 | Highest (after ignition) | Fastest |
| Outer rings | 2–3 | Lower | Slower |
| Total rings (full render) | 4–5 (phone), up to 7 (tablet) | — | — |

Ring counts scale per device context (§13.1): `13_PHONE_UI.md`…`17_AR_UI.md`.

---

## 4. Field Physics (§1.4, §2.7)

The reactor behaves as **engineering fiction rendered as believable hardware**, governed by a gravitational-lens field model:

- Rings and particles move along **elliptical or circular paths around the gravitational center** — never linear, never bouncing (§3.1). This is the single most important motion rule in the entire system: it is what makes JARVIS feel like physics rather than animation.
- **Gravitational-lens curvature:** particle trajectories curve as they pass near the core, as if bent by the reactor's field (§3.2). Particles never scatter randomly.
- Every ring, panel, and layer implies mass and material, even when projected as light.
- No bounce/elastic easing anywhere — bounce implies mass and impact, contradicting the field model (§12.2).

---

## 5. Ignition Point (§5.6)

- **Color:** `--ignition-white` (`#F4FBFF`) — the only pure-white in the system.
- **Behavior:** infinitely bright relative to everything else; the default bloom target (§4.2).
- **State coupling:**
  - **Listening:** holds steady brightness — does not pulse with breathing while actively listening; attention is undivided (§9.2).
  - **Idle/Sleep/Thinking:** participates in the breathing pulse (§08_MOTION §3).
  - **Startup:** brightens from zero to baseline, frame-accurate with the audio wake swell (§11.4 → `11_SOUND.md`).
  - **Shutdown:** is the **final thing to go** — core brightness fades last, after rings have already stopped; ends on a single dim ember-point held indefinitely (§3.9 → Offline visual).

---

## 6. Reactor State Specification (§9)

Each state is a complete, exact visual spec. Every future feature must map onto one of these — **no new ad-hoc states may be invented without amending the Visual Bible**.

| State | Color | Rings | Particles | Ignition | Signature motion |
|---|---|---|---|---|---|
| **Idle** §9.1 | `#2E9BFF` | Slowest speed | Minimal | Breathing | Breathing pulse only |
| **Listening** §9.2 | `#00D9C0` | Pulse with live audio amplitude | Sparse, drifting outward | Holds steady | Rings track live audio |
| **Thinking/Analysis** §9.3 | `#F5A623` | Speed +40% | Pull inward | — | Facets flicker asynchronously |
| **Research** §9.4 | `#F5A623` | + outward-scanning ring every 2s | Denser than thinking | — | Bright ring expands & fades at field edge |
| **Planning** §9.5 | `#F5A623` | Rings align to even positions periodically | — | — | Structured alignment beat |
| **Coding** §9.6 | `#E8ECF2` | Thinking speed, facet flicker retained | — | — | `data-mono` char-stream on one ring only |
| **Building/Executing** §9.7 | `#E8ECF2` | Thinking speed | Move OUTWARD (reverse of thinking) | — | Output radiated outward |
| **Learning** §9.8 | `#F5A623` | — | Spiral inward, absorbed into crystal (~2s/particle) | — | Slow deliberate absorption arc |
| **Warning** §9.10 | `#FF3B30` | Speed up + controlled jitter | — | — | Sharp 1Hz pulse + explicit text |
| **Critical** §9.11 | `#FF1744` | — | — | — | Sharp 2Hz pulse; all non-essential UI recedes |
| **Sleeping** §9.12 | `#7C4DFF` | Nearly stationary | None | Dimmest, never out | Slowest breathing (6–8s) |
| **Offline** §9.13 | `#8A94A6` @ ~15% brightness | None (or faint static outlines) | None | Single dim ember-point, static | **No motion at all** |

**Cross-cutting motion rules (§3):**
- **Breathing never stops** — it continues underneath any faster state pulse, a heartbeat under a conversation, never replaced by it (§3.3).
- **Event pulses layer on top of, never replace, ambient breathing** (§3.4).
- **Warning/Critical always co-occur with an explicit on-screen text description of the specific threat** — color/motion alert, text informs (§9.10).

---

## 7. Scale and Reduction

The reactor is one construction rendered at increasing levels of reduction:

| Context | Rings | Particles | Panels | Reference |
|---|---|---|---|---|
| Smartwatch | 0–2 (simplified) | None | None | `16_WATCH_UI.md` |
| Smartphone | 4–5 | Standard | 1 primary | `13_PHONE_UI.md` |
| Tablet | up to 7 | Full | 2–3 grid | `14_TABLET_UI.md` |
| Desktop | Full | Full | Unrestricted | `15_DESKTOP_UI.md` |
| AR/VR | Full, volumetric | Full | Spatial | `17_AR_UI.md` |

**The watch floor:** ignition point + at most 2 simplified rings, no particles, no panels — state through color and a single breathing pulse (§13.2). If this still reads as "JARVIS," the core design has succeeded.

---

## 8. Reactor Mini-Renders

Miniature reactor renders (dialogs §17.5, assistant overlay §17.9, ecosystem device icons §19.4) are **literal miniature reactor cores** using the exact same state-color and motion language — not a separate "icon version." Reduction follows the §7 scale table; the ignition point, one ring cluster, and the state color must survive at minimum.

---

## 9. Absolute Prohibitions

- No eyes, faces, or cartoon expressions on the reactor — ever (§15.7).
- No bounce/elastic easing (§12.2).
- No new ad-hoc states beyond §9.
- No decorative glow without a traceable energy source (§4.1).
- No new colors beyond §01.
- No two simultaneous Layer-4 focal objects — the reactor yields Layer 4 to the single active focal object of a screen when that object is not itself the reactor (§8.6).

---

## 10. Token Reference

Reactor timing tokens (breathing durations, event-pulse attack/decay, ring speeds) and the state colors are declared in `20_DESIGN_TOKENS.md`; full animation specs in `08_MOTION.md` / `18_ANIMATIONS.md`; light behavior in `10_LIGHTING.md`; particles in `09_PARTICLES.md`.
