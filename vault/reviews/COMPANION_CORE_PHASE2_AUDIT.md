# Companion Core — Phase 2 Independent Audit

| | |
|---|---|
| **Audit scope** | Phase 2 only — Presence Engine + renderer foundation (plan §5 Phase 2: §2.1, §2.5, §2.6, §2.21, §2.31 core) |
| **Audited against** | `vault/architecture/COMPANION_CORE_SPEC.md` (frozen contract), `vault/architecture/COMPANION_CORE_IMPLEMENTATION_PLAN.md` (rev 2), `vault/agent-logs/COMPANION_CORE_PHASE1_REPORT.md`, actual repo working tree |
| **Date** | 2026-08-06 |
| **Method** | Full source read of all Phase-2 files, all new `vf/` files, app-shell wiring diffs, and the Phase-2 test suite; full unit-test run verified (217 tests, 0 failures, 0 errors, 0 skipped) |
| **File audited** | `vault/agent-logs/COMPANION_CORE_PHASE2_REPORT.md` — **does not exist.** No Phase-2 report has been written. The Phase-1 report explicitly states "Stopped after Phase 1: No Phase-2 work begun," yet the working tree contains a large, uncommitted Phase-2 implementation. This audit therefore audits the **implementation as present in the tree**, not against a self-reported Phase-2 summary. |

---

## Executive Summary

The Phase-2 implementation present in the working tree is a **strong, well-tested pure-JVM core** with an **incomplete Android integration half**.

**What is genuinely good:**
- `PresenceEngine` (§2.1) is a clean 7-state machine with a `TransitionRequest` FIFO, a correct priority table, stale-signal→IDLE fallback, `deriveFromAction` dwell hysteresis, and the required **7×7 exhaustive transition test** plus the single-mode invariant test. It is the sole producer of `CompanionState` and honors the one-mode invariant.
- `OrbStateMachine` (§2.6) is a pure `(mode, emotion, alert) → OrbRenderParams + RenderIntent` mapping, correctly delegated to the frozen VF `ReactorEngine.spec` tokens, with the `OrbClip` set aligned 1:1 with `VisualState` (incl. `NOTIFICATION_GLOW`, `SLEEP_DIM`) and unit-tested independent of any renderer.
- `AnimationController` (§2.21) arbitration + frame-budget watchdog is pure and unit-testable; the orb's recomposition isolation (R-P2: `StateFlow` collected inside `PresenceOrb`, `derivedStateOf`-gated renderer inputs) is correctly implemented — 30Hz tick emissions do not recompose the screen.
- `MobileResourceManagement` (§2.31) core — tier ladder, anti-flap 10s cooldown, thermal-unknown fallback, `background_suspended` — is present and deterministically unit-tested.
- Recomposition isolation and the "sole producer" discipline are respected end-to-end.
- Verification is real: **217 unit tests, 0 failures** (123 Companion Core, 50 VF, 44 Human Core), Debug build compiles.

**What is wrong or missing** (detailed below): the resource governor's **outputs are never consumed** (no fps/particle caps, no background-render suspension), the lifecycle wiring uses the **activity observer instead of `ProcessLifecycleOwner`** (plan §4.3 R-A3 explicitly forbids this as the sole driver), the **conversation-loop seam is unwired** (`current_action` never leaves IDLE in the live app, so the orb cannot reach LISTENING/THINKING/SPEAKING), the **androidTest tier (plan §9) does not exist at all**, **dead presentation code was retained** (`DiagnosticsViewModel`, `ui.Orb`, `OrbState`, `JarvisShell`), and there is **no device measurement or manual QA**. The `vf/` "reactor" presence shell and the Companion Core are coupled only through `PresenceOrb` → `OrbRenderer` (correct), but nothing guards the shell against the gaps above.

**Bottom line:** the plan's Phase 2 *contract half* (pure mapping + state machine + tests) is essentially complete; the Phase 2 *integration half* (plan items 4/6 caps-consumed, item 5 migration completion, item 7/8 measurement+QA, §1.7/§4.3 Android lifecycle, §9 androidTest) is largely unmet. Verdict: **PASS WITH ISSUES**, with several issues release-blocking for Phase 3.

---

## Missing Requirements

Against plan §5 Phase 2 items 1–9, §1.6/§1.7, §4.3, and spec §2.31/§2.30/§2.9:

1. **Tier fps capping is not implemented** (plan Phase-2 item 4: "frame-rate capped to tier (16.6/33.3ms)"). `CompanionClock.nextFrameDelayNanos()` exists but is **never called by any render loop**. `OrbRenderer`/`PresenceOrb` run at full vsync (60fps) at every tier. The plan's own `DerivedStateFlow`… the watchdog only *measures*; nothing *caps*.
2. **Global caps are not consumed by Orb/Animation** (plan Phase-2 item 6). `ResourceBudget.targetFps` is read only inside `CompanionCore.reportFrame` to compute the watchdog's expected period; `maxParticleDensity` is read **nowhere** — `OrbStateMachine.map()` copies `spec.particleCount` straight from `ReactorEngine.spec` (vf) without any cap. `nextFrameDelayNanos` (R-P5/M-16 pacing) is dead. §2.31 is a measurement-only governor in Phase 2.
3. **`background_suspended` is dead state** (plan Phase-2 item 6, §1.7 "renders suspend (§2.31)"). `MobileResourceManagement.setBackgroundSuspended(true)` is called on background, but **no renderer or loop reads `backgroundSuspended`**. Suspension "works" only incidentally, because the choreographer stops delivering frames to a non-visible window — unverified across devices and absent in split-screen/PIP.
4. **`ProcessLifecycleOwner` suspension is not used** (plan §4.3, audit R-A3 — release-blocking): "drive suspension from `ProcessLifecycleOwner` — not the view-scoped `MainActivity` observer." Implementation uses the `MainActivity` `LifecycleEventObserver` only. There is no `androidx.lifecycle:lifecycle-process` dependency.
5. **androidTest tier (plan §9, Phase-0 item 7) is entirely absent** — no `androidTest` source set exists. Missing: lifecycle-suspension test, thermal tier-stepping test, lane-assertion instrumentation test (§1.6), leak soak, rotation-leak test. The lane assertion ("Human Core C-1 blocking-on-Main regression must not recur") has no instrumentation home.
6. **Foreground service + silent companion notification + `PARTIAL_WAKE_LOCK` (plan §1.7 — release-blocking, "now mandatory")** — absent. No `Service` in the manifest beyond the notification listener. On aggressive-OEM devices (Realme 9 Pro) the whole presence shell is simply killable; the "24/7 companion" premise is unmet.
7. **Doze / App-Standby exemption prompt** (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) — absent.
8. **Hard-shutdown path on `Application.onTrimMemory(TRIM_MEMORY_BACKGROUND)` / `onLowMemory()` (plan §1.7, audit M-5/R-A4)** — `JarvisApplication` has **no overrides at all**. Memory-pressure SHUTTING_DOWN is unhandled.
9. **Process-death restore (plan §1.7)** — `CompanionClock.reset()` exists but nothing persists/restores `last_render_timestamp` + `presence_mode` (no DataStore/PresentationStore; §2.29 is Phase 5 but §1.7 mandated the restore).
10. **Idle CPU budget measurement on reference devices (plan Phase-2 item 7, R-P4/R-B1)** — no measurements anywhere in the repo. The 30Hz tick + 60fps idle render is unverified against the ≤5% budget.
11. **Manual QA on reference devices (plan Phase-2 item 8)** — none recorded.
12. **Conversation-loop integration seam (plan §4.3)** — `HumanCoreIntegration.onUserSubmitted()`, `onFirstSegmentReady()`, `onUtteranceCompleted()` exist but are **never called from production code**. `ConversationViewModel` does not feed them. Consequence: `current_action` stays `IDLE` in the live app, so `deriveFromAction` can never raise LISTENING/THINKING, and `attentionTarget` stays `NONE`. The orb cannot respond to a conversation.
13. **Init-order assertion test (plan §4.3 R-I5)** — `HumanCore.init` → `CompanionCoreHolder.init` order is correct in `JarvisEngine` (verified), but the requested startup ordering assertion/check is not present.
14. **Migration incomplete (plan Phase-2 item 9)** — `OrbState.kt` was *not* folded into `OrbRenderParams`; the `DiagnosticsViewModel` label chain was *not* deleted (it is retained dead code, see Dead Code).
15. **`StateBus` test isolation (plan §9 R-M1)** — the Companion-Core suite uses `FakeBinding`/fixtures; no CC-suite audit-trail reset or leaked-subscription cross-suite test exists.

---

## Architectural Problems

1. **Two clocks in production wiring (spec Design Principle 4 / Hard Invariant 5, §1.5).** `CompanionCoreHolder` builds the `PresenceEngine` with the default `nowMs = { System.currentTimeMillis() }` and `CompanionCore` likewise, while `CompanionClock` (monotonic) drives only frame timing. `modeEnteredAt`, the wake handoff, and staleness run on wall-clock; frame epochs run on the master monotonic clock. They are never reconciled. Unit tests side-step this with `ManualEpochClock`. The single-clock mandate ("no subsystem keeps a second free-running clock") is violated in the production object graph.
2. **Tick scheduling drifts.** The tick loop is `while (isActive) { tick(); delay(33) }` (`CompanionCore.start`). `delay` is free-running and does not compensate for `tick()` execution time, is not aligned to `CompanionClock`, and the master clock does not own the schedule (plan §1.5: CompanionClock "owns tick scheduling"). Cadence will drift below the spec's fixed 30Hz.
3. **Resource governor is disconnected from the render path (structural).** The §2.31 budget is computed each tick and then *dropped*. Nothing in the `vf/` renderers accepts a budget. This is not a bug in `MobileResourceManagement`; it is an unbuilt contract between the engine lane and the render lane — the exact seam Phase-2 items 4/6 were meant to close.
4. **A third concurrent reader of the frozen Human Core.** `LocalHumanCoreBinding.read()` calls `HumanCore.snapshot()` up to 3× per tick from the `CompanionTick` lane, concurrent with UI-thread `beginExchange/express/completeExchange` and the integration executor's writes. The HC is concurrency-hardened (volatile snapshot swaps, `ConcurrentHashMap` in `PersonalityStore`, synchronized `FileStorage`), so this is *probably* safe — but it is a **new cross-thread access pattern to a FROZEN subsystem**, with no instrumentation coverage and no lane-assertion test. It deserves an explicit risk note, not silence. The 3×-per-tick `snapshot()` is mitigated by the HC's 2s TTL cache (two of three calls are cache hits), so it is not a hot path.
5. **Startup budget violation on the tick lane (latent).** The first `readStatus()` from the lane touches `object JarvisBrainBridge`, whose initializer builds an `OkHttpClient` — a one-time multi-ms allocation on the <1ms tick lane. Harmless after warmup; violates the lane rule once at boot.
6. **`JarvisShell`/`DiagnosticsViewModel` retained as a parallel orb driver (trap).** The dead path still imports `ui.Orb` + `OrbState`; any future wiring of `DiagnosticsViewModel` would silently create a second orb authority, violating the sole-producer discipline.
7. **`VisualIdentity` is thinner than §2.5 intends** (a color map only — no form/iconography, no per-consumer role plumbing beyond `SemanticRole`). Acceptable as a Phase-2 *foundation*; the §2.5 lifecycle/asset-integrity requirements (plan §9 M-17) are unmet.

---

## Android Risks

1. **Lifecycle suspension is wired to the wrong lifecycle (high).** `MainActivity` `LifecycleEventObserver` fires `ON_STOP` on **every rotation and multi-window hide**. Each `ON_STOP` → `CompanionCore.onBackground()` → spurious `DEVICE_BACKGROUNDED` advisory, render suspend, and — in the same observer — `HumanCore.endSession()` (which posts `onSessionEnd` + `registry.checkpoint()`). Rotation therefore triggers session-flush work and presence advisories that are semantically wrong. Plan §4.3 (R-A3) named exactly this risk and mandated `ProcessLifecycleOwner`.
2. **No process-level backgrounding awareness.** If the process backgrounds without the activity's `ON_STOP` (translucent overlay, other routes), the tick keeps running at 30Hz. The 0.1Hz background tick relies entirely on one activity's lifecycle.
3. **No foreground service / wake-lock** — on Realme 9 Pro–class OEM policy the process is killed regardless of what the code tries; the §1.7 design (which the plan marks mandatory) is simply not present.
4. **`onTrimMemory`/`onLowMemory` are unhandled** — no SHUTTING_DOWN grace on memory pressure; the "presence shell" dies silently.
5. **Cross-thread field without `@Volatile`:** `AnimationController.lastFrameStartElapsedNanos` is written on the render lane (`recordFrame`) and read on the engine lane (`lastRenderEpochMs` → `CompanionState.lastRenderTimestamp`) but is **not** `@Volatile` (only `consecutiveFrameMisses` is). Torn/stale reads possible on some devices; the render timestamp in `CompanionState` can lag or read null. Cheap fix; real race.
6. **`CompanionCoreHolder` lane thread is never torn down** — app-lifetime by design, but the `@Synchronized init` + `@Volatile core` pattern has no lifecycle for testing or shutdown; acceptable, note it.

---

## Performance Risks

1. **30Hz system-service polling (high).** `CompanionCore.tick()` calls `conditionsProvider()` every 33ms in foreground; `androidConditions` performs `PowerManager.getThermalHeadroom(0)` + two `BatteryManager.getIntProperty` calls. That is ~30 IPC-ish system-service round-trips per second, forever, even when nothing changed. Should be ~1Hz, or event-driven (battery `BroadcastReceiver` + a thermal callback). This is both a battery and a tick-budget risk (the lane's <1ms budget is at the mercy of binder latency).
2. **No idle-frame-rate cap.** The orb redraws at 60fps during idle foreground (breathing + particle flicker are `rememberInfiniteTransition`), exactly what §2.31/Phase-2 item 4 was to cap. Idle CPU budget on reference devices is unmeasured and almost certainly above the plan's target.
3. **Watchdog loop runs at vsync even when the params never change** — `PresenceOrb`'s `withFrameNanos` loop reports ~60 frame records/s into `AnimationController.recordFrame` even in a static idle state. Cheap but continuous; couples render cadence to the engine lane at full rate.
4. **Per-tick allocation churn** — `OrbRenderParams` + `RenderIntent` objects allocated 30×/s (StateFlow dedups equal values, so recomposition cost is nil). Trivial; not a concern.

---

## Memory Risks

1. **No leak-soak / rotation-leak instrumentation** (plan §9) — unverified by construction. The `LaunchedEffect` loops in `PresenceOrb` are composition-scoped and cancel on dispose (correct), and `PresenceOrb` itself is lifecycle-collected, so the *pattern* is sound; it is simply untested.
2. **`newSingleThreadContext("CompanionTick")` is never closed** — one leaked-by-design thread for app lifetime; acceptable, but it means the lane survives activity churn (intended) with no shutdown hook.
3. **`RemoteHumanCoreBinding`'s background HTTP executor** is constructed-but-unused in the local wiring; harmless today, must be shut down if ever enabled.
4. **Static graph retention:** `CompanionCoreHolder` holds the whole CC graph + lane scope statically for process lifetime — by design (Application-scoped), but there is no `onTrimMemory`-driven release.

---

## Battery Risks

1. **60fps idle rendering with no LOW-tier cap** — the dominant battery risk on the Tab A7 / Realme 9 Pro class devices. The plan's response (tier fps capping + measured idle CPU ≤5%) is unimplemented.
2. **30Hz thermal/battery polling** (above) — needless foreground drain.
3. **Background posture is unguarded:** no foreground service, no wake-lock, no doze exemption. The 0.1Hz background tick is correct *when it runs*, but the process has no protection, and `background_suspended` doesn't actually suspend anything. The §1.7 background-presence design (its stated purpose is battery + survivability) is effectively absent.
4. Spurious **rotation-triggered** background/foreground cycling can leave the renderer in a wrong tier or re-submit state more often than needed.

---

## Integration Risks (reactor / presence shell)

1. **The orb cannot leave IDLE in the live app (highest functional risk).** Because no production caller drives the discriminator (`onUserSubmitted`/`onFirstSegmentReady`/`onUtteranceCompleted` are never invoked), `current_action` stays `IDLE`; `PresenceEngine.deriveFromAction` never raises LISTENING/THINKING; `attentionTarget` stays `NONE`. After the wake handoff, the presence shell renders a breathing IDLE orb that ignores every conversation. The plan's §2.1 "infer … boundaries" requirement is implemented but dead in the wiring.
2. **Wake bloom is fragile to staleness.** `PresenceEngine.tick` forces IDLE on any stale signal while WAKING. If HC's snapshot is momentarily unavailable at boot (or the app backgrounds mid-bloom), the WAKING→IDLE snap precedes the `wake-complete` window. Init order makes this unlikely but the fallback has priority over the bloom.
3. **Cold-start frame flicker:** currentMode starts `ASLEEP`; the first tick applies WAKING, so the first composed frame(s) can show `SLEEP_DIM` before `WAKE_BLOOM` — cosmetic.
4. **Two renderers, one shell:** the presence shell uses `vf/orb/OrbRenderer`; `vf/reactor/ReactorRenderer` (the full reactor) is only used by the sandbox demo. Both read `ReactorEngine` so they cannot diverge on *specs*, but the shell vs. reactor render paths are duplicated and only one is exercised by the shell. If future phases swap the shell to the full reactor, the §2.21 watchdog and §2.6 params contract must survive that swap — unplanned today.
5. **Dead second driver trap** (above): `DiagnosticsViewModel`/`ui.Orb`/`OrbState` could be re-wired by mistake and fight `PresenceOrb`.
6. **`CompanionCore` is null-safe but silently inert:** `HomeScreen` degrades to a static idle orb if the core is absent — robust, but it also *masks* wiring failures (no log, no state).

---

## Suggested Fixes

Priority-ordered (P0 = release-blocking per plan §1.7/§4.3/Phase-2 items):

- **P0 — Wire the conversation seam.** Call `HumanCoreIntegration.onUserSubmitted()` from `ConversationViewModel` on send and `onUtteranceCompleted()`/`onFirstSegmentReady()` at the existing exchange boundaries; add a test that the discriminator transitions under a live conversation. Without this, the orb is functionally static.
- **P0 — Move background/foreground to `ProcessLifecycleOwner`** (add `androidx.lifecycle:lifecycle-process`); keep the `MainActivity` observer only for `HumanCore.endSession`, or guard it against config changes (`ON_DESTROY`-aware / compare instances) so rotation does not end sessions or emit spurious advisories.
- **P0 — Make `MobileResourceManagement`'s outputs real.** Have `OrbRenderer`/`PresenceOrb` consume `budget.targetFps` (via `nextFrameDelayNanos`) and cap particle density from `budget.maxParticleDensity`; gate the watchdog on `backgroundSuspended`. Close plan Phase-2 items 4/6.
- **P1 — Throttle device-condition polling** to ~1Hz (or event-driven), and mark `AnimationController.lastFrameStartElapsedNanos` `@Volatile`.
- **P1 — Stand up the androidTest tier** with the two Phase-2-critical tests: lifecycle suspension (foreground/background → `background_suspended` behavior) and a lane-assertion test proving no blocking/I-O on `CompanionTick`.
- **P1 — Delete the dead presentation path** (`DiagnosticsViewModel`, `ui.Orb`, `OrbState`, `JarvisShell`) or move it behind a clearly-disabled gate; complete Phase-2 item 5/9 migration.
- **P1 — Route all timing through `CompanionClock`** (PresenceEngine `nowMs`, wake handoff, staleness) and schedule the tick from the master clock with drift compensation (invariant 5).
- **P2 — Implement §1.7 background-presence**: foreground service + silent notification + battery-optimization prompt; `onTrimMemory`/`onLowMemory` hard-shutdown path in `JarvisApplication`.
- **P2 — Idle-frame cap + CPU measurement** on both reference devices (R-P4/R-B1), stepping to LOW (15fps) if idle CPU exceeds budget.
- **P2 — Wake-bloom guard:** treat a stale signal during WAKING as "hold bloom, don't force IDLE" until the startup window elapses.
- **P2 — Add the init-order assertion test (R-I5)** and a `StateBus` leak test (R-M1).

---

## Verdict

The Phase-2 *core* — PresenceEngine state machine, OrbStateMachine mapping, AnimationController arbitration/watchdog, MobileResourceManagement logic, the §2.30 integration choke point, recomposition isolation — is well-engineered and genuinely well-tested (217 unit tests green, build green). The Phase-2 *integration* — caps consumed by the renderer, background suspension actually enforced, `ProcessLifecycleOwner`-driven lifecycle, conversation-seam wiring, androidTest tier, dead-code migration, device measurement/QA — is substantially missing. The most visible consequence today is that **the presence shell's orb will not react to any conversation**, and the most damaging long-term one is that **§2.31 governance and §1.7 background-presence are unimplemented on the devices the plan targets**. None of this invalidates the core; all of it is addressable wiring and measurement. Because the missing items include plan-mandated, release-blocking seams, the phase should not be declared COMPLETE until the P0/P1 items land and the (as-yet-nonexistent) Phase-2 report is written.

**PASS WITH ISSUES**
