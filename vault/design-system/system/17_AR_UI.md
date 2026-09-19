# 17 — AR UI

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §13.6 (Smart Glasses / AR Visor), §13.7 (Large Displays / Spatial Projection), §8.5 (Spatial UI), §14.6 (User Proximity), §19.6 (AR), §19.7 (VR)
**Status:** Permanent.

---

## 1. Governing Rule

The AR/VR context renders the reactor and holograms **volumetrically in real space** using the Spatial UI rules — **the only device context where the Omni-Projection Interface pattern (holographic object projection, e.g., the reference's car hologram) is the primary interaction mode rather than a special case** (§13.6).

Same system, spatially embodied. Depth is no longer a screen z-index — it is real-world distance that encodes relevance.

---

## 2. Spatial UI — The Three Depth Fields (§8.5)

UI elements are placed at consistent **real-world-anchored depths**. Depth is never arbitrary; it always encodes importance/proximity of relevance to the current task.

| Field | Distance | Contents |
|---|---|---|
| **Near-field** | ~arm's reach (0.3–1m) | Direct controls, interactive panels |
| **Mid-field** | ~conversation distance (1–3m) | The active object being discussed (e.g., the projected car) |
| **Far-field** | >3m | Ambient/status information (state color, top-level telemetry) |

**Rules:**
- Depth encodes priority: direct controls are nearest; status is farthest.
- **Progressive disclosure by distance (§14.6):** distant users see only the reactor and top-level state; close users see full panel detail — the physical-space equivalent of screen-based progressive disclosure (§8.6).

---

## 3. The Reactor in Space

| Property | Spec |
|---|---|
| Rendering | Volumetric — the reactor exists at a real anchor point in space |
| Scale | Per-field placement (near/mid/far) — a mid-field reactor for conversation, a far-field reactor for ambient presence |
| State language | Unchanged — color, breathing, rings, particles (§07) |
| Omni-Projection | The reactor mini-render is the assistant's "seat" in the interface, per the car-hologram panel (§19.2) |

---

## 4. Holograms in Space (§17.6, §6.5)

- **Material:** Hologram — thin glowing wireframe/line-art + sparse Glass data callouts. Never solid/opaque.
- **Always slightly transparent** to whatever is behind it; faint scan-line/particle noise at edges selling the projected (not physical) nature.
- **Floating elements** carry an anchor line back to their real-world/data point and slight independent drift (§8.4).
- **Energy transmission** runs along holographic links in the direction of actual flow (§4.5).

### Omni-Projection pattern (§13.7, §19.2)

Large displays / spatial projection scale **up**, never simply "zoom": larger canvases earn more simultaneous Layer-3 content (more panels, wider data streams) rather than a bigger version of a phone layout. The Omni-Projection car hologram is the calibration reference.

---

## 5. Proximity Response (§14.6)

| User distance | What they see |
|---|---|
| Far | Reactor + top-level state only |
| Mid | Reactor + active hologram + sparse labels |
| Near | Full panel detail — direct controls, data callouts, waveform |

The density is earned by proximity, exactly as it is earned by task activity on screens (§1.5).

---

## 6. VR Extension (§19.7)

- Inherits all AR spatial rules.
- **Permits full Layer-0 (Void) environment authorship:** in VR, the "background" itself may be an extension of the reactor's ambient bloom field (§2.2) at room scale — the entire environment becomes a literal expression of JARVIS's current state rather than a neutral backdrop.
- This is the one context where Layer 0 may be state-colored via the ambient bloom field (a sanctioned extension of `01_COLORS.md` §2.3, not a new hue).

---

## 7. Panels in Space

- Panels follow §05 (Glass, three-tier, single accent, one-level nesting) but are **placed at a depth field**, not a screen grid.
- HUD readouts (§06) float anchored near their data objects at mid/near fields.
- Panel entry/exit uses the panel easings (§08 §5) with distance-scaled duration (near-field panels transition fastest).

---

## 8. Motion in Space

- Orbital rule unchanged — hologram rings/particles orbit a gravitational center (§3.1).
- **Motion hierarchy (§12.4)** applies volumetrically: the reactor/focal hologram gets full fidelity; far-field ambient content simplifies first.
- Voice response latency is identical to screen contexts: ~200–300ms to Listening (§14.4).

---

## 9. Performance Budget

- Volumetric rendering is the most expensive context. Simplification order is fixed (§12.4): far-field ambient → secondary holograms → panels → reactor/focal object last.
- Particle density and volumetric haze reduce before any state-animation fidelity loss.

---

## 10. Absolute Prohibitions

- No flat panel UI pasted into space — everything volumetric/holographic.
- No opaque holograms.
- No arbitrary placement — every element sits in a named depth field with a purpose.
- No floating element without an anchor line.
- No device-specific colors/motion.
- No cartoon-like scale exaggerations — the reactor's precise geometry is invariant (§15.7).

---

## 11. Token Reference

All spatial behavior reuses the system tokens in `20_DESIGN_TOKENS.md`; hologram material in `02_MATERIALS.md` §2.5; spatial depth fields are defined in this document (the only place real-world distance replaces the z-layer contract, §8.6 → §8.5).
