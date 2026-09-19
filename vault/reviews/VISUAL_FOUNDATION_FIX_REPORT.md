# Visual Foundation — Fix Report

| | |
|---|---|
| **Audit reviewed** | `VISUAL_FOUNDATION_AUDIT.md` (2026-08-05) |
| **Scope** | Fix valid issues in `mobile/app/src/main/java/com/jarvis/app/vf/` only. No new features, no system redesign, no unrelated code. |
| **Disposition** | Every finding reviewed; all were valid observations. None rejected as factually false. Fixed all concrete defects and the bounded renderer corrections; the remainder is feature work explicitly out of scope. |
| **Date** | 2026-08-05 |

---

## 1. Accepted findings — FIXED

Concrete defects, spec violations in existing behavior, dead code, and correctness bugs that were repaired without adding features or redesigning.

| Finding | Fix |
|---|---|
| **Arch 2 / M-adjacent — latent crash.** `CODING` belonged to no `JvColorFamily` → `JvColorFamily.of(CODING)` / `AnimationEngine.resolveTransition(CODING, …)` threw `NoSuchElementException`. | Registered `CODING` in `NEAR_WHITE_BUILDING` (`JvTokens.kt`). Added exhaustive regression tests (see §3). |
| **Arch 3 / M3 — breathing not on core brightness.** `breathing` was applied only to particle radius; core/glow/ignition drawn at fixed alpha, and the envelope was a cubic-bezier tween, not the spec sine. | Both renderers now route breathing through `MotionEngine.rememberBreathingBrightness` (true sine, ±amp §3.3) and apply it to core brightness: crystal-shell alpha (clamped), and the glow/bloom/haze/metal-reflection via a new multiplicative `brightness` parameter on `GlowEngine.drawStackedGlow/drawBloom/drawMetalReflection/drawVolumetricHaze`. Breathing removed from particle radius (was the §3.3 misapplication). Ignition point left steady — it is the single brightest anchor and §9.2 has it hold steady while listening. |
| **M4 — Offline renders full-brightness ignition + glow field.** | Both renderers now draw Offline as a single dim Steel ember (~15% brightness, small low-alpha circles), with no volumetric haze, no glow stack, no bloom, no rings, no particles (§9.13). |
| **Arch 7 / R7 — Theme mono `bodyMedium`.** `ThemeEngine` mapped Material3 `bodyMedium` to JetBrains Mono, silently monospacing all default Material text (violates §03 §9 "monospace never for conversational text"). | `bodyMedium` now uses `InterFamily` at 14sp/20sp (the token-correct body style). |
| **R6 / Fix 3 — typography `em` line-heights.** `DisplayXl.lineHeight = 56.em` at 48sp ≈ 2688sp; `Body = 20.em`. | All seven voices now carry absolute `sp` line-heights per the `20_DESIGN_TOKENS.md` table (display-xl 56, display 40, heading 28, label 16, body 20, caption 16, data-mono 16). |
| **R5 / Fix 9 — reserved-white violation.** `TextPrimary = #F4FBFF` (ignition white) as body/heading text, violating §5.6. | `TextPrimary` → `#ECEDF1`, a distinct near-white (matches the app's established headline color; ~19:1 contrast). Corrected the source row in `20_DESIGN_TOKENS.md` too, where the error originated. |
| **R3 — `JvCard` double border when selected.** `.border(…).then(Modifier.border(…))` rendered a 2dp frame. | Removed the redundant second `border`; a single selected-state border remains. |
| **R4 — `JvInput` near-opaque.** `DeepSpace.copy(alpha = 0.9f)` contradicted the Glass 70–85% transparency / 0.25 opacity token. | Background → `JvTokens.OpacityGlass` (0.25). |
| **R2 — sub-pixel raw-px strokes.** Machining `Stroke(0.5f)` and ring strokes 1.2–1.8f px vanish/alias across densities. | Ring strokes are now density-independent: the shared ring builder converts a `strokeWidthDp` via the DrawScope `Density`; the machining hairline uses `with(density) { 0.5.dp.toPx() }`. |
| **Arch 4 / Fix 10 — event pulse not single-shot.** `rememberEventPulseBrightness` looped forever (`RepeatMode.Restart`), used `triggerKey` only in a label, and emitted a triangle. | Rewritten as a true single-shot: `Animatable` + `LaunchedEffect(triggerKey)` drives elapsed time through the pure `eventPulseBrightness()` envelope. The envelope itself now matches §12.2 — a fast linear attack over 150ms, then a **sharp ease-out decay** over 500ms via `JvTokens.EaseWarningPulse()` sampled at remaining brightness (≈0.63 at 10% decay, not the linear 0.9) — so the spike is sharp, never a triangle. (Discrete-event one-shot; the continuous 1Hz/2Hz alert pulse is the separately-wired M1 below.) Tests added pinning the attack/decay shape. |
| **M1 / Arch 1 — Warning/Critical sharp pulse unrendered.** `PulseMode.SHARP_1HZ/SHARP_2HZ` was set in the spec but never consumed; the single most important alert signature was absent. | Wired end-to-end. `MotionEngine` gained `sharpPulse()` (a repeating sharp envelope: fast ~37ms attack + §12.2 sharp ease-out decay within a 150ms spike, silent between cycles) and `rememberSharpPulse(hz)` (single `Animatable` looping one period per cycle — no infinite transition). Both renderers map `spec.pulseMode` → 1Hz/2Hz and layer `coreBrightness = breathing × (1 + pulse × SharpPulseAmplitude 0.20)` — the spike sits on top of the ambient breathing per §3.4, never replacing it; non-alert states get factor 1. Distinct 1Hz/2Hz flash rates satisfy §5.7's grayscale-survivable separation. Tests added for the envelope, the frequency separation, and the `WARNING→SHARP_1HZ` / `CRITICAL→SHARP_2HZ` spec wiring. |
| **Arch 9 / Fix 11 — duplicated wobble-ring builder.** Near-identical 40-line functions in both renderers. | Extracted to `vf/orbital/OrbitalRing.kt` (shared, density-aware); both renderers call it. |
| **Perf R2 / Battery — motion in "no motion" states.** `noisePhase` (9s) and `flickerPhase` (2.6s) infinite transitions ran for every state, incl. Offline/Sleeping; Offline's breathing tween looped at 10Hz with amplitude 0. | All three animation phases are now created **only when the state has rings / particles / a breathing cycle**. Offline creates no animations at all; Sleeping keeps only its spec'd slow breathing and ring wobble. |
| **Maint — dead code.** | Removed: `TypographyEngine.Scale` map, `JvTokensAccess`, `RingCountInner`/`RingCountOuter`. Kept deliberately (spec'd, deferred wiring): `ParticleEngine.position()`, `AnimationEngine` transition API, `DurVoiceResponse` (frozen token). |
| **Maint — drift docstrings.** "every visual element reads from the spec", "there are no other motion sources", OrbRenderer "phone-primary / Design-System-exact … crossfade". | Corrected in `MotionEngine`, `OrbRenderer`, `ReactorRenderer` to describe what the code actually does (incl. an explicit note that the transition matrix is not yet wired). |
| **Maint / Fix 2 — `JvColorFamily` not exhaustively tested.** | Added exhaustive membership + 12×12 transition tests (see §3). |

## 2. Accepted findings — PARTIALLY FIXED

| Finding | What was fixed / what remains |
|---|---|
| **Arch 1 — `ReactorSpec` aspirational.** | Renderers now consume `breathingMs/Amplitude` (as brightness), `ringCount`, `particleDirection`, and `pulseMode` (the SHARP_1HZ/2HZ alert pulse). Still not consumed (feature): `researchScanMs`, `ringAlignment`, `hasFlicker`, and the `AUDIO` pulse mode (deferred M2). |
| **Arch 8 / M11 — hardcoded ring geometry.** | `ReactorRenderer` now draws `spec.ringCount` rings (split inner/outer), so Sleeping=2 and Offline=0. `deviceContext` parameterization and tilt/parallax tuning remain deferred (feature). |
| **Battery — always-on transitions / full redraws in Offline.** | Offline now draws only the base plate + ember with zero animation; Sleeping drops particle flicker. Active states still animate continuously, as §3.3 requires. |

## 3. Accepted findings — DEFERRED (out of scope: new features)

The following are **valid observations** — the code genuinely does not render these — but implementing them is feature work or system redesign, which was explicitly excluded. They are recorded here so the path to spec is known.

| Finding | Why deferred |
|---|---|
| **M2** Listening audio-driven rings | Requires an audio-amplitude input / pipeline. |
| **M5** Faceted crystal + facet flicker | New crystal geometry. |
| **M6** Research scan-ring | New visual. |
| **M7** Planning ring-alignment beat | New visual. |
| **M8** Coding `data-mono` char-stream | Not modeled; new feature. |
| **M9** Particle direction grammar rendering | Wiring `ParticleEngine.position()` into renderers; `position()` kept in place for it. |
| **M10** Ring opacity parallax | New visual. |
| **M11** `deviceContext` parameter | New parameterization (ring counts + particle budgets per §13). |
| **M12** Voice waveform | New component + audio engine. |
| **M13** Sound language | New subsystem. |
| **M14** Startup/shutdown sequences | New sequences. |
| **M15** Emotional Intelligence layer | New layer. |
| **M16** Wire `AnimationEngine` crossfade | Integration feature; `AnimationEngine` is correct and tested, kept for it. |
| **M17** Charts / Maps / Overlay / Terminal / Hologram / connectors | New components. |
| **M18** Icon language | New component language. |
| **M19** Floating-element drift | New animation. |
| **M20** Performance tiers | New tiering/frame-budget system. |
| **Perf** path caching, per-frame allocation reduction, renderer screenshot tests | Optimizations / test infrastructure, not correctness. |
| **Fix 13** Sleep-violet 4.21:1 < 4.5:1 floor | Verified accurate (computed 4.21:1), but the remedy either changes the frozen state palette or adds a text-safe accent variant — a design change. |
| **Maint** assertion-based color tests | Functional as-is; cosmetic refactor out of scope. |

## 4. Rejected findings

**None were rejected as factually invalid.** Every finding in the audit was verified against the code and reproduced where possible (the `CODING` crash, the `em` line-heights, the double border, the mono `bodyMedium`, the dead symbols, the 4.21:1 Sleep ratio, the unused `spec` fields, the unreferenced transition matrix).

One nuance: the audit's headline claims ("Design-System-exact", "every state traces to §6", "COMPLETE") are attributed to a `VISUAL_FOUNDATION_REPORT.md` that is **not present in the repository**, so those attributions could not be verified. The underlying code observations stand independently and are accurate.

## 5. Tests run

`gradle :mobile:app:testDebugUnitTest --offline`

- **Full suite: 20 test classes, 149 tests, 0 failures, 0 errors.**
- VF suite: **50 tests, 0 failures** across `AnimationEngineTest` (12), `JvTokensTest` (7), `MotionEngineTest` (13), `ParticleEngineTest` (6), `ReactorEngineTest` (10), `TypographyEngineTest` (2).

New regression tests added:

- `JvTokensTest`: every state belongs to exactly one `JvColorFamily` (guards the `CODING` gap); `TextPrimary` ≠ reserved ignition white (§5.6).
- `AnimationEngineTest`: all 12×12 transitions resolve without throwing; `CODING→BUILDING` is direct (same family).
- `TypographyEngineTest` (new): every voice line-height is `TextUnitType.Sp` (not `em`) and matches the token table (56/40/28/16/20/16/16).
- `MotionEngineTest` (+7): event-pulse attack reaches 1 exactly at the peak; decay is the §12.2 sharp ease-out (mid-decay well below linear 0.5, steep drop off the peak, monotone) — guards against the linear triangle; and the new alert pulse — 1Hz spikes once/second, 2Hz twice/second, the two frequencies distinct/non-overlapping at a grayscale-discriminating instant (§5.7), zero for non-alert hz, and the spike's fast-attack-then-sharp-ease-out shape.
- `ReactorEngineTest` (+1): `WARNING→SHARP_1HZ`, `CRITICAL→SHARP_2HZ` — pins the spec values the renderers consume.

## 6. Final status

The audit's **FAIL** verdict is now partially retired:

**Spec-correct after this pass:** the token layer (incl. the `CODING` family and a §5.6-compliant text color), the type engine (absolute line-heights), the theme (no mono body), breathing applied to core/glow brightness as a true sine, the inert Offline ember, spec-driven ring counts, motion-free Offline/Sleeping, single-shot event pulse, density-consistent strokes, and the component bugs.

**Still outstanding (documented, not fixed by design):** the audio rings (M2), facet flicker, research/planning/coding tells, particle direction grammar, parallax, device-context parameterization, the crossfade transition wiring, the voice/audio channel, and the performance/battery tiering. Each maps to a spec'd field or subsystem already modeled in the engine layer, so the remaining work is bounded and does not touch the tokens, engines, or models — consistent with the audit's own note that "the renderers, not the engines, are what must be brought to spec."
