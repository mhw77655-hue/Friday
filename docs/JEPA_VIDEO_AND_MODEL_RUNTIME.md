# Friday: JEPA, fast cognition, model portfolio, and video intake

Research snapshot: 2026-11-15. English-first phase.

## 1. The actual model strategy

Do not define Friday by a single LLM. Use a model portfolio with interchangeable organs and a runtime broker. Select for task capability, not parameter count alone; promote only after a repeatable evaluation on the target Android device.

### Local multimodal primary candidate: Qwen3.5-4B Uncensored v2

- Candidate: InterpreticAI/Qwen3.5-4B-Uncensored-v2-GGUF.
- Q4_K_M language weights reported at approximately 2.78 GB; its F16 multimodal projector is approximately 0.67 GB.
- Apache-2.0 is reported on the model repository.
- Model card advertises llama.cpp and image-text support. Its reported censorship evaluation is narrow: political-censorship prompts only, excludes capability gaps, and uses an LLM judge. Do not interpret that as a global zero-refusal or intelligence benchmark.
- Alternative: privastudio/qwen3.5-abliterated, about 2.71 GB Q4_K_M plus a BF16 mmproj. It documents source lineage and image projector.

This is the strongest immediate candidate for unified English reasoning + visual capability with an actual GGUF/llama.cpp path. It is not yet verified against Friday's Android JNI build, chat template, mmproj loading, or target phone.

### Mobile-optimized multimodal control candidate: Gemma 4 E2B / E4B

Google positions E2B and E4B for mobile/edge. The family supports image, audio, video and function calling; E2B/E4B include native audio and video. Google's published mobile memory estimates are approximately 1.1 GB for E2B and 2.5 GB for E4B, with text-only estimates of 0.84 GB and 2.2 GB. These are runtime-specific estimates, not guarantees for this app/device.

Use official Gemma 4 as a mobile runtime baseline and candidate sensory organ. It is not uncensored by default. A community abliterated derivative must be evaluated separately and cannot inherit official multimodal/runtime guarantees without validation.

### Research / server candidates, not assumed phone models

- NVIDIA Nemotron 3 Nano 4B is a hybrid Mamba2/Transformer model with vendor-published instruction-following results and a Q4_K_M variant. Its model card says it is optimized for NVIDIA GPU platforms and NeMo/Linux, not ordinary Android CPU inference. Keep it as a cloud/desktop benchmark candidate, not a promised phone install.
- Qwen3.5-9B abliterated Q4_K_M is about 5.63 GB of weights before runtime memory. It may be useful on a high-memory device or server, but is not a sensible always-resident Android default.
- Do not choose a model because its creator calls it uncensored. Abliteration changes refusal behaviour; it does not add reasoning ability and may degrade other behaviour.

### Runtime decision

Friday currently pins llama.cpp with Vulkan disabled in CMake and routes local GGUF inference through a JNI backend. That is a CPU-only path as currently configured. Do not assume it is the fastest available Android route.

Build a runtime broker:
1. LiteRT-LM for supported Gemma/Qwen/etc. models where Android GPU/NPU acceleration is available.
2. llama.cpp for GGUF models and architectures supported by the pinned build, with Vulkan/CPU selected only after device capability checks.
3. A cloud/desktop model provider only when connectivity and user policy allow it.
4. The same Friday identity, memory, continuity, tool permissions and experience state across every backend.

LiteRT-LM is Google's current production orchestration layer for on-device LLMs, with GPU/NPU support, tool use, multimodal embeddings and Gemma/Qwen/Phi support. Google's Android docs say the older MediaPipe LLM Inference API is maintenance-only and recommend LiteRT-LM. Do not add MediaPipe's old API.

## 2. How models run without making the phone unusable

The model file being on storage does not consume its full size as RAM. Once loaded, memory includes weights, KV cache, compute buffers, image/audio encoders, JNI/native allocations, Android app state and other processes.

Use a two-phase admission system:
- Install eligibility: storage space, download size, checksum, license and supported runtime.
- Live admission: current free memory, process PSS, memory class, thermal state, battery, CPU/GPU/NPU availability, foreground/background status and measured latency.

Required safeguards:
- Model weights stay in app-private storage and are not loaded until requested.
- Keep only one heavy model resident by default; unload or evict its KV cache on memory pressure.
- Start with modest context and expand only after measured stable sessions.
- Cap threads and concurrent inference based on device profile; yield to foreground UI/audio and thermal pressure.
- Run no speculative training or model conversion while the user is actively using the phone.
- Keep a hard memory reserve; never rely on Android's low-memory killer as the memory manager.
- Record tokens/sec, time-to-first-token, peak PSS, load time, thermal rise and battery per 1K tokens.
- Roll back an optimization if it causes worse quality, latency, crashes or heat.
- Show why a model was deferred and let the user pause local inference.

The existing PR #11 NAE already samples host resources, records aggregate workload outcomes and adjusts a safety reserve fed into ResourceGovernor. Its known gaps are applying learned context/concurrency/background outputs across every runtime path and doing real hardware-in-the-loop optimization. The separate PR #15 duplicate controller was closed to avoid two competing NAE implementations.

## 3. Video intake from TikTok

There is no discovered ChatGPT plugin that can read a user's private TikTok Saved/Favorites library directly and hand those private videos to the Friday Android app. TikTok's Data Portability API is an account-authorized export/download mechanism, not a general-purpose live API for Friday to browse all saved videos.

The better first-party integration is Android's share contract:
- TikTok Share -> Friday.
- Friday accepts ACTION_SEND and ACTION_SEND_MULTIPLE for video/*.
- Copy only the user-shared URI into Friday's private inbox; no scraping, account password, TikTok cookie or hidden API.
- Extract duration and a sparse set of keyframes locally.
- Later attach audio transcription, OCR, visual scene understanding and temporal reasoning.
- Let the user delete imported videos and their derived frames.

The first intake slice is implemented in this branch: Android share-target intent filters, private video copy, duration extraction, up to 16 sampled keyframes, a Video Library screen, and navigation. It does NOT yet semantically understand the video: the multimodal reasoning backend and frame-to-model input path remain to be connected. The UI says this plainly.

The current video import path uses Android's content URI permission at share time; it does not need TikTok account access. Imported content is kept private to the app. Add deletion/retention controls before calling the inbox complete.

## 4. JEPA and the thinking without thinking future

Interpret JEV as JEPA (Joint-Embedding Predictive Architecture), unless the project chats define a different algorithm. No JEPA implementation was found in the current repository or VISION.md under that exact name.

JEPA is not a chat model. It learns to predict representations of missing/future observations in latent space rather than reconstructing every pixel or token. I-JEPA predicts image-region representations. V-JEPA 2 extends this to video, learning world dynamics and action-conditioned prediction; Meta describes understand/predict/plan and releases research code/checkpoints.

### Friday's intended architecture

- Perception encoder: convert selected video/image/audio observations into compact latent state.
- Temporal state: maintain entities, objects, actions, scene changes and uncertainty over time.
- Predictor: predict likely next latent states and outcomes of candidate actions.
- Prediction error: compare predicted and observed outcomes; large error triggers deeper reasoning and memory updates.
- Experience consolidation: retain useful causal/semantic structure, not every raw frame.
- Fast-path policy: when a prediction pattern has repeatedly succeeded across varied contexts, compile it into a cheap recognition/action prior.
- Deliberative fallback: novel, uncertain, high-impact or contradictory situations invoke slower model reasoning and tool verification.
- Continuity: learned fast paths are attributable to experiences, versioned, reversible and tested with replay controls.

### Thinking without thinking

This should mean cognition becomes cheaper because Friday has learned useful internal predictions and action priors—not that she blindly skips reasoning. A familiar situation can be handled through recognition and a low-cost prediction. If confidence is low, context changed, or consequences are serious, she slows down and reasons explicitly.

A JEPA/world model could supply predictive state, but it is not itself the whole cognitive system and does not prove consciousness. The local phone should not be tasked with training V-JEPA 2 from scratch. Start with a compact, pretrained encoder/predictor or distill a smaller predictive model from a stronger training environment; use on-device experience to calibrate and adapt lightweight adapters/predictors under strict budgets.

### Required Life Tests

1. Prediction: forecast a held-out next event/state and score calibrated uncertainty.
2. Counterfactual: distinguish what would happen under two different actions.
3. Surprise: prediction error causes a durable change in later choices.
4. Fast-path transfer: learned response works in a new but structurally similar situation without replaying the original episode.
5. Deliberation trigger: uncertainty or high consequence invokes slower reasoning.
6. Ablation: disable the learned predictor and show the behaviour changes; block original episode text and show the learned behaviour remains.
7. Continuity: replacing the encoder/model does not erase the learned developmental history.

## 5. Work now present in this branch

- Android share target for one or multiple video files.
- Private video inbox import with a 1 GB per-file limit.
- Local duration extraction and up to 16 evenly spaced sampled keyframes.
- Video Library screen and navigation.
- Research document for English model portfolio, Android runtime strategy, JEPA and thinking-without-thinking.
- Based on PR #11's actual branch, not the stale main snapshot; no changes to main.

## 6. Still required

- Verify Kotlin/Android build and JVM tests on this branch.
- Fix any compile/runtime issues from share intents, media retriever, large-file cleanup and URI permissions.
- Add inbox delete/retention controls and import progress/errors.
- Connect frame sampling to a true multimodal vision-language backend (including mmproj or LiteRT multimodal video).
- Add audio extraction + offline English ASR, OCR, scene segmentation, temporal event tracking and citations to timestamps.
- Add Qwen3.5-4B uncensored as a candidate only after exact JNI + mmproj smoke test; add Gemma 4 LiteRT-LM backend as the mobile acceleration candidate.
- Evaluate Nemotron and other alternatives on a comparable suite; do not assume vendor scores compare across benchmarks.
- Extend NAE so its recommendations actually govern model load/unload, context, KV cache, CPU/GPU/NPU selection, concurrency, voice/vision and idle maintenance.
- Implement JEPA-inspired predictive state and uncertainty model; train/distill compact models off-device initially; then run Life Tests on device.
- Implement earned fast paths (thinking without thinking) with uncertainty-triggered deliberation and reversible learned state.
- Fix legacy voice localhost wiring, offline ASR, identity-consistent voice, visual scene understanding, Reporter integration and Builder's real developmental loop.
- Physical-device verification remains mandatory. No APK or live phone test is claimed by this branch.

## Sources

- LiteRT-LM overview: https://developers.google.com/edge/litert-lm/overview
- LiteRT overview: https://developers.google.com/edge/litert/overview
- Gemma 4 official model overview: https://ai.google.dev/gemma/docs/core
- Qwen3.5-4B Uncensored v2 GGUF: https://huggingface.co/InterpreticAI/Qwen3.5-4B-Uncensored-v2-GGUF
- Qwen3.5-4B Abliterated GGUF: https://huggingface.co/privastudio/qwen3.5-abliterated
- Nemotron 3 Nano 4B model card: https://developer.download.nvidia.com/assets/ace/model_card/Nemotron_3_Nano_4B.pdf
- V-JEPA 2: https://ai.meta.com/vjepa
- I-JEPA: https://ai.meta.com/research/publications/self-supervised-learning-from-images-with-a-joint-embedding-predictive-architecture/
- TikTok Data Portability API: https://developers.tiktok.com/docs/en/data-portability-api-download