# Friday: quality-preserving compute reduction

Status: research-backed implementation plan; no optimization is considered enabled until it passes the paired quality/performance gates below.

Target: realme 9 Pro 5G, Snapdragon 695 (SM6375), Adreno 619, 8 GB RAM, 128 GB storage. This is a device-specific plan, not a claim that desktop or other Snapdragon benchmarks predict this handset exactly.

## Executive decision

Do not globally shrink Friday's intelligence. Reduce *unnecessary repeated work* first, then apply model compression with measured quality budgets. Keep a stronger path available for difficult or uncertain tasks. NAE is a resource scheduler and learner, not a hidden semantic executive: it may choose a compute envelope, but it must not determine what Friday thinks or who she becomes.

Order of attack:

1. Measure the current CPU-first GGUF path and establish quality/performance baselines.
2. Remove redundant work: cache reusable prompt prefixes where the backend supports it, retrieve compact relevant memories, and avoid serially reprocessing the full history.
3. Route by task difficulty and uncertainty; escalate when the resident answer is insufficient.
4. Test weight quantization variants per exact model/runtime (Q4_K_M vs Q5_K_M vs Q8_0 where available), not by assuming a bit-width is quality-neutral.
5. Reduce KV memory only with backend/model support and position-safe policies.
6. Test speculative decoding only if draft-model overhead is lower than the target-model work it avoids.
7. Consider distillation and structured pruning as later training/build-farm work, not phone-side background training.

## Device implications

The repository's device profile records a close 8 GB Snapdragon 695 / Adreno 619 analogue where Gemma 4 E2B decode was measured at 8.26 tokens/s CPU and 3.11 tokens/s GPU. This is not the owner's exact realme handset, but it is enough to reject the assumption that GPU decode is automatically faster. Keep CPU as the initial default; benchmark CPU and GPU prefill/decode separately before enabling an accelerator.

Do not keep a 4B model resident by default. A model's file size is not its peak working set: mmap pages, runtime scratch, KV cache, tokenizer, JNI/native allocations, and concurrent voice/camera work all matter. Preserve a protected Android/foreground reserve. Keep expensive tasks serialized unless measured evidence proves safe overlap.

## Technique assessment

### 1. Adaptive routing / cascades — first priority

Use a small resident model for ordinary turns, but don't let it silently answer tasks beyond its competence. Escalation signals can include explicit task class, calibrated uncertainty, contradiction checks, tool-verification needs, repeated failed attempts, or a user request for deeper reasoning. A cheap model can draft/classify; a stronger model verifies or solves the hard subset.

Quality rule: evaluate the *whole cascade*, including routing mistakes and fallback availability. A router that saves compute but misses difficult prompts is a quality regression. Never rely on the model's self-reported confidence alone; calibrate signals against held-out tasks and include an "abstain/escalate" path.

### 2. Weight quantization — second priority

Quantization reduces weight storage and memory traffic, but it is not lossless. INT8 is the conservative first comparison; 4-bit formats can offer much larger savings but may hurt reasoning, instruction following, code, or long-form consistency differently. Compare exact runtime-supported formats (GGUF quant types for llama.cpp; supported LiteRT formats for LiteRT-LM). AWQ/GPTQ are not interchangeable labels and their quality depends on calibration, group size, kernels, model architecture, and workload.

Do not convert weights on the phone. Use reproducible external conversion, pin source revision and checksum, and keep the original candidate available. Test the same prompts and decoding settings against the highest-precision reference. If quality falls outside the agreed tolerance on any critical slice, reject that quantization even if it is faster.

### 3. KV-cache management — high leverage, backend-dependent

KV cache grows with context and can dominate memory for long sessions. KIVI demonstrates asymmetric low-bit KV quantization (keys per-channel, values per-token) with strong results in its evaluated server workloads, but those results do not establish support or equivalent quality in Friday's current llama.cpp build. Do not claim KIVI is active until the exact backend implements it.

For long-running conversation, position-safe attention-sink plus recent-window strategies (StreamingLLM family) are safer research candidates than arbitrary token eviction. Naive removal of middle tokens can damage positional coherence and continuity. Prefer summarizing/archiving older turns into Friday's semantic memory, while retaining current task context and exact source references.

### 4. Speculative decoding — conditional experiment

A small draft model proposes several tokens; the target model verifies them. With the correct rejection-sampling algorithm, output distribution can be preserved exactly relative to the target model. But speedup is not guaranteed: on a small CPU-bound phone, running a second model may cost more than it saves. Quantized target kernels may also fail to parallelize verification efficiently.

Only test if the runtime supports compatible draft/target tokenization and verification. Measure acceptance rate, total latency, CPU time and energy. Enable only when end-to-end results beat ordinary decoding on the actual phone.

### 5. Distillation — best route to a genuinely smaller capable organ

Train a smaller student from a stronger teacher using curated Friday-relevant tasks, preference/correction traces, tool-use demonstrations, reasoning outcomes and hard negative examples. Preserve the teacher/reference suite as a quality oracle. Distillation can reduce per-token compute, but it requires a controlled external training environment and a dataset with rights/privacy provenance. Do not train on private conversations by default or perform heavyweight training on the phone.

### 6. Pruning / sparsity — defer

Unstructured sparsity often produces little real speedup unless the target runtime has efficient sparse kernels. Structured pruning can reduce compute but may reduce reasoning capacity and requires careful recovery/finetuning. Recent SLM comparisons find quantization generally preserves fidelity better than pruning, while both can hurt complex reasoning. Do not prune the resident model as an early optimization.

### 7. Runtime and kernel co-design

Benchmark the actual native backend rather than changing frameworks on theory. Keep llama.cpp CPU as baseline on Snapdragon 695. Evaluate LiteRT-LM / Qualcomm delegate only for models and operators supported by the device/runtime, and measure initialization, prefill, decode, energy and fallback behaviour independently. Google documents CPU/GPU acceleration and Qualcomm AI Engine Direct delegates, but delegate availability is device-specific; don't assume the Snapdragon 695 is supported by a newer flagship-only NPU path.

### 8. Memory and scheduling

One expensive foreground inference at a time is the safe initial policy. Coordinate inference with TTS, ASR, camera/video extraction and memory consolidation. Pause/defer background consolidation during speech or active user turns. Unload on-demand models after a measured idle cooldown. Treat thermal state and Android memory pressure as hard admission constraints, not merely performance hints.

## Friday-specific implementation sequence

### A. Instrument before optimizing
Record per turn: model revision/quant, runtime/backend, context tokens, prompt-prefill time, time-to-first-token, decode tokens/s, total latency, process PSS/native peak if available, Android available RAM, thermal status, battery delta, failure/OOM and whether voice/camera overlapped. Store aggregate technical evidence only; never store prompt or transcript text in performance telemetry.

### B. Create a paired quality suite
Use a pinned set of English tasks: factual retrieval with citations, multi-step reasoning, arithmetic, code generation, instruction following, ambiguity/clarification, long-context retrieval, correction uptake, tool selection, refusal/safety boundaries, and conversational naturalness. Include adversarial and difficult cases. Compare exact same prompts, system context, sampling and output budget. Use deterministic decoding where possible and repeated runs for stochastic tasks.

Report quality by slice, not one average. Include factuality/unsupported-claim rate, task success, exact match where appropriate, instruction adherence, regression count and human preference. Do not use the model under test as its own sole judge.

### C. Set explicit gates
For each candidate optimization:
- Quality: no critical-slice regression beyond a predeclared tolerance; no safety or identity-continuity regression.
- Performance: meaningful improvement in at least one of latency, peak memory or energy, without unacceptable regression in the others.
- Stability: no increase in crashes, OOM, thermal throttling or corrupted context.
- Reproducibility: pinned model/runtime/build, repeatable benchmark protocol and stored raw aggregate metrics.

An optimization that does not pass is not selected. Keep a per-device profile and a known-good rollback configuration.

### D. Integrate NAE correctly
NAE should learn a Pareto frontier for each workload class and device state (cool/warm, charging/battery, memory headroom), not a single global "fastest" setting. It may select among already validated model/quant/runtime/context profiles. It must not change semantic policy, skip verification, or silently downgrade a hard task. On uncertainty or low quality, escalate even when that costs more compute, unless the user explicitly chooses a constrained mode.

## Current repository gap to address

The current NanoSymbioticPolicy is a useful substrate: it records aggregate outcomes and admits models against live RAM/CPU/thermal/storage. Its current model working-set estimate uses a generic weight multiplier and fixed context allowance; it is a screening heuristic, not a calibrated peak-memory model. Evidence is currently aggregated broadly, and the policy reduces context after failure streaks. Next implementation should make evidence keyed by exact model revision + quant + runtime + task class, distinguish process PSS from model-native peak allocation, and replace generic estimates with observed upper confidence bounds after successful/failed loads.

The existing Model-Free Device Lab explicitly says it does not measure tokens/s or load weights. Preserve that honesty. Its next step is a real, bounded inference benchmark using a small test model, not relabeling its synthetic CPU/memory test as an LLM benchmark.

## Research references

- On-device LLM compression and systems survey (2026): https://doi.org/10.1007/s10462-026-11538-1
- Revisiting pruning vs quantization for SLMs (Findings of EMNLP 2025): https://aclanthology.org/2025.findings-emnlp.645.pdf
- Qwen quantization benchmark (model/task-dependent quality trade-offs): https://qwen.readthedocs.io/en/v2.5/benchmark/quantization_benchmark.html
- KIVI, ICML 2024: https://proceedings.mlr.press/v235/liu24bz.html
- StreamingLLM, ICLR 2024: https://proceedings.iclr.cc/paper_files/paper/2024/hash/5e5fd18f863cbe6d8ae392a93fd271c9-Abstract-Conference.html
- LiteRT Android runtime: https://developer.android.com/ai/custom
- LiteRT Qualcomm NPU delegate overview: https://developers.google.com/edge/litert/android/npu/overview

## Bottom line

The strongest path for Friday is not "the smallest possible model." It is a quality-gated system: a compact resident model for routine work, selective escalation for difficult work, quantized weights only where paired tests pass, cache/memory discipline, and NAE learning which validated configuration performs best on this exact phone. The goal is less wasted computation, not less intelligence.
