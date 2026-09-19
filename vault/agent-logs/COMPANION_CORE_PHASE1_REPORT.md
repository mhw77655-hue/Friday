# Companion Core — Phase 1 Report

| | |
|---|---|
| **Phase** | 1 — Core contracts + Integration |
| **Plan scope** | `COMPANION_CORE_IMPLEMENTATION_PLAN.md` §5 Phase 1 (items 1–6) + §1.5 + §4.2/§4.2.1 |
| **Spec** | `COMPANION_CORE_SPEC.md` §1, §2.9, §2.30 |
| **Date** | 2026-08-05 |
| **Status** | **COMPLETE** — all 6 Phase-1 items implemented, tested, building |
| **Stopped after Phase 1** | ✅ No Phase-2 work begun |

---

## Summary

Phase 1 implemented the Companion Core's **core contracts** (immutable data classes for `CompanionSignal`, `CompanionState`, `RenderIntent`, `UserPresenceEvent`, `EmotionSnapshot`, and all §1 enums), the **single master clock** (`CompanionClock`, §1.5), the **§2.30 Human Core Integration choke point** (dual local/remote bindings behind one interface, `HumanCoreIntegration` with `StateFlow<CompanionSignal>`, `current_action` discriminator per §4.2.1, sole-writer discipline), the **additive `HumanCore.adviseUserPresence` facade** + `UserPresenceEvent` delivery path (audit A-7/V-2), and the **Emotion Expression Layer** (§2.9) with stale→neutral fallback.

The frozen Human Core was preserved: the ONLY change to `humancore/` is one additive facade method (`adviseUserPresence`) taking primitives — no existing method changed, no HC internal imports from CC beyond the sanctioned integration set (enforced by a source-scan dependency-lint test).

All 6 Phase-1 test requirements are covered (mapping, transition table, staleness, dependency lint, sole-writer, binding parity). Full unit suite: **99 tests, 0 failures**. Debug APK: **builds successfully**.

---

## Files Created

### Main source — `mobile/app/src/main/java/com/jarvis/app/companioncore/`

| File | Purpose |
|---|---|
| `contract/CompanionSignal.kt` | `CompanionSignal` (emotion_vector, confidence, energy, motivation, attention_focus, current_action, last_utterance_text, fabrication_flag), `EmotionVector`, `CurrentAction` enum. Immutable, no setters (sole-writer structural). |
| `contract/CompanionState.kt` | `CompanionState` + `PresenceMode`, `AttentionTarget`, `InterruptFlag` enums (§1). Declared in Phase 1 as the contract; the Presence Engine (§2.1, Phase 2) becomes its producer. |
| `contract/RenderIntent.kt` | `RenderIntent` as the REAL pipeline type (audit A-2/M-7): visual_state, audio_state, gesture_id, duration_hint_ms, priority + `VisualState`/`AudioState`/`GestureId`/`RenderPriority` enums. |
| `contract/UserPresenceEvent.kt` | `UserPresenceEvent` + `EventType` enum (§1, spec §0.3/§2.3). |
| `contract/EmotionSnapshot.kt` | Frozen tick-stamped snapshot (§2.9) + `neutral()` fallback (spec §2.30: "no reliable data", not "calm"). |
| `engine/CompanionClock.kt` | Single master clock (§1.5, audit A-3): injectable monotonic + epoch sources, `reset()` re-anchor, epoch↔master conversion, frame pacing (`framePeriodNanos`, `nextFrameDelayNanos`, audit R-P5/M-16). |
| `engine/RawCoreState.kt` | Binding-neutral raw HC state (plan §4.2) — the type bindings produce and the mapper consumes, so no CC code depends on HC internals. Includes `isMeaningful` (empty reads never count as fresh). |
| `engine/HumanCoreBinding.kt` | Dual-mode binding interface (`read()`, `isHealthy()`, `schemaVersion`; spec §2.30, audit R-M5/R-I2). |
| `engine/LocalHumanCoreBinding.kt` | Local in-process binding: reads `HumanCore.snapshot()` + `JarvisBrainBridge` status/lastReply, subscribes `StateBus` for `GuardSoften`/`GuardVeto` fabrication evidence (audit A-6/V-3 semantic guard). |
| `engine/RemoteHumanCoreBinding.kt` | Remote HTTP binding: async pull on an I/O executor, last-known-good cache (never blocks the caller), injectable HTTP+parser for tests, documented `/api/status`-shaped default parser. |
| `engine/SignalMapper.kt` | Pure `RawCoreState` + `CurrentAction` → `CompanionSignal` (plan §4.2). Documented GAP heuristics for confidence/energy/motivation; never extends the frozen HC. |
| `engine/CurrentActionDiscriminator.kt` | `current_action` state machine (plan §4.2.1, audit A-4/M-12/R-P6): explicit phases, conservative tool→GENERATING fallback, dwell hysteresis, DONE idle-timeout. + `BridgeStatusClassifier`. |
| `engine/EmotionExpressionLayer.kt` | §2.9 single fan-out point: one `EmotionSnapshot` per tick via `StateFlow`, stale→neutral fallback, immutable snapshot per tick. |
| `engine/HumanCoreIntegration.kt` | The §2.30 choke point: owns the private `MutableStateFlow<CompanionSignal>` (exposed read-only), drives tick → discriminator → mapper → emotion layer, live staleness, conversation-boundary events, `UserPresenceEvent` outward path. |

### Modified main source

| File | Change |
|---|---|
| `humancore/HumanCore.kt` | ADDED one additive facade method `adviseUserPresence(eventType, timestamp, confidence)` (audit A-7/V-2). Takes primitives so HC keeps zero dependency on the CC package; forwards absence/background advisories to `PresenceManager.noteBackgroundActivity`, accepts foreground observations without fabricating HC state. No existing method changed. |

### Tests — `mobile/app/src/test/java/com/jarvis/app/companioncore/`

| File | Requirement (plan Phase 1 item 6) |
|---|---|
| `TestFixtures.kt` | Manual clocks + `snapshot()` fixture. |
| `SignalMapperTest.kt` | Mapping unit tests — every `CompanionSignal` field from fixtures (incl. heuristic monotonicity, null→neutral, purity). |
| `CurrentActionDiscriminatorTest.kt` | Full `current_action` transition table + classifier. |
| `CompanionClockTest.kt` | Clock monotonic/epoch, round-trip conversions, frame pacing. |
| `EmotionExpressionLayerTest.kt` | One-snapshot-per-tick, stale→neutral, cross-consumer consistency, immutability. |
| `HumanCoreIntegrationTest.kt` | Staleness fallback (live), sole-writer flow view, conversation-boundary events, `UserPresenceEvent` facade delivery. |
| `BindingParityTest.kt` | Local-vs-remote parity (audit M-8): equivalent HC state → identical `CompanionSignal`, end-to-end through both bindings. |
| `DependencyLintTest.kt` | Source-scan dependency-graph lint (no HC imports outside `HumanCoreIntegration`/`LocalHumanCoreBinding`) + sole-writer structural test + immutable-field test. |
| `FakeBinding.kt` | Deterministic binding for integration tests. |

---

## Tests Executed

Command: `gradle :mobile:app:testDebugUnitTest`

| Suite | Tests | Failures |
|---|---|---|
| Companion Core (`com.jarvis.app.companioncore.*`) | 55 | 0 |
| Full project (incl. existing Human Core suite) | **99** | **0** |

All 6 Phase-1 test requirements pass:
1. ✅ Mapping unit tests (every `CompanionSignal` field)
2. ✅ `current_action` transition table (exhaustive, incl. dwell hysteresis)
3. ✅ Staleness fallback (live-computed, empty-read latch, emotion-layer neutral)
4. ✅ Dependency-graph lint (source scan, spec §2.30 Testing)
5. ✅ Sole-writer discipline (immutable contract + private `MutableStateFlow` + structural test)
6. ✅ Local-vs-remote binding parity (audit M-8)

---

## Build Result

Command: `gradle :mobile:app:assembleDebug`

```
> Task :mobile:app:assembleDebug
BUILD SUCCESSFUL in 45s
```

- **APK**: `mobile/app/build/outputs/apk/debug/app-debug.apk` ✅
- **Compilation**: main + test Kotlin compile clean (after fixing two compile-time issues during bring-up: `schemaVersion` override collision in the remote binding, and `walkTopDown` import in the lint test — both fixed, no lasting issues).

---

## Phase-1 Design Decisions (per plan §4.2/§4.2.1)

1. **`current_action` conservative fallback** (§4.2.1): the Kotlin `JarvisBrainBridge` exposes no tool-vs-generation distinction, so the local discriminator maps both tool and generation paths to `GENERATING`. `TOOL_CALL` remains in the enum (a remote binding may supply it) but is never produced locally. Dwelling hysteresis prevents GENERATING↔IDLE flapping on transient bridge errors (audit R-P6).
2. **`last_utterance_text` ownership** (audit R-I1): owned by the binding/integration — read from `JarvisBrainBridge.lastReply`, never written by the ViewModel.
3. **`fabrication_flag` semantic guard** (audit A-6/V-3): set only when the HC Consistency Guard publishes `GuardSoften`/`GuardVeto` whose reason names a `fabricated_` claim; cleared on a new user submission. Never inferred from raw text.
4. **GAP heuristics** (plan §4.2, no HC extension): `confidence = 0.25 + 0.35·|valence| + 0.4·trust`; `energy = arousal`; `motivation` = base + energy, discounted when HC presence is AWAY. `attention_focus` is spec-deferred → `null` until the Attention Engine (§2.3, Phase 4).
5. **StateBus subscription** lives in `LocalHumanCoreBinding` (part of the sanctioned §2.30 integration set) rather than `HumanCoreIntegration` itself — the binding is the HC-facing half of the adapter and already imports the sanctioned HC API; `HumanCoreIntegration` orchestrates without itself importing HC internals. The dependency-lint test allows exactly this set.
6. **`adviseUserPresence` takes primitives** so the frozen HC keeps zero type-level dependency on the Companion Core package — the one-directional contract holds at the type level too.

---

## Remaining Phase 1 Work

**None.** All 6 Phase-1 items are complete, tested, and building.

Open items deferred *by design* (out of Phase-1 scope, per the plan):
- No live tick loop — the Presence Engine (§2.1, Phase 2) owns cadence and drives `HumanCoreIntegration.tick()` (plan §1.6 Engine Tick Lane). Phase 1 ships the `tick()` seam, exercised deterministically by tests.
- `CompanionState` is declared but not produced — the Presence Engine (Phase 2) is its sole producer.
- `RenderIntent` is declared and typed for renderers, but the Animation Controller that resolves intents is Phase 2.
- `RemoteHumanCoreBinding`'s exact endpoint/field contract is a spec §4 open item; the parser is the single point to adapt once the live endpoint is settled.

---

## Risks Discovered

| Risk | Detail | Mitigation in Phase 1 |
|---|---|---|
| **Fabrication evidence is reason-string parsed** | `GuardSoften`/`GuardVeto` carry categories only in the `reason` string, not a structured field. The `fabricated_` prefix check is a string convention. | Semantic guard keeps it conservative (only confirmed fabrication names set the flag); documented in `LocalHumanCoreBinding`. |
| **Staleness basis** | Staleness is computed from read-arrival (a meaningful `binding.read()`), not from HC's snapshot timestamp — an idle-but-healthy HC keeps the signal fresh. This matches spec §2.30 ("no fresh signal *arrives*") but means staleness trips only when reads stop or go empty. | Verified by tests: empty read latches stale; no-tick-for-3s trips stale; fresh read re-arms. |
| **Confidence heuristic is a guess** | There is no HC self-confidence scalar; the `0.25 + 0.35·|valence| + 0.4·trust` formula is documented but uncalibrated. | Flagged in `CompanionSignal` docs as a GAP heuristic; calibration is deferred to Phase 5 (governor/metrics) where it belongs. |
| **`RemoteHumanCoreBinding` executor** | Uses a scheduled executor; if never shut down it lingers for the process lifetime (acceptable — app-lifetime object) and it is not exercised by a device test in Phase 1. | Thread is daemon; unit-tested via `refreshNow()`; device-gated remote tests land in the Phase-2 androidTest tier. |
| **Lint test is path-sensitive** | `DependencyLintTest` locates the source root via candidate paths from `user.dir`. | Falls back across `user.dir` and repo-relative paths; fails loudly (not silently) if the root can't be found. |

---

## Recommended Next Step

**Proceed to Phase 2 — Presence Engine + renderer foundation** (§2.1, §2.5, §2.6, §2.21, §2.31 core), per the plan:

1. Stand up the **Engine Tick Lane** (plan §1.6) and the Presence Engine tick loop that drives `HumanCoreIntegration.tick()` and `EmotionExpressionLayer` at 30Hz (≥30Hz foreground / reduced background).
2. Implement the **Presence Engine** (§2.1) to produce the first live `CompanionState` (7-mode state machine) and broadcast it.
3. Begin the **orb renderer foundation** (§2.5/§2.6) against the already-typed `RenderIntent` — the first real consumer of Phase-1 contracts.
4. Wire the Phase-2 **androidTest tier** (plan §9) for the device-gated acceptance criteria (frame pacing, leak soak).

Phase 1's contracts (`CompanionSignal`, `RenderIntent`, `CompanionState`, `EmotionSnapshot`) and the `HumanCoreIntegration` choke point are the seams Phase 2 builds on.
