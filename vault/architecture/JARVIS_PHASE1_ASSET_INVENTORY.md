# JARVIS Phase 1 Asset Inventory

**Generated:** 2026-08-09  
**Source Directory:** `vault/just_downloaded/`  
**Purpose:** Complete inventory of all downloaded assets for Phase 1 integration assessment

---

## 1. Large Language Models

### 1.1 Qwen3-1.7B-Q4_K_M.gguf

| Property | Value |
|----------|-------|
| **Format** | GGUF (version 3) |
| **Architecture** | Qwen3 |
| **Parameters** | 1.7B |
| **Quantization** | Q4_K_M (4-bit, medium quality) |
| **Tensors** | 311 |
| **Metadata entries** | 34 |
| **Size** | 1.28 GB (1,282,439,264 bytes) |
| **License** | Apache 2.0 (Qwen license) |
| **Runtime** | llama.cpp / llama.cpp Android |
| **Context window** | 32,768 tokens (model spec) |
| **Languages** | Multilingual: English, Chinese, Arabic, 100+ others |
| **RAM requirement (loaded)** | ~1.5–2 GB (model + KV cache) |
| **CPU requirement** | ARM64 NEON optimized; 4–8 threads recommended |
| **GPU/Vulkan** | llama.cpp supports Vulkan compute on Android |
| **Startup cost** | ~500ms–2s (model load + first token) |
| **Persistent runtime cost** | ~1.5 GB RAM when loaded |
| **Latency (tokens/sec)** | ~8–20 tok/s on mobile CPU (varies by device) |
| **Use case** | Primary local LLM for reasoning, conversation, tool use |

**Integration Assessment:**
- ✅ **RECOMMENDED** — Best fit for primary local brain
- Compatible with existing `LlamaCppAdapter` architecture (needs local inference boundary)
- Must implement: lazy loading, model lifecycle (DORMANT→PREWARMING→READY→GENERATING→IDLE→SUSPENDED→UNLOADING)
- Current `LlamaCppAdapter` expects remote llama-server — needs Android-native local inference engine
- Q4_K_M quantization balances quality/size for 8GB device

---

## 2. Voice Activity Detection

### 2.1 Silero VAD (Multiple variants in zip + standalone)

| Property | Value |
|----------|-------|
| **Format** | ONNX |
| **Models available** | `silero_vad.onnx` (2.3 MB), `silero_vad_v6.2.1.onnx` (2.3 MB), `silero_vad_16k_op15.onnx` (1.3 MB), `silero_vad_half.onnx` (1.3 MB), `silero_vad_op18_ifless.onnx` (2.8 MB), `silero_vad_openvino_16k.onnx` (1.3 MB) |
| **Size** | 1.3–2.8 MB per model |
| **License** | MIT (Silero license) |
| **Runtime** | ONNX Runtime (Android: onnxruntime-android) |
| **Sample rate** | 8 kHz or 16 kHz variants |
| **Input** | Float32 audio frames (512 samples @ 16kHz = 32ms) |
| **Output** | Speech probability [0,1] |
| **Languages** | Language-agnostic (audio-only) |
| **RAM requirement** | ~10–20 MB (model + buffers) |
| **CPU requirement** | Very low (~1–2% CPU) |
| **Startup cost** | ~50–100ms (model load) |
| **Persistent runtime cost** | Negligible |
| **Latency** | <10ms per inference (real-time capable) |
| **Use case** | Audio gate → VAD → speech start/end detection |

**Integration Assessment:**
- ✅ **RECOMMENDED** — Lightweight, production-grade VAD
- Should replace/augment current wake-word-only approach
- Implement: Audio Gate → VAD → speech start → wake/STT → speech end → release resources
- Multiple ONNX variants: use `silero_vad_16k_op15.onnx` (1.3 MB, OP15 compatible) for Android
- Adaptive thresholds per environment profile

---

## 3. Speech-to-Text (STT)

### 3.1 Sherpa ONNX (zip: sherpa-onnx-master.zip)

| Property | Value |
|----------|-------|
| **Format** | ONNX + Android AAR |
| **Models** | Multiple: Zipformer, Whisper, Paraformer, Nemotron, SenseVoice |
| **License** | Apache 2.0 |
| **Runtime** | sherpa-onnx (C++/Java/Android) |
| **Android support** | Native AAR + JNI (arm64-v8a, armeabi-v7a) |
| **Streaming** | Yes (Zipformer, Paraformer streaming) |
| **Offline** | Yes (Whisper, Paraformer offline) |
| **Languages** | 100+ (multilingual models) |
| **VAD built-in** | Yes (Silero VAD integration) |
| **Punctuation** | Yes (offline punctuation models) |
| **RAM requirement** | ~50–200 MB depending on model |
| **CPU requirement** | Moderate (2–4 threads) |
| **GPU/NPU** | NCNN/Vulkan/NCNN CPU/GPU backends |
| **Startup cost** | ~200–500ms |
| **Latency (streaming)** | ~100–300ms RTF |
| **Existing integration** | `JarvisSherpaWhisper.kt` uses whisper-tiny.en (assets) |

**Integration Assessment:**
- ✅ **RECOMMENDED** — Already partially integrated
- Current implementation uses `whisper-tiny.en` from assets (fallback only)
- Should upgrade to streaming Zipformer for primary English STT
- Add Arabic model (SenseVoice or Paraformer multilingual)
- Implement `STTArbitrator` for confidence-based escalation

### 3.2 Vosk (Already integrated)

| Property | Value |
|----------|-------|
| **Format** | Vosk model format |
| **Model** | `vosk-model-small-en-us-0.15` (~40 MB) |
| **License** | Apache 2.0 |
| **Runtime** | Vosk Android (JNI) |
| **Streaming** | Yes (continuous) |
| **Languages** | English (current), others available |
| **Wake word** | Grammar-constrained (`["jarvis", "hey jarvis", "[unk]"]`) |
| **Command mode** | Unconstrained after wake |
| **RAM requirement** | ~100–150 MB |
| **CPU requirement** | Low (continuous decode) |
| **Latency** | ~200–500ms |
| **Existing integration** | `JarvisVosk.kt` — fully integrated, dual-recognizer design |

**Integration Assessment:**
- ✅ **KEEP** — Working well as wake-word + command STT
- Grammar-constrained wake phase + unconstrained command phase is good design
- Keep as primary always-on wake word detector

### 3.3 Platform SpeechRecognizer (Android)

| Property | Value |
|----------|-------|
| **Format** | System API |
| **Languages** | Arabic (ar-EG), English (en-US), many others |
| **Runtime** | Android system service |
| **Latency** | Variable (network-dependent for cloud) |
| **Offline** | Limited (on-device models on newer Android) |
| **Existing integration** | `PlatformRecognizerStt.kt` — push-to-talk fallback |

**Integration Assessment:**
- ✅ **KEEP** — Essential for Arabic STT where local models are weak
- Use as fallback for Arabic / code-switched input
- Confidence-based escalation in STTArbitrator

---

## 4. Text-to-Speech (TTS)

### 4.1 Piper TTS Models (ONNX)

| Property | Value |
|----------|-------|
| **Models** | `ar_JO-kareem-medium.onnx` (63 MB), `en_US-lessac-medium.onnx` (63 MB) |
| **Format** | ONNX (Piper TTS) |
| **License** | MIT (Piper) / CC-BY (voices) |
| **Runtime** | Piper TTS / ONNX Runtime |
| **Sample rate** | 22050 Hz (typical) |
| **Voices** | Arabic (Jordan - Kareem), English (US - Lessac) |
| **Quality** | Medium (natural, not maximum) |
| **RAM requirement** | ~80–120 MB per model loaded |
| **CPU requirement** | Moderate |
| **Latency** | ~50–150ms per sentence |
| **Streaming** | Sentence-level possible |

**Integration Assessment:**
- ✅ **RECOMMENDED** — High-quality local TTS for both languages
- Replace/augment Android system TTS (`JarvisTts.kt`)
- Arabic support: critical for Egyptian Arabic requirement
- Implement lazy loading + engine lifecycle in TTS abstraction
- Sentence streaming via `StreamingTts.kt`

### 4.2 Android System TTS (Already integrated)

| Property | Value |
|----------|-------|
| **Format** | System service |
| **Engine** | Google TTS (preferred) |
| **Languages** | 50+ |
| **Quality** | High (network voices) |
| **Offline** | Limited |
| **Existing integration** | `JarvisTts.kt` + `StreamingTts.kt` |

**Integration Assessment:**
- ✅ **KEEP** — Fallback when Piper models unavailable
- Voice selection logic in `JarvisTts.kt` is good
- SSML pacing support exists

---

## 5. Wake Word Detection

### 5.1 openWakeWord (zip: openWakeWord-main.zip)

| Property | Value |
|----------|-------|
| **Format** | TensorFlow Lite / ONNX |
| **Models** | Multiple pre-trained + custom training |
| **License** | MIT |
| **Runtime** | TFLite / ONNX Runtime |
| **Architecture** | CNN-based wake word |
| **Custom words** | Yes (training pipeline included) |
| **RAM requirement** | ~5–20 MB |
| **CPU requirement** | Very low |
| **Latency** | <50ms |
| **False positive rate** | Low (tunable) |

**Integration Assessment:**
- ⚠️ **EVALUATE** — Could replace Vosk wake-word grammar
- Vosk's grammar-constrained approach works but is English-only
- openWakeWord supports custom wake words, multilingual
- Would require new integration; Vosk already working
- **Decision:** Keep Vosk for now; evaluate if Arabic wake word needed

---

## 6. Other Assets

### 6.1 tokenizer.json / tokenizer_config.json

| Property | Value |
|----------|-------|
| **Format** | HuggingFace tokenizer (JSON) |
| **Model** | Qwen3 (matches Qwen3-1.7B) |
| **Vocab size** | ~151,643 tokens |
| **Use case** | Local tokenization for Qwen3 (prompt budgeting, context management) |

### 6.2 sentencepiece.bpe.model

| Property | Value |
|----------|-------|
| **Format** | SentencePiece BPE |
| **Size** | 5 MB |
| **Use case** | Alternative tokenizer (possibly for other models) |

### 6.3 Large model files (unidentified)

| File | Size | Assessment |
|------|------|------------|
| `model.onnx` | 470 MB | Large ONNX model — likely Whisper or similar STT |
| `model_O4.onnx` | 235 MB | Optimized variant |
| `model_qint8_arm64.onnx` | 118 MB | INT8 quantized for ARM64 |
| `model_qint8_avx512_vnni.onnx` | 118 MB | INT8 quantized for x86 AVX512 |
| `inference.pdiparams` | 8.9 MB | PaddlePaddle model params |
| `openvino_model.bin` | 470 MB | OpenVINO IR format |
| `pytorch_model.bin` | 470 MB | PyTorch checkpoint |

**Assessment:** These appear to be variants of the same large model (likely Whisper-large or similar). The ARM64 INT8 version (`model_qint8_arm64.onnx`, 118 MB) is most relevant for Android. However, Sherpa ONNX already provides optimized ASR models — these may be redundant.

**Decision:** ❌ **REJECT** — Redundant with Sherpa ONNX models; larger, less optimized for mobile

### 6.4 llama.cpp-master.zip (38 MB)

| Property | Value |
|----------|-------|
| **Content** | Full llama.cpp source |
| **Use case** | Reference for Android NDK build |
| **Android support** | `android/` directory with CMake/NDK build |

**Integration Assessment:**
- ✅ **REFERENCE** — Use for building local inference engine
- Contains Android build scripts, Vulkan/CPU backends
- Do not bundle entire source; extract only needed integration patterns

---

## 7. Existing JARVIS Capabilities (for comparison)

| Capability | Current Implementation | Status |
|------------|----------------------|--------|
| Wake word | Vosk (grammar-constrained) | ✅ Working |
| Command STT | Vosk (unconstrained after wake) | ✅ Working |
| Fallback STT | Sherpa Whisper (whisper-tiny.en) | ✅ Working |
| Arabic STT | Platform SpeechRecognizer | ✅ Working |
| TTS | Android System TTS (Google) | ✅ Working |
| Streaming TTS | `StreamingTts.kt` (sentence-level) | ✅ Working |
| Local LLM | LlamaCppAdapter (remote llama-server) | ⚠️ Needs local engine |
| VAD | None (Vosk continuous) | ❌ Missing |
| Memory | `MemoryStore.kt` + `VocabularyStore.kt` | ✅ Working |
| Resource Governor | `ResourceGovernor.kt` | ✅ Working |
| Failure System | `FailureSurface`, `RecoveryController`, `CircuitBreaker` | ✅ Working |
| Nervous System | `BodyCoordinator` + `GlobalNervousSystem` | ✅ Working |
| Human Core | `HumanCore` (personality, memory, values) | ✅ Working |

---

## 8. Integration Decisions Summary

| Asset | Decision | Rationale |
|-------|----------|-----------|
| Qwen3-1.7B-Q4_K_M.gguf | ✅ **INTEGRATE** | Primary local brain; create `LocalInferenceEngine` |
| Silero VAD (ONNX) | ✅ **INTEGRATE** | Audio gate; adaptive speech detection |
| Sherpa ONNX (streaming Zipformer) | ✅ **INTEGRATE** | Primary STT (English + multilingual) |
| Vosk | ✅ **KEEP** | Wake word + command STT (proven) |
| Platform SpeechRecognizer | ✅ **KEEP** | Arabic fallback |
| Piper TTS (ar/en ONNX) | ✅ **INTEGRATE** | Local high-quality TTS for both languages |
| Android System TTS | ✅ **KEEP** | Fallback |
| openWakeWord | ⚠️ **DEFER** | Vosk works; evaluate if Arabic wake needed |
| Large unidentified ONNX models | ❌ **REJECT** | Redundant, unoptimized for mobile |
| llama.cpp-master.zip | 📖 **REFERENCE** | Build patterns for LocalInferenceEngine |
| Tokenizers | ✅ **INTEGRATE** | Qwen3 tokenization for context budgeting |

---

## 9. Architecture Gaps to Address in Phase 1

1. **LocalInferenceEngine** — Android-native GGUF inference (llama.cpp) with lifecycle management
2. **STTArbitrator** — Confidence-based routing: Vosk → Sherpa → Platform
3. **AudioGate + VAD** — Silero VAD integration with adaptive thresholds
4. **TTS Abstraction** — Unified Piper + System TTS with lazy loading
5. **Inference Policy** — MICRO/NORMAL/COMPLEX/SYSTEM classification
6. **Context Builder** — Adaptive context with budgeting
7. **Memory Promotion** — Policy-driven promotion (IGNORE→SHORT-TERM→SESSION→EPISODIC→SEMANTIC→USER-PREFERENCE→VOCABULARY→IDENTITY)
8. **Capability Registry** — Central registry with resource/quality metadata
9. **Resource Governor Enhancement** — States: NORMAL/LOW_RAM/HIGH_RAM_PRESSURE/THERMAL/BATTERY/BACKGROUND/RECOVERY
10. **Predictive Prewarming** — Speech-end → model warm; generation-start → TTS prep
11. **Benchmark Infrastructure** — Device measurements for all components