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

## STAGE-1 FRIDAY (2026-09-19) — CI INFRASTRUCTURE, overrides the stale JARVIS facts
- The prd.json is a NEW Stage-1 "Friday" PRD: story CI-BUILD-DEBUG-APK (closed) + CI-VERIFY-BRIDGE (pending, do this next). Git history is a fresh re-base (f3b4f10 "Friday: clean start", 26a5d93 "Stage 1 PRD"). The old JARVIS organ/wiring facts above describe the same mobile/app codebase but are NOT the active stage's concern; ignore them for CI-story work.
- Stage-1 stories are pure CI infrastructure; their stored verifyCommands are STATIC file/grep/bash checks — NO gradle/testDebugUnitTest run on-device. Termux-builds-banned rule is irrelevant here.
- JDK for CI = 17. Source of truth: mobile/app/build.gradle.kts (compileOptions source/targetCompatibility = VERSION_17 ~L77-78, kotlin compilerOptions jvmTarget = JVM_17 ~L111); root build.gradle.kts pins AGP 8.10.0 (requires JDK 17). Also: compileSdk 36, ndkVersion 27.3.13750724, cmake 3.22.1 (externalNativeBuild), Gradle wrapper 8.11.1.
- Device-specific settings that must be neutralized for a clean Linux runner (CI step does it, no Kotlin source edited): (1) gradle.properties:10 `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2` -> CI sed-deletes the line; (2) settings.gradle.kts maven repo was absolute `/storage/emulated/0/jarvis-repo/local-repo` -> now repo-relative `uri("local-repo")`. local-repo/ holds exactly one committed artifact (com/k2fsa/sherpa/onnx/sherpa-onnx-android/1.13.2/...aar) and is INERT (no dep resolves from com.k2fsa in mobile/app/build.gradle.kts).
- GitHub Actions workflow: .github/workflows/build.yml (name "build", push branches [main] + workflow_dispatch, ubuntu-latest, ANDROID_HOME=${{ runner.temp }}/android-sdk with explicit sdkmanager installs, actions/checkout@v4 + actions/setup-java@v4 (temurin 17, cache: gradle) + actions/upload-artifact@v4; testDebugUnitTest step continue-on-error:true; JUnit XML uploaded with if: always(); python summary step writes tests/failures/errors/skipped to GITHUB_STEP_SUMMARY; timeout 120min).
- prd.json `notes` is the LAST field of each story object: no trailing comma after its closing quote; missing closing quote -> "Invalid control character", trailing comma -> "Illegal trailing comma before end of object". Validate with python3 -c "import json; json.load(open('.ralph/prd.json'))" after every edit.
## STAGE-1 FRIDAY (2026-09-19) — ci_verify.sh (CI-VERIFY-BRIDGE, closed)
- `.ralph/ci_verify.sh [--dry-run] <filter>...` is the per-commit CI verification bridge: looks up the GitHub Actions run for HEAD (owner/repo parsed from `git config --get remote.origin.url` = mhw77655-hue/Friday, GITHUB_REPOSITORY fallback) at https://api.github.com using curl + jq; token ONLY from $GITHUB_TOKEN (never written, mktemp -d + EXIT trap); `git push origin HEAD:main` only when no run exists for HEAD, then polls to completion (25-min cap, per-poll progress lines); downloads the run's 'junit-results' artifact; python3 verdict exits 0 only if every testcase matching the --tests-style glob filter(s) (classname + classname.method) passed and >=1 matched; skips non-failing; no-match/failure -> exit 1 with failing FQCNs + failure-message lines; cancelled/tokenless/missing jq|curl|python3|unzip -> plain fail-fast. --dry-run does zero network and exits 0 with token unset. bash -n clean.
- VERIFYCOMMAND GREP-SELF-REFERENCE GOTCHA (bit twice, now a standing rule): a stored verifyCommand must never `grep -r` a directory that CONTAINS ITS OWN verifyCommand text (.ralph/ includes prd.json, and ralph_run_*.log transcripts echo it) — the scan always self-matches and the story auto-rejects forever. The token scan for CI-VERIFY-BRIDGE is scoped to the four stable tooling files only: `.ralph/ci_verify.sh .ralph/ralph.sh .ralph/reject_story.py .ralph/prompt.md`. Keep the GitHub token-prefix substrings out of REPO_FACTS.md too, because prompt.md is built from REPO_FACTS + transcripts and is itself one of the scanned files.
- prd.json `notes` is the LAST field of each story object and its value NEEDS its closing `"` and NO trailing comma. Missing closing quote -> 'Invalid control character'; trailing comma -> 'Illegal trailing comma before end of object'. Drops happened twice on 2026-09-19. Always `python3 -c "import json; json.load(open('.ralph/prd.json'))"` after editing, before commit.

## STAGE-3 FRIDAY (2026-09-22) — TURN-TRACE CLOSED (Gate 3a), first Kotlin story of the new era
- `com.jarvis.app.trace.TurnTrace` (object) + `TurnTraceStore` interface + `JsonlTurnTraceStore` (append-only JSON-Lines local file): one record per real turn through `CognitiveEngine.process` (raw input, working-memory snapshot ids, predictions slot (empty until a predictor exists), prompt section boundaries with offsets, synchronously-available output text, assembled generationPayload, per-stage latency ms: embed/retrieve/prompt-build/generate/post-process). Production entry-point proof path: `TermuxJarvisServer(port=0, backendOverride=FakeModelBackend(), turnTraceStore=JsonlTurnTraceStore(tmpfile))` → recording `LatencyPipeline` (bridgeSend collector, the ONLY swapped hop) → `CognitiveEngine.process`. The engine seam is `CognitiveEngine.turnTraceStore: TurnTraceStore?` (default null, appended after dialectDetector); JarvisEngine.init writes to `filesDir/traces/turn_traces.jsonl`; TermuxJarvisServer takes an optional `turnTraceStore` constructor param (default null). SystemGraph has a REAL `trace.turnTraceStore` organ (OrganType.TRACE — new enum member) with a `cognitive.engine`→`trace.turnTraceStore` SENDS_TO edge. `replay_turn(id, runner)` re-runs a stored turn's input against the current engine; `storedInput(id)` reads it back.
- AC5 double-guard: BOTH the engine seam (`if (!store.enabled) return`) AND `JsonlTurnTraceStore.append` (re-check `_enabled` under `synchronized(this)`) gate the write, so the disabled-store negative control is provable at the exact byte level over a real pipeline turn.
- Kotlin story verifyCommand pattern for this CI-bound repo: `! grep -rnE '...' <package-dir>/` static clause + `bash .ralph/ci_verify.sh '<NewTestClass>.*' '<SecondClass>.*'` — the harness re-runs it after passes:true, which pushes HEAD:main and polls the real GitHub Actions junit-results artifact (filter-mode verdict ignores unrelated baseline failures). Fixtures driven through the real engine must avoid the `it`/`that`/`this` REFERENT-unknown substrings and VAGUE_ACTION verbs (low-doubt turns); all three live-test fixtures do.
