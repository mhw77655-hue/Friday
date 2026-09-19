# 15 — Desktop UI

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §13.5 (Workstation / Desktop), §8.6 (Depth System), §7.4–7.5 (Terminal & HUD Typography)
**Status:** Permanent. The workstation is the reference's **"Full Control. Unlimited Power"** context — the deepest and most capable surface.

---

## 1. Governing Rule (§13.1)

Same system, highest level of detail. Desktop is the **only context where multiple Layer-3 content regions may be visible without one being forced to Layer 1 opacity** (§13.5) — it is the density ceiling of the system.

---

## 2. Reactor on Desktop

| Property | Spec |
|---|---|
| Ring count | Full reactor (max ring set) |
| Particle density | Full |
| Volumetric lighting | Full — light shafts and haze at active-state density |
| Position | Anchored as the persistent Layer-4 focal object (or the focal object of the active task) |

---

## 3. Windows & Multi-Window (§8.1, §13.5)

- **Unrestricted panel count** arranged in the depth system.
- Full **multi-window** support: windows are Glass, hairline state borders, panel-in/out transitions (§05 §3.1).
- Multiple Layer-3 content regions are permitted simultaneously — this is the desktop's distinguishing privilege over every smaller context.

### Desktop depth

| Layer | Typical occupant |
|---|---|
| 0 — Void | `--void-black` |
| 1 — Ambient | Bloom, haze, particles |
| 2 — Structure | Panel/window backgrounds, base plates |
| 3 — Content | **Multiple** windows/panels' content, HUD, terminal views |
| 4 — Core/Focus | Reactor or the single active focal object |

**Rule:** even with many windows, **one** Layer-4 focal object. Windows may hold Layer 3 simultaneously; the reactor or the active task object holds Layer 4 alone.

---

## 4. Full HUD & Terminal

- **HUD typography** (§7.5): `data-mono`, right-aligned, anchored — available across the workspace.
- **Terminal views** (§7.4): `data-mono` on near-black panels, 1px state-colored severity border, two-color max — available as first-class windows.

---

## 5. Available Surfaces

| Surface | Spec |
|---|---|
| Multiple windows | Yes — unrestricted count, Glass + hairline borders |
| Panels | Full — all §05 component set |
| Assistant Overlay | Yes — Reactor + Waveform + Text trio |
| HUD | Full |
| Terminal | Full |
| Maps / spatial views | Full hologram material |
| Dialogs / notifications | Full |

---

## 6. Layout & Spacing

- Grid: 4px base, 8px columns.
- Screen gutters: `--space-12`/`--space-16` (48/64px) — the largest canvas earns the largest negative space (§13.7: larger canvases earn more content *and* more air, never a bigger version of a phone layout).
- Window padding: `--space-6` (24px) primary.

---

## 7. Performance Budget

- Multi-window + full particles is the most demanding context. Under constraint, simplify ambient/secondary windows before the reactor or the cause-reporting surface (motion hierarchy §08 §7).
- The desktop's multiple Layer-3 regions may reduce to Layer-2 opacity as a last-tier simplification, but this is the only context allowed to hold several simultaneously in the first place.

---

## 8. Absolute Prohibitions

- No second Layer-4 focal object even in multi-window.
- No windows/panels beyond the one-level nesting rule.
- No device-specific language.
- No simulated window chrome outside the Glass + hairline contract.

---

## 9. Token Reference

System tokens in `20_DESIGN_TOKENS.md`; window/panel assembly in `05_COMPONENTS.md`; HUD in `06_HUD.md`; terminal in `03_TYPOGRAPHY.md` §5.
