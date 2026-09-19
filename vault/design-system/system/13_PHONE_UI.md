# 13 — Phone UI

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §13.3 (Smartphone), §8.6 (progressive disclosure / depth), §17.9 (Assistant Overlay)
**Status:** Permanent — the Smartphone is the **reference implementation baseline** (§13.3): most day-to-day interaction happens here.

---

## 1. Governing Rule (§13.1)

The phone gets no different design language — it gets a **level of detail** of the exact same system. The reactor, states, colors, and motion rules are identical to every other context; only ring count, particle density, and panel count scale. One consciousness, many windows.

---

## 2. Reactor on Phone

| Property | Spec |
|---|---|
| Ring count | Full reactor: **4–5 rings** (inner + outer clusters, §2.2) |
| Particle density | Standard (state-driven, §09) |
| Ignition point | Present, `--ignition-white`, full breathing participation |
| State palette | Full Core States palette available (§01 §2.2) |

The reactor is the anchor of the primary (home) surface and the Assistant Overlay (§17.9).

---

## 3. Panel Rule — Progressive Disclosure (§8.6, §13.3)

- **One primary panel visible at a time.** No simultaneous panel density on a phone.
- Secondary information is reached by **revealing** it (a state change, a drill-in, an overlay) — never by stacking panels.
- Full interface density (panels, holograms, data streams) is earned by an active task, never shown by default (§1.5).

### Depth on phone

The full Layer 0–4 depth system exists but is used one-at-a-time by role (§05 §2): the reactor or the single focal object holds Layer 4; the one active panel holds Layer 3; the phone does not present two competing Layer-3 regions simultaneously (§13.5 is desktop's privilege).

---

## 4. Available Surfaces (phone)

| Surface | Spec |
|---|---|
| Primary (home) | Reactor centered on `--void-black`; state via color + breathing; minimal chrome |
| Assistant Overlay (§17.9) | Fixed trio: reactor mini-render + voice waveform (when active) + response text |
| Panel | One at a time; Glass; three-tier hierarchy; one accent icon |
| Notifications | Slide-in Glass panels; two-tier text; auto-dismiss |
| Dialogs | Window treatment; state-colored reactor mini-render at top when a system action is involved |
| HUD | Limited availability; anchored `data-mono` readouts only in task contexts |
| Terminal | Available on demand (debug/log views), `data-mono` + state-colored severity border |

---

## 5. Interaction & Motion

- **Voice is the primary input signal:** any detected voice immediately triggers Listening with the fastest transition in the system (~200–300ms, §14.4).
- Motion rules are device-invariant (§08): orbital motion, breathing, event pulses, crossfade-through-family transitions. The phone renders them at reduced particle/panel counts, not at reduced quality.
- **Device pickup** (user motion) triggers a brief gentle brightening of the reactor — a single soft event pulse, lower-intensity than any state transition (§14.5).

---

## 6. Layout & Spacing

- Grid: 4px base, 8px column rhythm (§04 §4).
- Screen gutters: `--space-8` (32px) minimum at the edges.
- Panel padding: `--space-4` (16px) standard, `--space-6` (24px) for the primary panel.
- The background (`--void-black`) dominates — the phone is sparse by default.

---

## 7. Performance Budget

- Particle/panel density is the phone's primary simplification lever under constraint (motion hierarchy, §08 §7) — the reactor's state animation and the waveform never degrade first.
- Volumetric haze (idle) is faint on phone; light shafts are reserved for active states (§4.4).

---

## 8. Absolute Prohibitions

- No stacked simultaneous panels.
- No HUD/terminal as default chrome.
- No reactor degradation below the full-phone spec (4–5 rings) except under enforced performance tiers.
- No device-specific colors, fonts, or motion — only level-of-detail changes.

---

## 9. Token Reference

All phone surfaces consume the system tokens in `20_DESIGN_TOKENS.md`; reactor spec in `07_REACTOR.md`; assistant overlay assembly in `05_COMPONENTS.md` §7.
