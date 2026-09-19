# Visual Foundation — Independent Audit

| | |
|---|---|
| **Audited against** | `JARVIS_VISUAL_BIBLE.md` (frozen source of truth) + `system/01–20` (Design System) |
| **Implementation reviewed** | `mobile/app/src/main/java/com/jarvis/app/vf/` (Visual Foundation, "COMPLETE" per `VISUAL_FOUNDATION_REPORT.md`) |
| **Scope** | Renderers, engines, tokens, typography, components, demo. No code was modified. |
| **Date** | 2026-08-05 |

---

## Executive Summary

The **token layer and pure engine kernels are largely faithful** to the frozen Bible and Design System: palette values, durations, easing curves, spacing, the 4px type scale, particle lifecycle fade math, and the state→color mapping all match the source documents exactly.

The **rendering layer — the thing the deliverable is named for — implements only a fraction of the reactor's state language.** Most of the `ReactorSpec` that `ReactorEngine` produces is never consumed by the renderers. Signature motions of the majority of the 12 states are absent:

- Warning/Critical sharp 1Hz/2Hz pulses are not rendered (`pulseMode` is dead).
- Listening's audio-driven rings are not rendered (no audio input exists).
- The core breathing pulse (§3.3) is not applied to core/glow/ignition brightness — it only resizes particles, so the system's single most important resting motion is effectively missing.
- Offline renders a **full-brightness white ignition point + steel glow field** instead of the spec's dim 15%-brightness ember with no glow.
- Facet flicker, research scan-ring, planning ring-alignment, coding char-stream, and the particle direction grammar (inward/outward/spiral/drift) are all specified, unit-tested as model values, and **never rendered**.

There is also a **latent crash**: `CODING` is a `JvReactorStateColor` but is not registered in any `JvColorFamily`, so `AnimationEngine.resolveTransition()` on any Coding transition throws `NoSuchElementException`. A **typography bug** sets line-heights in `em` (`56.em`, `20.em`), which yields a ~2688px line-height on the 48px wordmark. The Bible's **reserved-white rule (§5.6) is violated** — `--ignition-white #F4FBFF` is used as primary body-text color (`TextPrimary`), an error that originates in `20_DESIGN_TOKENS.md` and was copied verbatim into `JvTokens.kt`.

Sound and voice visualization (the waveform, the audio engine, the hum, startup/shutdown sync) are entirely absent — admitted in the report as "token-only" — and performance tiering, device contexts, and startup/shutdown sequences are unimplemented. The report's headline claims ("Design-System-exact visual layer", "every state traces to §6", "COMPLETE") overstate what the renderers actually draw.

**Verdict: FAIL**

---

## Missing Requirements

The following Bible/Design-System requirements have no renderer, no component, or no consuming code path.

| # | Requirement (source) | Status in VF |
|---|---|---|
| M1 | Warning 1Hz / Critical 2Hz sharp pulse (§3.8, §9.10–9.11, §07 §6) | `PulseMode.SHARP_1HZ/SHARP_2HZ` set in spec, **never consumed** by `ReactorRenderer`/`OrbRenderer`. Warning/Critical render breathing + faster rings only. The single most important alert signature is absent. |
| M2 | Listening rings driven by live audio amplitude (§3.7, §9.2, §07 §5) | `PulseMode.AUDIO` set, never consumed; renderers accept no audio input. |
| M3 | Core brightness breathing ±8–12% sine (§3.3, §08 §2) | `breathing` multiplier is applied **only to particle radius**. Core circles, glow, bloom, and ignition are drawn at fixed alpha. The system's signature resting motion is effectively missing. |
| M4 | Offline = dim ember, Steel @ ~15%, no glow field (§9.13, §10 §9) | Renderers draw the full-brightness `IgnitionWhite` ignition + a full steel `drawStackedGlow`+`drawBloom` field. No ember-dimming logic exists. |
| M5 | Faceted crystal, rim-light facet edges, triangular focal motif, asynchronous facet flicker (§2.4, §3.6, §9.3) | Crystal core is two plain circles; `hasFlicker` is set in spec and **never used**. |
| M6 | Research outward-scanning ring every 2s (§9.4) | `researchScanMs=2000` set in spec, never used. |
| M7 | Planning ring alignment beat (§9.5) | `ringAlignment` set in spec, never used. |
| M8 | Coding `data-mono` char-stream on one ring (§9.6) | Not modeled, not rendered. |
| M9 | Particle direction grammar — inward=input, outward=output, drift=listen, spiral-in=learn (§3.2, §09 §4) | `ParticleEngine.position()` is **never called**. Both renderers compute positions inline with the same generic orbit equation for every direction; all four directional modes render identically. Direction grammar is dead code. |
| M10 | Ring opacity parallax (front bright / behind dim, §2.5) | Rings are single closed paths at uniform alpha. No front/back modulation. |
| M11 | Ring counts per context — phone 4–5, tablet ≤7 (§2.5, §13) | `spec.ringCount` unused in `ReactorRenderer` (hardcodes 2+2). `OrbRenderer` is labeled "phone-primary" but renders the **watch-floor** 1–2 rings. No `deviceContext` parameter exists. |
| M12 | Voice waveform — §10, `12_VOICE_VISUALIZER.md` | **No waveform component exists.** No attack 40ms/release 180ms envelope, no 200ms glow-trail, no Listening-teal/Speaking-building color switch. Voice visualization is entirely missing. |
| M13 | Sound language — hum, UI sounds, notification pitch, startup/shutdown sync (§11, `11_SOUND.md`) | Token-only (`AudioHumDb`, `AudioUiHeadroomDb`). No audio engine, no hum, no sync. |
| M14 | Startup / Shutdown sequences (§3.9, §18 §7) | Not implemented. Ignition never brightens from zero; rings never decelerate to a held ember. |
| M15 | Emotional Intelligence layer (§15, §01 §3.2) | No uncertainty/confidence/curiosity/urgency parameters anywhere in the model or renderers. |
| M16 | Crossfade-through-family state transitions (§12.3, §18 §3) | `AnimationEngine.resolveTransition` is fully implemented and tested but **called by nothing**. Renderers and the demo snap `state.color` directly; the transition matrix is unused dead code. |
| M17 | Charts (§17.8), Maps/Spatial (§17.6), Assistant Overlay with Reactor+Waveform+Text trio (§17.9), Terminal/Log view (§05 §6.3), energy-transmission connectors (§4.5), Hologram material (§6.5) | No components, no renderers. |
| M18 | Icon language (§19) | Not implemented (components take raw `Color` accents; no icon component exists). |
| M19 | Floating-element drift (§8.4) | `HudRenderer.AnchoredReadout` draws a static anchor line; no drift animation. |
| M20 | Performance tiers Full/Reduced/Minimal/Floor (§18 §8, §08 §11) | Not implemented; no frame-budget hook, no tier parameter. |

---

## Architectural Problems

1. **`ReactorSpec` is aspirational.** Fields `pulseMode`, `researchScanMs`, `ringAlignment`, `hasFlicker`, `isBuildingFamily`, `particleDirection`, and `ringCount` are computed, unit-tested, and then ignored by both renderers, which consume only `breathingMs`, `breathingAmplitude`, `ringSpeedFactor`, and `particleCount`. The model and the render are two different systems. (`ReactorEngine.kt:53-66`, `ReactorRenderer.kt`, `OrbRenderer.kt`)
2. **Latent crash in the state machine.** `JvReactorStateColor.CODING` appears in no `JvColorFamily.members` set (`JvTokens.kt:140-147`), so `JvColorFamily.of(CODING)` → `NoSuchElementException`. `resolveTransition()` calls `family()` on both endpoints (`AnimationEngine.kt:65-66`). Every Coding↔anything transition throws. Tests cover amber/red/blue/offline but never Coding.
3. **Breathing model/Compose mismatch.** `MotionEngine.breathingBrightness` is a correct sine, but the Compose renderers implement breathing with a cubic-bezier tween `1±amp` and apply it to particle size instead of brightness — a different envelope, different application, and never to the core.
4. **Event pulse is not a single shot.** `MotionEngine.rememberEventPulseBrightness` uses `rememberInfiniteTransition(RepeatMode.Restart)`, so it loops forever (a continuous 650ms pulse), its `triggerKey` only appears in the animation `label`, and it converts the tween into a triangle rather than the 150ms/500ms sharp-ease-out envelope. It is also called by nothing.
5. **"No other motion sources" is false.** `MotionEngine.kt:20` claims all brightness is produced by the engine, yet `ReactorRenderer` and `OrbRenderer` create their own `rememberInfiniteTransition` instances (`noisePhase`, `flickerPhase`) outside the engine.
6. **Transition matrix exists but is unwired.** The one genuinely correct, well-tested component (`AnimationEngine`) has no consumer; the demo "cycles all 12 states" by direct color swap, so the Design System's central transition law cannot be observed or verified at runtime.
7. **Theme pollution.** `ThemeEngine.kt:62` maps Material3 `bodyMedium` to **JetBrains Mono**, so any default-styled Material component renders body text in monospace — violating "monospace never for conversational/marketing text" (§03 §9).
8. **Hardcoded ring geometry.** Ring loops (`0..1` twice) ignore `spec.ringCount`; ring count, tilt, and parallax are not parameterized, blocking the device-context scaling the Bible requires (§13).
9. **Duplicated wobble-ring path builder** in both `ReactorRenderer.kt` and `OrbRenderer.kt` (near-identical 40-line function) — a maintenance hazard and a sign the renderers diverged.

---

## Performance Risks

- **Per-frame path rebuilding.** Each ring builds a 129-point `Path` from `sin/cos` calls every frame (4 rings in ReactorRenderer) — ~2,000+ trig calls/frame before particles. Plus 7-layer stacked glow, 10-layer bloom, volumetric haze, and up to 180 particles. All driven by infinite transitions → full-canvas redraw at 60fps.
- **Motion in "no motion" states.** `noisePhase` (9s loop) and `flickerPhase` (2.6s loop) infinite transitions run for **every** state, including Offline and Sleeping where the spec requires no motion; Offline's breathing tween also loops at 10Hz with amplitude 0. Wasted composition/GPU on the states meant to be cheapest.
- **No frame budget / tiering.** §18 §8's Reduced/Minimal tiers are absent; particle counts (up to 180) are never capped by device capability.
- **Per-frame allocations.** 180 `Offset` allocations/frame in the particle loops; GC pressure on low-end devices.
- **No renderer tests.** All 36 VF tests target pure kernels; the expensive Canvas paths are unmeasured and untested.

---

## Android Risks

- **minSdk 26** and the **stacked-stroke glow** (avoiding `Modifier.blur`, a silent no-op below API 31) are correct, proven choices. Good.
- **Sub-pixel strokes.** Machining rings use `Stroke(0.5f)` and ring strokes 1.2–1.8f in raw px; these can vanish or alias inconsistently across densities (0.5px strokes commonly render as nothing on mdpi and as dim hairline artifacts on high-density screens).
- **`JvCard` double border** when selected: `Modifier.border(...)` then `.then(Modifier.border(1.dp, accent))` — two stacked borders render as a 2dp frame.
- **`JvInput` is near-opaque.** Background `DeepSpace.copy(alpha = 0.9f)` (§JvInput.kt:36) contradicts the Glass 70–85% transparency material and the 0.25 opacity token.
- **`TextPrimary = #F4FBFF`** (reserved ignition white) used as body/heading text — Bible §5.6 explicitly forbids it anywhere outside the ignition point.
- **Wordmark line-height bug** (`56.em` ≈ 2688sp) will blow up vertical layout on any real device/preview where `TypographyEngine.DisplayXl` is used.
- **Theme's mono `bodyMedium`** silently changes default Material text across the app.

---

## Battery Risks

- **Always-on infinite transitions:** every orb/renderer instance runs 3 infinite animations continuously, including on a persistent home-surface orb. Never paused, never tiered.
- **Full redraws in Offline/Sleeping** where the design spec says the system should be at near-zero motion — directly against the "Invisible Until Needed" energy discipline (§1.5) and the Bible's explicit Offline "no motion at all" (§9.13).
- **No FPS capping, no Doze/visibility-aware pausing, no frame-budget hook** — the VF has no power story at all, which matters more than usual because the whole product is an always-on ambient assistant surface.

---

## Maintainability Issues

- **Dead code:** `ParticleEngine.position()`, `MotionEngine.rememberEventPulseBrightness`, `TypographyEngine.Scale` map, `AnimationEngine.resolveTransition`/`resolvedColor`/`transitionFrameCount` (no consumers), `JvTokensAccess`, `DurVoiceResponse`, `RingCountInner`/`RingCountOuter` (unused by renderer loops).
- **Spec/model/render drift:** docstrings assert behaviors the code does not perform ("every visual element reads from the spec", "there are no other motion sources", "12 states" vs "13 states" claims, OrbRenderer described as "phone-primary" while drawing the watch floor).
- **Assertion-based color tests** (`c.hashCode() and 0xFFFFFFFF`) are fragile and do not actually verify rendering.
- **`JvColorFamily` membership is not exhaustively tested** — the Coding gap is precisely what the family test suite should have caught.

---

## Suggested Fixes

1. **Consume the full `ReactorSpec` in the renderers.** Apply `pulseMode` (render 1Hz/2Hz sharp pulses by modulating core/glow/ignition alpha), accept an audio-amplitude parameter for `AUDIO`, apply the breathing multiplier to **core/glow brightness** (not just particle size), render the Research scan-ring, Planning alignment beat, and facet flicker; route particle positions through `ParticleEngine.position()` so the direction grammar actually renders.
2. **Register `CODING` in the `NEAR_WHITE_BUILDING` family** (`JvTokens.kt:144`); add an exhaustive test iterating all 12×12 transitions so no family membership can silently regress.
3. **Fix typography line-heights** to `sp` (`56.sp`, `20.sp`, `16.sp`…), matching `ThemeEngine`; add a unit test asserting line-height ≠ scaled `em`.
4. **Implement the Offline ember:** suppress glow/bloom/volumetric haze, draw a single dim (~15% brightness Steel) ember point. Extend the spec so the state is visually inert per §9.13.
5. **Parameterize device context:** `deviceContext` (watch/phone/tablet/desktop) → ring count and particle budgets from the §13 tables; use `spec.ringCount` instead of hardcoded loops.
6. **Wire `AnimationEngine` into the renderers** via an animated `stateColor` (crossfade direct or through-neutral) so state changes obey §12.3; drive it from the demo too.
7. **Build the waveform component** (attack 40ms/release 180ms, 200ms glow-trail, listening-teal vs speaking-building color) and the audio engine (hum, UI tones, startup/shutdown sync) to close §10/§11.
8. **Add a `motionTier` parameter** (Full/Reduced/Minimal/Floor); stop the `noisePhase`/`flickerPhase` transitions in Offline/Sleeping; cap particles on frame budget.
9. **Fix the reserved-white violation:** either add a distinct non-white `--text-primary` token or explicitly document the deviation in the Bible amendment — but text must not be `#F4FBFF`.
10. **Make the event pulse a true single-shot** keyed by `triggerKey` (e.g., an `Animatable` reset in `LaunchedEffect(triggerKey)`), remove the infinite loop.
11. **Clean up dead code** (`Scale`, `JvTokensAccess`, unused tokens) or wire it; deduplicate the wobble-ring draw function.
12. **Fix component bugs:** `JvCard` double border, `JvInput` glass opacity (0.25), `ThemeEngine` mono `bodyMedium`.
13. **Accessibility:** address Sleep-violet (`#7C4DFF`) at **≈4.2:1** against Void Black — below the Bible's own 4.5:1 floor (§5.7); verify state-accent text across Glass panels at worst-case backing.
14. **Add renderer/component tests** (Compose screenshot/UI tests) so rendering regressions are caught; at minimum test that Offline emits no glow and Warning/Critical modulate brightness.

---

## Verdict

**FAIL**

The tokens and pure engines honor the frozen Bible and Design System, but the deliverable's core promise — a "Design-System-exact" visual layer — is not met. The signature state language of the reactor (the entire point of the system) is largely unrendered: alert pulses, audio-driven listening, core breathing, the direction grammar, facet flicker, research/planning/coding tells, and the Offline ember are absent, and the crossfade transition matrix is never executed. Combined with a latent crash on Coding transitions, a rendering-breaking typography bug, a reserved-color violation, zero performance/battery discipline, and an entire missing voice/audio channel, the foundation does not yet match the bible.

The path to PASS is concrete and bounded (fixes 1–8 above are the critical mass); the renderers, not the engines, are what must be brought to spec.
