# 06 — HUD

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §7.5 (HUD Typography), §17.3 (HUD component), §8.4 (Floating Elements), §8.6 (Depth System)
**Status:** Permanent — frozen by the Visual Bible.

---

## 1. Governing Principle

**HUD data supports the focal object; it is never the focal object itself.** HUD readouts are the quiet technical underlay of the system — real machine data, floating near the objects it describes, always anchored by a visible relationship, always at Layer 3.

A HUD element is defined by three properties, all mandatory:
1. It renders **real machine data** in `data-mono`.
2. It is **anchored** — a thin connecting line ties it to its data point.
3. It sits at **Layer 3**, never Layer 4.

---

## 2. Typography (§7.5)

- **Face:** `--font-data-mono` exclusively. No proportional type in HUD.
- **Alignment:** **right-aligned to its associated data point**, never centered, never left-stacked arbitrarily.
- **Connecting line:** a thin line runs from the readout back to the element it labels. **Labels float near, never inside, the object they describe.**

### Number formatting

- Numeric readouts use a fixed, aligned format — columns of figures align on the same digit position (tabular figures are required in the monospace face).
- Units are part of the readout (`m`, `s`, `Hz`, `ms`, `%`, coordinates), always present, never implied.
- Precision is honest: show only the digits the real value supports (see "no fake telemetry", §18).

---

## 3. Anchoring Rules (§8.4)

Every HUD element includes:
1. **A faint anchor line** back to the real-world/data point it describes — never floating with no visible relationship.
2. **Slight independent drift** — a few px of slow, looping motion reinforcing a lightly perturbed 3D field, not a pinned flat-2D layer.

**Anchor-line spec:**

| Property | Value |
|---|---|
| Stroke | Hairline (1px) |
| Color | `--steel` at low opacity, or the element's subsystem/state color at `--accent-hairline` when the readout is state-relevant |
| Path | Straight or gently curved; always terminates exactly at the labeled point |
| Behavior | Never animated more than the readout itself; static except during data change |

---

## 4. Layer Contract

- **Always Layer 3.** HUD may overlay Layer 1 (ambient haze) and Layer 2 (panels) but never push the focal object out of Layer 4.
- HUD is the first content to **simplify or pause** under performance constraints — motion hierarchy §12.4 demotes ambient/secondary content first (§08_MOTION §6).

---

## 5. HUD Content Model

| Field | Spec |
|---|---|
| Data type | Coordinates, telemetry, timestamps, rates, status tokens |
| Font | `--font-data-mono` (12px) |
| Color | `--steel` base; **one** state-color accent per readout for the salient figure |
| Placement | Near the object, never overlapping it; offset ≥ 12px from the data point |
| Density | Sparse by default — HUD appears as a symptom of an active task, never as ambient decoration (§1.5) |

**State-color accent rule:** at most one figure per readout may carry the state color (the salient value). The rest stay `--steel`. This keeps HUD calm while still directing the eye.

---

## 6. HUD Families

Two sanctioned HUD forms, both obeying the above:

1. **Spatial labels** ("3D Spatial Logic"): coordinates/orientation near an object in a spatial/hologram context. Right-aligned to the object, anchor line to its centroid, offset into open space.
2. **Real-time data stream** ("Real-Time Data Stream"): a column of updating telemetry figures. Each row right-aligned, rows separated by hairline rules, streamed values update with a quick numeric roll (see `18_ANIMATIONS.md` §5).

Both appear in the reference panels and share the exact same typographic and anchoring contract.

---

## 7. HUD vs. Terminal

| | HUD | Terminal (§03 §5) |
|---|---|---|
| Face | `data-mono` | `data-mono` |
| Surface | Floating, anchored | Near-black panel |
| Border | None (anchor line) | 1px state-colored left border (severity) |
| Color | `--steel` + one accent | `--steel` + one accent |
| Role | Live spatial/telemetry context | Raw logs, diffs, error traces |

---

## 8. Absolute Prohibitions

- No proportional type in HUD.
- No HUD element without an anchor line.
- No HUD at Layer 4.
- No fabricated readouts ("Lorem stats") — empty/pending states are shown honestly.
- No more than one accent color per readout.
- No decorative HUD: a readout exists only when its value is real and relevant to the current task.

---

## 9. Token Reference

HUD uses the type token `--font-data-mono`, color tokens `--steel`/state accents, and the depth tokens (`--layer-3-content`). See `20_DESIGN_TOKENS.md`.
