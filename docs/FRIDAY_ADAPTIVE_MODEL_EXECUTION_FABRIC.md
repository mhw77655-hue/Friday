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

## 8. Research references

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
