# Model-free Device Lab

## Purpose

The lab screens model candidates on the actual handset before Friday downloads model weights. It is an Android in-app capability, not a cloud plugin and not an inference engine.

## Measurements collected on the phone

- SoC model/hardware identifier, CPU core count, ABI and Android API level.
- Total and currently available RAM from Android ActivityManager.
- Android app memory class.
- Free app-volume storage.
- Battery percentage and charging state.
- Android thermal status.
- Optional bounded synthetic CPU integer workload, 16 MiB page-touch test, and 4 MiB sequential cache-file write/read.
- App PSS before/after the optional synthetic test.

Only these technical measurements are persisted to app-private files/model-lab/device-profile.properties. No prompts, user files, video frames, transcripts, app list, or personal content is read or uploaded.

## Persistent host study (NAE)

The Android host adapter now records a device-specific, content-free study alongside workload evidence. Its fingerprint uses manufacturer/model/device/board/SoC model, Android API, total RAM, app memory class, CPU core count and primary ABI. It deliberately does not read serial numbers, installed-app lists, user files, prompts or transcripts.

The study accumulates sample count, minimum observed available RAM, maximum and exponentially smoothed CPU load, thermal-pressure sample count, low-memory sample count, minimum observed battery level, maximum observed process PSS, and last observation time. It is restored only when the current hardware/software fingerprint matches; a different host starts a clean profile.

Sampling is bounded to the existing 15-second observer, with persistence throttled to at most once per minute. PSS is explicitly a process-wide footprint estimate, not a per-model allocation. Repeated low-memory and thermal-pressure observations feed decaying pressure EMAs; once sustained pressure crosses the policy threshold, NAE adds 256–512 MiB of conservative admission headroom. That extra reserve decays again as healthy observations arrive, so a single bad moment does not permanently cripple the device profile. This is a bounded self-reconfiguration of model admission and scheduling, not a claim that Friday can alter Android's kernel, clocks, or hardware. These statistics are observations for calibration; they do not yet constitute an inference benchmark or automatically prove that a model/backend is faster.

## What it does not do

- It does not download, map, or load any model weights.
- It does not report model tokens/second.
- It cannot know exact architecture-specific KV-cache, scratch-buffer, mmap, allocator, GPU delegate, or runtime overhead without model-specific measurements.
- It cannot guarantee an inference model will load or run stably.
- It does not benchmark GPU/NPU kernels yet.

The synthetic test is intentionally short and bounded: approximately 450 ms of integer CPU work, 16 MiB touched memory, and 4 MiB storage I/O. It is not a stress test and should not be interpreted as an inference benchmark.

## Candidate screening

Initial candidate catalog:
- LFM2.5-1.2B-Instruct Q4_K_M: approximately 731 MB GGUF.
- Qwen3.5-2B Q4_0: approximately 1.21 GB model package; LiteRT-LM and GGUF distribution paths need runtime-specific validation.
- Gemma 4 E2B: approximately 2.58 GB quantized package; LiteRT-LM candidate.
- Qwen3.5-4B Uncensored v2 Q4_K_M: approximately 2.78 GB weights; separate vision projector not included.

The estimates use weight-file size, a conservative runtime scratch allowance, context-dependent cache allowance and a protected Android/foreground reserve. The cache allowance is an architecture-independent screening heuristic, not a model-specific truth.

Verdicts:
- Likely fit: estimated working set is below current available RAM after reserve and additional headroom.
- Conditional: borderline; try lower context or a quieter device state.
- Not recommended: estimate exceeds safe headroom or the phone is currently thermally/memory constrained.

The screen must show all assumptions and uncertainty. It must never call a candidate verified until actual model load and inference tests pass.

## Calibration roadmap

1. Collect baseline snapshots under normal, charging, battery-saver, warm and cool states.
2. Run the synthetic test more than once and retain medians, not a single lucky sample.
3. Add runtime-specific architecture metadata: layers, KV heads, head dimension, cache dtype, mmap behaviour, context and scratch allocation.
4. Compare predictions against actual model load PSS, native peak RSS, load time, first-token latency, decode speed, sustained thermal state and battery drain.
5. Fit per-device/per-runtime error bounds. Prefer false negatives (reject a borderline candidate) over false positives (load a model that destabilizes the phone).
6. Add GPU/NPU probes only through supported APIs and measure initialisation cost, prefill and decode separately. The Snapdragon 695 / Adreno 619 analogue benchmark shows that GPU decode can be slower than CPU decode on this generation.
7. Use the calibrated device profile to choose the best model and runtime; do not hard-code GPU first or largest model first.

## realme 9 Pro 5G target

The target is Snapdragon 695, Adreno 619, 8 GB RAM and 128 GB storage. A separate 8 GB Snapdragon 695 / Adreno 619 Android device measured Gemma 4 E2B at about 8.3 tokens/s CPU and 3.1 tokens/s GPU decode in one published benchmark. This is a hardware analogue, not a measurement of the owner's handset.

Initial policy: CPU-first GGUF inference, isolated process, one expensive workload at a time, at least 900 MiB RAM reserve, short initial context, and optional 4B models only after a live admission check and explicit on-device benchmark.

## Decision

The lab is a screening and calibration instrument. Its first output is a device-specific resource profile, not a false promise that synthetic arithmetic predicts a model's exact speed. The eventual goal is to learn from actual model runs and make model admission increasingly accurate over time.
