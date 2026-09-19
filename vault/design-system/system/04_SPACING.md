# 04 — Spacing

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §7.2 (base 4px scale), §1.3 (luxury principles: generous negative space, thin hairline dividers), §8.2 (generous internal padding, no drop shadows)
**Status:** Permanent. All tokens derive from the Visual Bible's 4px base and its stated luxury rules.

---

## 1. Governing Principle

**Luxury in JARVIS is restraint under obvious capability.** Spacing is the primary luxury signal: generous negative space, thin hairline dividers instead of boxes, and single-accent-color discipline per panel. The system is sparse by default and dense only as a symptom of real activity (§1.5).

Two absolute rules:
1. **Every spacing value is a multiple of 4px** — the same base the type scale uses (§7.2). No arbitrary values.
2. **Separate with hairlines, not boxes.** Structure is implied by air and by 1px dividers, never by enclosing rectangles with margins.

---

## 2. The Spacing Scale

| Token | Value | Typical use |
|---|---|---|
| `--space-1` | 4px | Hairline-to-element gaps, icon inset |
| `--space-2` | 8px | Tight inner gaps, icon–label pairs |
| `--space-3` | 12px | Element clusters inside cards |
| `--space-4` | 16px | Standard inner panel padding (baseline) |
| `--space-6` | 24px | Panel padding for primary content, section gaps |
| `--space-8` | 32px | Between panels / screen gutters |
| `--space-12` | 48px | Major section breaks, generous margins |
| `--space-16` | 64px | Screen-level outer margins on large canvases |

**Rules:**
- `--space-4` (16px) is the **default internal panel padding** for standard panels; `--space-6` (24px) for primary/focused panels.
- Never use 1px, 2px, 3px, 5px, 6px, 7px, 9px… — spacing is always a 4px multiple. The single exception is the **hairline** itself (1px), which is a stroke width, not a spacing unit.
- Larger canvases (tablet/desktop/spatial) earn *more* negative space proportionally, not more cramming — see `13_PHONE_UI.md`–`17_AR_UI.md`.

---

## 3. Line-Height Rhythm

Line-height follows the same 4px discipline:

| Type | Size | Line-height (recommended) |
|---|---|---|
| `display-xl` | 48px | 56px (7 × 8) |
| `display` | 32px | 40px |
| `heading` | 20px | 28px |
| `label` | 12px | 16px (uppercase, tracked) |
| `body` | 14px | 20px |
| `caption` | 11px | 16px |
| `data-mono` | 12px | 16px |

All line-heights are multiples of 4px so text baselines align to the grid.

---

## 4. Layout System

### 4.1 Grid

- **Base unit:** 4px. All layout dimensions (margins, gutters, padding, column widths) snap to it.
- **Column rhythm:** panels and cards align to an 8px column rhythm (2 × base) within a 4px-capable gutter system. Simplicity is preserved — JARVIS does not use fractional or exotic grids.
- **Alignment:** all content aligns to the grid; HUD and floating elements are the only sanctioned off-grid elements (they are anchored by a connecting line, not placed on-grid, per §8.4).

### 4.2 Negative space budget

| Surface | Minimum negative-space ratio |
|---|---|
| Screen background (`--void-black`) | The background is the dominant surface. Content occupies a minority of the canvas by default |
| Panel | Internal padding ≥ 16px; three-tier hierarchy separated by ≥ 8px between tiers |
| Card | Key visual centered above a two-line label pair (§8.3); padding ≥ 12px |
| Between panels | ≥ 24px gutter |

**Rule:** when in doubt, add space, not elements. The fastest way to lose trust is to be busy when there is nothing to say (§1.5).

---

## 5. Hairline Dividers (§1.3)

- Dividers are **1px hairlines**, never 2px+ strokes, never filled separator rectangles.
- Default divider color: `--steel` at low opacity on `--deep-space` (structural, quiet).
- State-colored hairlines: state color at `--accent-hairline` (20% opacity) — used for borders that carry meaning (window frames §8.1, button borders §17.1, terminal severity border §7.4).
- Hairlines are drawn **inside** the element's bounding area, so element sizes stay grid-aligned.

---

## 6. Depth and Spacing

Spacing interacts with the five-layer depth system (§8.6) in one consistent way:

| Layer | Contents | Spacing behavior |
|---|---|---|
| 0 — Void | Background | Unbounded negative space |
| 1 — Ambient | Bloom/haze, particles | Not grid-anchored (light, not structure) |
| 2 — Structure | Base plates, panel backgrounds | Grid-anchored, `--space-4`/`--space-6` padding |
| 3 — Content | Text, icons, data | Grid-anchored, tightest (`--space-2`/`--space-3` intra-cluster) |
| 4 — Core/Focus | The reactor / focal object | Centered, surrounded by the system's maximum negative space |

---

## 7. Component Spacing Contract (summary)

| Component | Internal | Between elements |
|---|---|---|
| Button | 8–16px horizontal, 8px vertical | — |
| Input | 12px horizontal, 8px vertical (bottom-hairline only) | — |
| Panel | ≥ 16px all sides | ≥ 8px between tiers |
| Card | ≥ 12px | icon-block → label pair ≥ 8px |
| Notification | ≥ 16px | icon → text ≥ 12px |
| HUD readout | — (floating, anchored) | label line length ≥ 12px offset from data point |

Full component specs in `05_COMPONENTS.md`.

---

## 8. Absolute Prohibitions

- No spacing value outside the 4px-multiple scale (except the 1px hairline stroke).
- No boxes-with-borders as separators — hairlines and air only.
- No cramming: a panel that exceeds the three-tier hierarchy is a spec violation before it is a layout problem.
- No drop shadows to create depth (shadows are a flat-UI convention — JARVIS uses light/glow, §8.2).

---

## 9. Token Reference

Spacing tokens (`--space-1` … `--space-16`) and the 4px base are declared in `20_DESIGN_TOKENS.md`. This document is the placement contract for every surface.
