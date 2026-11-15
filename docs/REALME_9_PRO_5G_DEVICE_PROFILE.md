# realme 9 Pro 5G — Friday device profile

Target handset supplied by the owner: realme 9 Pro 5G, 8 GB RAM, 128 GB storage.

## Verified hardware

The manufacturer's MEA specification lists:
- Qualcomm Snapdragon 695 5G (6 nm, octa-core, up to 2.2 GHz)
- Adreno 619 GPU
- 8 GB physical RAM / 128 GB UFS 2.2 storage configuration
- 5,000 mAh battery
- Android 12 / realme UI 3.0 at launch

Official specification: https://www.realme.com/mea-en/realme-9-pro/specs

## Closest measured inference evidence

A reproducible September 11, 2026 LiteRT-LM benchmark measured Gemma 4 E2B on a Samsung Galaxy Tab A9+ with the same Snapdragon 695 / Adreno 619 platform and 8 GB RAM. It is not the owner's exact handset, so treat it as a close hardware analogue, not a guaranteed phone result.

The published mean decode rates were:
- CPU: 8.26 tokens/second
- GPU: 3.11 tokens/second

For this Adreno 619 generation, CPU decode was about 2.65 times faster than GPU decode. GPU prefill was faster, but GPU initialisation/reinitialisation was much slower. The report explicitly concludes that backend selection should be per-device, not an assumption that GPU is always faster.

Source: https://github.com/blyzai/lexa-edge-benchmarks

## Consequences for Friday

1. Keep the current llama.cpp CPU path as the default on this handset. Do not enable Vulkan/Adreno offload merely because a GPU exists.
2. Keep the native runtime in the isolated `:inference` process. Model OOM/crash must not take down the UI, identity, memory, or voice process.
3. Do not keep a 4B model resident. Install it only as an optional on-demand reasoning model and load it only after live RAM/thermal admission.
4. Start with a 2B-class local model as the ordinary resident candidate, but benchmark it on the actual handset before locking the default. The model's disk size is not its peak RAM requirement.
5. Treat the Qwen3.5-4B-Uncensored-v2 GGUF as the strongest researched 4B candidate, not as a proven phone fit. Its Q4_K_M file is 2.78 GB and its separate vision projector is 0.67 GB. Text inference is a plausible on-demand experiment; video/image support is not implemented by merely downloading the projector.
6. Its creator reports that political refusals fell from 47/51 to 7/51 on a limited Chinese-language test. It still refused 93/100 harmful prompts on the BF16 test, and its card reports more fabricated facts than the base model. It is therefore lower-political-censorship, not literally refusal-free or universally uncensored.
7. The current model admission policy uses conservative estimated working-set + reserve calculations. Replace estimates with per-model measured peak PSS/native memory and sustained thermal results as soon as device benchmarks are available.
8. No background training or continuous heavy inference. JEPA/representation training belongs in an explicitly scheduled Forge workflow; on-device Friday should perform low-cost inference, observe outcomes, and persist compact learning evidence.

## Test protocol required before enabling 4B by default

- Record Android build, free RAM, free storage and battery state.
- Benchmark 2B and 4B with the same English prompts, context, output length and sampling.
- Capture process PSS, native peak RSS, first-token latency, decode rate, CPU load and thermal status.
- Run 5–10 minute repeated inference and a 20–30 minute sustained thermal test.
- Repeat while voice playback and video frame extraction are active.
- Keep the model on-demand if it causes foreground latency, thermal throttling, or Android memory pressure.

## Non-negotiable

NAE can schedule resources and learn which configurations work. It cannot manufacture memory bandwidth, erase heat, or guarantee a large model will fit. Friday must degrade gracefully rather than compete with Android or other foreground apps.
