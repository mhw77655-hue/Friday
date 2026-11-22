# Friday Adaptive Model Execution Fabric

Status: architecture proposal for implementation; not yet implemented or device-validated.
Owner outcome: Friday must operate as one persistent digital individual. Models are replaceable computational assets, not her identity. Model acquisition and operation should require no manual model administration after the initial app permissions and storage/network choices.

## 1. Decision

Build a Friday-owned **Adaptive Model Execution Fabric (FMEF)** above multiple replaceable inference runtimes. Do not make Friday synonymous with llama.cpp, Ollama, one GGUF file, one vendor, one model family, or a fixed model-size ladder.

FMEF has two cooperating parts:

- **Cognitive Workload Compiler (CWC):** converts a requested cognitive outcome into a dependency graph of operations, chooses algorithms/models/backends, assigns quality and resource budgets, and can progressively refine an answer.
- **Adaptive Asset and Runtime Orchestrator (AARO):** discovers host capabilities, acquires and verifies assets, prepares compatible runtime artifacts, loads/evicts components, monitors execution, recovers failures, and records measured outcomes.

The fabric is not itself Friday's identity or complete brain. It supplies execution for cognitive faculties owned by Friday's persistent state and capability contracts.

"Without effort" means Friday handles discovery, compatibility, downloads, configuration, benchmarking, loading, cache management, updates and recovery automatically within the owner's declared network/storage/privacy policy. It does not mean zero initial installation, zero storage, zero energy, zero compute, or permission to download large assets without consent.

## 2. Repository baseline and gap

The current production path includes `InAppLlamaModelBackend`, `LocalInferenceEngine`, GGUF resolution through `ModelPathResolver`, and NAE admission before loading and inference. The backend uses fixed reasoning/resident candidate lists and unloads when resource admission fails. `ModelManager` has a provider abstraction and tier lifecycle, but its current cloud provider mapping is a heuristic stub. The research catalog documents candidate weights and correctly says they still need exact-device validation.

This is a useful baseline, not the desired final fabric. It currently does not establish:
- Automatic, signed, policy-aware acquisition and qualification of all required model/algorithm assets.
- A measured multi-runtime backend portfolio selected by device and operation.
- A unified memory/weight/KV cache manager spanning concurrent cognitive workloads.
- Model-independent state transfer and task continuation across backend/model replacement.
- Expert/weight streaming and predictive prefetch as a measured serving mode.
- A workload compiler that composes neural and non-neural operations rather than selecting one model by tier.
- A complete per-asset capability truth state and end-to-end model lifecycle evidence.

Do not remove the current working path until alternatives pass the same contract and benchmark suite.

## 3. Core architecture

### 3.1 Canonical cognitive operation contract

Every backend implements a common typed contract, but the contract describes operations rather than pretending every model is interchangeable. Examples: text generation, embedding, reranking, speech recognition, speech synthesis, image/video encoding, latent prediction, classification, structured extraction and specialist inference.

Each operation declares:
- Input/output schema and modality.
- Required context/state and whether streaming is supported.
- Tool/action capability, if any; model output is never direct authorization.
- Quality profile and uncertainty/provenance requirements.
- Memory, storage, latency, energy and thermal estimates with confidence intervals.
- Supported runtime formats, operators, quantizations and hardware backends.
- Cancellation, pause/resume and checkpoint semantics.
- Model/tokenizer/preprocessor/postprocessor dependency versions.

Backends return typed results, measured resource telemetry, errors and provenance. No caller depends on provider-specific prompt formats or native handles.

### 3.2 Model-independent Friday state

The durable source of truth is Friday's own event/state system: identity, goals, relationship context, autobiographical memory, task state, learned representations, permissions and decisions. Runtime sessions are disposable.

A backend switch must preserve task intent, evidence, memory references, constraints and completed work. It must not assume KV caches or hidden recurrent state are portable across model architectures or tokenizers. On a switch, CWC reconstructs a bounded context from canonical state and provenance. Where a backend supports a compatible checkpoint, use it only after validating model, tokenizer, runtime and cache versions.

### 3.3 Cognitive Workload Compiler

For each request, CWC:
1. Resolves the desired outcome and required cognitive operations.
2. Builds a directed acyclic or stateful execution graph, including deterministic algorithms and specialist neural models.
3. Identifies mandatory operations versus optional quality refinements.
4. Queries current host, installed assets, runtime compatibility, permissions, thermal state and NAE resource envelope.
5. Selects a plan using quality, calibration, first-useful-result latency, sustained latency, energy, peak memory, storage I/O, privacy, availability and cost.
6. Starts a useful local plan as soon as possible; runs refinements when resources permit.
7. Monitors evidence and postconditions; retries/replans only through authorized alternatives.
8. Records prediction versus outcome to improve future scheduling.

The planner must not select solely by parameter count, advertised benchmark, model-card claims or file size. It must not silently substitute a weaker model if the task requires a quality threshold. It should report a typed degraded state and continue independent parts of the task.

### 3.4 Runtime adapter portfolio

Implement adapters behind the same operation contract. Select per operator/model and measured host; do not bundle every runtime into every app build.

| Runtime family | Intended use in FMEF | Decision |
|---|---|---|
| llama.cpp / GGUF | Existing portable text-generation baseline; CPU and supported GPU backends; broad GGUF ecosystem | Keep as baseline and compatibility fallback, not universal architecture |
| LiteRT / LiteRT-LM | Android-native CPU/GPU model execution; useful for perception, audio, embeddings and supported generative models | Prototype for non-LLM first; evaluate LiteRT-LM for eligible LLM assets |
| ExecuTorch | PyTorch-exported specialist models and supported delegated subgraphs | Add only for models whose operators and exact target SoC are supported |
| MLC-LLM / TVM | Ahead-of-time, target-compiled LLM execution; Vulkan/GPU candidates | Benchmark on Adreno 619; compilation and binary compatibility are per target |
| ONNX Runtime Mobile | Interoperable specialist models and supported execution providers | Use where model portability/operator coverage justifies it |
| Qualcomm QNN / GenieX | Qualcomm-accelerated execution where exact SoC, firmware, SDK and operator support are verified | Experimental on this target; never assume HTP support from the Snapdragon brand alone |
| Native Android / platform APIs | Speech, media, sensors and system capabilities | Use platform service when it meets offline/privacy/latency requirements; retain independent fallback |

A runtime is accepted only after a capability probe and representative correctness/performance test on the actual device. Runtime availability does not imply a particular model can execute efficiently on it. Avoid duplicate runtimes for the same model unless measured fallback or quality gains justify the APK and maintenance cost.

### 3.5 Adaptive asset registry and acquisition

Maintain a signed, versioned manifest for every asset and its dependency graph. Required metadata:
- Immutable asset ID, publisher, source, revision, license and permitted use.
- SHA-256 digest and publisher signature or trusted signed-manifest binding.
- Model architecture, format, tokenizer, pre/postprocessors, modality, context and operator requirements.
- Runtime and ABI versions, required kernels, supported CPU/GPU/NPU backends.
- Quantization, parameter count, file size and estimated peak working set (weights + KV/cache + activations + runtime buffers).
- Minimum/target RAM, storage reserve, API level, device/SoC allowlist or denylist.
- Quality-evaluation results, calibration, benchmark hardware and test-suite version.
- Dependencies, conflicts, supersedence, rollback version and security advisories.

Acquisition lifecycle:
1. Discover the capability gap from a workload, not by downloading a giant catalog.
2. Select an eligible asset variant using device profile and policy.
3. Check license, provenance, storage reserve, battery, thermal state and network policy.
4. Download in resumable chunks with per-chunk integrity; support pause/retry and mirror fallback.
5. Verify full digest and signature, format, metadata and dependency closure.
6. Stage in private app storage; never load from a partial file.
7. Run static compatibility checks and a sandboxed smoke test.
8. Run a bounded on-device qualification benchmark; record quality and resource telemetry.
9. Atomically promote to qualified; keep previous known-good version for rollback.
10. Garbage-collect only unreferenced assets after checking active tasks, pinned dependencies and owner retention policy.

Asset states must be explicit: catalogued, policy-blocked, downloading, staged, verified, compatible, smoke-tested, locally-qualified, ready, loaded, degraded, quarantined, superseded and removed. "Installed runtime", "downloaded model" and "qualified usable capability" are different facts.

For Google Play distribution, evaluate Play for On-device AI packs: install-time, fast-follow and on-demand delivery, device targeting and differential updates. It is a delivery mechanism, not the runtime or model manager. For non-Play APK distribution, AARO needs its own authenticated resumable downloader and update channel.

### 3.6 Resource and memory manager

NAE remains the resource governor, but extend it from a model admission gate into a whole-workload scheduler. Measure actual free/available RAM, process PSS/RSS, memory pressure, storage bandwidth/free space, battery, charging, thermal status, CPU/GPU activity and background restrictions. Estimates must include:
- Weight residency and file-backed page cache.
- KV cache (which grows with sequence length and concurrency).
- Activations, scratch buffers, graph/compiler memory and native allocations.
- Tokenizer, audio/video buffers, embeddings and other concurrent organs.
- Android/foreground-app reserve and uncertainty margin.

Use several strategies selectively:

**Model-side compression**
- Group-wise INT4/INT8 quantization and mixed precision; calibrate on Friday's actual tasks.
- Structured pruning and sparse kernels only when the runtime can exploit the sparsity.
- Distillation into task-specific compact students; retain a stronger teacher as an offline development tool.
- LoRA/adapters for personalization or specialization; merge adapters when quality and update workflow permit.
- Architecture-aware conversion and kernel fusion.

**Memory and I/O**
- Memory-map immutable weights where the runtime supports it; use OS page cache rather than copying entire files into duplicate heap buffers.
- Paged/quantized KV cache with context-aware eviction and bounded concurrency.
- Prompt-prefix caching only for identical compatible prefixes and safe privacy boundaries.
- Expert-on-demand storage for sparse MoE models, with expert-use prediction and asynchronous prefetch.
- Predictive warm-up for likely next operations, but stop prefetch under thermal, battery or foreground memory pressure.
- Avoid assuming that storage streaming is faster: flash I/O, random access, page faults and thermal throttling can make it slower than a smaller resident model.

**Execution**
- Partition operators across CPU/GPU/NPU only when measured end-to-end gains exceed tensor-transfer and synchronization overhead.
- Use asynchronous pipelines and overlap decode, I/O and preprocessing where safe.
- Speculative decoding only when a compatible draft model improves measured time/energy; it can regress on a phone.
- Keep voice capture, identity/memory writes, action control and health monitoring responsive even when model inference is paused.

### 3.7 Model composition and specialist cooperation

A "model" can be a node in a cognitive graph, not the whole brain. CWC may combine a language model with retrieval, a temporal memory, a predictive model, a deterministic solver, a speech model or a vision encoder. It must use typed intermediate representations and explicit confidence/provenance.

Do not concatenate arbitrary model outputs and call it intelligence. Resolve conflicts using evidence, task objectives and a Friday-owned executive policy. Preserve raw specialist outputs and their versions for audit. Keep authorization and device-action verification outside model control.

### 3.8 Automatic operation policy

After initial installation and owner policy selection, normal operation should be automatic:
- Detect hardware and build a device capability fingerprint.
- Benchmark candidate kernels and maintain a per-device performance profile.
- Download needed assets on unmetered Wi-Fi/charging/idle by default; allow explicit owner policy for mobile data or immediate downloads.
- Check free space and reserve before every acquisition.
- Load, pin, prewarm, pause, unload and resume according to workload and NAE.
- Maintain one resident minimal local execution path for essential supported functions.
- Download updates in the background, verify before activation, retain rollback and never update an active model in place.
- Explain only meaningful exceptions: missing owner permission, insufficient storage, incompatible hardware, license restriction, network policy or failed qualification.

No app can silently obtain a large model without network/data/storage consequences. If Friday must work offline immediately after installation, the required minimum assets must be bundled or delivered at install-time; otherwise first acquisition requires connectivity. The user should not need to select quantization, runtime flags, context size or model IDs manually.

## 4. Target-device constraints: Realme 9 Pro 5G

The documented target is Snapdragon 695 (Kryo 660 CPU, Adreno 619 GPU, Hexagon 686 with scalar accelerator and HVX), 8 GB physical RAM and 128 GB storage. The 8 GB figure is not an app budget: Android and other apps share it.

Important caution: Qualcomm's product specification confirms Hexagon 686 scalar/HVX features, but this is not proof of a modern tensor HTP path for arbitrary LLMs. The currently documented ExecuTorch QNN HTP target list examined for this proposal does not list Snapdragon 695. Therefore:
- Establish CPU inference as the guaranteed baseline.
- Benchmark Adreno 619 Vulkan/GPU execution.
- Probe supported Hexagon/QNN paths experimentally; do not assume they work.
- Treat DSP/voice low-power features separately from LLM tensor acceleration.
- Measure sustained thermals, battery drain and foreground-app impact on the actual handset.

Do not promise a 4B/35B/70B model will be usable because weights fit on disk. Peak memory, storage I/O, KV cache and sustained token rate determine usefulness.

## 5. Candidate optimization research to benchmark

These are techniques to test, not a shopping list to enable all at once:

- Quantization: GPTQ/AWQ/GGUF group quantization, mixed-bit and activation-aware approaches.
- Structured pruning, sparse execution and distillation.
- LoRA/DoRA adapters, mergeable low-rank compensation.
- KV quantization, paging, eviction and context compression.
- Prefix/prompt caching and reusable prefill.
- Speculative decoding and draft-model selection.
- MoE expert paging, expert-use prediction and prefetch (EdgeMoE/D2MoE-style ideas).
- Weight streaming and I/O-aware caching.
- Memory-mapped loading, asynchronous restore and page-cache policy.
- Compiler specialization, operator fusion and target-specific kernel tuning.
- CPU/GPU/DSP/NPU heterogeneous execution.
- Task decomposition and model cascades with calibrated early exit.
- Distillation of larger teacher models into local specialists.
- Adapter selection and conditional computation.

Use the ALEM evaluation: Accuracy/quality, Latency, Energy and Memory. Add storage I/O, thermals, first-useful-action latency, crash/recovery rate and offline availability. Report p50/p95, cold and warm starts, short and long context, and concurrent workloads. A single tokens-per-second number is not an adequate benchmark.

## 6. Implementation sequence

1. **Inventory and observability:** trace every model request, load/unload path, asset path and provider; add per-operation timing, memory, thermal, storage and energy proxies. Establish reproducible baselines on emulator and the actual Realme.
2. **Contracts and truth:** define typed operation/backend/asset contracts and capability states. Remove misleading provider claims; cloud stub must remain unavailable, not masquerade as a real cloud model.
3. **Asset lifecycle:** signed manifest, resumable acquisition, integrity verification, private staging, smoke qualification, atomic promotion, rollback and cleanup.
4. **CWC/AARO first version:** device fingerprint, workload graph, policy-aware planner, NAE integration, one resident path, demand-loaded specialists and safe lifecycle.
5. **Runtime portfolio experiments:** compare llama.cpp, LiteRT/LiteRT-LM, MLC, ExecuTorch and ONNX on the exact models/operators and target hardware. Add GenieX/QNN only if the 695 compatibility probe passes.
6. **Memory/I/O optimization:** paged KV, memory mapping, bounded concurrency, expert paging only for sparse architectures, predictive prefetch and resource-aware cancellation.
7. **Cognitive integration:** connect model operations to Friday's canonical memory and identity state; support model/runtime replacement without identity reset.
8. **Adversarial validation:** corrupt/truncated weights, wrong hashes/signatures, malicious metadata, incompatible tokenizer, missing projector, interrupted downloads, storage exhaustion, process death during promotion, thermal pressure, low memory, backend crash, model mismatch, permission revocation, network loss and concurrent requests.
9. **Device acceptance:** repeat cold/warm, offline and long-soak tests on the owner's exact handset; compare quality and ALEM metrics. Only then promote a runtime/model combination to production.

## 7. Non-negotiable invariants

- Friday's identity, durable memory, executive state and task state never live only inside a model session.
- No model or provider is mandatory for app startup.
- A runtime binary is not a model; a downloaded model is not a qualified capability.
- No asset is loaded before integrity, compatibility and resource checks.
- No partial download is ever executable.
- No automatic downgrade crosses a task's minimum quality/safety threshold silently.
- Model output cannot authorize its own actions or mark its own postconditions verified.
- Network loss cannot reset identity or erase local state.
- A model update cannot destroy the last known-good model or Friday's memory.
- NAE protects the phone and can pause inference; it must not evict identity, memory writes, action verification or recovery control.
- Every optimization is retained only if whole-system measurements show a benefit without unacceptable quality or reliability loss.

## 8. Neural Cell Fabric: residency-free cognitive execution

This is the proposed phone-first invention direction. It is a design hypothesis to prototype, not a claim that RAM can be abolished or that arbitrary model layers are interchangeable.

### 8.1 Change the unit of execution

Conventional model managers treat a model as the unit: select model -> load model -> run model -> unload model. Friday should instead treat a **bounded cognitive operation** as the unit of execution.

Build a small, always-resident **Neural Fabric Microkernel (NFM)** that owns:
- a task/operation scheduler and typed tensor/latent buffer allocator;
- a persistent canonical-state interface (identity, memory references, goals and task checkpoints);
- a hard memory governor and thermal/battery governor;
- a parameter-page broker and activation-page broker;
- a compatibility registry for operators, tensor layouts, quantization and latent-space contracts;
- cancellation, checkpoint, preemption, fault isolation and telemetry.

The microkernel is not a language model and does not contain a giant brain. It schedules short-lived computation over model components kept mostly cold in private storage. It is the only permanently resident inference control plane; neural weights are admitted in bounded pages/windows.

### 8.2 Neural Cell representation

Convert eligible model assets into immutable **Neural Cells**. A cell is the smallest independently schedulable unit that can be executed without violating its source model's mathematics and metadata. Depending on architecture, a cell may be:
- one MoE expert plus its router contract;
- a group of adjacent transformer layers with required norms/attention/MLP weights;
- an encoder/decoder block;
- a vision/audio stem or task head;
- a LoRA/adapter delta attached to a compatible base;
- a small standalone specialist network;
- a quantized matrix/tensor tile where the runtime supports tiled execution.

Every cell carries a signed manifest: source model and revision, exact tensor names/shapes/layouts, required predecessor/successor state, tokenizer or modality contract, precision, quantization scales, operator/kernel dependencies, memory bound, scratch/activation bound, expected latency and quality tests. Cells are never detached from the graph assumptions that make them valid.

**Important:** a transformer layer from Model A is not generally a drop-in replacement for a layer from Model B. Hidden dimensions, normalization, attention geometry, positional encoding, tokenizer, training distribution and residual conventions may differ. Friday may mix raw cells only when their graph contracts are compatible and the composite path has been trained/validated. Otherwise, use an explicit learned adapter/bridge or call the models as separate specialists through a shared typed latent/message contract. Do not splice arbitrary weights.

### 8.3 Three-level residency: cold vault, warm shelf, hot window

- **Cold vault (flash):** all verified model assets and cells reside as immutable, content-addressed files. Nothing is mapped into an inference runtime merely because it exists on disk.
- **Warm shelf (OS page cache / bounded shared buffers):** frequently useful parameter pages and reusable compiled kernels may remain cached when Android permits. Friday treats this as reclaimable and never assumes it is resident.
- **Hot window (strict RAM budget):** only the cells, working activations, KV pages and scratch buffers required for the current micro-operation are admitted. The window is bounded by a measured safe budget with a reserve for Android, the foreground app, voice capture and Friday's critical services.

Use double/triple-buffered asynchronous I/O where supported: while cell N computes, stage the predicted next cell into a separate bounded buffer. On a miss, the scheduler chooses among wait, use a qualified smaller path, recompute from canonical state, or defer; it never exceeds the hard memory cap to preserve latency.

### 8.4 Cross-model Neural Cell Exchange

Create a **Shared Cognitive Representation Bus (SCRB)**. It is a typed, versioned interchange format for results between different models and algorithms—not a claim that their hidden states are naturally identical.

A message contains modality/task type, representation schema/version, dimensions, normalization, semantic contract, provenance, uncertainty, timestamps and source model/cell IDs. Different specialists communicate through this bus. Where hidden-state transfer is desired, train and evaluate explicit projection bridges using paired representations and task-level distillation. Validate for semantic preservation, calibration and distribution shift.

This enables one vision encoder, another model's language reasoner, a specialist emotion/social classifier, a retrieval encoder and deterministic algorithms to cooperate without forcing all their raw layers into one network. Each specialist is invoked only for its operation. Results and evidence are returned to Friday's executive system.

### 8.5 Neural Cell compiler and scheduler

For each task, compile an operation graph, not a model choice:
1. Resolve task outcome, required modalities and minimum quality/safety.
2. Select compatible cells/operators from the local registry.
3. Prune unnecessary branches; reuse cached representations only when provenance and freshness permit.
4. Estimate each operation's peak live memory, page bytes, I/O latency, compute time, thermal cost and confidence.
5. Schedule a memory-safe sequence; overlap storage reads with independent compute.
6. Keep activation lifetimes short; release/reuse buffers as soon as dependency edges permit.
7. Checkpoint only canonical state and compact operation outputs, not entire model sessions.
8. Preempt at safe graph boundaries when voice, device action, foreground UI or Android memory pressure needs priority.
9. Refine the result with optional specialist cells only while budget remains.

The scheduler minimizes peak live memory first subject to quality and deadline constraints, then optimizes energy and latency. This is a graph-liveness and resource-constrained scheduling problem, not just an LRU cache.

### 8.6 RAM isolation and protection

Do not promise "zero RAM". Every active neural operation needs weights, activations and scratch space in some memory tier. The achievable objective is a hard, predictable upper bound on Friday's incremental resident memory and no uncontrolled allocation spikes.

Implement:
- one global inference memory arbiter across every model/runtime; no model may independently allocate an unbounded arena;
- per-operation reservations and admission before execution;
- separate critical reserve for identity, memory journal, action verification, voice buffers and recovery;
- bounded KV/activation pools with explicit ownership and lifetime;
- memory-mapped read-only weights only where the runtime/OS path is proven safe; page cache is opportunistic, not guaranteed;
- pressure callbacks and Android trim handling; stop prefetch, shrink caches, checkpoint, pause or cancel before the OS kills the process;
- native allocation accounting and leak detection; process isolation for untrusted/unstable native backends;
- thermal/battery-aware duty cycling and concurrency limits;
- no simultaneous full residency of multiple heavyweight models by default.

The "insulation" is architectural isolation: heavyweight neural work cannot consume the memory reserved for Friday's identity, continuity, voice/action control or recovery. If pressure rises, inference depth degrades first; the individual state and control plane survive.

### 8.7 Learning which cells are worth keeping warm

Use a private **Cell Utility Learner**. For each cell, estimate reuse probability and the cost of a miss from actual task history, not a static popularity list. Consider recency, task/goal context, time-of-day only if justified, storage read latency, size, load cost, accuracy contribution and thermal impact. Predict likely next cells from the current operation graph and prefetch only if the predicted benefit exceeds I/O, energy and memory opportunity cost.

The learner must be bounded and explainable: cap training state, avoid sensitive-content leakage into telemetry, decay stale patterns, and compare predicted versus actual hits. It cannot override the hard memory budget.

### 8.8 How this addresses "several models without running them all"

- Multiple model assets can be installed in the cold vault without being loaded.
- Multiple models can contribute to one task as short-lived specialists, sequentially or in carefully bounded parallelism.
- Only selected cells/experts/blocks are resident during each operation.
- Shared representations and cached intermediate results prevent needless repeat work.
- A different model's raw internal layers are reused only when mathematically compatible or connected through a validated learned bridge.
- No heavyweight model must remain continuously running just to preserve Friday's identity, memory or continuity.

This is the closest technically honest interpretation of "jump between model internals without running the whole model". It is operation-level execution over selectively admitted components, not arbitrary extraction of intelligence from dormant weights.

### 8.9 Prototype path and falsifiable gates

Prototype in this order:
1. Build an NFM simulator and trace current Friday inference to calculate peak live tensors and memory lifetimes.
2. Add global memory reservations and prove that two independent model requests cannot exceed the configured inference budget.
3. Implement one compatible block/encoder cell format and a cold-vault/hot-window loader; compare exact output against the original whole-model backend.
4. Add asynchronous prefetch and cancellation at safe block boundaries; measure stalls, storage traffic and energy.
5. Add MoE expert paging only for a model that genuinely exposes independent experts and router metadata.
6. Add SCRB between two architecturally different specialist models; verify task quality and calibration against isolated baselines.
7. Test low-memory, process death, storage exhaustion, thermal throttling, corrupted cells, incompatible contracts, simultaneous voice/action/inference and Android memory pressure on the target handset.

Acceptance requires:
- output parity within a declared numerical tolerance for exact cell/block execution;
- no hard-budget violation under adversarial concurrency;
- no loss of identity, memory journal or task checkpoint after inference eviction;
- measured peak PSS/RSS, page faults, storage I/O, cold/warm latency, energy and thermal behaviour;
- demonstrated benefit over loading the whole model for the target workload;
- clear disclosure where flash bandwidth makes paging slower than a compact resident model.

If the target runtime cannot execute a cell independently, keep the smallest valid block as the scheduling unit. Do not fake layer-level loading by reading a layer from disk while the runtime silently retains the entire model elsewhere.

## 9. Research references

- Qualcomm Snapdragon 695 platform specification: https://www.qualcomm.com/smartphones/products/6-series/snapdragon-695-5g-mobile-platform
- Android LiteRT deployment: https://developer.android.com/ai/custom
- LiteRT overview and LiteRT-LM: https://developers.google.com/edge/litert/overview
- Google Play for On-device AI delivery: https://developer.android.com/google/play/on-device-ai
- Qualcomm GenieX (developer preview): https://qualcomm.github.io/GenieX/
- ExecuTorch QNN backend overview and supported SoCs: https://github.com/pytorch/executorch
- On-device LLM compression and systems survey (2026): https://link.springer.com/article/10.1007/s10462-026-11538-1
- EdgeMoE, sparse expert paging and preloading: https://doi.org/10.1109/TMC.2025.3546466
- D2MoE, dynamic expert quantization and scheduling: https://doi.org/10.1145/3680207.3723493
- I/O and KV-cache bottlenecks survey (2026): https://link.springer.com/article/10.1007/s10462-026-11651-1
- mzCache, mobile model/KV memory restoration (2026): https://arxiv.org/abs/2609.01338

Research is not implementation evidence. All third-party runtimes, algorithms, models, licenses and performance claims must be rechecked at the time of integration.

## 10. Elastic MoE Streaming and Self-Optimizing Expert Fabric

This extends the Neural Cell Fabric rather than creating a competing model runtime. The design is inspired by storage-resident sparse experts, but goes beyond fixed expert caching: Friday continuously learns the best safe relationship between routing, storage, cache residency, prefetch, precision, quality and device conditions.

### 10.1 Separate the model into three execution planes

1. **Resident spine:** token embeddings, attention/DeltaNet or other always-used operators, normalization, positional state, routers, shared experts and small control tensors that the exact architecture requires on every token. Keep resident only after measuring their aggregate memory and proving they are actually on the critical path.
2. **Elastic expert field:** routed expert weights stored as immutable, independently addressable extents in app-private storage. Only selected experts/pages are staged into a bounded hot window.
3. **Friday control plane:** a small always-available scheduler, resource governor, model-independent identity/memory interfaces, quality monitor and recovery controller. It remains separate from the neural model and cannot be evicted by expert cache pressure.

Not every MoE has the same structure. Hybrid recurrent/attention models, shared experts, multi-token prediction heads and architecture-specific operators must be described in a model adapter manifest; never infer the decomposition from a parameter count or filename.

### 10.2 Expert Storage Fabric (ESF)

Build a model conversion tool in the development plane that turns a supported source checkpoint into a signed, content-addressed expert package:
- one manifest for architecture, tensor graph, tokenizer, layer order, router semantics, expert count, top-k policy, shared weights, quantization and calibration;
- aligned fixed-stride or indexed expert extents with per-extent hashes, offsets, sizes, precision and tensor layout;
- separate dense-spine and expert regions, plus optional compiled-kernel packages;
- integrity tree / signed manifest, atomic package installation and resumable acquisition;
- locality-aware layout so experts predicted together can be read with fewer storage operations where measurement supports co-location;
- a compatibility map from source checkpoint to the exact runtime adapter and kernel version.

Prefer sequential/coalesced reads where possible, but don't assume fixed-stride is always optimal: expert sizes, alignment, compression and flash translation layers differ. Benchmark packed extents against per-expert files and memory-mapped layouts on the actual Android storage device. Never use unsafe raw block access or require root; use app-private files and supported OS APIs. Direct I/O is an experiment only where Android/NDK support, alignment and filesystem semantics are proven.

### 10.3 Predictive Expert Pipeline (PEP)

At each layer, routing reveals the current token's selected experts. While the current layer computes, PEP can:
- use the current hidden state and prior routing history to predict likely experts in later layers;
- issue bounded asynchronous reads into staging buffers;
- overlap storage I/O with attention, shared-expert and independent compute;
- prioritize experts by probability × miss penalty × quality contribution ÷ bytes/energy;
- cancel stale speculative reads as soon as the actual router output is known;
- batch/coalesce reads when several experts are selected;
- adapt lookahead depth to available storage bandwidth, thermal state, token latency and prediction accuracy.

Prediction is a latency optimization, never a correctness dependency. An incorrect prefetch must only waste bounded I/O; actual router selections remain authoritative unless a separately qualified quality-constrained routing policy is active. The pipeline must avoid read amplification and stop prefetch immediately under memory pressure, thermal pressure or foreground urgency.

### 10.4 Quality-Constrained Cache-Aware Routing (QCAR)

A trained router's choices cannot be casually overridden. Introduce an optional policy layer with three progressively more ambitious modes:
- **Mode 0 — exact routing:** use the model's original top-k experts. This is the correctness baseline.
- **Mode 1 — route-preserving scheduling:** keep exactly the same selected experts, but optimize their cache placement, read order, batching and prefetch.
- **Mode 2 — validated substitute routing:** only where the model exposes calibrated expert similarity/importance or Friday has trained a substitute predictor, permit a cached expert to replace a requested expert when a quality-risk estimate remains within a declared budget.

Mode 2 must be trained/calibrated on representative hidden states and task data; use shadow evaluation and a holdout set. Enforce per-layer and per-task quality budgets, detect distribution shift, and instantly fall back to exact routing when uncertainty is high. Never apply an arbitrary positive cache bonus to router logits: that changes model semantics and can cause silent quality loss. Any route deviation is logged with requested expert, substitute, reason, confidence and measured downstream effect.

### 10.5 Adaptive precision and expert importance

Assign precision per expert or tensor group using sensitivity measurements, not a single global bit width. Frequently used, high-impact or quality-sensitive experts may receive higher precision; low-impact experts may use smaller representations. Use calibration, layer-wise error propagation and periodic full-precision/reference comparisons. Quantization metadata travels with each extent. Re-quantization creates a new immutable version and cannot overwrite the last known-good asset.

### 10.6 Self-optimizing scheduler: the part that makes this improve with age

Friday maintains a bounded workload profile: task family, layer/expert activation statistics, expert co-occurrence, hit/miss rates, storage latency distributions, prefetch accuracy, token latency, energy/thermal observations, memory pressure, output-quality checks and user-approved outcome signals. Avoid storing raw private content when aggregate statistics suffice.

The optimizer generates candidate policies—not arbitrary live mutations—including:
- cache size and replacement policy;
- expert grouping and storage layout;
- lookahead horizon and prefetch thresholds;
- read batching and buffer count;
- per-expert precision;
- model-specific admission and concurrency;
- safe routing substitutions where Mode 2 is supported;
- whether to use the sparse model, a smaller resident model or a specialist cascade for a task.

For each candidate, run deterministic replay and shadow evaluation against a frozen baseline, then bounded on-device canaries. Compare task quality, exact-routing agreement, p50/p95 latency, peak PSS/RSS, page faults, storage bytes/read count, energy proxy, thermal trajectory, cancellation/recovery and foreground responsiveness. Adopt only when the candidate meets all quality/safety floors and improves a declared objective under the same device and workload conditions. Maintain a champion policy, candidate policy, experiment provenance and one-step rollback. The optimizer cannot change weights/router policy and certify its own quality; independent evaluators and held-out tasks are required.

### 10.7 Multi-model pool scheduling

Extend the same storage and resource fabric across multiple independently packaged models. Each model exposes capabilities and costs, not a permanent agent identity. Friday can run one specialist, several sequentially, or a bounded parallel ensemble. The scheduler accounts for shared tokenizer/encoder reuse only when compatible, duplicated dense spines, adapter residency, representation-bridge cost, KV memory, storage contention and task deadline. Prefer sequential short-lived specialists on a constrained phone; parallelism is admitted only when measured total latency/quality benefit justifies peak memory and thermal cost.

### 10.8 Device reality and expected trade-offs

For Friday's initial Realme 9 Pro 5G / Snapdragon 695 target, this is a research/implementation path, not a promise that 35B/80B inference will be practical. CPU/GPU kernel throughput, UFS/flash random-read bandwidth, Android page-cache behaviour, available RAM, KV cache, thermals and background scheduling must be measured on the handset. Apple Swift/Metal results are architectural inspiration, not Android performance evidence. Port the data layout and scheduling concepts to Kotlin/C++/Android NDK and a verified backend; do not port Metal assumptions.

The model may fit in storage while remaining too slow, power-hungry or thermally unsustainable. Establish useful thresholds for time-to-first-token, sustained tokens/sec, task completion, quality, memory, energy and thermal stability. If expert I/O stalls dominate, choose a smaller model, larger bounded cache, better coalescing or a distilled student rather than pretending the scheduler can eliminate the bandwidth limit.

### 10.9 Falsifiable implementation gates

1. Reproduce exact outputs (within numerical tolerance) using original top-k routing and streamed experts on a supported MoE checkpoint.
2. Demonstrate a hard peak-memory cap under long context, cache misses and concurrent voice/action work.
3. Measure cache hit/miss and I/O stalls with cold and warm storage; compare file-per-expert, packed extents and memory-mapped candidates.
4. Demonstrate prefetch overlap and quantify wasted reads; disable it automatically when it harms energy/latency.
5. Prove QCAR Mode 0 and Mode 1 preserve routing exactly; evaluate Mode 2 separately on held-out tasks with explicit quality bounds and automatic fallback.
6. Show per-expert precision reduces storage/memory traffic without violating quality floors.
7. Demonstrate that the self-optimizer discovers a better policy from replay, promotes it through a canary and rolls it back after an injected regression.
8. Repeat on the actual Realme handset, recording Android version, build, free memory, storage, battery, thermal state and competing foreground load.

A successful file load is not an accepted result. Acceptance requires end-to-end output correctness, stable memory bounds, measured resource improvement and no unacceptable quality regression.

## 11. Response-Time and Continuity Architecture

The primary optimization target is not maximum parameter count, model count, or raw tokens/second. It is **time to first useful response, time to first safe action, verified task completion latency, and continuity under changing resources**. A model that produces tokens quickly but blocks interaction, loses task state or causes thermal throttling is not a fast Friday.

### 11.1 Three cooperating execution lanes, one individual

- **Immediate lane:** bounded, predictable, low-memory operations for input framing, turn-taking, interruption/cancellation, session continuity, deterministic checks, capability lookup, safe acknowledgements and eligible low-risk actions. It must not depend on loading a large generative model. It never fabricates deep reasoning or claims an action completed without verification.
- **Adaptive lane:** schedules the smallest set of specialist capabilities that meets the current task's quality and deadline constraints. Specialists may be resident, streamed, short-lived, sequential or bounded-parallel. The scheduler may refine an answer as evidence arrives.
- **Deep lane:** handles expensive reasoning, prediction, research, multi-specialist critique and complex planning. It runs without monopolizing the UI or starving the immediate lane. Work is checkpointed and resumable.

These are scheduling lanes, not three agents, identities or fixed model sizes. All lanes operate on one Friday-owned state and one task identity. A task can move between lanes without restarting the conversation.

### 11.2 Continuity Capsule (CC)

Every live task owns a compact, versioned, model-independent Continuity Capsule. It is not merely a transcript or serialized KV cache. It contains:
- task/turn identity, user intent and material alternative interpretations;
- relevant memory references with provenance, timestamps and confidence;
- facts, constraints, preferences and commitments currently in force;
- unresolved questions, uncertainty and evidence still required;
- actions requested, idempotency keys, execution status and independent postcondition evidence;
- specialist jobs, their inputs/outputs, cancellation tokens and checkpoints;
- current plan frontier, next useful operation and deadlines;
- model/runtime-independent conversation summary and response style state.

The capsule is append/checkpoint based, versioned, checksummed and recoverable after process death. Sensitive content is minimized and stored under Friday's local privacy policy. It supports interruption, model replacement, network loss, process restart and specialist failure. Rehydration must verify schema compatibility and provenance; a corrupt or stale capsule falls back to the last verified checkpoint, never inventing continuity.

KV caches, hidden states, recurrent states and accelerator buffers are optional ephemeral accelerators. They may be reused only when exact model, tokenizer, adapter, context position, runtime and tensor-layout compatibility is proven. The Continuity Capsule is the durable source of task continuity when those caches are evicted or incompatible.

### 11.3 Progressive response without false completion

The scheduler separates interaction acknowledgement from answer correctness:
1. acknowledge input and establish the task capsule within a strict latency budget;
2. classify urgency, risk, expected depth and whether immediate deterministic work is possible;
3. start the lowest-cost qualified path and stream useful partial progress when it is safe and clearly labelled;
4. concurrently prepare deeper specialist work only when the expected benefit justifies its resource cost;
5. merge results through evidence/conflict checks, not majority vote alone;
6. commit the final answer/action result only when required quality and independent verification gates pass.

For actions, preparation may be parallelized, but side effects remain behind authorization and idempotency controls. A fast lane must never execute a duplicate action merely because a deep lane timed out or restarted. If no qualified answer is available yet, Friday says she is still checking rather than inventing certainty.

### 11.4 Resource Envelope Governor (REG)

A device profile is not a fixed RAM number. REG continuously estimates a safe application envelope from available memory, process PSS/RSS, memory pressure, foreground importance, CPU/GPU/NPU availability, storage throughput and latency, battery/charging, thermal headroom, Android lifecycle, current voice/UI/action deadlines and competing workloads.

REG assigns dynamic budgets for:
- resident control plane and immediate lane;
- active model dense spine and bounded expert cache;
- KV/activation buffers and maximum context;
- concurrent model count and specialist queue;
- storage read bandwidth, prefetch I/O and decompression workers;
- CPU/GPU/NPU time slices and thermal duty cycle.

It uses hysteresis and admission control to avoid oscillation. On pressure, shed work in this order where safe: cancel speculative prefetch; stop low-priority deep jobs; evict clean expert/adapter/KV caches; reduce optional context and concurrency; suspend nonessential specialists; switch to a lower-resource qualified route. Never kill the immediate lane or corrupt the task capsule to preserve a model. If the OS kills the process anyway, recover from durable state. The governor must respect Android process and memory policies, not attempt to override LMK or claim privileged memory reservations.

### 11.5 Predictive activation and task-aware model acquisition

Do not preselect a permanent model roster by parameter count. First define cognitive capability requirements and acceptance tasks, then discover compatible models/runtimes/assets that meet them. Model acquisition is a capability-resolution process:
- query an authenticated, provenance-aware catalog for candidate assets and licences;
- evaluate architecture, tokenizer, language, modality, context, tool-use, quantization, runtime and accelerator compatibility;
- estimate download size, storage headroom, peak memory, sustained throughput, latency, energy and thermal cost;
- rank candidates against task quality floors and user network/storage policy;
- acquire only required manifests, tokenizer/metadata and the minimum model components needed for the next qualified capability;
- use resumable, content-addressed downloads, hash/signature verification, atomic activation and rollback;
- retain one known-good fallback and remove unused assets only after dependency and owner-retention checks.

No model is downloaded just because it is popular or large. No required capability is silently declared available before its model/runtime assets are installed and verified. The owner can set Wi-Fi-only, charging-only, storage ceiling and deletion/retention policies. First-run setup should explain unavoidable storage/network choices once; subsequent discovery, compatibility, download, setup, benchmark and update work is automatic within those policies.

### 11.6 Predictive working-set control

Use a multi-timescale cache rather than a single LRU:
- **hot:** components needed now or repeatedly during the active task;
- **warm:** experts/adapters likely to be needed soon, admitted only if predicted stall avoided exceeds memory and energy cost;
- **cold:** verified immutable assets on storage, not resident;
- **discardable:** speculative buffers, old KV state and recomputable activations.

Predictions use recent task context, expert co-occurrence, layer position, confidence, storage latency and miss penalty. Every prefetch has a byte/time budget and cancellation path. Prediction misses cannot change the model's required output. A predictor that increases read amplification, thermal load or foreground latency is disabled automatically.

### 11.7 Multi-model cooperation without model pile-up

The multi-AI pool is a logical pool of available skills and model endpoints, not a requirement to keep multiple full models resident. A specialist may be invoked as a short-lived process, a streamed expert package, a remote optional provider or a non-neural algorithm. Prefer sequential specialists when parallel execution would exceed the resource envelope. Parallelize only independent work whose expected latency/quality benefit is greater than model duplication, IPC, synchronization, KV, memory and thermal costs. Share representations or KV only under proven compatibility; otherwise use the capsule and explicit typed summaries.

### 11.8 Continuity and responsiveness acceptance

Test and report:
- time from input to acknowledgement, first useful response, first safe action and verified completion;
- p50/p95 latency under warm/cold caches, low storage, poor network, long context and competing foreground apps;
- interruption during generation, model switch, expert cache eviction, process death, device restart and network transition;
- recovery of exact task identity, pending work, completed side effects and verified evidence;
- peak PSS/RSS, private dirty memory, storage I/O/read amplification, CPU/GPU/NPU duty, battery and thermal trajectory;
- task quality and false-action rate at every scheduling mode.

Acceptance requires that memory pressure cannot silently discard task continuity; switching or evicting a model cannot change Friday's identity or duplicate an action; the immediate lane remains responsive under bounded deep work; and every claimed performance improvement is measured on target hardware with quality held constant.

## 12. Research-to-device engineering constraints

Android's memory manager reclaims file-backed clean pages and may kill processes under pressure; memory headroom cannot be assumed from installed RAM. Measure PSS/RSS, private dirty pages, low-memory exits, storage latency and thermal state. Use app-private files, supported asynchronous I/O and architecture-specific runtimes. Do not assume that SSD streaming will be fast enough on every handset: random-read throughput, flash translation, page cache, decompression and accelerator upload may dominate token latency.

Use hardware acceleration only after capability discovery and benchmarking. LiteRT provides CPU/GPU execution and NPU paths for supported devices/models; it is a candidate backend for compatible perception/specialist workloads, not a universal replacement for a generative MoE runtime. Runtime selection is per model/operator and must be verified on the actual SoC. Keep the UI, voice turn-taking and immediate control path isolated from long inference jobs.

The initial Realme 9 Pro 5G is the first measurement target, not the universal hardware ceiling. If a model cannot meet quality, latency, memory, energy and thermal floors on that handset, Friday should automatically select a smaller qualified local route, use optional remote compute when permitted, or explain the limitation. Never claim that arbitrary models can be made lag-free by scheduling alone; compute and storage bandwidth remain physical constraints.

Useful platform references:
- Android memory allocation and low-memory management: https://developer.android.com/topic/performance/memory-management
- Android memory-mapping and clean/dirty page behaviour: https://developer.android.com/topic/performance/memory/guide/concepts
- LiteRT Android deployment and accelerators: https://developer.android.com/ai/custom
- LiteRT model/runtime overview: https://developers.google.com/edge/litert/overview
- Survey of MoE inference optimization and expert offloading: https://doi.org/10.1145/3794845

