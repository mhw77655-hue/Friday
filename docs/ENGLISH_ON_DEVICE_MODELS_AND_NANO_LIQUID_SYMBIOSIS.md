# English-first model portfolio and nano-liquid symbiosis

Research snapshot: 2026-11-15. All model choices below are candidates until the exact files pass the app's native smoke test and real-device benchmarks.

## Model ladder now wired into the offline catalog

### Primary resident: MiniCPM5-2B-Heretic Q4_K_M

- Repository: https://huggingface.co/saidutta69/MiniCPM5-2B-heretic
- Q4_K_M file: `MiniCPM5-2B-heretic-Q4_K_M.gguf`, listed between roughly 1.3 and 1.56 GB depending on the model-card snapshot.
- GGUF quantizations and llama.cpp support are published by the model author.
- The author reports 4 refusals in 100 prompts and KL divergence 0.0646 against the base MiniCPM5-2B. These are creator-reported results, not an independent benchmark.
- The derivative inherits Apache-2.0 from the base model according to its card.
- The catalog pins revision `178a9e6`, validates GGUF magic and minimum size, but has no independently verified file SHA-256 yet.

This replaces the old tiny LFM model as the normal resident candidate. It is not automatically loaded: NAE must admit it first.

### Reasoning: Spark X2.6 4B Abliterated Q4_K_M

- Repository: https://huggingface.co/alexwkleung/Spark-X2.6-4B-Abliterated-GGUF
- Q4_K_M is listed at about 2.42 GiB.
- Uses the `spark2_5` GGUF architecture; the model card requires llama.cpp b10828 or newer. Friday's native pin is newer, but the exact model still needs an Android load test.
- The card says only Q8_0 was evaluated. Do not assume Q4 quality is identical.
- Its advertised million-token context is not a phone setting: the card estimates about 36 KiB/token of f16 KV cache, or roughly 36 GiB for one million tokens. Friday uses NAE-bounded context instead.

### Reasoning fallback: Qwen3.5-2B Uncensored HauhauCS Aggressive Q4_K_M

- Repository: https://huggingface.co/HauhauCS/Qwen3.5-2B-Uncensored-HauhauCS-Aggressive
- About 1.27 GB of weights.
- Apache-2.0 reported by the model repository. The Q4_K_M file has a pinned SHA-256 in the app catalog.
- Chosen only when Spark is absent or cannot be admitted.

### Low-memory fallback: LFM2.5-1.2B Abliterated Q4_K_M

- About 731 MB of weights.
- LFM 1.0 license, not Apache-2.0.
- Retained only as a resident fallback when MiniCPM5 cannot fit the live resource budget.

The ladder is therefore **MiniCPM5 2B → LFM2.5 1.2B** for ordinary resident turns and **Spark 4B → Qwen3.5 2B** for reasoning turns. A reasoning request is never silently downgraded to the tiny resident model.

## Visual models for the TikTok/video organ

These are separate perception candidates, not replacements for Friday's language/reasoning models:

- **Qwen2.5-VL-3B-Instruct-Abliterated GGUF** — strongest low-refusal visual candidate found. Its author says abliteration was applied to the text component only, leaving the image component unchanged. The static Q4_K_M language file is listed around 1.93 GB, plus a separate `mmproj` file. llama.cpp multimodal support is documented from b6907 onward. Friday's pinned llama.cpp is newer, but its current JNI request path is text-only; the model must not be offered as installable until the multimodal JNI path and both-file download/verification are implemented. https://huggingface.co/mradermacher/Qwen2.5-VL-3B-Instruct-abliterated-GGUF
- **SmolVLM2-2.2B-Instruct GGUF** — lighter open visual/video alternative. Q4_K_M language weights are listed around 1.11 GB, with a separate F16 multimodal projector around 872 MB. Apache-2.0; llama.cpp support is documented. It is not an abliterated derivative, so it is an alternative for quality/size benchmarking, not the low-refusal choice. https://huggingface.co/second-state/SmolVLM2-2.2B-Instruct-GGUF
- **V-JEPA 2.1** — the non-LLM world-model direction and the actual JEPA-family video predictor. Meta's official repository lists ViT-B/16 80M, ViT-L/16 300M, ViT-g/16 1B and ViT-G/16 2B variants; 2.1 adds denser, more temporally consistent features. The 80M variant is the first mobile-feasibility candidate, but no Android/TFLite/ONNX runtime or phone benchmark has been validated. Treat the larger models as teachers and distill a compact student rather than loading a 1B/2B teacher on the phone. https://github.com/facebookresearch/vjepa2

The video intake currently stores shared media and extracts keyframes. It does not yet run these visual models, transcribe video audio, or answer questions about the clip. The next implementation gate is a multimodal JNI interface that accepts sampled frames and an `mmproj` file, followed by NAE admission of the combined memory footprint.

## How Friday avoids hurting the phone

NAE is the admission and adaptation authority. It uses live Android host measurements and learned workload evidence to:

1. Sense available RAM, app/process memory, storage, battery, charging, power-save, thermal pressure and CPU load.
2. Estimate runtime working set, not just GGUF file size: model weights, native buffers, KV cache and an Android reserve.
3. Choose the strongest installed model that fits the current operating envelope.
4. Reduce context and CPU threads before rejecting a model.
5. Recheck before every inference turn; unload and pause if the phone becomes constrained.
6. Record model-load and inference outcomes back into NAE so future operating decisions improve.
7. Keep model files in app-owned storage; no all-files-storage permission is needed.

This is a conservative admission system, not a promise of zero impact. Real RAM, thermal, latency and battery measurements on the actual phone are still required. The phone's exact model and RAM have not yet been supplied.

## JEPA and thinking without thinking

Friday now has a production successor-state learner in the same CognitiveEngine turn path:

- It observes coarse interaction features (channel, time band, away/gap band, message kind and length band), not raw message text.
- It records observed state transitions, predicts likely successor states, and calibrates prediction accuracy against a simple baseline.
- The compact history is persisted locally and can be cleared for a person on a broad forget/reset operation.
- Predictions are added to turn diagnostics; they do not currently bypass deliberation or trigger actions.

This is a JEPA-inspired symbolic successor-state substrate, **not the neural V-JEPA 2.1 model**. The future reflex path must only activate after sufficient evidence, calibrated advantage over baseline, low risk, and a verified lower cost than deliberation. It must remain reversible and explainable.

## Video understanding and TikTok

The app now has a native Android share target and a Video Experience library:

- Share a TikTok link or an actual video file from Android into Friday.
- Import a TikTok data-export ZIP and extract saved/favorite video links.
- Store imported media privately, deduplicate by SHA-256, enforce per-file and total-library limits, and extract bounded keyframes.

TikTok's official Data Portability API currently covers users in the EEA and UK; it is not a general API for every country. The import path therefore does not require account scraping or TikTok credentials. A data export generally provides links; to inspect the actual visual content, the video itself must be shared or otherwise lawfully imported.

Keyframe extraction is implemented, but **semantic video understanding, audio transcription, and V-JEPA-style embeddings are not yet integrated**. Meta's V-JEPA 2 is a promising non-LLM world-model direction for learning motion and predicting physical outcomes from video, but an Android-ready, phone-safe distilled runtime has not been validated. See https://ai.meta.com/research/vjepa.

## Remaining verification gates

- Android compile, JVM tests and native CMake build on the consolidated branch.
- Exact GGUF load tests for MiniCPM5, Spark and Qwen on Android.
- Real-device memory/thermal/battery/latency benchmarks and adjustment of NAE thresholds.
- Model chat-template and tool-call validation; model refusal scores do not prove reasoning quality.
- Offline video audio transcription, semantic frame understanding and a distilled predictive visual model.
- A reflex execution gate that uses calibrated predictions without replacing necessary reasoning.
- User-visible NAE adaptation history, reset/disable controls and robust recovery after model-load failures.

Do not call Friday alive, the video reader complete, or the reflex system autonomous until those gates are demonstrated.
