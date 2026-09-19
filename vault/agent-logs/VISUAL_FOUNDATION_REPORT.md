# JARVIS Visual Foundation — Completion Report

| | |
|---|---|
| **Source of truth** | `vault/design-system/system/` (20 docs) + frozen `JARVIS_VISUAL_BIBLE.md` |
| **Deliverable** | `mobile/app/src/main/java/com/jarvis/app/vf/` — the complete Visual Foundation |
| **Scope** | Visual rendering engines ONLY. No business logic, no brain wiring, no state pipelines |
| **Verification** | `assembleDebug` ✓ · 135 unit tests green (36 new VF + 99 existing) ✓ |
| **Status** | **COMPLETE** |

---

## Summary

The frozen Design System was turned into a runnable, Design-System-exact visual layer for the JARVIS Android app. All 11 requested subsystems were built under a single new package `com.jarvis.app.vf` with a clean separation: **pure engine kernels** (plain Kotlin, JVM-unit-testable) that compute state, plus **thin Compose renderers** that draw it. Every value traces to `20_DESIGN_TOKENS.md`; every animation traces to `08_MOTION.md`/`18_ANIMATIONS.md`; every state traces to `07_REACTOR.md` §6.

**Design law enforced at every level:** *does this communicate real state, or does it exist to look good?* Only the former is permitted.

---

## The 11 Subsystems

| # | Subsystem | Files | What it implements |
|---|---|---|---|
| 1 | **Design tokens** | `vf/tokens/JvTokens.kt` | All tokens from 20_DESIGN_TOKENS.md: 6 core + 9 state colors, 7-token 4px type scale, spacing, 8 durations, 4 easing curves, 5 depth layers, 5 material opacities, audio levels. Plus `JvColorFamily` (the §2.3 family classification) and `JvReactorStateColor` (the §5.2 state-color meaning contract). |
| 2 | **Typography engine** | `vf/typography/TypographyEngine.kt` | The three voices (Display / UI / Mono), the 4px scale, the three-tier hierarchy builder, terminal (2-color max) and HUD styles. |
| 3 | **Theme engine** | `vf/theme/ThemeEngine.kt` | `JvTheme` — MaterialTheme wrapped with the frozen Design-System dark palette; `LocalJvTokens` + `LocalJvTypography` composition locals. |
| 4 | **Motion engine** | `vf/motion/MotionEngine.kt` | Breathing envelope (±8–12% sine, 3.2–4s), event-pulse envelope (150ms attack / 500ms decay), all durations, easing factories, Compose helpers. Pure math, unit-tested. |
| 5 | **Animation engine** | `vf/animation/AnimationEngine.kt` | The state transition matrix (§12.3): direct vs through-neutral paths, `resolvedColor` crossfade, warning 1Hz / critical 2Hz pulse, ring-speed factors. Pure, unit-tested. |
| 6 | **Glow engine** | `vf/glow/GlowEngine.kt` | Stacked-stroke glow (no `Modifier.blur` — no-op below API 31), bloom (brightest-element-only, §4.2), glass self-reflection (4–8%), metal reflection, volumetric haze. |
| 7 | **Particle engine** | `vf/particle/ParticleEngine.kt` | Orbital field paths, lifecycle (fade 15% in / 25% out, §3.2), the direction grammar (inward=input, outward=output, spiral-in=learning, drift=listening), density by state, deterministic precompute. Pure, unit-tested. |
| 8 | **Reactor rendering engine** | `vf/reactor/ReactorEngine.kt` + `ReactorRenderer.kt` | `ReactorSpecState` (12 visual states, §9) → resolved `ReactorSpec`; full four-register Canvas render: base plate (Metal) + inner/outer ring clusters + crystal core + ignition point + emission field + particles. |
| 9 | **Orb renderer** | `vf/orb/OrbRenderer.kt` | The reduced phone-primary reactor: ignition + 1–2 rings + glow + particles, full state-color/breathing language, crossfade-through-family rules. |
| 10 | **HUD renderer** | `vf/hud/HudRenderer.kt` | `Readout`, `AnchoredReadout` (data-mono, right-aligned, anchor line), `DataStream` (hairline rows, one accent). |
| 11 | **Component library** | `vf/components/` | `JvButton`, `JvTextButton`, `JvWindow`, `JvPanel`, `JvTieredPanel`, `JvCard`, `JvInput`, `JvNotification`, `JvDialog`, `JvChip` — all Glass material, hairline state borders, single-accent, micro-pulse press. |

Plus a **demo surface** (`vf/demo/VisualFoundationDemo.kt`) wired into the Sandbox tab — cycles all 12 states, renders orb + HUD + every component with static state only (verification, not business logic).

---

## Design-System Exactness

| Rule (source doc) | Enforcement |
|---|---|
| State palette frozen (§01) | `JvReactorStateColor` — only these 12 colors may appear on a reactor/orb |
| Breathing sine ±8–12%, 3.2–4s (§3.3) | `MotionEngine.breathingBrightness` — unit-tested at peak/trough/period |
| Event pulse 150ms/500ms (§3.4) | `MotionEngine.eventPulseBrightness` — unit-tested at attack/decay |
| No bounce/elastic anywhere (§12.2) | Only 4 canonical easing curves; token test asserts no bounce curve |
| Crossfade through family, never blend amber→red (§12.3) | `AnimationEngine.resolveTransition` — unit-tested: amber↔amber direct, Thinking→Warning through-neutral, any→Offline through-neutral |
| Bloom = brightest element only (§4.2) | `drawBloom` is an explicit engine call, not a scene filter |
| Glow without blur (minSdk26) (§4.1) | Stacked-stroke technique (7-layer) — same proven approach as existing Orb.kt |
| Particle fade 15% in / 25% out (§3.2) | `ParticleEngine.lifecycleOpacity` — unit-tested at every segment |
| Density = activity (§09) | Research 180 > idle 40; sleep/offline 0 — unit-tested |
| Ignition point = only pure white (§5.6) | `drawIgnition` uses `--ignition-white` only |
| 4px spacing (04) | `JvTokens.Space1…Space16` — unit-tested all multiples of 4 |
| 4px type scale (03) | `JvTokens.Font*` + `TypographyEngine.Scale` |
| One Layer-4 focal object (§8.6) | Documented contract; demo renders a single orb per screen |
| Terminal max 2 colors (§7.4) | `TypographyEngine.TerminalBase` + `TerminalAccent` |

---

## Architecture

```
com.jarvis.app.vf/
├── tokens/      JvTokens.kt            — frozen token registry + color-family/state enums
├── typography/  TypographyEngine.kt    — 3 voices, 4px scale, 3-tier hierarchy
├── theme/       ThemeEngine.kt         — JvTheme, composition locals
├── motion/      MotionEngine.kt        — breathing + event-pulse envelopes, durations
├── animation/   AnimationEngine.kt     — transition matrix, crossfade paths, pulse Hz
├── glow/        GlowEngine.kt          — DrawScope extension renderers (glow/bloom/reflection/haze)
├── particle/    ParticleEngine.kt      — field, lifecycle, direction grammar, density
├── reactor/     ReactorEngine.kt       — 12-state → spec (pure model)
│                ReactorRenderer.kt     — full four-register Canvas renderer
├── orb/         OrbRenderer.kt         — reduced phone-primary renderer
├── hud/         HudRenderer.kt         — readout / anchored / data-stream composables
├── components/  JvButton, JvPanel, JvCard, JvInput, JvNotification, JvDialog, JvChip
└── demo/        VisualFoundationDemo.kt — static all-subsystem demo (wired into Sandbox)
```

**Pure kernels** (no Compose runtime dependency, JVM-tested): `JvTokens`, `TypographyEngine` (TextStyle is compose-ui-text, JVM-safe), `MotionEngine` envelopes, `AnimationEngine`, `ParticleEngine`, `ReactorEngine`. **Compose renderers** consume them.

---

## Verification

- **Compile:** `:mobile:app:compileDebugKotlin` — clean.
- **APK:** `:mobile:app:assembleDebug` — success, 17 dex files, VF classes confirmed packaged.
- **Tests:** `:mobile:app:testDebugUnitTest` — **135 total, 0 failures**:
  - VF (new): 36 — JvTokens(5), Motion(6), Animation(10), Particle(6), Reactor(9).
  - Existing: 99 companioncore + humancore — no regressions.
- **Runtime demo:** Sandbox tab renders the orb, state cycling, HUD, and all components with static state (no brain wiring).

---

## Notes & Boundaries

1. **Palette divergence, deliberate.** The existing app screens (`JarvisColors`: violet idle) predate the Design System. The VF uses the frozen Design System palette (blue idle `#2E9BFF`, amber thinking `#F5A623`) exactly as required — the two coexist; existing screens are untouched and still compile/pass tests.
2. **No business logic.** The VF contains no HC/CompanionCore wiring, no ViewModels, no state pipelines. `VisualFoundationDemo` drives the renderers with a local state index only. Wiring to `RenderIntent`/`PresenceMode`/`CompanionSignal` is future work (contracts already exist in `companioncore/contract/`).
3. **Legacy `com.jarvis.app.Orb` untouched.** The existing Home orb keeps working; `OrbRenderer` is its Design-System-exact replacement and the recommended migration target.
4. **Material / sound are token-only** at this phase: materials (opacity tokens) and sound levels (`-36dB` hum) are registered in `JvTokens` but no audio engine or material shader was requested — the renderers consume the visual opacity side.

---

## File Manifest

```
mobile/app/src/main/java/com/jarvis/app/vf/
  tokens/JvTokens.kt                        (238 lines)
  typography/TypographyEngine.kt            (117)
  theme/ThemeEngine.kt                      (125)
  motion/MotionEngine.kt                    (148)
  animation/AnimationEngine.kt              (132)
  glow/GlowEngine.kt                        (168)
  particle/ParticleEngine.kt                (196)
  reactor/ReactorEngine.kt                  (122)
  reactor/ReactorRenderer.kt                (221)
  orb/OrbRenderer.kt                        (170)
  hud/HudRenderer.kt                        (148)
  components/JvButton.kt, JvPanel.kt, JvInput.kt,
             JvNotification.kt, JvDialog.kt, JvChip.kt
  demo/VisualFoundationDemo.kt              (152)
mobile/app/src/test/java/com/jarvis/app/vf/
  JvTokensTest.kt, MotionEngineTest.kt, AnimationEngineTest.kt,
  ParticleEngineTest.kt, ReactorEngineTest.kt   (36 tests)
vault/agent-logs/VISUAL_FOUNDATION_REPORT.md     (this report)
```

---

## Follow-up Recommendations

1. **Wire to CompanionCore:** consume `RenderIntent`/`PresenceMode` to drive `OrbRenderer` state — the contract types already exist; the VF is the render layer they were designed to feed.
2. **Migrate Home orb** to `OrbRenderer` with the VF palette when screens are next redesigned.
3. **Add tablet/desktop ring counts** via the existing `RingCountPhone` token → a `deviceContext` parameter.
4. **Performance tiering** (§2.31): add a `vf/demo`-adjacent frame-budget hook that drops particle count before reactor fidelity.
5. **Audio phase:** implement the `--audio-hum-db` reactor hum as the sound engine's first consumer.
