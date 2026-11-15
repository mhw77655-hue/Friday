# FRIDAY — Vision-to-Reality Build Plan

**Snapshot:** main at `1aec50652d9936d2cde0b2d192ab21d51ba4cdff` (2026-11-14 upload commit); reviewed 2026-11-15.  
**Purpose:** map Venon's intended Friday against the actual repository, identify the shortest evidence-backed route to the first developing Friday, and keep implementation decisions subordinate to the vision.

## 0. Source authority and limits

The authority order is:

1. Venon's latest explicit statements.
2. `.ralph/VISION.md` as the written record of those statements.
3. `.ralph/prd.json` as the current work queue, corrected wherever it conflicts with the first two.
4. Audits, plans, implementation comments and prior assistant recommendations are evidence or hypotheses, never authority.

The readable project conversations reinforce the central requirement: Venon wants to build Friday **with Friday**, not have external assistants dictate a fixed architecture or build her on his behalf. GPT, Claude, Ralph, search, coding workers and models are instruments she should progressively learn to use, direct and correct. Venon supplies intent, experience, correction, authorization and the real-world tests.

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

## 2. Current repository reality

### Implemented and merged

| Evidence | What exists | What it proves — and does not prove |
|---|---|---|
| PR #3, merged 2026-11-10 | Engine readiness state, typed access, UI waking surface, pure Kotlin tests | A meaningful launch-order repair. It does not close the PRD story's real-process/device and full Life Test evidence. |
| PR #4, merged 2026-11-11 | Foundation audit: eight Builder seats, failure-chain trace, legacy inventory, conflicts and worker dispatch | The bottlenecks and disconnected paths were documented. Documentation is not integration. |
| PR #5, merged 2026-11-11 | TTS initialization gate, per-process TTS owner and guarded call sites | TTS no longer assumes the Android engine is ready. The asynchronous file-synthesis completion race remains. |
| Existing source | `TurnPathAssembly`, Android/JVM platform ports, cognitive engine, identity/world/social components, memory graph and retrieval, continuity law, provenance, consolidation, capability registry, research and ToolBuilder | Substantial substrate exists. It has not yet demonstrated the complete living/developmental behaviour on one real composition. |

### Partial, disconnected or not demonstrated

| Requirement from the Vision | Repository evidence | Current assessment |
|---|---|---|
| One continuing individual | IdentityContext, SelfModel, UserProfile, WorldModelService, relationship model and persistent stores exist. VISION §12 still records identity as JARVIS/he; ONE-5 is open. | Partial; identity has mechanisms but the required Friday/she identity and continuity test are not complete. |
| Development, not storage | Workspace, RecordedTurns, ReplayCheck, ContinuityLaw, change log and memory consolidation exist. | Partial; no complete Life Test currently demonstrates experience-driven persistent behavioural change with both controls. |
| Open-ended cognition | VISION §12 identifies closed `ClaimKind`, fixed `ChangeLayer`, flat slow-change thresholds, intent always STATEMENT and no live prediction. | Active vision conflicts, not minor cleanup. Mechanisms must be evaluated and revised without replacing them with another fixed ontology. |
| One shared turn composition | `TurnPathAssembly`, `AndroidPlatformPorts`, `JvmPlatformPorts`, and parity/clock tests exist. ONE-2 remains open. `TurnPathAssembly` still initializes and calls legacy Human Core paths. | Partial and internally conflicted. The PRD asks for Human Core execution on both hosts; VISION §12 quarantines it. The Vision wins. Reconcile ONE-2 before executing it. |
| Memory and continuity | Memory graph, retrieval, consolidation daemon/loop, provenance and forget propagation exist. ONE-4 remains open. | Storage and derived-artifact provenance are real mechanisms; the Development Ledger, flight recorder, Mirror and proven developmental effects are not complete. |
| Capability acquisition | CapabilityGapDetector, ToolBuilder, ToolBuilderComposition, EvolutionEngine and UniversalResearchEngine exist. | A builder for registered micro-systems exists, not a demonstrated general code-building capability. |
| Failure → gap → construction | FailureSurface records failures and degraded capabilities; the audit found no live bridge to CognitiveEngine, no production callers of `updateCapabilityState`, and no runtime callers of `requestBuild`. | Disconnected. Friday can report a failure without autonomously turning it into a tested construction request. |
| Real resident intelligence | The current composition still names an Ollama endpoint at `127.0.0.1:8080`; the Termux service was removed from the phone. | No verified in-app offline resident inference path in the evidence reviewed. A model adapter or config is not proof that a model is running on the phone. |
| Voice as one presence | Native TTS readiness and voice-organism code exist. PR #5 still registers a file before its TTS completion callback. | Partial. Complete the async lifecycle, then test audio on the phone. Egyptian Arabic quality is unproven without recordings and an evaluation set. |
| Resource symbiosis / NAE | ResourceGovernor, environment and device observations exist. | No measured hardware-in-the-loop search yet proving better cognition per RAM, heat, latency and battery on the target phone. |
| Life Test | ONE-3 is open; ordinary unit tests exist. | No complete test harness found that drives the real composition through learning, restart, replay-blocked and mechanism-off controls. |
| Development partnership | Build and research mechanisms exist, but the human still owns gap detection, specification, device evidence and integration. | Friday is not yet a co-builder. She needs a visible workshop where she can inspect evidence, formulate a need, direct workers and explain the result. |

## 3. Decisions that must not be made by accident

1. **Do not equate Human Core with Friday's personhood.** The current Vision explicitly quarantines the legacy Human Core. ONE-2's acceptance criteria must be revised to preserve a shared composition without restoring that legacy mechanism as a permanent cognitive authority. It may remain temporarily as an adapter only where a Life Test proves contribution.
2. **Do not treat ToolBuilder as the whole Builder.** Its current mutant-habitat evolution of registered micro-systems is one construction instrument. Friday's broader Builder must also be able to research, direct external coding workers, inspect diffs, run independent tests, learn from failed attempts and safely request approval.
3. **Do not mistake a failure log for a capability gap.** A runtime bridge must carry the actual incident and affected outcome into an inspectable proposal, not merely copy a subsystem label into a fixed capability enum.
4. **Do not treat stored memories as development.** A lasting change must alter later behaviour under the Vision's two controls.
5. **Do not make Ollama, Termux, one model, one worker, or one device a condition of Friday's existence.** Each is replaceable infrastructure.
6. **Do not silently mark PRD stories passed because code merged.** Keep implementation, CI, real-composition Life Test and phone evidence distinct.
7. **Do not put long-lived credentials in chat or grant a builder unrestricted repository/production access.** Use narrowly scoped protected secrets, disposable workspaces, an independent judge and owner approval for consequential integration.

## 4. Execution map — dependency-driven, not a waterfall

### A. Close immediate defects and establish truthful evidence
- Finish TTS file synthesis: request accepted, callback completed, file validated, then cache promotion and playback; errors/stops remove partial files and reach FailureSurface.
- Reconcile `.ralph/prd.json` against merged code, audit notes and test results. In particular, do not close LAUNCH-WAKE-ORDER or ONE-2 merely because their PRs exist.
- Capture a fresh phone evidence pack after the next green APK: cold starts, one TTS owner per process, zero premature TTS errors, successful completed audio, and no hidden local-server dependency.

### B. Make one real Friday turn path
- Complete the Android/JVM composition parity story against the Vision, not against legacy Human Core acceptance criteria.
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
