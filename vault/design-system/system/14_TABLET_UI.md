# 14 — Tablet UI

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §13.4 (Tablet), §8.6 (Depth System), §12 (Motion)
**Status:** Permanent.

---

## 1. Governing Rule (§13.1)

Same system, higher level of detail. The tablet is the **"Mission Control" density context** (per the reference's own device label): more of the same system at once, never a different system.

---

## 2. Reactor on Tablet

| Property | Spec |
|---|---|
| Ring count | Full reactor, **up to 7 rings** |
| Particle density | Full (state-driven, §09) |
| Ignition point | Full brightness/participation |
| Volumetric lighting | Idle haze faint; active states show visible light shafts (§4.4) |

---

## 3. Panel Rule — Defined Grid (§13.4)

- **2–3 panels visible simultaneously in a defined grid.** The tablet's density is structured, never free-form stacking.
- Panels keep the one-level nesting rule (§8.2), the three-tier hierarchy (§03), and single-accent discipline.
- **Supports the full Layer 0–4 depth system at once** — the tablet is the smallest context that can host a complete depth stack simultaneously.

### Depth on tablet

| Layer | Typical occupant |
|---|---|
| 0 — Void | `--void-black` |
| 1 — Ambient | Bloom, haze, background particles |
| 2 — Structure | Panel backgrounds, base plates |
| 3 — Content | 2–3 panels' content, HUD |
| 4 — Core/Focus | Reactor or single focal object |

**Rule:** still only **one** Layer-4 focal object per screen (§8.6) — "Mission Control" means many panels around one core, not many cores.

---

## 4. Available Surfaces

| Surface | Spec |
|---|---|
| Primary grid | Reactor + 2–3 panels in a defined grid |
| Assistant Overlay | Present; trio anchored per §17.9 |
| Panels | Multiple simultaneous (grid); otherwise identical to §05 |
| HUD | Available — anchored `data-mono` readouts across the grid |
| Terminal | Available |
| Maps / spatial views | Hologram material (§17.6) |

---

## 5. Layout & Spacing

- Grid: 4px base, 8px columns; panel gutters ≥ `--space-8` (32px).
- Panel padding: `--space-4`/`--space-6` per panel role (§04 §2).
- Larger canvas earns more negative space, not more cramming (§13.7 scaling principle).

---

## 6. Performance Budget

- The tablet hosts more simultaneous content; under constraint, simplify ambient particles and secondary panels first (motion hierarchy §12.4 → §08 §7). The reactor and the cause-reporting surface keep full fidelity.

---

## 7. Absolute Prohibitions

- No free-form panel stacking — always the defined grid.
- No second Layer-4 focal object.
- No device-specific design language.
- No simultaneous transition-speed divergence across adjacent panels (§12.3).

---

## 8. Token Reference

System tokens in `20_DESIGN_TOKENS.md`; reactor in `07_REACTOR.md`; grid/spacing in `04_SPACING.md`.
