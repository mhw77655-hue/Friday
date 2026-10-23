# Vision audit — 413 files (paths not enumerated in this title; every finding below names its own file)

Generated 2026-10-20 22:56 UTC by `.ralph/audit.py` (a mechanism, per VISION.md Section 9; not an authority on anything).

## What was read

- Checklist: **VISION.md Section 13**, 8 questions, parsed out of the file at run time -- not a copy of it. The quoted text of each question is in every finding below.
- The rule a finding is measured against: **VISION.md Section 4**.
- The code: `mobile/app/src/main/java`, the 413 changed file(s) between `acdf9a78464172d0348056752dde3c09d2eae69d` and `HEAD`.
- **Not** read: VISION.md Section 12, which discloses this repo's known deviations by name. A tool handed the answers proves nothing, so these findings were reached from the code and the checklist alone. If a finding below matches a name in Section 12, that is a coincidence the tool is not allowed to benefit from.

## Headline

| | |
|---|---|
| deviations (Section 13 answered Yes) | **505** |
| observations (seen, deliberately not called a deviation) | 94 |
| model calls spent | **0** |
| draft patch produced | yes |
| branch built | no |
| merged | **no** — this tool has no merge path |

## Deviations

### closed_type — `DeviceControlOutcome`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/JarvisEngine.kt:739`
- **Code:** `sealed class DeviceControlOutcome {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class DeviceControlOutcome` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `AlertLevel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/alerts/LocalAlertStore.kt:216`
- **Code:** `enum class AlertLevel {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class AlertLevel` with 3 member(s) [INFO, WARNING, ERROR]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ActionType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/android/DeviceControl.kt:14`
- **Code:** `enum class ActionType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ActionType` with 9 member(s) [LAUNCH_APP, PLAY, PAUSE, NEXT_TRACK, PREV_TRACK, OPEN_SETTINGS, TOGGLE_WIFI, VOLUME_CHANGE, SCREENSCAPE_SUMMARY]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `VerificationStage`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/android/DeviceControl.kt:32`
- **Code:** `enum class VerificationStage {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class VerificationStage` with 3 member(s) [NOT_ATTEMPTED, LAUNCHED, TARGET_CONFIRMED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `MediaTransportCommand`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/android/MediaSessionControlBackend.kt:7`
- **Code:** `enum class MediaTransportCommand {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class MediaTransportCommand` with 3 member(s) [PLAY, PAUSE, NEXT_TRACK]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `GateOutcome`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/android/RiskGate.kt:20`
- **Code:** `sealed class GateOutcome {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class GateOutcome` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `RiskTier`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/android/RiskTier.kt:13`
- **Code:** `enum class RiskTier {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class RiskTier` with 3 member(s) [LOW, MEDIUM, HIGH]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `GateResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/approval/ApprovalGate.kt:28`
- **Code:** `sealed interface GateResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface GateResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ApprovalStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/approvals/LocalApprovalQueue.kt:279`
- **Code:** `enum class ApprovalStatus {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ApprovalStatus` with 4 member(s) [PENDING, APPROVED, DENIED, DEFERRED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ApprovalDecision`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/approvals/LocalApprovalQueue.kt:287`
- **Code:** `enum class ApprovalDecision {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ApprovalDecision` with 3 member(s) [APPROVE, DENY, DEFER]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `RiskLevel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/approvals/LocalApprovalQueue.kt:294`
- **Code:** `enum class RiskLevel {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class RiskLevel` with 3 member(s) [LOW, MEDIUM, HIGH]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SectionPriority`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt:404`
- **Code:** `enum class SectionPriority { CRITICAL, HIGH, MEDIUM, LOW }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SectionPriority` with 3 member(s) [CRITICAL, HIGH, MEDIUM]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `BodyState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:14`
- **Code:** `enum class BodyState {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class BodyState` with 12 member(s) [IDLE, WAKE, LISTENING, HEARING, THINKING, RETRIEVING, RESPONDING, SPEAKING, INTERRUPTED, LEARNING, ERROR, USER_PRESENT]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `BodyEvent`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:40`
- **Code:** `sealed interface BodyEvent {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface BodyEvent` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `MemoryType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:87`
- **Code:** `enum class MemoryType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class MemoryType` with 4 member(s) [FACT, EPISODIC, VOCABULARY, PREFERENCE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SttEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/LanguageRouter.kt:67`
- **Code:** `enum class SttEngine { VOSK, SHERPA_WHISPER, PLATFORM_SPEECH_RECOGNIZER }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SttEngine` with 2 member(s) [VOSK, SHERPA_WHISPER]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `PromotionDecision`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/MemoryPromotion.kt:39`
- **Code:** `enum class PromotionDecision {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class PromotionDecision` with 7 member(s) [IGNORE, SHORT_TERM, SESSION, EPISODIC, SEMANTIC, USER_PREFERENCE, VOCABULARY]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `BootstrapState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:181`
- **Code:** `sealed interface BootstrapState {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface BootstrapState` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `BootstrapStep`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:188`
- **Code:** `enum class BootstrapStep {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class BootstrapStep` with 7 member(s) [CHECK_PERMISSIONS, CHECK_SHIZUKU, CHECK_LLAMA_SERVER, LOAD_IDENTITY, SELECT_ENVIRONMENT, INITIALIZE_COMPONENTS, COMPLETE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `BootstrapResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:201`
- **Code:** `sealed interface BootstrapResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface BootstrapResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `BootstrapException`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:206`
- **Code:** `sealed class BootstrapException(message: String) : Exception(message) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class BootstrapException` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `TaskType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityArbitrator.kt:46`
- **Code:** `enum class TaskType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class TaskType` with 11 member(s) [WAKE_WORD_DETECTION, SPEECH_RECOGNITION, LANGUAGE_DETECTION, TEXT_GENERATION, REASONING, MEMORY_RETRIEVAL, MEMORY_PROMOTION, SPEECH_SYNTHESIS, VOCABULARY_LOOKUP, VISUAL_RENDERING, SYSTEM_DIAGNOSTICS]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ArbitrationResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityArbitrator.kt:62`
- **Code:** `sealed class ArbitrationResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class ArbitrationResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Category`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt:21`
- **Code:** `enum class Category {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Category` with 9 member(s) [STT, TTS, LLM, MEMORY, VAD, WAKE_WORD, SYSTEM, VISUAL, LANGUAGE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `QualityTier`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt:26`
- **Code:** `enum class QualityTier { LOW, MEDIUM, HIGH, BEST }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class QualityTier` with 3 member(s) [LOW, MEDIUM, HIGH]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `State`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt:29`
- **Code:** `enum class State { DORMANT, LOADING, LOADED, ACTIVE, IDLE, SUSPENDED, UNLOADING, FAILED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class State` with 7 member(s) [DORMANT, LOADING, LOADED, ACTIVE, IDLE, SUSPENDED, UNLOADING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Health`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt:32`
- **Code:** `enum class Health { HEALTHY, DEGRADED, FAILED, RECOVERING, DISABLED, UNAVAILABLE }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Health` with 5 member(s) [HEALTHY, DEGRADED, FAILED, RECOVERING, DISABLED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `RecoveryStrategy`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt:35`
- **Code:** `enum class RecoveryStrategy {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class RecoveryStrategy` with 5 member(s) [RETRY, FALLBACK, DEGRADE, RESTART, DISABLE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `RouteResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRouter.kt:28`
- **Code:** `sealed class RouteResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class RouteResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `CloudResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cloud/CloudModelPool.kt:14`
- **Code:** `sealed class CloudResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class CloudResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ChangeLayer`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ChangeLayer.kt:38`
- **Code:** `enum class ChangeLayer {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ChangeLayer` with 3 member(s) [FAST, SLOW, CORE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ClaimKind`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ClaimKind.kt:18`
- **Code:** `enum class ClaimKind(val isPrediction: Boolean, private val isIdentityAdjacent: Boolean = false) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ClaimKind` with 7 member(s) [MENTAL_STATE, CONTEXT_WINDOW, INTERPRETATION, PREDICTION, SELF_MODEL, PERSONA_TRAIT, TRUST_TIER]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SectionPriority`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveContextBuilder.kt:463`
- **Code:** `enum class SectionPriority { CRITICAL, HIGH, MEDIUM, LOW }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SectionPriority` with 3 member(s) [CRITICAL, HIGH, MEDIUM]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `TurnDecision`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:267`
- **Code:** `enum class TurnDecision {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class TurnDecision` with 3 member(s) [DIRECT_REPLY, CAPABILITY_CALL, PLAN]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `IntentCategory`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1298`
- **Code:** `enum class IntentCategory {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class IntentCategory` with 3 member(s) [QUESTION, COMMAND, STATEMENT]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `CognitiveEvent`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1579`
- **Code:** `sealed interface CognitiveEvent {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface CognitiveEvent` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `DecompositionCause`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1760`
- **Code:** `enum class DecompositionCause {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class DecompositionCause` with 2 member(s) [NO_SEAM, LLM_ERROR]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `IntentState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:100`
- **Code:** `enum class IntentState {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class IntentState` with 10 member(s) [UNKNOWN, QUESTION, COMMAND, CONVERSATION, TEACHING, CLARIFICATION, GREETING, FAREWELL, CORRECTION, META_COGNITIVE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `GoalPriority`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:126`
- **Code:** `enum class GoalPriority { LOW, NORMAL, HIGH, CRITICAL }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class GoalPriority` with 3 member(s) [LOW, NORMAL, HIGH]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `GoalStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:133`
- **Code:** `enum class GoalStatus {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class GoalStatus` with 8 member(s) [CREATED, ACTIVE, BLOCKED, PAUSED, COMPLETED, FAILED, CANCELLED, SUSPENDED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SubgoalStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:150`
- **Code:** `enum class SubgoalStatus { PENDING, IN_PROGRESS, COMPLETED, BLOCKED, FAILED, PAUSED, CANCELLED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SubgoalStatus` with 6 member(s) [PENDING, IN_PROGRESS, COMPLETED, BLOCKED, FAILED, PAUSED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `MemorySource`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:178`
- **Code:** `enum class MemorySource { FACT, EPISODIC, PREFERENCE, VOCABULARY, CONTEXT, INFERRED, EXTERNAL }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class MemorySource` with 6 member(s) [FACT, EPISODIC, PREFERENCE, VOCABULARY, CONTEXT, INFERRED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `AttentionSource`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:193`
- **Code:** `enum class AttentionSource {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class AttentionSource` with 5 member(s) [USER_INPUT, MEMORY_RETRIEVAL, MODEL_OUTPUT, ENVIRONMENT, INTERNAL_REASONING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ThermalState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:224`
- **Code:** `enum class ThermalState { NOMINAL, WARM, HOT, CRITICAL }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ThermalState` with 3 member(s) [NOMINAL, WARM, HOT]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EntityType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:316`
- **Code:** `enum class EntityType { PERSON, DEVICE, LOCATION, CONCEPT, TASK, UNKNOWN }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EntityType` with 5 member(s) [PERSON, DEVICE, LOCATION, CONCEPT, TASK]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `PlanStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:335`
- **Code:** `enum class PlanStatus { PENDING, EXECUTING, BLOCKED, PAUSED, COMPLETED, FAILED, REPLANNING }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class PlanStatus` with 6 member(s) [PENDING, EXECUTING, BLOCKED, PAUSED, COMPLETED, FAILED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `PlanStepStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:347`
- **Code:** `enum class PlanStepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED, BLOCKED, PAUSED, CANCELLED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class PlanStepStatus` with 7 member(s) [PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED, BLOCKED, PAUSED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `DecisionFactor`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:400`
- **Code:** `enum class DecisionFactor { GOAL_ALIGNMENT, CONSTRAINT, UNCERTAINTY, RESOURCE, CAPABILITY, OUTCOME, RISK, REVERSIBILITY, EFFORT }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class DecisionFactor` with 8 member(s) [GOAL_ALIGNMENT, CONSTRAINT, UNCERTAINTY, RESOURCE, CAPABILITY, OUTCOME, RISK, REVERSIBILITY]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `DecisionOutcome`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:433`
- **Code:** `enum class DecisionOutcome { SUCCESS, PARTIAL, FAILED, UNKNOWN, PENDING }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class DecisionOutcome` with 4 member(s) [SUCCESS, PARTIAL, FAILED, UNKNOWN]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ConstraintType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/IntentInference.kt:551`
- **Code:** `enum class ConstraintType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ConstraintType` with 6 member(s) [TIME, MODALITY, DETAIL, PRIVACY, RESOURCE, SAFETY]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `AmbiguityLevel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/IntentInference.kt:563`
- **Code:** `enum class AmbiguityLevel { NONE, LOW, MEDIUM, HIGH }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class AmbiguityLevel` with 3 member(s) [NONE, LOW, MEDIUM]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `InterpretationSource`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/IntentInference.kt:572`
- **Code:** `enum class InterpretationSource { EXPLICIT, INFERRED, CONFLICTING_PATTERNS, CONTEXTUAL }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class InterpretationSource` with 3 member(s) [EXPLICIT, INFERRED, CONFLICTING_PATTERNS]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `UnknownType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/IntentInference.kt:580`
- **Code:** `enum class UnknownType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class UnknownType` with 7 member(s) [REFERENT, VAGUE_ACTION, MISSING_PARAMETERS, MISSING_GOAL_CONTEXT, USER_IDENTITY, CAPABILITY_MATCH, TEMPORAL_CONTEXT]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EntityType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/IntentInference.kt:591`
- **Code:** `enum class EntityType { PERSON, LOCATION, OBJECT, CONCEPT, TIME, QUANTITY, UNKNOWN }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EntityType` with 6 member(s) [PERSON, LOCATION, OBJECT, CONCEPT, TIME, QUANTITY]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `MemoryEvent`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/WorkingMemory.kt:81`
- **Code:** `sealed interface MemoryEvent {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface MemoryEvent` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EvictionReason`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/WorkingMemory.kt:90`
- **Code:** `enum class EvictionReason {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EvictionReason` with 5 member(s) [CAPACITY, LOW_ACTIVATION, LOW_RELEVANCE, LOW_GOAL_ALIGNMENT, HIGH_UNCERTAINTY]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `InsertResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/WorkingMemory.kt:393`
- **Code:** `sealed interface InsertResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface InsertResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `AccessResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/WorkingMemory.kt:398`
- **Code:** `sealed interface AccessResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface AccessResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `CapabilityLifecycle`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Capability.kt:37`
- **Code:** `enum class CapabilityLifecycle {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class CapabilityLifecycle` with 9 member(s) [REGISTERED, AVAILABLE, RUNNING, PAUSED, DEGRADED, ISOLATED, UNAVAILABLE, DISABLED, FAILED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `RegisterResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityRegistry.kt:26`
- **Code:** `sealed class RegisterResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class RegisterResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `TransitionResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityRegistry.kt:33`
- **Code:** `sealed class TransitionResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class TransitionResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ResolutionReason`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityResolver.kt:7`
- **Code:** `enum class ResolutionReason {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ResolutionReason` with 6 member(s) [INVALID_REQUEST, OPERATION_NOT_FOUND, CAPABILITY_DISABLED, CAPABILITY_UNAVAILABLE, DEPENDENCY_UNAVAILABLE, PERMISSION_DENIED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Resolution`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityResolver.kt:20`
- **Code:** `sealed class Resolution {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class Resolution` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `RiskLevel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Descriptor.kt:29`
- **Code:** `enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class RiskLevel` with 3 member(s) [LOW, MEDIUM, HIGH]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Availability`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Descriptor.kt:31`
- **Code:** `enum class Availability { ONLINE, DEGRADED, OFFLINE }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Availability` with 2 member(s) [ONLINE, DEGRADED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ThermalCost`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Descriptor.kt:56`
- **Code:** `enum class ThermalCost { NONE, LOW, MEDIUM, HIGH }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ThermalCost` with 3 member(s) [NONE, LOW, MEDIUM]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `LatencyClass`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Descriptor.kt:58`
- **Code:** `enum class LatencyClass { LOW, MEDIUM, HIGH, UNBOUNDED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class LatencyClass` with 3 member(s) [LOW, MEDIUM, HIGH]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `CapabilityPermission`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Descriptor.kt:62`
- **Code:** `enum class CapabilityPermission { READ, WRITE, EXECUTE, NETWORK, SYSTEM, USER_DATA }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class CapabilityPermission` with 5 member(s) [READ, WRITE, EXECUTE, NETWORK, SYSTEM]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ActionFailure`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/execution/ActionPort.kt:39`
- **Code:** `sealed class ActionFailure {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class ActionFailure` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ExecutionStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/execution/ExecutionState.kt:4`
- **Code:** `enum class ExecutionStatus {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ExecutionStatus` with 7 member(s) [IDLE, EXECUTING, RETRYING, PAUSED, CANCELLED, BLOCKED, COMPLETED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EnvironmentOutcome`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneMemory.kt:96`
- **Code:** `sealed class EnvironmentOutcome {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class EnvironmentOutcome` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ProbeOutcome`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:229`
- **Code:** `enum class ProbeOutcome { DENIED, RECOVERED, FAILED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ProbeOutcome` with 2 member(s) [DENIED, RECOVERED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ResourceResponse`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ResourceFailure.kt:12`
- **Code:** `enum class ResourceResponse {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ResourceResponse` with 6 member(s) [NORMAL, REDUCE_CONCURRENCY, STOP_OPTIONAL_WORK, UNLOAD_EXPENSIVE_CAPABILITY, SWITCH_TO_LOWER_COST, PAUSE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ChainState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:501`
- **Code:** `enum class ChainState { CURRENT, SUPERSEDED, CONFLICTED, ARCHIVED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ChainState` with 3 member(s) [CURRENT, SUPERSEDED, CONFLICTED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `UnknownType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:565`
- **Code:** `enum class UnknownType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class UnknownType` with 5 member(s) [MISSING_ENTITY, MISSING_GOAL_CONTEXT, UNRESOLVED_CONFLICT, LOW_CONFIDENCE, TEMPORAL_GAP]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `PersistResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityPersistence.kt:268`
- **Code:** `sealed interface PersistResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface PersistResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ExperienceSource`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:67`
- **Code:** `enum class ExperienceSource {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ExperienceSource` with 7 member(s) [COGNITIVE_CYCLE, USER_INTERACTION, EXECUTION, OBSERVATION, REFLECTION, EXTERNAL_EVENT, CORRECTION]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EntityType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:98`
- **Code:** `enum class EntityType { PERSON, DEVICE, LOCATION, CONCEPT, TASK, UNKNOWN }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EntityType` with 5 member(s) [PERSON, DEVICE, LOCATION, CONCEPT, TASK]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EntityRole`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:100`
- **Code:** `enum class EntityRole { ACTOR, TARGET, OBSERVER, PARTICIPANT, CONTEXT }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EntityRole` with 4 member(s) [ACTOR, TARGET, OBSERVER, PARTICIPANT]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ActionType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:111`
- **Code:** `enum class ActionType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ActionType` with 10 member(s) [UNKNOWN, SPEAK, LISTEN, THINK, EXECUTE, OBSERVE, LEARN, DECIDE, PLAN, RETRIEVE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ActionInitiator`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:115`
- **Code:** `enum class ActionInitiator { SELF, USER, SYSTEM, EXTERNAL }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ActionInitiator` with 3 member(s) [SELF, USER, SYSTEM]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Outcome`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:127`
- **Code:** `enum class Outcome { UNKNOWN, SUCCESS, PARTIAL, FAILED, INTERRUPTED, DEFERRED, BLOCKED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Outcome` with 6 member(s) [UNKNOWN, SUCCESS, PARTIAL, FAILED, INTERRUPTED, DEFERRED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `FailureSeverity`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:138`
- **Code:** `enum class FailureSeverity { INFO, RECOVERABLE, DEGRADED, CRITICAL, FATAL }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class FailureSeverity` with 4 member(s) [INFO, RECOVERABLE, DEGRADED, CRITICAL]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EvidenceType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:149`
- **Code:** `enum class EvidenceType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EvidenceType` with 5 member(s) [OBSERVATION, USER_CONFIRMATION, SYSTEM_LOG, INFERENCE, EXTERNAL_SOURCE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `MemoryLifecycleState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryCandidate.kt:11`
- **Code:** `enum class MemoryLifecycleState(`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class MemoryLifecycleState` with 6 member(s) [CANDIDATE, ACTIVE, CONSOLIDATED, SUPERSEDED, CONFLICTED, ARCHIVED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `LifecycleTransition`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryCandidate.kt:64`
- **Code:** `enum class LifecycleTransition(`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class LifecycleTransition` with 18 member(s) [CANDIDATE_TO_ACTIVE, CANDIDATE_TO_REJECTED, CANDIDATE_TO_DEFERRED, ACTIVE_TO_CONSOLIDATED, ACTIVE_TO_SUPERSEDED, ACTIVE_TO_CONFLICTED, ACTIVE_TO_ARCHIVED, CONSOLIDATED_TO_SUPERSEDED, CONSOLIDATED_TO_CONFLICTED, CONSOLIDATED_TO_ARCHIVED, SUPERSEDED_TO_ARCHIVED, SUPERSEDED_TO_CONFLICTED, CONFLICTED_TO_RESOLVED_ACTIVE, CONFLICTED_TO_RESOLVED_CONSOLIDATED, CONFLICTED_TO_RESOLVED_SUPERSEDED, CONFLICTED_TO_ARCHIVED, ARCHIVED_TO_ACTIVE, ARCHIVED_TO_CONSOLIDATED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `PromotionDecision`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryCandidate.kt:211`
- **Code:** `enum class PromotionDecision {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class PromotionDecision` with 2 member(s) [PROMOTE, REJECT]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ConflictType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:106`
- **Code:** `enum class ConflictType(val label: String) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ConflictType` with 7 member(s) [CONTENT_CONTRADICTION, ATTRIBUTE_CONTRADICTION, TEMPORAL_CONTRADICTION, SOURCE_CONTRADICTION, PREFERENCE_CONTRADICTION, FACT_CONTRADICTION, SEMANTIC_CONTRADICTION]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ConflictResolutionState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:133`
- **Code:** `enum class ConflictResolutionState(val description: String) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ConflictResolutionState` with 6 member(s) [UNRESOLVED, RESOLVING, RESOLVED, SUPERSEDED, MERGED, UNKNOWN]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ResolutionMethod`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:187`
- **Code:** `enum class ResolutionMethod(val description: String) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ResolutionMethod` with 7 member(s) [HIGHEST_CONFIDENCE, LAST_WRITER_WINS, HIGHEST_RELIABILITY, USER_INSTRUCTION, MERGE, SUPERSESSION, DEFERRED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ConflictResolutionResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:556`
- **Code:** `sealed interface ConflictResolutionResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface ConflictResolutionResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SubmitResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConsolidator.kt:472`
- **Code:** `sealed interface SubmitResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface SubmitResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EvaluationResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConsolidator.kt:477`
- **Code:** `sealed interface EvaluationResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface EvaluationResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `StoreOperation`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryContinuityAdapter.kt:218`
- **Code:** `enum class StoreOperation {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class StoreOperation` with 5 member(s) [STORE_FACT, STORE_PREFERENCE, STORE_EPISODIC, PROMOTE_TO_EPISODIC, STORE_CONVERSATION]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `DerivationStepType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryProvenance.kt:118`
- **Code:** `enum class DerivationStepType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class DerivationStepType` with 11 member(s) [INITIAL_CONSOLIDATION, SUPERSESSION, UPDATE, CONFLICT_RESOLUTION, MERGE, SPLIT, VERIFICATION, CORRECTION, DECAY, REINFORCEMENT, RECALL]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `TemporalRelationshipType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryProvenance.kt:196`
- **Code:** `enum class TemporalRelationshipType(val label: String) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class TemporalRelationshipType` with 12 member(s) [BEFORE, AFTER, DURING, CHANGED_TO, SUPERSEDED_BY, SUPERSEDES, DERIVED_FROM, SOURCE_OF, CONCURRENT, CAUSED, CAUSED_BY, CONTRADICTS]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SupersessionResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:332`
- **Code:** `sealed interface SupersessionResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface SupersessionResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `UpdateResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:339`
- **Code:** `sealed interface UpdateResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface UpdateResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ReinforceResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:344`
- **Code:** `sealed interface ReinforceResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface ReinforceResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `DecayResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:349`
- **Code:** `sealed interface DecayResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface DecayResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `TemporalRelationshipResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:354`
- **Code:** `sealed interface TemporalRelationshipResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface TemporalRelationshipResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ModelDomain`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:78`
- **Code:** `enum class ModelDomain { SELF, USER, WORLD }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ModelDomain` with 2 member(s) [SELF, USER]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ModelFactStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:86`
- **Code:** `enum class ModelFactStatus {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ModelFactStatus` with 5 member(s) [UNKNOWN, OBSERVED, INFERRED, CONFIRMED, CONTRADICTED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ModelEvidenceSource`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:131`
- **Code:** `enum class ModelEvidenceSource {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ModelEvidenceSource` with 9 member(s) [EXPLICIT_USER_STATEMENT, SYSTEM_CONFIGURATION, CAPABILITY_REGISTRY, EXECUTION_RESULT, MEMORY, CONTINUITY_RECONSTRUCTION, ENVIRONMENT_OBSERVATION, RESEARCH_RESULT, SENSOR_OBSERVATION]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ConfirmationState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:195`
- **Code:** `enum class ConfirmationState {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ConfirmationState` with 3 member(s) [UNCONFIRMED, PARTIALLY_CONFIRMED, CONFIRMED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Sensitivity`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:208`
- **Code:** `enum class Sensitivity {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Sensitivity` with 3 member(s) [NONE, LOW, HIGH]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ExpectedChangeRate`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:216`
- **Code:** `enum class ExpectedChangeRate {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ExpectedChangeRate` with 3 member(s) [STABLE, SLOW, MODERATE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `UpdateClassification`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:245`
- **Code:** `enum class UpdateClassification {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class UpdateClassification` with 5 member(s) [CONFIRMS, CONTRADICTS, SUPERSEDES, ADDS, TOO_WEAK]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `PersistResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelPersistence.kt:712`
- **Code:** `sealed class PersistResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class PersistResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SelfLimitationType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/SelfModel.kt:107`
- **Code:** `enum class SelfLimitationType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SelfLimitationType` with 4 member(s) [UNAVAILABLE_MODEL, UNAVAILABLE_ENVIRONMENT, MISSING_DEPENDENCY, UNSUPPORTED_OPERATION]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SelfResourceKind`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/SelfModel.kt:123`
- **Code:** `enum class SelfResourceKind {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SelfResourceKind` with 5 member(s) [COMPUTE, MEMORY, STORAGE, MODEL, ENVIRONMENT]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `WorldEntityType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/WorldModel.kt:56`
- **Code:** `enum class WorldEntityType(val id: String) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class WorldEntityType` with 16 member(s) [PERSON, DEVICE, APPLICATION, FILE, FOLDER, PROJECT, TASK, CAPABILITY, ENVIRONMENT, MODEL, ORGANIZATION, LOCATION, CONCEPT, ARTIFACT, EXTERNAL_SERVICE, UNKNOWN]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `AttributeValue`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/WorldModel.kt:81`
- **Code:** `sealed class AttributeValue {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class AttributeValue` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `WorldEntityState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/WorldModel.kt:98`
- **Code:** `enum class WorldEntityState {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class WorldEntityState` with 3 member(s) [CURRENT, PREVIOUS, EXPECTED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `WorldRelationshipType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/WorldModel.kt:153`
- **Code:** `enum class WorldRelationshipType(val id: String) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class WorldRelationshipType` with 14 member(s) [OWNS, USES, CONTAINS, DEPENDS_ON, PART_OF, LOCATED_IN, RELATED_TO, CREATED_BY, CONTROLLED_BY, RUNS_ON, AVAILABLE_IN, REQUIRES, PRODUCES, DERIVED_FROM]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ConstraintKind`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/DecisionEngine.kt:32`
- **Code:** `enum class ConstraintKind { RESOURCE, CAPABILITY, TAG }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ConstraintKind` with 2 member(s) [RESOURCE, CAPABILITY]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ReplanStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/PlanGraph.kt:106`
- **Code:** `enum class ReplanStatus { REQUESTED, IN_PROGRESS, COMPLETED, ABANDONED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ReplanStatus` with 3 member(s) [REQUESTED, IN_PROGRESS, COMPLETED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `AlertLevel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/AlertLevel.kt:13`
- **Code:** `enum class AlertLevel(val rank: Int) { NONE(0), WARNING(1), CRITICAL(2), OFFLINE(3) }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class AlertLevel` with 3 member(s) [NONE, WARNING, CRITICAL]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `CurrentAction`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/CompanionSignal.kt:76`
- **Code:** `enum class CurrentAction { IDLE, RECEIVING_INPUT, TOOL_CALL, GENERATING, DONE }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class CurrentAction` with 4 member(s) [IDLE, RECEIVING_INPUT, TOOL_CALL, GENERATING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `PresenceMode`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/CompanionState.kt:43`
- **Code:** `enum class PresenceMode { ASLEEP, WAKING, IDLE, LISTENING, THINKING, SPEAKING, SHUTTING_DOWN }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class PresenceMode` with 6 member(s) [ASLEEP, WAKING, IDLE, LISTENING, THINKING, SPEAKING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `AttentionTarget`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/CompanionState.kt:46`
- **Code:** `enum class AttentionTarget { USER, NONE, NOTIFICATION, SELF_TASK }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class AttentionTarget` with 3 member(s) [USER, NONE, NOTIFICATION]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `InterruptFlag`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/CompanionState.kt:49`
- **Code:** `enum class InterruptFlag { INCOMING_CALL, NEW_USER_INPUT, WAKE_WORD, URGENT_NOTIFICATION, LOW_RESOURCE }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class InterruptFlag` with 4 member(s) [INCOMING_CALL, NEW_USER_INPUT, WAKE_WORD, URGENT_NOTIFICATION]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `VisualState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/RenderIntent.kt:29`
- **Code:** `enum class VisualState {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class VisualState` with 7 member(s) [IDLE_BREATHE, LISTENING_RIPPLE, THINKING_SWIRL, SPEAKING_PULSE, WAKE_BLOOM, SLEEP_DIM, SHUTDOWN_FADE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `AudioState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/RenderIntent.kt:35`
- **Code:** `enum class AudioState {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class AudioState` with 5 member(s) [NONE, WAKE_EARCON, LISTENING_EARCON, SPEAKING, NOTIFY_LOW]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `GestureId`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/RenderIntent.kt:40`
- **Code:** `enum class GestureId { NOD, TILT_CURIOUS, PULSE_ACKNOWLEDGE, SOFT_BLINK }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class GestureId` with 3 member(s) [NOD, TILT_CURIOUS, PULSE_ACKNOWLEDGE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `RenderPriority`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/RenderIntent.kt:43`
- **Code:** `enum class RenderPriority { LOW, NORMAL, HIGH, URGENT }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class RenderPriority` with 3 member(s) [LOW, NORMAL, HIGH]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EventType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/UserPresenceEvent.kt:21`
- **Code:** `enum class EventType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EventType` with 5 member(s) [USER_PRESENT, USER_ABSENT, USER_LOOKED_AWAY, USER_TAPPED, USER_IDLE_THRESHOLD]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `BridgePhase`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CurrentActionDiscriminator.kt:174`
- **Code:** `enum class BridgePhase { SENDING, OK, ERROR, UNKNOWN }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class BridgePhase` with 3 member(s) [SENDING, OK, ERROR]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SemanticRole`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/identity/VisualIdentity.kt:34`
- **Code:** `enum class SemanticRole {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SemanticRole` with 8 member(s) [IDLE, LISTENING, THINKING, SPEAKING, WAKE, WARNING, CRITICAL, SLEEP]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `OrbClip`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/render/OrbRenderParams.kt:14`
- **Code:** `enum class OrbClip {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class OrbClip` with 8 member(s) [IDLE_BREATHE, LISTENING_RIPPLE, THINKING_SWIRL, SPEAKING_PULSE, WAKE_BLOOM, SLEEP_DIM, SHUTDOWN_FADE, NOTIFICATION_GLOW]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `QualityTier`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:33`
- **Code:** `enum class QualityTier { HIGH, MEDIUM, LOW }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class QualityTier` with 2 member(s) [HIGH, MEDIUM]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SignalKind`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/continuity/ContinuityGate.kt:50`
- **Code:** `enum class SignalKind {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SignalKind` with 5 member(s) [DIALECT, MENTAL_STATE, SOCIAL_RELATIONSHIP, CONFIDENTIALITY, GALAXY_MEMORY]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ModelProviderType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:254`
- **Code:** `enum class ModelProviderType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ModelProviderType` with 5 member(s) [LLAMA_CPP, OLLAMA, REMOTE_JARVIS, CLOUD, HEURISTIC]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ModelSource`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:264`
- **Code:** `sealed interface ModelSource {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface ModelSource` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `MemoryPolicy`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:271`
- **Code:** `enum class MemoryPolicy {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class MemoryPolicy` with 3 member(s) [FULL, SESSION_ONLY, MINIMAL]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `PermissionPolicy`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:279`
- **Code:** `enum class PermissionPolicy {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class PermissionPolicy` with 2 member(s) [FULL, RESTRICTED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ToolCapability`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:286`
- **Code:** `enum class ToolCapability {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ToolCapability` with 10 member(s) [READ_FILE, LIST_DIR, BATTERY_STATUS, SHELL_EXEC, SCREEN_READ, NOTIFICATION_READ, MODEL_SWITCH, ENV_SWITCH, TASK_SUBMIT, APPROVAL_DECIDE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SyncBehavior`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:306`
- **Code:** `enum class SyncBehavior {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SyncBehavior` with 2 member(s) [REAL_TIME, MANUAL]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `LatencyPreference`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:313`
- **Code:** `enum class LatencyPreference {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class LatencyPreference` with 3 member(s) [FAST, BALANCED, THOROUGH]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `OfflineBehavior`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:321`
- **Code:** `enum class OfflineBehavior {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class OfflineBehavior` with 2 member(s) [DEGRADE, FALLBACK]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `UIBehavior`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:328`
- **Code:** `enum class UIBehavior {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class UIBehavior` with 2 member(s) [NORMAL, MINIMAL]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SafetyRestrictions`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:335`
- **Code:** `enum class SafetyRestrictions {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SafetyRestrictions` with 4 member(s) [NONE, MODEL_REMOTE, CLOUD_ONLY, READ_ONLY]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EnvironmentLifecycleEvent`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/LiquidEnvironmentManager.kt:231`
- **Code:** `sealed interface EnvironmentLifecycleEvent {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface EnvironmentLifecycleEvent` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EvolutionState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:331`
- **Code:** `enum class EvolutionState { IDLE, GENERATING, VALIDATING, TESTING, EVALUATING, PROMOTING, DIAGNOSING }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EvolutionState` with 6 member(s) [IDLE, GENERATING, VALIDATING, TESTING, EVALUATING, PROMOTING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Behavior`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/SpecSynthesizer.kt:23`
- **Code:** `sealed interface Behavior {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface Behavior` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `State`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/CircuitBreaker.kt:21`
- **Code:** `enum class State { CLOSED, OPEN, HALF_OPEN }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class State` with 2 member(s) [CLOSED, OPEN]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `FailureSeverity`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:16`
- **Code:** `enum class FailureSeverity(val rank: Int) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class FailureSeverity` with 6 member(s) [INFO, WARNING, RECOVERABLE, DEGRADED, ERROR, CRITICAL]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `FailureCategory`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:37`
- **Code:** `enum class FailureCategory {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class FailureCategory` with 23 member(s) [VOICE_INPUT, WAKE_WORD, STT, LANGUAGE, BRAIN, MODEL, TTS, AUDIO, MEMORY, VOCABULARY, PRESENCE, VISUAL, LATENCY, RESOURCE, LIFECYCLE, PERSISTENCE, DEPENDENCY, CAPABILITY, CONFIGURATION, INTERNAL, ENVIRONMENT, EVOLUTION, SYNCHRONIZATION]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Recoverability`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:67`
- **Code:** `enum class Recoverability {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Recoverability` with 3 member(s) [NONE, RETRYABLE, RECOVERABLE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `RecoveryAction`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:79`
- **Code:** `enum class RecoveryAction {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class RecoveryAction` with 11 member(s) [NONE, RETRY, RETRY_WITH_BACKOFF, FALLBACK, RELOAD, RESET_COMPONENT, REINITIALIZE, DEGRADE, DISABLE_COMPONENT, WAIT_FOR_RECOVERY, USER_ACTION_REQUIRED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `RecoveryResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:94`
- **Code:** `enum class RecoveryResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class RecoveryResult` with 4 member(s) [PENDING, NOT_ATTEMPTED, SUCCESS, FAILED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `FailureStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:103`
- **Code:** `enum class FailureStatus {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class FailureStatus` with 3 member(s) [OPEN, RECOVERING, RECOVERED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SubsystemStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:214`
- **Code:** `enum class SubsystemStatus { HEALTHY, DEGRADED, DOWN, UNKNOWN }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SubsystemStatus` with 3 member(s) [HEALTHY, DEGRADED, DOWN]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `FailureCause`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:273`
- **Code:** `enum class FailureCause {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class FailureCause` with 15 member(s) [TIMEOUT, RESOURCE_EXHAUSTION, MEMORY_PRESSURE, CPU_PRESSURE, THERMAL_PRESSURE, INVALID_INPUT, INVALID_OUTPUT, UNSUPPORTED, DEPENDENCY_FAILURE, PROCESS_FAILURE, NATIVE_FAILURE, IO_FAILURE, STATE_CORRUPTION, PERMISSION_FAILURE, INTERNAL_ERROR]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ContainmentStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:293`
- **Code:** `enum class ContainmentStatus {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ContainmentStatus` with 4 member(s) [HEALTHY, ISOLATED, DEGRADED, UNAVAILABLE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `DegradationLevel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:302`
- **Code:** `enum class DegradationLevel {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class DegradationLevel` with 5 member(s) [FULL, DEGRADED, LIMITED, OFFLINE, UNAVAILABLE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EnvironmentFailure`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:353`
- **Code:** `sealed class EnvironmentFailure {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class EnvironmentFailure` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `FederationState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/federation/Federation.kt:139`
- **Code:** `enum class FederationState { IDLE, RUNNING, COORDINATING, SUSPENDED, ERROR }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class FederationState` with 4 member(s) [IDLE, RUNNING, COORDINATING, SUSPENDED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `FeedbackType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/federation/Federation.kt:162`
- **Code:** `enum class FeedbackType { HEALTH, LATENCY, ERROR, RECOVERY, DEGRADATION, STATE_CHANGE }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class FeedbackType` with 5 member(s) [HEALTH, LATENCY, ERROR, RECOVERY, DEGRADATION]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `IsolationLevel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/genome/GenomeModel.kt:84`
- **Code:** `enum class IsolationLevel {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class IsolationLevel` with 2 member(s) [PROCESS_LOCAL, SEPARATE_PROCESS]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SafetyConstraint`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/genome/GenomeModel.kt:151`
- **Code:** `enum class SafetyConstraint {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SafetyConstraint` with 9 member(s) [NO_NETWORK, NO_CAMERA, NO_LOCATION, NO_CONTACTS, NO_SMS, NO_CALLS, MAX_FILE_SIZE_KB, USER_ACTION_REQUIRED, AUDIT_REQUIRED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `TestType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/genome/GenomeModel.kt:171`
- **Code:** `enum class TestType { UNIT, INTEGRATION, BEHAVIORAL, RESOURCE }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class TestType` with 3 member(s) [UNIT, INTEGRATION, BEHAVIORAL]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `GenomeHealth`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/genome/GenomeModel.kt:182`
- **Code:** `enum class GenomeHealth { UNKNOWN, TESTING, HEALTHY, DEGRADED, REJECTED, DEAD }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class GenomeHealth` with 5 member(s) [UNKNOWN, TESTING, HEALTHY, DEGRADED, REJECTED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `MutationOperator`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/genome/GenomeModel.kt:194`
- **Code:** `enum class MutationOperator {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class MutationOperator` with 10 member(s) [PARAMETER_MUTATION, STRATEGY_REPLACEMENT, COMPONENT_SUBSTITUTION, PIPELINE_MUTATION, ROUTING_MUTATION, ALGORITHM_MUTATION, TOOL_SUBSTITUTION, ENVIRONMENT_MUTATION, OPTIMIZATION_MUTATION, REPAIR_MUTATION]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `HcEvent`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/bus/HcEvent.kt:17`
- **Code:** `sealed interface HcEvent {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface HcEvent` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Mode`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/protocol/PresenceState.kt:25`
- **Code:** `enum class Mode { ACTIVE, AWAY, DEGRADED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Mode` with 2 member(s) [ACTIVE, AWAY]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SystemTier`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/protocol/PresenceState.kt:27`
- **Code:** `enum class SystemTier { FULL, DEGRADED, OFFLINE, UNKNOWN }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SystemTier` with 3 member(s) [FULL, DEGRADED, OFFLINE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `StyledResponse`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/protocol/StyledResponse.kt:18`
- **Code:** `sealed class StyledResponse {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class StyledResponse` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `PreferenceSource`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/store/AdaptationStore.kt:146`
- **Code:** `enum class PreferenceSource { EXPLICIT, INFERRED, DEFAULT }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class PreferenceSource` with 2 member(s) [EXPLICIT, INFERRED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `StoreKind`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/store/StoreKind.kt:20`
- **Code:** `enum class StoreKind(val fileName: String) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class StoreKind` with 10 member(s) [IDENTITY, PERSONALITY, MOOD, RELATIONSHIP, ADAPTATION, DIALOGUE, FAILURES, CONSOLIDATED_MEMORY, SELF_MODEL, USER_MODEL]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EntityType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/identity/WorldModelService.kt:183`
- **Code:** `enum class EntityType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EntityType` with 7 member(s) [USER, PERSON, PLACE, TOPIC, DEVICE, CONCEPT, UNKNOWN]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `BiometricAuthResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/identity/owner/BiometricAuthResult.kt:19`
- **Code:** `sealed class BiometricAuthResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class BiometricAuthResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `BiometricType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/identity/owner/BiometricType.kt:14`
- **Code:** `enum class BiometricType(val authenticatorBitmask: Int, val label: String) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class BiometricType` with 4 member(s) [BIOMETRIC_STRONG, BIOMETRIC_WEAK, DEVICE_CREDENTIAL, UNKNOWN]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `OwnerBindingOutcome`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/identity/owner/OwnerBiometricBinder.kt:64`
- **Code:** `sealed class OwnerBindingOutcome {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class OwnerBindingOutcome` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `InboxTaskStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/inbox/LocalTaskInbox.kt:356`
- **Code:** `enum class InboxTaskStatus {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class InboxTaskStatus` with 5 member(s) [PENDING, RUNNING, WAITING_APPROVAL, COMPLETED, FAILED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `CharType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/language/EgyptianArabicDialectDetector.kt:156`
- **Code:** `private enum class CharType { ARABIC, LATIN, OTHER }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class CharType` with 2 member(s) [ARABIC, LATIN]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Bucket`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/latency/AckSpeech.kt:127`
- **Code:** `enum class Bucket { POSITIVE, NEUTRAL, LOW }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Bucket` with 2 member(s) [POSITIVE, NEUTRAL]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Phase`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/latency/LatencyLayer.kt:57`
- **Code:** `enum class Phase { IDLE, ACKNOWLEDGING, THINKING, THINKING_LONG, REPLYING }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Phase` with 4 member(s) [IDLE, ACKNOWLEDGING, THINKING, THINKING_LONG]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ProvenanceKind`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/memory/provenance/ProvenanceLedger.kt:12`
- **Code:** `enum class ProvenanceKind {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ProvenanceKind` with 5 member(s) [SUMMARY, INDEX_ENTRY, GRAPH_EDGE, CACHE_ENTRY, TRACE_RECORD]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `MicroSystemStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/microsystem/MicroSystemContract.kt:104`
- **Code:** `enum class MicroSystemStatus { UNKNOWN, INITIALIZING, HEALTHY, DEGRADED, FAILING, DISABLED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class MicroSystemStatus` with 5 member(s) [UNKNOWN, INITIALIZING, HEALTHY, DEGRADED, FAILING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `LoadOutcome`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:448`
- **Code:** `private sealed interface LoadOutcome {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface LoadOutcome` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `MessageRole`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/ModelProvider.kt:86`
- **Code:** `enum class MessageRole {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class MessageRole` with 3 member(s) [SYSTEM, USER, ASSISTANT]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `StreamEvent`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/ModelProvider.kt:100`
- **Code:** `sealed interface StreamEvent {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface StreamEvent` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `FinishReason`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/ModelProvider.kt:122`
- **Code:** `enum class FinishReason {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class FinishReason` with 4 member(s) [STOP, LENGTH, TOOL_CALLS, CONTENT_FILTER]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ModelTier`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/ModelTier.kt:16`
- **Code:** `enum class ModelTier {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ModelTier` with 4 member(s) [RESIDENT, VOICE, VISION, ON_DEMAND_REASONING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `OrganRole`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/OrganRole.kt:9`
- **Code:** `enum class OrganRole(val tier: ModelTier) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class OrganRole` with 3 member(s) [RESIDENT, REASONING, VOICE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `AdmissionDecision`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/ResourceGovernor.kt:19`
- **Code:** `sealed interface AdmissionDecision {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface AdmissionDecision` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `NativeState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/adapters/LocalInferenceEngine.kt:53`
- **Code:** `enum class NativeState {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class NativeState` with 7 member(s) [DORMANT, PREWARMING, READY, GENERATING, IDLE, SUSPENDED, UNLOADING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SourceType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/config/ProviderConfig.kt:26`
- **Code:** `enum class SourceType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class SourceType` with 4 member(s) [LOCAL_LLAMA_CPP, LOCAL_OLLAMA, REMOTE_JARVIS, CLOUD]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `AlgorithmSpec`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutant/AlgorithmSpec.kt:20`
- **Code:** `sealed interface AlgorithmSpec {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface AlgorithmSpec` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Strategy`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutant/AlgorithmSpec.kt:36`
- **Code:** `enum class Strategy {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Strategy` with 2 member(s) [DETERMINISTIC, CACHED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SpecOutput`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutant/AlgorithmSpec.kt:94`
- **Code:** `sealed interface SpecOutput {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface SpecOutput` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Expr`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutant/SynthProgram.kt:213`
- **Code:** `sealed interface Expr`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface Expr` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `RuntimeSelection`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutation/EnvironmentFactory.kt:169`
- **Code:** `enum class RuntimeSelection {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class RuntimeSelection` with 5 member(s) [PROCESS_LOCAL, SEPARATE_PROCESS, TERMUX, CONTAINER, REMOTE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EnvironmentLifecycle`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutation/EnvironmentFactory.kt:178`
- **Code:** `enum class EnvironmentLifecycle {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EnvironmentLifecycle` with 2 member(s) [TEMPORARY, PERSISTENT]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EnvironmentStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutation/EnvironmentFactory.kt:184`
- **Code:** `enum class EnvironmentStatus {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EnvironmentStatus` with 5 member(s) [CREATING, READY, RUNNING, PRESERVED, FAILED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EvolutionState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:244`
- **Code:** `enum class EvolutionState { IDLE, GENERATING, SYNTHESIZING, VALIDATING, TESTING, EVALUATING, ERROR }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EvolutionState` with 6 member(s) [IDLE, GENERATING, SYNTHESIZING, VALIDATING, TESTING, EVALUATING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `NervousState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:82`
- **Code:** `enum class NervousState { DORMANT, ACTIVE, DEGRADED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class NervousState` with 2 member(s) [DORMANT, ACTIVE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `OrganismPhase`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/organism/OrganismLifecycle.kt:86`
- **Code:** `enum class OrganismPhase {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class OrganismPhase` with 5 member(s) [DORMANT, INITIALIZING, ACTIVE, SUSPENDED, DEGRADED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Tier`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/policy/InferencePolicyLayer.kt:30`
- **Code:** `enum class Tier {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Tier` with 3 member(s) [MICRO, NORMAL, COMPLEX]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ResearchDomain`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/research/ResearchModel.kt:6`
- **Code:** `enum class ResearchDomain {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ResearchDomain` with 21 member(s) [BIOLOGY, NEUROSCIENCE, ANIMALS, INSECTS, CELLULAR_SYSTEMS, IMMUNE_SYSTEMS, EVOLUTION, ECOLOGY, SWARM_INTELLIGENCE, ROBOTICS, CONTROL_THEORY, OPERATING_SYSTEMS, DATABASES, NETWORKING, MATHEMATICS, OPTIMIZATION, INFORMATION_THEORY, DISTRIBUTED_SYSTEMS, ECONOMICS, LOGISTICS, ENGINEERING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Complexity`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/research/ResearchModel.kt:44`
- **Code:** `enum class Complexity { SIMPLE, MODERATE, COMPLEX, FUNDAMENTAL }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Complexity` with 3 member(s) [SIMPLE, MODERATE, COMPLEX]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ResearchState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/research/ResearchPipeline.kt:138`
- **Code:** `enum class ResearchState { IDLE, SEARCHING, EXTRACTING, SIMULATING, BENCHMARKING }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ResearchState` with 4 member(s) [IDLE, SEARCHING, EXTRACTING, SIMULATING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `CandidateSource`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/research/universal/MechanismCandidate.kt:29`
- **Code:** `enum class CandidateSource {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class CandidateSource` with 2 member(s) [CLOUD_REASONING, FABRICATOR]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EvidenceStatus`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/research/universal/ResearchEvidence.kt:14`
- **Code:** `enum class EvidenceStatus {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EvidenceStatus` with 3 member(s) [SOURCE_FACT, ABSTRACTION, HYPOTHESIS]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ResolveOutcome`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/resolution/FuzzyCommandResolver.kt:50`
- **Code:** `sealed class ResolveOutcome {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class ResolveOutcome` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `UnresolvedReason`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/resolution/FuzzyCommandResolver.kt:64`
- **Code:** `enum class UnresolvedReason {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class UnresolvedReason` with 2 member(s) [NO_CANDIDATES, NO_ROUTABLE_MATCH]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ConnectionType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/runtime/RuntimeBinder.kt:112`
- **Code:** `enum class ConnectionType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ConnectionType` with 2 member(s) [LOCAL, REMOTE_JARVIS]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `RuntimeHealth`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/runtime/RuntimeBinder.kt:137`
- **Code:** `sealed class RuntimeHealth {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class RuntimeHealth` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ConnectionEvent`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/runtime/RuntimeBinder.kt:157`
- **Code:** `sealed interface ConnectionEvent {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface ConnectionEvent` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `OrganType`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/selfreconfig/SystemGraph.kt:13`
- **Code:** `enum class OrganType {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class OrganType` with 17 member(s) [CORE, COGNITIVE, MEMORY, IDENTITY, MODEL, CAPABILITY, SAFETY, VOICE, ENTRY, TRACE, THREAD, PLANNED, RESOLUTION, CLOUD, RESEARCH, ANDROID, BUILDER]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `EdgeKind`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/selfreconfig/SystemGraph.kt:53`
- **Code:** `enum class EdgeKind {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class EdgeKind` with 3 member(s) [DEPENDS_ON, SENDS_TO, READS_FROM]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Outcome`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:77`
- **Code:** `enum class Outcome {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Outcome` with 5 member(s) [NOTHING_TO_REPAIR, VALIDATION_FAILED, APPLIED_PENDING_VERIFICATION, PROMOTED, ROLLED_BACK]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `MessageRole`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/session/SessionManager.kt:296`
- **Code:** `enum class MessageRole {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class MessageRole` with 3 member(s) [USER, ASSISTANT, SYSTEM]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ProfileCategory`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/social/PersonProfile.kt:35`
- **Code:** `enum class ProfileCategory(val tag: String) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ProfileCategory` with 2 member(s) [BEHAVIOR, PREFERENCE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `StatementConfidence`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/social/PersonProfile.kt:50`
- **Code:** `enum class StatementConfidence {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class StatementConfidence` with 2 member(s) [EXPLICIT, INFERRED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `TrustTier`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/social/RelationshipState.kt:27`
- **Code:** `enum class TrustTier {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class TrustTier` with 4 member(s) [STRANGER, ACQUAINTANCE, KNOWN, TRUSTED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `RelationshipStage`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/social/RelationshipState.kt:44`
- **Code:** `enum class RelationshipStage {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class RelationshipStage` with 4 member(s) [NEW, DEVELOPING, ESTABLISHED, STRAINED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `TTSEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:27`
- **Code:** `sealed interface TTSEngine {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface TTSEngine` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Quality`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:36`
- **Code:** `enum class Quality { LOW, MEDIUM, HIGH, BEST }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Quality` with 3 member(s) [LOW, MEDIUM, HIGH]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `SpeakResult`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:83`
- **Code:** `sealed interface SpeakResult {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface SpeakResult` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `State`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/vad/SileroVadManager.kt:49`
- **Code:** `enum class State {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class State` with 4 member(s) [IDLE, LISTENING, SPEECH, SILENCE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Profile`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/vad/SileroVadManager.kt:58`
- **Code:** `enum class Profile {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Profile` with 5 member(s) [QUIET, NORMAL, NOISY, VEHICLE, OUTDOOR]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `GateVerdict`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/validation/PromotionGate.kt:60`
- **Code:** `enum class GateVerdict { PROMOTE, MUTATE, REJECT }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class GateVerdict` with 2 member(s) [PROMOTE, MUTATE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `AnimationPath`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/vf/animation/AnimationEngine.kt:49`
- **Code:** `sealed interface AnimationPath {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface AnimationPath` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ParticleDirection`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/vf/particle/ParticleEngine.kt:70`
- **Code:** `enum class ParticleDirection {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ParticleDirection` with 4 member(s) [INWARD, OUTWARD, DRIFT_OUT, SPIRAL_IN]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ReactorSpecState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/vf/reactor/ReactorEngine.kt:25`
- **Code:** `enum class ReactorSpecState(val color: JvReactorStateColor) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ReactorSpecState` with 12 member(s) [IDLE, LISTENING, THINKING, RESEARCH, PLANNING, CODING, BUILDING, LEARNING, WARNING, CRITICAL, SLEEPING, OFFLINE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `PulseMode`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/vf/reactor/ReactorEngine.kt:67`
- **Code:** `enum class PulseMode { NONE, BREATHING, AUDIO, SHARP_1HZ, SHARP_2HZ }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class PulseMode` with 4 member(s) [NONE, BREATHING, AUDIO, SHARP_1HZ]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `JvColorFamily`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:140`
- **Code:** `enum class JvColorFamily(val members: Set<JvReactorStateColor>) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class JvColorFamily` with 7 member(s) [BLUE_NOMINAL, TEAL_INPUT, AMBER_THINKING, NEAR_WHITE_BUILDING, RED_ALERT, VIOLET_SLEEP, STEEL_OFFLINE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `JvReactorStateColor`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:159`
- **Code:** `enum class JvReactorStateColor(val hex: Long) {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class JvReactorStateColor` with 12 member(s) [IDLE, LISTENING, THINKING, RESEARCH, PLANNING, CODING, BUILDING, LEARNING, WARNING, CRITICAL, SLEEPING, OFFLINE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `HotwordAvailability`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/HotwordAvailabilityProbe.kt:16`
- **Code:** `enum class HotwordAvailability {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class HotwordAvailability` with 2 member(s) [SUPPORTED, UNSUPPORTED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `VoiceForgeSynthesisResponse`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceForgeAdapter.kt:47`
- **Code:** `sealed interface VoiceForgeSynthesisResponse {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface VoiceForgeSynthesisResponse` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `VoiceForgeOutcome`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceForgeBackend.kt:35`
- **Code:** `sealed interface VoiceForgeOutcome {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed interface VoiceForgeOutcome` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Kind`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceMetrics.kt:15`
- **Code:** `enum class Kind { SYNTHESIS, MODEL_LOAD, CANCEL, RECOVERY, ERROR }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Kind` with 4 member(s) [SYNTHESIS, MODEL_LOAD, CANCEL, RECOVERY]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ModelState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceModelManager.kt:52`
- **Code:** `enum class ModelState { UNKNOWN, NOT_PRESENT, DOWNLOADING, READY, ACTIVE, IDLE, FAILED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ModelState` with 6 member(s) [UNKNOWN, NOT_PRESENT, DOWNLOADING, READY, ACTIVE, IDLE]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `State`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOrganismHost.kt:53`
- **Code:** `enum class State { OFFLINE, BINDING, BOUND, REBINDING, DISABLED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class State` with 4 member(s) [OFFLINE, BINDING, BOUND, REBINDING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `VoiceState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOrganismHost.kt:56`
- **Code:** `enum class VoiceState { CREATED, STARTING, READY, BUSY, DEGRADED, FAILED, RESTARTING, STOPPED }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class VoiceState` with 7 member(s) [CREATED, STARTING, READY, BUSY, DEGRADED, FAILED, RESTARTING]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `ResourcePreference`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceProfile.kt:34`
- **Code:** `enum class ResourcePreference { BATTERY, BALANCED, QUALITY }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class ResourcePreference` with 2 member(s) [BATTERY, BALANCED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `VoiceQuality`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProvider.kt:110`
- **Code:** `enum class VoiceQuality { LOW, MEDIUM, HIGH, BEST }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class VoiceQuality` with 3 member(s) [LOW, MEDIUM, HIGH]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### closed_type — `Selection`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProviderSelector.kt:23`
- **Code:** `sealed class Selection {`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `sealed class Selection` with 0 member(s) [(not an enum body)]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

### fixed_classification — `MediaTransportCommand -> {NEXT_TRACK, PAUSE, PLAY}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/android/MediaSessionControlBackend.kt:144`
- **Code:** `private fun commandFor(type: ActionType): MediaTransportCommand? = when (type) {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `private fun commandFor`, declared to return the closed type `MediaTransportCommand`, and it reaches 3 of that type's 3 members [NEXT_TRACK, PAUSE, PLAY] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: NEXT_TRACK -> `ActionType.NEXT_TRACK -> MediaTransportCommand.NEXT_TRACK`; PAUSE -> `ActionType.PAUSE -> MediaTransportCommand.PAUSE`; PLAY -> `ActionType.PLAY -> MediaTransportCommand.PLAY`.

### fixed_classification — `RiskLevel -> {LOW, MEDIUM}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/approval/ApprovalGate.kt:102`
- **Code:** `fun classifyRisk(action: JarvisAction): RiskLevel {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun classifyRisk`, declared to return the closed type `RiskLevel`, and it reaches 2 of that type's 3 members [LOW, MEDIUM] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: LOW -> `action.type == "respond" || action.type == "noop" -> RiskLevel.LOW`; MEDIUM -> `else -> RiskLevel.MEDIUM`.

### fixed_classification — `BodyState -> {ERROR, HEARING, IDLE, INTERRUPTED, LEARNING, LISTENING, RESPONDING, SPEAKING, THINKING, WAKE}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/BodyStateMachine.kt:14`
- **Code:** `fun next(current: BodyState, event: BodyEvent): BodyState = when (event) {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun next`, declared to return the closed type `BodyState`, and it reaches 10 of that type's 12 members [ERROR, HEARING, IDLE, INTERRUPTED, LEARNING, LISTENING, RESPONDING, SPEAKING, THINKING, WAKE] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: ERROR -> `is BodyEvent.ErrorOccurred -> BodyState.ERROR`; HEARING -> `BodyState.LISTENING, BodyState.WAKE -> BodyState.HEARING`; IDLE -> `BodyState.SPEAKING -> BodyState.IDLE`; INTERRUPTED -> `BodyState.RETRIEVING -> BodyState.INTERRUPTED`; LEARNING -> `BodyState.IDLE, BodyState.WAKE, BodyState.LISTENING -> BodyState.LEARNING`; LISTENING -> `BodyState.WAKE, BodyState.IDLE, BodyState.LISTENING -> BodyState.LISTENING`; RESPONDING -> `BodyState.THINKING -> BodyState.RESPONDING`; SPEAKING -> `BodyState.RESPONDING -> BodyState.SPEAKING`; THINKING -> `BodyState.HEARING, BodyState.WAKE, BodyState.IDLE -> BodyState.THINKING`; WAKE -> `BodyState.IDLE, BodyState.USER_ABSENT, BodyState.USER_PRESENT -> BodyState.WAKE`.

### fixed_classification — `AlertLevel -> {CRITICAL, NONE, WARNING}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/VisualStateBridge.kt:60`
- **Code:** `private fun mapFailureToAlert(severity: com.jarvis.app.failure.FailureSeverity): AlertLevel = when {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `private fun mapFailureToAlert`, declared to return the closed type `AlertLevel`, and it reaches 3 of that type's 3 members [CRITICAL, NONE, WARNING] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: CRITICAL -> `severity.rank >= com.jarvis.app.failure.FailureSeverity.ERROR.rank -> AlertLevel.CRITICAL`; NONE -> `else -> AlertLevel.NONE`; WARNING -> `severity.rank >= com.jarvis.app.failure.FailureSeverity.WARNING.rank -> AlertLevel.WARNING`.

### fixed_classification — `PresenceMode -> {ASLEEP, IDLE, LISTENING, SPEAKING, THINKING, WAKING}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/VisualStateBridge.kt:90`
- **Code:** `private fun mapToPresenceMode(bodyState: BodyState): PresenceMode = when (bodyState) {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `private fun mapToPresenceMode`, declared to return the closed type `PresenceMode`, and it reaches 6 of that type's 6 members [ASLEEP, IDLE, LISTENING, SPEAKING, THINKING, WAKING] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: ASLEEP -> `BodyState.USER_ABSENT -> PresenceMode.ASLEEP`; IDLE -> `BodyState.IDLE -> PresenceMode.IDLE`; LISTENING -> `BodyState.LISTENING -> PresenceMode.LISTENING`; SPEAKING -> `BodyState.SPEAKING -> PresenceMode.SPEAKING`; THINKING -> `BodyState.THINKING -> PresenceMode.THINKING`; WAKING -> `BodyState.WAKE -> PresenceMode.WAKING`.

### fixed_classification — `ChangeLayer -> {CORE, FAST, SLOW}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ChangeLayer.kt:88`
- **Code:** `fun forTarget(kind: ClaimKind, target: String?): ChangeLayer {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun forTarget`, declared to return the closed type `ChangeLayer`, and it reaches 3 of that type's 3 members [CORE, FAST, SLOW] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: CORE -> `field.startsWith(IDENTITY_PREFIX) || field.lowercase() in SELF_MODEL_IDENTITY_FIELDS -> CORE`; FAST -> `if (!kind.isIdentity) return FAST`; SLOW -> `SLOW`.

### fixed_classification — `IntentCategory -> {COMMAND, QUESTION, STATEMENT}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1328`
- **Code:** `internal fun parseIntentCategory(raw: String): IntentCategory? {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `internal fun parseIntentCategory`, declared to return the closed type `IntentCategory`, and it reaches 3 of that type's 3 members [COMMAND, QUESTION, STATEMENT] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: COMMAND -> `normalised.contains("command") -> IntentCategory.COMMAND`; QUESTION -> `normalised.contains("question") -> IntentCategory.QUESTION`; STATEMENT -> `normalised.contains("statement") -> IntentCategory.STATEMENT`.

### fixed_classification — `TurnDecision -> {DIRECT_REPLY, PLAN}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1349`
- **Code:** `private fun intentToDecision(category: IntentCategory): TurnDecision = when (category) {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `private fun intentToDecision`, declared to return the closed type `TurnDecision`, and it reaches 2 of that type's 3 members [DIRECT_REPLY, PLAN] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: DIRECT_REPLY -> `IntentCategory.CLARIFICATION_RESPONSE -> TurnDecision.DIRECT_REPLY`; PLAN -> `IntentCategory.COMMAND -> TurnDecision.PLAN`.

### fixed_classification — `FailureCause -> {INTERNAL_ERROR, TIMEOUT, UNSUPPORTED}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityInvoker.kt:198`
- **Code:** `fun failureCauseOf(failure: ActionFailure): FailureCause = when (failure) {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun failureCauseOf`, declared to return the closed type `FailureCause`, and it reaches 3 of that type's 15 members [INTERNAL_ERROR, TIMEOUT, UNSUPPORTED] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: INTERNAL_ERROR -> `is ActionFailure.Error -> FailureCause.INTERNAL_ERROR`; TIMEOUT -> `is ActionFailure.Timeout -> FailureCause.TIMEOUT`; UNSUPPORTED -> `is ActionFailure.Unsupported -> FailureCause.UNSUPPORTED`.

### fixed_classification — `ResourceResponse -> {NORMAL, PAUSE, REDUCE_CONCURRENCY, STOP_OPTIONAL_WORK, UNLOAD_EXPENSIVE_CAPABILITY}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ResourceFailure.kt:28`
- **Code:** `fun respond(state: ResourceState): ResourceResponse {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun respond`, declared to return the closed type `ResourceResponse`, and it reaches 5 of that type's 6 members [NORMAL, PAUSE, REDUCE_CONCURRENCY, STOP_OPTIONAL_WORK, UNLOAD_EXPENSIVE_CAPABILITY] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: NORMAL -> `else -> ResourceResponse.NORMAL`; PAUSE -> `ResourceResponse.PAUSE`; REDUCE_CONCURRENCY -> `ResourceResponse.REDUCE_CONCURRENCY`; STOP_OPTIONAL_WORK -> `ResourceResponse.STOP_OPTIONAL_WORK`; UNLOAD_EXPENSIVE_CAPABILITY -> `ResourceResponse.UNLOAD_EXPENSIVE_CAPABILITY`.

### fixed_classification — `DegradationLevel -> {DEGRADED, FULL, LIMITED, OFFLINE, UNAVAILABLE}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ResourceFailure.kt:48`
- **Code:** `fun degradationLevel(state: ResourceState): DegradationLevel {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun degradationLevel`, declared to return the closed type `DegradationLevel`, and it reaches 5 of that type's 5 members [DEGRADED, FULL, LIMITED, OFFLINE, UNAVAILABLE] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: DEGRADED -> `cpu >= 0.6f || state.batteryLevel < 0.35f || memory >= 0.5f -> DegradationLevel.DEGRADED`; FULL -> `else -> DegradationLevel.FULL`; LIMITED -> `memory >= 0.7f || cpu >= 0.8f -> DegradationLevel.LIMITED`; OFFLINE -> `thermal == ThermalState.HOT || memory >= 0.8f -> DegradationLevel.OFFLINE`; UNAVAILABLE -> `thermal == ThermalState.CRITICAL || memory >= 0.9f -> DegradationLevel.UNAVAILABLE`.

### fixed_classification — `PromotionDecision -> {PROMOTE, REJECT}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryCandidate.kt:136`
- **Code:** `fun getPromotionDecision(promoteThreshold: Float = 0.6f, rejectThreshold: Float = 0.3f): PromotionDecision {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun getPromotionDecision`, declared to return the closed type `PromotionDecision`, and it reaches 2 of that type's 2 members [PROMOTE, REJECT] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: PROMOTE -> `promotionScore >= promoteThreshold -> PromotionDecision.PROMOTE`; REJECT -> `promotionScore < rejectThreshold -> PromotionDecision.REJECT`.

### fixed_classification — `ResolutionMethod -> {DEFERRED, HIGHEST_CONFIDENCE, HIGHEST_RELIABILITY, LAST_WRITER_WINS, USER_INSTRUCTION}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:334`
- **Code:** `private fun determineResolutionMethod(conflict: MemoryConflict): ResolutionMethod {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `private fun determineResolutionMethod`, declared to return the closed type `ResolutionMethod`, and it reaches 5 of that type's 7 members [DEFERRED, HIGHEST_CONFIDENCE, HIGHEST_RELIABILITY, LAST_WRITER_WINS, USER_INSTRUCTION] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: DEFERRED -> `return ResolutionMethod.DEFERRED`; HIGHEST_CONFIDENCE -> `return ResolutionMethod.HIGHEST_CONFIDENCE`; HIGHEST_RELIABILITY -> `return ResolutionMethod.HIGHEST_RELIABILITY`; LAST_WRITER_WINS -> `return ResolutionMethod.LAST_WRITER_WINS`; USER_INSTRUCTION -> `if (aHasUser || bHasUser) return ResolutionMethod.USER_INSTRUCTION`.

### fixed_classification — `ModelFactStatus -> {CONFIRMED, INFERRED, OBSERVED}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelUpdateEngine.kt:197`
- **Code:** `private fun statusForSource(source: ModelEvidenceSource): ModelFactStatus = when (source) {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `private fun statusForSource`, declared to return the closed type `ModelFactStatus`, and it reaches 3 of that type's 5 members [CONFIRMED, INFERRED, OBSERVED] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: CONFIRMED -> `ModelEvidenceSource.EXPLICIT_USER_STATEMENT -> ModelFactStatus.CONFIRMED`; INFERRED -> `ModelEvidenceSource.RESEARCH_RESULT -> ModelFactStatus.INFERRED`; OBSERVED -> `ModelEvidenceSource.SYSTEM_DECLARATION -> ModelFactStatus.OBSERVED`.

### fixed_classification — `SubgoalStatus -> {BLOCKED, COMPLETED, FAILED, IN_PROGRESS, PENDING}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/GoalPlanner.kt:226`
- **Code:** `fun deriveSubgoalStatus(subgoal: Subgoal, nodes: List<PlanNode>): SubgoalStatus {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun deriveSubgoalStatus`, declared to return the closed type `SubgoalStatus`, and it reaches 5 of that type's 6 members [BLOCKED, COMPLETED, FAILED, IN_PROGRESS, PENDING] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: BLOCKED -> `if (subgoalNodes.isEmpty()) return SubgoalStatus.BLOCKED`; COMPLETED -> `subgoalNodes.all { it.isCompleted } -> SubgoalStatus.COMPLETED`; FAILED -> `subgoalNodes.any { it.isFailed } -> SubgoalStatus.FAILED`; IN_PROGRESS -> `subgoalNodes.any { it.isInProgress } -> SubgoalStatus.IN_PROGRESS`; PENDING -> `else -> SubgoalStatus.PENDING`.

### fixed_classification — `GoalStatus -> {ACTIVE, BLOCKED, COMPLETED, CREATED, FAILED}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/GoalPlanner.kt:242`
- **Code:** `fun deriveGoalStatus(goal: Goal, subgoals: List<Subgoal>): GoalStatus {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun deriveGoalStatus`, declared to return the closed type `GoalStatus`, and it reaches 5 of that type's 8 members [ACTIVE, BLOCKED, COMPLETED, CREATED, FAILED] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: ACTIVE -> `else -> GoalStatus.ACTIVE`; BLOCKED -> `subgoals.any { it.status == SubgoalStatus.BLOCKED } -> GoalStatus.BLOCKED`; COMPLETED -> `subgoals.all { it.status == SubgoalStatus.COMPLETED } -> GoalStatus.COMPLETED`; CREATED -> `if (subgoals.isEmpty()) return GoalStatus.CREATED`; FAILED -> `subgoals.any { it.status == SubgoalStatus.FAILED } -> GoalStatus.FAILED`.

### fixed_classification — `PlanStatus -> {BLOCKED, COMPLETED, EXECUTING, FAILED, PAUSED, PENDING}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/GoalPlanner.kt:370`
- **Code:** `private fun derivePlanStatus(graph: PlanGraph): PlanStatus = when {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `private fun derivePlanStatus`, declared to return the closed type `PlanStatus`, and it reaches 6 of that type's 6 members [BLOCKED, COMPLETED, EXECUTING, FAILED, PAUSED, PENDING] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: BLOCKED -> `graph.nodes.any { it.blocked || it.status == PlanStepStatus.BLOCKED } -> PlanStatus.BLOCKED`; COMPLETED -> `graph.isComplete -> PlanStatus.COMPLETED`; EXECUTING -> `graph.nodes.any { it.isInProgress } -> PlanStatus.EXECUTING`; FAILED -> `graph.nodes.any { it.isFailed && it.id != graph.replanning?.originalNodeId } -> PlanStatus.FAILED`; PAUSED -> `graph.nodes.any { it.status == PlanStepStatus.PAUSED } -> PlanStatus.PAUSED`; PENDING -> `else -> PlanStatus.PENDING`.

### fixed_classification — `BridgePhase -> {ERROR, OK, SENDING}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CurrentActionDiscriminator.kt:178`
- **Code:** `fun classify(status: String?): BridgePhase {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun classify`, declared to return the closed type `BridgePhase`, and it reaches 3 of that type's 3 members [ERROR, OK, SENDING] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: ERROR -> `status.contains("parse failed") -> BridgePhase.ERROR`; OK -> `status == "ok (local)" -> BridgePhase.OK`; SENDING -> `status == "sending (local)" || status.startsWith("retrying") -> BridgePhase.SENDING`.

### fixed_classification — `AlertLevel -> {NONE, WARNING}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/HumanCoreIntegration.kt:73`
- **Code:** `fun hcAlertLevel(): AlertLevel = forceAlertOverride ?: when {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun hcAlertLevel`, declared to return the closed type `AlertLevel`, and it reaches 2 of that type's 3 members [NONE, WARNING] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: NONE -> `else -> AlertLevel.NONE`; WARNING -> `lastHcSystemTierName == "DEGRADED" || lastHcPresenceModeName == "DEGRADED" -> AlertLevel.WARNING`.

### fixed_classification — `OrbClip -> {IDLE_BREATHE, LISTENING_RIPPLE, NOTIFICATION_GLOW, SHUTDOWN_FADE, SLEEP_DIM, SPEAKING_PULSE, THINKING_SWIRL, WAKE_BLOOM}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/render/OrbRenderParams.kt:31`
- **Code:** `fun fromVisualState(state: VisualState): OrbClip = when (state) {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun fromVisualState`, declared to return the closed type `OrbClip`, and it reaches 8 of that type's 8 members [IDLE_BREATHE, LISTENING_RIPPLE, NOTIFICATION_GLOW, SHUTDOWN_FADE, SLEEP_DIM, SPEAKING_PULSE, THINKING_SWIRL, WAKE_BLOOM] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: IDLE_BREATHE -> `VisualState.IDLE_BREATHE -> IDLE_BREATHE`; LISTENING_RIPPLE -> `VisualState.LISTENING_RIPPLE -> LISTENING_RIPPLE`; NOTIFICATION_GLOW -> `VisualState.NOTIFICATION_GLOW -> NOTIFICATION_GLOW`; SHUTDOWN_FADE -> `VisualState.SHUTDOWN_FADE -> SHUTDOWN_FADE`; SLEEP_DIM -> `VisualState.SLEEP_DIM -> SLEEP_DIM`; SPEAKING_PULSE -> `VisualState.SPEAKING_PULSE -> SPEAKING_PULSE`; THINKING_SWIRL -> `VisualState.THINKING_SWIRL -> THINKING_SWIRL`; WAKE_BLOOM -> `VisualState.WAKE_BLOOM -> WAKE_BLOOM`.

### fixed_classification — `OrbClip -> {IDLE_BREATHE, LISTENING_RIPPLE, SHUTDOWN_FADE, SLEEP_DIM, SPEAKING_PULSE, THINKING_SWIRL, WAKE_BLOOM}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/render/OrbStateMachine.kt:120`
- **Code:** `private fun clipFor(mode: PresenceMode, alert: AlertLevel): OrbClip = when {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `private fun clipFor`, declared to return the closed type `OrbClip`, and it reaches 7 of that type's 8 members [IDLE_BREATHE, LISTENING_RIPPLE, SHUTDOWN_FADE, SLEEP_DIM, SPEAKING_PULSE, THINKING_SWIRL, WAKE_BLOOM] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: IDLE_BREATHE -> `PresenceMode.IDLE -> OrbClip.IDLE_BREATHE`; LISTENING_RIPPLE -> `PresenceMode.LISTENING -> OrbClip.LISTENING_RIPPLE`; SHUTDOWN_FADE -> `alert == AlertLevel.OFFLINE -> OrbClip.SHUTDOWN_FADE`; SLEEP_DIM -> `PresenceMode.ASLEEP -> OrbClip.SLEEP_DIM`; SPEAKING_PULSE -> `PresenceMode.SPEAKING -> OrbClip.SPEAKING_PULSE`; THINKING_SWIRL -> `PresenceMode.THINKING -> OrbClip.THINKING_SWIRL`; WAKE_BLOOM -> `PresenceMode.WAKING -> OrbClip.WAKE_BLOOM`.

### fixed_classification — `AudioState -> {LISTENING_EARCON, NONE, SPEAKING, WAKE_EARCON}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/render/OrbStateMachine.kt:169`
- **Code:** `private fun audioFor(mode: PresenceMode, alert: AlertLevel): AudioState = when (alert) {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `private fun audioFor`, declared to return the closed type `AudioState`, and it reaches 4 of that type's 5 members [LISTENING_EARCON, NONE, SPEAKING, WAKE_EARCON] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: LISTENING_EARCON -> `PresenceMode.LISTENING -> AudioState.LISTENING_EARCON`; NONE -> `AlertLevel.OFFLINE -> AudioState.NONE`; SPEAKING -> `PresenceMode.SPEAKING -> AudioState.SPEAKING`; WAKE_EARCON -> `PresenceMode.WAKING -> AudioState.WAKE_EARCON`.

### fixed_classification — `RecoveryAction -> {RETRY, RETRY_WITH_BACKOFF}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/RecoveryController.kt:185`
- **Code:** `private fun specActionOf(spec: RetrySpec): RecoveryAction = when (spec.backoffMs) {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `private fun specActionOf`, declared to return the closed type `RecoveryAction`, and it reaches 2 of that type's 11 members [RETRY, RETRY_WITH_BACKOFF] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: RETRY -> `0L -> RecoveryAction.RETRY`; RETRY_WITH_BACKOFF -> `else -> RecoveryAction.RETRY_WITH_BACKOFF`.

### fixed_classification — `BiometricType -> {BIOMETRIC_STRONG, BIOMETRIC_WEAK, DEVICE_CREDENTIAL, UNKNOWN}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/identity/owner/BiometricType.kt:34`
- **Code:** `fun fromBitmask(bitmask: Int): BiometricType = when {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun fromBitmask`, declared to return the closed type `BiometricType`, and it reaches 4 of that type's 4 members [BIOMETRIC_STRONG, BIOMETRIC_WEAK, DEVICE_CREDENTIAL, UNKNOWN] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: BIOMETRIC_STRONG -> `bitmask and 0x0F != 0 -> BIOMETRIC_STRONG`; BIOMETRIC_WEAK -> `bitmask and 0xFF != 0 -> BIOMETRIC_WEAK`; DEVICE_CREDENTIAL -> `bitmask and 0x8000 != 0 -> DEVICE_CREDENTIAL`; UNKNOWN -> `else -> UNKNOWN`.

### fixed_classification — `Bucket -> {NEUTRAL, POSITIVE}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/latency/AckSpeech.kt:138`
- **Code:** `fun bucketFor(valence: Double?): Bucket = when {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun bucketFor`, declared to return the closed type `Bucket`, and it reaches 2 of that type's 2 members [NEUTRAL, POSITIVE] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: NEUTRAL -> `valence == null -> Bucket.NEUTRAL`; POSITIVE -> `valence >= 0.2 -> Bucket.POSITIVE`.

### fixed_classification — `OrganRole -> {REASONING, RESIDENT}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:273`
- **Code:** `private fun roleForProvider(type: ModelProviderType): OrganRole = when (type) {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `private fun roleForProvider`, declared to return the closed type `OrganRole`, and it reaches 2 of that type's 3 members [REASONING, RESIDENT] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: REASONING -> `else -> OrganRole.REASONING`; RESIDENT -> `ModelProviderType.HEURISTIC, ModelProviderType.NONE, ModelProviderType.CLOUD -> OrganRole.RESIDENT`.

### fixed_classification — `Tier -> {COMPLEX, MICRO, NORMAL}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/policy/InferencePolicyLayer.kt:162`
- **Code:** `private fun classify(features: ClassificationFeatures, context: DecisionContext): Tier {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `private fun classify`, declared to return the closed type `Tier`, and it reaches 3 of that type's 3 members [COMPLEX, MICRO, NORMAL] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: COMPLEX -> `return Tier.COMPLEX`; MICRO -> `return Tier.MICRO`; NORMAL -> `return Tier.NORMAL`.

### fixed_classification — `ParticleDirection -> {DRIFT_OUT, INWARD, OUTWARD, SPIRAL_IN}`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/vf/particle/ParticleEngine.kt:54`
- **Code:** `fun stateDirection(state: JvReactorStateColor): ParticleDirection = when (state) {`
- **Section 13 question this answers Yes to:** “Did this turn an emergent, per-task behavior into a fixed function with a fixed role?”
- **Vision section:** 4
- **Why:** a story added `fun stateDirection`, declared to return the closed type `ParticleDirection`, and it reaches 4 of that type's 4 members [DRIFT_OUT, INWARD, OUTWARD, SPIRAL_IN] as written-out results rather than as something the input earned. The set of roles was fixed when the function was written. Section 4: the architecture is allowed to become whatever actually produces the outcome; a fixed classification reserves it in advance. Each outcome is reached at: DRIFT_OUT -> `JvReactorStateColor.LISTENING -> ParticleDirection.DRIFT_OUT`; INWARD -> `JvReactorStateColor.THINKING -> ParticleDirection.INWARD`; OUTWARD -> `JvReactorStateColor.BUILDING -> ParticleDirection.OUTWARD`; SPIRAL_IN -> `JvReactorStateColor.LEARNING -> ParticleDirection.SPIRAL_IN`.

### flat_threshold — `MAX_CONTEXT_TURNS`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:132`
- **Code:** `const val MAX_CONTEXT_TURNS = 10`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `MAX_CONTEXT_TURNS = None` and it gates an evidence COUNT at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/body/MemoryStore.kt:150 `val trimmed = if (updated.size > MAX_CONTEXT_TURNS * 2) {`; mobile/app/src/main/java/com/jarvis/app/body/MemoryStore.kt:157 `while (contextOrder.size > MAX_CONTEXT_TURNS * 2) {`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### flat_threshold — `MAX_EPISODIC`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/MemoryStore.kt:20`
- **Code:** `private const val MAX_EPISODIC = 500`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `MAX_EPISODIC = None` and it gates an evidence COUNT at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/body/MemoryStore.kt:244 `if (episodicMemories.size > MAX_EPISODIC) {`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### flat_threshold — `MAX_CONTEXT_TURNS`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/MemoryStore.kt:22`
- **Code:** `private const val MAX_CONTEXT_TURNS = 20`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `MAX_CONTEXT_TURNS = None` and it gates an evidence COUNT at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/body/MemoryStore.kt:150 `val trimmed = if (updated.size > MAX_CONTEXT_TURNS * 2) {`; mobile/app/src/main/java/com/jarvis/app/body/MemoryStore.kt:157 `while (contextOrder.size > MAX_CONTEXT_TURNS * 2) {`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### flat_threshold — `PRESENCE_CONFIRMATION_MS`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/PresenceMonitor.kt:36`
- **Code:** `private const val PRESENCE_CONFIRMATION_MS = 2000L`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `PRESENCE_CONFIRMATION_MS = None` and it gates a real-time INTERVAL at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/body/PresenceMonitor.kt:139 `val recentInteraction = timeSinceInteraction < PRESENCE_CONFIRMATION_MS`; mobile/app/src/main/java/com/jarvis/app/body/PresenceMonitor.kt:145 `if (now - confirmationTimer >= PRESENCE_CONFIRMATION_MS) {`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### flat_threshold — `ABSENCE_CONFIRMATION_MS`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/PresenceMonitor.kt:37`
- **Code:** `private const val ABSENCE_CONFIRMATION_MS = 10000L`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `ABSENCE_CONFIRMATION_MS = None` and it gates a real-time INTERVAL at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/body/PresenceMonitor.kt:151 `if (now - confirmationTimer >= ABSENCE_CONFIRMATION_MS) {`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### flat_threshold — `MAX_CACHE_SIZE`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/StreamingTts.kt:30`
- **Code:** `private const val MAX_CACHE_SIZE = 50`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `MAX_CACHE_SIZE = None` and it gates an evidence COUNT at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/body/StreamingTts.kt:418 `if (synthesisCache.size > MAX_CACHE_SIZE) {`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### flat_threshold — `DEFAULT_MIN_EVIDENCE_CLAIMS`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ContinuityLaw.kt:221`
- **Code:** `const val DEFAULT_MIN_EVIDENCE_CLAIMS: Int = 2`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `DEFAULT_MIN_EVIDENCE_CLAIMS = Int` and it gates an evidence COUNT and a real-time INTERVAL at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ContinuityLaw.kt:125 `if (evidenceIds.size < minEvidenceClaims) {`; mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ContinuityLaw.kt:43 `require(minEvidenceClaims >= 2) {`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### flat_threshold — `DEFAULT_MIN_INTERVAL_MS`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ContinuityLaw.kt:228`
- **Code:** `const val DEFAULT_MIN_INTERVAL_MS: Long = 5_000L`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `DEFAULT_MIN_INTERVAL_MS = Long` and it gates a real-time INTERVAL at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ContinuityLaw.kt:46 `require(minIntervalMs > 0L) {`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### flat_threshold — `DEFAULT_MIN_DWELL_MS`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CurrentActionDiscriminator.kt:163`
- **Code:** `const val DEFAULT_MIN_DWELL_MS: Long = 1_500L`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `DEFAULT_MIN_DWELL_MS = Long` and it gates a real-time INTERVAL at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CurrentActionDiscriminator.kt:81 `if (t - enteredAtMs >= minDwellMs) {`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### flat_threshold — `CONDITIONS_EVAL_INTERVAL_TICKS`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:268`
- **Code:** `const val CONDITIONS_EVAL_INTERVAL_TICKS = 30`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `CONDITIONS_EVAL_INTERVAL_TICKS = None` and it gates a real-time INTERVAL at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:186 `if (tickCounter >= CONDITIONS_EVAL_INTERVAL_TICKS) {`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### flat_threshold — `AUDIT_LIMIT`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/bus/StateBus.kt:26`
- **Code:** `private const val AUDIT_LIMIT = 200`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `AUDIT_LIMIT = None` and it gates an evidence COUNT at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/humancore/bus/StateBus.kt:41 `while (audit.size > AUDIT_LIMIT) audit.removeFirst()`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### flat_threshold — `HISTORY_LIMIT`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/store/PersonalityStore.kt:216`
- **Code:** `private const val HISTORY_LIMIT = 40`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `HISTORY_LIMIT = None` and it gates an evidence COUNT at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/humancore/store/PersonalityStore.kt:121 `)).let { h -> if (h.size > HISTORY_LIMIT) h.takeLast(HISTORY_LIMIT) else h }`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### flat_threshold — `PRIME_COOLDOWN_MS`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/latency/WarmupEngine.kt:29`
- **Code:** `private const val PRIME_COOLDOWN_MS = 30_000L`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 3
- **Why:** a story added `PRIME_COOLDOWN_MS = None` and it gates a real-time INTERVAL at one fixed value for every field, every value and every moment, however established the value being challenged already is. Gating sites: mobile/app/src/main/java/com/jarvis/app/latency/WarmupEngine.kt:64 `if (now - lastPrimeMs < PRIME_COOLDOWN_MS) return`. Section 3: a mechanism that cannot itself develop on evidence is storage, not development. Section 4: the rate limit on a value should be a consequence of what the value has proved, not a number written down once.
- **Remediation available:** `evidence-scaled-threshold`

### meaning_arbiter — `PronounResolver`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/PronounResolver.kt:12`
- **Code:** `class PronounResolver(`
- **Section 13 question this answers Yes to:** “Did this assign one component one permanent job?”
- **Vision section:** 4
- **Why:** a story introduced `PronounResolver`, a type whose own name declares the role of settling or choosing, and it returns the MEANING type(s) List<IntentInference rather than a resource decision. Section 4: a mechanism that routes attention, allocates compute or settles competing interpretations is doing resource scheduling; if disabling it would change what she thinks, that mechanism has become the brain, which is a failure of the design rather than a feature.

### meaning_arbiter — `MemoryConflictResolver`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:218`
- **Code:** `class MemoryConflictResolver(`
- **Section 13 question this answers Yes to:** “Did this assign one component one permanent job?”
- **Vision section:** 4
- **Why:** a story introduced `MemoryConflictResolver`, a type whose own name declares the role of settling or choosing, and it returns the MEANING type(s) String rather than a resource decision. Section 4: a mechanism that routes attention, allocates compute or settles competing interpretations is doing resource scheduling; if disabling it would change what she thinks, that mechanism has become the brain, which is a failure of the design rather than a feature.

### meaning_arbiter — `MemoryPolicy`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:271`
- **Code:** `enum class MemoryPolicy {`
- **Section 13 question this answers Yes to:** “Did this assign one component one permanent job?”
- **Vision section:** 4
- **Why:** a story introduced `MemoryPolicy`, a type whose own name declares the role of settling or choosing, and it returns the MEANING type(s) String rather than a resource decision. Section 4: a mechanism that routes attention, allocates compute or settles competing interpretations is doing resource scheduling; if disabling it would change what she thinks, that mechanism has become the brain, which is a failure of the design rather than a feature.

### meaning_arbiter — `PermissionPolicy`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:279`
- **Code:** `enum class PermissionPolicy {`
- **Section 13 question this answers Yes to:** “Did this assign one component one permanent job?”
- **Vision section:** 4
- **Why:** a story introduced `PermissionPolicy`, a type whose own name declares the role of settling or choosing, and it returns the MEANING type(s) String rather than a resource decision. Section 4: a mechanism that routes attention, allocates compute or settles competing interpretations is doing resource scheduling; if disabling it would change what she thinks, that mechanism has become the brain, which is a failure of the design rather than a feature.

### meaning_arbiter — `ConflictResolver`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/algo/ConflictResolver.kt:30`
- **Code:** `object ConflictResolver {`
- **Section 13 question this answers Yes to:** “Did this assign one component one permanent job?”
- **Vision section:** 4
- **Why:** a story introduced `ConflictResolver`, a type whose own name declares the role of settling or choosing, and it returns the MEANING type(s) String rather than a resource decision. Section 4: a mechanism that routes attention, allocates compute or settles competing interpretations is doing resource scheduling; if disabling it would change what she thinks, that mechanism has become the brain, which is a failure of the design rather than a feature.

### meaning_arbiter — `ModelPathResolver`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/storage/ModelPathResolver.kt:22`
- **Code:** `class ModelPathResolver(`
- **Section 13 question this answers Yes to:** “Did this assign one component one permanent job?”
- **Vision section:** 4
- **Why:** a story introduced `ModelPathResolver`, a type whose own name declares the role of settling or choosing, and it returns the MEANING type(s) String rather than a resource decision. Section 4: a mechanism that routes attention, allocates compute or settles competing interpretations is doing resource scheduling; if disabling it would change what she thinks, that mechanism has become the brain, which is a failure of the design rather than a feature.

### named_wire — `fileStorage: FileStorage`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/alerts/LocalAlertStore.kt:23`
- **Code:** `fileStorage.append(alertsFile, alert.toJson().toString())`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `fileStorage: FileStorage` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/FileStorage.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/alerts/LocalAlertStore.kt:155. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `modelManager: ModelManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/anchor/AnchorEngine.kt:34`
- **Code:** `fun anchorHeld(): Boolean = modelManager.isTierLoaded(ModelTier.RESIDENT)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `modelManager: ModelManager` (declared at mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/anchor/AnchorEngine.kt:39. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `gateway: CognitiveAdmissionPolicy`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/anchor/AnchorEngine.kt:35`
- **Code:** `val admitted = gateway.serveTurn(uncertainty) {`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `gateway: CognitiveAdmissionPolicy` (declared at mobile/app/src/main/java/com/jarvis/app/model/CognitiveAdmissionPolicy.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/anchor/AnchorEngine.kt:63. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `fileStorage: FileStorage`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/approvals/LocalApprovalQueue.kt:23`
- **Code:** `fileStorage.append(approvalsFile, approval.toJson().toString())`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `fileStorage: FileStorage` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/FileStorage.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/approvals/LocalApprovalQueue.kt:199. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `memoryStore: MemoryStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt:20`
- **Code:** `val memories = memoryStore.queryMemories(currentTurn, limit = 20)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `memoryStore: MemoryStore` (declared at mobile/app/src/main/java/com/jarvis/app/body/MemoryStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt:197. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `vocabularyStore: VocabularyStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt:21`
- **Code:** `val vocabEntries = vocabularyStore.getEntriesForLanguage(language)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `vocabularyStore: VocabularyStore` (declared at mobile/app/src/main/java/com/jarvis/app/body/VocabularyStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt:239. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `localInferenceEngine: LocalInferenceEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt:22`
- **Code:** `val tokenCount = localInferenceEngine.countTokens(prompt)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `localInferenceEngine: LocalInferenceEngine` (declared at mobile/app/src/main/java/com/jarvis/app/model/adapters/LocalInferenceEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt:122. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `modelManager: ModelManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/BodyCoordinator.kt:53`
- **Code:** `turnResult.responseText ?: modelManager.sendWithContext(text, memoryItems)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `modelManager: ModelManager` (declared at mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/body/BodyCoordinator.kt:409. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `companionIntegration: HumanCoreIntegration`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/BodyCoordinator.kt:55`
- **Code:** `companionIntegration.setAlertOverride(com.jarvis.app.companioncore.contract.AlertLevel.NONE)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `companionIntegration: HumanCoreIntegration` (declared at mobile/app/src/main/java/com/jarvis/app/companioncore/engine/HumanCoreIntegration.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/body/BodyCoordinator.kt:298. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `memoryStore: MemoryStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/BodyCoordinator.kt:57`
- **Code:** `val items = memoryStore.retrieve(query, context)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `memoryStore: MemoryStore` (declared at mobile/app/src/main/java/com/jarvis/app/body/MemoryStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/body/BodyCoordinator.kt:374. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `memoryStore: MemoryStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/MemoryPromotion.kt:32`
- **Code:** `memoryStore.storePreference(prefKey, prefValue)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `memoryStore: MemoryStore` (declared at mobile/app/src/main/java/com/jarvis/app/body/MemoryStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/body/MemoryPromotion.kt:253. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `vocabularyStore: VocabularyStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/MemoryPromotion.kt:33`
- **Code:** `vocabularyStore.teachWord(word, pronunciation, detectLanguage(candidate.userText))`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `vocabularyStore: VocabularyStore` (declared at mobile/app/src/main/java/com/jarvis/app/body/VocabularyStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/body/MemoryPromotion.kt:238. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `context: Context`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/PresenceMonitor.kt:40`
- **Code:** `sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `context: Context` (declared at mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/body/PresenceMonitor.kt:76. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `languageRouter: LanguageRouter`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/StreamingTts.kt:44`
- **Code:** `val lang = language ?: languageRouter.detectLanguage(text)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `languageRouter: LanguageRouter` (declared at mobile/app/src/main/java/com/jarvis/app/body/LanguageRouter.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/body/StreamingTts.kt:191. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `companionIntegration: HumanCoreIntegration`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/body/VisualStateBridge.kt:25`
- **Code:** `else -> companionIntegration.hcAlertLevel()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `companionIntegration: HumanCoreIntegration` (declared at mobile/app/src/main/java/com/jarvis/app/companioncore/engine/HumanCoreIntegration.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/body/VisualStateBridge.kt:112. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `context: Context`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:25`
- **Code:** `if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `context: Context` (declared at mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:86. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `modelManager: ModelManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:28`
- **Code:** `val health = modelManager.health()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `modelManager: ModelManager` (declared at mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:124. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `runtimeBinder: RuntimeBinder`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:29`
- **Code:** `runtimeBinder.initialize()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `runtimeBinder: RuntimeBinder` (declared at mobile/app/src/main/java/com/jarvis/app/runtime/RuntimeBinder.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:152. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: MicroSystemRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/builder/CapabilityGapDetector.kt:20`
- **Code:** `val live = registry.provides(capability)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: MicroSystemRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/microsystem/MicroSystemRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/builder/CapabilityGapDetector.kt:26. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `genomeRegistry: GenomeRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/builder/CapabilityGapDetector.kt:21`
- **Code:** `val bestGenome = genomeRegistry.bestFor(capability)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `genomeRegistry: GenomeRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/genome/GenomeRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/builder/CapabilityGapDetector.kt:36. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `gapDetector: CapabilityGapDetector`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:34`
- **Code:** `val pre = gapDetector.detect(spec.capability)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `gapDetector: CapabilityGapDetector` (declared at mobile/app/src/main/java/com/jarvis/app/builder/CapabilityGapDetector.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:53. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `research: UniversalResearchEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:35`
- **Code:** `val researchResult = research.research(spec.description)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `research: UniversalResearchEngine` (declared at mobile/app/src/main/java/com/jarvis/app/research/universal/UniversalResearchEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:159. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `evolution: EvolutionEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:36`
- **Code:** `val run = evolution.evolve(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `evolution: EvolutionEngine` (declared at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:103. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: MicroSystemRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:37`
- **Code:** `registry.provides(spec.capability).isNotEmpty()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: MicroSystemRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/microsystem/MicroSystemRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:138. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `failureSurface: FailureSurface`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:41`
- **Code:** `failureSurface.report(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `failureSurface: FailureSurface` (declared at mobile/app/src/main/java/com/jarvis/app/failure/FailureSurface.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:67. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `spec: ToolSpecification`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:166`
- **Code:** `requirement = spec.toRequirement(),`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `spec: ToolSpecification` (declared at mobile/app/src/main/java/com/jarvis/app/builder/ToolSpecification.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:105. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `capabilityRegistry: CapabilityRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityArbitrator.kt:27`
- **Code:** `val bestCapability = capabilityRegistry.findBest(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `capabilityRegistry: CapabilityRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/capability/CapabilityArbitrator.kt:113. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `resourceGovernor: ResourceGovernor`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityArbitrator.kt:28`
- **Code:** `val admission = resourceGovernor.requestAdmission(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `resourceGovernor: ResourceGovernor` (declared at mobile/app/src/main/java/com/jarvis/app/model/ResourceGovernor.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/capability/CapabilityArbitrator.kt:151. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: CapabilityRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/capability/DeterministicCapabilityRouter.kt:21`
- **Code:** `val exact = registry.findBest(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: CapabilityRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/capability/DeterministicCapabilityRouter.kt:25. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `item: AttentionItem`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/AttentionEngine.kt:270`
- **Code:** `val updatedItem = item.copy(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `item: AttentionItem` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/AttentionEngine.kt:78. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `modelManager: ModelManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:60`
- **Code:** `modelManager.wake(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `modelManager: ModelManager` (declared at mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:389. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `goalPlanner: GoalPlanner`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:62`
- **Code:** `val result = goalPlanner.createPlan(goal)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `goalPlanner: GoalPlanner` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/planning/GoalPlanner.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1366. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `decisionEngine: DecisionEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:63`
- **Code:** `val result = decisionEngine.decide(enriched)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `decisionEngine: DecisionEngine` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/planning/DecisionEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1431. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `failure: FailureEvent`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1626`
- **Code:** `outcome.result.failure != null -> outcome.result.failure.toString()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `failure: FailureEvent` (declared at mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1229. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `topicTracker: TopicTracker`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/ContextWindowAssembler.kt:34`
- **Code:** `val allSegmentTurns = topicTracker.currentSegmentTurns()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `topicTracker: TopicTracker` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/TopicTracker.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/ContextWindowAssembler.kt:125. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `salienceScorer: SalienceScorer`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/ContextWindowAssembler.kt:35`
- **Code:** `val topEntities = salienceScorer.topSalient(maxEntities, currentTurnIndex, currentSegmentId)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `salienceScorer: SalienceScorer` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/SalienceScorer.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/ContextWindowAssembler.kt:130. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `blendedRetriever: BlendedMemoryRetriever`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/ContextWindowAssembler.kt:45`
- **Code:** `val retrieved = blendedRetriever.retrieve(currentTurnText, now = System.currentTimeMillis())`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `blendedRetriever: BlendedMemoryRetriever` (declared at mobile/app/src/main/java/com/jarvis/app/memory/BlendedMemoryRetriever.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/ContextWindowAssembler.kt:134. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `referenceStore: ReferenceStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/PronounResolver.kt:13`
- **Code:** `for (mention in referenceStore.all()) {`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `referenceStore: ReferenceStore` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/ReferenceStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/PronounResolver.kt:72. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `salienceScorer: SalienceScorer`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/PronounResolver.kt:15`
- **Code:** `salienceScorer.score(entity, resolveTurnIndex, resolveSegmentId)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `salienceScorer: SalienceScorer` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/SalienceScorer.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/PronounResolver.kt:83. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `referenceStore: ReferenceStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/SalienceScorer.kt:13`
- **Code:** `val mentions = referenceStore.all().filter { it.entity == entity }`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `referenceStore: ReferenceStore` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/ReferenceStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/SalienceScorer.kt:44. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `state: ExecutionState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityExecutor.kt:54`
- **Code:** `state = state.copy(status = ExecutionStatus.FAILED, failureCause = "plan has no steps")`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `state: ExecutionState` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/execution/ExecutionState.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityExecutor.kt:78. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `verdict: PartialFailureVerdict`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityExecutor.kt:385`
- **Code:** `val refreshed = verdict.copy(alternativeCapabilityExists = true)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `verdict: PartialFailureVerdict` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/immune/PartialFailure.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityExecutor.kt:300. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: CapabilityRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityFabric.kt:26`
- **Code:** `val result = registry.register(capability)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: CapabilityRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityFabric.kt:39. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: CapabilityRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityInvoker.kt:46`
- **Code:** `startedAt, 0, registry.stateOf(capabilityId), refused = true, refusalReason = "circuit open"`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: CapabilityRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityInvoker.kt:120. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `resolver: CapabilityResolver`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityInvoker.kt:47`
- **Code:** `val resolution = resolver.resolve(request, granted)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `resolver: CapabilityResolver` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityResolver.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityInvoker.kt:73. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `immune: ImmuneSystem`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityInvoker.kt:48`
- **Code:** `val circuit = immune.breaker(capabilityId, request.action)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `immune: ImmuneSystem` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityInvoker.kt:115. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `from: CapabilityLifecycle`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityRegistry.kt:16`
- **Code:** `if (!from.canTransitionTo(target)) return TransitionResult.Rejected(from, target)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `from: CapabilityLifecycle` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Capability.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityRegistry.kt:113. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: CapabilityRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityResolver.kt:31`
- **Code:** `val candidates = registry.lookupByOperation(request.action)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: CapabilityRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityResolver.kt:38. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `retryPolicy: RetryPolicy`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/execution/ExecutionEngine.kt:46`
- **Code:** `if (recoverable && retryPolicy.canRetry(attempt)) {`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `retryPolicy: RetryPolicy` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/execution/ExecutionState.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/execution/ExecutionEngine.kt:200. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `surface: FailureSurface`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:39`
- **Code:** `val event = surface.report(report)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `surface: FailureSurface` (declared at mobile/app/src/main/java/com/jarvis/app/failure/FailureSurface.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:64. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `containment: ContainmentRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:40`
- **Code:** `val isolated = containment.setStatus(report.subsystem, ContainmentStatus.ISOLATED)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `containment: ContainmentRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/immune/Containment.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:74. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `degradation: DegradedModeController`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:41`
- **Code:** `degradation.enter(cap, DegradationLevel.DEGRADED)?.let { (_, lvl) ->`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `degradation: DegradedModeController` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/immune/Containment.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:80. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `dependencyGraph: DependencyGraph`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:42`
- **Code:** `val dependents = dependencyGraph.affectedDependents(report.subsystem)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `dependencyGraph: DependencyGraph` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/immune/Containment.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:73. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `memory: ImmuneMemory`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:43`
- **Code:** `memory.record(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `memory: ImmuneMemory` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneMemory.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:215. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `backoffPolicy: BackoffPolicy`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:44`
- **Code:** `val retryable = backoffPolicy.isRetryable(cause)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `backoffPolicy: BackoffPolicy` (declared at mobile/app/src/main/java/com/jarvis/app/failure/BackoffPolicy.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:69. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `consolidator: MemoryConsolidator`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:28`
- **Code:** `val allMemories = consolidator.getAllConsolidated()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `consolidator: MemoryConsolidator` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConsolidator.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:88. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `memory: ConsolidatedMemory`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:481`
- **Code:** `if (memory.isCurrentTruth()) {`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `memory: ConsolidatedMemory` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryProvenance.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:258. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `provenance: MemoryProvenance`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:528`
- **Code:** `provenance = if (config.includeProvenance) memory.provenance.toProvenanceString() else "Provenance not included",`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `provenance: MemoryProvenance` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryProvenance.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:177. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `updateEngine: MemoryUpdateEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:219`
- **Code:** `updateEngine.updateMemory(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `updateEngine: MemoryUpdateEngine` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:493. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `consolidator: MemoryConsolidator`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryContinuityAdapter.kt:27`
- **Code:** `consolidator.submitExperience(experience)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `consolidator: MemoryConsolidator` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConsolidator.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryContinuityAdapter.kt:58. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `continuityManager: ContinuityManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryContinuityAdapter.kt:29`
- **Code:** `continuityManager.reconstructCurrentState(query)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `continuityManager: ContinuityManager` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryContinuityAdapter.kt:128. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `workingMemory: WorkingMemory`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryContinuityAdapter.kt:30`
- **Code:** `workingMemory.insertFromBodyMemory(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `workingMemory: WorkingMemory` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/WorkingMemory.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryContinuityAdapter.kt:189. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `consolidator: MemoryConsolidator`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:18`
- **Code:** `val oldMemory = consolidator.getMemory(oldMemoryId) ?: return SupersessionResult.NOT_FOUND(oldMemoryId)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `consolidator: MemoryConsolidator` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConsolidator.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:47. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `query: ModelQuery`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelContextPort.kt:52`
- **Code:** `selfFacts = query.getRelevantSelfContext(combined, config.maxSelfFacts, config.includeSensitive),`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `query: ModelQuery` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelQuery.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelContextPort.kt:73. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `self: SelfModel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelStore.kt:17`
- **Code:** `ModelDomain.SELF -> self.fact(factKey)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `self: SelfModel` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelStore.kt:28. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `user: UserModel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelStore.kt:20`
- **Code:** `ModelDomain.USER -> user.fact(factKey)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `user: UserModel` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelStore.kt:29. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `world: WorldModel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelStore.kt:23`
- **Code:** `ModelDomain.WORLD -> world.fact(factKey)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `world: WorldModel` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelStore.kt:30. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `engine: ModelUpdateEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelSynchronization.kt:25`
- **Code:** `engine.observe(store, ModelUpdate(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `engine: ModelUpdateEngine` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelUpdateEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelSynchronization.kt:36. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `fact: ModelFact`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelUpdateEngine.kt:428`
- **Code:** `val marked = fact.copy(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `fact: ModelFact` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelUpdateEngine.kt:179. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `confidence: ModelConfidence`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/model/WorldModel.kt:187`
- **Code:** `fun isCurrent(): Boolean = state == WorldEntityState.CURRENT && confidence.isCredible()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `confidence: ModelConfidence` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/model/WorldModel.kt:143. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `resourceState: ResourceState`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/DecisionEngine.kt:61`
- **Code:** `resourcePressureAtDecision = request.resourceState.isUnderPressure().let { if (it) 1f else 0f },`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `resourceState: ResourceState` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/planning/DecisionEngine.kt:129. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `graph: PlanGraph`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/GoalPlanner.kt:55`
- **Code:** `return refreshStatus(graph.copy(nodes = updated))`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `graph: PlanGraph` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/planning/PlanGraph.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/cognitive/planning/GoalPlanner.kt:264. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `discriminator: CurrentActionDiscriminator`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/HumanCoreIntegration.kt:42`
- **Code:** `raw.bridgeStatus?.let { discriminator.onBridgeStatus(it) }`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `discriminator: CurrentActionDiscriminator` (declared at mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CurrentActionDiscriminator.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/companioncore/engine/HumanCoreIntegration.kt:106. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `integration: HumanCoreIntegration`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:50`
- **Code:** `integration.tick()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `integration: HumanCoreIntegration` (declared at mobile/app/src/main/java/com/jarvis/app/companioncore/engine/HumanCoreIntegration.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:146. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `presenceEngine: PresenceEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:51`
- **Code:** `presenceEngine.requestTransition(PresenceMode.WAKING, "CompanionCore", "cold-start")`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `presenceEngine: PresenceEngine` (declared at mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:125. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `orbStateMachine: OrbStateMachine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:52`
- **Code:** `_renderParams.value = orbStateMachine.map(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `orbStateMachine: OrbStateMachine` (declared at mobile/app/src/main/java/com/jarvis/app/companioncore/render/OrbStateMachine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:167. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `animationController: AnimationController`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:53`
- **Code:** `animationController.submitState(intent)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `animationController: AnimationController` (declared at mobile/app/src/main/java/com/jarvis/app/companioncore/render/AnimationController.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:177. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `resourceManagement: MobileResourceManagement`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:54`
- **Code:** `resourceManagement.evaluate(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `resourceManagement: MobileResourceManagement` (declared at mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:189. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `clock: CompanionClock`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:55`
- **Code:** `fun masterElapsedNanos(frameTimeNanos: Long): Long = clock.masterElapsedNanos(frameTimeNanos)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `clock: CompanionClock` (declared at mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CompanionClock.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:253. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `clock: CompanionClock`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/companioncore/render/AnimationController.kt:21`
- **Code:** `fun framePeriodNanos(targetFps: Double): Long = clock.framePeriodNanos(targetFps)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `clock: CompanionClock` (declared at mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CompanionClock.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/companioncore/render/AnimationController.kt:62. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `blendedRetriever: BlendedMemoryRetriever`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/continuity/ContinuityGate.kt:43`
- **Code:** `val retrieved = blendedRetriever.retrieve(userText, now = System.currentTimeMillis())`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `blendedRetriever: BlendedMemoryRetriever` (declared at mobile/app/src/main/java/com/jarvis/app/memory/BlendedMemoryRetriever.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/continuity/ContinuityGate.kt:192. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `fileStorage: FileStorage`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentRepository.kt:13`
- **Code:** `fileStorage.write(profilesFile, jsonStr)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `fileStorage: FileStorage` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/FileStorage.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/env/EnvironmentRepository.kt:49. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `modelManager: ModelManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/LiquidEnvironmentManager.kt:35`
- **Code:** `modelManager.switchProvider(profile.provider, profile.modelSource)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `modelManager: ModelManager` (declared at mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/env/LiquidEnvironmentManager.kt:166. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `runtimeBinder: RuntimeBinder`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/env/LiquidEnvironmentManager.kt:36`
- **Code:** `runtimeBinder.attachToRemote(profile.modelSource)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `runtimeBinder: RuntimeBinder` (declared at mobile/app/src/main/java/com/jarvis/app/runtime/RuntimeBinder.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/env/LiquidEnvironmentManager.kt:171. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `archive: GenomeArchive`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:47`
- **Code:** `archive.put(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `archive: GenomeArchive` (declared at mobile/app/src/main/java/com/jarvis/app/genome/GenomeArchive.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:197. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `governor: ResourceGovernor`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:50`
- **Code:** `val admission = governor.requestAdmission(child, gen.id)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `governor: ResourceGovernor` (declared at mobile/app/src/main/java/com/jarvis/app/model/ResourceGovernor.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:145. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `failureSurface: FailureSurface`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:51`
- **Code:** `failureSurface.report(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `failureSurface: FailureSurface` (declared at mobile/app/src/main/java/com/jarvis/app/failure/FailureSurface.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:314. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `candidateGenerator: CandidateGenerator`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:52`
- **Code:** `for (gen in candidateGenerator.generate(base, requirement)) {`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `candidateGenerator: CandidateGenerator` (declared at mobile/app/src/main/java/com/jarvis/app/evolution/CandidateGenerator.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:105. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `mutantEnv: MutantEnvironment`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:53`
- **Code:** `val envResult = mutantEnv.create(child, gen.id, gen.implementation)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `mutantEnv: MutantEnvironment` (declared at mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:154. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `testRunner: CandidateTestRunner`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:54`
- **Code:** `val unitTests = testRunner.run(instance, gen.implementation)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `testRunner: CandidateTestRunner` (declared at mobile/app/src/main/java/com/jarvis/app/validation/CandidateTestRunner.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:164. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `failureRunner: FailureTestRunner`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:55`
- **Code:** `val failureTests = failureRunner.runFailurePath(instance, gen.implementation)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `failureRunner: FailureTestRunner` (declared at mobile/app/src/main/java/com/jarvis/app/validation/FailureTestRunner.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:165. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `resourceBenchmark: ResourceBenchmark`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:56`
- **Code:** `val resource = resourceBenchmark.run(instance, gen.implementation)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `resourceBenchmark: ResourceBenchmark` (declared at mobile/app/src/main/java/com/jarvis/app/validation/ResourceBenchmark.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:168. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `regressionRunner: RegressionRunner`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:57`
- **Code:** `val regressionTests = regressionRunner.run(instance, baselineTests)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `regressionRunner: RegressionRunner` (declared at mobile/app/src/main/java/com/jarvis/app/validation/RegressionRunner.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:167. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `gate: PromotionGate`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:58`
- **Code:** `val decision = gate.decide(evaluation, regressionTests, resource)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `gate: PromotionGate` (declared at mobile/app/src/main/java/com/jarvis/app/validation/PromotionGate.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:180. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `promotionController: PromotionController`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:59`
- **Code:** `val receipt = promotionController.promote(promoted.genome, promoted.implementation)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `promotionController: PromotionController` (declared at mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:289. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `mutationEngine: MutationEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:61`
- **Code:** `mutationEngine.mutate(parent, count = maxCandidates).map { it.child }`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `mutationEngine: MutationEngine` (declared at mobile/app/src/main/java/com/jarvis/app/mutation/MutationEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:98. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `selfRepairLoop: SelfRepairLoop`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:70`
- **Code:** `selfRepairLoop.repair(repairReq, "") { patch ->`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `selfRepairLoop: SelfRepairLoop` (declared at mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:244. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `provider: LlmSynthesisProvider`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/LlmSpecSynthesizer.kt:27`
- **Code:** `val program = provider.generateProgram(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `provider: LlmSynthesisProvider` (declared at mobile/app/src/main/java/com/jarvis/app/mutation/LlmSynthesisProvider.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/LlmSpecSynthesizer.kt:50. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: MicroSystemRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt:33`
- **Code:** `registry.all().firstOrNull { it.genome.id == prev.id }?.id`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: MicroSystemRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/microsystem/MicroSystemRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt:46. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `archive: GenomeArchive`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt:34`
- **Code:** `archive.put(healthy)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `archive: GenomeArchive` (declared at mobile/app/src/main/java/com/jarvis/app/genome/GenomeArchive.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt:59. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `genomeRegistry: GenomeRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt:35`
- **Code:** `genomeRegistry.register(healthy)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `genomeRegistry: GenomeRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/genome/GenomeRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt:61. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `failureSurface: FailureSurface`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt:36`
- **Code:** `failureSurface.report(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `failureSurface: FailureSurface` (declared at mobile/app/src/main/java/com/jarvis/app/failure/FailureSurface.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt:80. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: MicroSystemRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt:28`
- **Code:** `registry.unregister(receipt.systemId)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: MicroSystemRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/microsystem/MicroSystemRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt:36. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `archive: GenomeArchive`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt:29`
- **Code:** `val promoted = archive.get(receipt.genomeId)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `archive: GenomeArchive` (declared at mobile/app/src/main/java/com/jarvis/app/genome/GenomeArchive.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt:39. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `genomeRegistry: GenomeRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt:30`
- **Code:** `genomeRegistry.setCurrentBest(restoredGenome)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `genomeRegistry: GenomeRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/genome/GenomeRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt:48. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `failureSurface: FailureSurface`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt:31`
- **Code:** `failureSurface.report(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `failureSurface: FailureSurface` (declared at mobile/app/src/main/java/com/jarvis/app/failure/FailureSurface.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt:54. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `fileStorage: FileStorage`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/FailureStore.kt:24`
- **Code:** `fileStorage.append(StoreKind.FAILURES, toJson(e).toString())`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `fileStorage: FileStorage` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/FileStorage.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/failure/FailureStore.kt:52. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `surface: FailureSurface`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/failure/RecoveryController.kt:26`
- **Code:** `val event = surface.report(base)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `surface: FailureSurface` (declared at mobile/app/src/main/java/com/jarvis/app/failure/FailureSurface.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/failure/RecoveryController.kt:81. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `fileStorage: FileStorage`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/genome/GenomeArchive.kt:24`
- **Code:** `fileStorage.append(StoreKind.DIALOGUE, arr.toString())`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `fileStorage: FileStorage` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/FileStorage.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/genome/GenomeArchive.kt:70. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `relationship: RelationshipStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/BondFormation.kt:22`
- **Code:** `val rel = relationship.read()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `relationship: RelationshipStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/BondFormation.kt:32. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `ism: InternalStateManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/CompanionBehaviorOrchestrator.kt:24`
- **Code:** `val snapshot = ism.snapshot(now)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `ism: InternalStateManager` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/CompanionBehaviorOrchestrator.kt:33. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `values: ValuesSystem`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConsistencyGuard.kt:42`
- **Code:** `val v = values.values().firstOrNull { it.key == key }`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `values: ValuesSystem` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/ValuesSystem.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConsistencyGuard.kt:253. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `presence: PresenceManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConsistencyGuard.kt:43`
- **Code:** `val presenceSupports = presence.current().backgroundSummary != null`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `presence: PresenceManager` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/PresenceManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConsistencyGuard.kt:132. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `relationship: RelationshipStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConsistencyGuard.kt:44`
- **Code:** `val bond = relationship.read().depth.depth`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `relationship: RelationshipStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConsistencyGuard.kt:174. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `personality: PersonalityEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConversationStyleController.kt:37`
- **Code:** `val p = personality.styleProfile()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `personality: PersonalityEngine` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/PersonalityEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConversationStyleController.kt:43. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `mood: MoodSystem`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConversationStyleController.kt:38`
- **Code:** `val moodMods = mood.styleModifiers()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `mood: MoodSystem` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/MoodSystem.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConversationStyleController.kt:44. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `adaptation: UserAdaptation`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConversationStyleController.kt:39`
- **Code:** `val formality = blend(p.formality, adaptation.resolved(UserAdaptation.DIM_FORMALITY, p.formality))`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `adaptation: UserAdaptation` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/UserAdaptation.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConversationStyleController.kt:47. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `mood: MoodStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/EmotionalRegulation.kt:29`
- **Code:** `mood.applyNudge(valDelta, aroDelta, now)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `mood: MoodStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/MoodStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/EmotionalRegulation.kt:56. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `personality: PersonalityStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/EmotionalRegulation.kt:30`
- **Code:** `val warmth = personality.traitValue(PersonalityStore.TRAIT_WARMTH) ?: 0.60`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `personality: PersonalityStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/PersonalityStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/EmotionalRegulation.kt:82. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `personality: PersonalityStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/GrowthEngine.kt:29`
- **Code:** `personality.applyBatch(batch)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `personality: PersonalityStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/PersonalityStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/GrowthEngine.kt:80. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `identity: IdentityStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/IdentityRevisionHandler.kt:31`
- **Code:** `identity.applyRevision(newRecord, entry, auth)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `identity: IdentityStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/IdentityStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/IdentityRevisionHandler.kt:59. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `dialogue: DialogueLog`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalDialogueEngine.kt:35`
- **Code:** `dialogue.append(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `dialogue: DialogueLog` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/DialogueLog.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalDialogueEngine.kt:54. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `ism: InternalStateManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalDialogueEngine.kt:36`
- **Code:** `val snapshot = ism.snapshot(now)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `ism: InternalStateManager` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalDialogueEngine.kt:46. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `identity: IdentityKernel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:26`
- **Code:** `val id = identity.read()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `identity: IdentityKernel` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/IdentityKernel.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:83. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `personality: PersonalityStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:27`
- **Code:** `val traits = personality.allTraits().mapValues { (_, t) -> t.current }`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `personality: PersonalityStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/PersonalityStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:87. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `mood: MoodStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:28`
- **Code:** `val moodState = mood.effective(now)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `mood: MoodStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/MoodStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:86. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `relationship: RelationshipStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:29`
- **Code:** `val rel = relationship.read()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `relationship: RelationshipStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:85. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `presence: PresenceManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:30`
- **Code:** `val secondsSince = presence.current(now).lastActiveEpochMs?.let { last ->`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `presence: PresenceManager` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/PresenceManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:88. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `mood: MoodStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/MoodSystem.kt:26`
- **Code:** `fun current(now: Long = clock()): MoodState = mood.effective(now)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `mood: MoodStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/MoodStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/MoodSystem.kt:31. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `relationship: RelationshipStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/RelationshipModeling.kt:31`
- **Code:** `relationship.updateBaseline(affect, now)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `relationship: RelationshipStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/RelationshipModeling.kt:38. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `dialogue: DialogueLog`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/SelfReflection.kt:30`
- **Code:** `lastReflectionTs = dialogue.read().asReversed()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `dialogue: DialogueLog` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/DialogueLog.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/SelfReflection.kt:49. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `relationship: RelationshipStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/SelfReflection.kt:31`
- **Code:** `val rel = relationship.read()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `relationship: RelationshipStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/SelfReflection.kt:69. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `ism: InternalStateManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/SelfReflection.kt:32`
- **Code:** `val snapshot = ism.snapshot(now)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `ism: InternalStateManager` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/SelfReflection.kt:55. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `relationship: RelationshipStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/TrustModeling.kt:29`
- **Code:** `val before = relationship.read().trust.trust`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `relationship: RelationshipStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/TrustModeling.kt:116. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `store: AdaptationStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/mod/UserAdaptation.kt:41`
- **Code:** `fun currentNumeric(dimension: String): Preference? = store.readNumeric()[dimension]`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `store: AdaptationStore` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/AdaptationStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/mod/UserAdaptation.kt:51. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `styleController: ConversationStyleController`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/ExpressionPass.kt:27`
- **Code:** `val styled = styleController.style(reasoningReply, snapshot, context)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `styleController: ConversationStyleController` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConversationStyleController.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/ExpressionPass.kt:43. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `guard: ConsistencyGuard`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/ExpressionPass.kt:28`
- **Code:** `return guard.review(styled, reasoningReply, snapshot)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `guard: ConsistencyGuard` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConsistencyGuard.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/ExpressionPass.kt:44. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `ism: InternalStateManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/ExpressionPass.kt:29`
- **Code:** `val snapshot = ism.snapshot()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `ism: InternalStateManager` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/ExpressionPass.kt:42. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `emotionalRegulation: EmotionalRegulation`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:39`
- **Code:** `perception?.affect?.let { emotionalRegulation.observe(it) }`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `emotionalRegulation: EmotionalRegulation` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/EmotionalRegulation.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:65. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `relationshipModeling: RelationshipModeling`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:40`
- **Code:** `val vulnerabilityShared = relationshipModeling.observeMessage(exchange.userText)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `relationshipModeling: RelationshipModeling` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/RelationshipModeling.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:57. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `trustModeling: TrustModeling`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:41`
- **Code:** `trustModeling.observe(exchange.userText, perception?.affect, context.deviation)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `trustModeling: TrustModeling` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/TrustModeling.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:72. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `adaptation: UserAdaptation`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:42`
- **Code:** `adaptation.infer(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `adaptation: UserAdaptation` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/UserAdaptation.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:67. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `dialogueEngine: InternalDialogueEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:43`
- **Code:** `dialogueEngine.onExchange(exchange, context)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `dialogueEngine: InternalDialogueEngine` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalDialogueEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:73. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `growthEngine: GrowthEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:44`
- **Code:** `growthEngine.observeExchange(exchange, context)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `growthEngine: GrowthEngine` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/GrowthEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:74. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `memory: MemoryInterface`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:45`
- **Code:** `memory.annotateExchange(exchange, context)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `memory: MemoryInterface` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/MemoryInterface.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:75. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `bondFormation: BondFormation`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:46`
- **Code:** `bondFormation.onSessionEnd(sessionInteractionCount, now)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `bondFormation: BondFormation` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/BondFormation.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:90. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `selfReflection: SelfReflection`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:47`
- **Code:** `selfReflection.maybeReflect(now, force = longGapSinceLast)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `selfReflection: SelfReflection` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/SelfReflection.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:93. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `ei: EmotionalIntelligence`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/PerceptionPass.kt:29`
- **Code:** `val affect = ei.read(message, context)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `ei: EmotionalIntelligence` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/EmotionalIntelligence.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/PerceptionPass.kt:38. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `si: SocialIntelligence`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/PerceptionPass.kt:30`
- **Code:** `val deviation = si.detect(affect, relationship.read().baseline)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `si: SocialIntelligence` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/SocialIntelligence.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/PerceptionPass.kt:50. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `ism: InternalStateManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/PerceptionPass.kt:32`
- **Code:** `ism.recordAffect(affect)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `ism: InternalStateManager` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/PerceptionPass.kt:39. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: StoreRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/humancore/store/SyncPort.kt:41`
- **Code:** `registry.checkpoint()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: StoreRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/StoreRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/humancore/store/SyncPort.kt:45. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `worldModel: WorldModelService`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt:29`
- **Code:** `addAll(worldModel.getFactsAbout(WorldModelService.USER_NODE_NAME))`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `worldModel: WorldModelService` (declared at mobile/app/src/main/java/com/jarvis/app/identity/WorldModelService.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt:100. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `userProfile: UserProfile`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt:30`
- **Code:** `userProfile.ingestUtterance(userText, sessionId)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `userProfile: UserProfile` (declared at mobile/app/src/main/java/com/jarvis/app/identity/UserProfile.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt:91. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `selfModel: SelfModel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt:32`
- **Code:** `val id = selfModel.identity()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `selfModel: SelfModel` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt:125. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `personaTuner: PersonaTuner`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt:33`
- **Code:** `personaTuner.ingest(userText)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `personaTuner: PersonaTuner` (declared at mobile/app/src/main/java/com/jarvis/app/identity/PersonaTuner.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt:93. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `worldModel: WorldModelService`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/identity/PersonaTuner.kt:20`
- **Code:** `worldModel.persistPersonaAdjustment(adjustment.trait, adjustment.value, adjustment.triggeredBy)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `worldModel: WorldModelService` (declared at mobile/app/src/main/java/com/jarvis/app/identity/WorldModelService.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/identity/PersonaTuner.kt:39. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `capabilityRegistry: CapabilityRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/identity/SelfModel.kt:24`
- **Code:** `fun capabilities(): List<CapabilityRegistry.Capability> = capabilityRegistry.getAll()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `capabilityRegistry: CapabilityRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/identity/SelfModel.kt:37. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `worldModel: WorldModelService`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/identity/UserProfile.kt:22`
- **Code:** `worldModel.setUserPreference(key, value, source)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `worldModel: WorldModelService` (declared at mobile/app/src/main/java/com/jarvis/app/identity/WorldModelService.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/identity/UserProfile.kt:36. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `fileStorage: FileStorage`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/inbox/LocalTaskInbox.kt:24`
- **Code:** `fileStorage.append(tasksFile, task.toJson().toString())`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `fileStorage: FileStorage` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/FileStorage.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/inbox/LocalTaskInbox.kt:236. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `scorer: MemoryImportanceScorer`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/memory/BlendedMemoryRetriever.kt:60`
- **Code:** `score = scorer.score(node, context, now = now),`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `scorer: MemoryImportanceScorer` (declared at mobile/app/src/main/java/com/jarvis/app/memory/MemoryImportanceScorer.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/memory/BlendedMemoryRetriever.kt:152. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `userProfile: UserProfile`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/memory/MemoryConsolidationLoop.kt:52`
- **Code:** `val update = userProfile.ingestUtterance(statement.text, statement.sessionId)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `userProfile: UserProfile` (declared at mobile/app/src/main/java/com/jarvis/app/identity/UserProfile.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/memory/MemoryConsolidationLoop.kt:86. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `sandbox: ResourceSandbox`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/microsystem/SpecMicroSystem.kt:33`
- **Code:** `sandbox.shutdown()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `sandbox: ResourceSandbox` (declared at mobile/app/src/main/java/com/jarvis/app/mutant/ResourceSandbox.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/microsystem/SpecMicroSystem.kt:79. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `resourceGovernor: ResourceGovernor`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:48`
- **Code:** `when (val decision = resourceGovernor.admit(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `resourceGovernor: ResourceGovernor` (declared at mobile/app/src/main/java/com/jarvis/app/model/ResourceGovernor.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:337. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `ollama: OllamaAdapter`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/OllamaModelBackend.kt:30`
- **Code:** `ollama.listModels()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `ollama: OllamaAdapter` (declared at mobile/app/src/main/java/com/jarvis/app/model/adapters/OllamaAdapter.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/model/OllamaModelBackend.kt:51. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `usage: TokenUsage`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/adapters/RemoteJarvisAdapter.kt:77`
- **Code:** `usage.optInt("prompt_tokens", 0),`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `usage: TokenUsage` (declared at mobile/app/src/main/java/com/jarvis/app/model/ModelProvider.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/model/adapters/RemoteJarvisAdapter.kt:229. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `context: Context`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/storage/ModelPathResolver.kt:23`
- **Code:** `context.getExternalFilesDir(null)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `context: Context` (declared at mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/model/storage/ModelPathResolver.kt:98. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `factory: EnvironmentFactory`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt:27`
- **Code:** `val created = factory.createEnvironment(genome, manifest, backendName = "process")`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `factory: EnvironmentFactory` (declared at mobile/app/src/main/java/com/jarvis/app/mutation/EnvironmentFactory.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt:57. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `backend: ProcessLocalBackend`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt:28`
- **Code:** `backend.register(implId, implementation.spec)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `backend: ProcessLocalBackend` (declared at mobile/app/src/main/java/com/jarvis/app/mutant/ProcessLocalBackend.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt:62. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `sandbox: ResourceSandbox`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt:29`
- **Code:** `val result = sandbox.run(instance.implementation.spec, test.input)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `sandbox: ResourceSandbox` (declared at mobile/app/src/main/java/com/jarvis/app/mutant/ResourceSandbox.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt:80. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `artifactManager: ArtifactManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt:30`
- **Code:** `val bundle = artifactManager.collect(implId, genome.id, implementation, configuration, env.workspace)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `artifactManager: ArtifactManager` (declared at mobile/app/src/main/java/com/jarvis/app/mutant/ArtifactManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt:63. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `sandbox: ResourceSandbox`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutant/ProcessLocalBackend.kt:26`
- **Code:** `val run = sandbox.run(spec, input, defaultTimeoutMs)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `sandbox: ResourceSandbox` (declared at mobile/app/src/main/java/com/jarvis/app/mutant/ResourceSandbox.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/mutant/ProcessLocalBackend.kt:63. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `failureSurface: FailureSurface`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutation/EnvironmentFactory.kt:21`
- **Code:** `failureSurface.report(FailureReport(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `failureSurface: FailureSurface` (declared at mobile/app/src/main/java/com/jarvis/app/failure/FailureSurface.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/mutation/EnvironmentFactory.kt:43. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `archive: GenomeArchive`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:29`
- **Code:** `archive.put(promotedGenome)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `archive: GenomeArchive` (declared at mobile/app/src/main/java/com/jarvis/app/genome/GenomeArchive.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:164. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `resourceGovernor: ResourceGovernor`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:31`
- **Code:** `val admission = resourceGovernor.requestAdmission(candidate.child, candidate.id)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `resourceGovernor: ResourceGovernor` (declared at mobile/app/src/main/java/com/jarvis/app/model/ResourceGovernor.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:105. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `failureSurface: FailureSurface`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:32`
- **Code:** `failureSurface.report(FailureReport(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `failureSurface: FailureSurface` (declared at mobile/app/src/main/java/com/jarvis/app/failure/FailureSurface.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:227. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `mutationEngine: MutationEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:33`
- **Code:** `val mutations = mutationEngine.mutate(parent, parent.mutationOperators, maxCandidates)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `mutationEngine: MutationEngine` (declared at mobile/app/src/main/java/com/jarvis/app/mutation/MutationEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:55. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `parent: Genome`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/mutation/MutationEngine.kt:70`
- **Code:** `val child = parent.fork(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `parent: Genome` (declared at mobile/app/src/main/java/com/jarvis/app/genome/GenomeModel.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/mutation/MutationEngine.kt:36. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: MicroSystemRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/nervous/CapabilityRouter.kt:28`
- **Code:** `registry.provides(capability).sortedBy { costTier(it) }`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: MicroSystemRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/microsystem/MicroSystemRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/nervous/CapabilityRouter.kt:34. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `router: CapabilityRouter`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:30`
- **Code:** `return router.route(capability, inputs)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `router: CapabilityRouter` (declared at mobile/app/src/main/java/com/jarvis/app/nervous/CapabilityRouter.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:67. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `coordinator: OrganismCoordinator`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:31`
- **Code:** `fun manifests(): List<com.jarvis.app.organism.OrganismManifests> = coordinator.allManifests()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `coordinator: OrganismCoordinator` (declared at mobile/app/src/main/java/com/jarvis/app/nervous/OrganismCoordinator.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:49. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `predictive: PredictiveRouter`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:32`
- **Code:** `predictive.prepare(capability)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `predictive: PredictiveRouter` (declared at mobile/app/src/main/java/com/jarvis/app/nervous/PredictiveRouter.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:66. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: MicroSystemRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:34`
- **Code:** `registry.register(system)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: MicroSystemRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/microsystem/MicroSystemRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:61. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `governor: ResourceGovernor`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:35`
- **Code:** `governor.requestAdmission(genome, "view")`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `governor: ResourceGovernor` (declared at mobile/app/src/main/java/com/jarvis/app/model/ResourceGovernor.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:78. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: MicroSystemRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/nervous/OrganismCoordinator.kt:25`
- **Code:** `val readyCapabilities: Set<String> get() = registry.all().flatMap { it.capabilities }.toSet()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: MicroSystemRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/microsystem/MicroSystemRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/nervous/OrganismCoordinator.kt:30. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `router: CapabilityRouter`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/nervous/OrganismCoordinator.kt:26`
- **Code:** `val routed = router.route(capability, input)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `router: CapabilityRouter` (declared at mobile/app/src/main/java/com/jarvis/app/nervous/CapabilityRouter.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/nervous/OrganismCoordinator.kt:50. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `failureSurface: FailureSurface`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/onefriday/AndroidPlatformPorts.kt:35`
- **Code:** `failureSurface.report(report)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `failureSurface: FailureSurface` (declared at mobile/app/src/main/java/com/jarvis/app/failure/FailureSurface.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/onefriday/AndroidPlatformPorts.kt:114. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `selfModel: SelfModel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt:103`
- **Code:** `val identity = selfModel.identity()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `selfModel: SelfModel` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt:276. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `modelManager: ModelManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt:123`
- **Code:** `modelManager.requestChat(messages, maxTokens, timeoutMs)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `modelManager: ModelManager` (declared at mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt:255. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `router: CloudModelRouter`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/research/universal/CloudResearchProvider.kt:20`
- **Code:** `val result = router.submit(CloudReasoningRequest(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `router: CloudModelRouter` (declared at mobile/app/src/main/java/com/jarvis/app/cloud/CloudModelRouter.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/research/universal/CloudResearchProvider.kt:33. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `capabilityRouter: CapabilityRouter`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/resolution/FuzzyCommandResolver.kt:30`
- **Code:** `routed = capabilityRouter.route(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `capabilityRouter: CapabilityRouter` (declared at mobile/app/src/main/java/com/jarvis/app/nervous/CapabilityRouter.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/resolution/FuzzyCommandResolver.kt:102. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: CapabilityRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/resolution/FuzzyCommandResolver.kt:31`
- **Code:** `val capability = registry.get(rankedMemory.node.subject)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: CapabilityRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/resolution/FuzzyCommandResolver.kt:97. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `sandbox: ResourceSandbox`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/sandbox/LocalTestSandboxExecutor.kt:27`
- **Code:** `val result = sandbox.run(spec, input, timeoutMs = 500)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `sandbox: ResourceSandbox` (declared at mobile/app/src/main/java/com/jarvis/app/mutant/ResourceSandbox.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/sandbox/LocalTestSandboxExecutor.kt:51. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `observer: GradleXmlFailureObserver`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:53`
- **Code:** `val suites = observer.observe(suiteFilter)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `observer: GradleXmlFailureObserver` (declared at mobile/app/src/main/java/com/jarvis/app/selfrepair/TestRunObservation.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:113. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `diagnoser: DiagnosisEngine`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:54`
- **Code:** `val diagnosis = diagnoser.diagnose(requirement, target)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `diagnoser: DiagnosisEngine` (declared at mobile/app/src/main/java/com/jarvis/app/selfrepair/DiagnosisEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:121. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `synthesizer: LlmSynthesisProvider`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:55`
- **Code:** `val repairText = synthesizer.generateRepair(request)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `synthesizer: LlmSynthesisProvider` (declared at mobile/app/src/main/java/com/jarvis/app/mutation/LlmSynthesisProvider.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:145. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `gate: PromotionGate`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:56`
- **Code:** `val decision = gate.decide(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `gate: PromotionGate` (declared at mobile/app/src/main/java/com/jarvis/app/validation/PromotionGate.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:244. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `failureSurface: FailureSurface`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:60`
- **Code:** `failureSurface.report(`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `failureSurface: FailureSurface` (declared at mobile/app/src/main/java/com/jarvis/app/failure/FailureSurface.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:337. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `promotionController: PromotionController`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:61`
- **Code:** `val receipt = promotionController.promote(genome, implementation)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `promotionController: PromotionController` (declared at mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:288. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `rollbackController: RollbackController`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:62`
- **Code:** `rollbackController.rollback(receipt, "regression after self-repair promotion")`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `rollbackController: RollbackController` (declared at mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:305. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `diagnosis: RepairDiagnosis`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:95`
- **Code:** `diagnosis = diagnosis.render()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `diagnosis: RepairDiagnosis` (declared at mobile/app/src/main/java/com/jarvis/app/selfrepair/DiagnosisEngine.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:143. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `patch: SourcePatch`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:96`
- **Code:** `val problems = patch.validate(repoRoot)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `patch: SourcePatch` (declared at mobile/app/src/main/java/com/jarvis/app/selfrepair/SourcePatch.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:147. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `fileStorage: FileStorage`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/session/SessionManager.kt:23`
- **Code:** `fileStorage.append(sessionsFile, session.toJson().toString())`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `fileStorage: FileStorage` (declared at mobile/app/src/main/java/com/jarvis/app/humancore/store/FileStorage.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/session/SessionManager.kt:190. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `worldModel: WorldModelService`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/social/ConfidentialityFirewall.kt:33`
- **Code:** `val meta = worldModel.getFactsAbout(person)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `worldModel: WorldModelService` (declared at mobile/app/src/main/java/com/jarvis/app/identity/WorldModelService.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/social/ConfidentialityFirewall.kt:73. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `worldModel: WorldModelService`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/social/PersonRelationshipModel.kt:31`
- **Code:** `worldModel.registerEntity(person, EntityType.PERSON, source)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `worldModel: WorldModelService` (declared at mobile/app/src/main/java/com/jarvis/app/identity/WorldModelService.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/social/PersonRelationshipModel.kt:35. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `vocabularyStore: VocabularyStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:320`
- **Code:** `val finalText = vocabularyStore.applyPronunciationHints(text)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `vocabularyStore: VocabularyStore` (declared at mobile/app/src/main/java/com/jarvis/app/body/VocabularyStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:274. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `streamingTts: StreamingTts`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:321`
- **Code:** `streamingTts.speak(finalText, mapLanguage(language))`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `streamingTts: StreamingTts` (declared at mobile/app/src/main/java/com/jarvis/app/body/StreamingTts.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:275. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `conversationViewModel: ConversationViewModel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/ui/screens/ConversationScreen.kt:46`
- **Code:** `conversationViewModel.send(input)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `conversationViewModel: ConversationViewModel` (declared at mobile/app/src/main/java/com/jarvis/app/ui/viewmodel/ConversationViewModel.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/ui/screens/ConversationScreen.kt:56. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `diagnosticsViewModel: DiagnosticsViewModel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/ui/screens/SystemScreen.kt:41`
- **Code:** `onClick = { diagnosticsViewModel.crashVoiceSubstrate() },`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `diagnosticsViewModel: DiagnosticsViewModel` (declared at mobile/app/src/main/java/com/jarvis/app/DiagnosticsViewModel.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/ui/screens/SystemScreen.kt:89. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `currentConfig: Config`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/vad/SileroVadManager.kt:147`
- **Code:** `currentConfig = currentConfig.copy(modelPath = altFile.absolutePath)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `currentConfig: Config` (declared at mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/vad/SileroVadManager.kt:199. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `governor: ResourceGovernor`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceModelManager.kt:47`
- **Code:** `if (governor != null && !governor.canAdmit(entry.expectedRamMb, entry.expectedCpuPercent)) {`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `governor: ResourceGovernor` (declared at mobile/app/src/main/java/com/jarvis/app/model/ResourceGovernor.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/voice/VoiceModelManager.kt:108. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `context: Context`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOrganismHost.kt:47`
- **Code:** `bound = context.bindService(environment.bindIntent(context), connection, environment.bindFlags)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `context: Context` (declared at mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/voice/VoiceOrganismHost.kt:131. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `host: VoiceOrganismHost`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOrganismMicroSystem.kt:33`
- **Code:** `host.start()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `host: VoiceOrganismHost` (declared at mobile/app/src/main/java/com/jarvis/app/voice/VoiceOrganismHost.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/voice/VoiceOrganismMicroSystem.kt:81. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `host: VoiceOrganismHost`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOutputPath.kt:39`
- **Code:** `runCatching { host.cancelVoice() }`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `host: VoiceOrganismHost` (declared at mobile/app/src/main/java/com/jarvis/app/voice/VoiceOrganismHost.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/voice/VoiceOutputPath.kt:83. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `fallback: StreamingTts`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOutputPath.kt:42`
- **Code:** `runCatching { fallback.speakStreaming(segment, vocabulary ?: emptyVocabulary()) }`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `fallback: StreamingTts` (declared at mobile/app/src/main/java/com/jarvis/app/body/StreamingTts.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/voice/VoiceOutputPath.kt:66. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `profileStore: VoiceProfileStore`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOutputPath.kt:43`
- **Code:** `val profile = profileStore.get(DefaultJarvisVoiceProfile.id) ?: DefaultJarvisVoiceProfile`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `profileStore: VoiceProfileStore` (declared at mobile/app/src/main/java/com/jarvis/app/voice/VoiceProfileStore.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/voice/VoiceOutputPath.kt:56. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `scheduler: VoiceScheduler`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/VoiceService.kt:93`
- **Code:** `scheduler.close()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `scheduler: VoiceScheduler` (declared at mobile/app/src/main/java/com/jarvis/app/voice/VoiceScheduler.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/voice/VoiceService.kt:88. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `modelManager: VoiceModelManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/provider/SherpaTtsVoiceProvider.kt:35`
- **Code:** `val acquire = modelManager.acquire(entry)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `modelManager: VoiceModelManager` (declared at mobile/app/src/main/java/com/jarvis/app/voice/VoiceModelManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/voice/provider/SherpaTtsVoiceProvider.kt:92. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `registry: VoiceProviderRegistry`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProviderSelector.kt:19`
- **Code:** `val languageCandidates = registry.discover(language = lang)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `registry: VoiceProviderRegistry` (declared at mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProviderRegistry.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProviderSelector.kt:45. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

## Observations — seen, and why each was NOT called a deviation

- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/JarvisMic.kt:33` (`SAMPLE_RATE`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/JarvisMic.kt:41` (`SPEECH_RMS_THRESHOLD`): a story added `SPEECH_RMS_THRESHOLD = None` and it gates a bounded measure at one fixed value (gating sites: mobile/app/src/main/java/com/jarvis/app/JarvisMic.kt:109 `if (rms > SPEECH_RMS_THRESHOLD) {`). Recorded, not called a deviation: the quantity compared is not an accumulation of evidence and not elapsed time, so nothing here grows with history. **Not a deviation because:** the compared quantity is neither an evidence count nor an elapsed interval
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/JarvisMic.kt:42` (`SILENCE_HANG_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/android/MediaSessionControlBackend.kt:171` (`PAUSED`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/android/MediaSessionControlBackend.kt:172` (`PLAYING`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:125` (`STT_TIMEOUT_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:126` (`MODEL_WARMUP_TIMEOUT_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:127` (`TTS_FIRST_CHUNK_TIMEOUT_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:128` (`INTERRUPT_WINDOW_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:133` (`MAX_MEMORY_ITEMS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:134` (`MEMORY_RELEVANCE_THRESHOLD`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:138` (`MODEL_MAX_CONCURRENT`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:141` (`MAX_STT_RETRIES`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:142` (`MAX_TTS_RETRIES`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:143` (`MAX_MODEL_RETRIES`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/body/MemoryStore.kt:21` (`MAX_FACTS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/buildtwin/BuildTwinVerifier.kt:146` (`DEFAULT_TIMEOUT_SECONDS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ContinuityLaw.kt:236` (`EVIDENCE_CONFIDENCE`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ContinuityLaw.kt:242` (`ACCEPTED_CONFIDENCE`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/MentalStateClaims.kt:39` (`DECAY_RATE`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/MentalStateClaims.kt:45` (`UNANCHORED_CONFIDENCE`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ReplayCheck.kt:252` (`MIN_FIELD_SIMILARITY`): a story added `MIN_FIELD_SIMILARITY = Double` and it gates a bounded measure at one fixed value (gating sites: mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ReplayCheck.kt:148 `if (similarity < MIN_FIELD_SIMILARITY) {`). Recorded, not called a deviation: the quantity compared is not an accumulation of evidence and not elapsed time, so nothing here grows with history. **Not a deviation because:** the compared quantity is neither an evidence count nor an elapsed interval
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/Workspace.kt:58` (`TICK_INTERVAL_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityPersistence.kt:33` (`MAX_MEMORIES`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelPersistence.kt:35` (`SCHEMA_VERSION`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CompanionClock.kt:85` (`NANOS_PER_MILLI`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CompanionClock.kt:86` (`NANOS_PER_SECOND`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CompanionClock.kt:88` (`DEFAULT_STALENESS_THRESHOLD_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CurrentActionDiscriminator.kt:164` (`DEFAULT_DONE_IDLE_TIMEOUT_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/LocalHumanCoreBinding.kt:101` (`SCHEMA_VERSION`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/RemoteHumanCoreBinding.kt:81` (`SCHEMA_VERSION`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/RemoteHumanCoreBinding.kt:82` (`DEFAULT_REFRESH_INTERVAL_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/identity/VisualIdentity.kt:23` (`IDENTITY_VERSION`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:262` (`TICK_INTERVAL_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:265` (`BACKGROUND_TICK_INTERVAL_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:226` (`PRIORITY_SHUTTING_DOWN`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:227` (`PRIORITY_WAKING`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:228` (`PRIORITY_USER_INTERRUPT`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:229` (`PRIORITY_LISTENING`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:230` (`PRIORITY_SPEAKING`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:231` (`PRIORITY_THINKING`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:232` (`PRIORITY_IDLE`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:233` (`PRIORITY_ASLEEP`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:249` (`ACTION_DWELL_TICKS`): a story added `ACTION_DWELL_TICKS = None` and it gates a bounded measure at one fixed value (gating sites: mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:215 `if (actionDwell >= ACTION_DWELL_TICKS) {`). Recorded, not called a deviation: the quantity compared is not an accumulation of evidence and not elapsed time, so nothing here grows with history. **Not a deviation because:** the compared quantity is neither an evidence count nor an elapsed interval
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:141` (`COOLDOWN_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:144` (`BATTERY_LOW_PCT`): a story added `BATTERY_LOW_PCT = Int` and it gates a bounded measure at one fixed value (gating sites: mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:105 `conditions.batteryPercent >= BATTERY_LOW_PCT &&`). Recorded, not called a deviation: the quantity compared is not an accumulation of evidence and not elapsed time, so nothing here grows with history. **Not a deviation because:** the compared quantity is neither an evidence count nor an elapsed interval
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:147` (`MISSES_MEDIUM`): a story added `MISSES_MEDIUM = Int` and it gates a bounded measure at one fixed value (gating sites: mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:106 `conditions.consecutiveFrameMisses < MISSES_MEDIUM`). Recorded, not called a deviation: the quantity compared is not an accumulation of evidence and not elapsed time, so nothing here grows with history. **Not a deviation because:** the compared quantity is neither an evidence count nor an elapsed interval
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:148` (`MISSES_LOW`): a story added `MISSES_LOW = Int` and it gates a bounded measure at one fixed value (gating sites: mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:91 `val missStepDown = conditions.consecutiveFrameMisses >= MISSES_LOW ||`). Recorded, not called a deviation: the quantity compared is not an accumulation of evidence and not elapsed time, so nothing here grows with history. **Not a deviation because:** the compared quantity is neither an evidence count nor an elapsed interval
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:415` (`EVENT_SALIENCE_THRESHOLD`): a story added `EVENT_SALIENCE_THRESHOLD = None` and it gates a bounded measure at one fixed value (gating sites: mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:117 `if (salience < EVENT_SALIENCE_THRESHOLD) return`). Recorded, not called a deviation: the quantity compared is not an accumulation of evidence and not elapsed time, so nothing here grows with history. **Not a deviation because:** the compared quantity is neither an evidence count nor an elapsed interval
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:416` (`EVENT_LOG_LIMIT`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:417` (`EVENT_SUMMARY_MAX`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:418` (`TRUST_EVENT_LIMIT`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:419` (`TRUST_NEGATIVE_BOOST`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:420` (`GAP_DECAY_FRACTION`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:421` (`BOND_SESSION_STEP_CAP`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/latency/AckSpeech.kt:117` (`PREPARE_RETRY_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/latency/AckSpeech.kt:118` (`PREPARE_MAX_RETRIES`): a story added `PREPARE_MAX_RETRIES = None` and it gates a bounded measure at one fixed value (gating sites: mobile/app/src/main/java/com/jarvis/app/latency/AckSpeech.kt:95 `while (attempts < PREPARE_MAX_RETRIES && !TtsBridge.status.value.startsWith("ready")) {`). Recorded, not called a deviation: the quantity compared is not an accumulation of evidence and not elapsed time, so nothing here grows with history. **Not a deviation because:** the compared quantity is neither an evidence count nor an elapsed interval
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/latency/LatencyLayer.kt:476` (`ACK_GAP_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/latency/LatencyLayer.kt:478` (`SLOW_THRESHOLD_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/latency/LatencyLayer.kt:480` (`ACK_ONLY_IDLE_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/latency/WarmupEngine.kt:28` (`PING_INTERVAL_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/latency/WarmupEngine.kt:30` (`PING_TIMEOUT_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/memory/AndroidMemoryGraphStore.kt:232` (`SCHEMA_VERSION`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/memory/MemoryImportanceScorer.kt:36` (`FREQ_SATURATION`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/memory/MemoryImportanceScorer.kt:38` (`W_RECENCY`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/memory/MemoryImportanceScorer.kt:39` (`W_FREQUENCY`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/memory/MemoryImportanceScorer.kt:40` (`W_RELEVANCE`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/memory/MemoryImportanceScorer.kt:41` (`W_SALIENCE`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/memory/SignalSplitScorer.kt:180` (`STEP`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:616` (`MAX_RETRIES`): a story added `MAX_RETRIES = None` and it gates a bounded measure at one fixed value (gating sites: mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:551 `if (attempt < MAX_RETRIES) {`; mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:560 `if (attempt < MAX_RETRIES) {`). Recorded, not called a deviation: the quantity compared is not an accumulation of evidence and not elapsed time, so nothing here grows with history. **Not a deviation because:** the compared quantity is neither an evidence count nor an elapsed interval
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/termux/TermuxJarvisServer.kt:560` (`BIND_ATTEMPTS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/termux/TermuxJarvisServer.kt:561` (`BIND_RETRY_MS`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/vf/motion/MotionEngine.kt:54` (`SharpPulseAmplitude`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:43` (`AccentHairlineAlpha`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:83` (`DurVoiceResponse`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:90` (`EventPulseAttackMs`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:91` (`EventPulseDecayMs`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:105` (`Layer0Void`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:106` (`Layer1Ambient`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:107` (`Layer2Structure`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:108` (`Layer3Content`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:109` (`Layer4Core`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:119` (`GlassSelfReflectionMin`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:120` (`GlassSelfReflectionMax`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/voice/HotwordAvailabilityProbe.kt:121` (`STATE_SERVICE_UNAVAILABLE`): a story added `STATE_SERVICE_UNAVAILABLE = Int` and it gates a bounded measure at one fixed value (gating sites: mobile/app/src/main/java/com/jarvis/app/voice/HotwordAvailabilityProbe.kt:143 `soundTriggerStatus == STATE_SERVICE_UNAVAILABLE -> HotwordAvailability.UNSUPPORTED`). Recorded, not called a deviation: the quantity compared is not an accumulation of evidence and not elapsed time, so nothing here grows with history. **Not a deviation because:** the compared quantity is neither an evidence count nor an elapsed interval
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/voice/HotwordAvailabilityProbe.kt:122` (`STATE_KEYPHRASE_ENROLLED`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/voice/HotwordAvailabilityProbe.kt:123` (`STATE_KEYPHRASE_UNENROLLED`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/voice/HotwordAvailabilityProbe.kt:124` (`STATE_HARDWARE_UNSUPPORTED`): a story added `STATE_HARDWARE_UNSUPPORTED = Int` and it gates a bounded measure at one fixed value (gating sites: mobile/app/src/main/java/com/jarvis/app/voice/HotwordAvailabilityProbe.kt:142 `soundTriggerStatus == STATE_HARDWARE_UNSUPPORTED ||`). Recorded, not called a deviation: the quantity compared is not an accumulation of evidence and not elapsed time, so nothing here grows with history. **Not a deviation because:** the compared quantity is neither an evidence count nor an elapsed interval
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/voice/VoiceForgeAdapter.kt:99` (`DEFAULT_EXAGGERATION`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/voice/VoiceForgeAdapter.kt:100` (`DEFAULT_CFG_WEIGHT`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProviderRegistry.kt:25` (`DEFAULT_PRIORITY`): a story added `DEFAULT_PRIORITY = None` and it gates a bounded measure at one fixed value (gating sites: mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt:324 `} else if (effectiveConfig.compressionEnabled && section.priority != SectionPriority.CRITICAL) {`; mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveContextBuilder.kt:424 `} else if (config.compressionEnabled && section.priority != SectionPriority.CRITICAL) {`; mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:171 `if (req.priority > best.priority ||`; mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:172 `(req.priority == best.priority && req.requestedAtMs >= best.requestedAtMs)`). Recorded, not called a deviation: the quantity compared is not an accumulation of evidence and not elapsed time, so nothing here grows with history. **Not a deviation because:** the compared quantity is neither an evidence count nor an elapsed interval
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProviderRegistry.kt:26` (`HIGH_PRIORITY`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `flat_threshold` — `mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProviderRegistry.kt:27` (`LOW_PRIORITY`): declared here and used only as a value or in arithmetic; it never gates anything, so there is no threshold here. **Not a deviation because:** no comparison in the changed code reaches this constant
- `ownership_naming` — `mobile/app/src/main/java/com/jarvis/app/JarvisEngine.kt:39` (`AnchorEngine, AnimationEngine, ArbitrationEngine, AttentionEngine, CognitiveEngine, CompanionCore, CoreIdentity, DecisionEngine, DiagnosisEngine, EmotionalIntelligence, EvolutionEngine, ExecutionEngine, FallbackIdentity, GlowEngine, GrowthEngine, HumanCore, InternalDialogueEngine, JarvisEngine, LocalInferenceEngine, MemoryUpdateEngine, ModelUpdateEngine, MotionEngine, MutationEngine, ParticleEngine, PersonalityEngine, PresenceEngine, ReactorEngine, SelfIdentity, SocialIntelligence, SpeechEngine, StreamingTTSEngine, SttEngine, SystemTTSEngine, TTSEngine, ThemeEngine, TypographyEngine, UniversalResearchEngine, UserIdentity, VisualIdentity, WarmupEngine`): the free pre-screen found 40 added name(s) ending in an ownership word: - AnchorEngine  (mobile/app/src/main/java/com/jarvis/app/anchor/AnchorEngine.kt:33, declared `class`/`object`/`interface` as `class`)
- AnimationEngine  (mobile/app/src/main/java/com/jarvis/app/vf/animation/AnimationEngine.kt:22, declared `class`/`object`/`interface` as `object`)
- ArbitrationEngine  (mobile/app/src/main/java/com/jarvis/app/nervous/ArbitrationEngine.kt:14, declared `class`/`object`/`interface` as `class`)
- AttentionEngine  (mobile/app/src/main/java/com/jarvis/app/cognitive/AttentionEngine.kt:24, declared `class`/`object`/`interface` as `class`)
- CognitiveEngine  (mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:56, declared `class`/`object`/`interface` as `class`)
- CompanionCore  (mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:49, declared `class`/`object`/`interface` as `class`)
- CoreIdentity  (mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ReplayCheck.kt:266, declared `class`/`object`/`interface` as `class`)
- DecisionEngine  (mobile/app/src/main/java/com/jarvis/app/cognitive/planning/DecisionEngine.kt:97, declared `class`/`object`/`interface` as `class`)
- DiagnosisEngine  (mobile/app/src/main/java/com/jarvis/app/selfrepair/DiagnosisEngine.kt:55, declared `class`/`object`/`interface` as `class`)
- EmotionalIntelligence  (mobile/app/src/main/java/com/jarvis/app/humancore/mod/EmotionalIntelligence.kt:25, declared `class`/`object`/`interface` as `class`)
- EvolutionEngine  (mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:46, declared `class`/`object`/`interface` as `class`)
- ExecutionEngine  (mobile/app/src/main/java/com/jarvis/app/cognitive/execution/ExecutionEngine.kt:42, declared `class`/`object`/`interface` as `class`)
- FallbackIdentity  (mobile/app/src/main/java/com/jarvis/app/humancore/fallback/FallbackIdentity.kt:21, declared `class`/`object`/`interface` as `object`)
- GlowEngine  (mobile/app/src/main/java/com/jarvis/app/vf/glow/GlowEngine.kt:29, declared `class`/`object`/`interface` as `object`)
- GrowthEngine  (mobile/app/src/main/java/com/jarvis/app/humancore/mod/GrowthEngine.kt:28, declared `class`/`object`/`interface` as `class`)
- HumanCore  (mobile/app/src/main/java/com/jarvis/app/humancore/HumanCore.kt:62, declared `class`/`object`/`interface` as `object`)
- InternalDialogueEngine  (mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalDialogueEngine.kt:34, declared `class`/`object`/`interface` as `class`)
- JarvisEngine  (mobile/app/src/main/java/com/jarvis/app/JarvisEngine.kt:39, declared `class`/`object`/`interface` as `object`)
- LocalInferenceEngine  (mobile/app/src/main/java/com/jarvis/app/model/adapters/LocalInferenceEngine.kt:36, declared `class`/`object`/`interface` as `class`)
- MemoryUpdateEngine  (mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:16, declared `class`/`object`/`interface` as `class`)
- ModelUpdateEngine  (mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelUpdateEngine.kt:30, declared `class`/`object`/`interface` as `class`)
- MotionEngine  (mobile/app/src/main/java/com/jarvis/app/vf/motion/MotionEngine.kt:30, declared `class`/`object`/`interface` as `object`)
- MutationEngine  (mobile/app/src/main/java/com/jarvis/app/mutation/MutationEngine.kt:15, declared `class`/`object`/`interface` as `class`)
- ParticleEngine  (mobile/app/src/main/java/com/jarvis/app/vf/particle/ParticleEngine.kt:24, declared `class`/`object`/`interface` as `object`)
- PersonalityEngine  (mobile/app/src/main/java/com/jarvis/app/humancore/mod/PersonalityEngine.kt:22, declared `class`/`object`/`interface` as `class`)
- PresenceEngine  (mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:44, declared `class`/`object`/`interface` as `class`)
- ReactorEngine  (mobile/app/src/main/java/com/jarvis/app/vf/reactor/ReactorEngine.kt:19, declared `class`/`object`/`interface` as `object`)
- SelfIdentity  (mobile/app/src/main/java/com/jarvis/app/identity/SelfModel.kt:103, declared `class`/`object`/`interface` as `class`)
- SocialIntelligence  (mobile/app/src/main/java/com/jarvis/app/humancore/mod/SocialIntelligence.kt:30, declared `class`/`object`/`interface` as `class`)
- SpeechEngine  (mobile/app/src/main/java/com/jarvis/app/latency/SpeechEngine.kt:27, declared `class`/`object`/`interface` as `object`)
- StreamingTTSEngine  (mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:254, declared `class`/`object`/`interface` as `class`)
- SttEngine  (mobile/app/src/main/java/com/jarvis/app/body/LanguageRouter.kt:67, declared `class`/`object`/`interface` as `enum class`)
- SystemTTSEngine  (mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:114, declared `class`/`object`/`interface` as `class`)
- TTSEngine  (mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:27, declared `class`/`object`/`interface` as `interface`)
- ThemeEngine  (mobile/app/src/main/java/com/jarvis/app/vf/theme/ThemeEngine.kt:1, declared `class`/`object`/`interface` as `file`)
- TypographyEngine  (mobile/app/src/main/java/com/jarvis/app/vf/typography/TypographyEngine.kt:28, declared `class`/`object`/`interface` as `object`)
- UniversalResearchEngine  (mobile/app/src/main/java/com/jarvis/app/research/universal/UniversalResearchEngine.kt:21, declared `class`/`object`/`interface` as `class`)
- UserIdentity  (mobile/app/src/main/java/com/jarvis/app/cognitive/model/UserModel.kt:57, declared `class`/`object`/`interface` as `class`)
- VisualIdentity  (mobile/app/src/main/java/com/jarvis/app/companioncore/identity/VisualIdentity.kt:21, declared `class`/`object`/`interface` as `object`)
- WarmupEngine  (mobile/app/src/main/java/com/jarvis/app/latency/WarmupEngine.kt:27, declared `class`/`object`/`interface` as `object`). The semantic judgement (does the name CLAIM a permanent cognitive job, or declare an affordance?) was NOT made: no model was available, and this tool does not guess a meaning-level verdict. **Not a deviation because:** unjudged: the only model-backed check had no model, so the names are reported as open, not as deviations

## Open list — flagged, not resolved by this tool

- `closed_type` `DeviceControlOutcome` at `mobile/app/src/main/java/com/jarvis/app/JarvisEngine.kt:739` — no recipe this tool is confident enough to write.
- `closed_type` `AlertLevel` at `mobile/app/src/main/java/com/jarvis/app/alerts/LocalAlertStore.kt:216` — no recipe this tool is confident enough to write.
- `closed_type` `ActionType` at `mobile/app/src/main/java/com/jarvis/app/android/DeviceControl.kt:14` — no recipe this tool is confident enough to write.
- `closed_type` `VerificationStage` at `mobile/app/src/main/java/com/jarvis/app/android/DeviceControl.kt:32` — no recipe this tool is confident enough to write.
- `closed_type` `MediaTransportCommand` at `mobile/app/src/main/java/com/jarvis/app/android/MediaSessionControlBackend.kt:7` — no recipe this tool is confident enough to write.
- `closed_type` `GateOutcome` at `mobile/app/src/main/java/com/jarvis/app/android/RiskGate.kt:20` — no recipe this tool is confident enough to write.
- `closed_type` `RiskTier` at `mobile/app/src/main/java/com/jarvis/app/android/RiskTier.kt:13` — no recipe this tool is confident enough to write.
- `closed_type` `GateResult` at `mobile/app/src/main/java/com/jarvis/app/approval/ApprovalGate.kt:28` — no recipe this tool is confident enough to write.
- `closed_type` `ApprovalStatus` at `mobile/app/src/main/java/com/jarvis/app/approvals/LocalApprovalQueue.kt:279` — no recipe this tool is confident enough to write.
- `closed_type` `ApprovalDecision` at `mobile/app/src/main/java/com/jarvis/app/approvals/LocalApprovalQueue.kt:287` — no recipe this tool is confident enough to write.
- `closed_type` `RiskLevel` at `mobile/app/src/main/java/com/jarvis/app/approvals/LocalApprovalQueue.kt:294` — no recipe this tool is confident enough to write.
- `closed_type` `SectionPriority` at `mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt:404` — no recipe this tool is confident enough to write.
- `closed_type` `BodyState` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:14` — no recipe this tool is confident enough to write.
- `closed_type` `BodyEvent` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:40` — no recipe this tool is confident enough to write.
- `closed_type` `MemoryType` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:87` — no recipe this tool is confident enough to write.
- `closed_type` `SttEngine` at `mobile/app/src/main/java/com/jarvis/app/body/LanguageRouter.kt:67` — no recipe this tool is confident enough to write.
- `closed_type` `PromotionDecision` at `mobile/app/src/main/java/com/jarvis/app/body/MemoryPromotion.kt:39` — no recipe this tool is confident enough to write.
- `closed_type` `BootstrapState` at `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:181` — no recipe this tool is confident enough to write.
- `closed_type` `BootstrapStep` at `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:188` — no recipe this tool is confident enough to write.
- `closed_type` `BootstrapResult` at `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:201` — no recipe this tool is confident enough to write.
- `closed_type` `BootstrapException` at `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:206` — no recipe this tool is confident enough to write.
- `closed_type` `TaskType` at `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityArbitrator.kt:46` — no recipe this tool is confident enough to write.
- `closed_type` `ArbitrationResult` at `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityArbitrator.kt:62` — no recipe this tool is confident enough to write.
- `closed_type` `Category` at `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt:21` — no recipe this tool is confident enough to write.
- `closed_type` `QualityTier` at `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt:26` — no recipe this tool is confident enough to write.
- `closed_type` `State` at `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt:29` — no recipe this tool is confident enough to write.
- `closed_type` `Health` at `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt:32` — no recipe this tool is confident enough to write.
- `closed_type` `RecoveryStrategy` at `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt:35` — no recipe this tool is confident enough to write.
- `closed_type` `RouteResult` at `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRouter.kt:28` — no recipe this tool is confident enough to write.
- `closed_type` `CloudResult` at `mobile/app/src/main/java/com/jarvis/app/cloud/CloudModelPool.kt:14` — no recipe this tool is confident enough to write.
- `closed_type` `ChangeLayer` at `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ChangeLayer.kt:38` — no recipe this tool is confident enough to write.
- `closed_type` `ClaimKind` at `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ClaimKind.kt:18` — no recipe this tool is confident enough to write.
- `closed_type` `SectionPriority` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveContextBuilder.kt:463` — no recipe this tool is confident enough to write.
- `closed_type` `TurnDecision` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:267` — no recipe this tool is confident enough to write.
- `closed_type` `IntentCategory` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1298` — no recipe this tool is confident enough to write.
- `closed_type` `CognitiveEvent` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1579` — no recipe this tool is confident enough to write.
- `closed_type` `DecompositionCause` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1760` — no recipe this tool is confident enough to write.
- `closed_type` `IntentState` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:100` — no recipe this tool is confident enough to write.
- `closed_type` `GoalPriority` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:126` — no recipe this tool is confident enough to write.
- `closed_type` `GoalStatus` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:133` — no recipe this tool is confident enough to write.
- `closed_type` `SubgoalStatus` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:150` — no recipe this tool is confident enough to write.
- `closed_type` `MemorySource` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:178` — no recipe this tool is confident enough to write.
- `closed_type` `AttentionSource` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:193` — no recipe this tool is confident enough to write.
- `closed_type` `ThermalState` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:224` — no recipe this tool is confident enough to write.
- `closed_type` `EntityType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:316` — no recipe this tool is confident enough to write.
- `closed_type` `PlanStatus` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:335` — no recipe this tool is confident enough to write.
- `closed_type` `PlanStepStatus` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:347` — no recipe this tool is confident enough to write.
- `closed_type` `DecisionFactor` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:400` — no recipe this tool is confident enough to write.
- `closed_type` `DecisionOutcome` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt:433` — no recipe this tool is confident enough to write.
- `closed_type` `ConstraintType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/IntentInference.kt:551` — no recipe this tool is confident enough to write.
- `closed_type` `AmbiguityLevel` at `mobile/app/src/main/java/com/jarvis/app/cognitive/IntentInference.kt:563` — no recipe this tool is confident enough to write.
- `closed_type` `InterpretationSource` at `mobile/app/src/main/java/com/jarvis/app/cognitive/IntentInference.kt:572` — no recipe this tool is confident enough to write.
- `closed_type` `UnknownType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/IntentInference.kt:580` — no recipe this tool is confident enough to write.
- `closed_type` `EntityType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/IntentInference.kt:591` — no recipe this tool is confident enough to write.
- `closed_type` `MemoryEvent` at `mobile/app/src/main/java/com/jarvis/app/cognitive/WorkingMemory.kt:81` — no recipe this tool is confident enough to write.
- `closed_type` `EvictionReason` at `mobile/app/src/main/java/com/jarvis/app/cognitive/WorkingMemory.kt:90` — no recipe this tool is confident enough to write.
- `closed_type` `InsertResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/WorkingMemory.kt:393` — no recipe this tool is confident enough to write.
- `closed_type` `AccessResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/WorkingMemory.kt:398` — no recipe this tool is confident enough to write.
- `closed_type` `CapabilityLifecycle` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Capability.kt:37` — no recipe this tool is confident enough to write.
- `closed_type` `RegisterResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityRegistry.kt:26` — no recipe this tool is confident enough to write.
- `closed_type` `TransitionResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityRegistry.kt:33` — no recipe this tool is confident enough to write.
- `closed_type` `ResolutionReason` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityResolver.kt:7` — no recipe this tool is confident enough to write.
- `closed_type` `Resolution` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityResolver.kt:20` — no recipe this tool is confident enough to write.
- `closed_type` `RiskLevel` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Descriptor.kt:29` — no recipe this tool is confident enough to write.
- `closed_type` `Availability` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Descriptor.kt:31` — no recipe this tool is confident enough to write.
- `closed_type` `ThermalCost` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Descriptor.kt:56` — no recipe this tool is confident enough to write.
- `closed_type` `LatencyClass` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Descriptor.kt:58` — no recipe this tool is confident enough to write.
- `closed_type` `CapabilityPermission` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/Descriptor.kt:62` — no recipe this tool is confident enough to write.
- `closed_type` `ActionFailure` at `mobile/app/src/main/java/com/jarvis/app/cognitive/execution/ActionPort.kt:39` — no recipe this tool is confident enough to write.
- `closed_type` `ExecutionStatus` at `mobile/app/src/main/java/com/jarvis/app/cognitive/execution/ExecutionState.kt:4` — no recipe this tool is confident enough to write.
- `closed_type` `EnvironmentOutcome` at `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneMemory.kt:96` — no recipe this tool is confident enough to write.
- `closed_type` `ProbeOutcome` at `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:229` — no recipe this tool is confident enough to write.
- `closed_type` `ResourceResponse` at `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ResourceFailure.kt:12` — no recipe this tool is confident enough to write.
- `closed_type` `ChainState` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:501` — no recipe this tool is confident enough to write.
- `closed_type` `UnknownType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:565` — no recipe this tool is confident enough to write.
- `closed_type` `PersistResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityPersistence.kt:268` — no recipe this tool is confident enough to write.
- `closed_type` `ExperienceSource` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:67` — no recipe this tool is confident enough to write.
- `closed_type` `EntityType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:98` — no recipe this tool is confident enough to write.
- `closed_type` `EntityRole` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:100` — no recipe this tool is confident enough to write.
- `closed_type` `ActionType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:111` — no recipe this tool is confident enough to write.
- `closed_type` `ActionInitiator` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:115` — no recipe this tool is confident enough to write.
- `closed_type` `Outcome` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:127` — no recipe this tool is confident enough to write.
- `closed_type` `FailureSeverity` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:138` — no recipe this tool is confident enough to write.
- `closed_type` `EvidenceType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ExperienceRecord.kt:149` — no recipe this tool is confident enough to write.
- `closed_type` `MemoryLifecycleState` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryCandidate.kt:11` — no recipe this tool is confident enough to write.
- `closed_type` `LifecycleTransition` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryCandidate.kt:64` — no recipe this tool is confident enough to write.
- `closed_type` `PromotionDecision` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryCandidate.kt:211` — no recipe this tool is confident enough to write.
- `closed_type` `ConflictType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:106` — no recipe this tool is confident enough to write.
- `closed_type` `ConflictResolutionState` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:133` — no recipe this tool is confident enough to write.
- `closed_type` `ResolutionMethod` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:187` — no recipe this tool is confident enough to write.
- `closed_type` `ConflictResolutionResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:556` — no recipe this tool is confident enough to write.
- `closed_type` `SubmitResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConsolidator.kt:472` — no recipe this tool is confident enough to write.
- `closed_type` `EvaluationResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConsolidator.kt:477` — no recipe this tool is confident enough to write.
- `closed_type` `StoreOperation` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryContinuityAdapter.kt:218` — no recipe this tool is confident enough to write.
- `closed_type` `DerivationStepType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryProvenance.kt:118` — no recipe this tool is confident enough to write.
- `closed_type` `TemporalRelationshipType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryProvenance.kt:196` — no recipe this tool is confident enough to write.
- `closed_type` `SupersessionResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:332` — no recipe this tool is confident enough to write.
- `closed_type` `UpdateResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:339` — no recipe this tool is confident enough to write.
- `closed_type` `ReinforceResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:344` — no recipe this tool is confident enough to write.
- `closed_type` `DecayResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:349` — no recipe this tool is confident enough to write.
- `closed_type` `TemporalRelationshipResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:354` — no recipe this tool is confident enough to write.
- `closed_type` `ModelDomain` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:78` — no recipe this tool is confident enough to write.
- `closed_type` `ModelFactStatus` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:86` — no recipe this tool is confident enough to write.
- `closed_type` `ModelEvidenceSource` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:131` — no recipe this tool is confident enough to write.
- `closed_type` `ConfirmationState` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:195` — no recipe this tool is confident enough to write.
- `closed_type` `Sensitivity` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:208` — no recipe this tool is confident enough to write.
- `closed_type` `ExpectedChangeRate` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:216` — no recipe this tool is confident enough to write.
- `closed_type` `UpdateClassification` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelFact.kt:245` — no recipe this tool is confident enough to write.
- `closed_type` `PersistResult` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelPersistence.kt:712` — no recipe this tool is confident enough to write.
- `closed_type` `SelfLimitationType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/SelfModel.kt:107` — no recipe this tool is confident enough to write.
- `closed_type` `SelfResourceKind` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/SelfModel.kt:123` — no recipe this tool is confident enough to write.
- `closed_type` `WorldEntityType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/WorldModel.kt:56` — no recipe this tool is confident enough to write.
- `closed_type` `AttributeValue` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/WorldModel.kt:81` — no recipe this tool is confident enough to write.
- `closed_type` `WorldEntityState` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/WorldModel.kt:98` — no recipe this tool is confident enough to write.
- `closed_type` `WorldRelationshipType` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/WorldModel.kt:153` — no recipe this tool is confident enough to write.
- `closed_type` `ConstraintKind` at `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/DecisionEngine.kt:32` — no recipe this tool is confident enough to write.
- `closed_type` `ReplanStatus` at `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/PlanGraph.kt:106` — no recipe this tool is confident enough to write.
- `closed_type` `AlertLevel` at `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/AlertLevel.kt:13` — no recipe this tool is confident enough to write.
- `closed_type` `CurrentAction` at `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/CompanionSignal.kt:76` — no recipe this tool is confident enough to write.
- `closed_type` `PresenceMode` at `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/CompanionState.kt:43` — no recipe this tool is confident enough to write.
- `closed_type` `AttentionTarget` at `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/CompanionState.kt:46` — no recipe this tool is confident enough to write.
- `closed_type` `InterruptFlag` at `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/CompanionState.kt:49` — no recipe this tool is confident enough to write.
- `closed_type` `VisualState` at `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/RenderIntent.kt:29` — no recipe this tool is confident enough to write.
- `closed_type` `AudioState` at `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/RenderIntent.kt:35` — no recipe this tool is confident enough to write.
- `closed_type` `GestureId` at `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/RenderIntent.kt:40` — no recipe this tool is confident enough to write.
- `closed_type` `RenderPriority` at `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/RenderIntent.kt:43` — no recipe this tool is confident enough to write.
- `closed_type` `EventType` at `mobile/app/src/main/java/com/jarvis/app/companioncore/contract/UserPresenceEvent.kt:21` — no recipe this tool is confident enough to write.
- `closed_type` `BridgePhase` at `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CurrentActionDiscriminator.kt:174` — no recipe this tool is confident enough to write.
- `closed_type` `SemanticRole` at `mobile/app/src/main/java/com/jarvis/app/companioncore/identity/VisualIdentity.kt:34` — no recipe this tool is confident enough to write.
- `closed_type` `OrbClip` at `mobile/app/src/main/java/com/jarvis/app/companioncore/render/OrbRenderParams.kt:14` — no recipe this tool is confident enough to write.
- `closed_type` `QualityTier` at `mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:33` — no recipe this tool is confident enough to write.
- `closed_type` `SignalKind` at `mobile/app/src/main/java/com/jarvis/app/continuity/ContinuityGate.kt:50` — no recipe this tool is confident enough to write.
- `closed_type` `ModelProviderType` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:254` — no recipe this tool is confident enough to write.
- `closed_type` `ModelSource` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:264` — no recipe this tool is confident enough to write.
- `closed_type` `MemoryPolicy` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:271` — no recipe this tool is confident enough to write.
- `closed_type` `PermissionPolicy` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:279` — no recipe this tool is confident enough to write.
- `closed_type` `ToolCapability` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:286` — no recipe this tool is confident enough to write.
- `closed_type` `SyncBehavior` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:306` — no recipe this tool is confident enough to write.
- `closed_type` `LatencyPreference` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:313` — no recipe this tool is confident enough to write.
- `closed_type` `OfflineBehavior` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:321` — no recipe this tool is confident enough to write.
- `closed_type` `UIBehavior` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:328` — no recipe this tool is confident enough to write.
- `closed_type` `SafetyRestrictions` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:335` — no recipe this tool is confident enough to write.
- `closed_type` `EnvironmentLifecycleEvent` at `mobile/app/src/main/java/com/jarvis/app/env/LiquidEnvironmentManager.kt:231` — no recipe this tool is confident enough to write.
- `closed_type` `EvolutionState` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:331` — no recipe this tool is confident enough to write.
- `closed_type` `Behavior` at `mobile/app/src/main/java/com/jarvis/app/evolution/SpecSynthesizer.kt:23` — no recipe this tool is confident enough to write.
- `closed_type` `State` at `mobile/app/src/main/java/com/jarvis/app/failure/CircuitBreaker.kt:21` — no recipe this tool is confident enough to write.
- `closed_type` `FailureSeverity` at `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:16` — no recipe this tool is confident enough to write.
- `closed_type` `FailureCategory` at `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:37` — no recipe this tool is confident enough to write.
- `closed_type` `Recoverability` at `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:67` — no recipe this tool is confident enough to write.
- `closed_type` `RecoveryAction` at `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:79` — no recipe this tool is confident enough to write.
- `closed_type` `RecoveryResult` at `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:94` — no recipe this tool is confident enough to write.
- `closed_type` `FailureStatus` at `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:103` — no recipe this tool is confident enough to write.
- `closed_type` `SubsystemStatus` at `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:214` — no recipe this tool is confident enough to write.
- `closed_type` `FailureCause` at `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:273` — no recipe this tool is confident enough to write.
- `closed_type` `ContainmentStatus` at `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:293` — no recipe this tool is confident enough to write.
- `closed_type` `DegradationLevel` at `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:302` — no recipe this tool is confident enough to write.
- `closed_type` `EnvironmentFailure` at `mobile/app/src/main/java/com/jarvis/app/failure/FailureModel.kt:353` — no recipe this tool is confident enough to write.
- `closed_type` `FederationState` at `mobile/app/src/main/java/com/jarvis/app/federation/Federation.kt:139` — no recipe this tool is confident enough to write.
- `closed_type` `FeedbackType` at `mobile/app/src/main/java/com/jarvis/app/federation/Federation.kt:162` — no recipe this tool is confident enough to write.
- `closed_type` `IsolationLevel` at `mobile/app/src/main/java/com/jarvis/app/genome/GenomeModel.kt:84` — no recipe this tool is confident enough to write.
- `closed_type` `SafetyConstraint` at `mobile/app/src/main/java/com/jarvis/app/genome/GenomeModel.kt:151` — no recipe this tool is confident enough to write.
- `closed_type` `TestType` at `mobile/app/src/main/java/com/jarvis/app/genome/GenomeModel.kt:171` — no recipe this tool is confident enough to write.
- `closed_type` `GenomeHealth` at `mobile/app/src/main/java/com/jarvis/app/genome/GenomeModel.kt:182` — no recipe this tool is confident enough to write.
- `closed_type` `MutationOperator` at `mobile/app/src/main/java/com/jarvis/app/genome/GenomeModel.kt:194` — no recipe this tool is confident enough to write.
- `closed_type` `HcEvent` at `mobile/app/src/main/java/com/jarvis/app/humancore/bus/HcEvent.kt:17` — no recipe this tool is confident enough to write.
- `closed_type` `Mode` at `mobile/app/src/main/java/com/jarvis/app/humancore/protocol/PresenceState.kt:25` — no recipe this tool is confident enough to write.
- `closed_type` `SystemTier` at `mobile/app/src/main/java/com/jarvis/app/humancore/protocol/PresenceState.kt:27` — no recipe this tool is confident enough to write.
- `closed_type` `StyledResponse` at `mobile/app/src/main/java/com/jarvis/app/humancore/protocol/StyledResponse.kt:18` — no recipe this tool is confident enough to write.
- `closed_type` `PreferenceSource` at `mobile/app/src/main/java/com/jarvis/app/humancore/store/AdaptationStore.kt:146` — no recipe this tool is confident enough to write.
- `closed_type` `StoreKind` at `mobile/app/src/main/java/com/jarvis/app/humancore/store/StoreKind.kt:20` — no recipe this tool is confident enough to write.
- `closed_type` `EntityType` at `mobile/app/src/main/java/com/jarvis/app/identity/WorldModelService.kt:183` — no recipe this tool is confident enough to write.
- `closed_type` `BiometricAuthResult` at `mobile/app/src/main/java/com/jarvis/app/identity/owner/BiometricAuthResult.kt:19` — no recipe this tool is confident enough to write.
- `closed_type` `BiometricType` at `mobile/app/src/main/java/com/jarvis/app/identity/owner/BiometricType.kt:14` — no recipe this tool is confident enough to write.
- `closed_type` `OwnerBindingOutcome` at `mobile/app/src/main/java/com/jarvis/app/identity/owner/OwnerBiometricBinder.kt:64` — no recipe this tool is confident enough to write.
- `closed_type` `InboxTaskStatus` at `mobile/app/src/main/java/com/jarvis/app/inbox/LocalTaskInbox.kt:356` — no recipe this tool is confident enough to write.
- `closed_type` `CharType` at `mobile/app/src/main/java/com/jarvis/app/language/EgyptianArabicDialectDetector.kt:156` — no recipe this tool is confident enough to write.
- `closed_type` `Bucket` at `mobile/app/src/main/java/com/jarvis/app/latency/AckSpeech.kt:127` — no recipe this tool is confident enough to write.
- `closed_type` `Phase` at `mobile/app/src/main/java/com/jarvis/app/latency/LatencyLayer.kt:57` — no recipe this tool is confident enough to write.
- `closed_type` `ProvenanceKind` at `mobile/app/src/main/java/com/jarvis/app/memory/provenance/ProvenanceLedger.kt:12` — no recipe this tool is confident enough to write.
- `closed_type` `MicroSystemStatus` at `mobile/app/src/main/java/com/jarvis/app/microsystem/MicroSystemContract.kt:104` — no recipe this tool is confident enough to write.
- `closed_type` `LoadOutcome` at `mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:448` — no recipe this tool is confident enough to write.
- `closed_type` `MessageRole` at `mobile/app/src/main/java/com/jarvis/app/model/ModelProvider.kt:86` — no recipe this tool is confident enough to write.
- `closed_type` `StreamEvent` at `mobile/app/src/main/java/com/jarvis/app/model/ModelProvider.kt:100` — no recipe this tool is confident enough to write.
- `closed_type` `FinishReason` at `mobile/app/src/main/java/com/jarvis/app/model/ModelProvider.kt:122` — no recipe this tool is confident enough to write.
- `closed_type` `ModelTier` at `mobile/app/src/main/java/com/jarvis/app/model/ModelTier.kt:16` — no recipe this tool is confident enough to write.
- `closed_type` `OrganRole` at `mobile/app/src/main/java/com/jarvis/app/model/OrganRole.kt:9` — no recipe this tool is confident enough to write.
- `closed_type` `AdmissionDecision` at `mobile/app/src/main/java/com/jarvis/app/model/ResourceGovernor.kt:19` — no recipe this tool is confident enough to write.
- `closed_type` `NativeState` at `mobile/app/src/main/java/com/jarvis/app/model/adapters/LocalInferenceEngine.kt:53` — no recipe this tool is confident enough to write.
- `closed_type` `SourceType` at `mobile/app/src/main/java/com/jarvis/app/model/config/ProviderConfig.kt:26` — no recipe this tool is confident enough to write.
- `closed_type` `AlgorithmSpec` at `mobile/app/src/main/java/com/jarvis/app/mutant/AlgorithmSpec.kt:20` — no recipe this tool is confident enough to write.
- `closed_type` `Strategy` at `mobile/app/src/main/java/com/jarvis/app/mutant/AlgorithmSpec.kt:36` — no recipe this tool is confident enough to write.
- `closed_type` `SpecOutput` at `mobile/app/src/main/java/com/jarvis/app/mutant/AlgorithmSpec.kt:94` — no recipe this tool is confident enough to write.
- `closed_type` `Expr` at `mobile/app/src/main/java/com/jarvis/app/mutant/SynthProgram.kt:213` — no recipe this tool is confident enough to write.
- `closed_type` `RuntimeSelection` at `mobile/app/src/main/java/com/jarvis/app/mutation/EnvironmentFactory.kt:169` — no recipe this tool is confident enough to write.
- `closed_type` `EnvironmentLifecycle` at `mobile/app/src/main/java/com/jarvis/app/mutation/EnvironmentFactory.kt:178` — no recipe this tool is confident enough to write.
- `closed_type` `EnvironmentStatus` at `mobile/app/src/main/java/com/jarvis/app/mutation/EnvironmentFactory.kt:184` — no recipe this tool is confident enough to write.
- `closed_type` `EvolutionState` at `mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:244` — no recipe this tool is confident enough to write.
- `closed_type` `NervousState` at `mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:82` — no recipe this tool is confident enough to write.
- `closed_type` `OrganismPhase` at `mobile/app/src/main/java/com/jarvis/app/organism/OrganismLifecycle.kt:86` — no recipe this tool is confident enough to write.
- `closed_type` `Tier` at `mobile/app/src/main/java/com/jarvis/app/policy/InferencePolicyLayer.kt:30` — no recipe this tool is confident enough to write.
- `closed_type` `ResearchDomain` at `mobile/app/src/main/java/com/jarvis/app/research/ResearchModel.kt:6` — no recipe this tool is confident enough to write.
- `closed_type` `Complexity` at `mobile/app/src/main/java/com/jarvis/app/research/ResearchModel.kt:44` — no recipe this tool is confident enough to write.
- `closed_type` `ResearchState` at `mobile/app/src/main/java/com/jarvis/app/research/ResearchPipeline.kt:138` — no recipe this tool is confident enough to write.
- `closed_type` `CandidateSource` at `mobile/app/src/main/java/com/jarvis/app/research/universal/MechanismCandidate.kt:29` — no recipe this tool is confident enough to write.
- `closed_type` `EvidenceStatus` at `mobile/app/src/main/java/com/jarvis/app/research/universal/ResearchEvidence.kt:14` — no recipe this tool is confident enough to write.
- `closed_type` `ResolveOutcome` at `mobile/app/src/main/java/com/jarvis/app/resolution/FuzzyCommandResolver.kt:50` — no recipe this tool is confident enough to write.
- `closed_type` `UnresolvedReason` at `mobile/app/src/main/java/com/jarvis/app/resolution/FuzzyCommandResolver.kt:64` — no recipe this tool is confident enough to write.
- `closed_type` `ConnectionType` at `mobile/app/src/main/java/com/jarvis/app/runtime/RuntimeBinder.kt:112` — no recipe this tool is confident enough to write.
- `closed_type` `RuntimeHealth` at `mobile/app/src/main/java/com/jarvis/app/runtime/RuntimeBinder.kt:137` — no recipe this tool is confident enough to write.
- `closed_type` `ConnectionEvent` at `mobile/app/src/main/java/com/jarvis/app/runtime/RuntimeBinder.kt:157` — no recipe this tool is confident enough to write.
- `closed_type` `OrganType` at `mobile/app/src/main/java/com/jarvis/app/selfreconfig/SystemGraph.kt:13` — no recipe this tool is confident enough to write.
- `closed_type` `EdgeKind` at `mobile/app/src/main/java/com/jarvis/app/selfreconfig/SystemGraph.kt:53` — no recipe this tool is confident enough to write.
- `closed_type` `Outcome` at `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:77` — no recipe this tool is confident enough to write.
- `closed_type` `MessageRole` at `mobile/app/src/main/java/com/jarvis/app/session/SessionManager.kt:296` — no recipe this tool is confident enough to write.
- `closed_type` `ProfileCategory` at `mobile/app/src/main/java/com/jarvis/app/social/PersonProfile.kt:35` — no recipe this tool is confident enough to write.
- `closed_type` `StatementConfidence` at `mobile/app/src/main/java/com/jarvis/app/social/PersonProfile.kt:50` — no recipe this tool is confident enough to write.
- `closed_type` `TrustTier` at `mobile/app/src/main/java/com/jarvis/app/social/RelationshipState.kt:27` — no recipe this tool is confident enough to write.
- `closed_type` `RelationshipStage` at `mobile/app/src/main/java/com/jarvis/app/social/RelationshipState.kt:44` — no recipe this tool is confident enough to write.
- `closed_type` `TTSEngine` at `mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:27` — no recipe this tool is confident enough to write.
- `closed_type` `Quality` at `mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:36` — no recipe this tool is confident enough to write.
- `closed_type` `SpeakResult` at `mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:83` — no recipe this tool is confident enough to write.
- `closed_type` `State` at `mobile/app/src/main/java/com/jarvis/app/vad/SileroVadManager.kt:49` — no recipe this tool is confident enough to write.
- `closed_type` `Profile` at `mobile/app/src/main/java/com/jarvis/app/vad/SileroVadManager.kt:58` — no recipe this tool is confident enough to write.
- `closed_type` `GateVerdict` at `mobile/app/src/main/java/com/jarvis/app/validation/PromotionGate.kt:60` — no recipe this tool is confident enough to write.
- `closed_type` `AnimationPath` at `mobile/app/src/main/java/com/jarvis/app/vf/animation/AnimationEngine.kt:49` — no recipe this tool is confident enough to write.
- `closed_type` `ParticleDirection` at `mobile/app/src/main/java/com/jarvis/app/vf/particle/ParticleEngine.kt:70` — no recipe this tool is confident enough to write.
- `closed_type` `ReactorSpecState` at `mobile/app/src/main/java/com/jarvis/app/vf/reactor/ReactorEngine.kt:25` — no recipe this tool is confident enough to write.
- `closed_type` `PulseMode` at `mobile/app/src/main/java/com/jarvis/app/vf/reactor/ReactorEngine.kt:67` — no recipe this tool is confident enough to write.
- `closed_type` `JvColorFamily` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:140` — no recipe this tool is confident enough to write.
- `closed_type` `JvReactorStateColor` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:159` — no recipe this tool is confident enough to write.
- `closed_type` `HotwordAvailability` at `mobile/app/src/main/java/com/jarvis/app/voice/HotwordAvailabilityProbe.kt:16` — no recipe this tool is confident enough to write.
- `closed_type` `VoiceForgeSynthesisResponse` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceForgeAdapter.kt:47` — no recipe this tool is confident enough to write.
- `closed_type` `VoiceForgeOutcome` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceForgeBackend.kt:35` — no recipe this tool is confident enough to write.
- `closed_type` `Kind` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceMetrics.kt:15` — no recipe this tool is confident enough to write.
- `closed_type` `ModelState` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceModelManager.kt:52` — no recipe this tool is confident enough to write.
- `closed_type` `State` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOrganismHost.kt:53` — no recipe this tool is confident enough to write.
- `closed_type` `VoiceState` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOrganismHost.kt:56` — no recipe this tool is confident enough to write.
- `closed_type` `ResourcePreference` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceProfile.kt:34` — no recipe this tool is confident enough to write.
- `closed_type` `VoiceQuality` at `mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProvider.kt:110` — no recipe this tool is confident enough to write.
- `closed_type` `Selection` at `mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProviderSelector.kt:23` — no recipe this tool is confident enough to write.
- `fixed_classification` `MediaTransportCommand -> {NEXT_TRACK, PAUSE, PLAY}` at `mobile/app/src/main/java/com/jarvis/app/android/MediaSessionControlBackend.kt:144` — no recipe this tool is confident enough to write.
- `fixed_classification` `RiskLevel -> {LOW, MEDIUM}` at `mobile/app/src/main/java/com/jarvis/app/approval/ApprovalGate.kt:102` — no recipe this tool is confident enough to write.
- `fixed_classification` `BodyState -> {ERROR, HEARING, IDLE, INTERRUPTED, LEARNING, LISTENING, RESPONDING, SPEAKING, THINKING, WAKE}` at `mobile/app/src/main/java/com/jarvis/app/body/BodyStateMachine.kt:14` — no recipe this tool is confident enough to write.
- `fixed_classification` `AlertLevel -> {CRITICAL, NONE, WARNING}` at `mobile/app/src/main/java/com/jarvis/app/body/VisualStateBridge.kt:60` — no recipe this tool is confident enough to write.
- `fixed_classification` `PresenceMode -> {ASLEEP, IDLE, LISTENING, SPEAKING, THINKING, WAKING}` at `mobile/app/src/main/java/com/jarvis/app/body/VisualStateBridge.kt:90` — no recipe this tool is confident enough to write.
- `fixed_classification` `ChangeLayer -> {CORE, FAST, SLOW}` at `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ChangeLayer.kt:88` — no recipe this tool is confident enough to write.
- `fixed_classification` `IntentCategory -> {COMMAND, QUESTION, STATEMENT}` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1328` — no recipe this tool is confident enough to write.
- `fixed_classification` `TurnDecision -> {DIRECT_REPLY, PLAN}` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1349` — no recipe this tool is confident enough to write.
- `fixed_classification` `FailureCause -> {INTERNAL_ERROR, TIMEOUT, UNSUPPORTED}` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityInvoker.kt:198` — no recipe this tool is confident enough to write.
- `fixed_classification` `ResourceResponse -> {NORMAL, PAUSE, REDUCE_CONCURRENCY, STOP_OPTIONAL_WORK, UNLOAD_EXPENSIVE_CAPABILITY}` at `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ResourceFailure.kt:28` — no recipe this tool is confident enough to write.
- `fixed_classification` `DegradationLevel -> {DEGRADED, FULL, LIMITED, OFFLINE, UNAVAILABLE}` at `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ResourceFailure.kt:48` — no recipe this tool is confident enough to write.
- `fixed_classification` `PromotionDecision -> {PROMOTE, REJECT}` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryCandidate.kt:136` — no recipe this tool is confident enough to write.
- `fixed_classification` `ResolutionMethod -> {DEFERRED, HIGHEST_CONFIDENCE, HIGHEST_RELIABILITY, LAST_WRITER_WINS, USER_INSTRUCTION}` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:334` — no recipe this tool is confident enough to write.
- `fixed_classification` `ModelFactStatus -> {CONFIRMED, INFERRED, OBSERVED}` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelUpdateEngine.kt:197` — no recipe this tool is confident enough to write.
- `fixed_classification` `SubgoalStatus -> {BLOCKED, COMPLETED, FAILED, IN_PROGRESS, PENDING}` at `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/GoalPlanner.kt:226` — no recipe this tool is confident enough to write.
- `fixed_classification` `GoalStatus -> {ACTIVE, BLOCKED, COMPLETED, CREATED, FAILED}` at `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/GoalPlanner.kt:242` — no recipe this tool is confident enough to write.
- `fixed_classification` `PlanStatus -> {BLOCKED, COMPLETED, EXECUTING, FAILED, PAUSED, PENDING}` at `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/GoalPlanner.kt:370` — no recipe this tool is confident enough to write.
- `fixed_classification` `BridgePhase -> {ERROR, OK, SENDING}` at `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CurrentActionDiscriminator.kt:178` — no recipe this tool is confident enough to write.
- `fixed_classification` `AlertLevel -> {NONE, WARNING}` at `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/HumanCoreIntegration.kt:73` — no recipe this tool is confident enough to write.
- `fixed_classification` `OrbClip -> {IDLE_BREATHE, LISTENING_RIPPLE, NOTIFICATION_GLOW, SHUTDOWN_FADE, SLEEP_DIM, SPEAKING_PULSE, THINKING_SWIRL, WAKE_BLOOM}` at `mobile/app/src/main/java/com/jarvis/app/companioncore/render/OrbRenderParams.kt:31` — no recipe this tool is confident enough to write.
- `fixed_classification` `OrbClip -> {IDLE_BREATHE, LISTENING_RIPPLE, SHUTDOWN_FADE, SLEEP_DIM, SPEAKING_PULSE, THINKING_SWIRL, WAKE_BLOOM}` at `mobile/app/src/main/java/com/jarvis/app/companioncore/render/OrbStateMachine.kt:120` — no recipe this tool is confident enough to write.
- `fixed_classification` `AudioState -> {LISTENING_EARCON, NONE, SPEAKING, WAKE_EARCON}` at `mobile/app/src/main/java/com/jarvis/app/companioncore/render/OrbStateMachine.kt:169` — no recipe this tool is confident enough to write.
- `fixed_classification` `RecoveryAction -> {RETRY, RETRY_WITH_BACKOFF}` at `mobile/app/src/main/java/com/jarvis/app/failure/RecoveryController.kt:185` — no recipe this tool is confident enough to write.
- `fixed_classification` `BiometricType -> {BIOMETRIC_STRONG, BIOMETRIC_WEAK, DEVICE_CREDENTIAL, UNKNOWN}` at `mobile/app/src/main/java/com/jarvis/app/identity/owner/BiometricType.kt:34` — no recipe this tool is confident enough to write.
- `fixed_classification` `Bucket -> {NEUTRAL, POSITIVE}` at `mobile/app/src/main/java/com/jarvis/app/latency/AckSpeech.kt:138` — no recipe this tool is confident enough to write.
- `fixed_classification` `OrganRole -> {REASONING, RESIDENT}` at `mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:273` — no recipe this tool is confident enough to write.
- `fixed_classification` `Tier -> {COMPLEX, MICRO, NORMAL}` at `mobile/app/src/main/java/com/jarvis/app/policy/InferencePolicyLayer.kt:162` — no recipe this tool is confident enough to write.
- `fixed_classification` `ParticleDirection -> {DRIFT_OUT, INWARD, OUTWARD, SPIRAL_IN}` at `mobile/app/src/main/java/com/jarvis/app/vf/particle/ParticleEngine.kt:54` — no recipe this tool is confident enough to write.
- `meaning_arbiter` `PronounResolver` at `mobile/app/src/main/java/com/jarvis/app/cognitive/PronounResolver.kt:12` — no recipe this tool is confident enough to write.
- `meaning_arbiter` `MemoryConflictResolver` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:218` — no recipe this tool is confident enough to write.
- `meaning_arbiter` `MemoryPolicy` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:271` — no recipe this tool is confident enough to write.
- `meaning_arbiter` `PermissionPolicy` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:279` — no recipe this tool is confident enough to write.
- `meaning_arbiter` `ConflictResolver` at `mobile/app/src/main/java/com/jarvis/app/humancore/algo/ConflictResolver.kt:30` — no recipe this tool is confident enough to write.
- `meaning_arbiter` `ModelPathResolver` at `mobile/app/src/main/java/com/jarvis/app/model/storage/ModelPathResolver.kt:22` — no recipe this tool is confident enough to write.
- `named_wire` `fileStorage: FileStorage` at `mobile/app/src/main/java/com/jarvis/app/alerts/LocalAlertStore.kt:23` — no recipe this tool is confident enough to write.
- `named_wire` `modelManager: ModelManager` at `mobile/app/src/main/java/com/jarvis/app/anchor/AnchorEngine.kt:34` — no recipe this tool is confident enough to write.
- `named_wire` `gateway: CognitiveAdmissionPolicy` at `mobile/app/src/main/java/com/jarvis/app/anchor/AnchorEngine.kt:35` — no recipe this tool is confident enough to write.
- `named_wire` `fileStorage: FileStorage` at `mobile/app/src/main/java/com/jarvis/app/approvals/LocalApprovalQueue.kt:23` — no recipe this tool is confident enough to write.
- `named_wire` `memoryStore: MemoryStore` at `mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt:20` — no recipe this tool is confident enough to write.
- `named_wire` `vocabularyStore: VocabularyStore` at `mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt:21` — no recipe this tool is confident enough to write.
- `named_wire` `localInferenceEngine: LocalInferenceEngine` at `mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt:22` — no recipe this tool is confident enough to write.
- `named_wire` `modelManager: ModelManager` at `mobile/app/src/main/java/com/jarvis/app/body/BodyCoordinator.kt:53` — no recipe this tool is confident enough to write.
- `named_wire` `companionIntegration: HumanCoreIntegration` at `mobile/app/src/main/java/com/jarvis/app/body/BodyCoordinator.kt:55` — no recipe this tool is confident enough to write.
- `named_wire` `memoryStore: MemoryStore` at `mobile/app/src/main/java/com/jarvis/app/body/BodyCoordinator.kt:57` — no recipe this tool is confident enough to write.
- `named_wire` `memoryStore: MemoryStore` at `mobile/app/src/main/java/com/jarvis/app/body/MemoryPromotion.kt:32` — no recipe this tool is confident enough to write.
- `named_wire` `vocabularyStore: VocabularyStore` at `mobile/app/src/main/java/com/jarvis/app/body/MemoryPromotion.kt:33` — no recipe this tool is confident enough to write.
- `named_wire` `context: Context` at `mobile/app/src/main/java/com/jarvis/app/body/PresenceMonitor.kt:40` — no recipe this tool is confident enough to write.
- `named_wire` `languageRouter: LanguageRouter` at `mobile/app/src/main/java/com/jarvis/app/body/StreamingTts.kt:44` — no recipe this tool is confident enough to write.
- `named_wire` `companionIntegration: HumanCoreIntegration` at `mobile/app/src/main/java/com/jarvis/app/body/VisualStateBridge.kt:25` — no recipe this tool is confident enough to write.
- `named_wire` `context: Context` at `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:25` — no recipe this tool is confident enough to write.
- `named_wire` `modelManager: ModelManager` at `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:28` — no recipe this tool is confident enough to write.
- `named_wire` `runtimeBinder: RuntimeBinder` at `mobile/app/src/main/java/com/jarvis/app/bootstrap/BootstrapManager.kt:29` — no recipe this tool is confident enough to write.
- `named_wire` `registry: MicroSystemRegistry` at `mobile/app/src/main/java/com/jarvis/app/builder/CapabilityGapDetector.kt:20` — no recipe this tool is confident enough to write.
- `named_wire` `genomeRegistry: GenomeRegistry` at `mobile/app/src/main/java/com/jarvis/app/builder/CapabilityGapDetector.kt:21` — no recipe this tool is confident enough to write.
- `named_wire` `gapDetector: CapabilityGapDetector` at `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:34` — no recipe this tool is confident enough to write.
- `named_wire` `research: UniversalResearchEngine` at `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:35` — no recipe this tool is confident enough to write.
- `named_wire` `evolution: EvolutionEngine` at `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:36` — no recipe this tool is confident enough to write.
- `named_wire` `registry: MicroSystemRegistry` at `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:37` — no recipe this tool is confident enough to write.
- `named_wire` `failureSurface: FailureSurface` at `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:41` — no recipe this tool is confident enough to write.
- `named_wire` `spec: ToolSpecification` at `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:166` — no recipe this tool is confident enough to write.
- `named_wire` `capabilityRegistry: CapabilityRegistry` at `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityArbitrator.kt:27` — no recipe this tool is confident enough to write.
- `named_wire` `resourceGovernor: ResourceGovernor` at `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityArbitrator.kt:28` — no recipe this tool is confident enough to write.
- `named_wire` `registry: CapabilityRegistry` at `mobile/app/src/main/java/com/jarvis/app/capability/DeterministicCapabilityRouter.kt:21` — no recipe this tool is confident enough to write.
- `named_wire` `item: AttentionItem` at `mobile/app/src/main/java/com/jarvis/app/cognitive/AttentionEngine.kt:270` — no recipe this tool is confident enough to write.
- `named_wire` `modelManager: ModelManager` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:60` — no recipe this tool is confident enough to write.
- `named_wire` `goalPlanner: GoalPlanner` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:62` — no recipe this tool is confident enough to write.
- `named_wire` `decisionEngine: DecisionEngine` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:63` — no recipe this tool is confident enough to write.
- `named_wire` `failure: FailureEvent` at `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1626` — no recipe this tool is confident enough to write.
- `named_wire` `topicTracker: TopicTracker` at `mobile/app/src/main/java/com/jarvis/app/cognitive/ContextWindowAssembler.kt:34` — no recipe this tool is confident enough to write.
- `named_wire` `salienceScorer: SalienceScorer` at `mobile/app/src/main/java/com/jarvis/app/cognitive/ContextWindowAssembler.kt:35` — no recipe this tool is confident enough to write.
- `named_wire` `blendedRetriever: BlendedMemoryRetriever` at `mobile/app/src/main/java/com/jarvis/app/cognitive/ContextWindowAssembler.kt:45` — no recipe this tool is confident enough to write.
- `named_wire` `referenceStore: ReferenceStore` at `mobile/app/src/main/java/com/jarvis/app/cognitive/PronounResolver.kt:13` — no recipe this tool is confident enough to write.
- `named_wire` `salienceScorer: SalienceScorer` at `mobile/app/src/main/java/com/jarvis/app/cognitive/PronounResolver.kt:15` — no recipe this tool is confident enough to write.
- `named_wire` `referenceStore: ReferenceStore` at `mobile/app/src/main/java/com/jarvis/app/cognitive/SalienceScorer.kt:13` — no recipe this tool is confident enough to write.
- `named_wire` `state: ExecutionState` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityExecutor.kt:54` — no recipe this tool is confident enough to write.
- `named_wire` `verdict: PartialFailureVerdict` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityExecutor.kt:385` — no recipe this tool is confident enough to write.
- `named_wire` `registry: CapabilityRegistry` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityFabric.kt:26` — no recipe this tool is confident enough to write.
- `named_wire` `registry: CapabilityRegistry` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityInvoker.kt:46` — no recipe this tool is confident enough to write.
- `named_wire` `resolver: CapabilityResolver` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityInvoker.kt:47` — no recipe this tool is confident enough to write.
- `named_wire` `immune: ImmuneSystem` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityInvoker.kt:48` — no recipe this tool is confident enough to write.
- `named_wire` `from: CapabilityLifecycle` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityRegistry.kt:16` — no recipe this tool is confident enough to write.
- `named_wire` `registry: CapabilityRegistry` at `mobile/app/src/main/java/com/jarvis/app/cognitive/capability/CapabilityResolver.kt:31` — no recipe this tool is confident enough to write.
- `named_wire` `retryPolicy: RetryPolicy` at `mobile/app/src/main/java/com/jarvis/app/cognitive/execution/ExecutionEngine.kt:46` — no recipe this tool is confident enough to write.
- `named_wire` `surface: FailureSurface` at `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:39` — no recipe this tool is confident enough to write.
- `named_wire` `containment: ContainmentRegistry` at `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:40` — no recipe this tool is confident enough to write.
- `named_wire` `degradation: DegradedModeController` at `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:41` — no recipe this tool is confident enough to write.
- `named_wire` `dependencyGraph: DependencyGraph` at `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:42` — no recipe this tool is confident enough to write.
- `named_wire` `memory: ImmuneMemory` at `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:43` — no recipe this tool is confident enough to write.
- `named_wire` `backoffPolicy: BackoffPolicy` at `mobile/app/src/main/java/com/jarvis/app/cognitive/immune/ImmuneSystem.kt:44` — no recipe this tool is confident enough to write.
- `named_wire` `consolidator: MemoryConsolidator` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:28` — no recipe this tool is confident enough to write.
- `named_wire` `memory: ConsolidatedMemory` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:481` — no recipe this tool is confident enough to write.
- `named_wire` `provenance: MemoryProvenance` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityManager.kt:528` — no recipe this tool is confident enough to write.
- `named_wire` `updateEngine: MemoryUpdateEngine` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryConflictResolver.kt:219` — no recipe this tool is confident enough to write.
- `named_wire` `consolidator: MemoryConsolidator` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryContinuityAdapter.kt:27` — no recipe this tool is confident enough to write.
- `named_wire` `continuityManager: ContinuityManager` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryContinuityAdapter.kt:29` — no recipe this tool is confident enough to write.
- `named_wire` `workingMemory: WorkingMemory` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryContinuityAdapter.kt:30` — no recipe this tool is confident enough to write.
- `named_wire` `consolidator: MemoryConsolidator` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/MemoryUpdateEngine.kt:18` — no recipe this tool is confident enough to write.
- `named_wire` `query: ModelQuery` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelContextPort.kt:52` — no recipe this tool is confident enough to write.
- `named_wire` `self: SelfModel` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelStore.kt:17` — no recipe this tool is confident enough to write.
- `named_wire` `user: UserModel` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelStore.kt:20` — no recipe this tool is confident enough to write.
- `named_wire` `world: WorldModel` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelStore.kt:23` — no recipe this tool is confident enough to write.
- `named_wire` `engine: ModelUpdateEngine` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelSynchronization.kt:25` — no recipe this tool is confident enough to write.
- `named_wire` `fact: ModelFact` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelUpdateEngine.kt:428` — no recipe this tool is confident enough to write.
- `named_wire` `confidence: ModelConfidence` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/WorldModel.kt:187` — no recipe this tool is confident enough to write.
- `named_wire` `resourceState: ResourceState` at `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/DecisionEngine.kt:61` — no recipe this tool is confident enough to write.
- `named_wire` `graph: PlanGraph` at `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/GoalPlanner.kt:55` — no recipe this tool is confident enough to write.
- `named_wire` `discriminator: CurrentActionDiscriminator` at `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/HumanCoreIntegration.kt:42` — no recipe this tool is confident enough to write.
- `named_wire` `integration: HumanCoreIntegration` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:50` — no recipe this tool is confident enough to write.
- `named_wire` `presenceEngine: PresenceEngine` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:51` — no recipe this tool is confident enough to write.
- `named_wire` `orbStateMachine: OrbStateMachine` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:52` — no recipe this tool is confident enough to write.
- `named_wire` `animationController: AnimationController` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:53` — no recipe this tool is confident enough to write.
- `named_wire` `resourceManagement: MobileResourceManagement` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:54` — no recipe this tool is confident enough to write.
- `named_wire` `clock: CompanionClock` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:55` — no recipe this tool is confident enough to write.
- `named_wire` `clock: CompanionClock` at `mobile/app/src/main/java/com/jarvis/app/companioncore/render/AnimationController.kt:21` — no recipe this tool is confident enough to write.
- `named_wire` `blendedRetriever: BlendedMemoryRetriever` at `mobile/app/src/main/java/com/jarvis/app/continuity/ContinuityGate.kt:43` — no recipe this tool is confident enough to write.
- `named_wire` `fileStorage: FileStorage` at `mobile/app/src/main/java/com/jarvis/app/env/EnvironmentRepository.kt:13` — no recipe this tool is confident enough to write.
- `named_wire` `modelManager: ModelManager` at `mobile/app/src/main/java/com/jarvis/app/env/LiquidEnvironmentManager.kt:35` — no recipe this tool is confident enough to write.
- `named_wire` `runtimeBinder: RuntimeBinder` at `mobile/app/src/main/java/com/jarvis/app/env/LiquidEnvironmentManager.kt:36` — no recipe this tool is confident enough to write.
- `named_wire` `archive: GenomeArchive` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:47` — no recipe this tool is confident enough to write.
- `named_wire` `governor: ResourceGovernor` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:50` — no recipe this tool is confident enough to write.
- `named_wire` `failureSurface: FailureSurface` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:51` — no recipe this tool is confident enough to write.
- `named_wire` `candidateGenerator: CandidateGenerator` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:52` — no recipe this tool is confident enough to write.
- `named_wire` `mutantEnv: MutantEnvironment` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:53` — no recipe this tool is confident enough to write.
- `named_wire` `testRunner: CandidateTestRunner` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:54` — no recipe this tool is confident enough to write.
- `named_wire` `failureRunner: FailureTestRunner` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:55` — no recipe this tool is confident enough to write.
- `named_wire` `resourceBenchmark: ResourceBenchmark` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:56` — no recipe this tool is confident enough to write.
- `named_wire` `regressionRunner: RegressionRunner` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:57` — no recipe this tool is confident enough to write.
- `named_wire` `gate: PromotionGate` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:58` — no recipe this tool is confident enough to write.
- `named_wire` `promotionController: PromotionController` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:59` — no recipe this tool is confident enough to write.
- `named_wire` `mutationEngine: MutationEngine` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:61` — no recipe this tool is confident enough to write.
- `named_wire` `selfRepairLoop: SelfRepairLoop` at `mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt:70` — no recipe this tool is confident enough to write.
- `named_wire` `provider: LlmSynthesisProvider` at `mobile/app/src/main/java/com/jarvis/app/evolution/LlmSpecSynthesizer.kt:27` — no recipe this tool is confident enough to write.
- `named_wire` `registry: MicroSystemRegistry` at `mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt:33` — no recipe this tool is confident enough to write.
- `named_wire` `archive: GenomeArchive` at `mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt:34` — no recipe this tool is confident enough to write.
- `named_wire` `genomeRegistry: GenomeRegistry` at `mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt:35` — no recipe this tool is confident enough to write.
- `named_wire` `failureSurface: FailureSurface` at `mobile/app/src/main/java/com/jarvis/app/evolution/PromotionController.kt:36` — no recipe this tool is confident enough to write.
- `named_wire` `registry: MicroSystemRegistry` at `mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt:28` — no recipe this tool is confident enough to write.
- `named_wire` `archive: GenomeArchive` at `mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt:29` — no recipe this tool is confident enough to write.
- `named_wire` `genomeRegistry: GenomeRegistry` at `mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt:30` — no recipe this tool is confident enough to write.
- `named_wire` `failureSurface: FailureSurface` at `mobile/app/src/main/java/com/jarvis/app/evolution/RollbackController.kt:31` — no recipe this tool is confident enough to write.
- `named_wire` `fileStorage: FileStorage` at `mobile/app/src/main/java/com/jarvis/app/failure/FailureStore.kt:24` — no recipe this tool is confident enough to write.
- `named_wire` `surface: FailureSurface` at `mobile/app/src/main/java/com/jarvis/app/failure/RecoveryController.kt:26` — no recipe this tool is confident enough to write.
- `named_wire` `fileStorage: FileStorage` at `mobile/app/src/main/java/com/jarvis/app/genome/GenomeArchive.kt:24` — no recipe this tool is confident enough to write.
- `named_wire` `relationship: RelationshipStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/BondFormation.kt:22` — no recipe this tool is confident enough to write.
- `named_wire` `ism: InternalStateManager` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/CompanionBehaviorOrchestrator.kt:24` — no recipe this tool is confident enough to write.
- `named_wire` `values: ValuesSystem` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConsistencyGuard.kt:42` — no recipe this tool is confident enough to write.
- `named_wire` `presence: PresenceManager` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConsistencyGuard.kt:43` — no recipe this tool is confident enough to write.
- `named_wire` `relationship: RelationshipStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConsistencyGuard.kt:44` — no recipe this tool is confident enough to write.
- `named_wire` `personality: PersonalityEngine` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConversationStyleController.kt:37` — no recipe this tool is confident enough to write.
- `named_wire` `mood: MoodSystem` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConversationStyleController.kt:38` — no recipe this tool is confident enough to write.
- `named_wire` `adaptation: UserAdaptation` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ConversationStyleController.kt:39` — no recipe this tool is confident enough to write.
- `named_wire` `mood: MoodStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/EmotionalRegulation.kt:29` — no recipe this tool is confident enough to write.
- `named_wire` `personality: PersonalityStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/EmotionalRegulation.kt:30` — no recipe this tool is confident enough to write.
- `named_wire` `personality: PersonalityStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/GrowthEngine.kt:29` — no recipe this tool is confident enough to write.
- `named_wire` `identity: IdentityStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/IdentityRevisionHandler.kt:31` — no recipe this tool is confident enough to write.
- `named_wire` `dialogue: DialogueLog` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalDialogueEngine.kt:35` — no recipe this tool is confident enough to write.
- `named_wire` `ism: InternalStateManager` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalDialogueEngine.kt:36` — no recipe this tool is confident enough to write.
- `named_wire` `identity: IdentityKernel` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:26` — no recipe this tool is confident enough to write.
- `named_wire` `personality: PersonalityStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:27` — no recipe this tool is confident enough to write.
- `named_wire` `mood: MoodStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:28` — no recipe this tool is confident enough to write.
- `named_wire` `relationship: RelationshipStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:29` — no recipe this tool is confident enough to write.
- `named_wire` `presence: PresenceManager` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/InternalStateManager.kt:30` — no recipe this tool is confident enough to write.
- `named_wire` `mood: MoodStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/MoodSystem.kt:26` — no recipe this tool is confident enough to write.
- `named_wire` `relationship: RelationshipStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/RelationshipModeling.kt:31` — no recipe this tool is confident enough to write.
- `named_wire` `dialogue: DialogueLog` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/SelfReflection.kt:30` — no recipe this tool is confident enough to write.
- `named_wire` `relationship: RelationshipStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/SelfReflection.kt:31` — no recipe this tool is confident enough to write.
- `named_wire` `ism: InternalStateManager` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/SelfReflection.kt:32` — no recipe this tool is confident enough to write.
- `named_wire` `relationship: RelationshipStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/TrustModeling.kt:29` — no recipe this tool is confident enough to write.
- `named_wire` `store: AdaptationStore` at `mobile/app/src/main/java/com/jarvis/app/humancore/mod/UserAdaptation.kt:41` — no recipe this tool is confident enough to write.
- `named_wire` `styleController: ConversationStyleController` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/ExpressionPass.kt:27` — no recipe this tool is confident enough to write.
- `named_wire` `guard: ConsistencyGuard` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/ExpressionPass.kt:28` — no recipe this tool is confident enough to write.
- `named_wire` `ism: InternalStateManager` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/ExpressionPass.kt:29` — no recipe this tool is confident enough to write.
- `named_wire` `emotionalRegulation: EmotionalRegulation` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:39` — no recipe this tool is confident enough to write.
- `named_wire` `relationshipModeling: RelationshipModeling` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:40` — no recipe this tool is confident enough to write.
- `named_wire` `trustModeling: TrustModeling` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:41` — no recipe this tool is confident enough to write.
- `named_wire` `adaptation: UserAdaptation` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:42` — no recipe this tool is confident enough to write.
- `named_wire` `dialogueEngine: InternalDialogueEngine` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:43` — no recipe this tool is confident enough to write.
- `named_wire` `growthEngine: GrowthEngine` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:44` — no recipe this tool is confident enough to write.
- `named_wire` `memory: MemoryInterface` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:45` — no recipe this tool is confident enough to write.
- `named_wire` `bondFormation: BondFormation` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:46` — no recipe this tool is confident enough to write.
- `named_wire` `selfReflection: SelfReflection` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/IntegrationPass.kt:47` — no recipe this tool is confident enough to write.
- `named_wire` `ei: EmotionalIntelligence` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/PerceptionPass.kt:29` — no recipe this tool is confident enough to write.
- `named_wire` `si: SocialIntelligence` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/PerceptionPass.kt:30` — no recipe this tool is confident enough to write.
- `named_wire` `ism: InternalStateManager` at `mobile/app/src/main/java/com/jarvis/app/humancore/pipeline/PerceptionPass.kt:32` — no recipe this tool is confident enough to write.
- `named_wire` `registry: StoreRegistry` at `mobile/app/src/main/java/com/jarvis/app/humancore/store/SyncPort.kt:41` — no recipe this tool is confident enough to write.
- `named_wire` `worldModel: WorldModelService` at `mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt:29` — no recipe this tool is confident enough to write.
- `named_wire` `userProfile: UserProfile` at `mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt:30` — no recipe this tool is confident enough to write.
- `named_wire` `selfModel: SelfModel` at `mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt:32` — no recipe this tool is confident enough to write.
- `named_wire` `personaTuner: PersonaTuner` at `mobile/app/src/main/java/com/jarvis/app/identity/IdentityContext.kt:33` — no recipe this tool is confident enough to write.
- `named_wire` `worldModel: WorldModelService` at `mobile/app/src/main/java/com/jarvis/app/identity/PersonaTuner.kt:20` — no recipe this tool is confident enough to write.
- `named_wire` `capabilityRegistry: CapabilityRegistry` at `mobile/app/src/main/java/com/jarvis/app/identity/SelfModel.kt:24` — no recipe this tool is confident enough to write.
- `named_wire` `worldModel: WorldModelService` at `mobile/app/src/main/java/com/jarvis/app/identity/UserProfile.kt:22` — no recipe this tool is confident enough to write.
- `named_wire` `fileStorage: FileStorage` at `mobile/app/src/main/java/com/jarvis/app/inbox/LocalTaskInbox.kt:24` — no recipe this tool is confident enough to write.
- `named_wire` `scorer: MemoryImportanceScorer` at `mobile/app/src/main/java/com/jarvis/app/memory/BlendedMemoryRetriever.kt:60` — no recipe this tool is confident enough to write.
- `named_wire` `userProfile: UserProfile` at `mobile/app/src/main/java/com/jarvis/app/memory/MemoryConsolidationLoop.kt:52` — no recipe this tool is confident enough to write.
- `named_wire` `sandbox: ResourceSandbox` at `mobile/app/src/main/java/com/jarvis/app/microsystem/SpecMicroSystem.kt:33` — no recipe this tool is confident enough to write.
- `named_wire` `resourceGovernor: ResourceGovernor` at `mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:48` — no recipe this tool is confident enough to write.
- `named_wire` `ollama: OllamaAdapter` at `mobile/app/src/main/java/com/jarvis/app/model/OllamaModelBackend.kt:30` — no recipe this tool is confident enough to write.
- `named_wire` `usage: TokenUsage` at `mobile/app/src/main/java/com/jarvis/app/model/adapters/RemoteJarvisAdapter.kt:77` — no recipe this tool is confident enough to write.
- `named_wire` `context: Context` at `mobile/app/src/main/java/com/jarvis/app/model/storage/ModelPathResolver.kt:23` — no recipe this tool is confident enough to write.
- `named_wire` `factory: EnvironmentFactory` at `mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt:27` — no recipe this tool is confident enough to write.
- `named_wire` `backend: ProcessLocalBackend` at `mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt:28` — no recipe this tool is confident enough to write.
- `named_wire` `sandbox: ResourceSandbox` at `mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt:29` — no recipe this tool is confident enough to write.
- `named_wire` `artifactManager: ArtifactManager` at `mobile/app/src/main/java/com/jarvis/app/mutant/MutantEnvironment.kt:30` — no recipe this tool is confident enough to write.
- `named_wire` `sandbox: ResourceSandbox` at `mobile/app/src/main/java/com/jarvis/app/mutant/ProcessLocalBackend.kt:26` — no recipe this tool is confident enough to write.
- `named_wire` `failureSurface: FailureSurface` at `mobile/app/src/main/java/com/jarvis/app/mutation/EnvironmentFactory.kt:21` — no recipe this tool is confident enough to write.
- `named_wire` `archive: GenomeArchive` at `mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:29` — no recipe this tool is confident enough to write.
- `named_wire` `resourceGovernor: ResourceGovernor` at `mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:31` — no recipe this tool is confident enough to write.
- `named_wire` `failureSurface: FailureSurface` at `mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:32` — no recipe this tool is confident enough to write.
- `named_wire` `mutationEngine: MutationEngine` at `mobile/app/src/main/java/com/jarvis/app/mutation/EvolutionLoop.kt:33` — no recipe this tool is confident enough to write.
- `named_wire` `parent: Genome` at `mobile/app/src/main/java/com/jarvis/app/mutation/MutationEngine.kt:70` — no recipe this tool is confident enough to write.
- `named_wire` `registry: MicroSystemRegistry` at `mobile/app/src/main/java/com/jarvis/app/nervous/CapabilityRouter.kt:28` — no recipe this tool is confident enough to write.
- `named_wire` `router: CapabilityRouter` at `mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:30` — no recipe this tool is confident enough to write.
- `named_wire` `coordinator: OrganismCoordinator` at `mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:31` — no recipe this tool is confident enough to write.
- `named_wire` `predictive: PredictiveRouter` at `mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:32` — no recipe this tool is confident enough to write.
- `named_wire` `registry: MicroSystemRegistry` at `mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:34` — no recipe this tool is confident enough to write.
- `named_wire` `governor: ResourceGovernor` at `mobile/app/src/main/java/com/jarvis/app/nervous/GlobalNervousSystem.kt:35` — no recipe this tool is confident enough to write.
- `named_wire` `registry: MicroSystemRegistry` at `mobile/app/src/main/java/com/jarvis/app/nervous/OrganismCoordinator.kt:25` — no recipe this tool is confident enough to write.
- `named_wire` `router: CapabilityRouter` at `mobile/app/src/main/java/com/jarvis/app/nervous/OrganismCoordinator.kt:26` — no recipe this tool is confident enough to write.
- `named_wire` `failureSurface: FailureSurface` at `mobile/app/src/main/java/com/jarvis/app/onefriday/AndroidPlatformPorts.kt:35` — no recipe this tool is confident enough to write.
- `named_wire` `selfModel: SelfModel` at `mobile/app/src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt:103` — no recipe this tool is confident enough to write.
- `named_wire` `modelManager: ModelManager` at `mobile/app/src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt:123` — no recipe this tool is confident enough to write.
- `named_wire` `router: CloudModelRouter` at `mobile/app/src/main/java/com/jarvis/app/research/universal/CloudResearchProvider.kt:20` — no recipe this tool is confident enough to write.
- `named_wire` `capabilityRouter: CapabilityRouter` at `mobile/app/src/main/java/com/jarvis/app/resolution/FuzzyCommandResolver.kt:30` — no recipe this tool is confident enough to write.
- `named_wire` `registry: CapabilityRegistry` at `mobile/app/src/main/java/com/jarvis/app/resolution/FuzzyCommandResolver.kt:31` — no recipe this tool is confident enough to write.
- `named_wire` `sandbox: ResourceSandbox` at `mobile/app/src/main/java/com/jarvis/app/sandbox/LocalTestSandboxExecutor.kt:27` — no recipe this tool is confident enough to write.
- `named_wire` `observer: GradleXmlFailureObserver` at `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:53` — no recipe this tool is confident enough to write.
- `named_wire` `diagnoser: DiagnosisEngine` at `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:54` — no recipe this tool is confident enough to write.
- `named_wire` `synthesizer: LlmSynthesisProvider` at `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:55` — no recipe this tool is confident enough to write.
- `named_wire` `gate: PromotionGate` at `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:56` — no recipe this tool is confident enough to write.
- `named_wire` `failureSurface: FailureSurface` at `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:60` — no recipe this tool is confident enough to write.
- `named_wire` `promotionController: PromotionController` at `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:61` — no recipe this tool is confident enough to write.
- `named_wire` `rollbackController: RollbackController` at `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:62` — no recipe this tool is confident enough to write.
- `named_wire` `diagnosis: RepairDiagnosis` at `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:95` — no recipe this tool is confident enough to write.
- `named_wire` `patch: SourcePatch` at `mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt:96` — no recipe this tool is confident enough to write.
- `named_wire` `fileStorage: FileStorage` at `mobile/app/src/main/java/com/jarvis/app/session/SessionManager.kt:23` — no recipe this tool is confident enough to write.
- `named_wire` `worldModel: WorldModelService` at `mobile/app/src/main/java/com/jarvis/app/social/ConfidentialityFirewall.kt:33` — no recipe this tool is confident enough to write.
- `named_wire` `worldModel: WorldModelService` at `mobile/app/src/main/java/com/jarvis/app/social/PersonRelationshipModel.kt:31` — no recipe this tool is confident enough to write.
- `named_wire` `vocabularyStore: VocabularyStore` at `mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:320` — no recipe this tool is confident enough to write.
- `named_wire` `streamingTts: StreamingTts` at `mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt:321` — no recipe this tool is confident enough to write.
- `named_wire` `conversationViewModel: ConversationViewModel` at `mobile/app/src/main/java/com/jarvis/app/ui/screens/ConversationScreen.kt:46` — no recipe this tool is confident enough to write.
- `named_wire` `diagnosticsViewModel: DiagnosticsViewModel` at `mobile/app/src/main/java/com/jarvis/app/ui/screens/SystemScreen.kt:41` — no recipe this tool is confident enough to write.
- `named_wire` `currentConfig: Config` at `mobile/app/src/main/java/com/jarvis/app/vad/SileroVadManager.kt:147` — no recipe this tool is confident enough to write.
- `named_wire` `governor: ResourceGovernor` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceModelManager.kt:47` — no recipe this tool is confident enough to write.
- `named_wire` `context: Context` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOrganismHost.kt:47` — no recipe this tool is confident enough to write.
- `named_wire` `host: VoiceOrganismHost` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOrganismMicroSystem.kt:33` — no recipe this tool is confident enough to write.
- `named_wire` `host: VoiceOrganismHost` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOutputPath.kt:39` — no recipe this tool is confident enough to write.
- `named_wire` `fallback: StreamingTts` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOutputPath.kt:42` — no recipe this tool is confident enough to write.
- `named_wire` `profileStore: VoiceProfileStore` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceOutputPath.kt:43` — no recipe this tool is confident enough to write.
- `named_wire` `scheduler: VoiceScheduler` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceService.kt:93` — no recipe this tool is confident enough to write.
- `named_wire` `modelManager: VoiceModelManager` at `mobile/app/src/main/java/com/jarvis/app/voice/provider/SherpaTtsVoiceProvider.kt:35` — no recipe this tool is confident enough to write.
- `named_wire` `registry: VoiceProviderRegistry` at `mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProviderSelector.kt:19` — no recipe this tool is confident enough to write.
- `flat_threshold` `SAMPLE_RATE` at `mobile/app/src/main/java/com/jarvis/app/JarvisMic.kt:33` — no recipe this tool is confident enough to write.
- `flat_threshold` `SPEECH_RMS_THRESHOLD` at `mobile/app/src/main/java/com/jarvis/app/JarvisMic.kt:41` — no recipe this tool is confident enough to write.
- `flat_threshold` `SILENCE_HANG_MS` at `mobile/app/src/main/java/com/jarvis/app/JarvisMic.kt:42` — no recipe this tool is confident enough to write.
- `flat_threshold` `PAUSED` at `mobile/app/src/main/java/com/jarvis/app/android/MediaSessionControlBackend.kt:171` — no recipe this tool is confident enough to write.
- `flat_threshold` `PLAYING` at `mobile/app/src/main/java/com/jarvis/app/android/MediaSessionControlBackend.kt:172` — no recipe this tool is confident enough to write.
- `flat_threshold` `STT_TIMEOUT_MS` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:125` — no recipe this tool is confident enough to write.
- `flat_threshold` `MODEL_WARMUP_TIMEOUT_MS` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:126` — no recipe this tool is confident enough to write.
- `flat_threshold` `TTS_FIRST_CHUNK_TIMEOUT_MS` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:127` — no recipe this tool is confident enough to write.
- `flat_threshold` `INTERRUPT_WINDOW_MS` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:128` — no recipe this tool is confident enough to write.
- `flat_threshold` `MAX_MEMORY_ITEMS` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:133` — no recipe this tool is confident enough to write.
- `flat_threshold` `MEMORY_RELEVANCE_THRESHOLD` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:134` — no recipe this tool is confident enough to write.
- `flat_threshold` `MODEL_MAX_CONCURRENT` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:138` — no recipe this tool is confident enough to write.
- `flat_threshold` `MAX_STT_RETRIES` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:141` — no recipe this tool is confident enough to write.
- `flat_threshold` `MAX_TTS_RETRIES` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:142` — no recipe this tool is confident enough to write.
- `flat_threshold` `MAX_MODEL_RETRIES` at `mobile/app/src/main/java/com/jarvis/app/body/BodyTypes.kt:143` — no recipe this tool is confident enough to write.
- `flat_threshold` `MAX_FACTS` at `mobile/app/src/main/java/com/jarvis/app/body/MemoryStore.kt:21` — no recipe this tool is confident enough to write.
- `flat_threshold` `DEFAULT_TIMEOUT_SECONDS` at `mobile/app/src/main/java/com/jarvis/app/buildtwin/BuildTwinVerifier.kt:146` — no recipe this tool is confident enough to write.
- `flat_threshold` `EVIDENCE_CONFIDENCE` at `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ContinuityLaw.kt:236` — no recipe this tool is confident enough to write.
- `flat_threshold` `ACCEPTED_CONFIDENCE` at `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ContinuityLaw.kt:242` — no recipe this tool is confident enough to write.
- `flat_threshold` `DECAY_RATE` at `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/MentalStateClaims.kt:39` — no recipe this tool is confident enough to write.
- `flat_threshold` `UNANCHORED_CONFIDENCE` at `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/MentalStateClaims.kt:45` — no recipe this tool is confident enough to write.
- `flat_threshold` `MIN_FIELD_SIMILARITY` at `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ReplayCheck.kt:252` — no recipe this tool is confident enough to write.
- `flat_threshold` `TICK_INTERVAL_MS` at `mobile/app/src/main/java/com/jarvis/app/cognition/workspace/Workspace.kt:58` — no recipe this tool is confident enough to write.
- `flat_threshold` `MAX_MEMORIES` at `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/ContinuityPersistence.kt:33` — no recipe this tool is confident enough to write.
- `flat_threshold` `SCHEMA_VERSION` at `mobile/app/src/main/java/com/jarvis/app/cognitive/model/ModelPersistence.kt:35` — no recipe this tool is confident enough to write.
- `flat_threshold` `NANOS_PER_MILLI` at `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CompanionClock.kt:85` — no recipe this tool is confident enough to write.
- `flat_threshold` `NANOS_PER_SECOND` at `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CompanionClock.kt:86` — no recipe this tool is confident enough to write.
- `flat_threshold` `DEFAULT_STALENESS_THRESHOLD_MS` at `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CompanionClock.kt:88` — no recipe this tool is confident enough to write.
- `flat_threshold` `DEFAULT_DONE_IDLE_TIMEOUT_MS` at `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/CurrentActionDiscriminator.kt:164` — no recipe this tool is confident enough to write.
- `flat_threshold` `SCHEMA_VERSION` at `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/LocalHumanCoreBinding.kt:101` — no recipe this tool is confident enough to write.
- `flat_threshold` `SCHEMA_VERSION` at `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/RemoteHumanCoreBinding.kt:81` — no recipe this tool is confident enough to write.
- `flat_threshold` `DEFAULT_REFRESH_INTERVAL_MS` at `mobile/app/src/main/java/com/jarvis/app/companioncore/engine/RemoteHumanCoreBinding.kt:82` — no recipe this tool is confident enough to write.
- `flat_threshold` `IDENTITY_VERSION` at `mobile/app/src/main/java/com/jarvis/app/companioncore/identity/VisualIdentity.kt:23` — no recipe this tool is confident enough to write.
- `flat_threshold` `TICK_INTERVAL_MS` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:262` — no recipe this tool is confident enough to write.
- `flat_threshold` `BACKGROUND_TICK_INTERVAL_MS` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/CompanionCore.kt:265` — no recipe this tool is confident enough to write.
- `flat_threshold` `PRIORITY_SHUTTING_DOWN` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:226` — no recipe this tool is confident enough to write.
- `flat_threshold` `PRIORITY_WAKING` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:227` — no recipe this tool is confident enough to write.
- `flat_threshold` `PRIORITY_USER_INTERRUPT` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:228` — no recipe this tool is confident enough to write.
- `flat_threshold` `PRIORITY_LISTENING` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:229` — no recipe this tool is confident enough to write.
- `flat_threshold` `PRIORITY_SPEAKING` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:230` — no recipe this tool is confident enough to write.
- `flat_threshold` `PRIORITY_THINKING` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:231` — no recipe this tool is confident enough to write.
- `flat_threshold` `PRIORITY_IDLE` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:232` — no recipe this tool is confident enough to write.
- `flat_threshold` `PRIORITY_ASLEEP` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:233` — no recipe this tool is confident enough to write.
- `flat_threshold` `ACTION_DWELL_TICKS` at `mobile/app/src/main/java/com/jarvis/app/companioncore/presence/PresenceEngine.kt:249` — no recipe this tool is confident enough to write.
- `flat_threshold` `COOLDOWN_MS` at `mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:141` — no recipe this tool is confident enough to write.
- `flat_threshold` `BATTERY_LOW_PCT` at `mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:144` — no recipe this tool is confident enough to write.
- `flat_threshold` `MISSES_MEDIUM` at `mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:147` — no recipe this tool is confident enough to write.
- `flat_threshold` `MISSES_LOW` at `mobile/app/src/main/java/com/jarvis/app/companioncore/resource/MobileResourceManagement.kt:148` — no recipe this tool is confident enough to write.
- `flat_threshold` `EVENT_SALIENCE_THRESHOLD` at `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:415` — no recipe this tool is confident enough to write.
- `flat_threshold` `EVENT_LOG_LIMIT` at `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:416` — no recipe this tool is confident enough to write.
- `flat_threshold` `EVENT_SUMMARY_MAX` at `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:417` — no recipe this tool is confident enough to write.
- `flat_threshold` `TRUST_EVENT_LIMIT` at `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:418` — no recipe this tool is confident enough to write.
- `flat_threshold` `TRUST_NEGATIVE_BOOST` at `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:419` — no recipe this tool is confident enough to write.
- `flat_threshold` `GAP_DECAY_FRACTION` at `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:420` — no recipe this tool is confident enough to write.
- `flat_threshold` `BOND_SESSION_STEP_CAP` at `mobile/app/src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt:421` — no recipe this tool is confident enough to write.
- `flat_threshold` `PREPARE_RETRY_MS` at `mobile/app/src/main/java/com/jarvis/app/latency/AckSpeech.kt:117` — no recipe this tool is confident enough to write.
- `flat_threshold` `PREPARE_MAX_RETRIES` at `mobile/app/src/main/java/com/jarvis/app/latency/AckSpeech.kt:118` — no recipe this tool is confident enough to write.
- `flat_threshold` `ACK_GAP_MS` at `mobile/app/src/main/java/com/jarvis/app/latency/LatencyLayer.kt:476` — no recipe this tool is confident enough to write.
- `flat_threshold` `SLOW_THRESHOLD_MS` at `mobile/app/src/main/java/com/jarvis/app/latency/LatencyLayer.kt:478` — no recipe this tool is confident enough to write.
- `flat_threshold` `ACK_ONLY_IDLE_MS` at `mobile/app/src/main/java/com/jarvis/app/latency/LatencyLayer.kt:480` — no recipe this tool is confident enough to write.
- `flat_threshold` `PING_INTERVAL_MS` at `mobile/app/src/main/java/com/jarvis/app/latency/WarmupEngine.kt:28` — no recipe this tool is confident enough to write.
- `flat_threshold` `PING_TIMEOUT_MS` at `mobile/app/src/main/java/com/jarvis/app/latency/WarmupEngine.kt:30` — no recipe this tool is confident enough to write.
- `flat_threshold` `SCHEMA_VERSION` at `mobile/app/src/main/java/com/jarvis/app/memory/AndroidMemoryGraphStore.kt:232` — no recipe this tool is confident enough to write.
- `flat_threshold` `FREQ_SATURATION` at `mobile/app/src/main/java/com/jarvis/app/memory/MemoryImportanceScorer.kt:36` — no recipe this tool is confident enough to write.
- `flat_threshold` `W_RECENCY` at `mobile/app/src/main/java/com/jarvis/app/memory/MemoryImportanceScorer.kt:38` — no recipe this tool is confident enough to write.
- `flat_threshold` `W_FREQUENCY` at `mobile/app/src/main/java/com/jarvis/app/memory/MemoryImportanceScorer.kt:39` — no recipe this tool is confident enough to write.
- `flat_threshold` `W_RELEVANCE` at `mobile/app/src/main/java/com/jarvis/app/memory/MemoryImportanceScorer.kt:40` — no recipe this tool is confident enough to write.
- `flat_threshold` `W_SALIENCE` at `mobile/app/src/main/java/com/jarvis/app/memory/MemoryImportanceScorer.kt:41` — no recipe this tool is confident enough to write.
- `flat_threshold` `STEP` at `mobile/app/src/main/java/com/jarvis/app/memory/SignalSplitScorer.kt:180` — no recipe this tool is confident enough to write.
- `flat_threshold` `MAX_RETRIES` at `mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:616` — no recipe this tool is confident enough to write.
- `flat_threshold` `BIND_ATTEMPTS` at `mobile/app/src/main/java/com/jarvis/app/termux/TermuxJarvisServer.kt:560` — no recipe this tool is confident enough to write.
- `flat_threshold` `BIND_RETRY_MS` at `mobile/app/src/main/java/com/jarvis/app/termux/TermuxJarvisServer.kt:561` — no recipe this tool is confident enough to write.
- `flat_threshold` `SharpPulseAmplitude` at `mobile/app/src/main/java/com/jarvis/app/vf/motion/MotionEngine.kt:54` — no recipe this tool is confident enough to write.
- `flat_threshold` `AccentHairlineAlpha` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:43` — no recipe this tool is confident enough to write.
- `flat_threshold` `DurVoiceResponse` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:83` — no recipe this tool is confident enough to write.
- `flat_threshold` `EventPulseAttackMs` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:90` — no recipe this tool is confident enough to write.
- `flat_threshold` `EventPulseDecayMs` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:91` — no recipe this tool is confident enough to write.
- `flat_threshold` `Layer0Void` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:105` — no recipe this tool is confident enough to write.
- `flat_threshold` `Layer1Ambient` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:106` — no recipe this tool is confident enough to write.
- `flat_threshold` `Layer2Structure` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:107` — no recipe this tool is confident enough to write.
- `flat_threshold` `Layer3Content` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:108` — no recipe this tool is confident enough to write.
- `flat_threshold` `Layer4Core` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:109` — no recipe this tool is confident enough to write.
- `flat_threshold` `GlassSelfReflectionMin` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:119` — no recipe this tool is confident enough to write.
- `flat_threshold` `GlassSelfReflectionMax` at `mobile/app/src/main/java/com/jarvis/app/vf/tokens/JvTokens.kt:120` — no recipe this tool is confident enough to write.
- `flat_threshold` `STATE_SERVICE_UNAVAILABLE` at `mobile/app/src/main/java/com/jarvis/app/voice/HotwordAvailabilityProbe.kt:121` — no recipe this tool is confident enough to write.
- `flat_threshold` `STATE_KEYPHRASE_ENROLLED` at `mobile/app/src/main/java/com/jarvis/app/voice/HotwordAvailabilityProbe.kt:122` — no recipe this tool is confident enough to write.
- `flat_threshold` `STATE_KEYPHRASE_UNENROLLED` at `mobile/app/src/main/java/com/jarvis/app/voice/HotwordAvailabilityProbe.kt:123` — no recipe this tool is confident enough to write.
- `flat_threshold` `STATE_HARDWARE_UNSUPPORTED` at `mobile/app/src/main/java/com/jarvis/app/voice/HotwordAvailabilityProbe.kt:124` — no recipe this tool is confident enough to write.
- `flat_threshold` `DEFAULT_EXAGGERATION` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceForgeAdapter.kt:99` — no recipe this tool is confident enough to write.
- `flat_threshold` `DEFAULT_CFG_WEIGHT` at `mobile/app/src/main/java/com/jarvis/app/voice/VoiceForgeAdapter.kt:100` — no recipe this tool is confident enough to write.
- `flat_threshold` `DEFAULT_PRIORITY` at `mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProviderRegistry.kt:25` — no recipe this tool is confident enough to write.
- `flat_threshold` `HIGH_PRIORITY` at `mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProviderRegistry.kt:26` — no recipe this tool is confident enough to write.
- `flat_threshold` `LOW_PRIORITY` at `mobile/app/src/main/java/com/jarvis/app/voice/provider/VoiceProviderRegistry.kt:27` — no recipe this tool is confident enough to write.
- `ownership_naming` `AnchorEngine, AnimationEngine, ArbitrationEngine, AttentionEngine, CognitiveEngine, CompanionCore, CoreIdentity, DecisionEngine, DiagnosisEngine, EmotionalIntelligence, EvolutionEngine, ExecutionEngine, FallbackIdentity, GlowEngine, GrowthEngine, HumanCore, InternalDialogueEngine, JarvisEngine, LocalInferenceEngine, MemoryUpdateEngine, ModelUpdateEngine, MotionEngine, MutationEngine, ParticleEngine, PersonalityEngine, PresenceEngine, ReactorEngine, SelfIdentity, SocialIntelligence, SpeechEngine, StreamingTTSEngine, SttEngine, SystemTTSEngine, TTSEngine, ThemeEngine, TypographyEngine, UniversalResearchEngine, UserIdentity, VisualIdentity, WarmupEngine` at `mobile/app/src/main/java/com/jarvis/app/JarvisEngine.kt:39` — no recipe this tool is confident enough to write.
- recipe note: Recipe intent: Reconfigure the fixed gate into a function of the field's own accepted-change history: each additional accepted change to a field demands one more real claim and one more real interval, both saturating at a stated ceiling. At zero prior accepted changes the behaviour is identical to the flat one, so the change is a reconfiguration of the same gate, not a different gate.
- recipe note: 13 finding(s) carried the recipe `evidence-scaled-threshold`. Every one of its 11 substitutions matched exactly once, and it was applied to 3 file(s) as a plain diff; see the report for what it changes and why.

## Scope this tool refuses to touch, and why

- the security floor and approval requirements (VISION.md Section 5's one fixed exception: authorization roots, protected operations, self-modification protections)
- identity-adjacent claims and anything CONTINUITY-LAW already gates -- the gate is the mechanism, so flagging the gate's own vocabulary as drift would be flagging the fix; its two disclosed deviations are still reported because they are properties of the CODE, not of the identity it protects
- the non-production tree (tests, resources, build files): this tool audits implementation code, and a test is a claim about code, not code

## What this report does not claim

- It does not claim a finding is *wrong*. Section 13 says a Yes is a finding, not a verdict: a closed enum can be the right implementation today. What the tool claims is the narrower, checkable thing — the code has that shape, at that line.
- It does not claim a check is complete. Each check's blind spot is stated where the check is described, and gaps go on the open list above rather than being papered over.
- It did not touch `main`, did not merge, and did not close a story.

