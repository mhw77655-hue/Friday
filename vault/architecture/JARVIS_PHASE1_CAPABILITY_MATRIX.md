# JARVIS Phase 1 Capability Matrix

**Generated:** 2026-08-09  
**Purpose:** Centralized capability registry — every capability declares its metadata for nervous system routing, resource governance, and health monitoring

---

## Capability Registry Schema

```kotlin
data class Capability(
    val id: String,                    // Unique identifier
    val version: String,               // Semantic version
    val function: String,              // Human-readable function
    val category: CapabilityCategory,  // STT/TTS/LLM/MEMORY/VAD/SYSTEM
    val dependencies: List<String>,    // Other capability IDs required
    val model: String?,                // Model identifier if applicable
    val ramEstimateMb: Long,           // Peak RAM when active
    val cpuEstimatePercent: Double,    // CPU % when active
    val startupCostMs: Long,           // Cold start latency
    val thermalCost: Int,              // 0=none, 1=low, 2=medium, 3=high
    val batteryCost: Int,              // 0=none, 1=low, 2=medium, 3=high
    val latencyMs: Long,               // Typical end-to-end latency
    val languages: Set<String>,        // Supported language codes
    val quality: QualityTier,          // LOW/MEDIUM/HIGH/BEST
    val confidence: Float,             // Measured reliability 0-1
    val currentState: CapabilityState, // DORMANT/LOADED/ACTIVE/DEGRADED/FAILED
    val health: HealthStatus,          // HEALTHY/DEGRADED/FAILED/RECOVERING
    val fallback: String?,             // Alternative capability ID
    val recoveryStrategy: RecoveryStrategy
)

enum class CapabilityCategory { STT, TTS, LLM, MEMORY, VAD, WAKE_WORD, SYSTEM, VISUAL }
enum class QualityTier { LOW, MEDIUM, HIGH, BEST }
enum class CapabilityState { DORMANT, LOADING, LOADED, ACTIVE, IDLE, SUSPENDED, UNLOADING, FAILED }
enum class HealthStatus { HEALTHY, DEGRADED, FAILED, RECOVERING, DISABLED, UNAVAILABLE }
enum class RecoveryStrategy { NONE, RETRY, RELOAD, FALLBACK, REINITIALIZE, USER_ACTION }
```

---

## Complete Capability Matrix

| ID | Version | Function | Category | Dependencies | Model | RAM (MB) | CPU (%) | Startup (ms) | Thermal | Battery | Latency (ms) | Languages | Quality | Confidence | State | Health | Fallback | Recovery |
|----|---------|----------|----------|--------------|-------|----------|---------|--------------|---------|---------|--------------|-----------|---------|------------|-------|--------|----------|----------|
| **llm.qwen3_1b7_q4km** | 1.0.0 | Primary local LLM reasoning | LLM | vad.silero, memory.hybrid | qwen3-1.7b-q4km | 1500 | 40 | 800 | 2 | 2 | 200 (first tok) | en, ar, 100+ | HIGH | 0.85 | DORMANT | HEALTHY | llm.heuristic | RELOAD |
| **llm.heuristic** | 1.0.0 | Deterministic fallback responses | LLM | - | builtin | 0 | 1 | 0 | 0 | 0 | 5 | en | LOW | 1.0 | LOADED | HEALTHY | - | NONE |
| **stt.vosk_wake** | 1.0.0 | Wake word detection (grammar) | WAKE_WORD | vad.silero | vosk-small-en-0.15 | 120 | 15 | 300 | 1 | 1 | 50 | en | MEDIUM | 0.9 | LOADED | HEALTHY | - | RELOAD |
| **stt.vosk_command** | 1.0.0 | Command STT (unconstrained) | STT | stt.vosk_wake | vosk-small-en-0.15 | 120 | 20 | 100 | 1 | 1 | 300 | en | MEDIUM | 0.85 | ACTIVE | HEALTHY | stt.sherpa_zipformer | RETRY |
| **stt.sherpa_zipformer** | 1.0.0 | Streaming English STT | STT | vad.silero | sherpa-zipformer-en | 80 | 25 | 400 | 1 | 1 | 150 | en | HIGH | 0.88 | DORMANT | HEALTHY | stt.platform | RELOAD |
| **stt.sherpa_whisper** | 1.0.0 | Offline Whisper fallback | STT | - | whisper-tiny.en | 150 | 30 | 500 | 1 | 1 | 500 | en | HIGH | 0.8 | DORMANT | HEALTHY | stt.platform | RELOAD |
| **stt.platform_ar** | 1.0.0 | Arabic STT (system) | STT | - | system | 0 | 5 | 100 | 0 | 1 | variable | ar-EG, ar | BEST | 0.95 | AVAILABLE | HEALTHY | - | USER_ACTION |
| **stt.platform_en** | 1.0.0 | English STT (system fallback) | STT | - | system | 0 | 5 | 100 | 0 | 1 | variable | en-US | HIGH | 0.9 | AVAILABLE | HEALTHY | - | USER_ACTION |
| **stt.arbitrator** | 1.0.0 | STT routing & escalation | STT | stt.vosk_command, stt.sherpa_zipformer, stt.platform_ar, stt.platform_en | - | 5 | 2 | 0 | 0 | 0 | 10 | all | - | 0.95 | LOADED | HEALTHY | - | NONE |
| **tts.piper_en** | 1.0.0 | Local English TTS (Piper) | TTS | vocab.store | en_US-lessac-medium | 100 | 30 | 300 | 1 | 1 | 120 | en | HIGH | 0.9 | DORMANT | HEALTHY | tts.system | RELOAD |
| **tts.piper_ar** | 1.0.0 | Local Arabic TTS (Piper) | TTS | vocab.store | ar_JO-kareem-medium | 100 | 30 | 300 | 1 | 1 | 150 | ar | HIGH | 0.85 | DORMANT | HEALTHY | tts.system | RELOAD |
| **tts.system** | 1.0.0 | System TTS fallback | TTS | - | Google TTS | 0 | 10 | 100 | 0 | 1 | variable | 50+ | BEST | 0.95 | AVAILABLE | HEALTHY | - | USER_ACTION |
| **tts.manager** | 1.0.0 | TTS engine selection & lifecycle | TTS | tts.piper_en, tts.piper_ar, tts.system | - | 10 | 2 | 0 | 0 | 0 | 5 | all | - | 0.98 | LOADED | HEALTHY | - | NONE |
| **vad.silero** | 1.0.0 | Voice activity detection | VAD | - | silero_vad_16k_op15 | 15 | 2 | 80 | 0 | 0 | 10 | all | HIGH | 0.95 | DORMANT | HEALTHY | - | RELOAD |
| **memory.hybrid** | 1.0.0 | Hybrid memory retrieval | MEMORY | memory.store, vocab.store | minilm-l6 (optional) | 80 | 10 | 200 | 1 | 0 | 50 | en, ar | HIGH | 0.88 | LOADED | HEALTHY | memory.lexical | RETRY |
| **memory.store** | 1.0.0 | Core memory persistence | MEMORY | - | - | 30 | 5 | 50 | 0 | 0 | 20 | all | HIGH | 0.98 | LOADED | HEALTHY | - | RETRY |
| **memory.vocab** | 1.0.0 | User vocabulary & pronunciation | MEMORY | - | - | 10 | 1 | 10 | 0 | 0 | 5 | en, ar | HIGH | 0.99 | LOADED | HEALTHY | - | RETRY |
| **memory.promoter** | 1.0.0 | Memory promotion policy | MEMORY | memory.store, humancore | - | 5 | 2 | 0 | 0 | 0 | 10 | all | - | 0.9 | LOADED | HEALTHY | - | NONE |
| **policy.inference** | 1.0.0 | Request classification (MICRO/NORMAL/COMPLEX/SYSTEM) | SYSTEM | - | - | 2 | 1 | 0 | 0 | 0 | 2 | all | - | 0.9 | LOADED | HEALTHY | - | NONE |
| **context.builder** | 1.0.0 | Adaptive context construction | SYSTEM | memory.hybrid, policy.inference | qwen3-tokenizer | 20 | 5 | 0 | 0 | 0 | 15 | all | - | 0.92 | LOADED | HEALTHY | - | NONE |
| **resource.governor** | 1.0.0 | Resource admission & state control | SYSTEM | - | - | 5 | 2 | 0 | 0 | 0 | 1 | - | - | 1.0 | LOADED | HEALTHY | - | NONE |
| **prewarmer.predictive** | 1.0.0 | Speculative subsystem warming | SYSTEM | resource.governor | - | 10 | 5 | 0 | 0 | 0 | 5 | - | - | 0.85 | LOADED | HEALTHY | - | NONE |
| **registry.capability** | 1.0.0 | Central capability metadata | SYSTEM | - | - | 2 | 1 | 0 | 0 | 0 | 1 | - | - | 1.0 | LOADED | HEALTHY | - | NONE |
| **nervous.body** | 1.0.0 | Communication body coordinator | SYSTEM | all above | - | 50 | 10 | 200 | 0 | 0 | - | - | - | 0.95 | ACTIVE | HEALTHY | - | REINITIALIZE |
| **nervous.global** | 1.0.0 | Global nervous system | SYSTEM | nervous.body, registry.capability | - | 10 | 2 | 50 | 0 | 0 | - | - | - | 0.98 | ACTIVE | HEALTHY | - | REINITIALIZE |
| **humancore** | 1.0.0 | Personality, values, identity | SYSTEM | memory.store, model.llm | - | 30 | 5 | 100 | 0 | 0 | - | all | HIGH | 0.95 | ACTIVE | HEALTHY | - | REINITIALIZE |
| **visual.orb** | 1.0.0 | Reactor visual state | VISUAL | nervous.body | - | 20 | 8 | 100 | 1 | 1 | - | - | HIGH | 0.98 | ACTIVE | HEALTHY | visual.simple | THROTTLE |
| **visual.simple** | 1.0.0 | Minimal visual fallback | VISUAL | - | - | 2 | 1 | 10 | 0 | 0 | - | - | LOW | 1.0 | DORMANT | HEALTHY | - | NONE |
| **diagnostics.self** | 1.0.0 | Self-diagnosis & health reporting | SYSTEM | failure.surface, registry.capability | - | 10 | 2 | 50 | 0 | 0 | 100 | - | - | 1.0 | LOADED | HEALTHY | - | NONE |
| **failure.surface** | 1.0.0 | Central failure bus | SYSTEM | - | - | 5 | 1 | 0 | 0 | 0 | 1 | - | - | 1.0 | LOADED | HEALTHY | - | NONE |
| **recovery.controller** | 1.0.0 | Circuit breakers & retries | SYSTEM | failure.surface | - | 10 | 2 | 0 | 0 | 0 | 10 | - | - | 0.95 | LOADED | HEALTHY | - | NONE |

---

## Capability Dependency Graph

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CAPABILITY DEPENDENCY GRAPH                          │
└─────────────────────────────────────────────────────────────────────────────┘

                          ┌──────────────────┐
                          │  resource.governor│
                          └────────┬─────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          ▼                          ▼
┌───────────────┐          ┌───────────────┐          ┌───────────────┐
│  registry.    │          │  failure.     │          │  nervous.     │
│  capability   │◀────────▶│  surface      │◀────────▶│  body         │
└───────┬───────┘          └───────┬───────┘          └───────┬───────┘
        │                          │                          │
        │                    ┌─────┴─────┐                   │
        │                    ▼           ▼                   │
        │            ┌─────────────┐ ┌─────────────┐         │
        │            │  recovery.  │ │ diagnostics.│         │
        │            │  controller │ │  self       │         │
        │            └─────────────┘ └─────────────┘         │
        │                                                    │
┌───────┴───────┐                                    ┌──────┴──────┐
▼               ▼                                    ▼             ▼
VAD/STT/TTS    MEMORY/POLICY                       HUMANCORE    VISUAL
                              ┌─────────────────────┐
        ┌────────────────────┤  nervous.global      ├────────────────────┐
        │                    └─────────────────────┘                    │
        ▼                                                                ▼
┌───────────────┐                                               ┌───────────────┐
│ STT Pipeline  │                                               │ TTS Pipeline  │
├───────────────┤                                               ├───────────────┤
│ vad.silero    │                                               │ tts.manager   │
│    │          │                                               │    │          │
│    ▼          │                                               │    ▼          │
│ stt.vosk_    │                                               │ tts.piper_   │
│ wake ────────┼─────────────────────────────────────────────────▶ en/ar        │
│    │          │                                               │    │          │
│    ▼          │                                               │    ▼          │
│ stt.vosk_    │                                               │ tts.system    │
│ command       │                                               └───────────────┘
│    │          │
│    ├──▶ ACCEPT (conf ≥ 0.8)
│    │
│    ▼
│ stt.arbitrator ──▶ ESCALATE
│    │
│    ├──▶ stt.sherpa_zipformer (conf ≥ 0.7)
│    │
│    ▼
│ stt.platform_ar/en
└───────────────┘

MEMORY PIPELINE                     POLICY & CONTEXT
┌─────────────────────┐             ┌─────────────────────┐
│ memory.hybrid       │             │ policy.inference    │
│  ├─ fast (lexical)  │             │  ├─ patterns        │
│  ├─ semantic        │             │  ├─ heuristic       │
│  ├─ graph           │             │  └─ tiny classifier │
│  └─ rank+dedupe     │             └─────────┬───────────┘
└─────────┬───────────┘                       │
          │                                   ▼
          ▼                          ┌─────────────────────┐
┌─────────────────────┐             │ context.builder     │
│ memory.store        │             │  ├─ system prompt   │
│  ├─ episodic        │             │  ├─ recent turns    │
│  ├─ facts           │             │  ├─ retrieved mem   │
│  ├─ preferences     │             │  ├─ user profile    │
│  └─ context         │             │  └─ compress→budget │
└─────────────────────┘             └─────────────────────┘
          │
          ▼
┌─────────────────────┐
│ memory.promoter     │
│  ├─ evaluate        │
│  ├─ tier assign     │
│  └─ persist         │
└─────────────────────┘
```

---

## Resource Profiles by System State

| Capability | NORMAL | LOW_RAM | HIGH_RAM | THERMAL | BATTERY | BACKGROUND |
|------------|--------|---------|----------|---------|---------|------------|
| llm.qwen3_1b7_q4km | LOADED | LOADED | UNLOADED | UNLOADED | UNLOADED | SUSPENDED |
| stt.vosk_wake | ACTIVE | ACTIVE | ACTIVE | ACTIVE | ACTIVE | SUSPENDED |
| stt.vosk_command | ACTIVE | ACTIVE | ACTIVE | ACTIVE | ACTIVE | SUSPENDED |
| stt.sherpa_zipformer | DORMANT | DORMANT | UNLOADED | UNLOADED | UNLOADED | SUSPENDED |
| stt.sherpa_whisper | DORMANT | DORMANT | UNLOADED | UNLOADED | UNLOADED | SUSPENDED |
| stt.platform_ar | AVAILABLE | AVAILABLE | AVAILABLE | AVAILABLE | AVAILABLE | AVAILABLE |
| tts.piper_en | DORMANT | DORMANT | UNLOADED | UNLOADED | UNLOADED | SUSPENDED |
| tts.piper_ar | DORMANT | DORMANT | UNLOADED | UNLOADED | UNLOADED | SUSPENDED |
| tts.system | AVAILABLE | AVAILABLE | AVAILABLE | AVAILABLE | AVAILABLE | AVAILABLE |
| vad.silero | DORMANT | DORMANT | DORMANT | DORMANT | DORMANT | SUSPENDED |
| memory.hybrid | LOADED | LOADED | LOADED | LOADED | LOADED | SUSPENDED |
| visual.orb | ACTIVE | REDUCED | MINIMAL | THROTTLED | THROTTLED | SLEEP |

**Memory Budget per State:**
- NORMAL: ~2.5 GB
- LOW_RAM: ~1.8 GB
- HIGH_RAM: ~1.0 GB
- THERMAL: ~0.8 GB
- BATTERY: ~0.8 GB
- BACKGROUND: ~0.3 GB

---

## Health Monitoring Signals

Each capability exposes:

```kotlin
data class CapabilityHealth(
    val capabilityId: String,
    val status: HealthStatus,
    val lastCheck: Long,
    val metrics: Map<String, Double>,  // latency, error_rate, cpu, ram, etc.
    val degradedReason: String?,
    val recoveryAction: RecoveryStrategy?,
    val fallbackActive: Boolean
)
```

**Health check intervals:**
- CRITICAL (STT wake, TTS, LLM): every 30s
- STANDARD (memory, STT fallback, VAD): every 60s
- AUXILIARY (visual, diagnostics): every 120s

---

## Routing Rules (Nervous System)

| User Input Pattern | Policy Class | Capabilities Activated |
|--------------------|--------------|------------------------|
| "stop", "cancel", "no" | MICRO | heuristic only |
| "yes", "ok", "repeat" | MICRO | heuristic only |
| "what time", "weather", simple command | NORMAL | llm (128 tok) + memory (recent) |
| "plan", "explain", "analyze", "write" | COMPLEX | llm (512 tok) + memory (full) + retrieval |
| "debug", "capabilities", "memory", "health" | SYSTEM | diagnostics.self + registry.capability |
| Arabic speech detected | NORMAL/COMPLEX | stt.platform_ar → llm → tts.piper_ar |
| Code-switching (ar+en) | COMPLEX | stt.platform_ar → llm → tts.manager (per-segment) |

---

## Fallback Chains

```
LLM: qwen3_1b7_q4km → heuristic
STT English: vosk_command → sherpa_zipformer → sherpa_whisper → platform_en
STT Arabic: platform_ar → (none - best available)
TTS English: piper_en → system
TTS Arabic: piper_ar → system
VAD: silero → (none - energy fallback in BodyCoordinator)
Memory: hybrid → lexical (memory.store direct)
Visual: orb → simple → none
```

---

## Capability Lifecycle Commands

```kotlin
// Nervous system controls capabilities via:
interface CapabilityControl {
    suspend fun load(capabilityId: String): Result<Unit>
    suspend fun unload(capabilityId: String): Result<Unit>
    suspend fun warm(capabilityId: String): Result<Unit>
    suspend fun suspend(capabilityId: String): Result<Unit>
    suspend fun resume(capabilityId: String): Result<Unit>
    fun health(capabilityId: String): CapabilityHealth
    fun state(capabilityId: String): CapabilityState
}
```

**State Transitions:**
```
DORMANT → LOADING → LOADED → ACTIVE → IDLE → SUSPENDED → UNLOADING → DORMANT
                ↓                                    ↓
               FAILED ←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←
                ↓
           RECOVERING → LOADED
```

---

## Quality Gates (Promotion Criteria)

| Capability | Minimum Confidence | Maximum Latency | Maximum Error Rate | Minimum Uptime |
|------------|-------------------|-----------------|-------------------|----------------|
| llm.qwen3_1b7_q4km | 0.8 | 500ms first token | 2% | 99% |
| stt.vosk_command | 0.85 | 500ms | 5% | 99.5% |
| stt.sherpa_zipformer | 0.85 | 300ms | 3% | 99% |
| tts.piper_en | 0.9 | 200ms | 1% | 99.9% |
| tts.piper_ar | 0.85 | 250ms | 2% | 99.5% |
| vad.silero | 0.95 | 20ms | 1% | 99.9% |
| memory.hybrid | 0.88 | 100ms | 1% | 99.9% |

---

*This matrix is the single source of truth for the nervous system's routing, admission, and recovery decisions. All values are estimates from research; actual values will be calibrated during device benchmarking (see JARVIS_PHASE1_BENCHMARK_REPORT.md).*