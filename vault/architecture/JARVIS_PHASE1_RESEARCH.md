# JARVIS Phase 1 Research Report

**Generated:** 2026-08-09  
**Purpose:** Universal mechanism research for Phase 1 capabilities — DISCOVER → COMPARE → EXTRACT MECHANISMS → IDENTIFY LIMITATIONS → COMBINE COMPLEMENTARY MECHANISMS → DESIGN HYBRID → BENCHMARK → IMPLEMENT → VALIDATE

---

## 1. Local LLM Inference (GGUF / llama.cpp on Android)

### 1.1 State of the Art

| Approach | Mechanism | Pros | Cons |
|----------|-----------|------|------|
| **llama.cpp CPU (GGML/GGUF)** | Quantized matrix multiplication (GGML), KV cache, flash attention | Mature, wide quantization support (Q4_K_M, Q8_0), CPU fallback always works | Slower than GPU; memory bandwidth bound |
| **llama.cpp Vulkan (GPU)** | Compute shaders for GEMM, layer norm, attention | 2–5× speedup on capable GPUs; offloads CPU | Driver bugs, thermal throttling, not all devices support required extensions |
| **llama.cpp Metal (iOS)** | Metal Performance Shaders | Excellent on Apple Silicon | N/A for Android |
| **ONNX Runtime + quantized models** | Graph optimization, operator fusion, NNAPI/QNN delegates | Cross-platform, hardware acceleration via delegates | Less mature LLM support; quantization differences |
| **MNN / NCNN / TFLite** | Mobile-first inference engines | Lightweight, optimized for mobile | Limited LLM operator support; conversion complexity |
| **llamafile / llama.cpp server** | Single-file executable with embedded model | Easy deployment | Not suitable for Android app integration |

### 1.2 Key Mechanisms to Extract

**From llama.cpp:**
- **GGUF format parsing** — single-file model with metadata, tensor data, quantization info
- **K-bit quantization (Q4_K_M, Q5_K_M, Q8_0)** — block-wise quantization with scaling factors; dequantization during matmul
- **KV cache management** — rotating buffer, prefix sharing, memory-efficient attention
- **Flash attention (optional)** — fused attention kernel reducing memory bandwidth
- **Rope/ALiBi position embeddings** — precomputed or on-the-fly
- **Parallel/batched inference** — multiple sequences, speculative decoding
- **Grammar-constrained sampling** — GBNF grammar for structured output
- **Context shifting** — sliding window when context exceeds limit

**From mobile optimization research:**
- **Model splitting** — shard model across CPU/GPU
- **Weight preloading** — mmap + madvise for zero-copy load
- **Thread affinity** — pin threads to big cores (Android: `setThreadAffinity`)
- **Thermal management** — monitor `PowerManager` thermal status, throttle threads
- **Dynamic quantization** — per-layer quantization based on sensitivity analysis

### 1.3 Limitations Identified

1. **Vulkan on Android is unstable** — driver bugs on Mali/Adreno; thermal throttling kills gains
2. **Memory bandwidth is the bottleneck** — not compute; quantization helps more than GPU
3. **Context window limited by RAM** — 32K context needs ~2GB KV cache at Q4_K_M
4. **First-token latency high** — full model load + prompt processing
5. **No standard Android distribution** — must build llama.cpp for each ABI

### 1.4 Hybrid Design

```
┌─────────────────────────────────────────────────────────────┐
│                    LocalInferenceEngine                      │
├─────────────────────────────────────────────────────────────┤
│  State Machine: DORMANT → PREWARMING → READY → GENERATING  │
│                    ↓                    ↓                    │
│              IDLE → SUSPENDED → UNLOADING                   │
└─────────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
   ┌─────────┐      ┌──────────┐      ┌──────────┐
   │ CPU     │      │ Vulkan   │      │ Hybrid   │
   │ Path    │      │ Path     │      │ (Auto)   │
   │(Default)│      │(Optional)│      │(Benchmark)│
   └─────────┘      └──────────┘      └──────────┘
        │                 │                 │
        └─────────────────┼─────────────────┘
                          ▼
              ┌───────────────────────┐
              │ Resource Governor     │
              │ (RAM/thermal/battery) │
              └───────────────────────┘
```

**Decision Logic:**
1. Default to CPU (Q4_K_M, 4 threads, pinned to performance cores)
2. Benchmark Vulkan on device at first run; store result
3. If Vulkan >1.5× faster AND thermal < threshold → use Vulkan
4. Under resource pressure: reduce threads → reduce context → unload model

### 1.5 Implementation Requirements

- [ ] JNI wrapper for llama.cpp (minimal: init, generate, stream, unload, health)
- [ ] GGUF loader with mmap + madvise(WILLNEED)
- [ ] KV cache with configurable max size
- [ ] Cancellation token (cooperative)
- [ ] Generation timeout (wall-clock)
- [ ] Token budget enforcement
- [ ] Prompt template (system + user + memory context)
- [ ] Metrics: load time, first token, tokens/sec, peak RAM, CPU%

---

## 2. Voice Activity Detection (Silero VAD)

### 2.1 State of the Art

| Approach | Mechanism | Pros | Cons |
|----------|-----------|------|------|
| **Silero VAD (ONNX)** | 4-layer LSTM + linear; 512-sample frames @ 16kHz | SOTA accuracy, tiny (1.3 MB), language-agnostic, MIT license | Requires ONNX Runtime |
| **WebRTC VAD** | Gaussian Mixture Model | Very fast, no ML runtime | Lower accuracy, only 8/16/32/48kHz, fixed thresholds |
| **Vosk built-in** | Vosk recognizer internal VAD | Zero extra cost | Tied to Vosk; not separable |
| **Custom CNN** | Depthwise separable conv | Can be smaller | Requires training data |
| **Energy-based** | RMS threshold + hangover | Zero cost | Poor in noise |

### 2.2 Key Mechanisms

**Silero VAD internals:**
- Input: 512 samples (32ms @ 16kHz) float32 [-1,1]
- Hidden state: 2×128 LSTM cells (carried between frames)
- Output: speech probability [0,1]
- Post-processing: threshold (default 0.5), min speech duration (250ms), min silence (100ms)

**Adaptive threshold mechanisms:**
- **Noise floor estimation** — track minimum probability during silence
- **Dynamic threshold** — `threshold = noise_floor + k * (1 - noise_floor)`
- **Environment profiles** — quiet/office/noisy/car with different `k` values
- **Hangover/pre-speech padding** — include 200ms before/after detected speech

### 2.3 Limitations

1. **ONNX Runtime dependency** — adds ~5 MB to APK
2. **Fixed 16kHz input** — must resample if mic provides 48kHz
3. **No language awareness** — treats all speech equally
4. **False positives in music/TV** — harmonic content triggers speech

### 2.4 Hybrid Design: Audio Gate

```
MIC (48kHz)
    │
    ▼
┌─────────────┐     16kHz      ┌─────────────┐
│ Resampler   │ ──────────────▶│ Silero VAD  │
│ (linear)    │   512 frames   │ (ONNX)      │
└─────────────┘                └─────────────┘
                                    │
                          Speech prob [0,1]
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
             ┌──────────┐    ┌──────────┐    ┌──────────┐
             │ SILENCE  │    │ SPEECH   │    │ UNSURE   │
             │ prob<0.3 │    │ prob>0.7 │    │ 0.3-0.7  │
             └──────────┘    └──────────┘    └──────────┘
                    │               │               │
                    ▼               ▼               ▼
              Keep mic       Signal       Extend
              capture        START       window,
              only                                          collect
              (cheap)       Activate      more frames
                             STT/VAD
```

### 2.5 Implementation Requirements

- [ ] ONNX Runtime Android dependency (onnxruntime-android:1.20+)
- [ ] Silero VAD wrapper (init, process_frame, reset)
- [ ] Adaptive threshold with environment profiles
- [ ] Speech start/end callbacks
- [ ] Integration with `JarvisMic` audio feed
- [ ] Configurable: threshold, min_speech_ms, min_silence_ms, padding_ms
- [ ] Metrics: false positive rate, false negative rate, latency, CPU%

---

## 3. STT Arbitration System

### 3.1 State of the Art

| Recognizer | Type | Latency | Accuracy | Languages | RAM | Best For |
|------------|------|---------|----------|-----------|-----|----------|
| **Vosk** | Hybrid HMM/DNN (Kaldi) | ~300ms | Good (EN) | 20+ | 100MB | Wake word + commands |
| **Sherpa Zipformer** | Streaming Transformer | ~150ms | Excellent | 100+ | 80MB | Continuous streaming |
| **Sherpa Paraformer** | Non-streaming Transformer | ~200ms | Excellent | 100+ | 60MB | Batch/high accuracy |
| **Whisper (Sherpa)** | Offline Transformer | ~500ms | Best | 99 | 150MB | Difficult audio |
| **Platform STT** | Cloud/on-device hybrid | Variable | Excellent | 50+ | 0MB | Arabic, code-switching |
| **SenseVoice** | Multilingual + emotion | ~300ms | Excellent | 50+ | 100MB | Multilingual + LID |

### 3.2 Key Mechanisms: Confidence-Based Escalation

```
AUDIO → VAD → PRIMARY (Vosk streaming)
                    │
                    ▼
            Confidence ≥ 0.8?
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
         YES                  NO
          │                   │
          ▼                   ▼
    ACCEPT RESULT      ESCALATE
          │                   │
          ▼                   ▼
    [Done]          ┌─────────────────┐
                    ▼                 ▼
             SECONDARY (Sherpa Zipformer)
                    │
                    ▼
            Confidence ≥ 0.7?
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
         YES                  NO
          │                   │
          ▼                   ▼
    ACCEPT RESULT      TERTIARY
          │            (Platform STT
          ▼             for Arabic/
                       code-switch)
          [Done]              │
                               ▼
                       Confidence check
                               │
                      ┌────────┴────────┐
                      ▼                 ▼
                    ACCEPT           REJECT
                      │                 │
                      ▼                 ▼
                   [Done]         [Error/Fallback]
```

**Confidence signals:**
- Vosk: `result.conf` field (0-1)
- Sherpa: `result.prob` / token-level probabilities
- Platform: `SpeechRecognizer.CONFIDENCE` (0-1)
- Language ID: SenseVoice or fast LID model

**Escalation triggers:**
1. Low confidence (< threshold)
2. Language mismatch (detected ≠ expected)
3. Recognizer failure (timeout, error)
4. Special domain (proper nouns, numbers) → stronger model

### 3.3 Limitations

1. **Multiple recognizers = more RAM** — cannot run all simultaneously
2. **Latency adds up** — each escalation tier adds latency
3. **Confidence calibration differs** — Vosk 0.8 ≠ Sherpa 0.8
4. **Language detection latency** — need fast LID before routing

### 3.4 Hybrid Design: STTArbitrator

```
┌────────────────────────────────────────────────────────────┐
│                      STTArbitrator                          │
├────────────────────────────────────────────────────────────┤
│  State: IDLE → VOSK_STREAMING → SHERPA_ONESHOT → PLATFORM │
│                                                             │
│  Policy:                                                    │
│  - Default: Vosk (always-on wake + command)                │
│  - Push-to-talk: Platform (Arabic) / Sherpa (English)      │
│  - Fallback: Sherpa Whisper (offline)                       │
│  - Confidence thresholds: configurable per recognizer       │
│  - Language routing: LID → recognizer map                   │
└────────────────────────────────────────────────────────────┘
```

**Recognizer lifecycle:**
- Vosk: ALWAYS_LOADED (wake word requires continuous)
- Sherpa: LOADED_ON_DEMAND (fallback/push-to-talk)
- Platform: SYSTEM_SERVICE (no RAM cost)

### 3.5 Implementation Requirements

- [ ] `STTArbitrator` class with pluggable recognizers
- [ ] Confidence normalization across recognizers
- [ ] Fast language ID (first 1-2 seconds)
- [ ] Recognizer interface: `start()`, `accept_audio()`, `get_result()`, `stop()`
- [ ] Escalation policy configuration
- [ ] Metrics tracking per recognizer
- [ ] Integration with `BodyCoordinator` event loop

---

## 4. Local TTS (Piper + System)

### 4.1 State of the Art

| Engine | Type | Quality | Latency | RAM | Languages | Streaming |
|--------|------|---------|---------|-----|-----------|-----------|
| **Piper (ONNX)** | VITS (flow-based) | Excellent | ~100ms/sent | 80MB/model | 60+ | Sentence |
| **Android System TTS** | Various (Google cloud/on-device) | Good-Excellent | Variable | 0MB (service) | 50+ | Sentence |
| **Sherpa TTS** | VITS/XTTS (ONNX) | Excellent | ~150ms/sent | 100MB | 10+ | Sentence |
| **Coqui TTS** | Various | Good | High | Large | Many | Limited |
| **eSpeak-NG** | Formant synthesis | Robotic | Very fast | <5MB | 100+ | Yes |

### 4.2 Key Mechanisms

**Piper TTS (VITS):**
- Text → phonemes (espeak-ng) → token IDs
- Encoder (transformer) → latent representation
- Flow-based decoder (normalizing flow) → mel spectrogram
- HiFi-GAN vocoder → waveform
- All in ONNX; can run on CPU/GPU/NPU

**Sentence streaming:**
- Split text at sentence boundaries
- Synthesize sentence N while speaking sentence N-1
- Pre-synthesize common phrases (cache)
- Interrupt: stop current, clear queue

**Language routing:**
- Per-sentence language detection
- Switch voice/model per language
- SSML phoneme tags for vocabulary pronunciation

### 4.3 Limitations

1. **Piper models are per-language** — need separate model per voice
2. **No true token streaming** — sentence is minimum unit
3. **Phonemizer (espeak-ng) needed** — adds binary size
4. **Arabic diacritization** — Piper expects vocalized text

### 4.4 Hybrid Design: TTS Abstraction

```
┌────────────────────────────────────────────────────────────┐
│                      TTSManager                             │
├────────────────────────────────────────────────────────────┤
│  Engines (priority order):                                  │
│  1. Piper (local, high quality, EN + AR)                   │
│  2. System TTS (fallback, cloud voices)                    │
│                                                             │
│  Lifecycle per engine:                                      │
│  DORMANT → LOADING → READY → SPEAKING → IDLE → UNLOADING   │
│                                                             │
│  Streaming: sentence-level with pre-synthesis cache         │
│  Interruption: immediate stop + queue clear                 │
│  Vocabulary: SSML <phoneme> tags for learned words          │
└────────────────────────────────────────────────────────────┘
```

### 4.5 Implementation Requirements

- [ ] `TTSManager` with engine registry
- [ ] Piper ONNX wrapper (init, synthesize_stream, interrupt)
- [ ] Sentence splitter (handles EN/AR punctuation)
- [ ] Voice/model lazy loading
- [ ] Cache for common phrases
- [ ] Vocabulary integration (SSML phoneme)
- [ ] Language detection per segment
- [ ] Metrics: synthesis latency, first audio, RAM, interruption latency

---

## 5. Adaptive Inference Policy

### 5.1 Classification Mechanisms

| Class | Examples | Processing | Context | Budget |
|-------|----------|------------|---------|--------|
| **MICRO** | "stop", "yes", "no", "repeat", "cancel" | Pattern match / heuristic | None | <50 tokens |
| **NORMAL** | "What's the weather?", "Set timer for 5 min" | Local LLM (short) | Recent turns (3-5) | 128 tokens |
| **COMPLEX** | "Plan my week", "Explain quantum mechanics" | Local LLM (full) | Memory + context | 512 tokens |
| **SYSTEM** | "Debug memory", "Show capabilities" | Special handlers | Full system state | Variable |

**Classification approaches:**
1. **Keyword/pattern matching** — fast, deterministic, limited coverage
2. **Fast classifier (tiny BERT/distilBERT)** — learned, handles variation
3. **LLM self-classification** — prompt "Classify this request: MICRO/NORMAL/COMPLEX/SYSTEM" — accurate but adds latency
4. **Hybrid** — patterns for obvious cases, classifier for ambiguous

### 5.2 Hybrid Design

```
USER INPUT
    │
    ▼
┌─────────────────────────────────────────┐
│         InferencePolicyClassifier       │
├─────────────────────────────────────────┤
│  1. Pattern match (MICRO: stop/yes/no)  │
│  2. Intent detection (system commands)  │
│  3. Heuristic: length + complexity      │
│  4. Optional: tiny classifier model     │
└─────────────────────────────────────────┘
    │
    ├──▶ MICRO → HeuristicResponse (instant)
    ├──▶ NORMAL → LocalInferenceEngine (budget=128)
    ├──▶ COMPLEX → LocalInferenceEngine (budget=512, memory)
    └──▶ SYSTEM → SystemHandler (special routes)
```

### 5.3 Implementation Requirements

- [ ] `InferencePolicy` enum + classifier
- [ ] Pattern rules for MICRO/SYSTEM
- [ ] Heuristic scoring (length, question words, entities)
- [ ] Configurable thresholds
- [ ] Metrics: classification accuracy, latency added

---

## 6. Context Budgeting

### 6.1 Mechanisms

| Technique | Mechanism | Cost | Effectiveness |
|-----------|-----------|------|---------------|
| **Sliding window** | Keep last N turns | Low | Good for conversation |
| **Importance scoring** | Score each memory item, keep top-K | Medium | Better relevance |
| **Summarization** | Compress old context via LLM | High (needs LLM) | Best compression |
| **Token budgeting** | Hard limit on prompt tokens | Low | Guarantees fit |
| **RAG-style retrieval** | Embed query, retrieve top-K | Medium | Best for facts |
| **Hierarchical context** | Recent (full) + older (summary) | Medium | Balance |

### 6.2 Hybrid Design: AdaptiveContextBuilder

```
BUILD CONTEXT(request, memory_items, system_state)
    │
    ├── 1. ALWAYS INCLUDE
    │     - System prompt (identity, instructions)
    │     - Current turn user input
    │
    ├── 2. RECENT CONVERSATION (priority: HIGH)
    │     - Last N turns (configurable, default 5)
    │     - Trim if token budget exceeded
    │
    ├── 3. RETRIEVED MEMORY (priority: MEDIUM)
    │     - Rank by relevance + recency + type boost
    │     - Take top-K within budget
    │
    ├── 4. USER PROFILE / PREFERENCES (priority: MEDIUM)
    │     - Key facts, preferences, vocabulary
    │
    ├── 5. SYSTEM STATE (priority: LOW)
    │     - Active capabilities, time, device state
    │
    └── 6. COMPRESS IF OVER BUDGET
          - Drop lowest priority first
          - Summarize older turns if needed
          - Hard stop at max_tokens
```

**Token budget allocation (example for 2048 context):**
- System prompt: 200 tokens
- Recent conversation: 600 tokens (5 turns)
- Memory: 800 tokens (top 10 items)
- User profile: 200 tokens
- System state: 100 tokens
- Generation reserve: 148 tokens

### 6.3 Implementation Requirements

- [ ] `ContextBuilder` with priority-based inclusion
- [ ] Token counter (use Qwen3 tokenizer)
- [ ] Configurable budgets per policy class
- [ ] Summarization fallback (when LLM available)
- [ ] Integration with `MemoryStore.retrieve()`

---

## 7. Memory Retrieval Enhancement

### 7.1 Pipeline Stages

```
QUERY
    │
    ▼
┌────────────────────────────────────────────────────────────┐
│  STAGE 1: FAST RETRIEVAL (lexical)                         │
│  - Inverted index / keyword match                          │
│  - Sub-ms latency                                           │
│  - Returns: candidate IDs                                   │
└────────────────────────────────────────────────────────────┘
    │
    ▼
┌────────────────────────────────────────────────────────────┐
│  STAGE 2: SEMANTIC RETRIEVAL (embedding)                   │
│  - Query embedding (tiny model, e.g., MiniLM 33M)          │
│  - Vector similarity (cosine) vs stored embeddings         │
│  - Returns: scored candidates                               │
│  - Latency: ~50ms                                           │
└────────────────────────────────────────────────────────────┘
    │
    ▼
┌────────────────────────────────────────────────────────────┐
│  STAGE 3: ASSOCIATIVE/GRAPH RETRIEVAL                      │
│  - Follow links: tags, entities, temporal adjacency        │
│  - Expand from high-confidence seeds                       │
│  - Returns: connected candidates                            │
└────────────────────────────────────────────────────────────┘
    │
    ▼
┌────────────────────────────────────────────────────────────┐
│  STAGE 4: RANKING & DEDUPLICATION                          │
│  - Combine scores: lexical + semantic + graph + recency    │
│  - Deduplicate by content similarity                       │
│  - Apply type boosts (FACT > EPISODIC > CONTEXT)           │
│  - Apply confidence thresholds                             │
└────────────────────────────────────────────────────────────┘
    │
    ▼
┌────────────────────────────────────────────────────────────┐
│  STAGE 5: CONTEXT COMPRESSION                              │
│  - Truncate to budget                                      │
│  - Merge similar items                                     │
│  - Format for prompt injection                             │
└────────────────────────────────────────────────────────────┘
```

### 7.2 Key Mechanisms

**Fast retrieval:** Inverted index on tokenized content (existing `MemoryStore` does keyword overlap)
**Semantic retrieval:** Requires embedding model — use MiniLM-L6-v2 (22MB ONNX) or distilBERT
**Graph retrieval:** Memory items have tags/entities — build adjacency at store time
**Deduplication:** MinHash / SimHash for near-duplicate detection
**Compression:** Extract key phrases, drop filler

### 7.3 Limitations

1. **Embedding model adds RAM** — ~50MB for MiniLM
2. **Index maintenance** — rebuild on new memories
3. **Cold start** — first query slower (index load)
4. **Arabic embeddings** — need multilingual model

### 7.4 Implementation Requirements

- [ ] `HybridRetriever` orchestrating stages
- [ ] Embedding model integration (optional, can defer)
- [ ] Graph edges in `MemoryStore` (tags → related items)
- [ ] Ranking formula with configurable weights
- [ ] Deduplication (content hash + similarity)
- [ ] Budget enforcement
- [ ] Metrics: retrieval latency, candidates, precision@K

---

## 8. Memory Promotion Policy

### 8.1 Promotion Tiers

| Tier | Lifetime | Criteria | Examples |
|------|----------|----------|----------|
| **IGNORE** | Discard | Low importance, duplicate, noise | "uh huh", "ok", filler |
| **SHORT-TERM** | Session | Conversation context | Current topic, recent facts |
| **SESSION** | App session | Relevant to active task | User's current goal |
| **EPISODIC** | Weeks | Significant exchange | "We discussed vacation plans" |
| **SEMANTIC** | Months | Extracted facts | "User lives in Cairo" |
| **USER-PREFERENCE** | Permanent | Explicit preference | "I prefer metric units" |
| **VOCABULARY** | Permanent | User-taught words | "My name is pronounced X" |
| **IDENTITY** | Permanent | Core identity/values | "I value honesty" |

### 8.2 Promotion Signals

| Signal | Weight | Source |
|--------|--------|--------|
| **Explicit user teaching** | 1.0 | "Remember that..." |
| **Repetition across sessions** | 0.8 | Same fact mentioned 3+ times |
| **Emotional significance** | 0.7 | High arousal/valence in HC |
| **Task relevance** | 0.6 | Related to active goal |
| **Correction** | 0.9 | User corrects JARVIS |
| **Question asked** | 0.5 | User asks "Do you remember?" |
| **Contradiction detected** | 0.8 | HC ConsistencyGuard flags |

### 8.3 Implementation Requirements

- [ ] `MemoryPromoter` evaluating candidates
- [ ] Scoring function with configurable weights
- [ ] Tier assignment logic
- [ ] Integration with `HumanCore.memoryInterface.annotateExchange()`
- [ ] `MemoryStore` tier separation (already has FACT/EPISODIC/PREFERENCE)
- [ ] Promotion audit trail

---

## 9. Resource Governor Enhancement

### 9.1 Current State

`ResourceGovernor.kt` exists with:
- Admission control (memory, CPU, thermal, battery)
- Per-system budgets
- Concurrent system limit

### 9.2 Required Enhancements

**Resource States:**
```
NORMAL → LOW_RAM → HIGH_RAM_PRESSURE → THERMAL_PRESSURE → BATTERY_PRESSURE → BACKGROUND → RECOVERY
```

**Governor Actions per State:**

| State | Model | STT | TTS | Context | Prewarming | Visual |
|-------|-------|-----|-----|---------|------------|--------|
| NORMAL | Loaded | Vosk+Sherpa | Piper+System | Full | Active | Full |
| LOW_RAM | Loaded | Vosk only | Piper only | Reduced | Paused | Reduced |
| HIGH_RAM | Unloaded | Vosk only | System only | Minimal | Disabled | Minimal |
| THERMAL | Unloaded | Vosk only | System only | Minimal | Disabled | Throttled |
| BATTERY | Unloaded | Vosk only | System only | Minimal | Disabled | Throttled |
| BACKGROUND | Suspended | None | None | None | Disabled | Sleep |
| RECOVERY | Reloading | Reloading | Reloading | Rebuilding | Disabled | Recovering |

### 9.3 Implementation Requirements

- [ ] `ResourceState` enum + transitions
- [ ] System monitors: `ActivityManager.getMemoryInfo()`, thermal API, battery
- [ ] Action executor per subsystem
- [ ] Integration with `BodyCoordinator` + `CompanionCore` resource tier
- [ ] Emergency actions (priority-ordered)

---

## 10. Predictive Prewarming

### 10.1 Triggers

| Trigger | Action | Budget |
|---------|--------|--------|
| Speech end detected (VAD) | Warm LLM (load if dormant) | 500ms |
| LLM generation starts | Preload TTS voice | 200ms |
| User presence detected | Warm STT recognizers | 300ms |
| App foreground | Warm all critical paths | 1s |
| Idle > 30s | Unload dormant models | - |

### 10.2 Mechanisms

- **Speculative loading** — start async load, cancel if not needed
- **Warm cache** — keep model weights mmap'd, just instantiate context
- **Prewarm budget** — max 10% RAM, 5% CPU; auto-disable if pressure

---

## 11. Capability Registry

### 11.1 Schema

```kotlin
data class Capability(
    val id: String,
    val version: String,
    val function: String,
    val dependencies: List<String>,
    val model: String?,           // e.g., "qwen3-1.7b-q4km"
    val ramEstimateMb: Long,
    val cpuEstimatePercent: Double,
    val startupCostMs: Long,
    val thermalCost: Int,         // 0-2
    val batteryCost: Int,         // 0-2
    val latencyMs: Long,
    val languages: Set<String>,
    val quality: QualityTier,     // LOW/MEDIUM/HIGH/BEST
    val confidence: Float,        // 0-1 (measured)
    val currentState: CapabilityState,
    val health: HealthStatus,
    val fallback: String?,        // alternative capability ID
    val recoveryStrategy: RecoveryStrategy
)
```

### 11.2 Implementation

- Central registry populated at init
- `BodyCoordinator` + `GlobalNervousSystem` query it
- Health updates from `FailureSurface`

---

## 12. Benchmark Infrastructure

### 12.1 Required Measurements

| Component | Metrics | Method |
|-----------|---------|--------|
| **Model** | Load time, first token, tok/s, peak RAM, avg RAM, CPU%, thermal | Macrobenchmark + custom |
| **STT** | Latency, CPU, RAM, accuracy proxy, confidence, fallback rate | Recorded audio corpus |
| **TTS** | Synthesis latency, playback start, RAM, interrupt latency | Timed calls |
| **Memory** | Retrieval latency, candidates, context size, persist latency | Synthetic queries |
| **App** | Cold/warm start, UI render, orb animation, interaction latency, GC | Macrobenchmark |

### 12.2 Android Macrobenchmark

- Critical user journeys: cold launch, voice interaction, conversation UI, orb rendering
- Baseline Profiles for startup optimization
- Physical device testing (emulator not representative)

---

## 13. Summary: Research Conclusions

| Capability | Recommended Approach | Key Mechanism |
|------------|---------------------|---------------|
| **Local LLM** | llama.cpp CPU (Q4_K_M) + optional Vulkan benchmark | GGUF + KV cache + lifecycle state machine |
| **VAD** | Silero VAD ONNX (1.3 MB) | LSTM frame classifier + adaptive threshold |
| **STT** | STTArbitrator: Vosk → Sherpa Zipformer → Platform | Confidence escalation + language routing |
| **TTS** | TTSManager: Piper (EN/AR) → System fallback | Sentence streaming + SSML phoneme + lazy load |
| **Inference Policy** | Hybrid: patterns + heuristic + optional tiny classifier | 4-class routing with token budgets |
| **Context** | Priority-based builder with hard token budget | Sliding window + retrieval + compression |
| **Memory Retrieval** | Hybrid: lexical → semantic → graph → rank → compress | Multi-stage pipeline with deduplication |
| **Memory Promotion** | Signal-weighted tier assignment | 8 tiers from IGNORE to IDENTITY |
| **Resource Governor** | 7 states with per-subsystem actions | Admission control + emergency degradation |
| **Prewarming** | Trigger-based speculative loading | Speech-end → LLM, generation-start → TTS |
| **Capability Registry** | Central metadata store | Query by nervous system for routing |

---

## 14. Next Steps

1. **Implement LocalInferenceEngine** (JNI + llama.cpp)
2. **Integrate Silero VAD** (ONNX Runtime dependency)
3. **Build STTArbitrator** (confidence escalation)
4. **Create TTSManager** (Piper + System abstraction)
5. **Add InferencePolicy classifier**
6. **Enhance ContextBuilder** (token budgeting)
7. **Upgrade MemoryStore** (hybrid retrieval)
8. **Implement MemoryPromoter**
9. **Extend ResourceGovernor** (states + actions)
10. **Add PredictivePrewarmer**
11. **Build CapabilityRegistry**
12. **Create benchmark suite** (Macrobenchmark + custom)
13. **Run device tests** (15 scenarios)
14. **Generate benchmark report**
15. **Write milestone report**

---

*This research follows the mandated process: DISCOVER → COMPARE → EXTRACT MECHANISMS → IDENTIFY LIMITATIONS → COMBINE COMPLEMENTARY MECHANISMS → DESIGN HYBRID → BENCHMARK → IMPLEMENT → VALIDATE*