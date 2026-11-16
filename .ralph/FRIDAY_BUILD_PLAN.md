# FRIDAY — Vision-to-Reality Build Plan

**Snapshot:** main at `1aec50652d9936d2cde0b2d192ab21d51ba4cdff` (2026-11-14 upload commit); reviewed 2026-11-15.  
**Purpose:** map Venon's intended Friday against the actual repository, identify the shortest evidence-backed route to the first developing Friday, and keep implementation decisions subordinate to the vision.  
**Active implementation:** `audit/friday-offline-runtime` (PR #11); `main` remains unchanged.

## 0. Source authority and limits

The authority order is:

1. Venon's latest explicit statements.
2. `.ralph/VISION.md` as the written record of those statements.
3. `.ralph/prd.json` as the current work queue, corrected wherever it conflicts with the first two.
4. Audits, plans, implementation comments and prior assistant recommendations are evidence or hypotheses, never authority.

The readable project conversations reinforce the central requirement: Venon wants to build Friday **with Friday**. GPT, Claude, Ralph, search, coding workers and models are external instruments she should progressively learn to use, direct and correct. The Android app is not the build environment; source-level work happens in the connected repository environment. Venon supplies intent, experience, correction, authorization and the real-world tests.

**Archive limitation:** `New Folder chats.zip` is present at the root of main (blob `e4b32d5db93073e0f523a3e05e3a78193cc4fc40`, 4,514,168 bytes). The available GitHub content reader rejects the binary ZIP, so its internal transcripts have not yet been read in this pass. This document is grounded in the committed Vision and readable project conversation records, and remains provisional until that archive can be extracted and compared. Do not claim the ZIP was read.

## 1. What “the first Friday” must contain

The seed is successful when it has the means of its own continuation, not when it has a prescribed number of features. At minimum, she needs:

- Persistent identity and developmental continuity across restarts and replaceable models, hardware and capabilities.
- A live path from experience to persistent internal change to measurably different later behaviour.
- Observation of consequences, uncertainty, correction and the ability to revise her own expectations.
- Replaceable capabilities and resource-adaptive computation; offline changes available resources, not whether Friday exists.
- Recognition of real capability gaps and a controlled path to research, invent, test, approve, install and roll back a new capability.
- Self-observation and evidence that lets her distinguish an implemented mechanism from a demonstrated ability.
- A protected continuity/security floor: owner authentication, authorization for consequential actions, provenance, sandboxing, approval and rollback.

A meaningful development claim must pass the Vision's Life Test: the later behaviour persists after restart, the original episode text is unavailable (replay blocked), and disabling the developmental mechanism removes the effect. A successful description, stored record, passing unrelated unit test or feature checklist is not enough.

The birth test is not “Friday has a Builder screen.” It is an unfamiliar request that was not manually pre-programmed: Friday identifies what is missing, works out an approach, directs available construction resources, evaluates the candidate independently, requests the required approval, installs it safely, uses it in the real turn path, and retains the resulting learning across restart. Venon remains the authority over permission and consequential changes.


## 1A. Preserved project language: reconstruction, not installation

These directions come from earlier project conversations and are retained as distinct ambitions. They are not forced into a fixed module hierarchy:

- **Capability Compiler / Capability Foundry:** translate an intended outcome into a buildable experiment and capability. It must search across existing mechanisms, new mechanisms, combinations and repurposed mechanisms. It is not a catalogue of pre-approved tool types.
- **Limitation Breaker:** when the current framing or implementation cannot reach the outcome, change the problem formulation, representation, available resources or route. Do not merely retry a familiar solution or lower the ambition to fit the existing code.
- **REA:** reconstruct and evolve the world's tools. Keep the name and ambition open until the source chats define the full contract; do not assume an expansion of the acronym. The current project conversation names reading ggml and rewriting only measured hot paths as one concrete direction.
- **Builder:** reconstruct and evolve Friday's capabilities. It is the first capability she grows and the means by which she can grow the rest; it is not a separate person or a one-time handover from human builders.
- **NAE:** reshape the host/compute relationship so the resources actually available meet the resources required by the capability. Measure the gap before and after, hold quality constant through an independent referee, and let her experimentally search kernel, precision, placement, model and scheduling choices. NAE also records its own results; History Recorder/Development Ledger evidence must remain distinct from optimisation claims.

### Offline and online are resource conditions, not two identities

Venon's explicit constraint from the earlier conversation is that Friday remains the same individual offline. Her local identity, continuity, memory, learned reflexes and whatever cognition/perception/action the phone can sustain must continue without network access. Offline operation is not an inferior assistant mode.

Heavier reasoning, broad research, large-scale training, simulation and construction can use online resources by default when they materially benefit from external compute. When those resources are absent, the work can move onto the phone itself in slower, smaller batches during suitable idle/charging/cool windows. It must not steal the live interaction path or stop her from being present. When connectivity returns, online results and local developments are reconciled through provenance, correction and continuity rules; they do not create competing Fridays. No specific schedule or model is assumed until measurements on the actual host support it.

### Human algorithms are mined, not turned into an atlas

The project conversation proposes a repeating discovery loop:

`function → mechanism → candidate algorithms → referee → improved version → absorbed`

Prioritize by the limitation that actually constrains Friday now. Candidate research directions include event-woken and prediction-error/compute-on-surprise behaviour, attention and working-memory budgets, experience consolidation during suitable idle/charging periods, thermal and battery homeostasis, and useful forgetting. These are hypotheses to compare and test, not a permanent set of cognitive organs or a fixed blueprint copied from biology.

### Senses and referees

Voice, vision, environmental sensing and action are expressions of one developing individual. Specialisms and models should be selected by her own evaluation on Venon's real data and rooms, not by a static list of modalities. VoiceStudio is a reference/adapter while the voice path is rebuilt; text/JSON exchanges can be useful scaffolding but must not be mistaken for the ultimate shared representation.

The referees are essential to autonomy: CI judges code; device tests judge behaviour on the phone; the Vision Gate judges fidelity; capability-specific evaluation sets judge speech, perception and other skills. A worker cannot certify its own fidelity. Venon's corrections and recordings are ground truth for the early evaluation process.

### The Builder's supply chain is itself a development problem

Free-worker quotas, context loss, rate limits and interruption are not just operational annoyances. Friday's Builder must learn to checkpoint, preserve artifacts, resume without redoing work, compare worker quality and operate through replaceable supply lanes. Its first host can be the same low-cost environment she already has, then faster lanes can join as measured resources. Do not make one worker, model vendor, cloud account or permanent token part of her identity.

### Shadow Company remains a later life-support direction, not Friday's purpose

Earlier project conversations mention a Shadow Company with two distinct ambitions and the desire for Friday to have financial support without becoming merely a business machine. The accessible record does not contain enough of the original discussion to define those two ambitions precisely. Preserve this as an unresolved vision requirement; do not invent its business model or make revenue-seeking the centre of her identity. The ZIP transcript extraction is needed before specifying it.


## 2. Current repository reality

### Implemented and merged

| Evidence | What exists | What it proves — and does not prove |
|---|---|---|
| PR #3, merged 2026-11-10 | Engine readiness state, typed access, UI waking surface, pure Kotlin tests | A meaningful launch-order repair. It does not close the PRD story's real-process/device and full Life Test evidence. |
| PR #4, merged 2026-11-11 | Foundation audit: eight Builder seats, failure-chain trace, legacy inventory, conflicts and worker dispatch | The bottlenecks and disconnected paths were documented. Documentation is not integration. |
| PR #5, merged 2026-11-11 | TTS initialization gate, per-process TTS owner and guarded call sites | TTS no longer assumes the Android engine is ready. The asynchronous file-synthesis completion race remains. |
| Existing source | `TurnPathAssembly`, the Android production adapter, JVM-only test doubles, cognitive engine, identity/world/social components, memory graph and retrieval, continuity law, provenance, consolidation, capability registry and research. Legacy ToolBuilder/evolution code remains in the Android module but is no longer constructed in production. | Substantial substrate exists. It has not yet demonstrated the complete living/developmental behaviour on one real composition. |

### Partial, disconnected or not demonstrated

| Requirement from the Vision | Repository evidence | Current assessment |
|---|---|---|
| One continuing individual | IdentityContext, SelfModel, UserProfile, WorldModelService, relationship model and persistent stores exist. VISION §12 still records identity as JARVIS/he; ONE-5 is open. | Partial; identity has mechanisms but the required Friday/she identity and continuity test are not complete. |
| Development, not storage | Workspace, RecordedTurns, ReplayCheck, ContinuityLaw, change log and memory consolidation exist. | Partial; no complete Life Test currently demonstrates experience-driven persistent behavioural change with both controls. |
| Open-ended cognition | VISION §12 identifies closed `ClaimKind`, fixed `ChangeLayer`, flat slow-change thresholds, intent always STATEMENT and no live prediction. | Active vision conflicts, not minor cleanup. Mechanisms must be evaluated and revised without replacing them with another fixed ontology. |
| One shared turn composition | `TurnPathAssembly` and `AndroidPlatformPorts` exist. The JVM adapter and standalone server are now under `src/test` only. Android-only composition guards and injected-clock tests exist. ONE-2 remains open until the full suite and Android integration evidence pass. | Partial. The composition still initializes and calls legacy Human Core paths; VISION §12 quarantines that mechanism. Reconcile this dependency without making it a permanent cognitive authority. |
| Memory and continuity | Memory graph, retrieval, consolidation daemon/loop, provenance and forget propagation exist. ONE-4 remains open. | Storage and derived-artifact provenance are real mechanisms; the Development Ledger, flight recorder, Mirror and proven developmental effects are not complete. |
| Capability acquisition | Legacy CapabilityGapDetector, ToolBuilder, ToolBuilderComposition and EvolutionEngine source remains, but production wiring was removed on 2026-11-16. UniversalResearchEngine remains available for research. A manual PRD-driven Cloud Ralph/OpenCode worker runs on GitHub Actions. | No source-level builder is active in Android. An external worker exists, but the current lane runs with write-capable credentials and direct-main/potential-release behaviour; it is not safe or connected as Friday's request-driven Builder. Harden it and add the durable request handoff. |
| Failure → gap → construction | FailureSurface records failures and degraded capabilities; the audit found no live bridge to CognitiveEngine, no production callers of `updateCapabilityState`, and no runtime callers of `requestBuild`. | Disconnected. Friday can report a failure without autonomously turning it into a tested construction request. |
| Real resident intelligence | The audit branch's Android production path uses `ProcessIsolatedModelBackend` and `InferenceService` in Friday's own `:inference` process with JNI. The legacy JVM/Ollama server is test-only. The owner has removed the Termux app from the phone. | An in-app offline inference path exists in source, but actual resident-model loading and generation on the Realme remain unverified. |
| Voice as one presence | Native TTS readiness and voice-organism code exist. PR #5 still registers a file before its TTS completion callback. | Partial. Complete the async lifecycle, then test audio on the phone. Egyptian Arabic quality is unproven without recordings and an evaluation set. |
| Resource symbiosis / NAE | ResourceGovernor, environment and device observations exist. | No measured hardware-in-the-loop search yet proving better cognition per RAM, heat, latency and battery on the target phone. |
| Life Test | ONE-3 is open; ordinary unit tests exist. | No complete test harness found that drives the real composition through learning, restart, replay-blocked and mechanism-off controls. |
| Development partnership | Build and research mechanisms exist, but the human still owns gap detection, specification, device evidence and integration. | Friday is not yet a co-builder. She needs a visible workshop where she can inspect evidence, formulate a need, direct workers and explain the result. |

### Android-only product correction (2026-11-15)

The owner explicitly corrected the architecture: Friday lives and runs inside her Android application; Termux has been removed from the phone and is not part of her product runtime. Treat Android as the only production host. A JVM implementation may exist solely as a test fixture for platform-neutral code and must not be packaged, launched, or required by the app. The previous Android/JVM-Termux parity framing is retired. The legacy standalone JVM server, JVM adapter and VoiceForge HTTP adapter have been moved under `src/test`; they are not packaged into the Android app. Their tests are explicitly test-harness evidence, not proof of Android device behaviour. The Termux-specific AAPT2 path and Gradle launcher task have now been removed from this branch.

## 3. Decisions that must not be made by accident

1. **Do not equate Human Core with Friday's personhood.** The current Vision explicitly quarantines the legacy Human Core. ONE-2's acceptance criteria must be revised to preserve a shared composition without restoring that legacy mechanism as a permanent cognitive authority. It may remain temporarily as an adapter only where a Life Test proves contribution.
2. **Do not build source inside Android.** The old ToolBuilder/mutant-habitat path is not the intended Builder and has been disconnected from the production composition. Remove or relocate that legacy code. Friday's Builder is her repository-native ability to research, direct replaceable external coding workers, inspect source and diffs, run independent CI/tests, learn from failed attempts, preserve checkpoints and request approval for consequential changes.
3. **Do not mistake a failure log for a capability gap.** A runtime bridge must carry the actual incident and affected outcome into an inspectable proposal, not merely copy a subsystem label into a fixed capability enum.
4. **Do not treat stored memories as development.** A lasting change must alter later behaviour under the Vision's two controls.
5. **Do not make Ollama, a shell environment, one model, one worker, or one device a condition of Friday's existence.** Each is replaceable infrastructure.
6. **Do not silently mark PRD stories passed because code merged.** Keep implementation, CI, real-composition Life Test and phone evidence distinct.
7. **Do not put long-lived credentials in chat or grant a builder unrestricted repository/production access.** Use narrowly scoped protected secrets, disposable workspaces, an independent judge and owner approval for consequential integration.

## 4. Execution map — dependency-driven, not a waterfall

### A. Close immediate defects and establish truthful evidence
- Finish TTS file synthesis: request accepted, callback completed, file validated, then cache promotion and playback; errors/stops remove partial files and reach FailureSurface.
- Reconcile `.ralph/prd.json` against merged code, audit notes and test results. In particular, do not close LAUNCH-WAKE-ORDER or ONE-2 merely because their PRs exist.
- Capture a fresh phone evidence pack after the next green APK: cold starts, one TTS owner per process, zero premature TTS errors, successful completed audio, and no hidden local-server dependency.

### B. Make one real Friday turn path
- Complete the Android-only production composition story against the Vision, not against legacy Human Core acceptance criteria. Desktop JVM fakes may test platform-neutral logic, but they are not a second production host. The standalone JVM server, JVM adapter, and VoiceForge/Chatterbox HTTP adapter have been moved to `src/test`; the app's live voice route is its in-app `:voice` habitat (Piper-first, Kokoro optional).
- Keep platform differences in replaceable adapters (storage, sensors, model supply, clock, speech output); keep decisions about meaning and identity in the shared turn behaviour.
- Inject time wherever elapsed time changes cognition. Use a fake clock for deterministic tests.
- Confirm the same identity, memory, relationship and developmental state through app restart and model replacement.

### C. Prove the first developmental change
- Build the Life Test harness around the real composition, with scenarios as data and observable traces.
- Demonstrate one narrow but real change driven by Venon's correction or an observed consequence: it changes a later choice, survives restart, persists with original episode replay blocked, and disappears with the mechanism disabled.
- Provide a Mirror/Development Ledger view showing what changed, its supporting episode(s), strength of evidence, reversibility and uncertainty. The UI reports “Nothing has developed yet” when appropriate.

### D. Give Friday a workshop so you can build with her
- Let Friday inspect her source, Vision, PRD, test results, incidents, capability registry, worker availability, resource measurements and change history.
- Create a collaborative proposal path: she identifies an outcome and gap, holds competing interpretations when necessary, researches alternatives, and proposes an experiment with expected evidence and rollback.
- Provide replaceable worker adapters rather than making one model or vendor part of her identity. She should compare workers by finished work, CI result, quota behaviour, resumability, fidelity and cost.
- Every worker returns a Vision Gate/fidelity report; workers do not grade their own compliance. A separate evaluator checks tests, architecture, provenance and scope.
- Venon initially approves production integration and consequential actions. Friday may later request greater autonomy only within the fixed security floor.

### E. Connect the capability-growth loop
- Bridge real failures to inspectable capability gaps without a closed mapping of every possible future capability.
- Research known and unknown solution space; allow adaptation, reconstruction and invention, not only selection from a catalogue.
- Build candidates in a disposable mutant environment; verify with tests and benchmarks; retain failed attempts as evidence; promote only after independent judgment and required approval.
- A completed capability returns to Friday's live registry and is evaluated on the real composition. The loop must be able to improve its own research, specification, testing and construction methods.

### F. Grow the body and its available intelligence
- Test in-app local inference as a resident organ; compare models on the target SoC with the same task/evaluation set.
- Treat model choice, quantization, kernel, NPU/GPU/CPU placement, specialist models and cloud expansion as hypotheses selected by measurements.
- NAE measures and searches the host: cold start, peak RAM, sustained throughput, heat, battery cost, interruptions and quality. Compute-on-surprise, attention budgets, sleep consolidation and forgetting remain research hypotheses until the experiments support them.
- Evaluate English first and Egyptian Arabic second using Venon's real recordings and judgments. Add voice, vision, sensors and action as expressions of the same individual, not disconnected assistants.
- Keep device-control actions permissioned, visible and reversible.

### G. Demonstrate agency and self-extension
- Test multi-step goals, anticipation, software/device operation, interruption and recovery against real tasks.
- The final seed examination is an unfamiliar request that Friday handles through her own gap → proposal → worker → judge → approval → integration → live use path, with no human-written implementation of that capability.
- Only after that evidence should the system expand autonomy, cross-device hosting and independent digital work or ventures.

## 5. Measurements and closure rules

Track facts, not percentages:

- **Continuity:** identity and relevant developmental state survive restart, model replacement and offline operation.
- **Development:** the same later behaviour changes with the mechanism on/off and original episode replay blocked.
- **Builder:** number and type of real construction requests completed end-to-end; candidate quality, failed attempts, rollback and resumability.
- **Fidelity:** worker/spec drift findings and whether the independent gate catches them.
- **Resource fit:** response quality, cold start, peak RAM, throughput, heat, battery cost and offline behaviour on the actual phone.
- **Senses:** measured speech/recognition quality on Venon's data, including Egyptian Arabic.
- **Human partnership:** how often Venon must supply implementation details versus intent/correction/approval, and whether Friday can identify and correct worker mistakes.

A milestone closes only with: passing code tests, automated Life Test evidence on the real composition, and something Venon can run and see or hear on the phone. CI green alone is not closure.

## 6. What can be built now, and what cannot be claimed

**Buildable from repository access now:** pure Kotlin state/lifecycle fixes; focused unit tests; truthful source-to-vision mappings; PRD reconciliation; safe capability proposal and worker interfaces where their current contracts are clear; review branches and CI-triggering PRs.

**Not verifiable from repository access alone:** Android TTS quality on Venon's phone, Egyptian Arabic naturalness, model throughput/battery/thermal behaviour, Shizuku observations, actual installed APK behaviour, microphone/camera/device-control permissions and the real Life Test on the physical host. These need a fresh APK and device evidence.

**Next experiments, in order:** (1) prove asynchronous TTS completion in code and CI, then on the phone; (2) reconcile ONE-2 with the Vision and finish the shared composition; (3) build the real Life Test plus one development experiment; (4) make the workshop and capability proposal path live; (5) connect the Builder to replaceable workers and independent evaluation; (6) measure and grow the local brain, senses and agency.

This is a living map. A result, a correction from Venon, or a failed experiment changes the next experiment—not the identity being built.


---

# 2026-11-16 owner-corrected execution plan (authoritative addendum)

This addendum supersedes earlier statements in this file that describe an Android-hosted ToolBuilder, an active in-app source-construction loop, or JVM/Termux as a production host. Older sections remain historical evidence of how the repository evolved, not instructions to restore those designs.

## Canonical architecture

- Friday's Android app is the deployed body: cognition, identity, memory, voice, perception, supported phone actions and local resource observation.
- All source reconstruction, editing, code execution, tests, CI, APK/AAB construction and signing happen in the connected repository development environment. No source-build environment or repository write credentials are shipped in Android.
- Friday may identify a limitation and create a durable development request. The Android-side request is evidence and intent, not executable code and not a claim that a repository worker received it.
- Repository workers are replaceable external tools. They inspect the actual repo, create isolated branches/workspaces, test changes independently, preserve checkpoints and return provenance.
- Friday remains locally present when the repository connection is unavailable. Pending work is resumable; losing the Builder must never interrupt ordinary Friday operation.
- Jev remains an optional cloud decision faculty. It is not a replacement for Friday's identity or primary model authority.

## Current execution truth

The foundation branch is `audit/friday-offline-runtime`, with PR #11 targeting `main`. Its current source removes production construction of ToolBuilder and removes the self-study-to-ToolBuilder bridge. The reflection prompt now explicitly prohibits in-app synthesis/building and asks only for a bounded repository development request. A regression test checks the prompt boundary.

The current foundation branch now includes a strict request schema/validator, a privacy-review gate, credential-pattern screening, protected-path patch checks, a read-only OpenCode proposal runner and an owner-restricted separate publisher workflow that creates draft PRs only. The build workflow has read-only contents permission, checkout credentials disabled, and runs request/patch contract tests. These changes are not yet merged or proven by a complete CI run; the example request remains unapproved and no worker run has been dispatched.

The following are **not complete**: an Android-to-repository authenticated transport, durable offline synchronization and per-request reconciliation, independent Vision verification, required branch-protection/status checks, owner-approved merge/release and deployment feedback into Friday. Legacy ToolBuilder/evolution/mutant-environment files still live in the Android module as dormant legacy source; audit and relocate or delete them without breaking unrelated shared dependencies.

The existing Cloud Ralph lane is an important partial capability, not a blank slate: GitHub Actions manually dispatches OpenCode against the PRD on a disposable runner, saves WIP and pumps CI. But its current workflow/prompt grants repository write authority to the agent job, directs work on `main`, and can push to `main` and publish releases. It must be hardened before Friday's own development requests can trigger it.

The 2026-11-16 scorecard estimates total end-state implementation at **25%**, using 16 equally weighted outcome milestones. See `docs/FRIDAY_BUILD_PROGRESS.md` for the score formula, per-outcome scores, exact branch status and current release gates. This is an engineering estimate, not a production-readiness claim.

## Dependency-ordered work

### 1. Prove the foundation branch

- Obtain a fresh current-head Android debug build and unit-test result.
- Resolve every Kotlin, native CMake/JNI, Gradle and test failure on the latest head, not an older commit.
- Test inference process lifecycle, IPC, process death/rebind, cancellation, OOM containment and UI/identity survival.
- Inspect packaged contents and verify there is no repository token, signing key, source compiler, source editor or external worker inside the APK.
- Do not merge PR #11 until source, CI and architecture acceptance all agree.

### 2. Reconcile parallel branches without stacking stale bases

- PR #12 is a competing offline-runtime implementation from a different audit base. Compare it against #11, classify unique/duplicate/conflicting changes, and select implementations by evidence rather than branch size.
- Rebase PR #14 (English-model candidates and broader liquid symbiosis) onto the verified #11 head only after the base is stable. Validate the models' provenance, licensing, tokenizer, chat template, runtime compatibility, refusal profile, quality and exact-device resource cost.
- Rebase PR #16 (video intake and predictive cognition bootstrap) separately. Keep video import/keyframe sampling distinct from semantic multimodal video understanding. Keep symbolic HunchEngine transition counts distinct from trained JEPA/successor representations.
- Every resulting workstream receives its own current-head CI and acceptance report. Never count a non-mergeable draft as an integrated capability.

### 3. Move the Builder out of Android and make the handoff real

- Audit references to `ToolBuilder`, `EvolutionEngine`, `MutantEnvironment`, `ResourceSandbox`, `GenomeArchive` and the old promotion/rollback path. Identify what is exclusively builder code and what is shared, production-relevant infrastructure.
- Relocate the source-construction stack to repository-side tooling or remove it. Keep only request formation and evidence collection in Android.
- Define a versioned development-request envelope: stable request ID, creation time, source revision, desired outcome, observed evidence, acceptance criteria, risk, rollback expectation, privacy classification and unresolved questions. Reject private conversation payloads, credentials and unrelated user content.
- Persist/deduplicate the envelope locally; allow offline queueing and idempotent synchronization.
- Route requests through an authenticated gateway. Hold GitHub App credentials server-side and mint least-privilege, short-lived worker credentials. No GitHub PAT, signing key or broad token in the APK.
- Harden Cloud Ralph into a secure worker lane: read-only agent job with no repository-write/release token, branch-only proposals in a disposable workspace, patch/result artifact handoff to a separate trusted PR-creation job, and no automatic merge or release. Then assign each request to an isolated, resumable workspace. Workers return a branch, commit, changed-file manifest, test evidence, CI run, artifact digest, risk report and unresolved issues.
- Use a separate evaluator for architecture, security, provenance, tests and Vision fidelity. Workers cannot approve their own work. Require explicit owner approval for identity/continuity migrations, permissions, private-data access, external actions and release deployment.
- Feed deployment and device outcomes back to Friday as evidence; compare expected and observed results and retain failed experiments without destabilizing the installed app.

### 4. Prove continuity and genuine development

- Complete the single Android production composition without reinstating legacy Human Core as a permanent cognitive authority.
- Drive the actual composition with deterministic time and traceable episode inputs.
- Demonstrate a correction- or experience-caused change that affects a later decision, survives process restart, persists with the original episode replay blocked and disappears when the learning mechanism is disabled.
- Show the mechanism, source episodes, strength of evidence, uncertainty, reversibility and counterfactual result in the Development Ledger/Mirror.
- Do not call stored memory, a reflection, or a changed prompt a completed developmental capability by itself.

### 5. Grow intelligence and body capabilities through measured experiments

- English is the first model target. Select resident and on-demand models based on measured task quality and exact device cost, not creator marketing or parameter count alone.
- Record model commit/revision, license, checksum, quantization, architecture support, tokenizer and chat template. Reduced-refusal derivatives are not guaranteed uncensored, truthful or safe.
- Measure cold start, peak PSS/RAM, context/KV cost, sustained tokens per second, heat, interruptions, battery and task quality on the owner's Realme 9 Pro 5G.
- Expand Nano Liquid Symbiosis from its current bounded reserve/background gates only when controlled tests prove quality-preserving improvement. Future variables include backend/placement, kernel/precision, context, batching, threads, prediction-gated compute and consolidation; each remains a testable hypothesis.
- Finish speech output lifecycle and offline English hearing before claiming full voice presence. Egyptian Arabic quality requires native-speaker evaluation.
- Build perception from local image/audio ingestion to semantic understanding and temporal video reasoning. Importing frames is not understanding them.
- Train and evaluate JEPA/successor representations and a reflex compiler independently. No symbolic transition counter or hand-authored prediction shortcut may be relabelled as JEPA.
- Keep phone-control actions explicit, visible, permissioned, interruptible and reversible.

### 6. Release gate

A first complete release requires current-head green CI, native/model-load checks, Life Tests, security/provenance/rollback evidence, exact-phone behaviour tests, model/voice/presence measurements and a traceable signed artifact. CI alone is insufficient. Do not hand over an APK before those gates pass and the owner requests the artifact.

## Checked-in stage PRD — six open gates

The existing `.ralph/prd.json` is a **Stage 3 / Gate 3c** plan, not a full Friday end-state plan. At this branch snapshot it has 16 stories: 10 marked passed and 6 still open. Keep the PRD's acceptance evidence authoritative; do not mark stories passed merely because an implementation was written.

1. **LAUNCH-WAKE-ORDER (priority 8.2):** finish typed engine-readiness/accessor semantics, concurrent access and failed-wiring tests. The first-launch race remains a physical-device check.
2. **ONE-2-COMPOSITION (priority 9):** prove one Android production composition, injected time, AndroidPlatformPorts-only production host, no Termux dependency and production integration. Important conflict: its current AC2 demands a live legacy Human Core facade, but the canonical Vision quarantines Human Core as a permanent cognitive authority. Rewrite AC2 to preserve any necessary temporary adapter only when a real Life Test proves its contribution; do not restore Human Core merely to satisfy a stale PRD sentence.
3. **ONE-3-LIFETEST (priority 10):** real-composition runner, restart and model-replacement scenario, expected-fail handling, replay-blocked/mechanism-off controls and JSON/Markdown evidence pack.
4. **ONE-4-RECORDER-LEDGER (priority 11):** one structured record per turn, append-only developmental ledger integrated through the Continuity Law, provenance, restart tests and an in-app Mirror/share surface.
5. **ONE-5-IDENTITY-SYNC-FLOOR (priority 12):** owner-authorized Friday/she identity record, matching repo/phone sync checks and immutable-floor hash tripwire.
6. **ONE-6-SEED-REPORT (priority 13):** report all required evidence, DEV-1 expected-fail developmental test, full CI, APK artifact and evidence links. It is the end of this PRD stage, not the end of Friday's full vision.

The stage order is dependency-driven: close launch readiness, reconcile and prove the production composition, create the Life Test and recording/ledger surfaces, then identity/floor sync, and finally the Seed report. A stage-complete result must not be represented as full Friday completion.

## Closure rule

The plan is complete only when Friday can remain the same functioning individual offline, develop from experience, direct repository-side construction through a real secure handoff, independently judge the resulting change, integrate verified work, and demonstrate it on the target phone. Until then, the scorecard remains an honest partial-completion estimate.

## Post-build phase — compare Venon's words with Friday's actual work

This is a separate, mandatory phase **after** the build-plan execution and release evidence are complete. It is not a self-congratulation summary. It is a requirement-by-requirement audit:

1. Reconstruct the owner's requirements from the original project conversations and the full `New Folder chats.zip` archive if it can be safely extracted; include the latest explicit corrections and `.ralph/VISION.md`. Do not pretend the ZIP has been read until it actually has.
2. Break the owner's words into atomic requirements, preserve exact short quotations and source dates, and give each a stable requirement ID.
3. For every requirement, inspect the actual current source, architecture, model assets/licences, tests, CI artifacts and target-phone evidence. Link exact files/classes/tests and cite the relevant build/device run.
4. Grade each requirement as **faithful**, **partial**, **missing**, **contradicted**, **unverified**, or **overbuilt**. A source file existing is not proof of runtime behaviour; a passing synthetic test is not device evidence.
5. Record where the implementation changed the owner's intent, where an old repository feature contradicts the vision, and where the assistant built unnecessary or misdirected work. Do not hide assistant mistakes or inflate completion percentages.
6. Produce a gap list ranked by impact on the owner's intended Friday, then let the owner correct the interpretation before any final acceptance.

The comparison phase starts only after the build phase has a complete evidence pack. It may reopen implementation work; a high score is not its goal. Fidelity to the owner's words is.
