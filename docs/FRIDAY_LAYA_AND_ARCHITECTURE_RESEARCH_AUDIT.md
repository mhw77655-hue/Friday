# Friday research audit: Laya, typed decisions, and the path to a durable individual

**Audit date:** 2026-11-21  
**Repository examined:** `mhw77655-hue/Friday`, canonical `main`  
**Governing authority:** `.ralph/FRIDAY_DELIVERY_PLAN.md`  
**Purpose:** extract the strongest reusable mechanisms from Laya and related systems, identify the real architectural fragilities that prevent Friday from becoming a durable, continuously developing individual, and turn those findings into testable engineering priorities.

This is a source-and-architecture audit, not a claim that every one of the repository's 1,269 tracked paths was line-by-line reviewed. The inventory contains 746 Kotlin files and 348 test-source files; this review inspected the governing plan, key architecture/seed/resource/self-evolution documents, the production turn-path composition, seed implementation, deterministic decision engine, model/runtime surfaces and update client, plus the Laya repository and primary documentation. Findings are labelled by evidence strength. Follow-up file-by-file audits remain required for the large subsystems.

## 1. Terminology correction: Jev was not the algorithm

The earlier project discussion called the bounded typed-decision role "JEV". That was imprecise.

- **TypeSafe Jev** is a hosted decision service/API. Friday sends bounded state and typed Choice / Score / Noul questions; the service returns judgments. It is not an algorithmic family name, a local model, an actuator, or Friday's brain.
- **Laya** is a concrete family of non-autoregressive typed-decision models. A bidirectional encoder jointly processes state and question and decision heads return structured probabilities for Choice, Score and Noul without generating a token sequence or parsing generated JSON.
- Friday's durable abstraction should be called a **Typed Decision Capability** (TDC) or equivalent neutral contract. Jev, Laya, deterministic rules, other local models and future learned mechanisms are replaceable providers of that contract.

Do not retain "JEV algorithm" as a technical dependency. Keep historical references for provenance, but use the corrected vocabulary in new design and code.

## 2. What Laya actually contributes

The Laya family provides a useful System-1-like decision primitive: ask a constrained question over a bounded state and receive a distribution over explicit outcomes. This can be much cheaper and easier to verify than asking a generative model to reason in prose and then parsing its answer.

The CoreML port reports:
- Apple Silicon/Core ML/Neural Engine short-decision measurements around 4.98 ms P50 / 5.31 ms P95 on an M3 Max for one 91-token question padded to 96; the compared compiled MLX result was 6.94 / 7.39 ms.
- The same experiment reports estimated whole-system energy per decision of 0.1540 J vs 0.4288 J, but this is an Apple M3 Max system measurement, not an Android handset result.
- The W8 palette is approximate; six- and four-bit variants failed the port's stated numerical-fidelity gate and were not published.
- The 96-token ANE bundle is explicitly short-context. A separate 1,024-token ANE graph took about 91.7 ms in its serial screen.
- The CoreML port's 189/189 and 59/59 fixture matches measure conversion/port fidelity on selected fixtures, not broad real-world task accuracy.
- The library exposes confidence/abstention fields, but the authors explicitly warn that these confidence values need held-out calibration before interpreting them as correctness probabilities.

The upstream model family lists ModernBERT-large 421M (English) and multilingual mmBERT-base 322M checkpoints, with a typed-decisions specialist. A separate published typed-decisions benchmark reports 0.766 accuracy over 400 cases / 2,000 decisions for its specialist checkpoint, while Jev's 0.727 is a third-party published comparison with differing sample/prompt conditions. This is indicative evidence, not a clean head-to-head result on Friday's tasks.

**The strongest transferable idea is not "use the Laya app". It is:**
1. express bounded decisions as explicit typed questions;
2. compute all relevant answer alternatives in one bidirectional pass rather than autoregressive generation;
3. batch independent questions when the backend permits;
4. return distributions, not only a hard label;
5. calibrate and allow abstention;
6. evaluate on task-specific held-out data;
7. keep model loading/routing separate from the decision contract;
8. use a bounded resident-model router with explicit preloading/unloading and concurrency semantics;
9. preserve a transparent deterministic safety/verification layer around the learned decision.

## 3. Why copying Laya-CoreML directly would be wrong for Friday

The supplied repository is specifically a **Core ML / Apple Neural Engine** implementation. Friday's first production body targets Android on a Realme 9 Pro 5G with Snapdragon 695 / Adreno 619 and 8 GB RAM. Core ML and Apple's ANE execution graph cannot be treated as an Android backend.

The Laya source weights/architecture are a potential donor; the CoreML export, graph rewrite, shape cache and ANE-specific kernel strategy are not portable as-is. A port must preserve the trained model's input formatting, tokenizer, attention/mask semantics, position/option layout, calibration and output semantics. Merely converting a file or changing a compute-device flag is not sufficient.

Android's NNAPI is deprecated starting Android 15. New work should not assume NNAPI is the future universal accelerator interface. LiteRT supports vendor delegates, including Qualcomm AI Engine Direct / QNN for compatible Qualcomm devices, but vendor availability, chipset support, model-op coverage, ABI, driver and firmware must be probed on the exact phone. ONNX Runtime CPU/XNNPACK is a useful portable baseline; QNN is an optional hardware path, not a guaranteed Snapdragon 695 speedup.

The right adaptation route is:
- retain original Laya checkpoint and tokenizer as the reference;
- create a model-independent TDC contract in Friday;
- establish a CPU reference implementation and numerical parity tests;
- evaluate ONNX Runtime Android CPU first, then QNN/LiteRT delegate only if the target handset and required ops are actually supported;
- profile cold load, warm latency, batch latency, peak process PSS/RSS, memory-mapped weights, battery/thermal behaviour and contention with voice/UI;
- keep the TDC provider unloadable and outside Friday's identity/continuity store;
- only choose the runtime after real handset evidence.

No current M3 Max millisecond result should be quoted as a Friday phone performance prediction.

## 4. Friday's architecture: real assets and real fragilities

### 4.1 Durable individual versus language ability

**Observed asset:** `FridayIndividual` owns an app-local identity record, a persistent experience substrate and a model-free expression path. Its identity file uses a version marker, a process lock plus OS file lock, atomic move and refuses to silently create a new identity if the existing record is corrupt. This is a meaningful continuity primitive.

**Critical limitation:** `FridayModelFreeMind` is a deliberately small pattern/fact substrate. It handles a few regular-expression facts, arithmetic, greetings, time/date and feature associations. Its own source says it is not a language model and does not claim arbitrary prose understanding. This is honest and useful as a continuity fallback, but it is not yet a sufficient model-free cognitive foundation for the owner's desired open-ended interaction.

**Required distinction:** the individual/experience substrate must remain alive and authoritative across model loss; the actual language/reasoning provider must be replaceable. Do not market the small seed as general intelligence. Prove that it is connected to real production turns and that its durable learning changes later behaviour in a novel context.

### 4.2 Composition and integration risk

**Observed asset:** `TurnPathAssembly` is explicitly the production turn-path composition root. It wires stores, identity, memory retrieval, social/context estimation, capability fabric, model authority and the latency pipeline, and fingerprints the components.

**Fragility:** the repository has many parallel-looking packages and historical architecture documents. A class, module, test or architecture label is not proof that it is called by the live Android turn path. Every capability needs a trace from the real UI/voice entry point through the production composition to a real output/action and persisted outcome. The architecture truth audit already records examples where isolated builder/synthesis mechanisms were not in production composition and process-local capabilities did not survive restart.

**Required action:** maintain a generated production call graph / composition manifest from the real root; mark each subsystem as wired, exercised, persistent, device-verified or legacy. Remove or quarantine dead duplicate paths only after reference and behaviour tests.

### 4.3 Typed decisions and execution

**Observed assets:** Friday already has a deterministic `DecisionEngine` that ranks supplied options against goal alignment, expected outcome, risk, uncertainty, reversibility, effort, resources and constraints. It does not invent options. The Android control documentation separates the decision adviser from the local executor and says the local policy remains authoritative.

**Fragilities:**
- Current decision engine's option supply is a separate problem: if candidate generation is narrow, better scoring cannot invent missing routes.
- TypeSafe Jev is hosted and requires a configured key/network. It cannot be Friday's always-available local fast path.
- One-action-at-a-time phone control is safe but limits outcome-level multi-step completion and adaptive fallback.
- Confidence from any learned model is not proof. Friday needs calibrated abstention and a local verifier for each consequential result.
- Decision trace must capture alternatives considered, evidence, cost, uncertainty, policy decision, chosen route, execution result and postcondition—not merely the final label.

**Recommended design:** typed-decision providers return structured candidate assessments into Friday's own policy/decision substrate. Friday retains option discovery, policy, authorization, action execution and postcondition verification. The provider must never be the authority that grants Android permissions or declares the outcome successful.

### 4.4 Resource adaptation and working-set intelligence

**Observed assets:** Friday has a `ResourceGovernor`, `CognitiveAdmissionPolicy`, model backend abstractions, process-isolated inference, model catalogue/storage components and device-specific quality-preserving-compute documentation. The device profile correctly warns that CPU may beat GPU on a Snapdragon 695 analogue and that a large model's file size does not equal its peak RAM.

**Fragilities:**
- Resource estimates are not the same as measured per-model peak PSS/RSS, memory bandwidth, cold-start and sustained thermal evidence.
- A model can be small on disk but expensive in activation/KV/context memory.
- Model paging and expert selection do not guarantee constant compute: token routing, weight movement, cache misses and storage bandwidth can dominate.
- Android's heterogeneous accelerator stack is not a single reliable API; NNAPI deprecation makes a fixed NNAPI-first design fragile.
- Loading/unloading while voice and UI are active can cause contention or process death unless admission and lifecycle are coordinated.

**Recommended direction:** a measured working-set controller across all capability resources, not only model weights. Separate hot resident state, warm prefetch, cold durable resources and evictable caches; protect identity, active task state and voice path. Predict upcoming needs from task state, not a fixed domain-to-expert atlas. Measure compute + I/O + memory + energy + latency as one cost.

### 4.5 Learning and developmental change

**Observed assets:** the repository contains experience, memory provenance, consolidation, world/self/user models, expectation/prediction, capability evidence, adapter manifests, continuity gates and Life Test concepts. The governing plan correctly requires experience to alter later behaviour, not merely add notes to a prompt.

**Critical fragility:** the codebase has many stores and learning labels, but quantity of memory infrastructure does not prove a causal developmental loop. The architecture truth audit explicitly identifies promoted executable capability rehydration after process death as incomplete and process-local registries as a continuity gap. The source archive also describes self-study-to-builder synchronization as incomplete and warns that old ToolBuilder code is not the approved production runtime.

**Required proof:** a real experience produces a versioned internal change; a later, meaningfully different task uses that change; the result improves against baseline; the improvement survives force-stop/restart; disabling the learned change restores baseline behaviour; provenance identifies the experience and update; no transcript replay shortcut is used.

### 4.6 Capability grafting and reconstitution

**Observed asset:** the plan already describes a model-independent Capability Substrate and several donor-transfer routes: compatible parameter/adapters, distillation, representation alignment, algorithm extraction and training a new shared representation.

**Fragility:** this is currently a long-term architecture direction, not a demonstrated end-to-end donor-to-Friday capability inheritance pipeline. A model's skill is not necessarily localized to a detachable layer. Heterogeneous tensor surgery is invalid without architecture/tokenizer/base compatibility. Behavioural distillation is the general route across incompatible donors, but it requires representative teacher data, task definitions, quality gates and regression suites.

**Recommended direction:** start with Laya's bounded typed decision behavior as a tightly scoped donor capability. Build a donor-independent TDC test suite. Compare the donor's outputs with a small student or alternative implementation, then remove the donor and rerun. Only call it inherited if Friday retains the capability without querying Laya at runtime.

### 4.7 The install-once update channel is not yet complete

**Observed source:** `FridayUpdateClient` currently discovers the latest GitHub release, checks package ID, version and current signer fingerprint, downloads the APK and verifies its SHA-256 before staging.

**Fragilities still visible in this first implementation:**
- No cryptographic signature verification of the release metadata itself is present in the inspected class; a checksum fetched from the same untrusted metadata endpoint is not independent authenticity.
- It checks a current single signer fingerprint, not a complete proof-of-rotation / signer-lineage policy.
- It does not yet verify APK package/version/signer by parsing the staged APK before installation.
- It does not install, handle pending owner action, resume interrupted install, run post-update health checks, preserve/recover a last-known-good package, or verify that the installed revision equals the intended release.
- It assumes a GitHub latest-release asset naming contract that the publishing workflow must actually satisfy.
- It needs bounded redirects/host allowlists, response-size/time limits and atomic temp-file staging to avoid partial files being mistaken for valid artifacts.
- The permanent production signing key and same-key update proof must precede first phone installation.

Do not enable automatic updates from this client until these are addressed and tested.

### 4.8 Evidence and diagnostics

**Observed assets:** action traces, crash journal, diagnostic event log, settings export/copy, parent trace correlation and an explicit live-device bridge contract exist.

**Fragilities:** physical-device round-trip has not been proven; the bridge's shared-text receive path and export/import UX have needed corrections; action-level causal trace and UI/logcat evidence have known gaps. Tests run on emulator are not evidence for Realme firmware.

**Required evidence packet:** installed app version + exact source SHA, device/API/ABI and permission state, request/interpretation (only owner-approved), candidate routes/reasons, timestamps, every backend attempt, platform errors, expected/observed postconditions, verification evidence, resource snapshot and redaction preview. The packet must be importable to the same development conversation and reproduce against the exact source revision.

### 4.9 Voice and presence

The governing plan correctly prioritizes voice-first interaction, interruption/barge-in, Egyptian Arabic and mixed Arabic/English, offline fallback and measured listening tests. The broad repository contains voice service, voice model management, streaming TTS, interaction service and phone action surfaces. A source inventory is not proof that microphone capture, streaming recognition, synthesis, interruption and action overlap all work on the actual phone. These must be tested as one continuous path under Doze, screen-off, process death, thermal load and permission revocation.

### 4.10 Self-development, remote workshop and security

GitHub can be Friday's workshop, CI and provenance store; it is not a magic remote shell into an Android phone. Phone-initiated, authenticated, narrow capability requests are safer than exposing ADB or unrestricted shell. Generated capabilities must remain restricted, signed, permission-declared, resource-bounded, independently tested and rollbackable. A process-local sandbox must not be called an OS security boundary.

## 5. Recommended Friday architecture: a donor-neutral adaptive capability field

Do not build a permanent list of specialist brains. Build a capability field in which every candidate resource advertises:
- typed input/output contracts and supported tasks;
- preconditions and evidence requirements;
- model/runtime/checkpoint/format/license provenance;
- quality and calibration curves on Friday's task corpus;
- cold/warm latency, peak RAM/PSS, storage I/O, CPU/GPU/NPU use, energy and thermal profile;
- residency state (hot/warm/cold), startup cost and unload safety;
- uncertainty and abstention behaviour;
- dependencies, permission needs and failure modes;
- rollback lineage and known-good revision.

For each token, event or requested outcome, Friday should spend computation only where the current uncertainty and expected value justify it. Candidate resources can be prepared in parallel and composed through shared typed interfaces. The chooser itself must be replaceable and learnable. Use a cheap local fast path for familiar cases, TDC for bounded uncertain decisions, and deeper generative/remote reasoning only when the expected value exceeds the cost. Preserve the possibility that Friday eventually builds a non-LLM cognitive substrate; none of these current models defines her final brain.

## 6. Research and implementation order

### Immediate experiments (before production integration)
1. Extract and pin the exact Laya upstream checkpoint, tokenizer, prompt formatting, license, and a reproducible reference runner.
2. Create Friday-specific held-out decision tasks: phone route choice, permission/capability classification, ambiguity, risk, verification confidence, fallback selection, multilingual Egyptian Arabic / English mixed input, and adversarial near-misses.
3. Establish baselines: Friday deterministic DecisionEngine, hosted Jev if available, Laya upstream CPU, and a local small-model/generative baseline.
4. Record accuracy, calibration (Brier/ECE), selective risk/coverage, abstention, p50/p95 end-to-end latency, cold-start, memory, energy and sustained thermal behavior.
5. Port Laya to ONNX Android CPU as a portability baseline; only then evaluate LiteRT/QNN on the actual Realme. CoreML is not the target runtime.
6. Check whether multiple typed questions can be batched and whether state encoding can be reused without changing the trained model's semantics. Do not assume reuse is valid; current Laya docs explicitly state question representations depend on both state and question and the CoreML implementation may issue sequential calls.

### Production integration after evidence
1. Add a model/runtime-neutral `TypedDecisionProvider` contract.
2. Add deterministic provider and Laya provider behind the same contract; keep Jev as optional remote provider.
3. Add calibrated abstention and policy-owned fallback.
4. Add concurrency-safe bounded resident resource manager and predictive prefetch with memory reserve.
5. Integrate provider into actual production turn/action path with complete trace IDs.
6. Run differential tests against upstream Laya and held-out Friday tasks.
7. Promote only if quality meets the deterministic/hosted baseline while latency/resource cost materially improves on target handset.

### Critical continuity and delivery work in parallel
- Finish install-once production signing and updater authenticity/installation/health/rollback.
- Persist and rehydrate promoted executable capability artifacts atomically.
- Prove one causal developmental learning loop across restart.
- Prove voice interruption and local fallback on the actual phone.
- Prove physical phone diagnostic export/import and source-revision matching.
- Generate a production composition inventory and remove dead/duplicate architecture paths safely.

## 7. Decision: what to take from Laya, what to reject, what to extend

**Take:** non-autoregressive typed outputs; explicit decision questions; batching; calibrated distributions; abstention; concurrency-safe bounded model residency; shape-aware compiled execution; reproducible end-to-end benchmarks; reference parity and numerical-fidelity gates.

**Reject as Friday's permanent design:** Apple-only CoreML assumptions; fixed 96-token ANE context; a hard-coded language/domain expert map; uncalibrated confidence-as-truth; interpreting conversion fixture parity as task competence; assuming model weights can be cut and pasted across architectures; and assuming M3 Max numbers transfer to Snapdragon 695.

**Extend:** make the decision capability one resource among many in Friday's adaptive capability field; allow learned providers and synthesized procedures to replace it; share state/context and resource scheduling across perception, prediction, action and verification; and eventually allow Friday to create a new decision substrate rather than remain tied to Laya or Jev.

## 8. Sources

- Laya CoreML port: https://github.com/mizorewww/laya-coreml
- Laya upstream model family: https://huggingface.co/convaiinnovations/laya
- Laya typed-decisions checkpoint and its stated benchmark: https://huggingface.co/convaiinnovations/laya-typed-decisions
- Independent MLX port / architecture notes: https://github.com/xosi/laya
- Android NNAPI migration guide: https://developer.android.com/ndk/guides/neuralnetworks/migration-guide
- Android NNAPI deprecation details: https://developer.android.com/ndk/guides/neuralnetworks/
- LiteRT Android NPU delegates: https://developers.google.com/edge/litert/android/npu/overview
- Qualcomm LiteRT / QNN delegate docs: https://docs.qualcomm.com/bundle/publicresource/topics/80-70029-54
- Friday source documents: `FRIDAY_ARCHITECTURE_TRUTH_AUDIT.md`, `FRIDAY_SEED_DESIGN_HYPOTHESIS.md`, `QUALITY_PRESERVING_COMPUTE.md`, `FRIDAY_SELF_EVOLUTION_AND_DEVICE_BRIDGE.md`, `REALME_9_PRO_5G_DEVICE_PROFILE.md`, `JEV_PHONE_CONTROL.md`, and `.ralph/FRIDAY_DELIVERY_PLAN.md`.

All third-party performance and accuracy values above are attributed to the relevant project's own published measurements. They are not independent replications on Friday's handset.
