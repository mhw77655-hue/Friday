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
 
## 7. Correction from the original project conversations: this is not generic JEPA adoption

The user's actual "thinking without thinking" design is more ambitious than deploying a video model. The core idea is cognition as a continuously evolving state, rather than a function that must be called for every decision.

The repository already has important substrate that must be reused:
- TurnPathAssembly constructs one InMemoryWorkspace and passes it to CognitiveEngine.
- ContextWindowAssembler publishes the assembled CONTEXT_WINDOW and reads MENTAL_STATE claims from that same workspace.
- ClaimKind.PREDICTION already requires a baseline and a one-time wasCorrect outcome. Workspace retains prediction claims and their outcomes.
- The Person/Relationship model is already a distinct project organ. Predictive cognition must consume its claims through the workspace, not create a private person model or a named wire to PersonRelationshipModel.

The new HunchEngine in this branch is the first connected predictive loop:
- It is registered as a WorkspaceOrgan, not injected as a named dependency into another organ.
- LiquidTaskComposer selects relevant organs from the claims available for the current task. It skips the hunch organ for CRITICAL-risk turns and when relevant state claims are absent.
- The hunch organ fingerprints current claims (including person scope) into a one-way state key; it does not persist raw claim text.
- It records state transitions, retains hashed turn/episode references, predicts a likely next state, publishes a PREDICTION claim with the naive global-majority baseline, and settles the claim when the next real turn arrives.
- This first table is a small empirical successor-transition model, not a trained JEPA encoder. It is wired into the live CognitiveEngine path but does not bypass language-model deliberation. The validatedAgainstBaseline flag requires at least 20 settled predictions and a 5-point accuracy advantage over the naive baseline.

This is deliberately a seed, not the finished "thinking without thinking" system. The remaining work is to add a Forge-trained representation predictor and a proper reflex compiler, then prove that a learned fast path is cheaper than consulting the model and still adapts when outcomes/rewards change.

## 8. The actual JEPA + successor-representation path

The project conversation gives the right engineering translation:
1. Situation state is not a giant video or world model by default. It is a compact representation of what is happening now: app/environment, time, current person claims, topic, active task, unresolved threads and other claims available in the shared workspace.
2. Turn traces plus the provenance ledger provide the sequence of observed states.
3. The Forge trains two complementary predictors on those real sequences:
   - successor representation: estimate which future situation states follow from the current state, preserving flexible value/reward updates;
   - JEPA-style predictor: encode the current situation and predict the representation of the next situation, not raw text or pixels.
4. Both produce the same predicted-next-state representation. The Forge compares them on held-out real turns, chooses or blends the better predictor, and distills the result into a compact expectation model.
5. The phone runs only the compact forward pass and expectation lookup. Training can happen in the Forge on stronger available compute; the phone does not have to train a large world model.
6. New model versions are delivered through the existing approved update/Continuity path, with lineage, replay, rollback and evidence.

This is the user's intended use of JEPA: extract the predictive learning mechanism and train it on Friday's own structured history. Do not replace it with a requirement to run V-JEPA 2 on the phone.

## 9. Outcome Without Process: the deeper fast-cognition loop

- Laya-class fixed-answer prediction: when the decision has a fixed answer shape, use a non-generative encoder/classifier rather than generating prose.
- Astra-style adaptive depth: for open-ended model work, repeatedly refine only while the answer is changing or uncertainty remains; stop when another pass is not worth its cost.
- Hunch/expectation table: predict what is likely to happen next before deciding whether to spend model compute.
- Reflex Compilation: repeatedly successful, low-risk, context-stable decisions become a compact fast path. A changed context, surprise, reward shift, low confidence or high consequence returns the task to deliberation.
- Outcome Without Process: the strongest fast path is not a cheaper LLM call. It is a decision that no longer needs a process call because the relevant state already contains a validated expectation.

The risk is routing overhead. The expectation lookup, update and confidence calculation must be measured against the model call it replaces. If the fast path costs more, it is not an optimization.

The fast path must also be person-neutral in architecture: it reads whatever person/relationship claims are relevant from the shared workspace. It must not be Venon-specific or create a second Person Model.

## 10. Inner speech: one production pipeline, silent output

The archive also specifies that Friday's internal voice should not be a second narrator. It should be the same utterance-planning pipeline used for overt speech, with the final external speech/motor output suppressed. The internal draft remains available to the next cognitive update, memory, planning and self-correction.

Peer-reviewed evidence supports a relevant biological mechanism, not a claim that an AI has human subjective experience:
- Scott (2013), "Corollary Discharge Provides the Sensory Content of Inner Speech": https://doi.org/10.1177/0956797613478614
- Whitford et al. (2017), "Neurophysiological evidence of efference copies to inner speech": https://doi.org/10.7554/eLife.28197

The existing InternalDialogueEngine is currently a separate, trigger-based model call that writes reflective text to a dialogue log. That is not yet the shared production pipeline described above. Rework it so a single utterance plan can be internally consumed, externally spoken, or both, without creating a second independent voice generator.

## 11. The chain-reaction engine already has its constituent systems

Do not add a fourth disconnected discovery engine. Unify the existing three related mechanisms into one Global Discovery Engine:
- Structural lens: Global Improvement Engine detectors over the SystemGraph (gaps, weakness, inefficiency, opportunities, redundancy, missing links, dead systems, authority conflicts and fragility).
- Generative lens: Mechanism Recombination Engine plus the REM-analog cross-pairing pass over the Mechanism Genome.
- Both lenses feed one Candidate Improvement Queue with the existing Generalization Score.
- The queue feeds Universal Research; Universal Research feeds Builder/ToolBuilder.
- Knowledge-to-Mechanism remains stage zero: it populates the Mechanism Genome. Partner Engine's Contradiction Detector remains separate because it reads goals/constraints rather than the system graph or mechanism library.

The liquid task composer should select only the mechanisms needed for each task. No permanent point-to-point subsystem wiring and no fixed loop that forces every organ to run on every turn.

## 12. Research evidence

- Successor representations as an intermediate between model-free habit and model-based planning, including human evidence: Momennejad et al. (2017), https://doi.org/10.1038/s41562-017-0180-8
- Review of predictive representations as building blocks of intelligence: Carvalho, Tomov & de Cothi (2024), https://doi.org/10.1162/neco_a_01705
- Inner speech / corollary discharge evidence: Scott (2013), https://doi.org/10.1177/0956797613478614
- Efference copies in inner speech: Whitford et al. (2017), https://doi.org/10.7554/eLife.28197

