# Task Report — STT Arbitrator Integration & Kotlin Compile Fix

**Task:** Build `STTArbitrator` with confidence-based escalation (Task #3), integrating the new `JarvisSherpaZipformer` recognizer and repairing the Kotlin compilation across the codebase.

**Result:** ✅ `gradle :mobile:app:compileDebugKotlin` → **BUILD SUCCESSFUL** — zero Kotlin errors.

**Date:** 2026-08-10

---

## 1. Root cause

The Kotlin compile was broken by a cluster of errors with two origins:

1. **`JarvisSherpaZipformer.kt` used a wrong sherpa-onnx API.** The file was written against an assumed API. The actual `sherpa-onnx-v1.12.40-api.jar` was decompiled (`javap`) to recover the real signatures.
2. **New subsystems referencing each other's APIs incorrectly.** The new `capability/`, `policy/`, `stt/`, `vad/`, `model/adapters/`, `tts/`, `body/` subsystems disagreed on data classes, enum shapes, parameter names, and coroutine semantics.

## 2. What was done

### 2.1 `JarvisSherpaZipformer.kt` — rewritten against the real sherpa-onnx API

Decompiled the on-device jar to get the true signatures, then rewrote the streaming Zipformer recognizer:

- `OnlineRecognizerConfig(featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80), modelConfig = OnlineModelConfig(transducer = OnlineTransducerModelConfig(encoder/decoder/joiner), tokens, numThreads, provider, modelType), enableEndpoint, decodingMethod, maxActivePaths, hotwordsScore, blankPenalty)`
- `OnlineStream.acceptWaveform(floats, 16000)` — **parameter order is `(samples, sampleRate)`**, not the reverse the old code assumed
- `recognizer.decode(stream)` and `recognizer.getResult(stream).text` (not `decodeStream`)
- `start()` verifies the 4 required assets exist (`encoder.onnx`, `decoder.onnx`, `joiner.onnx`, `tokens.txt`) before creating the recognizer, so a missing model bundle degrades to non-fatal `false` instead of crashing the STT path
- Exposes `transcribe(ShortArray)`, `feedAudio(ShortArray)`, `finalizeUtterance()`, `shutdown()`, `isReady()`
- `start()` failure emits a `FailureReport` with `FailureCategory.STT` (corrected from the non-existent `PERFORMANCE` category)

### 2.2 `STTArbitrator.kt` — integration & fixes

- Wired the new recognizer: `JarvisSherpaZipformer(assetManager = context.assets, onFailure = onFailure)` — the constructor takes `AssetManager`, not `Context`
- Fixed `return allLoaded` inside `withContext` → `return@withContext`
- Handled nullable `getNextRecognizer()` at both call sites (resource-budget escalation and failed-recognizer path now emit `Decision.Failed` when no recognizer remains)
- `SttResult(..., 0L, ...)` — the latency param is `Long`, not `Int`
- Replaced the `"${x:.2f}"` Kotlin template (invalid — Kotlin has no format specifiers in templates) with `String.format("%.2f", …)`
- `ArbitrationMetrics` converted from mutable `var`s to immutable `val`s + `copy()` so `StateFlow.update {}` returns the new value
- Nested `ArbitrationMetrics` can't read the outer class's `escalationCount` — now passed explicitly as a `wasEscalated` parameter
- Added `kotlinx.coroutines.withContext` and `kotlinx.coroutines.flow.update` imports

### 2.3 `LocalInferenceEngine.kt` — GGUF adapter repairs

- `enum class NativeState` declared `val ordinal: Int`, **shadowing `Enum.ordinal`** → removed the constructor param (enum order already matches the C++ values)
- `configure()` was marked `override` but **is not part of the `ModelProvider` interface** → removed `override`; it now writes to a private `providerConfig` backing field exposed via `config: ProviderConfig? get()`
- `config.contextWindow`/`threads`/`gpuLayers`/`temperature`/`topP`/`topK` don't exist on `ProviderConfig` → read from `config.parameters["context_window" | "threads" | …]` with `toIntOrNull()`/`toFloatOrNull()` fallbacks
- `nativeHandle != 0` compared `Long` to `Int` → all zero-checks/assignments are now `0L`
- `return` inside `synchronized` blocks (non-inline function) in `load()`/`unload()` → restructured to compute a value inside the lock and return after
- `request.topK` doesn't exist on `GenerateRequest` → hardcoded `40`
- Streaming: `emit()` can't be called from the JNI `TokenCallback` (not a coroutine context) → rewrote with a `Channel<StreamEvent>` + a `launch`ed generator inside `coroutineScope`, drained into the flow
- `requestChat` override re-specified default args that the interface already declares → defaults removed
- Fixed `tokenUsage()` `return`-inside-`withContext`

### 2.4 `TTSEngine.kt`

- Removed bogus `import kotlinx.coroutines.await` (no such symbol) — `CompletableDeferred.await()` is a member, not a top-level import; added `kotlinx.coroutines.withContext` instead
- `operator fun <T> fold(...)` — `fold` is not a valid operator name → removed `operator`
- `override val quality = Quality.HIGH` → `TTSEngine.Quality.HIGH` (nested enum isn't auto-scoped into implementors); same for `Quality.BEST` in `StreamingTTSEngine`
- `estimatedLatencyMs = 300` / `ramEstimateMb = 20` (Int) override `Long` interface props → `300L` / `20L` / `150L` / `30L`
- `engine.setVolume(...)` doesn't exist on `TextToSpeech` → volume moved into the speak `Bundle` via `TextToSpeech.Engine.KEY_PARAM_VOLUME`
- `tts?.availableVoices` doesn't exist → `tts?.voices`; `it.networkRequired` → `it.isNetworkConnectionRequired`
- `StreamingTTSEngine`: `streamingTts.stream(...)` doesn't exist → use `speakStreaming(...)` and return a no-op `TtsStream`; `language: String` → mapped to `LanguageRouter.Language?` via a `mapLanguage()` helper; `isSpeaking` is `StateFlow<Boolean>` → `.value`
- `initialize()` awaited a `CompletableDeferred` **inside** `synchronized` (suspension point in critical section) → await moved outside the lock
- `return allSuccess` inside `withContext` → `return@withContext`
- `selectBestEngine` called the suspend `healthCheck()` from a non-suspend fun and `.await()` on its result → health filter removed (checked lazily elsewhere)

### 2.5 `SileroVadManager.kt`

- `enum class State`/`Profile` declared `val ordinal: Int` shadowing `Enum.ordinal` → removed the constructor params (enum order matches native indices)
- All `nativeHandle` zero-comparisons/assignments: `0` → `0L`
- Added `kotlinx.coroutines.withContext` import

### 2.6 `AdaptiveContextBuilder.kt` — rewritten

Removed dependency on the human-core protocol types that don't match this builder's needs (`Exchange`/`SessionContext` fields it referenced don't exist in that shape). Now defines its own lightweight inputs:

- `ConversationTurn(userText, jarvisText)` and `SystemContextInput(currentLanguage, turnCount, activeTask)`
- `RankedMemory` now holds the top-level `MemoryItem` (was `MemoryStore.MemoryItem`, which doesn't exist — `MemoryItem` is a top-level class in `BodyTypes.kt`)
- Full token-budgeted context building: recency/relevance/importance ranking, section compression, resource-pressure scaling, per-section `ContextBreakdown` metrics

### 2.7 `MemoryPromotion.kt`

- `private const val TAG` inside a class (only legal top-level/companion) → moved to file level
- `MemoryStore.MemoryItem` → top-level `MemoryItem`
- `PromotionStats.record()` returned `Unit` → now returns the updated `PromotionStats` (immutable `copy()` style) so `StateFlow.update {}` compiles
- Added `kotlinx.coroutines.flow.update` and `kotlin.math.abs` imports

### 2.8 `CapabilityArbitrator.kt` / `CapabilityRegistry.kt`

- `ResourceBudget`/`Genome` built with wrong package + param names → `com.jarvis.app.genome.Genome` / `com.jarvis.app.genome.ResourceBudget` with `maxMemoryMb`/`maxCpuPercent`
- Genome constructed with a non-existent `metadata` param → removed
- `admission.granted` → `admission.admitted` (the real `AdmissionResult` field)
- `CapabilityRegistry.getFallback()` was missing its `return` (result of `?.let` discarded) → `return capability.fallback?.let { … }`
- `ArbitrationStats` mutable + `record()` returning `Unit` → immutable `copy()` style
- Added `kotlinx.coroutines.withContext` and `kotlinx.coroutines.flow.update` imports

### 2.9 `InferencePolicyLayer.kt`

- `PolicyStats.record()` returned `Unit` → immutable `copy()` style returning the new stats
- Added `kotlinx.coroutines.flow.update` import

### 2.10 `ResourceGovernor.kt`

- Added a side-effect-free `canAdmit(memoryMb: Long, cpuPercent: Double): Boolean` convenience method (pure check against the current policy + device state, no registration), which `STTArbitrator` calls for the resource-budget gate.

## 3. Verification

```
gradle :mobile:app:compileDebugKotlin --no-daemon
→ BUILD SUCCESSFUL in 1m 43s — 0 Kotlin errors
```

(Full APK build not run in this session — only the Kotlin compilation task was verified.)

## 4. Files touched

| File | Change |
|------|--------|
| `mobile/app/src/main/java/com/jarvis/app/JarvisSherpaZipformer.kt` | **Rewritten** against real sherpa-onnx API |
| `mobile/app/src/main/java/com/jarvis/app/stt/STTArbitrator.kt` | Fixed: API, coroutines, nullability, metrics |
| `mobile/app/src/main/java/com/jarvis/app/model/adapters/LocalInferenceEngine.kt` | Fixed: enum shadow, config, Long, streaming |
| `mobile/app/src/main/java/com/jarvis/app/tts/TTSEngine.kt` | Fixed: imports, types, Bundle volume, lock/await |
| `mobile/app/src/main/java/com/jarvis/app/vad/SileroVadManager.kt` | Fixed: enum shadow, `0L` |
| `mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt` | **Rewritten**: own input types, top-level `MemoryItem` |
| `mobile/app/src/main/java/com/jarvis/app/body/MemoryPromotion.kt` | Fixed: const-val placement, stats, imports |
| `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityArbitrator.kt` | Fixed: Genome/ResourceBudget, `admitted`, stats |
| `mobile/app/src/main/java/com/jarvis/app/capability/CapabilityRegistry.kt` | Fixed: missing `return` |
| `mobile/app/src/main/java/com/jarvis/app/policy/InferencePolicyLayer.kt` | Fixed: stats, import |
| `mobile/app/src/main/java/com/jarvis/app/resource/ResourceGovernor.kt` | Added `canAdmit()` helper |

## 5. Follow-ups / notes

- **Model assets not verified at runtime.** `JarvisSherpaZipformer.start()` degrades gracefully when `assets/sherpa-onnx-streaming-zipformer-en/` is absent; the 2026-08-09 inventory report (`JARVIS_INVENTORY_REPORT.md`) listed the streaming Zipformer bundle (~540MB) as present under `/models/sherpa-onnx-streaming-zipformer-en-2023-06-21/` but **not yet copied into `app/src/main/assets/`**. Copying it in is required for the Zipformer tier to actually engage at runtime.
- **Native libs (`jarvis_llama_jni`, `jarvis_silero_vad`) are referenced by `System.loadLibrary`** — the JNI sources are untracked (`mobile/app/src/main/cpp/`); whether they build is not covered by the Kotlin compile.
- **Platform Arabic STT** path is a stub (returns `null` → "not immediately available").
- The full APK (`assembleDebug`) was not run; only `compileDebugKotlin` was verified green.
