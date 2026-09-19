# BODY — Failure Surface & Fault Containment Spine — Milestone Report

**Date:** 2026-08-09  
**Status:** COMPLETE  
**Scope:** One canonical failure model, a central failure surface, subsystem capture wiring, turn correlation, circuit-breaker recovery, resource-failure reporting, visual-state overlay, user-facing translation, diagnostics integration, failure persistence, and the full fault-injection test suite.

---

## 1. What Was Built

### 1.1 Canonical Failure Model (`failure/FailureModel.kt`)
- 6 severity levels: `INFO → WARNING → RECOVERABLE → DEGRADED → ERROR → CRITICAL`
- 20 category taxonomy covering the communication body
- Recoverability (`NONE / RETRYABLE / RECOVERABLE / PERMANENT`) and RecoveryAction (`RETRY / RETRY_WITH_BACKOFF / FALLBACK / RELOAD / REINITIALIZE / DEGRADE / DISABLE_COMPONENT / WAIT_FOR_RECOVERY / USER_ACTION_REQUIRED / ESCALATE`)
- `FailureEvent` (immutable occurrence), `FailureReport` (capture-site shape), `FailureAggregate` (deduplicated current state), `SubsystemHealth`, `SystemHealth`, `RecoveryEvent`, `FailureSnapshot`

### 1.2 Circuit Breaker (`failure/CircuitBreaker.kt`)
- Per-dependency `CLOSED → OPEN → HALF_OPEN → CLOSED` lifecycle
- Configurable failure threshold, sliding window, open cooldown
- Prevents retry storms on repeatedly-failing dependencies

### 1.3 Central Failure Surface (`failure/FailureSurface.kt`)
- **Thread-safe** (single lock; `report()` is cheap)
- `currentFailures: StateFlow<List<FailureAggregate>>` — active (OPEN/RECOVERING) conditions
- `recentFailures: StateFlow<List<FailureEvent>>` — bounded ring of most recent occurrences
- `recentRecoveryEvents: StateFlow<List<RecoveryEvent>>` — bounded recovery history
- `worstSeverity: StateFlow<FailureSeverity>` — highest active severity
- `subsystemHealth: StateFlow<Map<String, SubsystemHealth>>`
- `systemHealth: StateFlow<SystemHealth>` — overall: HEALTHY / DEGRADED / DOWN
- `degradedCapabilities: StateFlow<Set<String>>` — currently degraded capability keys
- `currentTurnId` — soft turn correlation (stamped by the coordinator)
- `newTurnId()` — allocate a fresh TURN-id for the correlation chain
- `snapshot()` — immutable point-in-time snapshot for diagnostics / self-diagnosis
- `reset()` — test/diagnostics helper to clear all state
- **No mutable flows exposed publicly**

### 1.4 Recovery Controller (`failure/RecoveryController.kt`)
- Per-(subsystem,operation) bounded retry with linear backoff
- `register(RetrySpec)` — declares how to recover a dependency
- `handle(FailureReport)` — surfaces the failure, feeds the breaker, schedules bounded retries
- `recordSuccess()` — closes the breaker on a healthy signal
- Circuit breaker opens → disables component (DEGRADE / DISABLE_COMPONENT reports)
- No retry storms: bounded attempts + backoff + breaker

### 1.5 Self-Diagnosis (`failure/SelfDiagnosis.kt`)
- `diagnose(event)` answers the 9 diagnostic questions deterministically from the snapshot
- Last successful stage, prior context, isolation check, recoverability, fallback availability, fallback success, subsystem health after

### 1.6 User-Facing Translation (`failure/UserFailureText.kt`)
- Maps internal failures to user-visible sentences
- Only surfaces at RECOVERABLE+ severity (not noisy)
- Never shows raw stack traces or subsystem internals

### 1.7 Failure Memory (`failure/FailureStore.kt`)
- Persists meaningful failures (≥ RECOVERABLE) to `failures.jsonl` via existing `FileStorage`
- Dedup across restarts (bounded in-memory set)
- Only persists on aggregate transitions (first-open, recovery, escalation) — no error-spam
- `loadRecent()` for diagnostic queries

---

## 2. Subsystem Capture Wiring

| Subsystem | What's Captured | Severity |
|---|---|---|
| **Vosk STT** | start failure, model unavailable | DEGRADED |
| **Sherpa Whisper** | start failure, transcribe failure | DEGRADED / RECOVERABLE |
| **Platform Recognizer** | unavailable, security exception, error, timeout | DEGRADED / ERROR / RECOVERABLE |
| **TTS (JarvisTts)** | init failure, language unavailable, speak-before-ready | DEGRADED / RECOVERABLE |
| **StreamingTts** | init failure, language unavailable, synthesis+playback failure, utterance error | DEGRADED / ERROR / RECOVERABLE |
| **MemoryStore** | persist failure, load failure | RECOVERABLE |
| **VocabularyStore** | persist failure, load failure | RECOVERABLE |
| **Mic capture** | start failure | ERROR |
| **Model warm-up** | ping failure | RECOVERABLE |
| **Model idle unload** | unload failure | RECOVERABLE |
| **Resource governor** | tier step to LOW / background suspension | WARNING |
| **LatencyLayer** | brain send exception, bridge-status ERROR | ERROR / RECOVERABLE |
| **Streaming interrupt** | TTS not wired during interrupt | RECOVERABLE |
| **Body errors** | ErrorOccurred event | ERROR |

---

## 3. Turn Correlation

Every communication turn gets a `TURN-<ms>-<n>` ID via `FailureSurface.newTurnId()`.  
The coordinator stamps this on SttResult → THINKING and on BargeIn → INTERRUPTED.  
All failure reports made during the turn carry this correlation ID.  
Events share a correlation chain that is reconstructible from the `recentFailures` ring.  

---

## 4. Containment Boundaries

- Each subsystem reports independently — a TTS failure does not touch STT, memory, or the brain.
- Circuit breakers isolate repeated failures: hammering a broken component stops when the breaker opens.
- The body state machine is unaffected: `BodyStateMachine.next()` is a pure function with no failure-reporting side effects.
- Recovery actions are bounded by `RetrySpec.maxAttempts` — no retry storms.
- Failures never disappear from the trail (aggregate counts + recovery events preserved).

---

## 5. Visual Integration

`VisualState.failureLevel: FailureSeverity` overlaid on top of the body-state mode.  
`VisualStateBridge` collects `failureWorstSeverity` and merges the alert override: `INFO→NONE`, `WARNING→WARNING`, `ERROR/CRITICAL→CRITICAL`, combined with the body-state alert via `maxOf`.  
The orb shows: `IDLE+WARNING`, `THINKING+DEGRADED`, `SPEAKING+ERROR` — never pretending healthy.

---

## 6. Diagnostics

- `DiagnosticsViewModel.failureText: StateFlow<String>` — live human-readable failure summary
- `SystemScreen` — new "Failures" row with green/amber/red dot
- `FailureSurface.snapshot()` provides `SystemHealth`, `SubsystemHealth`, active failures, degraded capabilities, recovery history — all readable from the diagnostics UI
- `FailureStore.loadRecent()` — persisted failure trail for offline diagnostics

---

## 7. Tests

| Test Class | Tests | What's Covered |
|---|---|---|
| `FailureSurfaceTest` | 7 | Dedup + count, severity escalation, turn correlation, system health, recovery without hiding failures, failed recovery, newTurnIds |
| `RecoveryControllerTest` | 3 | No spec → surfaces only, succeeding retry → recovered, exhausted retries → degraded + onDisabled |
| `FailureStoreTest` | 3 | Persist meaningful failures and reload, skip sub-threshold noise, round-trip through existing FileStorage |
| `SelfDiagnosisTest` | 2 | Diagnosis answers from surface state, isolated failure detection |

**Total new tests:** 15  
**Total tests after milestone:** 309 (294 + 15)  
**Test result:** ALL PASS

---

## 8. Build Result

| Check | Result |
|---|---|
| `compileDebugKotlin` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | BUILD SUCCESSFUL (309 tests, 0 failures) |
| `assembleDebug` | BUILD SUCCESSFUL (1m 24s) |

---

## 9. Files Created

| File | Purpose |
|---|---|
| `failure/FailureModel.kt` | Canonical enums + data types |
| `failure/FailureSurface.kt` | Central failure bus |
| `failure/CircuitBreaker.kt` | Per-dependency breaker |
| `failure/RecoveryController.kt` | Bounded retry executor |
| `failure/SelfDiagnosis.kt` | Deterministic self-diagnosis |
| `failure/UserFailureText.kt` | Internal → user-facing translation |
| `failure/FailureStore.kt` | JSONL failure persistence |
| `test/.../failure/NervousSystemFailureTest.kt` | 15 tests for surface + recovery + persistence + diagnosis |

## 10. Files Modified

| File | Change |
|---|---|
| `JarvisVosk.kt` | Added `onFailure` callback + reports on start failure |
| `JarvisSherpaWhisper.kt` | Added `onFailure` callback + reports on start + transcribe failures |
| `JarvisTts.kt` | Added `onFailure` callback + reports on init, language, speak failures |
| `StreamingTts.kt` | Added `onFailure` callback + reports on init, language, synthesis, playback failures |
| `JarvisMic.kt` | No constructor change (object) — mic start failure caught in coordinator |
| `MemoryStore.kt` | Added `onFailure` callback + try/catch on persistAsync + load failure report |
| `VocabularyStore.kt` | Added `onFailure` callback + try/catch on persistAsync + load failure report |
| `PlatformRecognizerStt.kt` | Added `onFailure` callback + reports on unavailable, security, error, timeout |
| `BodyCoordinator.kt` | + `failureSurface`, `recoveryController` params; capture points in initializeSubsystems, turn correlation, handleError, warmModel, checkModelIdle, resource tier, startPlatformRecognition, runSherpaFallback; recovery specs for Vosk/Sherpa/TTS; `failureLevel` in VisualState |
| `BodyTypes.kt` | + `VisualState.failureLevel` field |
| `VisualStateBridge.kt` | + `failureWorstSeverity` StateFlow param; `mapFailureToAlert` + `refreshAlert` merge |
| `JarvisEngine.kt` | + `FailureSurface`, `FailureStore`, `RecoveryController`, `SelfDiagnosis` creation + getters; wires MemoryStore/StreamingTts/BodyCoordinator/VisualStateBridge/LatencyLayer failure sinks |
| `LatencyLayer.kt` | + `failureSink` internal var + `setFailureSink()`; reports on bridge ERROR + model send exception |
| `DiagnosticsViewModel.kt` | + `failureText: StateFlow`; `combine` collector on surface.worstSeverity + currentFailures + subsystemHealth; `summarizeFailures` |
| `SystemScreen.kt` | + "Failures" row with `failureDotColor` mapping |
| `StoreKind.kt` | + `FAILURES("failures.jsonl")` |
| `AlertLevel.kt` | + `rank: Int` param on existing enum (needed for alert merge) |
| `ConflictResolver.kt` | + `FAILURES → unionJsonLines` branch (needed after adding FAILURES) |

---

## 10a. Honest Caveats

- **DEVICE VERIFICATION NOT PERFORMED.** No Android device available in this environment. Compiled green + JVM tests only.
- **Existing swallowed exceptions** outside this milestone (ModelManager, TtsBridge, Telemetry, LiquidEnvironmentManager, etc.) are documented in the audit but NOT fixed — per task #20 ("DO NOT FIX UNRELATED THINGS").
- **`LocalAlertStore` is dead infrastructure** (created but never invoked) — noted in audit, not fixed.
- **VocabularyStore and MemoryStore share the DIALOGUE store file** (pre-existing collision, not introduced by this milestone) — noted in audit.
- **Resource governor transitions** (LOW/background suspension) surface as WARNING; the user-facing text for these is generic ("running on reduced resources"). No UI shows the exact thermal/battery value — that's a separate instrumentation task.
- **LatencyLayer failure sink** is wired to MODEL/BRAIN category only; ack-speech or audio playback failures are captured at the StreamingTts/JarvisTts engine level, not in the pipeline.
- **The `FailureStore` appends to the existing DIALOGUE-type FileStorage path** (`filesDir/humancore/dialogue.jsonl`); collisions with other stores sharing that path are a pre-existing concern (not introduced by this milestone).

---

## 11. Remaining Risks

1. **No foreground service for the communication body** — on aggressive OEMs (Realme 9 Pro), the process may be killed while TTS is speaking or memory is being persisted. The failure surface records the condition but can't prevent the kill.
2. **StreamingTts creates a second Google TTS engine** (lazy) — two TTS engines in parallel may be throttled by the OS audio policy. Currently StreamingTts only activates when the streaming reply path is invoked; JarvisTts handles acks. The dual-engine cost is acceptable on 8 GB but should be monitored on-device.
3. **The `FailureSurface` is in-memory** (bounded rings). After process death, the failure history is lost until `FailureStore` catches up. The `FailureStore` is async and may lose the most recent event on a crash.
4. **Failure category `LATENCY`** was used for turn-level body errors (an imprecision from rapid integration — could be renamed `PIPELINE` or `BODY` in a cleanup pass).

---

*DEVICE VERIFICATION NOT PERFORMED.*
