# Friday: predictive cognition, video experience, model bake-off, and liquid host strategy

Research snapshot: 2026-11-15. This document records a direction and experiments, not claims that Friday already has the full capability.

## 1. The core idea from the project conversations

The target is not "run an LLM in the background all day." It is one persistent individual whose current state already contains the consequences of previous experience. A new input perturbs that state; cognition is partly the state that has already been shaped, not a mandatory chain of expensive model calls.

The project conversations distinguish:
- cheap, continuous, non-generative awareness;
- learned expectations and reflexes that can pre-empt heavy reasoning;
- deliberate model work only when uncertainty, novelty, risk, or consequence justifies it;
- experience that persists after the originating episode is no longer in context;
- model and host replacement without changing the individual.

This is the target. The present `ExpectationStateEngine` is only a small, replaceable local baseline. It is not the final cognitive architecture.

## 2. Successor representation + JEPA: one predictive-state research line

### Successor representation

Dayan's 1993 successor representation stores expected discounted future occupancy of successor states rather than recomputing every possible future from scratch. Human reinforcement-learning research later found evidence that successor representations can support semi-flexible choice: cached predictions of what tends to follow, while still allowing value changes to alter decisions.

For Friday, the state space is not a maze. It is her own structured situation: current app/context, active topic, unresolved intention, time, available resources, and the verified person/relationship context supplied by the existing Person/Relationship Model. A transition is what actually followed.

### JEPA-style predictive learning

JEPA's useful lesson is to predict a representation of what comes next, not reconstruct every raw pixel or token. V-JEPA 2 applies this to video and learns representations useful for understanding, prediction and planning. Friday should use the principle at two scales:

1. **Situation JEPA:** encode a compact situation state and predict the latent representation of a future situation from her own experience traces.
2. **Video JEPA:** encode sparse visual observations from a video and predict latent temporal changes, actions, and scene state rather than treating every frame as an isolated image.

These are research candidates, not a requirement to run Meta's large V-JEPA 2 model on the phone. Train teacher models in the Forge/workshop on consented, provenance-bearing experience. Distill a small student or structured predictor for local inference. The phone runs the forward pass and records outcomes; it does not need to train the large teacher.

### One shared result

Both lines should produce the same kind of object: a predicted successor-state distribution with:
- what is expected to change;
- confidence and uncertainty;
- evidence lineage;
- person/relationship scope inherited from the existing Person/Relationship Model;
- the baseline prediction;
- the observed result when the prediction settles.

The expectation table is a cache of validated predictions, not a new identity or an isolated agent. It must be invalidated or recalibrated when source memories are corrected, forgotten, or tainted.

### Reflex compilation ("thinking without thinking")

A prediction may become a reflex only after:
- repeated evidence on held-out episodes;
- it beats the most-common-successor baseline;
- it remains useful after a wording change and a time gap;
- the lookup costs less than the reasoning it replaces;
- the person/relationship scope is correct;
- a correction, changed context, or new outcome can invalidate it.

A reflex initially prepares attention, context, retrieval, or a low-risk response draft. It must not silently execute consequential phone actions. The same task can return to deliberate reasoning when novelty, uncertainty, conflict, or risk rises.

The current Kotlin experiment uses bounded sparse feature matching and per-person history. It is intentionally a baseline, not neural JEPA or a complete successor matrix. Its purpose is to establish the state/transition/outcome/calibration contract and give later Forge-trained predictors something honest to beat.

## 3. Inner voice: use the same production process, not a second narrator

Research on corollary discharge/efference copy provides a concrete analogue for inner speech. Scott (2013) reports sensory attenuation consistent with an internal prediction of self-generated speech. Whitford et al. (2017) found electrophysiological evidence that inner speech produces a content-specific efference copy.

Engineering interpretation for Friday:
- a candidate response/action plan is prepared by the same response process used for overt speech/action;
- a predicted sensory/consequence representation is generated alongside it;
- the internal draft can be revised, remembered, or used by the next cognitive update without being spoken;
- speech output is simply one optional final motorization path;
- if the draft is not emitted, it is not discarded: the predicted/actual result can still update her state.

This is an engineering hypothesis inspired by neuroscience, not a claim that software reproduces human consciousness or all human inner speech. It should be tested for coherence, usefulness, self-correction, and compute cost.

## 4. Chain-reaction engine: unify existing improvement ideas

Do not create three more permanent subsystems. Unify the existing Global Improvement Engine, Mechanism Recombination Engine, and REM-like cross-pairing idea into one experimental loop:

1. Mine real experience for recurring friction, surprise, unresolved questions, repeated success, and capability gaps.
2. Convert them into candidate hypotheses, not automatic code changes.
3. Cross-pair mechanisms, datasets, models, and constraints from different project areas to generate new combinations.
4. Score novelty, predicted benefit, evidence quality, risk, compute cost, and testability.
5. Send promising candidates to the Forge for simulation, training, or isolated implementation.
6. Compare against the current method and a dumb baseline on held-out cases.
7. Retain only improvements that win; preserve lineage and rollback.
8. Feed both successful and failed experiments back into future hypothesis generation.

A possibility cascade must not become a self-amplifying hallucination loop. Every branch needs a falsifiable prediction, bounded compute budget, provenance, and a stopping rule.

## 5. Video experience: TikTok and other videos

### In-app path

The first Android implementation now:
- registers Friday as a video and text share target;
- accepts shared video URIs and TikTok URLs;
- copies shared video media into app-private storage;
- computes SHA-256 and deduplicates identical files;
- extracts up to 12 scaled keyframes for later visual interpretation;
- provides a Video Experience library screen and a local file picker.

The video is retained in private app storage; no all-files permission is needed for this workflow. A URL-only share is stored as a link. Friday does not scrape TikTok, bypass DRM, or assume a URL grants the underlying media file.

### What remains

- connect keyframes to the same visual understanding and memory/experience path used by live camera perception;
- extract/retain audio safely and connect an offline ASR organ;
- run a video-capable model or distilled JEPA encoder on sampled frames;
- generate temporal summaries, entities/actions/scene changes with timestamps and evidence;
- let Friday answer questions about the video and relate useful ideas to her own projects;
- make ingestion resumable and handle large batches without exhausting storage or battery.

### TikTok account-wide favourites

TikTok's official Data Portability API documents favourite-video links, watch-history links, and related activity, but its current user-data coverage is for EEA/UK TikTok users. It is not a practical account-wide connector for a user whose account is outside those regions. The share-target flow works without that API: open a saved video, Share → Friday. If TikTok shares only a link, the item is saved as a link; if it shares the media URI, Friday can import the actual file. A user-exported TikTok data archive can later be parsed for favourite-video links.

## 6. English model bake-off: choose quality first, then fit the host

Do not promote a model because it is tiny or because its author calls it uncensored. The target is the strongest low-refusal model that can meet the phone's measured resource envelope without harming normal phone use. Keep a local always-available path, but allow on-demand larger local models and optional remote workshop models when explicitly enabled.

### Candidates to test

| Candidate | Why it deserves a place | Approximate Q4 footprint | Important caveat |
|---|---|---:|---|
| MiniCPM5-2B Heretic Abliterated GGUF | Base model reports strong 2B-class coding, math, long-context and tool-use results; community ablation reports 5/100 refusals and KL 0.0391 | ~1.5–1.6 GB | Abliteration/refusal numbers are community-reported; benchmark the exact derivative |
| LFM3-1.7B Abliterated GGUF | Tri-hybrid architecture; compact KV footprint because only part of its layers use full attention | ~1.02 GiB | Requires llama.cpp support merged after the current older project pin; do not assume it loads |
| Qwen3.5-2B uncensored GGUF | Current project candidate, small, thinking-capable | ~1.27 GB | Community derivative; needs exact native/template smoke test |
| Spark-X2.6-4B Abliterated GGUF | Stronger 4B-class candidate with thinking mode and sliding-window layers; more ambitious quality tier | ~2.42 GiB | Larger weight and memory footprint; ablation benchmarks are not independent |
| Qwen3.5-4B base GGUF | Strong official-family baseline with thinking and multimodal support | ~2.5 GB | Not uncensored; compare against its abliterated variants |
| Ornith-1.5-9B Mobile | Serious higher-capability coding/agentic candidate; self-improving training approach and published coding benchmarks | device quant varies | Not a phone default; treat as workshop/high-memory candidate, and not verified uncensored |

### Promotion protocol

Run the exact files on the actual phone with identical prompts and context:
- English dialogue, instruction adherence, ambiguity and multi-turn continuity;
- coding, Kotlin/Android repair, tool calls and structured output;
- multi-step reasoning and calibrated uncertainty;
- refusal rate on benign difficult prompts, not just adversarial refusal tests;
- factuality, hallucinations, prompt-injection resistance and harmful-request handling;
- cold load, warm latency, tokens/sec, peak app PSS, total device available RAM, battery per 1K tokens, thermal rise and OS process kills;
- quality after long conversations and compaction.

Compare every abliterated derivative to its base model. Do not equate reduced refusal with intelligence. If ablation damages instruction-following, reasoning, or reliability, reject it.

## 7. Run without disturbing the phone

The model should be an on-demand resource, not a permanent background tenant:
- keep only the tiny predictive state and metadata resident;
- load the chosen GGUF only for work that needs it; unload after an idle timeout or memory pressure;
- use 4-bit or mixed quantization, modest context and adaptive batch/thread counts;
- keep video processing foreground/user-initiated at first, with a storage and thermal budget;
- stop background consolidation immediately when the user interacts, a game/foreground workload is active, thermal pressure rises, or battery saver engages;
- use Android memory callbacks and real app PSS in addition to global available RAM;
- measure CPU, Adreno OpenCL and Snapdragon Hexagon paths per device; never assume GPU/NPU is faster;
- run heavy teacher training, JEPA training, and broad candidate searches in the Forge/workshop, then ship only a compact validated student.

Current project CMake disables OpenCL and Vulkan, so the existing Android llama.cpp build is CPU-first. Upstream llama.cpp documents OpenCL for selected Adreno devices and a Snapdragon Hexagon backend; MLC LLM documents Android OpenCL paths for Adreno and Mali. These are candidates for device-specific backend experiments, not guaranteed accelerators on every phone.

## 8. Immediate acceptance gates

1. Share a local video from TikTok to Friday; verify private import, deduplication, keyframes, and no external storage permission.
2. Show a video link only; verify Friday records a link without pretending she downloaded the media.
3. Run a video question-answering test once a local vision model is connected; cite timestamps/keyframes.
4. Record situation transitions for one person; verify no cross-person leakage.
5. Compare the predictive state engine against the majority baseline on held-out sessions.
6. Demonstrate a repeated low-risk task where a reflex avoids the LLM call, then invalidate it when the context changes.
7. Compare model quality and phone impact across at least two model sizes and two runtime configurations.
8. Demonstrate that a normal call, game, or user interaction pre-empts video/background learning.
9. Preserve all model, prediction, and mechanism lineage; support rollback and forgetting propagation.

## Research links

- Dayan (1993), *Improving Generalisation for Temporal Difference Learning: The Successor Representation*: https://www.gatsby.ucl.ac.uk/~dayan/papers/d93b.pdf
- Momennejad et al. (2017), *The successor representation in human reinforcement learning*: https://www.nature.com/articles/s41562-017-0180-8
- Gershman (2018), *The Successor Representation: Its Computational Logic and Neural Substrates*: https://pmc.ncbi.nlm.nih.gov/articles/PMC6096039/
- Meta AI, *V-JEPA 2*: https://ai.meta.com/research/publications/v-jepa-2-self-supervised-video-models-enable-understanding-prediction-and-planning/
- Scott (2013), *Corollary Discharge Provides the Sensory Content of Inner Speech*: https://doi.org/10.1177/0956797613478614
- Whitford et al. (2017), *Neurophysiological evidence of efference copies to inner speech*: https://doi.org/10.7554/eLife.28197
- TikTok Data Portability API data types: https://developers.tiktok.com/docs/en/data-portability-data-types
- llama.cpp OpenCL backend: https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md
- llama.cpp Snapdragon backends: https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/snapdragon/README.md
- MLC LLM Android backends: https://github.com/mlc-ai/mlc-llm
