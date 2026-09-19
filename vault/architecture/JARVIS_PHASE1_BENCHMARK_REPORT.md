# JARVIS Phase 1 Benchmark Report

**Generated:** 2026-08-09  
**Status:** Template — awaiting device measurements  
**Purpose:** Benchmark infrastructure, methodology, and results for all Phase 1 capabilities

---

## 1. Benchmark Infrastructure

### 1.1 Android Macrobenchmark Setup

**Module:** `:benchmarks` (new Gradle module)

```kotlin
// benchmarks/build.gradle.kts
dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
    implementation("androidx.test:rules:1.5.0")
    implementation("androidx.test:runner:1.5.2")
    implementation("androidx.test.uiautomator:uiautomator:2.2.0")
}
```

**Build Type:** `benchmark` (release-optimized, debuggable)

```kotlin
// mobile/app/build.gradle.kts
buildTypes {
    create("benchmark") {
        initWith(release)
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += "release"
        isDebuggable = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

### 1.2 Baseline Profiles

**Critical User Journeys (CUJs):**

| CUJ | Description | Target |
|-----|-------------|--------|
| `CUJ_COLD_START` | App launch from cold to interactive Orb | < 2.5s |
| `CUJ_WARM_START` | App launch from warm to interactive Orb | < 800ms |
| `CUJ_WAKE_WORD` | "Jarvis" → wake detected → listening state | < 500ms |
| `CUJ_VOICE_EN` | English command → STT → LLM → TTS → speech start | < 800ms |
| `CUJ_VOICE_AR` | Arabic command → STT → LLM → TTS → speech start | < 1200ms |
| `CUJ_CODE_SWITCH` | Mixed ar/en sentence → correct routing → response | < 1000ms |
| `CUJ_CONVERSATION_UI` | Scroll 50 messages in conversation | 60fps |
| `CUJ_ORB_RENDER` | Orb animation steady state | 60fps, < 16ms/frame |

**Baseline Profile Generation:**
```bash
./gradlew :benchmarks:connectedBaselineProfile
```

**Output:** `mobile/app/src/main/baseline-prof.txt` → bundled in APK

### 1.3 Custom Benchmark Harness

**Location:** `mobile/app/src/androidTest/java/com/jarvis/app/benchmark/`

```kotlin
// BenchmarkRunner.kt
class BenchmarkRunner {
    companion object {
        fun measureColdStart(): BenchmarkResult
        fun measureWarmStart(): BenchmarkResult
        fun measureWakeWord(): BenchmarkResult
        fun measureVoiceInteraction(language: String): BenchmarkResult
        fun measureMemoryRetrieval(): BenchmarkResult
        fun measureModelInference(): BenchmarkResult
        fun measureSTTLatency(recognizer: String): BenchmarkResult
        fun measureTTSLatency(engine: String): BenchmarkResult
        fun measureVisualFPS(): BenchmarkResult
        fun measureGC(): BenchmarkResult
    }
}

data class BenchmarkResult(
    val name: String,
    val iterations: Int,
    val medianMs: Double,
    val p90Ms: Double,
    val p99Ms: Double,
    val minMs: Double,
    val maxMs: Double,
    val stdDevMs: Double,
    val metadata: Map<String, Any>
)
```

### 1.4 Resource Monitoring During Benchmarks

```kotlin
// ResourceMonitor.kt
class ResourceMonitor {
    fun start()  // Begin sampling
    fun stop(): ResourceReport  // End and generate report
}

data class ResourceReport(
    val durationMs: Long,
    val peakPssMb: Long,
    val avgPssMb: Double,
    val peakCpuPercent: Double,
    val avgCpuPercent: Double,
    val thermalEvents: List<ThermalEvent>,
    val gcCount: Int,
    val gcTotalMs: Long,
    val allocatedMb: Long,
    val freedMb: Long
)
```

---

## 2. Benchmark Methodology

### 2.1 Device Preparation

1. **Factory reset** or clean profile
2. **Disable** battery optimization for JARVIS
3. **Disable** adaptive brightness, auto-rotate
4. **Set** screen timeout to 30 min
5. **Charge** to 80%, unplug, let settle 10 min
6. **Close** all other apps
7. **Enable** developer options: "Show surface updates", "GPU rendering profile"

### 2.2 Test Execution

| Benchmark | Iterations | Warmup | Cooldown |
|-----------|------------|--------|----------|
| Cold start | 10 | N/A | 30s |
| Warm start | 20 | 3 | 10s |
| Wake word | 50 | 5 | 5s |
| Voice EN | 30 | 5 | 10s |
| Voice AR | 20 | 3 | 15s |
| Code-switch | 20 | 3 | 15s |
| Memory retrieval | 100 | 10 | 2s |
| Model inference | 20 | 5 | 10s |
| Visual FPS | 60s continuous | 5s | N/A |
| GC pressure | During conversation | N/A | N/A |

### 2.3 Statistical Rigor

- **Warmup runs discarded** — JIT compilation, class loading
- **Median reported** — robust to outliers
- **P90/P99** — tail latency matters for UX
- **StdDev** — consistency indicator
- **Min/Max** — bounds checking
- **Confidence** — 95% CI via bootstrap (1000 resamples)

### 2.4 Thermal Protocol

1. Measure ambient temp
2. Run benchmarks in order: cold → warm → voice → memory → visual
3. **Pause 60s** between benchmark groups
4. **Abort** if thermal = CRITICAL
5. Record thermal state per iteration

---

## 3. Benchmark Definitions

### 3.1 Model Benchmarks

| Benchmark | Description | Metrics |
|-----------|-------------|---------|
| `model.load_cold` | Load Qwen3-1.7B from disk to READY | load_ms, peak_ram_mb |
| `model.load_warm` | Load when mmap cached | load_ms, peak_ram_mb |
| `model.first_token` | Prompt → first token (128 tok budget) | first_token_ms, tok_s |
| `model.stream_512` | Generate 512 tokens streaming | tok_s, total_ms, ram_mb |
| `model.cancel` | Cancel mid-generation | cancel_latency_ms |
| `model.unload` | Unload model, free memory | unload_ms, ram_freed_mb |
| `model.health` | Health check latency | health_ms |

### 3.2 STT Benchmarks

| Benchmark | Description | Metrics |
|-----------|-------------|---------|
| `stt.vosk_wake` | "Jarvis" detection latency | detect_ms, false_pos_rate |
| `stt.vosk_command` | Command transcription (3s audio) | latency_ms, wer_estimate |
| `stt.sherpa_zipformer` | Streaming EN (5s audio) | latency_ms, rtf, wer_estimate |
| `stt.sherpa_whisper` | Offline Whisper (5s audio) | latency_ms, wer_estimate |
| `stt.platform_ar` | Arabic STT (system) | latency_ms, wer_estimate |
| `stt.arbitrator` | Full arbitration path | total_ms, escalation_rate |

**Test Corpus:** Pre-recorded audio samples (10 EN commands, 10 AR commands, 5 code-switched, 5 noise)

### 3.3 TTS Benchmarks

| Benchmark | Description | Metrics |
|-----------|-------------|---------|
| `tts.piper_en.first` | First sentence synthesis + play | synthesis_ms, first_audio_ms |
| `tts.piper_en.stream` | 5-sentence streaming | sentence_ms, gap_ms |
| `tts.piper_ar.first` | Arabic first sentence | synthesis_ms, first_audio_ms |
| `tts.system` | System TTS fallback | latency_ms |
| `tts.interrupt` | Interrupt mid-speech | interrupt_latency_ms |
| `tts.preload` | Pre-synthesize cache | cache_hit_latency_ms |

### 3.4 VAD Benchmarks

| Benchmark | Description | Metrics |
|-----------|-------------|---------|
| `vad.silero.speech` | Speech detection latency | detect_ms, false_neg_rate |
| `vad.silero.silence` | Silence detection latency | detect_ms, false_pos_rate |
| `vad.adaptive` | Threshold adaptation | adaptation_cycles |

### 3.5 Memory Benchmarks

| Benchmark | Description | Metrics |
|-----------|-------------|---------|
| `memory.retrieve` | Hybrid retrieval (1000 items) | latency_ms, candidates, precision |
| `memory.store` | Store conversation exchange | latency_ms |
| `memory.promote` | Promotion evaluation | latency_ms |
| `memory.persist` | Disk write | latency_ms, size_kb |

### 3.6 Visual Benchmarks

| Benchmark | Description | Metrics |
|-----------|-------------|---------|
| `visual.orb.idle` | Idle state 60s | fps, frame_time_p99, cpu% |
| `visual.orb.listening` | Listening animation | fps, frame_time_p99 |
| `visual.orb.thinking` | Thinking animation | fps, frame_time_p99 |
| `visual.orb.speaking` | Speaking (waveform-driven) | fps, frame_time_p99 |
| `visual.conversation` | Scroll 100 messages | fps, jank_frames |

### 3.7 System Benchmarks

| Benchmark | Description | Metrics |
|-----------|-------------|---------|
| `system.cold_start` | Process start → Orb interactive | time_ms, pss_mb |
| `system.warm_start` | Activity resume → Orb interactive | time_ms |
| `system.background_5min` | Background → foreground | reload_ms, state_correct |
| `system.low_battery` | 5% battery simulation | functional, latency |
| `system.thermal` | Thermal throttling simulation | graceful_degradation |

---

## 4. Results Template

### 4.1 Model Results

| Benchmark | Median | P90 | P99 | StdDev | Unit | Target | Pass |
|-----------|--------|-----|-----|--------|------|--------|------|
| model.load_cold | TBD | TBD | TBD | TBD | ms | < 2000 | ⬜ |
| model.load_warm | TBD | TBD | TBD | TBD | ms | < 500 | ⬜ |
| model.first_token | TBD | TBD | TBD | TBD | ms | < 500 | ⬜ |
| model.stream_512 | TBD | TBD | TBD | TBD | tok/s | > 8 | ⬜ |
| model.cancel | TBD | TBD | TBD | TBD | ms | < 100 | ⬜ |
| model.unload | TBD | TBD | TBD | TBD | ms | < 500 | ⬜ |
| model.peak_ram | TBD | TBD | TBD | TBD | MB | < 2000 | ⬜ |

### 4.2 STT Results

| Benchmark | Median | P90 | P99 | StdDev | Unit | Target | Pass |
|-----------|--------|-----|-----|--------|------|--------|------|
| stt.vosk_wake | TBD | TBD | TBD | TBD | ms | < 300 | ⬜ |
| stt.vosk_command | TBD | TBD | TBD | TBD | ms | < 500 | ⬜ |
| stt.sherpa_zipformer | TBD | TBD | TBD | TBD | ms | < 300 | ⬜ |
| stt.sherpa_whisper | TBD | TBD | TBD | TBD | ms | < 800 | ⬜ |
| stt.platform_ar | TBD | TBD | TBD | TBD | ms | < 1500 | ⬜ |
| stt.arbitrator.escalation_rate | TBD | - | - | - | % | < 20% | ⬜ |

### 4.3 TTS Results

| Benchmark | Median | P90 | P99 | StdDev | Unit | Target | Pass |
|-----------|--------|-----|-----|--------|------|--------|------|
| tts.piper_en.first | TBD | TBD | TBD | TBD | ms | < 200 | ⬜ |
| tts.piper_en.stream_gap | TBD | TBD | TBD | TBD | ms | < 100 | ⬜ |
| tts.piper_ar.first | TBD | TBD | TBD | TBD | ms | < 250 | ⬜ |
| tts.interrupt | TBD | TBD | TBD | TBD | ms | < 50 | ⬜ |

### 4.4 VAD Results

| Benchmark | Median | P90 | P99 | StdDev | Unit | Target | Pass |
|-----------|--------|-----|-----|--------|------|--------|------|
| vad.speech_detect | TBD | TBD | TBD | TBD | ms | < 100 | ⬜ |
| vad.silence_detect | TBD | TBD | TBD | TBD | ms | < 200 | ⬜ |
| vad.false_positive_rate | TBD | - | - | - | % | < 5% | ⬜ |
| vad.false_negative_rate | TBD | - | - | - | % | < 2% | ⬜ |

### 4.5 Memory Results

| Benchmark | Median | P90 | P99 | StdDev | Unit | Target | Pass |
|-----------|--------|-----|-----|--------|------|--------|------|
| memory.retrieve | TBD | TBD | TBD | TBD | ms | < 100 | ⬜ |
| memory.precision_at_5 | TBD | - | - | - | % | > 80% | ⬜ |
| memory.store | TBD | TBD | TBD | TBD | ms | < 50 | ⬜ |

### 4.6 Visual Results

| Benchmark | Median FPS | P99 Frame Time | Jank % | CPU % | Target | Pass |
|-----------|------------|----------------|--------|-------|--------|------|
| visual.orb.idle | TBD | TBD | TBD | TBD | 60fps, <16ms, <1% | ⬜ |
| visual.orb.listening | TBD | TBD | TBD | TBD | 60fps, <16ms, <1% | ⬜ |
| visual.orb.thinking | TBD | TBD | TBD | TBD | 60fps, <16ms, <1% | ⬜ |
| visual.orb.speaking | TBD | TBD | TBD | TBD | 60fps, <16ms, <1% | ⬜ |
| visual.conversation | TBD | TBD | TBD | TBD | 60fps, <16ms, <1% | ⬜ |

### 4.7 System Results

| Benchmark | Median | P90 | P99 | Unit | Target | Pass |
|-----------|--------|-----|-----|------|--------|------|
| system.cold_start | TBD | TBD | TBD | ms | < 2500 | ⬜ |
| system.warm_start | TBD | TBD | TBD | ms | < 800 | ⬜ |
| system.background_reload | TBD | TBD | TBD | ms | < 1000 | ⬜ |
| system.steady_pss | TBD | TBD | TBD | MB | < 400 | ⬜ |

---

## 5. Device Test Matrix (15 Scenarios)

| # | Scenario | Steps | Expected | Status |
|---|----------|-------|----------|--------|
| 1 | Cold launch | Fresh install → open app | Orb interactive < 2.5s | ⬜ |
| 2 | Wake word | Say "Jarvis" → wake detected | < 500ms, visual feedback | ⬜ |
| 3 | English conversation | "What time is it?" → response | < 800ms end-to-end | ⬜ |
| 4 | Egyptian Arabic | "إزيك يا جارفيس" → response | < 1200ms, AR TTS | ⬜ |
| 5 | Code switching | "Jarvis, ما الوقت now?" | Correct routing, mixed TTS | ⬜ |
| 6 | Memory retrieval | "What did I say about X?" | Retrieves correct fact | ⬜ |
| 7 | Long conversation | 20 turns back-and-forth | No degradation, context kept | ⬜ |
| 8 | Interruption | Speak during TTS → barge-in | Immediate stop, new turn | ⬜ |
| 9 | Repeated conversations | 5 sessions over 1 hour | Memory promotion works | ⬜ |
| 10 | Background/foreground | Home → 2 min → reopen | State preserved, fast reload | ⬜ |
| 11 | Low battery | 5% battery (simulated) | Degraded but functional | ⬜ |
| 12 | Thermal pressure | CPU stress → thermal throttle | Graceful degradation, no crash | ⬜ |
| 13 | Low RAM | Fill RAM → voice interaction | Unloads model, uses heuristic | ⬜ |
| 14 | Model reload | After unload → new request | Reloads, responds correctly | ⬜ |
| 15 | Failure recovery | Kill STT → request → fallback | Recovers via arbitrator | ⬜ |

---

## 6. Measurement Tools

| Tool | Purpose | Command |
|------|---------|---------|
| `adb shell dumpsys meminfo` | PSS, heap, native | `adb shell dumpsys meminfo com.jarvis.app` |
| `adb shell dumpsys gfxinfo` | Frame timing | `adb shell dumpsys gfxinfo com.jarvis.app` |
| `adb shell perfetto` | System trace | `adb shell perfetto -c /data/misc/perfetto-configs/jarvis.pbtx` |
| `adb shell am start -W` | Launch time | `adb shell am start -W com.jarvis.app/.MainActivity` |
| `adb logcat -b all` | Logs | `adb logcat -b all -v time | grep JARVIS` |
| `simpleperf` | CPU profiling | `adb shell simpleperf record -g -p <pid> --duration 10` |
| `systrace` / `perfetto` | Full trace | Record during benchmark runs |

---

## 7. CI/CD Integration

```yaml
# .github/workflows/benchmark.yml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  benchmark:
    runs-on: [self-hosted, android, pixel8]  # Physical device runner
    steps:
      - uses: actions/checkout@v4
      - name: Build benchmark APK
        run: ./gradlew :benchmarks:assembleBenchmark
      - name: Install APK
        run: adb install -r benchmarks/build/outputs/apk/benchmark/debug/benchmarks-benchmark.apk
      - name: Run Macrobenchmark
        run: ./gradlew :benchmarks:connectedBenchmarkAndroidTest
      - name: Upload results
        uses: actions/upload-artifact@v4
        with:
          name: benchmark-results
          path: benchmarks/build/outputs/connected_android_test_additional_output/
      - name: Compare with baseline
        run: |
          python scripts/compare_benchmarks.py \
            --current benchmarks/build/outputs/... \
            --baseline .github/benchmark-baselines/main.json
```

---

## 8. Current Status: PENDING DEVICE TESTING

| Category | Status | Notes |
|----------|--------|-------|
| **Infrastructure** | ✅ Designed | Macrobenchmark + custom harness ready |
| **Baseline Profiles** | ⬜ Not generated | Requires device |
| **Model Benchmarks** | ⬜ Pending | Requires LocalInferenceEngine implementation |
| **STT Benchmarks** | ⬜ Pending | Requires STTArbitrator + Sherpa Zipformer |
| **TTS Benchmarks** | ⬜ Pending | Requires Piper integration |
| **VAD Benchmarks** | ⬜ Pending | Requires Silero VAD integration |
| **Memory Benchmarks** | ⬜ Pending | Requires HybridRetriever |
| **Visual Benchmarks** | ✅ Ready | Existing orb can be benchmarked |
| **System Benchmarks** | ⬜ Pending | Requires full integration |
| **Device Test Matrix** | ⬜ Pending | 15 scenarios defined |

---

## 9. Next Steps

1. **Implement LocalInferenceEngine** (JNI + llama.cpp)
2. **Integrate Silero VAD** (ONNX Runtime)
3. **Build STTArbitrator** with Sherpa Zipformer
4. **Create TTSManager** with Piper models
5. **Add benchmark module** to Gradle
6. **Generate Baseline Profiles** on device
7. **Run all benchmarks** on physical device
8. **Populate results tables** above
9. **Compare with targets** → iterate
10. **Finalize benchmark report**

---

*This report template will be populated with actual measurements during device testing phase. All "TBD" values will be replaced with measured data. The target columns represent Phase 1 acceptance criteria.*