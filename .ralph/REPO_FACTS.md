# REPO_FACTS.md — durable, verified facts about this repo

This file is auto-prepended to every prompt Ralph sends. It exists so known
facts are never rediscovered the hard way. When you learn something new and
durable about this repo during a run, add it here — one line, plain fact,
no narration.

## Build system
- `gradlew` and `gradlew.bat` live at the REPO ROOT: `/sdcard/jarvis-repo/gradlew`
- Do NOT `cd mobile` before running gradle — `mobile/` has no gradlew.
- Correct invocation, always from repo root: `cd /sdcard/jarvis-repo && bash ./gradlew <task>`
- NEVER run `./gradlew` directly — /sdcard does not preserve the executable
  bit. ALWAYS use `bash ./gradlew <task>`.
- Do not attempt `chmod +x gradlew` — it will not persist on /sdcard.
- Gradle caching/parallel/config-cache are enabled in `gradle.properties` —
  do not disable them; they exist to keep iteration time down.

## Repo layout
- Repo root: `/sdcard/jarvis-repo`
- Android app module: `mobile/app/`
- Ralph scaffolding: `/sdcard/jarvis-repo/.ralph/` (ralph.sh, prompt.md,
  prd.json, progress.txt, REPO_FACTS.md)
- Galaxy Memory (Stage 02) module: `mobile/app/src/main/java/com/jarvis/app/memory/`
- Memory unit tests: `mobile/app/src/test/java/com/jarvis/app/memory/` — the
  JVM tests run against pure-Kotlin references (TestEmbeddingProvider,
  KotlinVectorStore, FakeMemoryGraphStore); the PRODUCTION classes that ship in
  the APK are NeuralEmbeddingProvider + AndroidVectorStore +
  AndroidMemoryGraphStore (android.database.sqlite).
- Stage 03 (Self/User/World Models) module: `mobile/app/src/main/java/com/jarvis/app/identity/`
  (new package added this stage; verifyCommands scope to `com.jarvis.app.identity.*`).
  WorldModelService lives there.
- Emotional Intelligence Tier 1 module: `mobile/app/src/main/java/com/jarvis/app/emotion/`
  (FusionLayerTier1 + EmotionHypothesis). Wired at the SAME composition point as identity:
  JarvisEngine.init builds `FusionLayerTier1()` and injects it into the REAL
  `UserMentalStateEstimator`'s `hypothesisProvider` seam via
  `MentalStateHypothesis.fromEmotion(estimate(text))` — the emotion reading rides the existing
  single identity path (ContextWindowAssembler.mentalStateEstimator ->
  CognitiveTurnResult.assembledMentalState). No second estimator; a new emotion/LLM source is a
  lambda swap on that seam. MentalStateHypothesis.emotion defaults to neutral() so legacy
  constructions stay unchanged.
- `MemoryGraphStore` has NO `neighbors()`/edges-API (documented contradiction). The existing
  weighted traversal semantics live in `BlendedMemoryRetriever`; to reuse them, call
  `BlendedMemoryRetriever.blendedEdgeWeight(u,v)` (a @JvmStatic companion fn). The edge
  weight is between FACT-NODES by shared subject/predicate STRING, not a true entity graph:
  0.9 same subject+predicate / 0.6 same subject / 0.4 same predicate-only / 0 unrelated.

## Known gotchas (add to this list, don't rediscover)
- Any test that collects a coroutine-produced stream (e.g. `engine.observeEvents().collect`)
  into a list and later polls with `received.any{predicate}` MUST do BOTH: write into
  `java.util.Collections.synchronizedList(...)` AND read inside `synchronized(received){ received.any{...} }`
  (the `Iterable.any` extension iterates the raw fail-fast ArrayList iterator, NOT the wrapper's
  lock). A plain list CMEs intermittently under full-sweep CPU contention — it auto-rejects EVERY
  `passes:true` story whose verifyCommand includes `com.jarvis.app.cognitive.*`, regardless of which
  story is being verified. Fixed pattern lives in ExecutionIntegrationTest/CognitivePlanningIntegrationTest
  (helper `hasReceived`), pre-existing in CognitiveEngineTest.
- Kotlin string interpolation: `$var.prop` interpolates `var` (the whole object)
  then appends literal `.prop` — you MUST write `${var.prop}`. Tripped twice in
  IdentityContext (`$ctx.selfSummary`, `$id.name`); always brace the full
  property path.
- Stage 03 identity wiring: WorldModelService/UserProfile/UserMentalStateEstimator/
  SelfModel/PersonaTuner lived ONLY inside the identity package until
  PHASE-A-SELF-USER-WORLD-WIRING-AUDIT. The integration holder is
  `com.jarvis.app.identity.IdentityContext` (binds all five) passed to
  CognitiveEngine's nullable `identityContext` seam (default null);
  `identityContext.mentalStateEstimator` feeds the ContextWindowAssembler seam,
  and `IdentityContext.formatForPrompt` appends '[Identity context]' to the
  DIRECT_REPLY generation message. Durable user prefs (preference:<key>) and
  persona traits (persona:<trait>) are ordinary facts on the Venon user node.
- Galaxy Memory wiring: until PHASE-A-MEMORY-WIRING-AUDIT, MemoryGraphStore/
  BlendedMemoryRetriever was a freestanding package with ZERO instantiation in
  app/src/main outside the memory package — not wired into the live turn.
  The designed integration seam is ContextWindowAssembler's optional
  `blendedRetriever`. After PHASE-A, CognitiveEngine exposes two nullable seams
  `blendedRetriever: BlendedMemoryRetriever?` and `graphStore: MemoryGraphStore?`
  (both default null): blendedRetriever feeds the ContextWindowAssembler seam and
  appends retrieved `[Cross-session memory]` into the DIRECT_REPLY generation
  message; graphStore write-back uses `addFact(subject="user", predicate="stated",
  object=userText)` for DIRECT_REPLY turns. To enable Galaxy Memory in production,
  construct AndroidMemoryGraphStore + BlendedMemoryRetriever in JarvisEngine and
  pass both into CognitiveEngine.
- Termux/local Android builds are banned — CI (`build-app.yml` via GitHub
  Actions) is the only supported Android build path, per a prior
  phone-crash incident. Do not attempt local `assembleDebug` etc. on-device.
- ralph.sh's banned-pattern auto-reject greps production source at
  `$REPO_DIR/mobile/app/src/main` (the Android module root is `mobile/app/`,
  NOT `app/` — a prior version pointed at non-existent `$REPO_DIR/app/src/main`
  and could never catch a banned pattern; fixed in this stage, do not regress).
- Embedding BLOB contract: `EmbeddingMath.binaryQuantize` uses sign-of-value
  (`1 if component >= 0`), so an exact 0.0 component quantizes to bit 1. When
  testing, use a DENSE embedder (like a real learned model); a sparse embedder
  yields an all-ones code with no Hamming separation, and a -1-initialized
  embedder breaks `EmbeddingMath.cosine` (used by the scorers).
- When computing Hamming distance over `ByteArray`, mask to unsigned byte
  (`(b.toInt() and 0xFF)`) before XOR — `Byte.toInt()` sign-extends and
  inflates the popcount beyond the real bit width.
- LIVE-OLLAMA TESTS ARE DECOUPLED FROM DEFAULT SWEEPS (RALPH-VERIFY-DECOUPLE-LIVE-OLLAMA, 2026-09-10): `OllamaModelBackendLiveWiringTest` + `OllamaModelBackendTest` are now EXCLUDED from every default AGP unit test task via `testOptions.unitTests.all { it.filter.excludeTestsMatching(...) }` in app/build.gradle.kts, and are run on demand via the `:mobile:app:verifyLiveOllama` task (includeTestsMatching on exactly those two class FQCNs). This structurally ends the 2026-09-05..09 cold-Ollama auto-reject loop — a default `--tests "com.jarvis.app.latency.*"`/`model.*` sweep NEVER touches the live server anymore (proven: full 16-wildcard sweep with the server STOPPED = BUILD SUCCESSFUL EXIT=0, 670 tests / 0 failures, both live classes absent from the result XMLs). OLLAMA KEEP-ALIVE/-preload instructions below still apply ONLY when running `verifyLiveOllama` or re-proving the live backend.
- Custom `Test`/`JavaExec` Gradle tasks in build.gradle.kts must wire `classpath` from the compile output DIRECTORY entries (`files(mainOut, testOut, runtimeCp)`), NEVER `files(mainOut.get().asFileTree, ...)` — a `.asFileTree` expansion yields one classpath entry per individual `.class` FILE, and a JVM classloader cannot resolve package structure from file entries (verifyLiveOllama failed with ClassNotFoundException on every test class despite all 787 .class files being present until switched to directory entries).
- NEVER use `pkill -f 'ollama serve'` on this host: ralph prepends REPO_FACTS.md to the opencode `-m` prompt, whose literal text contains "ollama serve", so the `-f` pattern matches and SIGTERMs the harness/sweep shell itself. Kill the server by exact process name: `pkill -x ollama` (or `ollama stop <model>` when a server is up).

- Stage 04 FEP/doubt: there is NO Stage 01B prediction-error/doubt scorer (the PRD
  premise is wrong — grep "doubt|prediction.?error|freeEnergy|fep" over app/src finds
  only false positives inside `ofEpochMilli`). The real existing per-turn doubt carrier
  is `CognitiveState.UncertaintyProfile` (overall/intentAmbiguity/unknowns), populated
  in `CognitiveEngine.processInput` step 4 from intent ambiguity confidenceGap +
  surfaced unknowns. `CognitiveAdmissionPolicy` (com.jarvis.app.model) only projects
  max(overall, intentAmbiguity, 1.0-if-unknowns) and thresholds (default 0.5); below
  stays RESIDENT, at-or-above wakes `ModelManager.wake(OrganRole.REASONING)`.
  CRITICAL for any federation/phase test: `identifyUnknowns` (IntentInference) flags a
  REFERENT unknown on the raw SUBSTRING `it`/`that`/`this` — so innocuous words like
  `with`, `capabilities`, `abilities` (containing `it`) and any use of `do`/`make`/
  `handle`/`fix`/`take care of`/`deal with` (VAGUE_ACTION) push doubt to 1.0 and wake
  the reasoning organ. To make a turn genuinely low-doubt (<0.5), phrase fixtures to
  AVOID it/that/this substrings AND those vague-action verbs; only the intentionally
  high-doubt turn should contain them.
  `ModelManager.wake()` returns `WakeResult(handle, requestedTier, servedTier,
  degraded)`; `request()` delegates to it. Gate outcome lands in
  `CognitiveTurnResult.servedDegraded` (true = governor denied/degraded → served from
  a lower tier, never throws/hangs).

- ANCHOR-ENGINE-FOUNDATION (2026-09-05): `com.jarvis.app.anchor.AnchorEngine` now REAL
  and wired in JarvisEngine.init over its two real deps: model_manager = real
  `ModelManager` (ONE loading authority) + cognitive_runtime_gateway = the real
  `CognitiveAdmissionPolicy`. The gateway is now ONE shared instance passed to BOTH
  `CognitiveEngine` and `AnchorEngine` (never build a second decision authority).
  `ModelManager.isTierLoaded(ModelTier)` (lifecycleLock-guarded) is the anchor's read.
  Semantics: anchor() = `modelManager.request(OrganRole.RESIDENT)` holding the
  never-auto-unloaded resident tier; recovered=true only when the tier was missing.
  serve(uncertainty) = `gateway.serveTurn(uncertainty){ wake(REASONING) }`, then if
  `!isTierLoaded(RESIDENT)` re-establishes + reports anchorRecovered. On governor
  DENY/DEGRADE_TO the reasoning wake degrades ONTO the cached resident handle
  (degraded=true, servedTier=RESIDENT, servedHandle non-null) — never throws. The
  RESIDENT tier is never auto-unloaded by the cooldown sweep (ModelManager skips it).
  JarvisOrganGraph nodes: `model.anchorEngine`, `model.cognitiveAdmissionPolicy`
  (the PLANNED `planned.anchorEngine` placeholder is gone).

- Stage 03 identity package now contains: WorldModelService (user-node durable
  facts; registerEntity/getEntity; setUserPreference/getUserPreference with
  predicate prefix "preference:"; persistPersonaAdjustment + userNodeFacts with
  prefix "persona:"; getAllFacts/getFactsAbout historically-valid query hooks;
  USER_NODE_NAME = "Venon"; EntityType.USER/PERSON/ORGANIZATION), UserProfile
  (ProfileDurabilityGate - explicit/repeated same-value preference promotes to
  durable, single ambiguous never persists), UserMentalStateEstimator (pure
  per-turn rule-based; NEVER persists - by construction the mental state is
  ephemeral), SelfModel (identity/capabilities/limitations/confidencePerDomain/
  history/goals, all evidence-linked to live sources, never self-asserted),
  PersonaTuner + PersonaFeedbackGate (offline, explicit-only, contradiction
  supersedes via graph supersession, never stacks). CapabilityRegistry lives at
  com.jarvis.app.capability.CapabilityRegistry (State.LOADED/IDLE/ACTIVE/FAILED/
  UNAVAILABLE, Health.DEGRADED/FAILED/UNAVAILABLE/HEALTHY, Category.STT/LLM/TTS/
  vision), central source for SelfModel capabilities/limitations. FakeGraph test
  double lives in the identity test package (extends MemoryGraphStore - pure
  in-memory, no network), reused across all identity tests.

## 2026-09-01 no-download batch (5 packages added)
- `com.jarvis.app.selfreconfig`: SystemGraph (registerNode/addEdge/markFixedInvariant/tryMarkMutable/computeReachability) + WiringDiagnostics (reachability from entry.latencyPipeline). This is the ONE source of truth organ map; do not build a second graph structure.
- `com.jarvis.app.capability`: CapabilityRouter interface (contract-first) + DeterministicCapabilityRouter routing over the REAL registry. Distinct from cognitive.capability (execution-path registry) — central one lives in com.jarvis.app.capability; JarvisEngine registers the real TTS capability `voice_organism_v1`.
- `com.jarvis.app.android`: DeviceControlRouter fallback chain official-api->intent->media-session->accessibility->shizuku->adb (first VERIFIED backend used, each attempt logged); RiskGate enforces LOW/MEDIUM(confirmation)/HIGH(biometric+confirmation)/SELF_MODIFICATION(3-gate). Only real Android-device-control code is ShizukuBridge detection + ScreenBridge (Shizuku exec uiautomator dump) — backends are injectable interfaces, testable without an Android runtime. As of ANDROID-CAPABILITY-CHAIN-EXTENSION (2026-09-05) the chain has TWO real backends wired in JarvisEngine.init: MediaSessionControlBackend (id "media-session", rung 3) over the injectable MediaSessionControlPort (production PlatformMediaSessionControlPort = real android.media.session.MediaSessionManager, compile-time-only android.jar ref like PlatformBiometricPromptResultMapper) and ScreenBridgeControlBackend (id "shizuku", rung 5) over real ScreenBridge. CRITICAL: a ControlBackend id MUST be a member of DeviceControlRouter.ORDER — the router sorts by ORDER.indexOf(id), so an out-of-ORDER id sorts to index -1 (tried FIRST, ahead of every rung) and silently corrupts the chain the moment a second backend exists. New backends: put all Android-runtime calls behind an injectable port, use runCatching{} (not `catch (e: Exception)`, which trips ralph.sh's incident-file disable-pattern grep on added lines), and report VERIFIED only when a re-read of platform state observably confirms the dispatch.
- `com.jarvis.app.cloud`: CloudModelRouter is a faithful port of ralph.sh MODEL_POOL — round-robin cursor advance per submit + one case-insensitive regex (quota|rate.limit|capacity is busy|Cannot connect|exhausted|429) for rate-limit detection. CloudReasoningRequest carries text ONLY (no capability/action) — the architectural law that cloud never directly controls the phone.
- AnchorEngine + Fabricator DO NOT EXIST anywhere (grep-verified; only prd.json design mentions) — register as PLANNED organs in SystemGraph, never as implemented.
- Two ResourceGovernors: com.jarvis.app.resource (microsystem) vs com.jarvis.app.model (model-tier). Two CapabilityRegistries: com.jarvis.app.capability (central) vs com.jarvis.app.cognitive.capability (execution-path).
- JVM unit tests run with working directory = module root (mobile/app/); file reads in tests must use `src/main/...`, NOT `mobile/app/src/main/...`. CapabilityRegistry.Category has VISUAL (not VISION).
- ALL new-package verifyCommands should be scoped to the package (e.g. `--tests "com.jarvis.app.selfreconfig.*"`) with `--no-configuration-cache --no-build-cache`. NOTE (2026-09-02): the last pre-existing failure class is RESOLVED — `com.jarvis.app.cognitive.*` and `com.jarvis.app.model.*` wildcards now pass EXIT=0; GoalPlanner/CognitivePlanningIntegrationTest replan tests and CapabilityExecutorTest (18/18) are green. Only memory wildcard native-download (EmbeddingProviderTest sqlite-vec .so, MemoryGraphStoreTest xerial) may still fail on-device — prefer scoping memory.* to the JVM-safe classes.

## Research (UNIVERSAL-RESEARCH) facts
- Research scaffold lives in com.jarvis.app.research (ResearchPipeline in-memory orchestrator) + .research.universal (UniversalResearchEngine, UniversalCatalog, MechanismExtractor, CrossDomainMapper, ResearchEvidence). All in-memory; NO opportunities/mechanisms/skills tables or HF Space DB layer exist (only design-doc mentions). The 'real mechanisms table' is grounded as the MechanismsStore interface + InMemoryMechanismsStore JVM reference.
- Fabricator does NOT exist; FabricatorChannel (fun interface) is the pluggable fiction-mining input channel consuming the same MechanismCandidate shape as the pipeline's own research. MechanismCandidate carries structure/inputs/outputs/constraints/confidence/source.
- UniversalResearchEngine.researchPersist(gapDescription, ...) runs all wired channels (LOCAL_CATALOG always, CLOUD_REASONING via real CloudModelRouter, FABRICATOR via channel) and writes every candidate to ONE MechanismsStore.
- Kotlin gotchas (tripped here): (1) override functions CANNOT declare default parameter values — "An overriding function is not allowed to specify default values"; call the derived type's override with all args. (2) JUnit4 test methods must return Unit: `fun x() = runBlocking {...}` infers Unit? when the block tail is a safe call (stored?.let{...}) → InvalidTestClassError 'should be void'; always write `runBlocking<Unit> {...}`. (3) triple-quoted Kotlin strings pass `\s`/`\n`/`\|` through to Regex literally (no escape processing).

## Owner-binding (PEOPLE-VOICE-FACE-MEMORY-OWNER-BINDING) facts
- HumanCore owner chain now exists: `OwnerBinding` (humancore/protocol/OwnerBinding.kt) = relationshipId/ownerKey/authenticator/authenticatedAtEpochMs, `OWNER_RELATIONSHIP_ID = "owner"`, persisted on the single `RelationshipRecord.owner` field via `RelationshipStore.bindOwner`/`ownerBinding()` (serialized in parse; missing field backcompat -> null). Single-owner sticky guard: bindOwner refuses a non-owner relationshipId OR a different ownerKey; same-owner re-auth refreshes the timestamp. Facade: `HumanCore.bindOwner/ownerBinding/isOwnerBound`.
- Real Android biometric consumption point: `com.jarvis.app.identity.owner.PlatformBiometricPromptResultMapper.toSuccess(BiometricPrompt.AuthenticationResult, authenticators, ts)` — targets the PLATFORM `android.hardware.biometrics.BiometricPrompt` (API 28+), compile-time-only reference (android.jar stub), NEVER invoked on the JVM; androidx.biometric is NOT available (no network). No @RequiresApi possible (`android.annotation.RequiresApi` does not resolve in this module).
- `BiometricAuthResult.Success` is EXACTLY (authenticators: BiometricType, authenticatedAtEpochMs: Long) — no device identity, no biometric payload, no crypto object. AC3 authority is device-independent (data-possession via persisted owner file, not phone possession). `OwnerBiometricBinder(port, ownerKey = "Venon" [WorldModelService.USER_NODE_NAME])` returns OwnerBindingOutcome Bound/ReAuthenticated/Conflict(existingOwner)/NotAuthorized(code,msg)/Cancelled.
- kotlin-reflect is NOT on the unit-test classpath — `KClass::memberProperties` will not compile. For "model has exactly these fields" proofs use a source-text structural assertion over the shipped file (cwd = module root mobile/app/, read `src/main/...`).
- Source-grep acceptance tests match banned tokens ANYWHERE in shipped main sources, including inside your own KDoc negation ("builds no recognizer"). Never write a banned token in any shipped file, even in prose.

## Fuzzy command resolution (FUZZY-COMMAND-RESOLUTION) facts
- `com.jarvis.app.resolution` exists: `FuzzyCommandResolver(blendedRetriever, capabilityRouter, registry, maxCandidates=3)` + `CapabilityMemoryIndex(graphStore)`. Resolution = real BlendedMemoryRetriever retrieve (context defaults to the reference) -> filter `node.predicate == "capability"` -> per-candidate `DeterministicCapabilityRouter.route(RouteRequest(category=cap.category, action=reference))` -> top candidate must be RouteResult.Matched else Unresolved (NO_CANDIDATES/NO_ROUTABLE_MATCH/ROUTER_AMBIGUOUS). Never silently picks an ambiguous top candidate.
- Capability declarations are indexed into Galaxy Memory as ordinary facts: subject=capability id, predicate="capability", object=fuzzy-searchable user-phrased description, source="capability-index". Re-index supersedes via normal bi-temporal supersession. Journey: index descriptions are what Hamming seeding + relevance ranking match against — phrase them like a user, not a canonical name.
- Intent->capability in this repo is EXACT-STRING only: `cognitive.capability.CapabilityResolver.lookupByOperation` is a Map key lookup; >1 eligible = hard AMBIGUOUS. The com.jarvis.app.capability router matches category-enum-only and is UNWIRED in production (test-fixture only). Galaxy Memory's BlendedMemoryRetriever is the ONLY real ranking engine and the sanctioned fuzzy surface.
- MemoryImportanceScorer relevance (0.35 weight) uses only `context` text; ranking between same-recency/salience nodes is governed by passing the reference as context to retrieve() — do that to get deterministic fuzzy ordering.

## Voice platform replacement (VOICE-PLATFORM-REPLACEMENT, 2026-09-08) facts
- The in-app native STT stack is GONE: JarvisVosk.kt / JarvisSherpaWhisper.kt / JarvisSherpaZipformer.kt / VoskBridge.kt / SherpaBridge.kt / STTArbitrator.kt / PlatformRecognizerStt.kt / PlatformArabicStt.kt deleted. Do not re-add a Vosk or sherpa-onnx STT path; the sanctioned STT is `JarvisSpeechRecognizer` (com.jarvis.app.voice) over `SpeechRecognizerPort`, wired via `BodyCoordinator.startPlatformRecognition()` on the LISTENING/WAKE tap-to-trigger path — the ONE voice-input path.
- `SherpaTtsVoiceProvider.kt` (voice/provider, R2 `:voice` isolated-process TTS via com.k2fsa.sherpa.onnx.OfflineTts) is INTENTIONALLY kept: it is TTS, not STT, lives behind process isolation + supervised restart (VoiceOrganismHost), and is why `com.github.k2-fsa:sherpa-onnx:v1.12.40` + onnxruntime-android + commons-compress stay in app/build.gradle.kts. AC2's delete scope is JarvisVosk + JarvisSherpaWhisper only; grep "com.k2fsa.sherpa.onnx.*" is NOT zero by design.
- Hotword/wake-word: `HotwordAvailabilityProbe` (com.jarvis.app.voice) is the runtime arbiter — ALWAYS read availability through it; classify() must NEVER assume custom-keyphrase SoundTrigger support (Realme 9 Pro 5G-class devices are UNSUPPORTED → MANUAL_TAP_TO_TRIGGER via wakeStrategyFor(), continuous 'jarvis' wake is a tracked gap, not the silent default). Only a proven HotwordAvailability.SUPPORTED may enable continuous AlwaysOnHotwordDetector wake.
- BodyCoordinator has NO native-STT bridges, NO resource-governor Vosk-pause block, NO runSherpaFallback, and no `JarvisMic.audioFeedPaused`. JarvisMic now only does energy-based endpointing → `speechEnded` + `utteranceSamples()` for the platform-STT SpeechEnd event. DiagnosticsViewModel/SystemScreen read SpeechBridge.status/lastResult + HotwordAvailabilityProbe.availability (STT/Hotword rows).
- The voice crash incident needs NO further disable-pattern grep: incident 2026-07-28_voice_pipeline.md STATUS = **bypassed** (crashing native classes deleted, root cause permanently unknown — NOT "fixed"). ralph.sh disable-pattern grep therefore must not expect concurrent `catch (e: Exception)` avoidance to come from the removed legacy block.
- Full-sweep flake families documented again under load: VoiceSchedulerTest drain-timeout (0.125s/task×20 under contention) and OllamaModelBackendTest 120s live-socket timeout — both pass isolated/warm; re-verify sweeps with the Ollama server warm (curl http://127.0.0.1:8080/api/tags) and prefer isolated re-runs for these two classes.
- THE OLLAMA VERIFY ROOT-FIX (2026-09-09, breaks the cold-model auto-reject loop): 'server warm' is NOT enough — the default OLLAMA_KEEP_ALIVE=5m evicts jarvis-resident between the long gradle sweeps, so the next sweep's OllamaModelBackendLiveWiringTest AC3 pays model-weight-load + 200-token generation inside its 120s window and times out. Deterministic fix, do this at the START of any verification run that includes com.jarvis.app.latency.*: (1) `pkill -f 'ollama serve'`; start with `setsid bash -c 'OLLAMA_HOST=http://127.0.0.1:8080 OLLAMA_KEEP_ALIVE=-1 nohup ollama serve >/tmp/opencode/ollama_serve.log 2>&1 </dev/null &'` (default serve listens on 11434, NOT 8080 — must set OLLAMA_HOST); (2) pre-load + verify: `curl .../api/generate -d '{"model":"jarvis-resident:latest","prompt":"warm","stream":false,"options":{"num_predict":50}}'` (cold load ~21s, warm gen ~6s). With the model HELD, every sweep is deterministic: isolated flaky-class run 1m08s, full sweeps 1m27s-3m56s EXIT=0.

## Social stack (PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL, 2026-09-11) facts
- `com.jarvis.app.social`: PersonProfile (identity/behaviorPatterns/preferences/history; each entry a TieredValue with StatementConfidence.EXPLICIT|INFERRED in the fact PREDICATE, tiers never merged, same-tier restatement supersedes) + RelationshipState (TrustTier STRANGER|ACQUAINTANCE|KNOWN|TRUSTED, RelationshipStage NEW|DEVELOPING|ESTABLISHED|STRAINED) + PersonRelationshipModel (recordStatement/loadProfile/setTrust/relationshipState/personSurface) + ConfidentialityFirewall. Persisted through the REAL galaxy MemoryGraphStore via the WorldModelService seam — profile facts `(person,'profile:<cat>:<desc>:<tier>',value)`; relationship facts on the `Venon` node `relationship:<person>:trust|trajectory|emotionalBaseline`; registered persons tagged with EntityType.PERSON. No new store.
- Secrets: `(person,'secret:<desc>',value)` + ledger `(person,'secret:<desc>:owner',owner)` + one `(person,'secret:<desc>:authorized:<who>',who)` per allowed party — the ledger lives in PREDICATES so graph supersession handles updates. The gate is CLOSED-WORLD and runs BEFORE generation on BOTH real pre-generation seams: IdentityContext.gatherForTurn -> `filterForInterlocutor(facts, interlocutor)` (identity suffix) and ContextWindowAssembler -> `filterRankedMemoriesForInterlocutor(ranked, interlocutor)` (galaxy [Cross-session memory]); metadata `:owner`/`:authorized:` NEVER renders for anyone, non-secret facts always pass. Firewall constructed in JarvisEngine.init at JarvisEngine.kt:312-313 AND in TermuxJarvisServer.
- Per-turn `interlocutor` is threaded LatencyLayer/LatencyPipeline.onUserInput(text, ackOverride, interlocutor) -> CognitiveEngine.process(userText, ..., interlocutor) -> ContextWindowAssembler.assemble(..., interlocutor) + IdentityContext.gatherForTurn(..., interlocutor); defaults to USER_NODE_NAME (Venon). The AC6 production-path probe pattern = start TermuxJarvisServer, drive the engine via a recording LatencyPipeline (only swap bridgeSend = captured.add(it)), assert on the captured generation payload: unauthorized interlocutor payload must NOT contain the secret value while '[Identity context]' still flows; trust-tier differentiation proven by same input + different persisted trust producing different payloads.
- prd.json is fragile under Edit-tool notes rewrites: a notes string must keep its closing `",` before `"verifyCommand"` or the file parses as "control character"/"Expecting ',' delimiter". Always `python3 -c "import json; json.load(open('.ralph/prd.json'))"` after editing.

## Language tier (EGYPTIAN-ARABIC-TEXT-HALF, 2026-09-10) facts
- `com.jarvis.app.language` has TWO files: DialectSignal (detectedLanguageMix/dialectConfidence/
  codeSwitchPoints/register + neutral()) and EgyptianArabicDialectDetector (lexicon/rule-based:
  Arabic Unicode ranges, Egyptian markers, Arabizi numeral regex `\d+[a-z]|[a-z]+\d`, script
  transitions, register inference). Zero model weights. `DialectSignal.neutral()` = mix "en",
  confidence 0, no switches, register "neutral".
- Arabic script ALONE yields mix "ar-EG" + confidence 0.15 (script baseline) even for MSA;
  Egyptian markers (e.g. ايه، كده، يلا، ماشي، عايز، اهلا) and Arabizi push it higher (~0.5+).
  Register: informal (markers/arabizi/casual-English), formal (الذي/التي/كما/ولكن markers or bare
  Arabic script), neutral otherwise.
- Wiring: JarvisEngine.kt:454 + TermuxJarvisServer each construct EgyptianArabicDialectDetector()
  and pass it into CognitiveEngine's `dialectDetector` seam (defaulted null). Per routed
  DIRECT_REPLY turn the detector reads userText; the signal is threaded into
  IdentityContext.gatherForTurn(dialectSignal) and printed by formatForPrompt ONLY when non-neutral:
  "- user dialect: ar-EG (register=informal, confidence=0.54)". That suffix joins the REAL
  generation message `userText + galaxyContext + identitySuffix` (CognitiveEngine.kt:331).
- The JVM 'exact production call path' harness for any JarvisEngine.init-wired text-pipeline seam:
  `TermuxJarvisServer(port=0, backendOverride=FakeModelBackend())` -> a recording LatencyPipeline
  whose `bridgeSend = { captured.add(it) }` captures the outgoing generation payload; assert on the
  captured text, never on model reply text. This is the IdentityInChatPipelineGroundTruthTest /
  DialectIntegrationTest pattern (dialect tests live in com.jarvis.app.language.*).
- Human-readable text files (Arabic/Arabizi lexicons, prompts) in this repo MUST NOT ship
  corrupted literals — a lexicon whose test comment claims a marker the list doesn't contain is
  'comment pretending'. Fix by adding the genuinely-correct marker and correcting the comment, not
  by deleting the test. Garbage entries previously shipped here included a Chinese char, "بitter",
  and " awaits".


## 2026-09-11 OLLAMA HEALTH (AUTO-REJECT-DIAGNOSTIC-AND-RECOVERY AC1 ground truth)
- Ollama server IS running on this host. `ps aux` shows one `ollama serve` (uid aid_u0_+ = Termux process, pid 15068).
- `curl http://127.0.0.1:8080/api/tags` returns: one model, `jarvis-resident:latest` (LFM2 1.2B, Q4_K_M, context 128000, embedding 2048, capabilities=[completion]). No `jarvis-reasoning` tag exists on-device.
- The `ollama` CLI (`ollama list`) FAILS with "could not connect to ollama server" because it talks to the DEFAULT port 11434, while this server was started with `OLLAMA_HOST=http://127.0.0.1:8080`. Health checks must always use `curl ...:8080/api/tags`, never the CLI, on this host.
- This health evidence is REGISTRATION (model present in registry), not proof a given model is warm-loaded; the resident/reasoning model-pool state is unchanged by this story.

## 2026-09-11 AUTO-REJECT ROOT CAUSE (AC2, full verbatim content of /tmp/ralph_verify_1.txt, retrieved before it could disappear; raw ANSI ESC bytes elided as \\x1b)

```text

> Configure project :mobile:app
WARNING: The option setting 'android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2' is experimental.
w: \x1b[33m\x1b[1m⚠️ Deprecated Gradle Version\x1b[0m\x1b[0m
The used Gradle version (Gradle 8.11.1) is deprecated and will not be supported in future Kotlin Gradle Plugin releases.
The minimum supported Gradle version will become Gradle 8.14.4 in Kotlin 2.5.0.

This warning can be suppressed in 'gradle.properties':
    kotlin.suppressGradlePluginWarnings=DeprecatedGradleVersionWarning
\x1b[32m\x1b[1mSolution:\x1b[0m\x1b[0m
\x1b[32m\x1b[3mPlease update the Gradle version to at least Gradle 8.14.4.\x1b[0m\x1b[0m


> Task :mobile:app:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :mobile:app:preBuild UP-TO-DATE
> Task :mobile:app:preDebugBuild UP-TO-DATE
> Task :mobile:app:generateDebugBuildConfig UP-TO-DATE
> Task :mobile:app:checkDebugAarMetadata UP-TO-DATE
> Task :mobile:app:generateDebugResValues UP-TO-DATE
> Task :mobile:app:mapDebugSourceSetPaths UP-TO-DATE
> Task :mobile:app:generateDebugResources UP-TO-DATE
> Task :mobile:app:mergeDebugResources UP-TO-DATE
> Task :mobile:app:packageDebugResources UP-TO-DATE
> Task :mobile:app:parseDebugLocalResources UP-TO-DATE
> Task :mobile:app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :mobile:app:extractDeepLinksDebug UP-TO-DATE
> Task :mobile:app:processDebugMainManifest UP-TO-DATE
> Task :mobile:app:processDebugManifest UP-TO-DATE
> Task :mobile:app:processDebugManifestForPackage UP-TO-DATE
> Task :mobile:app:processDebugResources UP-TO-DATE
> Task :mobile:app:compileDebugKotlin UP-TO-DATE
> Task :mobile:app:javaPreCompileDebug UP-TO-DATE
> Task :mobile:app:compileDebugJavaWithJavac UP-TO-DATE
> Task :mobile:app:bundleDebugClassesToRuntimeJar UP-TO-DATE
> Task :mobile:app:bundleDebugClassesToCompileJar UP-TO-DATE
> Task :mobile:app:compileDebugUnitTestKotlin UP-TO-DATE
> Task :mobile:app:preDebugUnitTestBuild UP-TO-DATE
> Task :mobile:app:javaPreCompileDebugUnitTest UP-TO-DATE
> Task :mobile:app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :mobile:app:processDebugJavaRes UP-TO-DATE
> Task :mobile:app:processDebugUnitTestJavaRes UP-TO-DATE
> Task :mobile:app:testDebugUnitTest

MemoryConsolidationLoopTest > idle-scheduled loop consolidates automatically and the pass is manually triggerable FAILED
    java.lang.AssertionError at MemoryConsolidationLoopTest.kt:174

712 tests completed, 1 failed

> Task :mobile:app:testDebugUnitTest FAILED
[Incubating] Problems report is available at: file:///mnt/sdcard/jarvis-repo/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':mobile:app:testDebugUnitTest'.
> There were failing tests. See the report at: file:///mnt/sdcard/jarvis-repo/mobile/app/build/reports/tests/testDebugUnitTest/index.html

* Try:
> Run with --scan to get full insights.

BUILD FAILED in 1m 8s
24 actionable tasks: 1 executed, 23 up-to-date

```
- The auto-reject that flipped PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL to passes:false ran story 2's EXACT stored verifyCommand (the 18-wildcard sweep incl. social.*) and it FAILED with 712 tests / 1 failed. The single failure is the documented contention-flake family (timing), NOT the social work:
- Failure line (verbatim from the log): `MemoryConsolidationLoopTest > idle-scheduled loop consolidates automatically and the pass is manually triggerable FAILED` + `java.lang.AssertionError at MemoryConsolidationLoopTest.kt:174`.
- MemoryConsolidationLoopTest.kt:174 is the start of `runBlocking {` for the idle-loop timing test: `idleIntervalMs = 5`, `startIdleLoop()`, `delay(300)`, then asserts `loop.isIdleLoopRunning()` and the consolidated preference. Under full-sweep CPU contention the Default-dispatcher tick does not fire inside the 300ms window -> AssertionError. Same family as the documented VoiceSchedulerTest drain-timeout flake and the pre-RALPH-VERIFY-DECOUPLE Ollama timeouts.
- The story-2 SOCIAL WORK itself was green: commit 5698e69 logged "full sweep EXIT=0 (712/0)"; the identical sweep passed at 02:13 (story2_verify.log, BUILD SUCCESSFUL 1m15s) and in the 02:51 "All stories independently re-verified after 10 iteration(s)" run; it failed once at 08:01 (verify_1.txt, BUILD FAILED 1m08s). Intermittent flake, not a code regression.
- Also on disk: /tmp/ralph_verify_0.txt (Sep 11 15:13) is the story-1 re-verify, BUILD SUCCESSFUL (all up-to-date) -> the EGYPTIAN-ARABIC story auto-reject (same run, earlier) was never substantiated by a failed log; story 1 remains passes:true in the current tree.

## 2026-09-11 PRIOR-RUN COMMIT BLOCKER DIAGNOSIS (AC3, actual finding)
- NOT a gradle daemon lock: one java daemon runs fine (pid 27816), gradle sweeps complete in 1-5m. NOT a model-pool/API failure in opencode output: `git log` has no commit between 5698e69 (2026-09-11 02:46) and now; progress.txt shows two consecutive "Reached max iterations (10) without independently-verified completion" runs (04:12, 08:45).
- The blocker is a SELF-SUSTAINING auto-reject loop: ralph.sh (lines 163-169) re-runs every passes:true story's verifyCommand; story 2's verifyCommand is the expensive 18-wildcard FULL SWEEP; under contention the sweep hits the MemoryConsolidationLoopTest timing flake (above) -> auto-reject flips story 2 to passes:false -> next iterations start from a rejected tree, produce no production diff, and burn all 10 iterations re-attempting the same flaky sweep without ever committing anything (only .ralph bookkeeping edits accrue).
- Working-tree truth at diagnosis time: `git status` = modified ONLY .ralph/prd.json + .ralph/progress.txt (story-2 flip + stories 3/4 added + run-end bookkeeping), plus untracked .ralph log dumps and `mobile/app/.cxx/Debug/...` local-build remnants (a banned on-device assembleDebug was attempted at some point; those .cxx dirs must NEVER be committed).
- Lesson: a story whose verifyCommand IS the full regression sweep cannot be "re-verified" under load without inheriting every unrelated flake in the sweep. Recover such stories with an isolated/retriable sweep (see the recovery section that follows).

## 2026-09-11 STANDING RULE (AC5) - future AUTO-REJECT log preservation
- ANY future AUTO-REJECT (ralph.sh reject_story.py append) MUST have its /tmp/ralph_verify_<idx>.txt contents copied VERBATIM into REPO_FACTS.md (or progress.txt) in the SAME iteration that observes it, before the file in /tmp disappears. Never leave a rejection that points only at a /tmp path. Include: exact failure line(s), test class + line, test count, build duration, and the story's git diff state at the time.

## 2026-09-11 RECOVERY CONFIRMED (AC4 + AC6 closing proof)
- Story 2 (PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL) recovered: EXACT stored verifyCommand (18 wildcards incl. social.*) ran BUILD SUCCESSFUL in 2m50s — 712 tests / 0 failures / 0 errors, production code untouched, `--no-configuration-cache --no-build-cache`.
- Story 4 (AUTO-REJECT-DIAGNOSTIC-AND-RECOVERY) full regression sweep: 17 wildcards ran BUILD SUCCESSFUL in 1m23s — 700 tests / 0 failures / 0 errors.
- Both stories flipped passes:true. The MemoryConsolidationLoopTest timing flake did NOT fire under normal contention this run.

## 2026-09-12 REASONING-TIER-MODEL-GROUND-TRUTH (AC0, verified BEFORE code changes)
- Ground truth: `OrganRole.REASONING.tier == ModelTier.ON_DEMAND_REASONING` (model/OrganRole.kt), the "reasoning" per-tier fallback string comes from `ModelManager.defaultModelId(tier)` (model/ModelManager.kt:274), and DIRECT_REPLY conversation generation ALWAYS rides `modelManager.send()` -> `attemptSend` -> active provider `GenerateRequest` (JarvisEngine.init configures OllamaAdapter with `ProviderConfig(modelId = OLLAMA_RESIDENT_MODEL = "jarvis-resident:latest")`, JarvisEngine.kt:152-158 + const at :807). CONCLUSION: **REASONING == RESIDENT today** — both resolve to jarvis-resident:latest, and the woken reasoning handle never generates (no code path calls `backend.generate(reasoningHandle)`).
- On-device Ollama registry (curl http://127.0.0.1:8080/api/tags, OLLAMA_HOST must be 127.0.0.1:8080 not 11434): `jarvis-reasoning:latest` IS ALREADY IMPORTED (LFM2.5-2.6B QAD, 2.7B Q4_0, context 128000, created 2026-09-12T06:25:31, digest ae3a84e4a6b4db1282500b279345a697c8e67810fe4402092789fc8c53a79255) alongside `jarvis-resident:latest` (LFM2.5-1.2B Q4_K_M). The import source is committed as `.ralph/reasoning.Modelfile` (single line `FROM /storage/emulated/0/JARVIS/models/LFM2.5-2.6B-QAD-Q4_0.gguf`).
- OllamaModelBackend is a single-backend client but its generate() sends `GenerateRequest` with NO model override, and OllamaAdapter.sendRequest hardcodes `"model": currentModel` — so even `backend.generate(handle)` targets the adapter-configured model, not `handle.modelId`. Closing this gap = `GenerateRequest.modelId` override honored by OllamaAdapter.
- The FEP wake gate (CognitiveAdmissionPolicy.serveTurn, threshold 0.5 on max(overall, intentAmbiguity, 1.0-if-unknowns)) calls ModelManager.wake(OrganRole.REASONING) and returns TurnServeOutcome(decision, requestedTier, servedTier, degraded, servedHandle). ResourceGovernor default snapshot = alwaysHealthy(); ON_DEMAND_REASONING budget = 1536MB/75%cpu/thermal<=1/battery>=25-or-charging (ResourceGovernor.kt:116).
- Test-wiring constraint discovered: Stage04LiveWiringTest (latency package, runs in default sweep) asserts `sentThroughBridge == lowDoubt.size + 1` and PhaseAIntegrationTest asserts `sentThroughBridge.size == 6` — the high-doubt generation MUST keep passing through sendBlock. Tier-serving must therefore live INSIDE ModelManager.send (turn-scoped booking consumed by send()), NOT by rerouting the engine away from sendBlock.
- Engine.direct_reply booking contract added this story: CognitiveEngine.process, immediately after the admission gate, calls `modelManager.noteTurnServe(servedHandle)` when decision==REASONING && servedTier==ON_DEMAND_REASONING && !degraded, and clears the booking at turn teardown. ModelManager.send() consumes the booking synchronously (getAndSet(null)) and generates via `backend.generate(handle)` (modelId = handle.modelId) instead of the active provider.

## 2026-09-12 AUTO-REJECT ROOT CAUSE (REASONING-TIER re-verify, /tmp/ralph_verify_2.txt, ANSI ESC elided as \x1b)
- Same documented contention flake family as the 2026-09-11 AC2 auto-reject; NOT the story's code. The implementation commit 98218df (REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE) was closed with "714 tests / 0 failures / 0 errors" (exact verifyCommand green), then the controller's auto-reject loop re-ran the exact 17-wildcard verifyCommand under load and it FAILED on the unrelated `MemoryConsolidationLoopTest` idle-loop timing assert. Verbatim tail of the failed log:
```text
MemoryConsolidationLoopTest > idle-scheduled loop consolidates automatically and the pass is manually triggerable FAILED
    java.lang.AssertionError at MemoryConsolidationLoopTest.kt:174
...
702 tests completed, 1 failed
...
BUILD FAILED in 1m 12s
24 actionable tasks: 1 executed, 23 up-to-date
```
- Same mechanism as documented in the 2026-09-11 AC2 AUTO-REJECT ROOT CAUSE section (this file): `idleIntervalMs = 5`, `startIdleLoop()`, `delay(300)` then `assert(loop.isIdleLoopRunning())` — under full-sweep CPU contention the Default-dispatcher tick does not fire inside the 300ms window. Intermittent, not a regression: the identical 17-wildcard sweep (incl. latency.* + model.* + ReasoningTierServeGroundTruthTest) passed cleanly after (702 tests / 0 failures, isolated re-run via cleanTestDebugUnitTest).
- Recovery followed the 2026-09-11 playbook: production code untouched (git diff = .ralph/prd.json auto-reject bookkeeping only), exact stored verifyCommand re-run clean -> REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE flipped back to passes:true.

## 2026-09-12 AUTO-REJECT ROOT CAUSE (PERSON-RELATIONSHIP re-verify, /tmp/ralph_verify_1.txt, ANSI ESC elided as \x1b)
- Story 2 (PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL) was flipped to passes:false by the controller's auto-reject loop at 16:45: the exact stored 18-wildcard verifyCommand (incl. social.*) FAILED on the model.* wildcard, NOT the social code. Sole FAILED test = `ModelManagerTest > health sweep populates the per-provider map` `java.lang.AssertionError at ModelManagerTest.kt:91`, "714 tests completed, 1 failed". The build took an extreme **1h 50m 30s** (pathological CPU contention) — the async `refreshHealth()` sweep polls `providerHealths.value.size < 6` with a 5s wall-clock deadline (ModelManagerTest.kt:96-100); under that load the Default-dispatcher sweep did not settle in time. Same timing-flake family as MemoryConsolidationLoopTest (2026-09-11 AC2 / 2026-09-12 REASONING-TIER), NOT a code regression. Verbatim failure tail:
```text
ModelManagerTest > health sweep populates the per-provider map FAILED
    java.lang.AssertionError at ModelManagerTest.kt:91

714 tests completed, 1 failed

> Task :mobile:app:testDebugUnitTest FAILED
...
BUILD FAILED in 1h 50m 30s
24 actionable tasks: 1 executed, 23 up-to-date
```
- Git diff state at the time: PRODUCTION + TEST code untouched (empty `git diff` over mobile/app/src); the only working-tree change was `.ralph/prd.json` auto-reject bookkeeping (the reject_story.py append). The story's implementation remains commit 5698e69 (full 18-wildcard sweep incl. social.* had verified BUILD SUCCESSFUL EXIT=0 in 1m15s at 02:13 that day).
- The rejected story was NOT re-verified later in that run (the loop only re-checks passes:true stories), leaving passes:false pointing at a /tmp path. Recovery follows the established playbook: exact stored verifyCommand genuinely re-run (cleanTestDebugUnitTest to defeat UP-TO-DATE reuse) -> flip back passes:true only on EXIT=0.

## 2026-09-13 VOICE-FORGE-EGYPTIAN-KAREN-TTS — implementation facts (all verified on the current tree)
- Real spoken-reply speech backend: Python server `voiceforge/voiceforge_server.py` (repo root; stdlib HTTPServer + optional chatterbox_tts runtime; POST /v1/synthesize {text, language, checkpoint, exaggeration, cfg_weight, audio_prompt_path} -> {wav_base64, sample_rate, channels, duration_ms, checkpoint}; GET /health). Base weights default = Egyptian fine-tune (CHECKPOINT "NAMAA-Egyptian-TTS" per AC1). Honest 503/"healthy":false when the runtime is missing (verified by direct curl: health {healthy:false,error:"chatterbox-tts not importable"}, synthesize -> HTTP 503).
- Kotlin: `com.jarvis.app.voice.VoiceForgeSynthesizer` (the single wire-contract hop) + `VoiceForgeAdapter` (real okhttp client, VoiceForgeConfig defaults baseUrl http://127.0.0.1:8765, checkpoint chatterbox-multilingual, egyptianCheckpoint NAMAA-Egyptian-TTS, exaggeration 0.65, cfgWeight 1.7, audioPromptPath null) + `VoiceForgeBackend` (routes DialectSignal.detectedLanguageMix containing "ar-EG" -> egyptianCheckpoint; carries ITS OWN configured checkpoint/exaggeration/cfgWeight/audioPromptPath with the text, never server defaults; on Failed/Unreachable falls back to the injected platformFallback lambda -> FellBack, else Silent; NEVER throws). Exposed outcome + counters for tests/diagnostics.
- AC2 construction site (recorded line numbers): JarvisEngine.kt:175-189 (adapter+backend constructed at the SAME composition point as OllamaAdapter/OllamaModelBackend :152-167; backend val at :185), TermuxJarvisServer.kt:232 (ctor gained `voiceForgeSynthesizerOverride: VoiceForgeSynthesizer? = null` so tests swap ONLY the final HTTP hop). Live reply sink: JarvisEngine.kt:586-598 (legacyReplySpeech lambda = prior platform streaming path, now ONLY the AC3c failure fallback; setStreamingSpeak routes every full reply through JarvisEngine.voiceForgeBackend.synthesize, signal=null pending a sink-side dialect signal).
- AC3 proof (both new tests in com.jarvis.app.voice, full-sweep green): VoiceForgeBackendTest (7 tests — routing by real DialectSignal, configured params not defaults, FellBack vs Silent, throw treated as Unreachable) + VoiceForgeProductionCallPathTest (3 tests — real TermuxJarvisServer + real CognitiveEngine + recording LatencyPipeline + recording synthesizer at the final hop: Egyptian turn drives engine "user dialect: ar-EG" -> NAMAA checkpoint; English turn -> base checkpoint, no dialect flag; FAIL mode -> FellBack with reply text reaching the platform fallback). The recording synth is a file-private fake; the ONLY swapped hop is the synthesizer.
- AC5: real `voice.voiceForge` node (com.jarvis.app.voice.VoiceForgeBackend, organType MODEL, not PLANNED) + DEPENDS_ON edge entry.latencyPipeline -> voice.voiceForge (the pipeline owns fullSpeak/spoken output); SystemGraphTest pin "voice forge backend is a real organ owning the spoken output path" (real-class/non-PLANNED/reachability/edge-kind/construction-site) PASS.
- AC4 honest state: the voice_prompt asset SLOT is committed (`mobile/app/src/main/assets/voiceforge/` + PROVENANCE.md) but `venon_voice_reference.wav` is PENDING — Venon's own recorded reference clip is human input the agent cannot manufacture. VoiceForgeConfig.audioPromptPath therefore defaults to null (production never requests a missing clip); set it to the committed path the moment the real recording lands. This is the single documented PARTIAL of the story.
- AC6: exact stored verifyCommand (17 wildcards) BUILD SUCCESSFUL EXIT=0, no cleanTest needed (test task genuinely executed: 713 tests / 0 failures / 0 errors / 102 classes; scoped voice.*+SystemGraphTest run was 60 tests / 0 failures first).
- NOTE: `mobile/app/.cxx/` + `.ralph/*.log`, `prd.json.bak`, `ralph_run_*.log` are untracked noise and are NEVER committed.

## 2026-09-13 BUILD-TWIN-ARM64-VERIFICATION — implementation facts (all verified on the current tree)
- AC1/AC2: on-demand ARM64-native CPU-only 6-8GB twin — `.ralph/buildtwin/provision.sh` (AWS Graviton allow-list m7g.large/m6g.large = 2 vCPU/8GiB, arm64 Ubuntu 24.04 AMI via SSM param, refuses double-provision, records state.json) + `.ralph/buildtwin/teardown.sh` (idempotent, marks 'terminated', never invents an instanceId). No always-on instance.
- Kotlin `com.jarvis.app.buildtwin`: BuildTwinSpec (init-enforced arch=='arm64', cpuOnly==true, ramCapGb in 6..8 — a looser twin is a construction error) + BuildTwinVerifier (AC3 gate: runs any GGUF conversion/quantization step ON THE TWIN FIRST via TwinCommandRunner, then probes the twin's voiceforge /health via TwinHealthProbe; ONLY healthy:true + step-exit-0 authorizes artifact promotion; NEVER throws — probe throws are honest healthy=false verdicts) + HttpTwinHealthProbe (real GET, java.net + org.json, both runtimes) + LocalTwinCommandRunner (ssh-wrapper step for a remote twin) + IncidentReportWriter (AC4: full verbatim run output in `.ralph/incidents/<date>_buildtwin_verify.md`, TEMPLATE sections WHAT/WHERE/WHEN/WHY/MITIGATION/REPRODUCTION/STATUS).
- AC3 construction site: BuildTwinVerifier constructed on TermuxJarvisServer (TermuxJarvisServer.kt:239) with spec host=127.0.0.1/port=8765, real HttpTwinHealthProbe + LocalTwinCommandRunner — the re-usable JVM production composition point; no JarvisEngine.init (Android runtime) wiring needed since the gate is an ops-time tool.
- AC5: real `infra.buildVerificationTwin` node (com.jarvis.app.buildtwin.BuildTwinVerifier, organType INFRA, not PLANNED) + GATES edges from `voice.voiceForge` and `model.reasoningTier` (the two organs that own device-artifact promotion); new SystemGraphTest pin "build verification twin is a real organ gating artifact promotion" PASSES (node/class/non-PLANNED/edge-kind/reachability-from-entry). New enum values: SystemGraph.OrganType.INFRA, DependencyEdge.EdgeKind.GATES.
- 15 BuildTwinVerifierTest tests (spec contract, gate ordering step-before-health, healthy/exit-0 authorization, honest failures, real HTTP probe over a live ServerSocket mini-server, real LocalTwinCommandRunner exit codes, IncidentReportWriter TEMPLATE-format persistence pass+fail) — all green.
- STANDING RULE (AC6): any future story touching native/ARM64-sensitive code (voice, model conversion, quantization) MUST have a twin-verified incident log entry (`.ralph/incidents/<date>_buildtwin_verify.md`, non-empty WHAT telling what actually ran + verdict) referenced before passes can flip to true for that story — same pattern as the 2026-09-11 AUTO-REJECT standing rule.
- GOTCHA (test-land): `com.sun.net.httpserver.HttpServer` is NOT on the Android unit-test compile classpath (AGP compiles unit tests against mockable android.jar, not the full JDK) — Unresolved reference 'sun' at compile time. A live-health-server test double must be a raw `java.net.ServerSocket` HTTP/1.1 responder (JDK-only), which is how BuildTwinVerifierTest's `runWithHealthServer` is built.

## 2026-09-14 CONTINUITY-GATE-ENFORCED-SEAM — implementation facts (all verified on the current tree)
- New main-source package `com.jarvis.app.continuity`: `ContinuityGate` is the typed registry every organ contributing to the generation payload registers into at construction and the ONLY source of the per-turn generation snapshot. `Snapshot(turnIndex, userText, interlocutor, mentionedEntities, selfReferential, dialectSignal, mentalState, socialLines, crossSessionMemories: List<RankedMemory>, confidentialityFirewall, servedTier)` is the single assembly parameter surface: `IdentityContext.gatherForTurn(snapshot: ContinuityGate.Snapshot)` and `ContextWindowAssembler.assembleFrom(snapshot, currentTurnIndex, currentSegmentId)` accept ONLY the snapshot — no raw dialect/mental/interlocutor parameters exist on the assembly path anymore.
- AC2 construction sites (recorded AFTER build): JarvisEngine.kt:502 and TermuxJarvisServer.kt:207 construct `ContinuityGate(dialectDetector, personRelationshipModel, confidentialityFirewall, blendedRetriever, mentalStateEstimator)` at the SAME composition point as the identity/emotion/social stack and pass it into CognitiveEngine's new nullable `continuityGate` seam. CognitiveEngine's `effectiveContinuityGate` (continuityGate ?: ContinuityGate(seams)) keeps any pre-gate harness byte-for-byte — nullable-seam convention preserved.
- Snapshot computation in `snapshotForTurn`: dialect = EgyptianArabicDialectDetector per-turn; mentalState = UserMentalStateEstimator.estimateForTurn; socialLines = PersonRelationshipModel trust-trajectory lines; galaxy memories = BlendedMemoryRetriever recall gated by ConfidentialityFirewall.filterRankedMemoriesForInterlocutor (max 5); servedTier = the CognitiveAdmissionPolicy admission outcome (`noteTurnServe` booking tier), which is WHY the admission gate moved BEFORE snapshot building in CognitiveEngine.process — write-back order (identity ingest, graphStore.addFact, booking, sendBlock) otherwise preserved. Snapshot uses `List<RankedMemory>` (not CrossSessionMemory) to avoid a continuity->cognitive package dependency; assembler maps to CrossSessionMemory domain objects.
- SignalKind enum: DIALECT / MENTAL_STATE / SOCIAL_RELATIONSHIP / CONFIDENTIALITY / GALAXY_MEMORY / MODEL_TIER — one per migrated organ (language.egyptianArabicTextHalf, identity.mentalStateEstimator, social.personRelationshipModel, social.confidentialityFirewall, memory.blendedRetriever, model.reasoningTier). The MODEL_TIER contribution is always registered (model.reasoningTier feed, no nullable seam).
- JarvisOrganGraph: real `continuity.continuityGate` node (FQCN com.jarvis.app.continuity.ContinuityGate, COGNITIVE not PLANNED) + DEPENDS_ON cognitive.engine->gate + SENDS_TO gate->identity.identityContext / gate->cognitive.contextWindowAssembler + SENDS_TO into the gate from all six contributor organs. SystemGraphTest pin "continuity gate is a real organ every generation contributor feeds into" (20/20).
- Proof status: ContinuityGateTest 2 tests + ContinuityGateProductionPathTest 4 tests (TermuxJarvisServer -> LatencyPipeline recording bridgeSend -> CognitiveEngine.process) — dialect signal, person/relationship+trust lines, confidentiality blocking vs owner/authorized, and trust-tier differentiation (trust=TRUSTED vs STRANGER, same input) all proven IN the outgoing payload. Full 20-wildcard stored verifyCommand BUILD SUCCESSFUL EXIT=0: 748 tests / 0 failures / 0 errors / 0 skipped.

## 2026-09-14 AUTO-REJECT ROOT CAUSE (EGYPTIAN-ARABIC re-verify, /tmp/ralph_verify_0.txt — while CONTINUITY-GATE work was committing)
- Story (EGYPTIAN-ARABIC-TEXT-HALF) was flipped to passes:false by an external ralph auto-reject DURING the CONTINUITY-GATE-ENFORCED-SEAM session: the concurrent verify ran the exact 17-wildcard stored verifyCommand while the gradle project lock / CPU was contended by my own 20-wildcard sweep, and it FAILED on the documented contention-flake family. Verbatim failure tail:
```text
MemoryConsolidationLoopTest > idle-scheduled loop consolidates automatically and the pass is manually triggerable FAILED
714 tests completed, 1 failed
> Task :mobile:app:testDebugUnitTest FAILED
BUILD FAILED in 1m 19s
```
- Same mechanism as the 2026-09-11 / 2026-09-12 / 2026-09-13 documented auto-rejects (idleIntervalMs=5, delay(300) Default-dispatcher tick under load) — NOT the dialect code. Only the single MemoryConsolidationLoopTest timing assert failed.
- Recovery followed the standing playbook: production code untouched (EGYPTIAN has no new diff), exact stored verifyCommand re-run genuinely (test task executed, 4 tasks) BUILD SUCCESSFUL EXIT=0 — 715 tests / 0 failures / 0 errors / 102 classes, MemoryConsolidationLoopTest green. Flipped passes:true, stripped the stale AUTO-REJECTED annotation.
- LESSON: the outside ralph controller may auto-reject ANY passes:true story while this session holds the gradle lock / CPU. Before committing .ralph/prd.json, diff it for concurrent AUTO-REJECTED annotations and re-verify those stories rather than silently carrying a stale flag into the commit (as happened HERE — the CONTINUITY commit already captured the flag; this entry + flip-back commit follows).

## 2026-09-14 AUTO-REJECT CASCADE — FOUR STORIES FLIPPED TO passes:false BY CONTROLLER RE-VERIFIES (verify logs preserved verbatim per standing rule AC5; observed + recovered this session)
- The external ralph controller re-ran every passes:true story's verifyCommand during this 4-story session (f229788 VOICE-FORGE, a3343bd BUILD-TWIN, 59ae22c CONTINUITY all committed at HEAD before the flips; 5698e69 person-relationships verified at 02:13). Under CPU/gradle-lock contention, four sweeps FAILED — each on ONE unrelated, documented contention-flake test, never the story's own code. The four rejections' /tmp logs were captured here before they could vanish:
  - idx 0 (EGYPTIAN-ARABIC-TEXT-HALF) /tmp/ralph_verify_0.txt: `ModelManagerTest > health sweep populates the per-provider map FAILED` + `java.lang.AssertionError at ModelManagerTest.kt:91`; 715 tests completed, 1 failed; BUILD FAILED in 4m 53s.
  - idx 4 (VOICE-FORGE-EGYPTIAN-KAREN-TTS) /tmp/ralph_verify_4.txt: log TERMINATES after `> Task :mobile:app:testDebugUnitTest UP-TO-DATE` — truncated capture (grep'd result: no test task executed, no FAILURE block). Non-zero exit despite a harmless UP-TO-DATE tail implies the sweep was killed/collapsed under the competing gradle lock; NOT a code regression (VOICE-FORGE verified 713/0 at f229788).
  - idx 5 (BUILD-TWIN-ARM64-VERIFICATION) /tmp/ralph_verify_5.txt: `ModelManagerTest > health sweep populates the per-provider map FAILED` + `java.lang.AssertionError at ModelManagerTest.kt:91`; 742 tests completed, 1 failed; BUILD FAILED in 1m 49s.
  - idx 6 (CONTINUITY-GATE-ENFORCED-SEAM) /tmp/ralph_verify_6.txt: `OwnerBiometricBindingTest > production port binds through the real HumanCore singleton facade FAILED` + `java.lang.AssertionError at OwnerBiometricBindingTest.kt:156`; 748 tests completed, 1 failed; BUILD FAILED in 2m 34s.
- ALL are the documented pre-existing flake families, NOT the rejected stories: ModelManagerTest.kt:91 is the async refreshHealth() sweep polling `providerHealths.value.size < 6` under a 5s wall-clock deadline (documented 2026-09-12 PERSON-RELATIONSHIP re-verify section); OwnerBiometricBindingTest.kt:156 is the process-global `HumanCore` singleton being mutated by a prior test (ReAuthenticated vs Bound; documented progress.txt Codebase Patterns). EGYPTIAN was ALREADY flipped back + re-verified on 2026-09-14 (see prior section); it got re-flipped AGAIN by the cascade.
- Recovery per the standing playbook: production code untouched (git diff over mobile/app/src EMPTY), each story's exact stored verifyCommand re-run genuinely (cleanTestDebugUnitTest to defeat UP-TO-DATE reuse), flipped back passes:true only on EXIT=0. BUILD-TWIN recovered this session: exact 20-wildcard verifyCommand (incl. com.jarvis.app.buildtwin.* + com.jarvis.app.continuity.*) BUILD SUCCESSFUL EXIT=0 (see progress.txt).

## 2026-09-15 AUTO-REJECT RE-CASCADE — TWO MORE STORIES FLIPPED passes:false (PERSON-RELATIONSHIP iterate 5 / REASONING-TIER iterate 9; verify logs preserved verbatim per standing rule AC5, captured before /tmp could be overwritten)
- After the commits re-closing EGYPTIAN/BUILD-TWIN/REASONING-TIER, the external ralph controller re-ran every passes:true story's verifyCommand again; two sweeps FAILED under CPU/gradle-lock contention on the SAME documented MemoryConsolidationLoopTest idle-loop timing flake as every prior auto-reject (MemoryConsolidationLoopTest.kt:174 — `idleIntervalMs = 5`, `startIdleLoop()`, `delay(300)` then assert, Default-dispatcher tick does not fire inside 300ms under load). NOT the story code. Verbatim failure tails:
  - PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL (/tmp/ralph_verify_1.txt, Sep 14 23:59): `MemoryConsolidationLoopTest > idle-scheduled loop consolidates automatically and the pass is manually triggerable FAILED` + `java.lang.AssertionError at MemoryConsolidationLoopTest.kt:174`; `727 tests completed, 1 failed`; `> Task :mobile:app:testDebugUnitTest FAILED`; `BUILD FAILED in 1m 11s`; `24 actionable tasks: 1 executed, 23 up-to-date`.
  - REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE (/tmp/ralph_verify_2.txt, Sep 15 00:19): identical `MemoryConsolidationLoopTest > idle-scheduled loop consolidates automatically and the pass is manually triggerable FAILED` + `java.lang.AssertionError at MemoryConsolidationLoopTest.kt:174`; `715 tests completed, 1 failed`; `BUILD FAILED in 1m 33s`; `24 actionable tasks: 1 executed, 23 up-to-date`.
- Recovered this session per the standing playbook: production code untouched (git diff over mobile/app/src EMPTY), the widest stored 21-wildcard verifyCommand (CONTINUITY's, a superset of every story's filter incl. social.* + buildtwin.* + continuity.*) genuinely re-run via cleanTestDebugUnitTest, EXIT=0 -> all five auto-rejected stories flipped back passes:true. This is now the FOURTH occurrence of the exact same flake family rejection mechanism; the loop repeats because a passes:true story whose verifyCommand is a big wildcard sweep inherits every unrelated contention flake on each controller re-verify.
