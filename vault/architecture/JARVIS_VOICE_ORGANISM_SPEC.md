# JARVIS — Voice Organism Research & Engineering Specification

**Document type:** Universal-research output + subsystem specification for the Voice capability.
**Scope:** Voice I/O as a JARVIS capability organism — perception (STT/VAD/KWS), synthesis (TTS), identity (voice profiles), and their execution environments. The document records the complete research path: what Voicebox (the research seed) does, what JARVIS already does, the genuine gaps, the cross-domain mechanisms that solve them, and the Jarvis-native organism that results.
**Method:** Universal Research workflow. The Voicebox archive (`vault/just_downloaded/voicebox-main.zip`, extracted to a disposable `/tmp/vb-research` environment) was studied as *research material only*. No Voicebox code is ported, installed, or made a dependency. No Voicebox desktop architecture is reproduced. Its mechanisms were extracted, compared against JARVIS, and re-derived into Jarvis-native designs.
**Status:** Research complete. Gap set and organism design specified. Implementation is a downstream phase, gated by the promotion gate in §13.

---

## 0. Executive Findings

1. **JARVIS's voice pipeline is retired because native inference crashes the host.** Commit `d0735ac` ("Disable voice pipeline — confirmed source of repeated native crashes") and its root-cause commit `6a5df37` ("blocking TTS was starving other native engines") document the failure. The `capability_manifest.json` still reads `"voice_pipeline_v1": "retired, native mutex crash root-caused"`. **The single most important mechanism Voicebox demonstrates is that it runs every native voice engine in a separate process (a FastAPI server), never in the host. JARVIS runs them in-process.** Process isolation is the fix for the crash class that retired JARVIS's voice.

2. **JARVIS already possesses the entire nervous-system framework the directive describes — it is simply unwired.** `CapabilityRegistry` (with `STT`, `TTS`, `VAD`, `WAKE_WORD` categories), `CapabilityArbitrator`, `MicroSystemContract`, `OrganismLifecycle`, `CapabilityRouter` (cheapest-valid + fallback chains), `UniversalResearchEngine`, `MutantEnvironment` (disposable candidate habitats), `PromotionController`, `RollbackController` — all exist as code. A `JARVIS_PHASE1_CAPABILITY_MATRIX.md` even declares the intended voice capability surface (`tts.piper_en`, `tts.piper_ar`, `stt.arbitrator`, …) with Piper models already downloaded. **What does not exist is a VoiceOrganism that plugs into this framework, and a mutated environment for voice that isolates its native code.**

3. **The capability matrix declares Piper voices that are not implemented.** `tts.piper_en` / `tts.piper_ar` are designed with models already on disk (`en_US-lessac-medium.onnx`, `ar_JO-kareem-medium.onnx` in `vault/just_downloaded/`), but no Piper implementation exists anywhere in the codebase. This is a genuine gap: a designed-and-downloaded capability with no implementation.

4. **JARVIS has sophisticated-but-dead voice scaffolds.** `TTSEngine`/`TTSEngineRegistry` (TTS provider abstraction with engine selection), `STTArbitrator` (confidence-based escalation across Vosk → Sherpa-Zipformer → Sherpa-Whisper → Platform-Arabic) — both complete and unreferenced. The live path today is `BodyCoordinator` → `JarvisVosk` + `JarvisSherpaWhisper` + `JarvisTts`/`StreamingTts`, all Google-TTS/Vosk/Whisper on-device, in-process.

5. **JARVIS has a voice-identity gap.** Voice selection is "best quality for the language." There are no voice profiles, no persona→voice mapping, no cloning, no voice effects, no multi-sample voices. HumanCore gives JARVIS full text-level personality/relationship/expression (more advanced than Voicebox's personality rewrite), but `ProsodyHint` in the HumanCore protocol is a placeholder that is always null — the text-to-voice bridge does not exist.

6. **JARVIS's STT architecture is *more* sophisticated than Voicebox's.** Voicebox picks one Whisper backend by platform (MLX vs PyTorch). JARVIS has a confidence-based escalation chain across four recognizers plus a language router (en/ar code-switching). The gap is not the STT arbitration — it is that the escalation chain is dead code and the underlying native engines crash in-process.

---

## 1. Phase 0 — Research the Seed (Voicebox Mechanism Study)

The Voicebox backend (`backend/`, 120 Python files) was studied as a mechanism inventory. Each item below is a *mechanism* extracted for gap analysis — not code to copy.

### 1.1 Backend abstraction & registry

- **Protocol abstraction:** `TTSBackend`, `STTBackend`, `LLMBackend` are `@runtime_checkable` Protocols (`backend/backends/__init__.py`). Every engine implements `load_model / is_loaded / unload_model / generate | transcribe`, plus engine-specific `create_voice_prompt / combine_voice_prompts`. `runtime_checkable` means any object with the right shape satisfies the protocol — engines are never required to share a base class.
- **Declarative model registry:** Each backend class declares `MODEL_CONFIGS: list[ModelConfig]` — a dataclass carrying `model_name, display_name, engine, hf_repo_id, model_size, size_mb, needs_trim, retries_runaway, supports_instruct, languages`. This is provider *capability metadata*: language coverage, failure-mode flags, instruct support, download size are all first-class data, not code.
- **Double-checked-lock singletons:** Engine instances are held in `_tts_backends` (per-engine), created lazily under a `threading.Lock` with a lock-free fast path — the classic lazy-singleton pattern, per engine.
- **Backend-type selection:** `get_backend_type()` returns `"mlx"` on Apple Silicon (if `mlx.core` imports) else `"pytorch"`. One platform probe decides the whole inference stack (TTS, STT, LLM share the same runtime family).

### 1.2 Model lifecycle

- **Lazy load on first use:** every backend's `generate()`/`transcribe()` checks `is_loaded()` and, if not, pushes the heavy synchronous loader onto a worker thread (`asyncio.to_thread`). Load is deferred until the first real request.
- **Progress context:** `model_load_progress(name, is_cached)` (in `backends/base.py`) is a context manager that (a) patches `tqdm` to forward HF download progress, (b) drives a shared `ProgressManager` + `TaskManager` ("downloading"/error/complete), (c) re-raises errors while marking both managers, (d) tears down the tqdm patch on exit. One pattern, shared by every backend.
- **Cache check:** `is_model_cached(hf_repo)` inspects `HF_HUB_CACHE/models--<repo>`, rejects any `*.incomplete` blob (download in flight), and requires a weight file (`.safetensors`/`.bin`, MLX adds `.npz`).
- **Unload:** `unload_model()` does `del` + `torch.cuda.empty_cache()` (or XPU). Per-engine: Qwen family and TADA support **size switching** (`_current_model_size`) — switching 0.6B↔1.7B unloads then reloads.
- **Deterministic seeding:** `manual_seed(seed, device)` seeds CPU + CUDA/XPU (deliberately not MPS) so the same (text, seed) pair is reproducible.

### 1.3 Voice prompt caching & cloning

- **Content-addressed cache key:** `MD5(audio file bytes + reference text utf-8)` — the key is *content-based*, so any change to the reference audio or transcript invalidates the cache automatically. Cache hit returns a dict of already-encoded tensors; miss re-encodes.
- **Disk + memory:** prompts are `torch.save`d to `cache_dir/<key>.prompt` (with `weights_only=True`) *and* held in an in-memory dict. Corrupt files are deleted and re-encoded. No size limit / LRU; a `clear_voice_prompt_cache()` wipes both.
- **Engine-specific prefixes:** LuxTTS uses `"luxtts_"+md5`, TADA `"tada_"+md5`; Qwen/MLX/PyTorch/Chatterbox share one key space, meaning *one prompt encoding serves multiple Qwen-variant engines*.
- **What is actually encoded** varies by engine:
  - *Kokoro:* no cloning — `create_voice_prompt` returns a static preset `{"voice_id": "af_heart"}`; "voice" is a pretrained style `.pt` vector picked by name.
  - *Qwen CustomVoice:* stub returning a default speaker; input = `text + language + speaker(+instruct)` as one string.
  - *Chatterbox:* prompt is `{ref_audio, ref_text}` — never cached; the model re-reads the audio file at generation time via `audio_prompt_path`.
  - *LuxTTS:* `encode_prompt(audio, duration=5, rms=0.01)` runs its **internal Whisper ASR** to derive text embeddings; the reference text is only in the cache key, never given to the model.
  - *TADA:* encoder does forced alignment under `torch.inference_mode()` (autograd across the DAC/Snake stack ballooned VRAM — issue #890) → `EncoderOutput` of 1:1 token↔audio embeddings, serialized to CPU tensors, cached, rebuilt to model dtype at generate.
  - *Qwen/PyTorch Base:* `create_voice_clone_prompt(ref_audio, ref_text, x_vector_only_mode=False)` → cached prompt dict.
- **Multi-sample combination:** N samples → load each, normalize, `np.concatenate`, `" ".join(texts)`, save `combined_<profile>_<md5>.wav`, then encode the combined file. So multi-sample voices are *concatenated audio + concatenated text*, producing a longer, richer conditioning signal.

### 1.4 Chunked long-form synthesis & crossfade

- **Sentence-boundary splitting** (`utils/chunked_tts.py`): priority = sentence-end (`.!?` skipping abbreviations like `Dr.` and decimal periods) → clause boundary (`;:,—`) → whitespace → hard cut. CJK punctuation (`。！？`) supported; paralinguistic `[tags]` are atomic (never split).
- **Short-text fast path:** text ≤ `max_chunk_chars` (default 800, configurable 100–5000) uses the single-shot path with zero splitting overhead.
- **Per-chunk generation with seed perturbation:** `chunk_seed = seed + i` — deterministic but decorrelated across chunks.
- **Crossfade** (`concatenate_audio_chunks`): linear fade-out/fade-in over `crossfade_ms` (0–200ms) at chunk boundaries to kill clicks. Default 50ms.
- **Runaway detection & retry:** `has_tts_runaway` (in `utils/audio.py`) frames audio at 20ms, computes RMS vs −40dB, and returns True on the classic **EOS-miss signature: speech → ≥2s internal silence → more speech** (codec noise after the model missed its end token). When flagged, the chunk is **split in half and retried** up to `MAX_RUNAWAY_RETRIES=2` with varied seeds; persistent failure raises rather than returning corrupt audio. Only enabled for MLX Qwen TTS.
- **Output trim:** `trim_tts_output` cuts at the first internal silence gap > 1000ms and trims trailing silence to a 200ms tail with a 30ms cosine fade — specifically for Chatterbox's `[speech][silence][hallucinated noise]` shape.

### 1.5 Effects pipeline

- **DSP library:** Spotify `pedalboard`. Registry in `utils/effects.py` with per-param `{default,min,max,step}`: chorus, reverb, delay, compressor, gain, highpass, lowpass, pitch_shift (±12 semitones).
- **Chain application:** `apply_effects(audio, sample_rate, chain)` reshapes mono→`(channels, samples)` float32, runs `board(audio, sr)`, restores dimensionality. Invalid chains are validated and skipped.
- **Presets:** 4 built-in (`robotic`, `radio`, `echo_chamber`, `deep_voice`) + user presets (CRUD, unique name, built-ins immutable). **Per-profile defaults:** `VoiceProfile.effects_chain` (JSON text) applied when the request omits a chain.
- **Versioning:** a clean "original" is always saved; if effects apply, a processed version is saved and becomes default; invalid chains fall back to the original.

### 1.6 Voice profiles & identity

- **Three `voice_type`s** (`database/models.py`): **cloned** (reference audio; engines = qwen/luxtts/chatterbox/chatterbox_turbo/tada), **preset** (prebuilt engine voice — Kokoro 50+ and Qwen CustomVoice 9 curated, metadata-only), **designed** (text-described voice → Qwen CustomVoice path).
- **Profile row:** id, name, description, language (default `en`), avatar_path, `effects_chain` JSON, `voice_type`, `preset_engine`, `preset_voice_id`, `design_prompt`, `default_engine`, **`personality`** (free-form character text), timestamps. N `profile_samples` rows (audio_path + reference_text).
- **Engine resolution precedence:** request engine → profile.default_engine → profile.preset_engine → `"qwen"`. **Language does not auto-select an engine** — language is a stored preference passed through to TTS; the caller picks the engine.
- **Personality is orthogonal to sound:** it drives the compose/rewrite LLM layer, not the voice itself.

### 1.7 Voice personality layer (local LLM)

- **Shared Qwen3 LLM** (MLX 4-bit quants on Apple Silicon, PyTorch 0.6B/1.7B/4B elsewhere) — the *same* runtime family as TTS/STT, one model cache, one GPU footprint. No extra download.
- **`compose_as_profile`:** no input; system = short character-framing + personality + compose task; trigger `"Speak."`; temp 0.9 (variety), max_tokens 256 → a fresh in-character line.
- **`rewrite_as_profile`:** pre-cleans input via `collapse_repetitive_artifacts`, system = framing + personality + rewrite task ("restate every idea, change wording, add nothing"); temp 0.3 (fidelity), max_tokens 1024. Wired into `/generate` when `personality=true` and the profile has a personality.
- **Framing rule:** prompts are deliberately short because small models (0.6B) degrade with long system prompts.

### 1.8 STT / transcription

- **Platform-selected Whisper:** MLX (`mlx_audio.stt`) or PyTorch (`transformers.WhisperForConditionalGeneration`); sizes base/small/medium/large/turbo mapped via `WHISPER_HF_REPOS`. Same lazy-load + cache + progress machinery as TTS.
- **Language handling:** `decode_options["language"]` (MLX) or `forced_decoder_ids` (PyTorch); omitted → auto-detect. Audio re-encoded to 16kHz for PyTorch.
- **Download-or-202:** REST `/transcribe` returns 202 with a "downloading" body and kicks off a background download; MCP `_transcribe_file` instead raises a helpful error if the model is undownloaded.
- **Refinement:** `refine_transcript` collapses repetitive artifacts (≥6-run word loops, 2–60-char substring loops — Whisper hallucination loops) then sends to the local LLM with **flag-driven system prompt sections** (`smart_cleanup`, `self_correction`, `preserve_technical`). **Few-shot examples are passed as real chat turns, not inline system-prompt text** — inline examples made small models pattern-match/echo; chat turns read as prior conversation. The last slots pin the hardest rules (self-correction, which 4B silently flips without a demo).

### 1.9 Concurrency, scheduling & failure

- **Serial generation queue** (`services/task_queue.py`): a single async worker processes generation jobs one at a time to **prevent GPU contention**. Cancellation is split: a queued job is dropped with a cancelled-set marker; a running job gets `task.cancel()`.
- **Orphan recovery:** startup SQL flips `status IN ('generating','loading_model')` → failed with "Server was shut down during generation"; the worker also `_force_fail_if_active` flips rows when a gen coroutine dies without writing a terminal status (SQLite-lock race).
- **Event pub/sub:** `events.py` is an in-memory per-subscriber `asyncio.Queue(maxsize=64)`, drop-oldest on full; publishes `speak-start` / `speak-end` for the pill overlay. Pub/sub never breaks the generation path.

### 1.10 Agent / MCP interface

- **FastMCP mounted at `/mcp`** (Streamable HTTP) on the FastAPI app; a **stdio shim binary** (`mcp_shim/__main__.py`) proxies JSON-RPC lines → HTTP for stdio-only clients, propagates `mcp-session-id`, maps HTTP ≥400 → JSON-RPC `-32000`, converts SSE frames back to stdout JSON.
- **Four tools:** `voicebox.speak`, `voicebox.transcribe`, `voicebox.list_captures`, `voicebox.list_profiles`.
- **Per-client identity:** `X-Voicebox-Client-Id` + remote addr go into ContextVars via middleware; `MCPClientBinding` rows pin a profile + engine + default personality per client, stamped with `last_seen_at`.
- **Resolution precedence** (`resolve.py`): explicit arg → per-client binding → `capture_settings.default_playback_voice_id` → None.
- **Loopback gate:** `request_is_loopback()` restricts `audio_path` filesystem access so a server bound on `0.0.0.0` is not an unauthenticated local-file read primitive.
- **Streaming:** `POST /generate/stream` is *not* SSE — it runs full TTS synchronously, encodes the whole WAV, streams in 64KB chunks with a client-disconnect guard. No mid-inference cancellation. This is a recognized limitation (see §12).

### 1.11 What Voicebox does *not* do (equally important)

- No true token-level streaming TTS (only generate-then-stream-WAV).
- No automatic engine selection per language — the caller must pick.
- No process-level crash isolation on the client (the server is a separate process by construction, but there is no supervised restart / health-rebuild loop).
- The full Python stack is heavy (PyTorch + transformers + 7 engines); model download/storage cost is real (0.3GB–8GB per engine).
- No mobile/embedded deployment; desktop-server architecture only.

---

## 2. Phase 1 — Gap Extraction

For each meaningful Voicebox capability, the question set was: *what problem does this solve?* / *does JARVIS already solve it?* / *is Voicebox's mechanism superior?* Only genuine gaps are recorded.

Legend: **HAVE** = JARVIS has it (equal or better); **GAP** = missing or inferior, recorded with the problem it creates.

| # | Voicebox capability | Problem it solves | JARVIS current state | Verdict |
|---|---|---|---|---|
| G1 | Separate-process native inference (FastAPI server) | Native crashes (mutex, segfault) don't kill the host; heavy inference doesn't starve the UI | Native voice engines (Vosk/sherpa/TTS) run **in-process**; `d0735ac` retired the pipeline over exactly these crashes | **GAP (critical)** |
| G2 | Serial inference queue | GPU/CPU contention between concurrent inferences | ModelManager + RuntimeBinder exist; no serialized voice-inference scheduler; `6a5df37` root-caused TTS starving other native engines | **GAP** |
| G3 | Model lifecycle (lazy load, unload, empty_cache, size-switch, progress) | Download/load/unload without holding GPU memory permanently | No voice model lifecycle; engines load eagerly and hold memory | **GAP** |
| G4 | Declarative provider capability metadata (ModelConfig: languages, needs_trim, retries_runaway, supports_instruct, size_mb) | Engine selection can reason over language/failure/quality/data | `CapabilityRegistry.Capability` has languages/RAM/latency but no `needs_trim`/`retries_runaway`/`supports_instruct`/per-language quality | **GAP** |
| G5 | Voice profiles (cloned/preset/designed, multi-sample, per-profile engine+effects+personality) | A persistent, named voice identity with samples and defaults | No voice identity of any kind; "best voice for language" only | **GAP** |
| G6 | Voice cloning (zero-shot from reference audio) | Speak in a specific person's voice from a few seconds of audio | Not present | **GAP** |
| G7 | Voice prompt caching (content-addressed) | Don't re-encode the voice embedding on every generation | Not present | **GAP** |
| G8 | Long-form chunking + crossfade | Unlimited-length, click-free synthesis | `StreamingTts` splits sentences for Google TTS but no chunked synthesis + crossfade for native engines | **GAP** |
| G9 | Audio effects pipeline (pedalboard) + presets | Post-process a voice (pitch, reverb, filters) | Not present | **GAP** |
| G10 | Voice personality layer (LLM compose/rewrite) | A voice "is" someone with a character, not just a timbre | **HumanCore is superior at text-level** (full identity/personality/relationship/expression). The gap is the **bridge**: `ProsodyHint` is null; personality never reaches voice | **PARTIAL GAP** (bridge) |
| G11 | Runaway detection + trim (speech→silence→speech) | Catch EOS-miss/codec-noise corrupt output and retry smaller | Circuit-breaker + RecoveryController exist at the nervous level, but no engine-output-level runaway detection | **GAP** |
| G12 | Agent-to-voice interface (MCP speak with per-client binding) | An external agent can speak in a configured voice | Not present (JARVIS *is* the agent; no speak tool exposed). But a clean `SpeakRequest→audio` capability contract is still needed | **GAP (contract)** |
| G13 | Unload-orphan / stale-generation recovery | Crashed jobs don't wedge the queue forever | FailureSurface + RecoveryController exist; no generation-queue equivalent | **GAP** |
| G14 | Refinement few-shot-as-chat-turns + artifact collapsing | STT transcripts become clean, technical terms preserved | Not present | **GAP** |
| G15 | Version provenance (original / effects / takes / lineage) | Every generation is reproducible and reversible | Not present | **GAP** |

**Non-gaps (JARVIS equal or better — do not invent work):**
- **STT escalation** — JARVIS's `STTArbitrator` (confidence-based escalation across 4 recognizers) beats Voicebox's single platform-selected Whisper. Gap is only that it is unwired.
- **Language routing** — JARVIS's `LanguageRouter` (en + Egyptian-Arabic + code-switching segments) beats Voicebox's explicit-per-request language. 
- **Text-level personality** — HumanCore exceeds Voicebox's personality rewrite.
- **Failure surface** — JARVIS's FailureSurface + RecoveryController + circuit breakers exceed Voicebox's per-engine try/except.

---

## 3. Phase 2 — Universal Research (Cross-Domain Mechanism Analysis)

The question per gap: **"What mechanism produces this capability most efficiently?"** — not "what voice tool should JARVIS copy?" Mechanisms are drawn from operating systems, biology, distributed systems, microkernels, audio engineering, and communications.

### 3.1 G1 — Native inference isolation (the critical gap)

**Problem:** in-process native engines (Vosk C++, sherpa-onnx C++, onnxruntime, Google TTS service) crash the whole app; even without crashing, a blocking TTS call starves other native engines in the same process (`6a5df37`).

**Voicebox mechanism:** run inference in a separate process; the host talks over HTTP/MCP. The FastAPI server process is where PyTorch lives; the Tauri shell never touches it.

**Cross-domain mechanisms:**

| Domain | Mechanism | Transferable principle |
|---|---|---|
| **Operating systems** | **Process address-space isolation** | A fault in one process cannot corrupt another; the OS guarantees a crash boundary. The most fundamental isolation primitive that exists. |
| **Microkernel design** (QNX, seL4, L4) | **Drivers in user-space servers, restarted on crash** | Device drivers (the crash-prone component) run as supervised servers, not in the kernel; a crash kills only the server, which a supervisor restarts. |
| **Web browsers** (Chromium) | **Renderer sandboxing / multi-process** | One tab's renderer crash doesn't kill the browser; the browser supervises and restarts the renderer. |
| **Biological** | **Cellular compartmentalization; organelles with their own membranes; immune surveillance** | High-risk, high-throughput functions run behind a membrane; damage is contained and repaired. |
| **Android platform** | **`android:process` — separate process + Service; Binder death recipients** | Android natively supports a capability running in its own process with supervised lifecycle. |
| **Containers** | **Namespace isolation + supervisor** | Isolation + declarative rebuild recipe ("the environment can be recreated"). |

**Jarvis-native design (§5):** the VoiceOrganism executes in a **dedicated Android process** (`android:process=":voice"`), owning all native voice libraries. The app process hosts the organism *contract* (AIDL/Binder); the nervous system supervises the voice process, detects death (Binder `linkToDeath`), and **rebuilds the environment** from its recipe. This is microkernel supervision + Chromium renderer restart applied to voice. It solves the exact crash class that retired JARVIS's voice, and it is the Android-native expression of Voicebox's separate-server insight.

### 3.2 G2 — Inference scheduling (contention)

**Problem:** concurrent native inferences contend for CPU/RAM (TTS starved KWS/Vosk in-process — root cause of `6a5df37`).

**Cross-domain:**
- **OS schedulers:** priority + preemption; a high-priority latency-critical task preempts a batch task.
- **Database engines:** a single-writer serialization for transactions that share a hot resource (WAL); reads may proceed in parallel.
- **Audio/music production:** a mix bus with per-track buses — each engine renders into its own buffer, mixed by a single consumer.
- **Robotics:** a real-time loop with a fixed scheduling budget per subsystem (control at 1kHz, perception at 30Hz, planning at 1Hz).

**Jarvis-native design:** a `VoiceScheduler` that (a) **serializes model-bound inference** on the voice process (single worker, like Voicebox's serial queue — one native engine at a time), (b) **prioritizes by capability role** (KWS wake = highest, push-to-talk STT = next, TTS reply = next, background transcribe = lowest), (c) respects the ResourceGovernor budget already in JARVIS. Because the voice process is separate, "one native engine at a time" is *within* the voice process — it can never starve the app's LLM/brain process.

### 3.3 G3 — Model lifecycle (resources)

**Problem:** voice models are large; holding them all resident wastes RAM and startup time.

**Cross-domain:** OS demand paging (load only what is touched); HTTP caches (lazy fetch + eviction); JVM classloaders (load on first reference); **containers** (images pulled once, instances started on demand).

**Jarvis-native design:** a `VoiceModelManager` inside the voice process with Voicebox-style lazy load on first use, `is_loaded()` gating, unload + memory return, size switching (0.6B/1.7B), and download-progress reporting into `ProgressManager`/`TaskManager` (which JARVIS already has). Models are a *resource* owned by the organism, released on suspend (`OrganismLifecycle.SUSPENDED`).

### 3.4 G4 — Declarative provider metadata (intelligent selection)

**Problem:** "best engine for this utterance" needs per-engine capability facts (languages, quality, failure modes, instruct support), not a hard-coded if/else.

**Cross-domain:** **capability-based security** (object capability model — an engine advertises what it can do); **SPA route metadata**; **HTTP content negotiation** (Accept header ↔ provider capabilities); **Linux kernel modules** (a driver declares which hardware it supports); **evolutionary fitness** (a candidate's measured attributes drive selection).

**Jarvis-native design:** extend `CapabilityRegistry.Capability` with the Voicebox-derived fields — `needsTrim`, `retriesRunaway`, `supportsInstruct`, `perLanguageQuality: Map<String,QualityTier>`, `sampleRateHz`, `voices: List<String>`, `supportsCloning`, `isStreaming`, `startupCostMs`, `sizeMb` — and let `findBest()` (which already filters by category/language/RAM/latency) also filter by these. Selection becomes **benchmark-driven data, not code** (§8).

### 3.5 G5/G6/G7 — Voice identity, cloning, prompt caching

**Problem:** JARVIS has no persistent voice identity, no way to clone a voice, no way to avoid re-encoding a voice per generation.

**Cross-domain:**
- **Human identity:** a stable identity across contexts; a faceprint/voiceprint is the persistent representation.
- **Graph databases / object identity:** an entity with attributes + samples, versioned.
- **Caches (memcached, browser):** content-addressed keys for expensive derived values.
- **Protein folding / homology:** a small reference set (samples) encodes a large structure (voice) via an alignment model — exactly what zero-shot voice cloning does.

**Jarvis-native design:** a `VoiceProfile` entity (id, name, language, `voiceType` = `CLONED`/`PRESET`/`DESIGNED`, samples, `personalityRef`, `defaultEngine`, `effectsChain`, provenance) stored like other JARVIS state; `VoiceIdentity` owns the profile→organism mapping. `VoiceCache` reuses Voicebox's content-addressed pattern (`MD5(audio bytes + reference text)`) to avoid re-encoding. Cloning capability is **declared by the provider** (G4), so JARVIS doesn't hard-code which engines clone.

### 3.6 G8/G11 — Long-form chunking, crossfade, runaway handling (audio engineering)

**Problem:** long replies exceed engine context; unstable engines emit corrupt tail audio; chunk seams click.

**Cross-domain:**
- **Speech synthesis prosody** — crossfading is linear amplitude blending; more advanced is *equal-power* crossfades (sin²) to avoid perceived dips.
- **Lossless streaming/concatenation** — packet loss concealment, jitter buffers.
- **Error detection in communications:** parity/CRC → **detect-then-retransmit**, exactly the runaway-detection-then-retry pattern.
- **Music DAWs:** region crossfade at zero-crossings to kill clicks.

**Jarvis-native design:** port the *mechanism* — sentence-boundary splitting with abbreviation/tag/CJK awareness, short-text fast path, per-chunk seed perturbation, crossfade (equal-power), `has_tts_runaway` (speech→silence→speech) → split-in-half retry, trailing-noise trim. These are pure audio algorithms, independent of Voicebox's code, and belong in a `utils/audio`-style layer of the voice process. Only enable runaway-retry per provider (metadata flag, G4).

### 3.7 G9 — Effects (signal processing)

**Problem:** voice needs post-processing (radio/robot/echo character, filters, compression) without touching the synthesis engine.

**Cross-domain:** pedalboards/rack units (chain of effects with bypass); DAW plugin architecture (VST); broadcast processing (compressor before transmission); **pipeline architecture in general** (sources → transforms → sinks — Voicebox's own roadmap).

**Jarvis-native design:** a small, dependency-free DSP chain (`applyEffects(audio, sr, chain)`) with gain/compressor/eq/pitch-shift/reverb — either a JARVIS-native DSP implementation in the voice process or a lightweight library pinned to the environment. Per-profile default chain (from VoiceProfile), built-in + user presets, "original is always preserved" versioning.

### 3.8 G10 — Personality→voice bridge (the HumanCore integration)

**Problem:** HumanCore produces character at text level; voice is a flat "best-quality" TTS. The two never meet.

**Cross-domain:** **actor direction** (an actor interprets a script with emotion/pace); **prosody in linguistics** (intonation, stress, rhythm carry affect); **theater/performance** (character = voice + timing + manner).

**Jarvis-native design:** the missing piece is `ProsodyHint` becoming real. HumanCore's Expression Pass emits a structured affect/register per utterance (warmth, urgency, formality, mood) → the VoiceOrganism maps it to (a) engine/voice selection, (b) speech parameters (rate, pitch), (c) optional SSML/paralinguistic markup (if the provider supports it — metadata flag). This preserves the *separation* the directive demands: HumanCore owns *who* and *how it's said*; the VoiceOrganism owns *sound*. No voice code reaches into HumanCore; HumanCore never renders audio.

### 3.9 G12 — Agent-to-voice contract

**Problem:** even though JARVIS *is* the companion, the voice capability must expose a clean contract so any subsystem (or future remote agent) can request speech, and the nervous system can route it.

**Cross-domain:** **RPC contracts** (a typed request/response with idempotency); **service meshes** (client → router → provider with retry/timeout); **actor systems** (message + reply channel).

**Jarvis-native design:** `VoiceSynthesisRequest{text, profileId?, language?, provider?, personality?, streaming?, priority}` → `VoiceAudio{path|stream, durationMs, providerId, seed, latencyMs}` as a typed capability contract, routed through `CapabilityRouter` (cheapest-valid provider, fallback chain, `gapDetected`). This is the "JARVIS says 'Speak this response' → contract → nervous system → organism → environment → audio" flow from Phase 5 of the directive.

### 3.10 G14 — Refinement

**Problem:** transcripts from STT carry disfluencies, self-corrections, and hallucination loops.

**Cross-domain:** **transcript-to-summary in journalism** (filler removal, verification); **compilers** (token-level artifact collapsing); **spell-check autocorrect** (context-aware rewrite).

**Jarvis-native design:** artifact-collapsing pre-pass (≥6-run word loops, substring loops) + flag-driven LLM refinement, with the Voicebox-derived lesson that **few-shot examples must be chat turns, not inline system-prompt text** for small models. JARVIS has an LLM already (cloud brain + optional local llama) — refinement is a thin capability over it.

---

## 4. Phase 3 — EnvironmentSpecification (the Mutated Voice Environment)

The environment is part of the solution. The directive's principle: **the host does not need to contain every dependency.** The Voice capability's environment:

```kotlin
// EnvironmentSpecification for the Voice capability (conceptual schema)
data class VoiceEnvironmentSpec(
    // Process/runtime
    val process: DedicatedProcess = DedicatedProcess(  // ANDROID :voice process
        androidProcessName = ":voice",
        crashSupervision = SupervisedRestart,          // linkToDeath + rebuild
        binderContract = "com.jarvis.voice.IVoiceService"
    ),
    // Native libraries (bundled or built at environment-build time)
    val nativeLibraries: List<NativeLib> = listOf(     // per provider, selected by capability
        NativeLib("sherpa-onnx",  "com.github.k2-fsa:sherpa-onnx"),
        NativeLib("onnxruntime",  "com.microsoft.onnxruntime:onnxruntime-android"),
        NativeLib("vosk",         "com.alphacephei:vosk-android"),
        // Piper engine: sherpa-onnx already ships Piper voices; a Piper
        // provider is a sherpa-offline-tts configuration, not a new library
    ),
    // Compute
    val cpu: CpuSpec    = CpuSpec(cores = 2..4, affinity = "big cores for TTS/STT"),
    val ram: RamSpec    = RamSpec(budgetMb = 512,  // organism-managed, released on suspend
                                  allowSwap = false),
    // Models
    val models: List<ModelSpec> = listOf(
        ModelSpec("vosk-model-small-en-us-0.15",  sizeMb = 40,   onDevice = true),
        ModelSpec("sherpa-whisper-tiny.en",       sizeMb = 40,   onDevice = true),
        ModelSpec("en_US-lessac-medium",          sizeMb = 60,   onDevice = true),  // piper, already downloaded
        ModelSpec("ar_JO-kareem-medium",          sizeMb = 60,   onDevice = true),  // piper, already downloaded
        ModelSpec("silero-vad-v6.2.1",            sizeMb = 2,    onDevice = true)
    ),
    // Failure
    val failure: FailureSpec = FailureSpec(
        isolateCrashes = true,          // process boundary (G1)
        supervisedRestart = true,       // nervous system rebuilds on death (G1/G13)
        serializedInference = true      // one native inference at a time (G2)
    ),
    // Timing
    val latency: LatencySpec = LatencySpec(
        wakeWordMs = 400, pushToTalkSttMs = 1500, ttsFirstChunkMs = 500, ttsInterruptMs = 80
    ),
    // Energy
    val power: PowerSpec = PowerSpec(
        idleUnloadsModels = true, suspendOnBackground = true
    ),
    // Persistence
    val persistence: PersistenceSpec = PersistenceSpec(
        storeProfiles = true, storeVoiceCache = true, storeRefinement = true
    )
)
```

**Why this environment — and no other — for voice:**
- **Dedicated process** (not in-APK, not remote-cloud): Android's native mechanism for the exact crash-isolation problem JARVIS proved it has. Cloud would add latency + privacy loss for a capability that is fundamentally local.
- **Models already on device:** Vosk, whisper-tiny, silero, and the two Piper ONNX voices are all already in the repo/assets or `vault/just_downloaded/`. The environment *selects* the two Piper models that are already downloaded rather than inventing a new download.
- **The environment is a recipe, not a habitat:** the APK bundles the voice process; the nervous system recreates it on demand from the spec. §14.

**Environment selection is benchmark-gated, never hard-coded** (§8): "voice always runs locally" is not asserted. If a future remote worker demonstrates better latency/RAM/quality for a *specific* capability slice (e.g. heavy cloning), that slice may route there. Default today is the dedicated local voice process because it wins on latency, privacy, and crash isolation.

---

## 5. Phase 4-5 — The VoiceOrganism (Capability Contract)

A `VoiceOrganism` implementing `MicroSystemContract` (§ 5 of the codebase), living in the dedicated voice process, exposing a clean capability contract. The APK-side `CapabilityRegistry` sees capabilities; the organism's internals are opaque to the body.

### 5.1 Capability surface (registered in CapabilityRegistry)

| Capability id | Category | Providers (current) | fallback |
|---|---|---|---|
| `voice.wake_word` | WAKE_WORD | `wake.silero_vad` + `wake.vosk_grammar` (en) | push-to-talk only |
| `voice.stt` | STT | `stt.vosk` → `stt.sherpa_zipformer` → `stt.sherpa_whisper` → `stt.platform` (ar/en) | platform cloud |
| `voice.vad` | VAD | `vad.silero` (onnx in voice process) | RMS energy endpointing |
| `voice.tts` | TTS | `tts.piper_en` → `tts.piper_ar` → `tts.system` | tts.system |
| `voice.identity` | VOCABULARY/… | `identity.profile_store` | default voice |
| `voice.clone` | TTS | declared by provider (`supportsCloning`) | preset voice |
| `voice.effects` | SYSTEM | `dsp.chain` (in-process DSP) | none (clean audio) |

### 5.2 Organism structure

```
VoiceOrganism (MicroSystemContract)
│  id="voice", genome=<voice-genome>, version
│  capabilities = {voice.wake_word, voice.stt, voice.vad, voice.tts, voice.identity, ...}
│  resourceRequirements, currentResources, health, telemetry, sync flows
│
├── VoicePerception      (VAD + KWS + STT escalation, in voice process)
├── VoiceSynthesis       (TTS provider selection + chunking + crossfade + effects)
├── VoiceIdentity        (voice profiles; persona→voice mapping)
├── VoiceProfile         (profile entities, samples, effects, provenance)
├── VoiceModelManager    (lazy load/unload/size-switch/download-progress)  (G3)
├── VoiceRouter          (per-utterance provider selection from metadata)   (G4)
├── VoiceCache           (content-addressed voice prompt cache)            (G7)
├── AudioPipeline        (resample, normalize, trim, runaway-detect, effects) (G8/G9/G11)
├── VoiceScheduler       (serialized inference + priority + budget)        (G2)
├── VoiceResourceController (model/RAM/battery release on suspend)
└── VoiceFailureController  (runaway, trim, engine retry, process restart) (G11/G13)
```

### 5.3 Capability contracts (typed, versioned)

```kotlin
// The capability contract the nervous system routes on. JARVIS never sees
// the implementation or environment — only these shapes.
data class VoiceSynthesisRequest(
    val text: String,
    val profileId: String?,      // null → identity default
    val language: String = "en",
    val personality: Boolean = true,   // route through HumanCore Expression first
    val streaming: Boolean = true,
    val priority: Int = 0
)
data class VoiceSynthesisResult(
    val audio: AudioSink,        // file / stream / pending
    val providerId: String,
    val durationMs: Long,
    val latencyMs: Long,
    val seed: Int?,              // reproducibility
    val effectsApplied: List<String>
)

data class VoicePerceptionRequest(
    val audio: AudioSource,      // mic frame / file
    val mode: WakeWord | Utterance | Transcribe,
    val languageHint: String? = null
)
data class VoicePerceptionResult(
    val text: String?,
    val confidence: Float,
    val language: String,
    val providerId: String,
    val isWakeWord: Boolean = false
)
```

**Separation enforced:** the nervous system routes `voice.tts` / `voice.stt` through `CapabilityRouter`; the body never imports a voice engine class. The voice process implements the contract over AIDL; the APK-side binding is a thin `IVoiceService` proxy. **If the voice process is rebuilt (new provider wins a benchmark), only the environment recipe changes — the contract is stable.**

---

## 6. Phase 6 — Environment Mutation (the Voice Habitat)

JARVIS's `MutantEnvironment` already provides disposable candidate habitats (§6). For voice, a candidate provider is evaluated exactly the way the directive prescribes:

```
CapabilityGap (e.g. "TTS: Piper en" — designed, downloaded, unimplemented)
   → UniversalResearchEngine (G4 metadata + §3 mechanisms)
   → EnvironmentSpecification (per-provider variant of §4)
   → MutantEnvironment (voice process sandbox, voice-process-native)
   → build provider (sherpa-offline-tts with en_US-lessac-medium)
   → benchmark (quality/latency/RAM/CPU/battery) vs tts.system baseline
   → failure test (long text, code-switch, interruption, cold start)
   → PROMOTE if measurable improvement, else DESTROY habitat
```

**Concrete first candidates (already on device, no new downloads):**
1. **`tts.piper_en`** — sherpa-onnx offline TTS + `en_US-lessac-medium.onnx` (already in `vault/just_downloaded/`). Baseline vs `tts.system` (Google TTS): measure first-chunk latency, voice stability, interruption, offline reliability.
2. **`tts.piper_ar`** — `ar_JO-kareem-medium.onnx` (already downloaded). Arabic voice is a declared but unimplemented capability.
3. **`stt.sherpa_zipformer`** — the dead `JarvisSherpaZipformer` has no assets; a Zipformer model is the promotion candidate for streaming STT over Vosk.
4. **`vad.silero`** — `silero-vad-v6.2.1.onnx` is in `vault/just_downloaded/extracted/` and the native build is disabled; a voice-process-resident Silero VAD replaces RMS thresholds.

**The retired pipeline as the baseline:** the promotion gate compares a candidate against the **current best** — which today is "no voice at all" (retired) or "push-to-talk platform only." A candidate that re-enables wake-word + command STT with crash isolation **demonstrates measurable improvement by construction** (capability restored) — but it still must pass the same benchmark gate as everything else.

---

## 7. Phase 7 — Provider Ecosystem (candidate implementations)

Providers are **candidate implementations**, evaluated and added only on merit:

| Provider | Category | Evaluation axes (from directive) | Likely niche |
|---|---|---|---|
| Android `TextToSpeech` (Google) | TTS | quality HIGH, latency low, RAM tiny, 50+ langs, offline if voices installed | Universal fallback (cheapest-valid) |
| **Piper via sherpa-onnx** | TTS | quality HIGH, RAM ~60MB/model, fast CPU, **ar-EG + en-US already on disk** | Default local voices — first promotion target |
| Vosk (grammar KWS + open STT) | STT/KWS | ~40MB, streaming, low CPU — but in-process crash history | KWS + command STT (voice process) |
| Sherpa-Zipformer | STT | streaming, multilingual — assets missing | Streaming STT upgrade |
| Sherpa-Whisper (tiny.en) | STT | offline fallback, non-streaming | Fallback tier |
| Android `SpeechRecognizer` | STT | cloud, ar-EG native, variable latency | Arabic/push-to-talk when local fails |
| Silero VAD | VAD | tiny 2MB, accurate, onnx | Replace RMS endpointing |

**Not automatically added** (from Voicebox's engine set): Qwen3-TTS, LuxTTS, Chatterbox, TADA, Kokoro — these are multi-hundred-MB to multi-GB PyTorch models. They become candidates **only if** the voice process benchmark shows they beat Piper + system on a JARVIS-relevant axis (expressiveness, cloning) within the RAM/battery budget. They would run in a *mutated* voice process variant (heavier RAM spec), never in-APK.

---

## 8. Phase 8 — Intelligent Environment Selection (benchmark-driven)

Selection is data, not hard-coding. The nervous system reasons:

- `"voice.tts"` requested, language `ar` → query `CapabilityRegistry` by category+language → candidates with per-language quality + RAM + latency metadata → choose cheapest-valid (CapabilityRouter) → if all local providers unhealthy, route to `tts.system` fallback.
- If a provider's measured latency/RAM drifts out of budget (ResourceGovernor), the router re-routes and the VoiceModelManager unloads the offender.
- If a *new* environment (e.g. a remote worker with a cloning engine) is proposed, `EnvironmentResearcher` benchmarks it against the local voice process on the same harness; promotion only on measured improvement (§13).

**The voice process is the current best environment for most slices; the router can still pick a different one per slice.** Nothing is asserted as "voice always runs here."

---

## 9. Phase 9 — Nervous System Integration

The nervous system supervises the organism end-to-end, exactly per the directive:

```
Jarvis Core → (SpeakResponse) → Nervous System → CapabilityRouter → VoiceOrganism
   └── organism state (lifecycle phase, health, latency, resources, failures)
   └── environment state (voice process alive? model loaded?)
   └── on failure: detect → isolate (process boundary) → recover (restart) → fallback (next provider) → rebuild (from recipe)
```

**Process supervision (the G1 fix):** the app-process `VoiceOrganismHost` binds the `:voice` service; `Binder.linkToDeath` detects process death; on death the host reports `FailureReport` (subsystem=`VOICE`, category=`VOICE_INPUT`/`TTS`), marks `voice.*` capabilities DEGRADED/FAILED in CapabilityRegistry, triggers RecoveryController, and **rebuilds the voice process from its environment spec**. Because the recipe is complete (§4), rebuild is deterministic — Chromium's renderer-restart pattern.

**Lifecycle integration:** `OrganismLifecycle` already has DORMANT/ACTIVE/SUSPENDED/DEGRADED/DISABLED. Voice uses it: ACTIVE = voice process resident + models loaded; SUSPENDED = process resident, models unloaded (battery); DORMANT = process not started (first request cold-starts it); DISABLED = user turn-off. CapabilityRegistry reflects phase as state.

**Health:** `checkHealth()` reports voice-process liveness, model-loaded state, last-failure, consecutive-failures — feeding the 30s health polling already designed in the capability matrix.

---

## 10. Phase 10 — Self-Improvement Loop

Bounded evolutionary engineering. The loop is already scaffolded (`EvolutionLoop`, `PromotionController`, `RollbackController`, `FitnessModel`). For voice:

```
Voice quality insufficient (e.g. Arabic TTS robotic, or a crash class)
   → CapabilityGapDetector.detect("voice.tts:ar") → GAP
   → UniversalResearchEngine.research(...) → candidate mechanisms
   → MutantEnvironment per candidate → benchmark harness
   → PromotionController gate: measurable objective + baseline + threshold
   → deploy (new provider in voice process, recipe updated, version bumped)
```

Every cycle has: **measurable objective** (e.g. "MCD/pitch error < X, or WER < Y, or crash-free wake→reply < Zs"), **baseline** (current best provider), **improvement threshold**, **resource constraints** (RAM/battery budget), **termination condition** (no infinite optimization; stop when threshold met or candidates exhausted).

**Rollback:** `RollbackController` + version provenance mean the previous provider is one swap away; the recipe records `version` + rollback info (§14).

---

## 11. Phase 11 — Voicebox Research Objectives, Answered

For each of the 18 directive objectives: JARVIS existing mechanism → Voicebox mechanism → superior design (all in §1-§3).

| # | Objective | Verdict & superior design |
|---|---|---|
| 1 | Multi-engine abstraction | JARVIS has `TTSEngine`/`TTSEngineRegistry` (dead) + `CapabilityRouter`. Superior: router over capability metadata, not engine classes. |
| 2 | Model registry | JARVIS has `CapabilityRegistry`. Superior: extend with provider failure/quality metadata (G4). |
| 3 | Lazy model loading | Absent. Adopt Voicebox's lazy-on-first-use inside the voice process (G3). |
| 4 | Model lifecycle | Absent. Add unload + size-switch + download-progress (G3). |
| 5 | Voice prompt caching | Absent. Content-addressed cache (G7). |
| 6 | Voice profiles | Absent. VoiceProfile entity + identity organism (G5). |
| 7 | Voice cloning | Absent. Provider-declared cloning (G6). |
| 8 | Multilingual routing | JARVIS superior (LanguageRouter en/ar + code-switching). |
| 9 | Streaming synthesis | JARVIS has sentence-level Google TTS streaming. Superior: per-provider streaming + first-chunk latency budget. |
| 10 | Long-form chunking | Absent for native engines. Adopt sentence-boundary split + per-chunk seed + fast path (G8). |
| 11 | Crossfade | Absent. Equal-power crossfade (G8). |
| 12 | Audio effects pipeline | Absent. In-process DSP chain (G9). |
| 13 | Voice personality layer | JARVIS superior at text (HumanCore). Gap is the prosody bridge (G10). |
| 14 | Serialized inference scheduling | Absent. VoiceScheduler (G2). |
| 15 | Provider capability metadata | Partial. Extend CapabilityRegistry (G4). |
| 16 | Engine fallback | JARVIS has CapabilityRouter fallback chains — superior. Make it live. |
| 17 | STT/TTS unified architecture | JARVIS has both scaffolds; unify under VoiceOrganism contract. |
| 18 | Agent-to-voice interface | Absent. VoiceSynthesisRequest contract (G12). |

---

## 12. Phase 12 — Do Not Copy Limitations

Voicebox's own limitations become research questions, not inherited defaults:

- **Heavy Python/transformers stack:** JARVIS's voice process must not import PyTorch to speak. Piper/sherpa-onnx/Vosk are lightweight native+onnx runtimes. Heavy LLM TTS (Qwen etc.) stays a *candidate* behind the promotion gate.
- **Model download/storage cost:** JARVIS reuses already-downloaded models (Piper ×2, Vosk, whisper-tiny, silero) before considering new downloads.
- **Platform-specific acceleration:** JARVIS targets one platform (Android/arm64) with onnxruntime + sherpa-onnx; NPU/QNN/HTP acceleration is a later environment mutation, benchmarked, not assumed.
- **No true streaming, no per-language engine auto-selection, no supervised restart:** JARVIS designs these in (§3.8-G12, §3.4-G4, §3.1-G1). Voicebox's generate-then-stream-WAV and caller-picks-engine are *limitations we explicitly do not inherit*.
- **Dependency complexity:** 7 engines × PyTorch is not a JARVIS goal. The voice process keeps a minimal, pinned native dependency set per provider; each provider is an environment variant, not a co-resident stack.

---

## 13. Phase 13 — Validation (Promotion Gate)

Every candidate runs in its own mutant habitat (§6) and is tested on: functionality, performance, resource usage (RAM/CPU/battery), latency (wake-word, first-chunk, barge-in), memory, stability (crash-free soak), failure recovery (kill the voice process mid-generation → rebuild), regression, environment reproducibility (rebuild from recipe → identical capability).

**Comparison:** baseline = current best JARVIS capability. For voice today that baseline is *degraded* (retired). Promotion requires **either** (a) restoration of a designed capability with crash isolation + measured budget, or (b) measurable improvement over the existing provider on the objective. "It works" is never sufficient.

**First promotion targets (ordered):**
1. **Voice process habitat** — the G1 fix itself (process isolation + supervised restart). Promotion criterion: crash-free soak with native engines resident.
2. **`vad.silero`** in the voice process (replace RMS endpointing).
3. **`tts.piper_en` + `tts.piper_ar`** (models already on disk).
4. **Wake-word + command STT** re-enabled in the voice process (restores the retired capability with the crash boundary).
5. **`voice.identity` + `voice.effects` + `voice.tts`-personality bridge** (G5/G9/G10) — the "voice JARVIS owns" layer.

---

## 14. Phase 14 — Surviving Artifact

Production JARVIS retains, per the directive:

```
VoiceOrganism contract          (typed capability contracts, §5.3)
Implementation artifact         (the voice process binary + APK binding)
Environment recipe              (VoiceEnvironmentSpec, §4)
Dependency manifest             (pinned native libs per provider)
Model manifest                  (Vosk, whisper-tiny, piper×2, silero — on-device)
Benchmark record                (per-provider metrics from §13 harness)
Validation record               (tests run, crash soak, failure tests)
Failure record                  (crash classes, root causes — d0735ac etc.)
Version + rollback info         (PromotionController + RollbackController)
```

The disposable habitat is recreated from the recipe on demand. **JARVIS stores the recipe for its organism's habitat, not the habitat itself.**

---

## 15. Roadmap (downstream, gated)

| Step | Deliverable | Gate |
|---|---|---|
| R1 | `VoiceEnvironmentSpec` + `:voice` process shell + `VoiceOrganismHost` (Binder linkToDeath) | builds + crash-free cold start |
| R2 | Move Silero VAD + Vosk KWS + STT + Piper TTS into the voice process | regression on `stt.vosk_wake` / `stt.vosk_command` / `tts.piper_en` |
| R3 | `VoiceModelManager` + `VoiceScheduler` (lazy load, serialized inference, suspend-unload) | ResourceGovernor budget respected |
| R4 | `VoiceProfile`/`VoiceIdentity` + `VoiceCache` (content-addressed) | profile round-trip + cache hit |
| R5 | Effects DSP chain + per-profile defaults | clean-original preserved |
| R6 | HumanCore `ProsodyHint` → voice bridge (rate/pitch/engine per expression) | affect maps to audibly different delivery |
| R7 | `VoiceSynthesisRequest`/`VoicePerceptionRequest` routed through CapabilityRouter | fallback + gap detection verified |
| R8 | Refinement capability over LLM (artifact-collapse + chat-turn few-shot) | WER/disfluency reduction measured |

Each step is a promotion-gated cycle (§10): objective, baseline, threshold, terminate. Failed habitats are destroyed; the recipe is the only survivor.

---

## Appendix A — Research sources (the seed)

- `vault/just_downloaded/voicebox-main.zip` — extracted to disposable `/tmp/vb-research/voicebox-main/` (48MB, 120 backend Python files + React/Tauri app). Mechanism study in §1.
- JARVIS working tree — `mobile/app/src/main/java/com/jarvis/app/` (voice stack), `capability/`, `organism/`, `nervous/`, `microsystem/`, `research/`, `builder/`, `mutation/`, `mutant/`, `env/`, `humancore/`.
- `vault/architecture/JARVIS_PHASE1_CAPABILITY_MATRIX.md` — the designed voice capability surface.
- `capability_manifest.json` — retirement record: `"voice_pipeline_v1": "retired, native mutex crash root-caused"`.
- Git history: `d0735ac` (disable), `6a5df37` (dedicated threads, starvation root cause).

## Appendix B — Non-goals

- No Voicebox installation, port, or dependency (§ directive).
- No reproduction of Voicebox's desktop architecture.
- No Python/PyTorch in the voice process by default.
- No voice code inside HumanCore and no HumanCore inside the voice organism (separation preserved).
- No "voice always runs locally" or "voice always runs on Android" assertions — environment selection is benchmark-driven (§8).
