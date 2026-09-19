# JARVIS Repository Inventory Report

**Generated:** 2025-08-09
**Repository:** `/mnt/sdcard/jarvis-repo`

---

## A. DOWNLOADED MODEL ASSETS (Raw Files)

| Asset | Location | Size | Status |
|-------|----------|------|--------|
| **Vosk STT** (vosk-model-small-en-us-0.15) | `/vosk-model-small-en-us-0.15/` + `mobile/app/src/main/assets/vosk-model-small-en-us-0.15/` | ~50MB | **ACTIVE** — used by `JarvisVosk.kt` |
| **Sherpa Whisper-tiny.en** | `/sherpa-onnx-whisper-tiny.en/` + `mobile/app/src/main/assets/sherpa-onnx-whisper-tiny.en/` | ~100MB | **ACTIVE** — used by `JarvisSherpaWhisper.kt` |
| **Sherpa Streaming Zipformer** (encoder/decoder/joiner .onnx) | `/models/sherpa-onnx-streaming-zipformer-en-2023-06-21/` | ~540MB | **UNUSED** — no adapter loads this |
| **Qwen3-1.7B-Q4_K_M.gguf** | `/vault/just_downloaded/Qwen3-1.7B-Q4_K_M.gguf` | 1.3GB | **UNUSED** — no llama.cpp adapter wired to GGUF |
| **Piper TTS (Arabic + English ONNX)** | `/vault/just_downloaded/ar_JO-kareem-medium.onnx`, `en_US-lessac-medium.onnx` | ~63MB each | **UNUSED** — no Piper adapter |
| **Silero VAD ONNX** | `/vault/just_downloaded/silero_vad_v6.2.1.onnx` | 2.3MB | **UNUSED** — no VAD integration |
| **llama.cpp source** | `/vault/just_downloaded/llama.cpp-master.zip` | 38MB | **SOURCE ONLY** — not built |
| **sherpa-onnx source** | `/vault/just_downloaded/sherpa-onnx-master.zip` | 14MB | **SOURCE ONLY** — not built |
| **openWakeWord source** | `/vault/just_downloaded/openWakeWord-main.zip` | 3MB | **SOURCE ONLY** — not built |
| **Silero VAD source** | `/vault/just_downloaded/silero-vad-master.zip` | 28MB | **SOURCE ONLY** — not built |
| **ONNX Runtime lib** | `/jniLibs/*/libonnxruntime.so` | 25MB | **ACTIVE** — Sherpa uses it |
| **Sherpa JNI libs** | `/jniLibs/*/libsherpa-onnx-*.so` | ~5MB | **ACTIVE** — Sherpa uses them |

---

## B. EXISTING SUBSYSTEMS (Code)

| Subsystem | Files | Model Dependency | Status |
|-----------|-------|------------------|--------|
| **Vosk STT** | `JarvisVosk.kt`, `VoskBridge.kt` | vosk-model-small-en-us-0.15 (assets) | **REAL** — native Android JNI |
| **Sherpa Whisper** | `JarvisSherpaWhisper.kt`, `SherpaBridge.kt` | whisper-tiny.en ONNX (assets) | **REAL** — sherpa-onnx JNI |
| **Platform STT** | `PlatformRecognizerStt.kt` | Android `SpeechRecognizer` | **REAL** — system API |
| **Android TTS** | `JarvisTts.kt`, `TtsBridge.kt` | Google TTS engine | **REAL** — system API |
| **Streaming TTS** | `StreamingTts.kt` | Google TTS engine | **REAL** — sentence-level streaming |
| **LlamaCppAdapter** | `LlamaCppAdapter.kt` | llama.cpp server (HTTP) | **REAL ADAPTER** — but no server running |
| **OllamaAdapter** | `OllamaAdapter.kt` | Ollama server (HTTP) | **REAL ADAPTER** — but no server running |
| **RemoteJarvisAdapter** | `RemoteJarvisAdapter.kt` | Another JARVIS host (HTTP) | **REAL ADAPTER** — but no remote host |
| **HeuristicAdapter** | `HeuristicAdapter.kt` | **NONE** — keyword rules | **MOCK/HEURISTIC** — fallback only |
| **MemoryStore** | `MemoryStore.kt` | Local JSONL files | **REAL** — fact/episodic/preference storage |
| **VocabularyStore** | `VocabularyStore.kt` | Local JSONL files | **REAL** — user-taught pronunciations |
| **HumanCore** | `HumanCore.kt` +17 modules | HeuristicAdapter (default) | **REAL LOGIC** — but model port is heuristic |
| **CompanionCore** | 30+ files (presence, render, engine) | HumanCore state | **REAL** — orb reactor & presence |
| **Failure System** | 8 files (CircuitBreaker, RecoveryController, etc.) | NONE | **REAL** — fault containment |
| **LatencyLayer** | `LatencyLayer.kt`, `LatencyPipeline.kt` | ModelManager | **REAL** — fast/slow path orchestrator |
| **BodyCoordinator** | `BodyCoordinator.kt` | All voice subsystems | **REAL** — 13-state nervous system |

---

## C. ADAPTERS: REAL vs MOCKED

| Adapter | Type | Requires External Service? | Currently Functional? |
|---------|------|---------------------------|----------------------|
| `HeuristicAdapter` | **MOCK/HEURISTIC** | No | ✅ Yes (always works) |
| `LlamaCppAdapter` | Real HTTP adapter | llama.cpp server on :8080 | ❌ No server |
| `OllamaAdapter` | Real HTTP adapter | Ollama server on :11434 | ❌ No server |
| `RemoteJarvisAdapter` | Real HTTP adapter | Another JARVIS host | ❌ No host |
| `ModelBackedModelPort` | Real interface | Any active adapter | ❌ Falls back to heuristic |

**The entire model fabric runs on HeuristicAdapter by default.** No LLM is actually loaded.

---

## D. DUPLICATES / REDUNDANCIES

| Duplicate | Locations | Notes |
|-----------|-----------|-------|
| Vosk model | Root `/vosk-model-small-en-us-0.15/` + `mobile/app/src/main/assets/` | Same model copied twice |
| Sherpa Whisper | Root `/sherpa-onnx-whisper-tiny.en/` + `mobile/app/src/main/assets/` | Same model copied twice |
| Sherpa Zipformer | `/models/sherpa-onnx-streaming-zipformer-en-2023-06-21/` + `vault/just_downloaded/sherpa-onnx-master.zip` | Two copies of streaming model |
| TTS Systems | `JarvisTts.kt` (legacy) + `StreamingTts.kt` (new) | Both use Android TTS; legacy retained for fallback |
| STT Bridges | `VoskBridge.kt`, `SherpaBridge.kt`, `TtsBridge.kt` | Thin wrappers; could be inlined |

---

## E. UNUSED / ORPHANED ASSETS

| Asset | Why Unused |
|-------|------------|
| **Sherpa Streaming Zipformer** (encoder/decoder/joiner .onnx) | No `StreamingRecognizer` adapter; only `OfflineRecognizer` used |
| **Qwen3-1.7B-Q4_K_M.gguf** | No GGUF loader; `LlamaCppAdapter` expects HTTP server, not local GGUF |
| **Piper TTS ONNX** (Arabic + English) | No Piper adapter; `StreamingTts` uses Android TTS only |
| **Silero VAD ONNX** | No VAD integration; `JarvisMic` uses energy-based endpointing |
| **openWakeWord** | Not integrated; Vosk handles wake word via grammar |
| **llama.cpp / sherpa-onnx / openWakeWord source zips** | Source only; not built into JNI libs |

---

## F. MISSING ADAPTERS (Assets exist but no code to use them)

| Missing Adapter | Asset Available | Effort |
|-----------------|-----------------|--------|
| **GGUF/Llama.cpp Local Adapter** | Qwen3-1.7B-Q4_K_M.gguf (1.3GB) | High — needs llama.cpp Android build + JNI |
| **Piper TTS Adapter** | ar_JO-kareem-medium.onnx, en_US-lessac-medium.onnx | Medium — Piper has ONNX runtime support |
| **Silero VAD Adapter** | silero_vad_v6.2.1.onnx | Low — simple ONNX inference |
| **Sherpa Streaming STT Adapter** | Zipformer encoder/decoder/joiner | Medium — requires `OnlineRecognizer` config |
| **openWakeWord Adapter** | openWakeWord models | Medium — TensorFlow Lite or ONNX |

---

## G. ONLY MOCKED / HEURISTIC (No Real Implementation)

| Component | What's Mocked |
|-----------|---------------|
| **LLM Reasoning** | `HeuristicAdapter.generateHeuristicResponse()` — keyword matching only |
| **Model Port** | `ModelBackedModelPort.requestChat()` → falls back to heuristic when no server |
| **Embeddings / Semantic Search** | `MemoryStore.retrieve()` uses word-overlap scoring, not embeddings |
| **Wake Word** | Vosk grammar `["jarvis", "hey jarvis", "[unk]"]` — not a trained KWS model |
| **VAD** | Energy threshold (RMS >35) in `JarvisMic` — not ML-based |
| **Language Detection** | `LanguageRouter.detectLanguage()` — simple script/character heuristics |
| **Code-switching** | `LanguageRouter.detectSegments()` — per-word script detection only |

---

## H. ARCHITECTURAL GAPS

| Gap | Impact |
|-----|--------|
| **No local LLM** | All reasoning is heuristic keyword matching |
| **No embeddings** | Memory retrieval is lexical (word overlap), not semantic |
| **No neural VAD** | Energy threshold fails in noisy environments |
| **No neural wake word** | Vosk grammar approach is brittle |
| **No Piper/Coqui TTS** | Locked to Android system TTS quality |
| **No streaming STT** | Only offline Whisper fallback; no Zipformer streaming |
| **No model download/management** | Models hardcoded in assets; no dynamic loading |

---

## SUMMARY

The codebase has a **complete, well-architected nervous system** (BodyCoordinator, HumanCore, CompanionCore, Failure system) with **real STT/TTS pipelines** (Vosk, Sherpa Whisper, Android TTS). However, the **brain is entirely heuristic** — no LLM is actually running. Five major model assets sit downloaded but unused because the adapters to load them locally don't exist.

**Critical Path to Real Intelligence:**
1. Build llama.cpp for Android (JNI) → wire GGUF adapter → load Qwen3-1.7B
2. Add Piper TTS adapter for Arabic/English neural voices
3. Add Silero VAD for robust speech detection
4. Add Sherpa Streaming Zipformer for low-latency streaming STT
5. Replace heuristic memory retrieval with embeddings (sentence-transformers ONNX)