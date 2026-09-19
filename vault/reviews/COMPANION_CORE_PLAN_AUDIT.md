# Companion Core — Implementation Plan Adversarial Architecture Audit

| | |
|---|---|
| **Audit subject** | `vault/architecture/COMPANION_CORE_IMPLEMENTATION_PLAN.md` (303 lines, 2026-08-05) |
| **Reference contract** | `vault/architecture/COMPANION_CORE_SPEC.md` (frozen, 31 subsystems, 1022 lines) |
| **Cross-checked against** | `vault/architecture/JARVIS_HUMAN_CORE_SPEC.md` (frozen) + `vault/reviews/HUMAN_CORE_AUDIT.md` findings (C-1/C-2, m-3/m-10, `StateBus` singleton) |
| **Audit date** | 2026-08-05 |
| **Audit stance** | Independent architecture review — no source code reviewed, no files modified. Verifies spec→plan coverage, coupling, Android lifecycle, perf/memory/battery, animation & voice pipelines, Human Core boundary, maintainability, scalability, testing, dependencies. |
| **Verdict** | **PASS WITH ISSUES** — see §10 for the issues that must be closed before production. |

---

# Executive Summary

The implementation plan is a faithful, honest mapping of a 31-subsystem specification onto a real Kotlin codebase. Its three headline strengths are real: (1) it correctly identifies the central gap — the spec is written against a Python Human Core (`state.py`, 7-step loop, Turso) that does not exist here, and builds the `CompanionSignal` adapter (§2.30) first; (2) it treats the frozen Kotlin Human Core as inviolable and enumerates every spec field with no native Kotlin source (`energy`, `motivation`, `attention_focus`, `confidence`, `current_action`, `fabrication_flag`, `UserPresenceEvent` delivery) as an explicit decision point rather than fabricating state; (3) it reuses aggressively and correctly (Canvas orb renderer, Vosk/Sherpa/TTS pipeline, `JarvisEngine` init seam, the audit's own never-invoked HC hooks).

The plan will not survive production contact as written, for four structural reasons:

1. **The threading model is unspecified.** The spec's own design principle — "everything interruptible within one frame," "mode transitions on a dedicated dispatcher," "never block the render thread" — has no dispatcher/thread-ownership definition anywhere in the plan. The Human Core audit (C-1) proved this exact omission produces a blocking-on-Main bug. The Companion Core adds a 30Hz tick loop, a 60fps `withFrameNanos` render loop, TTS segment playback, and `StateFlow` collection, all with no stated thread home. This is the single most likely source of the next ANR.

2. **The "24/7 companion" premise has no background story.** The spec assumes a persistent process (1Hz tick in doze, background signal polling, wake-word listening). On Android, none of that survives modern background/doze/OEM battery-manager policy without a foreground service, and Realme 9 Pro — one of the two reference devices — is among the most aggressive OEM kill policy vendors. The plan never addresses foreground service, doze, OEM kill/restore, or the battery cost of continuous wake-word VAD. The product's core value proposition is unbuildable as specified without closing this.

3. **Device-gated acceptance criteria from the spec are relegated to manual QA with no gate.** The spec requires as testing requirements: 7×7 exhaustive mode-transition coverage, a 24h presence soak, multi-hour battery-drain ground-truth per quality tier on both reference devices, cold-start timing, earcon latency, interrupt <1-frame handoff, leak soaks. The plan's strategy is "pure JVM unit tests only, no Robolectric/androidTest/emulator," with all device behavior listed as *manual* QA. That converts acceptance criteria into suggestions and removes any regression safety net for the subsystems most likely to regress (thermal, audio, sensors, lifecycle).

4. **The signal that drives three presence modes (`current_action`) is under-determined by its stated sources.** `current_action` ∈ {IDLE, RECEIVING_INPUT, TOOL_CALL, GENERATING, DONE} must distinguish TOOL_CALL from GENERATING and IDLE from RECEIVING_INPUT. The proposed sources (`JarvisBrainBridge.status` "sending (local)"/"retrying"/"ok (local)" + viewmodel boundaries) cannot reliably separate those — yet THINKING escalation, Speaking entry, and Conversation Flow all depend on it. The plan acknowledges the gap but does not design the discriminator.

Secondary findings include: `RenderIntent` is declared in the contracts but consumed by no subsystem in the plan; Greeting (§2.19) and Notification (§2.20) are slated to read Human Core data (`secondsSinceLastContact`, `companionBehaviors()`) in a way that risks violating Hard Invariant 2's single choke point; the audio pipeline has no audio-focus or incoming-call interrupt handling despite both specs naming call interrupts; the global `StateBus` singleton (already flagged in the Human Core audit) gains a Companion Core subscription with no unsubscribe discipline and no test-isolation plan; `fabrication_flag` is proposed to derive from `GuardSoften`/`GuardVeto`, which are veto events, not `self_check()` fabrication corrections — a semantic mismatch that can misfire the honesty-reinforcing `SelfCorrecting` cue.

The dependency graph, phased ordering, build-order, and JVM-testable pure-function strategy are sound. The plan is executable and the issues below are closeable; none require a spec rewrite.

---

# Missing Requirements

Requirements in the spec with no representation, or only partial representation, in the plan.

| # | Spec requirement | Status in plan | Gap |
|---|---|---|---|
| M-1 | **Foreground persistence** — spec's entire model (1Hz doze tick §2.1, background attention §2.3, background signal polling §2.31, "24/7-running companion" §2.16) presupposes an alive process. | Absent | No foreground-service strategy, no OEM battery-manager mitigation (Realme is a reference device), no doze/work, no process-death restore, no wake-word-listening-while-backgrounded story. |
| M-2 | **1Hz doze tick + "not more than once per 60s" CPU wake** (§2.1, §2.3). | Absent | A 30Hz/1Hz loop cannot tick inside deep doze without AlarmManager/WorkManager; 0.1Hz (every 10s) also contradicts the once-per-60s rule. The plan inherits the contradiction without resolving either side. |
| M-3 | **7×7 exhaustive transition table + 24h random-transition soak** (§2.1 Testing). | Partial | Phase 2 says only "transition tests + single-mode invariant test." No exhaustive-table or soak test. |
| M-4 | **Device-gated acceptance: battery-drain ground truth per tier, cold-start timing, fps/thermal per tier, earcon latency, multi-hour idle soak** (§2.16, §2.31, §2.17, §2.6, §2.28, §2.15). | Manual QA only | No instrumented/androidTest/CI-device plan at all; these become manual checkboxes, not gates. |
| M-5 | **Hard-shutdown path (`onTrimMemory(CRITICAL)` / `onLowMemory` / `onDestroy` without `onStop`)**, §2.18. | Absent | Plan's §2.18 row wires only `MainActivity ON_STOP`. `Application.onTrimMemory` and the skip-choreography hard path are not wired. |
| M-6 | **Audio-focus + incoming-call interrupt** (spec §0.4/§2.15: "incoming call" preempts; §2.28 mixing rules). | Absent | No `AudioManager` audio-focus handling, no telephony/headset routing, no pause-on-call, no test. |
| M-7 | **`RenderIntent` contract (spec §1)** — internal contract consumed by Avatar/Orb/Audio renderers. | Dead contract | Declared in Phase 1, then never produced or consumed anywhere in the plan; subsystems emit `OrbRenderParams`/`AvatarRenderParams` directly. No subsystem maps state → `RenderIntent`. |
| M-8 | **Local-vs-remote binding parity test** (§2.30 Testing). | Absent | Phase 1 lists mapping/staleness/lint tests only. Also unverified: the remote brain's `/api/status`-class endpoints must expose every `CompanionSignal` field — the plan never checks the remote API surface. |
| M-9 | **Listener-registration leak test across rotation/background cycles** (§2.23 Testing). | Absent | Phase 4 test list has no sensor/listener lifecycle leak test. |
| M-10 | **Full-utterance soak for memory/handle leaks** (§2.15 Testing); LeakCanary-class tooling (§2.18 Testing). | Absent | No leak-detection tooling or soak in any phase. |
| M-11 | **Interrupt-timing test (<1 frame handoff)** and **amplitude-to-visual latency <50ms** (§2.15 Testing/Perf). | Absent | Not in Phase 4 test list; no timing assertion stated. |
| M-12 | **`current_action` TOOL_CALL vs GENERATING discriminator** (feeds §2.14/§2.15/§2.24). | Gap flagged, not designed | `JarvisBrainBridge.status` has no tool-vs-generation distinction; no design for how THINKING escalation will discriminate. |
| M-13 | **Mic-permission-revoked mid-session → text-input fallback** (§2.13). | Absent | Not mentioned in plan or tests. |
| M-14 | **Notification coalescing cap ("N updates") and dismissed-notification no-nag** (§2.20). | Partial | Plan covers queue/urgency; not the cap/coalesce or the user-dismissed-no-nag interaction with Attention Engine. |
| M-15 | **Presence Engine stale-signal → IDLE fallback** (§2.1 Failure Handling). | Partial | Staleness handling specified for §2.9/§2.30; the §2.1 "fall back to IDLE, never freeze" is not represented. |
| M-16 | **Refresh-rate capping** (Realme 9 Pro is 120Hz-capable; §2.31 `targetFps` caps must bind renderer). | Absent | `withFrameNanos` follows display refresh; no mechanism caps render to tier fps. Idle battery risk. |
| M-17 | **Asset integrity CI check + visual diff for Visual Identity versioning** (§2.5 Testing). | Absent | No CI asset-parse step; no visual-diff gate. |
| M-18 | **Speech Timing latency budgets (<800ms first audio, <50ms gap jitter)**. | Absent | No pre-roll/double-buffer design and no benchmark test; per-segment sequential synthesis cannot meet jitter without it. |
| M-19 | **`self_check()`-fabrication flag semantics** (§2.30). | Mismapped | Proposed source (`GuardSoften`/`GuardVeto`) is a veto/soften event, not a caught-and-corrected claim; risks over-firing the hard-requirement `SelfCorrecting` cue (§2.25). |
| M-20 | **Idle ≤5% CPU / reduced GPU target measurement** (§2.16 Perf). | Absent | No idle-CPU budget test; Phase 7 battery tests omit it. |
| M-21 | **Multi-surface policy** (Phase 6 embeds companion surface in "app navigation surfaces"). | Absent | Two visible screens hosting the orb would run two render loops over one state machine. No single-visible-surface rule. |
| M-22 | **`OrbClip` set drift** (§2.6). | Diverges silently | Spec: {…, Sleep_Dim}; plan: {…, Shutdown_Fade, Notification_Glow}. Reasonable extension, but not flagged for spec coordination, and Sleep_Dim semantics lost. |
| M-23 | **ANR/crash watchdogs and Companion Core telemetry**. | Absent | No ANR watchdog, no runtime observability of mode/battery/thermal/drain — the Human Core audit already established the codebase has a main-thread bug class; CC adds more timers. |

---

# Architectural Problems

1. **A-1 — No threading/ownership model (critical).** Nowhere does the plan state which dispatcher runs the Presence Engine tick (30Hz), which thread `StateFlow<CompanionSignal>` is emitted/collected on, how `withFrameNanos` (Main) receives state without jank, or whether TTS `AudioTrack` callbacks touch render state. Spec §2.1 explicitly demands "a dedicated coroutine dispatcher… avoiding blocking the render thread." The plan must define a three-lane model (tick/engine lane, UI/render lane, IO lane) with explicit hand-offs, or it repeats the Human Core's C-1 defect by construction.

2. **A-2 — `RenderIntent` is a dead contract.** The spec's internal render pipeline is `CompanionState → RenderIntent → (Avatar/Orb/Audio renderers)`. The plan builds `OrbRenderParams`/`AvatarRenderParams` and has every state bundle emit directly. Either promote `RenderIntent` to the plan's actual pipeline (needed so interrupt/priority/duration-hint semantics in §1 exist), or delete it from the contract — as written it will rot.

3. **A-3 — Two clocks, one invariant.** Hard Invariant 5 ("one clock") names two sources — Presence Engine tick *and* Animation Controller frame clock — and adds Speech Timing's audio `PlaybackTimestamp` as a third timing domain (orb pulses to audio, not a guess). The plan does not define clock arbitration (which clock is master for the orb when speech timestamps and frame clock disagree). Drift between speech and animation is the exact failure §0.4.4 exists to prevent.

4. **A-4 — `current_action` under-determination (feeds three modes).** TOOL_CALL vs GENERATING is not discriminable from `JarvisBrainBridge.status` + viewmodel boundaries as described. THINKING escalation (§2.14), Speaking entry, and Conversation Flow all depend on it. Without a designed discriminator (e.g., tool-execution lifecycle events in the app shell), the plan risks THINKING never firing or firing on weak proxies.

5. **A-5 — Greeting/Notification read HC outside the choke point (invariant 2 risk).** Plan rows §2.19/§2.20 reuse `secondsSinceLastContact` and `CompanionBehaviorOrchestrator` directly from the frozen HC. Spec Invariant 2 + §2.30 mandate *all* HC state arrive via HumanCoreIntegration → CompanionSignal. The plan must route these reads through §2.30 (extend `CompanionSignal` with `secondsSinceLastContact`/proactive-behavior fields), not import HC modules into CC subsystems.

6. **A-6 — `fabrication_flag` source semantics.** `GuardSoften`/`GuardVeto` express *output* vetos, not `self_check()`-caught fabrication corrections. Mapping veto→fabrication will misfire §2.25's hard-requirement `SelfCorrecting` cue and the §2.8 confidence-muting rule. Needs a source that actually corresponds to a caught/corrected claim, or an explicit documented divergence.

7. **A-7 — `UserPresenceEvent` delivery conflicts with the frozen-HC constraint.** The two candidates offered — reusing `PresenceManager.noteBackgroundActivity()` or adding an `HumanCore` facade method — are not equivalent: the former is a narrow background-activity feed (semantic overload for USER_LOOKED_AWAY/USER_TAPPED/USER_IDLE_THRESHOLD) and a direct HC-internal call from CC; the latter modifies a frozen file. The additive-facade pattern (already established with `endSession`/`describe`) is the right path; the plan leaves this open instead of committing.

8. **A-8 — `StateBus` global-singleton coupling.** The Human Core audit documented `object StateBus` as a process-wide singleton with the only subscription never unsubscribed (one retained graph leaked per test). CC adds more subscriptions (HumanCoreIntegration, and CC tests). No unsubscribe discipline, no test-isolation plan, and the bus remains un-replaceable for any future multi-profile. This couples the two cores' test suites through global state.

9. **A-9 — Phase 4 scope cramming.** Thirteen subsystems in two weeks, several with Android-framework dependencies (sensors, PowerManager, audio), while the pure-JVM test strategy covers almost none of them. This phase cannot be delivered at spec quality; it should be split (sensor/attention workstream vs. behavioral bundles vs. greeting/notification/gesture).

10. **A-10 — Phase 0 defers decisions that block invariants, not just phases.** The plan correctly lists open items, but two of them (`UserPresenceEvent` mechanism, `current_action` discriminator) are *contract* decisions that the dependency-lint and invariant-2 contract tests cannot be written until resolved. The plan should sequence those decisions before Phase 1's "contract tests," or the tests will be written against the wrong contract.

---

# Android Risks

1. **R-A1 — No foreground service / OEM-kill strategy.** The product premise is continuous presence; the plan ships no foreground service, no `START_STICKY`/restore story, no handling for Realme/Oppo/Xiaomi battery managers. The companion will be killed within hours on the flagship reference device. This is the highest-severity Android risk and it is unmentioned.

2. **R-A2 — Doze/background tick is unimplementable as specified.** A 30Hz→1Hz loop in doze requires AlarmManager (inexact, min-interval) or WorkManager (not tick-equivalent). Spec's "0.1Hz background" (every 10s) exceeds its own "no more than once per 60s" CPU wake rule. The plan inherits both without a mechanism.

3. **R-A3 — Lifecycle seam is view-scoped.** Suspension is wired to `MainActivity`'s `ON_STOP`. The Presence Engine is Application-scoped; other activities, PIP, or split-screen mean an off-screen orb keeps rendering. Should be driven by `ProcessLifecycleOwner` + `onTrimMemory`, with the Activity observer as one input among several.

4. **R-A4 — Hard shutdown un-wired.** `Application.onTrimMemory(CRITICAL)`/`onLowMemory` path (skip choreography, release within teardown window) not represented; only the soft `ON_STOP` path is.

5. **R-A5 — Rotation leak surface.** §2.23 requires sensor listeners to survive rotation without leaks; no test, and the attention subsystem is Phase 4 where no device test infrastructure exists.

6. **R-A6 — Wake word while backgrounded.** Continuous Vosk listening requires a mic-holding foreground service with a visible notification and mic-indicator handling (Android 12+ privacy indicators). Not addressed; without it the entire voice-driven Wake Sequence is foreground-only.

7. **R-A7 — Missing `POST_NOTIFICATIONS` (API 33+).** The OS-channel fallback for background notifications (§2.20) needs runtime permission; not in the plan.

8. **R-A8 — Audio-focus and call interrupts missing.** No `AudioManager.requestAudioFocus`, no `ACTION_PHONE_STATE_CHANGED`/Telecom handling, no headset-routing handling — despite the spec naming incoming calls as a first-class preemption trigger.

---

# Performance Risks

1. **R-P1 — Undefined threading → main-thread jank/ANR.** The render loop, 30Hz tick, and TTS callbacks have no thread assignment. History (Human Core C-1) shows this codebase produces blocking-on-Main without explicit discipline.

2. **R-P2 — Orb renderer cost at target.** 260-particle Canvas grain + 7-pass glow + 3 `rememberInfiniteTransition`s at 60fps on Adreno 619, with no recomposition isolation specified. `StateFlow` emissions at 30Hz into a composable that recomposes broadly will jank. The orb must be isolated in its own recomposition scope with `derivedStateOf`-gated parameter reads.

3. **R-P3 — Speech Timing has no pre-roll pipeline.** Per-segment sequential "synthesize then play" cannot meet <50ms gap jitter or <800ms first-audio unless segment N+1 is synthesized (off-lane) while segment N plays. No double-buffer/pre-roll design; the plan's own risk note says TTS fidelity is the hardest risk but the design doesn't answer it.

4. **R-P4 — 30Hz tick + Vosk always-on + ≥15fps idle render on battery.** The ≤5% idle-CPU budget is unmeasured and likely exceeded; no idle profiling step before Phase 7.

5. **R-P5 — No frame-rate capping to tier.** On a 120Hz display the orb renders at 120fps unless explicitly throttled; the tier ladder sets `targetFps` but nothing enforces it against the display.

6. **R-P6 — `current_action` staleness→mode flapping.** THINKING/SPEAKING entry gated on an under-determined signal risks mode oscillation (Thinking→Idle→Thinking), which the spec explicitly tries to avoid.

---

# Memory Risks

1. **R-M1 — `StateBus` global state + CC subscriptions.** Human Core audit already confirmed one retained graph per test invocation; CC adds subscribers with no unsubscribe path and no cross-suite isolation. Tests will contaminate each other through the singleton.
2. **R-M2 — Per-utterance playback handles.** `SpeechTiming` is instantiated per utterance with `MediaPlayer`/`AudioTrack` ownership; the spec's own leak-soak requirement is absent (M-10). `SoundPool` + repeated TTS instances are the classic leak pair.
3. **R-M3 — Rive footprint.** `rive-android 8.7.0` AAR adds significant binary size to a low-end device build even when gated off at runtime; acceptable but should be costed (spec target is zero-budget device tier).
4. **R-M4 — No watchdog for render-resource churn.** Background suspend/reacquire of GPU surfaces (Rive/Canvas) with no leak tooling means the "release-and-reacquire with no leaks" test (§2.31) has no enforcement mechanism.

---

# Battery Risks

1. **R-B1 — Continuous wake-word VAD cost.** The dominant idle energy consumer is unaddressed: Vosk active-listening burns a persistent mic + CPU/audio path. No duty-cycle, no "wake word only while foreground/service-active" policy, no measured budget.
2. **R-B2 — Idle render floor.** 15fps minimal render + 30Hz tick + background polling simultaneously in IDLE (the majority-time state) — the "≤5% CPU, near-zero background" target is not engineered to, only stated.
3. **R-B3 — No refresh capping** (see R-P5) — direct battery leakage on 120Hz devices.
4. **R-B4 — Attention background polling contradiction** (0.1Hz vs once/60s) risks either a 10s wake cadence (battery) or a spec violation (functionality).
5. **R-B5 — No ground-truth soak in CI.** Battery drain per tier is a manual QA item; regressions will ship.

---

# Integration Risks

1. **R-I1 — HumanCoreIntegration + viewmodel dual-source ambiguity for `last_utterance_text`.** The mapping table sources it from `ConversationViewModel` while §2.30 is the designated single adapter. Who owns the field — the adapter observing `express()` output, or the viewmodel? Undefined ownership = drift and duplicate wiring.
2. **R-I2 — Remote-binding surface unverified.** The remote brain's `/api/status`-class endpoints must expose emotion/current_action/fabrication_flag/last_utterance_text with ≤2s staleness. The plan never checks this exists; if it doesn't, the "remote binding" is a fiction and the dual-mode promise (spec §2.30/§4) cannot be delivered.
3. **R-I3 — Invariant-2 boundary in Greeting/Notification** (see A-5) — the two subsystems most likely to be implemented with direct HC imports because the plan's own mapping table shows direct reuse.
4. **R-I4 — Multi-surface double-render** (see M-21): Phase 6 embeds the companion surface in navigation; no single-visible-surface arbitration.
5. **R-I5 — Init-order unspecified in `JarvisEngine`.** Companion Core init hooks are mentioned, but the ordering relative to `HumanCore` init and capability-manifest/voice init is not stated; §2.30 must come after HC init and before any subsystem consumes it.
6. **R-I6 — `fabrication_flag`/`current_action`/`confidence` all assembled from three different sources** (StateBus, bridge.status, viewmodel) with no single ownership rule for who mutates `CompanionSignal` — the choke point is a class, not a discipline.
7. **R-I7 — TTS pipeline assumption unverified.** Phase 3/4 depend on "mic VAD reuse from the existing Vosk pipeline" (20ms amplitude frames) and `JarvisTts` being extensible to segment playback; verification is scheduled after Phase 2 when Conversation Presence (Phase 4) already depends on the signal. Dependency ordering risk.

---

# Human Core Violations

1. **V-1 — Invariant-2 risk (direct reads).** §2.19/§2.20 rows reuse frozen-HC data paths outside §2.30 (A-5). If implemented as the table suggests, this breaks the single-choke-point invariant structurally, not just cosmetically.
2. **V-2 — `UserPresenceEvent` into `PresenceManager` feeds.** Direct invocation of `noteBackgroundActivity()` from CC is (a) a cross-core import outside §2.30, and (b) semantically wrong for non-background events. Frozen HC is thereby either imported or modified; the additive-facade path (A-7) should be committed to.
3. **V-3 — `fabrication_flag` from veto events** (A-6) risks misrepresenting HC state to the entire expressive layer — the exact "never fabricates / never editorialize" boundary the spec calls out.
4. **V-4 — Correct restraint elsewhere.** To be credited: the plan explicitly refuses to extend the frozen HC for `energy`/`motivation`/`attention_focus`/`confidence`, prefers neutral defaults over invented scalars, and the dependency-lint test is the right enforcement mechanism. Those choices comply with Invariant 2 and the "never fabricates" rule.

---

# Maintainability

**Strengths:** package layout mirrors `humancore/`; immutable contract data classes; pure functions (Orb mapping, expression table, theme map, gesture compatibility, ContextTag table) fully unit-testable; contract tests for all 7 invariants; dependency-graph lint; additive migrations (TTS wrapper, lifecycle observer extension) that avoid rewrites of frozen code; honest flagging of decision points.

**Weaknesses:**
- No per-subsystem design docs or module-ownership map equivalent to what the Human Core has; 31 subsystems need an ownership doc to stay decoupled.
- No defined logging/telemetry standard (severity, tagging, sampling) for a subsystem whose failures are silent-by-design (degradation everywhere).
- No ANR/crash monitoring plan; the degraded-gracefully philosophy needs a telemetry companion or failures become invisible.
- Phase 4's 13-subsystem package is an ownership and review bottleneck (A-9).
- `RenderIntent` dead code (A-2) will invite drift and duplicate types.

---

# Scalability

- **Single-identity architecture is now baked into two cores.** `StateBus` (global) + Application-scoped `PresenceEngine` + `CompanionMemoryHooks` all presume one companion; any future multi-profile/identity is a rewrite. Acceptable for v1, but should be a stated, documented decision rather than an accident.
- **Device-testing gap scales badly.** As the CC grows, the Android-dependent surface grows with no automated device gate; each change increases manual-QA burden. The pure-JVM strategy is right for pure logic and wrong for everything touching framework.
- **9–12 engineer-weeks for 31 subsystems with Phase 4 at 2 weeks** is optimistic; sequential-phase staffing means long latency to a testable whole. Phases 3–4 overlap opportunities are missed.
- **No experiment/feature-flag path** for behavior changes (greeting policy, gesture rate, idle variation) — behavioral tuning will be slow and risky without them.

---

# Testing Gaps

Summarized from M-series above; the pattern is: pure-JVM coverage is well-designed, but the entire Android-framework surface and every spec acceptance benchmark has **no automated home**:

- No instrumented tests / device CI / Robolectric at all. All of §2.6/§2.17/§2.18/§2.28/§2.31 acceptance criteria (fps, thermal, timing, battery, leaks) are manual.
- Missing unit/contract tests: 7×7 + 24h soak; staleness→IDLE; `current_action` discriminator; fabrication-flag semantics; notification coalescing/no-nag; render-interrupt <1 frame; amplitude-sync <50ms; utterance leak-soak; sensor-listener rotation leak; local/remote parity; AudioFocus/call-pause; DataStore corruption (planned); StateBus unsubscribe.
- No CI job for the dependency-lint gate is specified (test exists in Phase 1, but no pipeline statement).
- Missing: OrbClip/enum drift guard between spec and code; asset-integrity CI step; refresh-capping test.

---

# Dependency Issues

1. **Unstated new framework dependencies** for requirements the plan implies: `androidx.lifecycle:lifecycle-process` (process lifecycle), AlarmManager/WorkManager (background), `androidx.media`/audio-focus APIs (A-8/R-A8), `androidx.core` notification permission (R-A7). The plan lists only `datastore-preferences` + `rive-android`.
2. **Version-matrix risk:** Kotlin 2.4.10 with Compose BOM 2024.09.00 — the plan does not verify compiler/runtime compatibility for the new deps (`rive-android` 8.7.0, DataStore) against that pinned matrix.
3. **`rive-android` added in Phase 0** "loaded lazily" is reasonable, but its AAR ships in all builds from day one (R-M3); a `variant`/`debug`-only or `productFlavor` gating option should be considered.
4. **No new JVM test deps** for `CompanionClock`/fixtures — acceptable (manual clock pattern), but `DataStore` (Android) has no JVM test story in the plan; needs `preferences` in-memory doubles or an interface seam.
5. **No dependency on the remote brain contract** — a schema/version contract for the remote binding endpoints should be versioned alongside CC, or the dual-mode promise drifts (R-I2).

---

# Suggested Changes

**Must-fix before any device build (blocks production):**
1. Define the threading model (engine tick lane, UI/render lane, IO lane) with explicit dispatcher ownership and hand-off rules for every subsystem; add a static lint/gradle check forbidding framework/audio calls on the wrong lane. Close A-1.
2. Produce a background-presence design: foreground service + notification, doze strategy (AlarmManager/WorkManager with realistic cadence), OEM battery-manager whitelist flow, process-death restore, and a measured wake-word duty-cycle. Close M-1/M-2/R-A1/R-A2/R-B1.
3. Wire the hard-shutdown path (`Application.onTrimMemory`/`onLowMemory`) alongside the soft `ON_STOP` path; add the §2.18 timing test. Close M-5/R-A4.
4. Design the `current_action` discriminator (tool-execution lifecycle events) before Phase 1 contract tests; fall back to a documented conservative mapping (Thinking-while-sending) if the distinction proves unavailable. Close A-4/M-12.
5. Route §2.19/§2.20 HC reads through §2.30 by extending `CompanionSignal`; commit to the additive `HumanCore` advisory facade for `UserPresenceEvent`; re-source `fabrication_flag` to a true caught-correction event or document the divergence. Close A-5/A-6/A-7/V-1/V-2/V-3.
6. Add audio-focus and call/headset handling to the voice layer with tests. Close M-6/R-A8.
7. Add an instrumented/androidTest tier (even one reference device in CI) covering: lifecycle suspension, thermal tier-stepping, sensor leak, notification-permission, TTS pre-roll, leak soak, and the spec's battery/timing benchmarks — replacing "manual QA" for acceptance criteria. Close M-4/M-9/M-10/M-11/R-B5.
8. Implement `RenderIntent` as the real pipeline (state bundles → `RenderIntent` → renderers) or delete it from the contract. Close A-2/M-7.

**Strongly recommended (before hardening):**
9. Define clock arbitration for speech-vs-frame-vs-tick timing (one master, drift policy). Close A-3.
10. Specify orb recomposition isolation and frame-rate capping to tier. Close R-P2/R-P5/M-16.
11. Add Speech Timing pre-roll/double-buffering design with <800ms/<50ms benchmarks. Close M-18/R-P3.
12. Specify `StateBus` unsubscribe discipline + test isolation; add a subscription-leak test. Close A-8/R-M1.
13. Add Companion Core telemetry and ANR watchdogs; a "silent degradation" system without observability will fail invisibly. Close M-23.
14. Split Phase 4 into two workstreams; add per-subsystem ownership docs; add feature flags for behavioral tuning. Close A-9/M-21.
15. Resolve and record spec-contract drifts (OrbClip set, `RenderIntent`, `current_action`, attention cadence) as a spec-change request rather than silently. Close M-22.

---

# Overall Score

| Dimension | Rating |
|---|---|
| Spec coverage (31 subsystems) | Good — all 31 mapped, several requirements partial/absent (M-1..M-23) |
| Human Core boundary compliance | Mostly good, two invariant-2 risks + one semantic mismatch (V-1..V-3) |
| Android lifecycle | **Weak** — no background/FGS/doze/hard-shutdown story (R-A1..R-A8) |
| Performance | **At risk** — threading undefined, render/speech budgets unengineered (R-P1..R-P6) |
| Memory | At risk — bus coupling + no leak tooling (R-M1..R-M4) |
| Battery | **Weak** — wake-word cost, idle floor, cadence contradiction, no ground truth (R-B1..R-B5) |
| Testing | **Weak on device surface** — pure-JVM strong, framework surface untested |
| Maintainability / Scalability | Good with noted ownership and phase-size concerns |
| Dependencies | Minor — unstated framework deps, version matrix unverified |

**Composite: 7.0 / 10** — the plan is architecturally aligned with the spec, honest about its gaps, and executable. It is not production-ready: the threading model, the background-presence reality, and the device-testing void are structural, not cosmetic, and each independently blocks release.

---

# Verdict

**PASS WITH ISSUES**

The plan must not proceed to implementation without closing, in order: (1) the threading model, (2) the background/foreground-service/doze strategy, (3) the hard-shutdown wiring, (4) the `current_action` discriminator, (5) the §2.30 choke-point violations (Greeting/Notification/`UserPresenceEvent`/`fabrication_flag`), (6) audio-focus/call handling, and (7) an instrumented test tier for the framework-dependent acceptance criteria. Items 1–2 are release-blocking by themselves.
