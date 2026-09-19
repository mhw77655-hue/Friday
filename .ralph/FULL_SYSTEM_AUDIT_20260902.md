# FULL SYSTEM AUDIT — JARVIS — 2026-09-02

Branch: `ralph/full-system-audit-20260902`
Date: Wed Sep 02 2026
Scope: AUDIT ONLY. No production code was modified. This is a ground-truth report.

Primary tool: the real `SystemGraph` + `WiringDiagnostics` (com.jarvis.app.selfreconfig),
with cross-checks by source grep, git history, and a full unit-test run.

---

## 0. Executive summary

The JARVIS codebase is a large, mostly-complete Android+JVM codebase whose **component
logics are broadly IMPLEMENTED and green-tested (1052/1054 unit tests pass), but whose
production wiring is far thinner than the stage narrative implies.** The critical,
repeated finding is: **most subsystems are implemented and covered by tests, yet never
instantiated in the live production turn path (`JarvisEngine.init` → `LatencyPipeline` →
`CognitiveEngine`).**

- The only production-turned-on "advanced" seam is **CognitiveAdmissionPolicy → ModelManager.wake** (FEP gate).
- **Galaxy Memory, Stage-03 Identity, fuzzy resolution, cloud pool, self-reconfig, capability router, research, federation, and voice are NOT wired into the production `CognitiveEngine`/`JarvisEngine` construction.** They exist as real classes with real (green) tests, reachable only from test code.
- The **SystemGraph organ map is itself incomplete and partly out of sync** with both production and the real `CognitiveEngine` nullable seams. Its declared edges omit `core.bodyCoordinator` as reachable from the entry and do not include several real classes at all.
- **2 pre-existing unit-test failures** remain, both in `cognitive.planning` (replan logic). All historical "6 CapabilityExecutorTest failures" are now RESOLVED (18/18 pass).
- Working tree is **clean of production changes** (only the two `.ralph` planning files are modified). CI history is **not verifiable** from this environment (no `.github` dir, no `gh` CLI, no APK artifact). One 2.3 MB model file (`silero_vad.onnx`) is present and tracked in `assets/`.

---

## 1. Full `com.jarvis.app.*` package inventory (real classes per package)

Only production source (`mobile/app/src/main/java/com/jarvis/app`) is inventoried. Count of top-level `.kt` files per package, followed by the class list.

### Root package `com.jarvis.app` (app-level classes)
`CloudVoiceClient`, `DecisionGate` (deprecated/superseded), `DiagnosticsViewModel`, `ExecutionLayer` (deprecated/superseded), `IJarvisShellService`, `JarvisApplication`, `JarvisEngine` (composition root), `JarvisMic`, `JarvisNotificationListener`, `JarvisShellService`, `JarvisSherpaWhisper`, `JarvisSherpaZipformer`, `JarvisShizuku`, `JarvisTts`, `JarvisVosk`, `MainActivity`, `ObsidianSync`, `ScreenBridge`, `SherpaBridge`, `Telemetry`, `TtsBridge`, `VoskBridge`.

### `com.jarvis.app.android` — **UNWIRED in production**
`DeviceControl`, `DeviceControlRouter`, `RiskGate`, `RiskTier`. Backends are injectable interfaces; real Android code is Shizuku detection + ScreenBridge dump. No production instantiation; tested via `DeviceControlRouterTest`/`RiskGateTest`.

### `com.jarvis.app.approval` / `com.jarvis.app.approvals` **— WIRED (boot init)**
`ApprovalGate` (boot-init in JarvisEngine), `TaskExecutor`, `LocalApprovalQueue`. These are live (initialized in `JarvisEngine.init`).

### `com.jarvis.app.body` **— WIRED**
`AdaptiveContextBuilder`, `BodyCoordinator` (live), `BodyStateMachine`, `BodyTypes`, `LanguageDetection`, `LanguageRouter` (live), `MemoryPromotion`, `MemoryStore` (live), `MemoryStorePort`, `PlatformRecognizerStt`, `PresenceMonitor`, `StreamingTts` (live), `VisualStateBridge` (live), `VocabularyStore`.

### `com.jarvis.app.builder` **— UNWIRED**
`CapabilityGapDetector`, `ToolBuilder`, `ToolSpecification`, `ToolValidator`. Not constructed in production.

### `com.jarvis.app.capability` **— UNWIRED in production (registered with, not invoked)**
`CapabilityArbitrator`, `CapabilityRegistry` (central), `CapabilityRouter`, `DeterministicCapabilityRouter`. The central registry is ONLY exercised by `JarvisEngine` to register the single `voice_organism_v1` TTS capability; nothing reads it in the live turn.

### `com.jarvis.app.cloud` **— UNWIRED**
`CloudModelPool`, `CloudModelRouter`. Faithful port of `ralph.sh` MODEL_POOL; round-robin + rate-limit regex. Referenced only by research code as an import; never constructed in production.

### `com.jarvis.app.cognitive` **— WIRED (CognitiveEngine is live); helper components live**
`AttentionEngine`, `CognitiveContextBuilder`, `CognitiveEngine` (live via LatencyLayer seam), `CognitiveState`, `ContextWindowAssembler` (constructed inside CognitiveEngine but its *cross-session* and *mental-state* features are null-gated → inert), `IntentInference`, `PronounResolver` (internal, live within processInput), `ReferenceStore` (live), `SalienceScorer` (live), `TaskWorkingMemory` (component; NOT used in live turn), `TopicTracker` (live), `WorkingMemory` (live).

### `com.jarvis.app.cognitive.capability` **— UNWIRED (execution-path resolver)**
`Capability`, `CapabilityActionPort`, `CapabilityExecutor`, `CapabilityFabric`, `CapabilityInvoker`, `CapabilityRegistry`, `CapabilityResolver` (exact-string Map lookup → AMBIGUOUS), `Descriptor`.

### `com.jarvis.app.cognitive.execution`
`ActionPort`, `ExecutionEngine`, `ExecutionState`. Covered by tests; not in live turn.

### `com.jarvis.app.cognitive.immune`
`Containment`, `ImmuneMemory`, `ImmuneSystem`, `PartialFailure`, `ResourceFailure`. Tested; not in live turn.

### `com.jarvis.app.cognitive.memory`
`ContinuityManager`, `ContinuityPersistence`, `ContinuityPort`, `ExperienceRecord`, `MemoryCandidate`, `MemoryConflictResolver`, `MemoryConsolidator`, `MemoryContinuityAdapter`, `MemoryProvenance`, `MemoryUpdateEngine`. Tested; not in live turn.

### `com.jarvis.app.cognitive.model`
`ModelContextPort`, `ModelFact`, `ModelPersistence`, `ModelQuery`, `ModelStore`, `ModelSynchronization`, `ModelUpdateEngine`, `SelfModel`, `UserModel`, `WorldModel`. Note: these `SelfModel/UserModel/WorldModel` are the **cognitive-state** models — a DIFFERENT family from `com.jarvis.app.identity`'s Self/User/World. Tested; not in live turn.

### `com.jarvis.app.cognitive.planning` **— 2 FAILING tests**
`DecisionEngine`, `GoalPlanner`, `PlanGraph`. `GoalPlanner`/`DecisionEngine` are default-constructed inside `CognitiveEngine` and used by its plan path; the 2 failing tests are the replan tests.

### `com.jarvis.app.companioncore` (+ contract/engine/identity/presence/render/resource/ui)
Live: `CompanionCoreHolder`, `PresenceEngine`, `HumanCoreIntegration`, `AnimationController`, `OrbStateMachine`, etc. Wired via `CompanionCoreHolder.init` in JarvisEngine and `LatencyLayer` reactor seams.

### `com.jarvis.app.env`
`EnvironmentProfile`, `EnvironmentRepository`, `LiquidEnvironmentManager`. `LiquidEnvironmentManager` is live in JarvisEngine.

### `com.jarvis.app.evolution` / `com.jarvis.app.mutant` / `com.jarvis.app.mutation` / `com.jarvis.app.genome` / `com.jarvis.app.validation` / `com.jarvis.app.selfrepair` / `com.jarvis.app.microsystem` **— UNWIRED (build-time/offline tooling)**
Self-repair + evolution + validation + genome + sandbox tooling. Real and green-tested, but only reachable as a dev/test pipeline, never in the live turn.

### `com.jarvis.app.failure` **— WIRED**
`BackoffPolicy`, `CircuitBreaker`, `FailureModel`, `FailureStore` (live), `FailureSurface` (live), `RecoveryController` (live), `SelfDiagnosis` (live), `UserFailureText`.

### `com.jarvis.app.federation`
`Federation`, `SyncFabric`. Tested; not in production.

### `com.jarvis.app.humancore` (+ algo/bus/fallback/mod/pipeline/protocol/store) **— WIRED**
`HumanCore` (live, process-global object) + all mod/store/pipeline subsystems. Heavy real implementation; the personality/identity core.

### `com.jarvis.app.identity` (+ owner) **— IMPLEMENTED + TESTED, UNWIRED in production**
`IdentityContext`, `PersonaTuner`, `PrdStageHistorySource`, `SelfModel`, `UserMentalStateEstimator`, `UserProfile`, `WorldModelService`, `owner/*` (BiometricAuthResult, BiometricType, HumanCoreOwnerBindingPort, OwnerBindingPort, OwnerBiometricBinder, PlatformBiometricPromptResultMapper). None instantiated in production main; all green-tested; wired only in `CognitiveEngine` null-gated seams (`identityContext = null` in JarvisEngine).

### `com.jarvis.app.inbox` — `LocalTaskInbox` is LIVE (constructed in JarvisEngine).

### `com.jarvis.app.latency` **— THE ENTRY POINT**
`AckSpeech`, `LatencyLayer`, `SpeechEngine`, `WarmupEngine`. `LatencyLayer`/`LatencyPipeline` is the live conversation entry (`onUserInput`).

### `com.jarvis.app.memory` **— IMPLEMENTED + TESTED, UNWIRED in production**
`AndroidMemoryGraphStore`, `AndroidVectorStore`, `BlendedMemoryRetriever`, `ConsolidationDaemon`, `EmbeddingProvider`, `MemoryGraphStore`, `MemoryImportanceScorer`, `NeuralEmbeddingProvider`, `VectorStore`. None instantiated in production main; green-tested; wired only in `CognitiveEngine` null-gated seams (`blendedRetriever`/`graphStore = null` in JarvisEngine).

### `com.jarvis.app.model` (+ adapters/config/storage) **— WIRED**
`AdapterModelBackend`, `AndroidResourceSnapshot`, `CognitiveAdmissionPolicy` (live, FEP gate), `ModelBackend`, `ModelManager` (live), `ModelProvider`, `ModelTier`, `OrganRole`, `ResourceGovernor` (live). Adapters: `AdaptersUtil`, `HeuristicAdapter` (stub/no-op load), `LlamaCppAdapter` (load returns "does not support dynamic model loading"), `LocalInferenceEngine` (real JNI GGUF path), `OllamaAdapter` (real /api/pull), `RemoteJarvisAdapter` (real /os/api/models/load).

### `com.jarvis.app.nervous` **— UNWIRED**
`ArbitrationEngine`, `CapabilityRouter`, `GlobalNervousSystem`, `OrganismCoordinator`, `PredictiveRouter`.

### `com.jarvis.app.organism`
`Manifests`, `OrganismLifecycle`. Tested; not live.

### `com.jarvis.app.policy`
`InferencePolicyLayer`.

### `com.jarvis.app.research` (+ universal) **— IMPLEMENTED + TESTED, UNWIRED**
`ResearchModel`, `ResearchPipeline`, `universal/*` (CloudResearchProvider, CrossDomainMapper, FabricatorChannel (fun-interface only), InMemoryMechanismsStore, MechanismCandidate, MechanismExtractor, MechanismsStore, ResearchEvidence, UniversalCatalog, UniversalResearchEngine). Green-tested in-memory orchestrator; used only via `UniversalResearchEngine.researchPersist` tests. FabricatorChannel is a **fun-interface with no production implementation**.

### `com.jarvis.app.resolution` **— UNWIRED**
`CapabilityMemoryIndex`, `FuzzyCommandResolver`. Green-tested (`FuzzyCommandResolverTest`); never constructed in production.

### `com.jarvis.app.resource`
`ResourceGovernor` (the microsystem one — distinct from `com.jarvis.app.model.ResourceGovernor`).

### `com.jarvis.app.runtime`
`RuntimeBinder` (live in JarvisEngine), `discovery/LanRuntimeDiscovery`.

### `com.jarvis.app.sandbox` **— tooling only**
`CloudVmSandboxExecutor` (**NotImplementedError stub**), `LocalTestSandboxExecutor` (real), `SandboxExecutor`.

### `com.jarvis.app.selfreconfig` **— IMPLEMENTED + TESTED, never instantiated in main**
`SystemGraph`, `WiringDiagnostics`. The graph is built ONLY inside the test `SystemGraphTest.buildJarvisSystemGraph()`; neither is constructed anywhere in production main.

### `com.jarvis.app.session` — `SessionManager` is LIVE (constructed in JarvisEngine).

### `com.jarvis.app.stt` — `PlatformArabicStt`, `STTArbitrator` (placeholder language detect returns "en").

### `com.jarvis.app.tts` — `TTSEngine`.

### `com.jarvis.app.ui` (+ components/navigation/screens/theme/viewmodel) — Compose UI, live.

### `com.jarvis.app.vad` — `SileroVadManager` (wakes on `silero_vad.onnx`).

### `com.jarvis.app.validation` — `CandidateTestRunner`, `FailureTestRunner`, `PromotionGate`, `RegressionRunner`, `ResourceBenchmark` (tooling, green-tested).

### `com.jarvis.app.vf` (Visual Foundation) + subpackages — Compose UI system, live via UI.

### `com.jarvis.app.voice` (+ provider) **— partially wired (R1 host + R2 output path live; real engines not moved in)**
`IVoiceService`, `VoiceEnvironment`, `VoiceFailureController`, `VoiceMetrics`, `VoiceModelManager`, `VoiceModelManifest`, `VoiceOrganismHost` (live), `VoiceOrganismMicroSystem` (live), `VoiceOutputPath` (live), `VoiceProfile`, `VoiceProfileStore` (live), `VoiceScheduler`, `VoiceService` (R1 placeholder), `VoiceProfileStore`, `provider/*` (SherpaTtsVoiceProvider, SystemTtsVoiceProvider, VoiceCapabilities, VoiceProvider, VoiceProviderRegistry, VoiceProviderSelector).

---

## 2. WiringDiagnostics against the real entry point (`entry.latencyPipeline`)

`WiringDiagnostics` was run against the **real `SystemGraph` topology** defined by
`SystemGraphTest.buildJarvisSystemGraph()`. **This graph is the one source of truth organ
map** (per REPO_FACTS) and it is constructed nowhere except that test. The reachability
computation is deterministic from its registered node + edge set; the result below is the
exact output of `graph.computeReachability("entry.latencyPipeline")` delegating through
`WiringDiagnostics.diagnose()`.

### Reachable (10 nodes) — declared edges exist from the entry
`entry.latencyPipeline`, `core.humanCore`, `cognitive.engine`,
`cognitive.contextWindowAssembler`, `memory.blendedRetriever`, `memory.graphStore`,
`model.modelManager`, `model.resourceGovernor`, `identity.mentalStateEstimator`,
`identity.userProfile`.

### Unreachable (16 nodes)
`core.bodyCoordinator`, `identity.selfModel`, `identity.worldModelService`,
`identity.personaTuner`, `capability.capabilityRegistry`, `safety.approvalMechanism`,
`safety.circuitBreaker`, `planned.anchorEngine`, `planned.fabricator`, plus the 7 declared
fixed invariants (`identity.root`, `authorization.root`, `approval.mechanism`,
`upgrade.provenance`, `rollback.mechanism`, `security.boundaries`, `core.recovery`).

**WiringDiagnostics verdict: `unreachableCount = 16`, `allInvariantNodesPresent = true`**
(no invariant is missing).

### Caveat — the graph under-reports reality
`core.bodyCoordinator` is declared unreachable only because the graph has **no edge INTO
it** (it only has outgoing edges), yet `BodyCoordinator` is in fact constructed in
`JarvisEngine.init` and — through it — `CognitiveEngine` is reached in the reply path.
Similarly `identity.worldModelService`/`identity.personaTuner` are reachable *out of the
graph's declared edges* only in the `CognitiveEngine.identityContext` seam (which is null
in production) — so their "unreachable" status is coincidentally correct in effect.

**Critical scope finding:** the SystemGraph itself is **not kept in sync** — it (a) omits
many real production classes (failure, body, humancore subsystems, voice, cognitive
helper components, research, resolution, cloud, android, capability router), (b) includes
`safety.approvalMechanism`/`safety.circuitBreaker` whose `ApprovalMechanism` FQCN does not
exist (the real gate is `com.jarvis.app.approval.ApprovalGate`), and (c) fails to encode
the actual `CognitiveEngine` nullable seams. This is a documented contradiction to flag
for the telemetry/self-model queue: the "one source of truth organ map" is incomplete and
out of date.

---

## 3. Organs/classes with zero real callers in `main` (production), plus test-only reachability

Cross-check of the WiringDiagnostics result with a grep of `mobile/app/src/main` for
constructor invocation. The following are **implemented, green-tested, but have ZERO
instantiation in production main** (reachable ONLY from test code or via a nullable seam
that is null in production):

| Class | Package | Production caller |
|---|---|---|
| `AndroidMemoryGraphStore`, `AndroidVectorStore`, `BlendedMemoryRetriever`, `ConsolidationDaemon`, `MemoryGraphStore`, `MemoryImportanceScorer`, `NeuralEmbeddingProvider` | memory | NONE |
| `IdentityContext`, `WorldModelService`, `UserProfile`, `PersonaTuner`, `UserMentalStateEstimator`, `SelfModel`, `PrdStageHistorySource`, `owner/*` | identity | NONE |
| `FuzzyCommandResolver`, `CapabilityMemoryIndex` | resolution | NONE |
| `CloudModelPool`, `CloudModelRouter` | cloud | NONE (imported by research only, never constructed) |
| `SystemGraph`, `WiringDiagnostics` | selfreconfig | NONE |
| `DeterministicCapabilityRouter`, `CapabilityRouter`, `CapabilityArbitrator` | capability | NONE (registry is only written to, never read in turn) |
| `DeviceControlRouter`, `RiskGate`, `DeviceControl` | android | NONE |
| `Federation`, `SyncFabric` | federation | NONE |
| `ResearchPipeline`, `universal/*` | research | NONE |
| `FuzzyCommandResolver` pipelines | resolution | NONE |
| `ExecutionEngine`, `ContinuityManager`, `MemoryConsolidator`, `immune/*`, `model/*` (cognitive-state) | cognitive subpackages | NONE (not in live turn) |
| `CapabilityFabric`, `CapabilityExecutor`, `CapabilityResolver` | cognitive.capability | NONE (live turn uses `sendBlock` model path, not the fabric) |
| `TaskWorkingMemory` decompose/runNext | cognitive | Component only — **not** driven in the live turn path |
| `nervous/*`, `builder/*`, `evolution/*`, `genome/*`, `selfrepair/*`, `sandbox/*`, `validation/*` | various | NONE (tooling/offline only) |

### Fully wired entry path (real callers from `LatencyLayer.onUserInput` → `JarvisEngine`)
`LatencyPipeline.onUserInput` → `cognitiveEngine.process(...)` (with all advanced seams
null) → on DIRECT_REPLY, `CognitiveAdmissionPolicy.serveTurn` → `ModelManager.wake`.
`LatencyPipeline.onReplyReady` → `HumanCore.express` + speech. **This is the entire live
turn path.** Everything else in §3 is reachable only from test code.

---

## 4. Stub / placeholder / fun-interface-only implementations standing in for a real one

| Gap | Location | Real state |
|---|---|---|
| **FabricatorChannel** | `research/universal/FabricatorChannel.kt:13` | **fun-interface only, no production implementation.** KDoc self-declares "Fabricator does not yet exist (PLANNED organ in SystemGraph)". Consumes `MechanismCandidate`; never wired to a real fabricator. |
| **CloudVmSandboxExecutor** | `sandbox/CloudVmSandboxExecutor.kt:38` | **NotImplementedError stub** — `stressTest()` always throws with a TODO for cloud-VM infra. NOT wired. Active impl is `LocalTestSandboxExecutor`. |
| **LLM/dispatch injectable-lambda seams (null in production)** | `CognitiveEngine.kt` | `decompositionComplete`, `intentClassificationComplete`, `capabilityFabric`, `modelContext`, `continuity`, `blendedRetriever`, `graphStore`, `identityContext` — all `= null` in `JarvisEngine` construction. Classify falls back to STATEMENT; decompose silently skips. |
| **Model adapters load()** | `model/adapters/` | `LlamaCppAdapter.load` returns error "llama-server does not support dynamic model loading"; `HeuristicAdapter.load` is a no-op success (keyword-template generate); `CLOUD` provider aliased to heuristic in `ModelManager:69`. Only `OllamaAdapter`/`RemoteJarvisAdapter` do real HTTP model pulls. |
| **STT language detection** | `stt/STTArbitrator.kt:473` | `detectLanguageFromAudio` is a placeholder returning hardcoded `"en"`. |
| **Cognitive-state models** | `cognitive/model/*` | Real data classes, but `SelfModel/UserModel/WorldModel` here are the cognitive-state family, separate from `identity`'s. Not a stub per se, but distinct and easily conflated. |
| **`DecisionGate` / `ExecutionLayer`** | root | Deprecated/superseded by `ApprovalGate`/`TaskExecutor`; retained only for audit continuity. |

---

## 5. Re-verification of every `passes=true` story against real current code

This is the crucial section. Each story was marked `passes:true` in `prd.json` history.
The judgement below reflects what the **real, still-shipped code actually does today** —
not the story's prose. Stages from progress.txt + git log + REPO_FACTS.

### Stage 01A (WORKING-MEMORY-CORE, GOAL-DECOMPOSITION, STEP-EXECUTION-LOOP, REPLAN-ON-FAILURE, STAGE-01A-INTEGRATION)
- **Code present:** YES — `TaskWorkingMemory`, `CognitiveEngine.decompose/runNext/replanOnFailure`, `Stage01AIntegrationTest` all exist and their scoped tests pass.
- **Classification: PARTIALLY_IMPLEMENTED.** The decompose→execute→replan loop is a real, green capability, but it is **not driven from the live turn**. `JarvisEngine` constructs `CognitiveEngine` without `decompositionComplete` or `capabilityFabric`, so `decompose()`/`runNext()` are only reachable from tests (the new `TaskWorkingMemory` path). The live turn uses only `process()` → `MemoryStorePort` → model.

### Stage 01B (REFERENCE-STORE, PRONOUN-RESOLUTION, INTENT-CLASSIFICATION, AMBIGUOUS-REFERENCE-CLARIFICATION, STAGE-01B-INTEGRATION)
- **Code present:** YES — `ReferenceStore`, `PronounResolver`, `classifyIntent`, `NEEDS_CLARIFICATION`; tests pass.
- **Classification: IMPLEMENTED (components) / PARTIALLY_IMPLEMENTED (wiring).** `ReferenceStore`/`PronounResolver`/`TopicTracker`/`SalienceScorer` run inside `processInput()`. But `intentClassificationComplete` is `null` in production → `classifyIntent` always falls back to STATEMENT and the COMMAND→PLAN route never fires in production. The components are live-constructed and their internal logic runs; the LLM classification gate is unwired.

### Stage 01C (TOPIC-TRACKER, SALIENCE-SCORING, CONTEXT-WINDOW-ASSEMBLY, STALE-CONTEXT-DECAY, STAGE-01C-INTEGRATION)
- **Code present:** YES — `TopicTracker`, `SalienceScorer`, `ContextWindowAssembler`, `StaleContextDecayTest`; tests pass.
- **Classification: IMPLEMENTED (components) / PARTIALLY_IMPLEMENTED (semantic features).** The assembler is constructed inside `CognitiveEngine`, but its **cross-session `blendedRetriever` is null** → returns empty; its **mental-state estimator is null** → returns null. So the current-segment/salience composition runs, but the cross-session and mental-state features are inert.

### Stage 02 Galaxy Memory (EMBEDDING-PROVIDER, MEMORY-GRAPH-STORE, SALIENCE-VALENCE-SCORING, BLENDED-RETRIEVAL, CONSOLIDATION-DAEMON, STAGE-02-INTEGRATION)
- **Code present:** YES — `NeuralEmbeddingProvider` (llama-server /embedding forward pass + binary-quantized Hamming), `AndroidVectorStore` (android.database.sqlite + Hamming NN), `AndroidMemoryGraphStore`, `BlendedMemoryRetriever` (staged seed-traverse-rank), `MemoryImportanceScorer`, `ConsolidationDaemon`. `memory.*` tests: 39/39 pass.
- **Classification: IMPLEMENTED (subsystem) / UNWIRED (production).** This is the flagship gap. Galaxy Memory is fully real and green-tested, and `CognitiveEngine` has real `blendedRetriever`/`graphStore` seams, but **production `JarvisEngine` never constructs any memory class and passes the seams as null** → no cross-session retrieval, no graph write-back in the live turn. `STAGE-02-INTEGRATION` proves the real pipeline in a JVM test, not in production.
- **Contradiction (recorded):** the redesigned PRD's `MEMORY-GRAPH-STORE` AC1/AC4 promise a `neighbors()` weighted-edge API; the shipped store has no `neighbors()`/edges table (edge weights are derived inside `BlendedMemoryRetriever`). Documented in REPO_FACTS as an existing contradiction.

### Stage 03 Identity (WORLD-MODEL-SERVICE, USER-MODEL, SELF-MODEL, PERSONA-TUNING, STAGE-03-INTEGRATION)
- **Code present:** YES — `WorldModelService`, `UserProfile`, `UserMentalStateEstimator`, `SelfModel`, `PersonaTuner`, `IdentityContext`, `Stage03IntegrationTest`; `identity.*` tests pass.
- **Classification: IMPLEMENTED (subsystem) / UNWIRED (production).** Same flagship gap as Stage 02. Production `JarvisEngine` never instantiates any identity class and passes the `identityContext` seam as null → no self/user/world modeling in the live turn. `Stage03IntegrationTest` proves it only at test level.

### Stage 04 Model Federation (MODEL-BACKEND-CONTRACT, RESOURCE-GOVERNOR, MODEL-MANAGER-LIFECYCLE, FEP-WAKE-GATE, STAGE-04-INTEGRATION)
- **Code present:** YES — `ModelBackend` + `AdapterModelBackend`, `ResourceGovernor`/`ModelTier`/`ResourceSnapshot`, `ModelManager` refactor (ONE loading path), `CognitiveAdmissionPolicy` + `ModelManager.wake`, `Stage04IntegrationTest`. `model.*` tests pass.
- **Classification: IMPLEMENTED + WIRED (partial).** This is the stage that IS live:
  - `ModelManager` + `ResourceGovernor` (with real `AndroidResourceSnapshot`) are constructed in `JarvisEngine` and drive load/unload.
  - **`CognitiveAdmissionPolicy` → `ModelManager.wake` IS active in the live turn** (`CognitiveEngine.process` line 286-288, guarded by `modelManager != null && cognitiveAdmissionPolicy != null`, both true in production). `servedDegraded` is surfaced in `CognitiveTurnResult`.
- **Contradiction (recorded, from progress):** the FEP-WAKE-GATE PRD premised a Stage 01B prediction-error/doubt scorer that does not exist. The real doubt carrier is `CognitiveState.UncertaintyProfile`, thresholded by `CognitiveAdmissionPolicy`. That premise error is already flagged in REPO_FACTS.

### Phase A wiring (PHASE-A-MEMORY-WIRING-AUDIT, PHASE-A-SELF-USER-WORLD-WIRING-AUDIT, PHASE-A-INTEGRATION)
- **Code present (tests only).** These commits added live-style tests (`PhaseAMemoryWiringLiveTest`, `PhaseAIdentityWiringLiveTest`, `PhaseAIntegrationTest`) that construct a real `CognitiveEngine` WITH the memory/identity seams wired, and modified `CognitiveEngine.kt` (adding the seams). **They did NOT modify `JarvisEngine.kt`.**
- **Classification: CONTRADICTORY.** The story titles claim "wire Galaxy Memory / self-user-world into the real live conversation turn", but **production `JarvisEngine.init` still constructs `CognitiveEngine` with those seams null** (verified at `JarvisEngine.kt:186-192`). The wiring is proved only in tests that build their own `CognitiveEngine`; the real production composition never enables it. So the "closing Phase A through LatencyPipeline.onUserInput" claim is **not true of the shipped production composition** — it is true only of an instrumented test harness.

### SYSTEM-GRAPH
- **Classification: IMPLEMENTED but UNWIRED + PARTIALLY STALE.** `SystemGraph`/`WiringDiagnostics` are real and green-tested, but (1) never instantiated in production main, (2) the single graph is built only inside the test, (3) omits many real classes and mis-points `safety.approvalMechanism` to a non-existent `ApprovalMechanism`. See §2 caveat.

### CAPABILITY-FABRIC-ROUTING, ANDROID-CAPABILITY-ADAPTER-STACK, ONLINE-INTELLIGENCE-FABRIC, UNIVERSAL-RESEARCH, PEOPLE-VOICE-FACE-MEMORY-OWNER-BINDING, FUZZY-COMMAND-RESOLUTION
- **Code present:** YES for all (green scoped tests): capability router/fabric, android device-control + RiskGate, cloud model pool/router, research pipeline + FabricatorChannel, owner-binding biometrics, fuzzy command resolution.
- **Classification: UNIVERSALLY UNWIRED IN PRODUCTION.** None of these are instantiated in `main`. The **ONLY real data-possession/biometric production hook** is `PlatformBiometricPromptResultMapper` (targets platform `android.hardware.biometrics.BiometricPrompt`; compile-time-only, never invoked on JVM). `CloudModelRouter` is imported by research but never constructed. `FuzzyCommandResolver`/`DeterministicCapabilityRouter` are test-fixture-only.

### BIBLE-STEP-1 / BIBLE-STEP-2
- CognitiveEngine construction in `JarvisEngine` + routing the live turn through `CognitiveEngine.process` — **IMPLEMENTED + WIRED** (this is the core live path and is real).

---

## 6. Git working-tree state, CI history, APK size, assets scan

### Git working tree
- Branch: `ralph/full-system-audit-20260902` (matches PRD `branchName`).
- `git status --short`: exactly **2 modified files** — `.ralph/prd.json`, `.ralph/progress.txt` (the builder's own planning notes). **0 untracked, 0 deleted, 0 staged.**
- Production tree diff vs HEAD: **0 files** (`git diff --name-only HEAD -- mobile/app/src/main` is empty) → the working tree carries **zero production changes**; the 2 test failures below are pre-existing on committed code.
- `git stash list`: empty.

### CI run history — NOT VERIFIABLE
- The current working tree has **no `.github/` directory** and **no `build-app.yml`** on disk.
- Git history shows `build-app.yml` + companion workflows were **added (2f35255, 2026-07-19)** and repeatedly modified (21ae8c4, cbcbd01, af6b8c6, e938f94, 5dbee2a, cb201a4), then **deleted in 620efde "Phase 0: full wipe" (2026-07-25)**.
- The recovered workflow ran `gradle assembleDebug --no-daemon --stacktrace` (no `testTask`/test step) and uploaded `jarvis-debug-apk`. Note: the REPO_FACTS instruction "CI (build-app.yml via GitHub Actions) is the only supported Android build path" describes a workflow that **no longer exists in the tree**.
- **Verdict: has `build-app.yml` ever succeeded? Cannot be proven from repo-local evidence.** No run artifact, badge, or log is present; `gh` CLI is not installed; only a remote (`origin https://github.com/mhw77655-hue/JARVIS.git`) exists. Circumstantial commit notes ("prior CI success") exist but are not proof.

### APK size
- **No APK artifact is present in the repo tree** (`find ... -name "*.apk"` → none). `mobile/app/build/outputs` is empty. So there is no built APK to size; the last local build (a gitignored 517 MB `build/` dir) did not leave an APK.

### Assets scan for stray model files
`mobile/app/src/main/assets/` contains exactly:
- `capability_manifest.json` — 1,447 B (text)
- **`models/silero_vad.onnx` — 2,327,524 B (2.3 MB binary model)** ← present and git-tracked.

**Yes — one model file is in assets.** It is the Silero VAD (small, expected by `SileroVadManager`), but it does add ~2.3 MB to any built APK. Note the historical APK-size policy commit (0ac834d) moved the larger ASR models out to `/sdcard/JARVIS/models`; the whisper model dir `sherpa-onnx-whisper-tiny.en` referenced by `JarvisSherpaWhisper` is **absent** from source assets (only present in stale local `build/` output) — a potential runtime dependency gap if the vote loader expects it from assets.

### Working-tree sizes
- `mobile/app/src/main` (tracked source): **7.2 MB**.
- `mobile/app` (incl. gitignored `build/`): 526 MB — of which ~517 MB is stale local gradle output.

---

## 7. Pre-existing test failures (currently failing)

Full unit-test run: `bash ./gradlew testDebugUnitTest --tests "com.jarvis.app.*" --no-configuration-cache --no-build-cache`
→ **1054 tests completed, 2 failed, 0 errors.**

Exact failing tests (both `cognitive.planning`, both replan-related):
1. `GoalPlannerTest > replan replaces only the failed step and its downstream` — `AssertionError` in an `assertFalse`.
2. `CognitivePlanningIntegrationTest > replan recovers from a failed step without restarting` — `AssertionError`.

**The historical "6 CapabilityExecutorTest failures" are RESOLVED:** `CapabilityExecutorTest` now runs **18/18 PASS** on the current tree. The long-documented pre-existing failure list is now reduced to these 2 planning tests.

Both failures are **pre-existing and pre-date this audit** (working tree has zero production changes; they fail on the committed HEAD).

---

## 8. Recorded contradictions / accuracy notes

1. **PHASE-A wiring vs production:** commits titled "wire X into the live turn" changed only `CognitiveEngine` + tests, never `JarvisEngine`. The production composition still leaves Galaxy Memory and Identity seams null → the narrative "Phase A closed through the real turn" is contradicted by `JarvisEngine.kt:186-192`.
2. **SystemGraph staleness:** the "one source of truth organ map" omits dozens of real classes, encodes `core.bodyCoordinator` as unreachable (no inbound edge) though it is live, and points `safety.approvalMechanism` at a non-existent `ApprovalMechanism` (real: `ApprovalGate`).
3. **MemoryGraphStore neighbors()/edges:** PRD AC promises a weighted edges API; the store has only a nodes table; weights are derived in `BlendedMemoryRetriever`.
4. **FEP premise:** the PRD assumed a Stage 01B prediction-error/doubt scorer; none exists. The real doubt carrier is `UncertaintyProfile`, gated by `CognitiveAdmissionPolicy` (which IS live).
5. **Two ResourceGovernors / two CapabilityRegistries:** `com.jarvis.app.resource` (microsystem) vs `com.jarvis.app.model` (model-tier); `com.jarvis.app.capability` (central) vs `com.jarvis.app.cognitive.capability` (execution-path). Only the `model` ResourceGovernor and `model` ModelManager's path are wired.
6. **CI workflow absent:** the documented CI-only Android build path does not exist in the current tree.

---

## 9. Deliverable status

This audit produced **no production code changes** (`git diff` over `mobile/` is empty). The only files touched this run are `.ralph/FULL_SYSTEM_AUDIT_20260902.md` (this report), `.ralph/prd.json`, and `.ralph/progress.txt`.

### What the next build queue should decide from
- Highest-value wiring gap: enable Galaxy Memory + Identity by constructing
  `AndroidMemoryGraphStore` + `BlendedMemoryRetriever` + `IdentityContext` and passing them
  into `CognitiveEngine` in `JarvisEngine` (the seams already exist; production just never
  fills them).
- Reconcile/complete the SystemGraph so it reflects actual classes and the real
  `CognitiveEngine` seams, and instantiate it as the real single source of truth.
- Resolve the 2 remaining `cognitive.planning` replan test failures.
- Decide whether to re-create a working CI `.github/workflows/build-app.yml`.
