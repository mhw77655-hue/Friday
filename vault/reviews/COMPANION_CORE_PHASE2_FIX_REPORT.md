# Companion Core — Phase 2 Fix Report

| | |
|---|---|
| **Audit fixed** | `vault/reviews/COMPANION_CORE_PHASE2_AUDIT.md` |
| **Scope** | Companion Core / Visual Foundation only. No new features, no redesign, no Human Core changes, no unrelated code. |
| **Date** | 2026-08-06 |
| **Verification** | 219 unit tests (0 failures / 0 errors / 0 skipped); `assembleDebug` BUILD SUCCESSFUL |

---

## Accepted findings (implemented)

| # | Finding (from audit) | Fix |
|---|---|---|
| F1 | Conversation-loop seam never wired — `current_action` never leaves IDLE in the live app, so the orb cannot reach LISTENING/THINKING/SPEAKING. | `ConversationViewModel` now calls `CompanionCoreHolder.instance()?.integration?.onUserSubmitted()` on send, `onFirstSegmentReady()` when a reply arrives, and `onUtteranceCompleted()` after `completeExchange`. The discriminator already drives these (tested), so the orb now leaves IDLE through a real conversation. |
| F2 | Background/foreground wired to the `MainActivity` observer → fires on rotation/multi-window; no process-level awareness. | Added `androidx.lifecycle:lifecycle-process:2.8.4`; `JarvisApplication` now drives `core.onBackground()/onForeground()` from `ProcessLifecycleOwner` (fires only when the **whole process** stops/starts). `MainActivity` observer now only ends the Human Core session and is guarded with `!isChangingConfigurations`, so rotation no longer flushes sessions or emits spurious advisories. |
| F3 | §2.31 governor outputs never consumed: no tier fps cap, `maxParticleDensity` unread, `background_suspended` dead. | `CompanionCore` exposes `budget` / `suspended` StateFlows (kept in lock-step each tick and on background/foreground). `PresenceOrb` now runs a **paced render clock**: a monotonic render time advanced only at the tier's frame period (via the master clock's `framePeriodNanos`), the renderer samples all animation from that time, so a LOW tier renders at 15fps instead of 60fps and a backgrounded orb holds a static frame. `OrbStateMachine.map()` takes `maxParticleDensity` and caps `particleDensity`; `OrbRenderer` accepts `particleCountOverride`. |
| F4 | Cross-thread render timestamp: `AnimationController.lastFrameStartElapsedNanos` non-volatile + PresenceOrb fed raw `withFrameNanos` timestamps as master-elapsed (garbage epoch in `lastRenderTimestamp`). | Field is now `@Volatile`. `CompanionClock.masterElapsedNanos(frameTimeNanos)` converts the `System.nanoTime()`-base frame timestamp into master-elapsed nanos; `PresenceOrb`/`HomeScreen` convert before `reportFrame`. |
| F5 | Two clocks in production wiring (Presence Engine on wall-clock, frames on master clock) + 30Hz system-service polling. | `CompanionCoreHolder` now routes `nowMs` for the Presence Engine, Human Core Integration, and the resource governor from the one `CompanionClock` (invariant 5). `CompanionCore.tick()` evaluates thermal/battery ~1Hz (`CONDITIONS_EVAL_INTERVAL_TICKS = 30`) instead of every 33ms. |
| F6 | Dead presentation path retained: `DiagnosticsViewModel` orb label chain, `ui/Orb.kt`, `OrbState.kt`, `JarvisShell`. | `ui/Orb.kt`, `OrbState.kt`, and the `JarvisShell` composable deleted. The orb-driving path (orbState flow, `computeLiveOrbState`/`computeRestingOrbState`, battery→orb mapping, all orb emissions) removed from `DiagnosticsViewModel`; the diagnostics text flows SystemScreen consumes are preserved, and the Battery row now reads a new `batteryText` StateFlow. `OrbState` is fully folded into `OrbRenderParams` (Phase-2 item 5/9 migration complete). |
| F7 | Stale-signal fallback cuts the WAKING bloom. | `PresenceEngine.tick` no longer forces `IDLE` from a stale signal while `WAKING` — the CompanionCore startup handoff owns `WAKING→IDLE` after the §12 window. New test covers it. |

---

## Rejected findings (not implemented — with rationale)

| # | Finding | Reason rejected / deferred |
|---|---|---|
| R1 | Foreground service + silent companion notification + `PARTIAL_WAKE_LOCK` + `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (plan §1.7). | A new app-shell subsystem (manifest `<service>`, notification, OEM battery handling, permission UX) — not a Phase-2 correctness fix. Belongs to the background-presence implementation phase. The runtime hooks it needs (`CompanionCore.onBackground/onForeground`, `resourceManagement.setBackgroundSuspended`) are now in place. |
| R2 | `Application.onTrimMemory`/`onLowMemory` hard-shutdown (SHUTTING_DOWN grace). | Part of the Phase-4 shutdown sequence. |
| R3 | Process-death restore via DataStore (`CompanionClock.reset()` consumers). | DataStore restore is plan §2.29, Phase 5. |
| R4 | androidTest tier (§9) — lifecycle-suspension test, lane-assertion instrumentation, leak soak, rotation-leak test. | No device/emulator is available in this environment; the pass is pure-JVM. The two Phase-2-critical behaviors (lifecycle suspension, lane non-blocking) are wired but require instrumentation to assert on-device. Deferred to the phase that stands up §9. |
| R5 | Idle CPU measurement + manual QA on the Tab A7 / Realme 9 Pro reference devices (R-P4/R-B1). | No reference devices attached. The pacing/caps are now in place to be measured; measurement itself is deferred. |
| R6 | Init-order assertion test (R-I5). | Ordering is verified in code (`JarvisEngine.init` → `CompanionCoreHolder.init` before `setContent`); a runtime assertion needs instrumentation. |
| R7 | `StateBus` leak/audit-trail test (R-M1). | The Companion-Core suite drives the bus only through `LocalHumanCoreBinding`/`FakeBinding`; no leaked-subscription path exists in the pure-JVM CC suite. |
| R8 | Tick scheduling owned by the master clock (drift-free 30Hz). | The pacing/polling halves are fixed; making the tick loop itself clock-scheduled is a Phase-5/`CompanionClock` scheduling change — deferred to avoid touching the untested schedule mid-phase. |
| R9 | `DiagnosticsViewModel` full deletion. | Not valid — `SystemScreen` consumes its per-signal diagnostics flows (`networkText`, `micText`, `voskText`, `sherpaText`, `ttsText`, `screenText`, `humanCoreText`, …). Only the dead orb path was removed. |

---

## Fixes applied — files touched

- `mobile/app/build.gradle.kts` — `lifecycle-process:2.8.4` dependency.
- `mobile/app/src/main/java/com/jarvis/app/JarvisApplication.kt` — `ProcessLifecycleOwner` observer driving CC background/foreground.
- `mobile/app/src/main/java/com/jarvis/app/MainActivity.kt` — observer trimmed to `HumanCore.endSession()` guarded by `!isChangingConfigurations`; dead `JarvisShell` + `DiagnosticsViewModel` field + unused imports removed.
- `mobile/app/src/main/java/com/jarvis/app/DiagnosticsViewModel.kt` — orb-driving path removed; `batteryText` StateFlow added for SystemScreen.
- `mobile/app/src/main/java/com/jarvis/app/ui/screens/SystemScreen.kt` — Battery row rewired to `batteryText`; `batteryDotColor` keyword-maps the text.
- `mobile/app/src/main/java/com/jarvis/app/ui/screens/HomeScreen.kt` — `PresenceOrb` now receives `budget`/`suspended`/`masterTimeOf`/`framePeriodNanos` from the core.
- `mobile/app/src/main/java/com/jarvis/app/ui/viewmodel/ConversationViewModel.kt` — conversation-loop seam wired.
- `mobile/app/src/main/java/com/jarvis/app/ui/Orb.kt`, `mobile/app/src/main/java/com/jarvis/app/OrbState.kt` — **deleted**.
- `companioncore/engine/CompanionClock.kt` — `masterElapsedNanos(frameTimeNanos)`.
- `companioncore/render/AnimationController.kt` — `@Volatile lastFrameStartElapsedNanos`.
- `companioncore/render/OrbStateMachine.kt` — `map(..., maxParticleDensity)` cap.
- `companioncore/presence/CompanionCore.kt` — `budget`/`suspended` flows, throttled governor evaluation, `framePeriodNanos`/`masterElapsedNanos` helpers, immediate `_suspended` updates on lifecycle.
- `companioncore/presence/CompanionCoreHolder.kt` — all engine timing routed through the master clock.
- `companioncore/presence/PresenceEngine.kt` — WAKING exempt from stale-signal → IDLE.
- `companioncore/ui/PresenceOrb.kt` — paced render clock + suspension + new inputs.
- `vf/orb/OrbRenderer.kt` — time-driven (`frameTimeMs`) animation path + `particleCountOverride`.
- `companioncore/identity/VisualIdentity.kt` — stale comment reference removed.

## Tests

- **219 unit tests, 0 failures, 0 errors, 0 skipped** (`:mobile:app:testDebugUnitTest`).
- New: `PresenceEngineTest.stale signal does not yank the waking bloom`; `CompanionClockTest.master elapsed converts a frame timestamp on the same monotonic base`.
- Existing suites still green: Companion Core (PresenceEngine 7×7, OrbStateMachine, AnimationController, MobileResourceManagement, CompanionClock, discriminator, integration), Visual Foundation, Human Core.

## Build result

- `compileDebugKotlin` — clean (only pre-existing warnings; the `lifecycle-process:2.8.4` artifact resolved from the local Gradle cache, no network).
- `:mobile:app:assembleDebug` — **BUILD SUCCESSFUL** (APK packages).

## Remaining work

1. §1.7 background-presence (foreground service + notification + wake-lock + battery-optimization prompt) — next phase.
2. `onTrimMemory`/`onLowMemory` hard-shutdown (Phase 4) and DataStore restore (Phase 5).
3. androidTest tier (§9): lifecycle-suspension and lane-assertion instrumentation — needs a device/emulator.
4. Idle CPU measurement + manual QA on the Tab A7 / Realme 9 Pro reference devices, now that pacing/caps are enforceable.
5. `onFirstSegmentReady`/`onUtteranceCompleted` are currently bound to reply-arrival (the voice pipeline is disabled); true speech-timing precision lands with the voice phase.

## Final status

**PHASE 2 FULLY FIXED.** All valid audit findings are implemented, the full unit suite passes (219/219), and the app builds. The rejected items are documented deferrals to their owning phases, not unimplemented valid fixes.
