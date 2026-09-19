# 02 — Materials

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §6 (Materials), §4 (Light System), §8 (Interface Language)
**Status:** Permanent — frozen by the Visual Bible.

---

## 1. Governing Principle

**Every surface in the JARVIS system belongs to exactly one of five material families.** Mixing behaviors across families (e.g., glass that behaves like metal) is forbidden. Materials behave like real physical substances — glass refracts, metal reflects, crystal transmits light — rather than flat vector fills (§1.3).

The five families are: **Glass, Metal, Crystal, Energy, Hologram.** A surface is assigned to exactly one family at specification time and may not drift.

---

## 2. The Five Material Families

### 2.1 Glass

**Used for:** holographic panels, floating windows, HUD frames, buttons, inputs, notifications, cards.

| Property | Specification |
|---|---|
| Transparency | 70–85% (token `--opacity-glass` = 0.25 opacity, i.e. 75% transparent) |
| Refraction | Slight edge refraction of background light (never of its own content) |
| Rim light | Thin bright edge-highlight on the side facing the core's light source |
| Boundary | **Never fully opaque. Never fully invisible** — always at least a hairline border defining its plane in space |
| Surface color | `--deep-space` as the base tint, mixed at the transparency above |
| Border | Hairline (1px) border in current state color at 20% opacity (`--accent-hairline`) |

**Rules:**
- Glass never casts a drop shadow. Depth is expressed through light/glow, not shadows (§8.2).
- Glass reflects its own content faintly along its lower edge at 4–8% opacity, implying a physical surface even when notionally weightless light (§4.3).
- Glass is the default material for interactive elements (buttons, inputs, dialogs).

### 2.2 Metal

**Used for:** base plate, device chassis, structural framing, ecosystem device bodies.

| Property | Specification |
|---|---|
| Finish | Matte-to-satin — **never glossy, never plastic** |
| Reflection | Soft, blurred reflections of ambient state color; never sharp mirror reflections |
| Texture | Visible fine machining at close range — concentric rings, brushed lines |
| Glow | **Metal never glows on its own.** It only reflects light from the core or an active panel |
| Base colors | `--gunmetal` (`#1B2130`) for dark structure, `--steel` (`#8A94A6`) for lighter chassis |

**Rules:**
- Metal always reflects *something* — a dead matte metal with zero reflection is as wrong as a glossy mirror (§4.3).
- The base plate of the reactor is the canonical metal object: dark gunmetal, faint concentric machining rings, grounding the reactor as a device rather than a pure hologram (§2.1).

### 2.3 Crystal

**Used for:** the reactor core shell, and any "focal" object inside a hologram (e.g., the projected car in Omni-Projection).

| Property | Specification |
|---|---|
| Structure | Faceted, glass-like polyhedral, internally lit |
| Transparency | 40–60% (`--opacity-crystal` = 0.50) — denser than panel glass |
| Light | Refracts and internally reflects its own light source; casts caustic-like light patterns on nearby surfaces when bright |
| Exclusivity | **Crystal is the only material permitted to appear to generate light from within** rather than merely reflect it. This exclusivity marks an object as core-adjacent/important |

**Rules:**
- Only core-adjacent/focal objects may read as internally lit. A panel or a data element made of "crystal" is a spec error.
- Crystal refracts strongly — refraction strength scales with implied material thickness (§6.6).

### 2.4 Energy

**Used for:** particles, connection lines, pulse effects, the ignition point itself.

| Property | Specification |
|---|---|
| Substance | Pure light — no implied solid geometry |
| State | **Always animated** — energy is never static |
| Transmission | Always follows the energy-transmission rules (§4.5): light travels along lines in the direction of actual data/energy flow |

**Rules:**
- Energy is the only family with no resting state. A static "energy" element is a contradiction in terms.
- The ignition point (`--ignition-white`) is pure energy and is the only pure-white element permitted anywhere in the system (§5.6).

### 2.5 Hologram

**Used for:** any projected interface content — the Omni-Projection car, spatial data, floating UI at large scale.

| Property | Specification |
|---|---|
| Construction | Thin glowing wireframe/line-art **plus** sparse glass-paneled data callouts |
| Opacity | Never solid/opaque; always slightly transparent to whatever is behind it |
| Edge treatment | Faint scan-line or particle noise at edges, selling its projected (not physical) nature |
| Opacity token | `--opacity-hologram` = 0.15 base wireframe |

**Rules:**
- A hologram is always a projection of real spatial data or a real projected object relevant to the current task — never ambient sci-fi set-dressing (§18).
- Holograms are rendered with thin glowing line-art, never as filled/opaque objects.

---

## 3. Transparency Hierarchy (§6.7)

From most to least transparent. This ordering **governs z-depth and layering decisions everywhere** — do not place a more-transparent material above a less-transparent one where the lower one is the meaningful content.

| Rank | Material | Approx. transparency |
|---|---|---|
| 1 | Ambient bloom / haze | ~95% |
| 2 | Hologram wireframes | ~80–90% |
| 3 | Glass panels | ~70–85% |
| 4 | Crystal core shell | ~40–60% |
| 5 | Metal | 0% (opaque, but always reflective, never matte-dead) |

---

## 4. Refraction (§6.6)

- Applies to **Glass and Crystal only**.
- Refraction bends background light passing through the object, **never** the object's own foreground content.
- Strength scales with the material thickness implied by the object's visual weight:
  - Thin panels → subtle refraction.
  - Dense reactor core → strong refraction.

---

## 5. Material-to-Component Assignment

| Component | Material |
|---|---|
| Windows (§8.1) | Glass |
| Panels (§8.2) | Glass |
| Cards (§8.3, §17.7) | Glass |
| Floating elements (§8.4) | Hologram + Glass callout |
| Buttons (§17.1) | Glass |
| Inputs (§17.2) | Glass |
| HUD readouts (§17.3) | Hologram (floating, anchored) |
| Notifications (§17.4) | Glass |
| Dialogs (§17.5) | Glass (Window treatment) |
| Maps / spatial views (§17.6) | Hologram |
| Charts (§17.8) | Glass (transparent plot area) |
| Assistant overlay (§17.9) | Glass + Reactor + Energy waveform |
| Reactor base plate | Metal |
| Reactor rings / lattice / ignition | Crystal + Energy |
| Device bodies (ecosystem) | Metal |

---

## 6. Light Interaction by Material

| Material | Glow source | Reflection | Bloom eligibility |
|---|---|---|---|
| Glass | Rim light only (from core/active panel) | Faint self-content reflection 4–8% lower edge | No |
| Metal | Never self-glows; reflects ambient state color | Soft blurred, never mirror | No |
| Crystal | Internally lit — permitted to self-generate light | Internal reflection + caustics | Yes, when core-adjacent |
| Energy | Self-luminous | None (no surface) | Yes (brightest element only) |
| Hologram | Edge glow + wireframe lines | Scan-line noise at edges | No (holograms never bloom) |

**Bloom rule recap (§4.2):** only the brightest element on screen blooms — typically the ignition point or one active accent icon. Secondary elements do not bloom, or hierarchy collapses.

---

## 7. Token Reference

Material behavior is encoded in `20_DESIGN_TOKENS.md` via the opacity set (`--opacity-bloom`, `--opacity-hologram`, `--opacity-glass`, `--opacity-crystal`, `--opacity-metal`) and the structural colors (`--deep-space`, `--gunmetal`, `--steel`). This document is the behavioral contract; the token table is the machine-consumable form.
