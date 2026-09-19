# JARVIS Phase 1 Resource Model

**Generated:** 2026-08-09  
**Target Device:** Android phone, 8 GB RAM  
**Purpose:** Explicit configurable budgets, resource states, governor actions, and thresholds for architectural concurrency with computational selectivity

---

## 1. Device Resource Baseline

### 1.1 Physical Constraints

| Resource | Total | Reserved (OS) | Available to App | Notes |
|----------|-------|---------------|------------------|-------|
| **RAM** | 8 GB | ~2.5 GB | **~5.5 GB** | Varies by OEM, Android version; measure at runtime |
| **CPU** | 8 cores (typical) | 2 cores (system) | **6 cores** | Big.LITTLE: 2-4 perf + 4-6 efficiency |
| **GPU** | Adreno/Mali | Shared | **Available** | Vulkan compute support varies |
| **NPU** | Optional (DSP/HTA) | System | **If present** | Not required; CPU fallback |
| **Storage** | 128+ GB | System | **App sandbox** | filesDir + cacheDir |
| **Battery** | 4000-5000 mAh | N/A | **Full** | Monitor level + charging state |
| **Thermal** | N/A | N/A | **Monitor** | PowerManager thermal API |

### 1.2 Measured Baselines (Target)

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| Cold app start | < 2.5s | Macrobenchmark |
| Warm app start | < 800ms | Macrobenchmark |
| Voice interaction latency (end-to-end) | < 800ms | Device test |
| Orb render (steady 60fps) | < 16ms/frame | Systrace |
| GC pressure (alloc rate) | < 10 MB/s | Profiler |
| PSS (steady state) | < 400 MB | dumpsys meminfo |

---

## 2. Resource Budget Allocation

### 2.1 Global Budget (Configurable)

```kotlin
data class ResourceBudget(
    // Memory
    val totalMemoryMb: Long = 5500,          // Available to app (measured)
    val reservedMemoryMb: Long = 500,        // Emergency reserve
    val maxModelMemoryMb: Long = 2000,       // Max for any single model
    val maxSttMemoryMb: Long = 300,          // STT subsystem budget
    val maxTtsMemoryMb: Long = 200,          // TTS subsystem budget
    val maxMemoryMemoryMb: Long = 150,       // Memory subsystem budget
    val maxVisualMemoryMb: Long = 50,        // Visual subsystem budget
    val maxOverheadMemoryMb: Long = 300,     // Framework, buffers, etc.

    // CPU
    val totalCpuPercent: Double = 80.0,      // Leave 20% for OS
    val maxModelCpuPercent: Double = 50.0,   // Model inference
    val maxSttCpuPercent: Double = 25.0,     // STT continuous
    val maxTtsCpuPercent: Double = 20.0,     // TTS synthesis
    val maxBackgroundCpuPercent: Double = 10.0,

    // Thermal
    val thermalThrottleThreshold: Int = 1,   // 0=normal, 1=throttled, 2=critical
    val thermalCriticalThreshold: Int = 2,

    // Battery
    val minBatteryPercent: Int = 10,         // Below: aggressive powersave
    val lowBatteryPercent: Int = 20,         // Below: moderate powersave
    val allowWhileCharging: Boolean = true,

    // Concurrency
    val maxConcurrentModels: Int = 1,
    val maxConcurrentStt: Int = 2,           // Vosk + one fallback
    val maxConcurrentTts: Int = 1,
    val maxConcurrentHeavy: Int = 3,         // Total heavy subsystems

    // Timeouts
    val modelIdleUnloadMs: Long = 120_000,   // 2 minutes
    val sttIdleUnloadMs: Long = 60_000,      // 1 minute
    val ttsIdleUnloadMs: Long = 30_000,      // 30 seconds
    val prewarmBudgetMs: Long = 5_000,       // Max prewarm time
)
```

### 2.2 Per-Capability Budgets (from Capability Matrix)

| Capability | RAM (MB) | CPU (%) | Startup (ms) | Thermal | Battery | Priority |
|------------|----------|---------|--------------|---------|---------|----------|
| **llm.qwen3_1b7_q4km** | 1500 | 40 | 800 | 2 | 2 | CRITICAL |
| **stt.vosk_wake** | 120 | 15 | 300 | 1 | 1 | CRITICAL |
| **stt.vosk_command** | 120 | 20 | 100 | 1 | 1 | HIGH |
| **stt.sherpa_zipformer** | 80 | 25 | 400 | 1 | 1 | MEDIUM |
| **stt.sherpa_whisper** | 150 | 30 | 500 | 1 | 1 | LOW |
| **tts.piper_en** | 100 | 30 | 300 | 1 | 1 | HIGH |
| **tts.piper_ar** | 100 | 30 | 300 | 1 | 1 | HIGH |
| **tts.system** | 0 | 10 | 100 | 0 | 1 | FALLBACK |
| **vad.silero** | 15 | 2 | 80 | 0 | 0 | MEDIUM |
| **memory.hybrid** | 80 | 10 | 200 | 1 | 0 | HIGH |
| **visual.orb** | 20 | 8 | 100 | 1 | 1 | MEDIUM |

**Total CRITICAL path:** ~1740 MB RAM, 75% CPU  
**Total with MEDIUM fallbacks:** ~2100 MB RAM, 95% CPU (over budget → selective loading)

---

## 3. Resource States

### 3.1 State Definitions

```kotlin
enum class ResourceState(
    val ordinal: Int,
    val description: String,
    val ramLimitPercent: Double,      // % of totalMemoryMb
    val cpuLimitPercent: Double,      // % of totalCpuPercent
    val allowModelLoad: Boolean,
    val allowPrewarm: Boolean,
    val visualQuality: VisualQuality,
    val sttMode: SttMode,
    val ttsMode: TtsMode,
    val contextBudgetPercent: Double  // % of max context tokens
) {
    NORMAL(0, "Normal operation", 1.0, 1.0, true, true, VisualQuality.FULL, SttMode.FULL, TtsMode.FULL, 1.0),
    LOW_RAM(1, "Available RAM < 60%", 0.6, 0.8, true, false, VisualQuality.REDUCED, SttMode.VOSK_ONLY, TtsMode.PIPER_ONLY, 0.7),
    HIGH_RAM_PRESSURE(2, "Available RAM < 40%", 0.4, 0.6, false, false, VisualQuality.MINIMAL, SttMode.VOSK_ONLY, TtsMode.SYSTEM_ONLY, 0.4),
    THERMAL_PRESSURE(3, "Device throttling", 0.5, 0.4, false, false, VisualQuality.THROTTLED, SttMode.VOSK_ONLY, TtsMode.SYSTEM_ONLY, 0.3),
    BATTERY_PRESSURE(4, "Battery < 20%", 0.5, 0.5, false, false, VisualQuality.THROTTLED, SttMode.VOSK_ONLY, TtsMode.SYSTEM_ONLY, 0.4),
    BACKGROUND(5, "App backgrounded", 0.1, 0.1, false, false, VisualQuality.SLEEP, SttMode.NONE, TtsMode.NONE, 0.0),
    RECOVERY(6, "Recovering from pressure", 0.7, 0.7, true, true, VisualQuality.REDUCED, SttMode.VOSK_ONLY, TtsMode.PIPER_ONLY, 0.5)
}

enum class VisualQuality { FULL, REDUCED, MINIMAL, THROTTLED, SLEEP }
enum class SttMode { FULL, VOSK_ONLY, NONE }
enum class TtsMode { FULL, PIPER_ONLY, SYSTEM_ONLY, NONE }
```

### 3.2 State Transitions

```
NORMAL
    │
    ├── RAM < 60% ──────────────────────▶ LOW_RAM
    │                                      │
    │                                      ├── RAM < 40% ──────▶ HIGH_RAM_PRESSURE
    │                                      │
    │                                      └── RAM > 70% ──────▶ NORMAL
    │
    ├── Thermal = THROTTLED ────────────▶ THERMAL_PRESSURE
    │                                      │
    │                                      └── Thermal = NORMAL ─▶ NORMAL (or LOW_RAM)
    │
    ├── Battery < 20% ──────────────────▶ BATTERY_PRESSURE
    │                                      │
    │                                      └── Battery > 30% ───▶ NORMAL (or LOW_RAM)
    │
    └── App backgrounded ───────────────▶ BACKGROUND
                                              │
                                              └── App foregrounded ──▶ RECOVERY → NORMAL
```

**Transition Rules:**
- Only one state active at a time (highest ordinal wins)
- Hysteresis: require 5% margin before returning to better state
- Minimum dwell time: 10s in any state before transition
- Emergency transition (CRITICAL thermal/battery) bypasses hysteresis

---

## 4. Governor Actions per State

### 4.1 Action Priority Order

When entering a constrained state, execute in order:

1. **Stop optional work** — cancel background evolution, research, non-critical sync
2. **Reduce context** — shrink LLM context budget per `contextBudgetPercent`
3. **Reduce inference budget** — lower max tokens, temperature
4. **Unload dormant models** — call `unload()` on SUSPENDED/IDLE models
5. **Stop prewarming** — cancel all speculative loads
6. **Downgrade capabilities** — switch to fallback modes (VOSK_ONLY, SYSTEM_ONLY)
7. **Pause nonessential visual** — reduce orb frame rate, disable particles
8. **Recover** — when conditions improve, reverse in opposite order

### 4.2 Detailed Actions

```kotlin
sealed interface GovernorAction {
    data class StopOptionalWork(val tasks: List<String>) : GovernorAction
    data class ReduceContext(val newBudgetPercent: Double) : GovernorAction
    data class ReduceInferenceBudget(val maxTokens: Int, val temperature: Float) : GovernorAction
    data class UnloadModel(val capabilityId: String, val reason: String) : GovernorAction
    data class StopPrewarming : GovernorAction
    data class DowngradeCapability(
        val capabilityId: String,
        val newMode: String,  // e.g., "VOSK_ONLY"
        val fallbackId: String
    ) : GovernorAction
    data class ThrottleVisual(val targetFps: Int, val disableParticles: Boolean) : GovernorAction
    data class EmergencyUnloadAll(val preserveCritical: Set<String>) : GovernorAction
}

fun ResourceState.actions(previousState: ResourceState): List<GovernorAction> {
    return when (this) {
        LOW_RAM -> listOf(
            GovernorAction.StopOptionalWork(listOf("evolution", "research", "sync")),
            GovernorAction.ReduceContext(0.7),
            GovernorAction.StopPrewarming(),
            GovernorAction.DowngradeCapability("stt.sherpa_zipformer", "VOSK_ONLY", "stt.vosk_command"),
            GovernorAction.DowngradeCapability("tts.piper_en", "PIPER_ONLY", "tts.system"),
        )
        HIGH_RAM_PRESSURE -> listOf(
            GovernorAction.StopOptionalWork(listOf("evolution", "research", "sync", "prewarming")),
            GovernorAction.ReduceContext(0.4),
            GovernorAction.ReduceInferenceBudget(128, 0.5f),
            GovernorAction.UnloadModel("llm.qwen3_1b7_q4km", "HIGH_RAM_PRESSURE"),
            GovernorAction.UnloadModel("stt.sherpa_zipformer", "HIGH_RAM_PRESSURE"),
            GovernorAction.UnloadModel("stt.sherpa_whisper", "HIGH_RAM_PRESSURE"),
            GovernorAction.UnloadModel("tts.piper_en", "HIGH_RAM_PRESSURE"),
            GovernorAction.UnloadModel("tts.piper_ar", "HIGH_RAM_PRESSURE"),
            GovernorAction.DowngradeCapability("stt.vosk_command", "VOSK_ONLY", "stt.vosk_wake"),
            GovernorAction.DowngradeCapability("tts.manager", "SYSTEM_ONLY", "tts.system"),
            GovernorAction.ThrottleVisual(30, true),
        )
        THERMAL_PRESSURE -> listOf(
            GovernorAction.StopOptionalWork(listOf("evolution", "research", "sync", "prewarming")),
            GovernorAction.ReduceContext(0.3),
            GovernorAction.ReduceInferenceBudget(64, 0.3f),
            GovernorAction.UnloadModel("llm.qwen3_1b7_q4km", "THERMAL_PRESSURE"),
            GovernorAction.UnloadModel("stt.sherpa_zipformer", "THERMAL_PRESSURE"),
            GovernorAction.UnloadModel("stt.sherpa_whisper", "THERMAL_PRESSURE"),
            GovernorAction.UnloadModel("tts.piper_en", "THERMAL_PRESSURE"),
            GovernorAction.UnloadModel("tts.piper_ar", "THERMAL_PRESSURE"),
            GovernorAction.DowngradeCapability("visual.orb", "THROTTLED", "visual.simple"),
            GovernorAction.ThrottleVisual(15, true),
        )
        BATTERY_PRESSURE -> listOf(
            GovernorAction.StopOptionalWork(listOf("evolution", "research", "sync", "prewarming")),
            GovernorAction.ReduceContext(0.4),
            GovernorAction.ReduceInferenceBudget(128, 0.5f),
            GovernorAction.UnloadModel("llm.qwen3_1b7_q4km", "BATTERY_PRESSURE"),
            GovernorAction.UnloadModel("stt.sherpa_zipformer", "BATTERY_PRESSURE"),
            GovernorAction.UnloadModel("tts.piper_en", "BATTERY_PRESSURE"),
            GovernorAction.UnloadModel("tts.piper_ar", "BATTERY_PRESSURE"),
            GovernorAction.DowngradeCapability("visual.orb", "THROTTLED", "visual.simple"),
            GovernorAction.ThrottleVisual(20, true),
        )
        BACKGROUND -> listOf(
            GovernorAction.StopOptionalWork(listOf("all")),
            GovernorAction.ReduceContext(0.0),
            GovernorAction.UnloadModel("llm.qwen3_1b7_q4km", "BACKGROUND"),
            GovernorAction.UnloadModel("stt.sherpa_zipformer", "BACKGROUND"),
            GovernorAction.UnloadModel("stt.sherpa_whisper", "BACKGROUND"),
            GovernorAction.UnloadModel("tts.piper_en", "BACKGROUND"),
            GovernorAction.UnloadModel("tts.piper_ar", "BACKGROUND"),
            GovernorAction.DowngradeCapability("stt.vosk_wake", "NONE", ""),
            GovernorAction.DowngradeCapability("tts.manager", "NONE", ""),
            GovernorAction.DowngradeCapability("visual.orb", "SLEEP", "visual.simple"),
            GovernorAction.ThrottleVisual(0, true),
        )
        RECOVERY -> listOf(
            GovernorAction.DowngradeCapability("visual.orb", "REDUCED", "visual.simple"),
            GovernorAction.ThrottleVisual(30, false),
        )
        NORMAL -> listOf()  // No restrictive actions; subsystems manage own lifecycle
    }
}
```

### 4.3 Recovery Actions (RECOVERY → NORMAL)

```kotlin
fun ResourceState.recoveryActions(): List<GovernorAction> = when (this) {
    RECOVERY -> listOf(
        GovernorAction.DowngradeCapability("visual.orb", "FULL", "visual.simple"),
        // Note: Models are lazily reloaded on demand, not eagerly
    )
    else -> emptyList()
}
```

---

## 5. Monitoring & Measurement

### 5.1 Runtime Metrics Collection

```kotlin
data class ResourceSnapshot(
    val timestamp: Long,
    val totalMemoryMb: Long,
    val availableMemoryMb: Long,
    val usedMemoryMb: Long,
    val pssMb: Long,                    // Proportional Set Size
    val cpuPercent: Double,             // App process CPU
    val thermalLevel: Int,              // 0=normal, 1=throttled, 2=critical
    val batteryPercent: Int,
    val isCharging: Boolean,
    val gpuMemoryMb: Long,              // If available
    val allocatedCapabilities: Map<String, CapabilityUsage>
)

data class CapabilityUsage(
    val capabilityId: String,
    val memoryMb: Long,
    val cpuPercent: Double,
    val state: CapabilityState,
    val uptimeMs: Long
)
```

### 5.2 Collection Intervals

| Metric | Interval | Method |
|--------|----------|--------|
| Memory (PSS, available) | 5s | `ActivityManager.getMemoryInfo()`, `Debug.getPss()` |
| CPU % | 5s | `/proc/self/stat` or `Process.getCpuPercent()` |
| Thermal | 10s | `PowerManager.getCurrentThermalStatus()` |
| Battery | 30s | `BatteryManager` broadcast |
| Capability usage | On state change | Governor tracks active capabilities |
| Model metrics | Per inference | `LocalInferenceEngine` reports |

### 5.3 Emergency Thresholds

| Condition | Threshold | Immediate Action |
|-----------|-----------|------------------|
| **OOM risk** | Available < 200 MB | `EmergencyUnloadAll(preserveCritical = {vosk_wake, failure_surface})` |
| **Critical thermal** | Level = 2 (CRITICAL) | Enter THERMAL_PRESSURE, unload model, throttle visual |
| **Critical battery** | < 5% not charging | Enter BATTERY_PRESSURE, unload all non-critical |
| **CPU saturation** | > 90% for 10s | Reduce inference threads, drop context |

---

## 6. Model Lifecycle Integration

### 6.1 State Machine (per capability)

```
DORMANT
    │ load()
    ▼
LOADING ──(timeout/fail)──▶ FAILED
    │ success
    ▼
LOADED ──(idle > timeout)──▶ IDLE
    │ warm()
    ▼
ACTIVE ──(done)──▶ IDLE
    │ suspend()
    ▼
SUSPENDED ──(resume)──▶ LOADED
    │ unload()
    ▼
UNLOADING ──(complete)──▶ DORMANT
```

### 6.2 Governor-Controlled Transitions

```kotlin
class ResourceGovernor {
    // Called by nervous system when capability needed
    suspend fun requestCapability(capabilityId: String): Result<CapabilityHandle> {
        val capability = registry[capabilityId]
        val budget = capability.budget
        
        // Check admission
        val admission = admit(budget)
        if (!admission.admitted) {
            // Try to make room
            val freed = makeRoom(budget)
            if (!freed) return Result.failure("Insufficient resources")
        }
        
        // Load if needed
        val handle = capability.load()
        
        // Track usage
        activeCapabilities[capabilityId] = handle
        
        return Result.success(handle)
    }
    
    // Called when capability released
    fun releaseCapability(capabilityId: String) {
        val handle = activeCapabilities.remove(capabilityId)
        handle?.startIdleTimer()
    }
    
    // Background: check idle timeouts
    fun checkIdleTimeouts() {
        activeCapabilities.forEach { (id, handle) ->
            if (handle.idleTimeMs > handle.idleTimeoutMs) {
                handle.unload()
                activeCapabilities.remove(id)
            }
        }
    }
}
```

---

## 7. Configuration (Externalizable)

### 7.1 Resource Config File (`resources_config.json`)

```json
{
  "budget": {
    "totalMemoryMb": 5500,
    "reservedMemoryMb": 500,
    "maxModelMemoryMb": 2000,
    "totalCpuPercent": 80.0,
    "minBatteryPercent": 10,
    "lowBatteryPercent": 20,
    "modelIdleUnloadMs": 120000,
    "prewarmBudgetMs": 5000
  },
  "states": {
    "NORMAL": { "ramLimitPercent": 1.0, "cpuLimitPercent": 1.0 },
    "LOW_RAM": { "ramLimitPercent": 0.6, "cpuLimitPercent": 0.8 },
    "HIGH_RAM_PRESSURE": { "ramLimitPercent": 0.4, "cpuLimitPercent": 0.6 },
    "THERMAL_PRESSURE": { "ramLimitPercent": 0.5, "cpuLimitPercent": 0.4 },
    "BATTERY_PRESSURE": { "ramLimitPercent": 0.5, "cpuLimitPercent": 0.5 },
    "BACKGROUND": { "ramLimitPercent": 0.1, "cpuLimitPercent": 0.1 },
    "RECOVERY": { "ramLimitPercent": 0.7, "cpuLimitPercent": 0.7 }
  },
  "hysteresis": {
    "ramMarginPercent": 0.05,
    "minDwellMs": 10000
  },
  "capabilityOverrides": {
    "llm.qwen3_1b7_q4km": { "ramMb": 1500, "cpuPercent": 40, "priority": "CRITICAL" },
    "stt.vosk_wake": { "ramMb": 120, "cpuPercent": 15, "priority": "CRITICAL" }
  }
}
```

### 7.2 Build-Time Constants (`ResourceConfig.kt`)

```kotlin
object ResourceConfig {
    // These can be overridden by resources_config.json at runtime
    const val DEFAULT_TOTAL_MEMORY_MB = 5500L
    const val DEFAULT_RESERVED_MEMORY_MB = 500L
    const val MAX_MODEL_MEMORY_MB = 2000L
    const val MODEL_IDLE_UNLOAD_MS = 120_000L
    const val STT_IDLE_UNLOAD_MS = 60_000L
    const val TTS_IDLE_UNLOAD_MS = 30_000L
    const val PREWARM_BUDGET_MS = 5_000L
    const val THERMAL_CHECK_INTERVAL_MS = 10_000L
    const val MEMORY_CHECK_INTERVAL_MS = 5_000L
    const val HYSTERESIS_RAM_MARGIN = 0.05
    const val MIN_STATE_DWELL_MS = 10_000L
}
```

---

## 8. Integration Points

### 8.1 BodyCoordinator → ResourceGovernor

```kotlin
// In BodyCoordinator.init()
scope.launch {
    val governor = ResourceGovernor.getInstance()
    governor.state.collect { state ->
        when (state) {
            ResourceState.HIGH_RAM_PRESSURE, 
            ResourceState.THERMAL_PRESSURE,
            ResourceState.BATTERY_PRESSURE -> {
                // Pause Vosk continuous decode (keep wake word)
                JarvisMic.audioFeedPaused = true
                // Unload model
                modelManager.unloadModel(ModelProviderType.LLAMA_CPP)
            }
            ResourceState.BACKGROUND -> {
                // Full suspend
                vosk?.stop()
                sherpa?.stop()
                tts?.stop()
                modelManager.unloadModel(ModelProviderType.LLAMA_CPP)
            }
            ResourceState.NORMAL, ResourceState.RECOVERY -> {
                JarvisMic.audioFeedPaused = false
                // Models lazily reload on next request
            }
        }
    }
}
```

### 8.2 CapabilityRegistry → ResourceGovernor

```kotlin
// CapabilityRegistry registers with governor at startup
class CapabilityRegistry {
    fun registerAll(governor: ResourceGovernor) {
        capabilities.values.forEach { cap ->
            governor.registerCapabilityBudget(
                cap.id,
                ResourceBudget.CapabilityBudget(
                    maxMemoryMb = cap.ramEstimateMb,
                    maxCpuPercent = cap.cpuEstimatePercent,
                    priority = cap.priority,
                    idleTimeoutMs = cap.idleTimeoutMs
                )
            )
        }
    }
}
```

### 8.3 LocalInferenceEngine → ResourceGovernor

```kotlin
class LocalInferenceEngine {
    suspend fun generate(request: GenerateRequest): GenerateResult {
        // Request admission
        val handle = governor.requestCapability("llm.qwen3_1b7_q4km")
            .getOrThrow()
        
        try {
            // Adjust context budget per current state
            val contextBudget = governor.currentState.contextBudgetPercent
            val maxTokens = (request.maxTokens * contextBudget).toInt()
            
            // Generate with timeout
            return withTimeout(generationTimeoutMs) {
                doGenerate(request.copy(maxTokens = maxTokens))
            }
        } finally {
            governor.releaseCapability("llm.qwen3_1b7_q4km")
        }
    }
}
```

---

## 9. Testing & Validation

### 9.1 Unit Tests

- [ ] State machine transitions with hysteresis
- [ ] Admission control with various budgets
- [ ] Emergency threshold triggers
- [ ] Capability load/unload tracking
- [ ] Context budget calculation per state

### 9.2 Integration Tests

- [ ] Full state transition: NORMAL → LOW_RAM → HIGH_RAM → THERMAL → RECOVERY → NORMAL
- [ ] Background/foreground cycle
- [ ] Concurrent capability requests under pressure
- [ ] Model reload after unload
- [ ] Prewarm cancellation under pressure

### 9.3 Device Benchmarks

| Scenario | Expected Behavior | Measurement |
|----------|-------------------|-------------|
| Cold start | NORMAL state, critical capabilities loaded | Time to interactive |
| Voice interaction (NORMAL) | All capabilities available | End-to-end latency |
| Voice interaction (LOW_RAM) | Vosk + heuristic LLM + Piper | Latency, quality |
| Voice interaction (THERMAL) | Vosk + heuristic + System TTS | Latency, no crash |
| Background 5min → foreground | RECOVERY → NORMAL, capabilities reload | Reload time |
| Battery 5% not charging | BATTERY_PRESSURE, minimal path | No crash, functional |

---

## 10. Summary

| Aspect | Decision |
|--------|----------|
| **Architecture** | Concurrent subsystems, selective computation |
| **State machine** | 7 states with hysteresis, priority-based |
| **Model lifecycle** | DORMANT→LOADING→LOADED→ACTIVE→IDLE→SUSPENDED→UNLOADING |
| **Admission** | Budget-based with emergency make-room |
| **Prewarming** | Trigger-based, budget-limited, auto-cancel under pressure |
| **Fallbacks** | Heuristic LLM, Vosk-only STT, System TTS, simple visual |
| **Monitoring** | 5s intervals, PSS + CPU + thermal + battery |
| **Config** | External JSON + build-time defaults |
| **Testing** | Unit + integration + 15 device scenarios |

**Core Principle:** *"Architecturally concurrent, computationally selective"* — every capability exists in the architecture, but only the necessary ones consume resources at any moment.

---

*This resource model is the contract between the nervous system and the hardware reality. All values are starting estimates; final calibration occurs during device benchmarking (see JARVIS_PHASE1_BENCHMARK_REPORT.md).*