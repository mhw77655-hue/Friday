package com.jarvis.app.capability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * CapabilityRegistry - Centralized capability registry
 *
 * Every capability declares its metadata for:
 * - Nervous system routing
 * - Resource governance
 * - Health monitoring
 * - Fallback chains
 */
class CapabilityRegistry {

    /** Capability category */
    enum class Category {
        STT, TTS, LLM, MEMORY, VAD, WAKE_WORD, SYSTEM, VISUAL, LANGUAGE, VOCABULARY
    }

    /** Quality tier */
    enum class QualityTier { LOW, MEDIUM, HIGH, BEST }

    /** Capability state */
    enum class State { DORMANT, LOADING, LOADED, ACTIVE, IDLE, SUSPENDED, UNLOADING, FAILED }

    /** Health status */
    enum class Health { HEALTHY, DEGRADED, FAILED, RECOVERING, DISABLED, UNAVAILABLE }

    /** Recovery strategy */
    enum class RecoveryStrategy {
        RETRY,           // Retry same capability
        FALLBACK,        // Try fallback capability
        DEGRADE,         // Reduce quality/functionality
        RESTART,         // Restart capability
        DISABLE,         // Disable until manual intervention
        NONE             // No recovery
    }

    /** Capability declaration */
    data class Capability(
        val id: String,
        val version: String,
        val name: String,
        val function: String,
        val category: Category,
        val dependencies: List<String> = emptyList(),
        val model: String? = null,
        val ramEstimateMb: Long = 0,
        val cpuEstimatePercent: Double = 0.0,
        val startupCostMs: Long = 0,
        val thermalCost: Int = 0,        // 0=none, 1=low, 2=medium, 3=high
        val batteryCost: Int = 0,        // 0=none, 1=low, 2=medium, 3=high
        val latencyMs: Long = 0,
        val languages: Set<String> = emptySet(),
        val quality: QualityTier = QualityTier.MEDIUM,
        val confidence: Float = 1.0f,
        var currentState: State = State.DORMANT,
        var health: Health = Health.HEALTHY,
        val fallback: String? = null,
        val recoveryStrategy: RecoveryStrategy = RecoveryStrategy.FALLBACK,
        val tags: Set<String> = emptySet()
    ) {
        /** Check if capability can run given current system state */
        fun canRun(availableRamMb: Long, availableCpuPercent: Double, thermalOk: Boolean, batteryOk: Boolean): Boolean {
            return currentState == State.LOADED || currentState == State.IDLE || currentState == State.ACTIVE
        }
    }

    /** Capability metrics */
    data class Metrics(
        val capabilityId: String,
        var totalActivations: Long = 0,
        var successfulActivations: Long = 0,
        var failedActivations: Long = 0,
        var avgLatencyMs: Long = 0,
        var avgRamMb: Long = 0,
        var avgCpuPercent: Double = 0.0,
        var lastActivated: Long = 0,
        var lastError: String? = null,
        var consecutiveFailures: Int = 0
    ) {
        val successRate: Float get() = if (totalActivations > 0) successfulActivations.toFloat() / totalActivations else 0f

        fun recordActivation(latencyMs: Long, ramMb: Long, cpuPercent: Double, success: Boolean, error: String? = null) {
            totalActivations++
            lastActivated = System.currentTimeMillis()
            if (success) {
                successfulActivations++
                consecutiveFailures = 0
                avgLatencyMs = (avgLatencyMs * (successfulActivations - 1) + latencyMs) / successfulActivations
                avgRamMb = (avgRamMb * (successfulActivations - 1) + ramMb) / successfulActivations
                avgCpuPercent = (avgCpuPercent * (successfulActivations - 1) + cpuPercent) / successfulActivations
            } else {
                failedActivations++
                consecutiveFailures++
                lastError = error
            }
        }
    }

    // Registry storage
    private val capabilities = ConcurrentHashMap<String, Capability>()
    private val metrics = ConcurrentHashMap<String, Metrics>()
    private val initializationOrder = mutableListOf<String>()

    // State flows
    private val _capabilityStates = MutableStateFlow<Map<String, State>>(emptyMap())
    val capabilityStates: StateFlow<Map<String, State>> = _capabilityStates.asStateFlow()

    private val _capabilityHealth = MutableStateFlow<Map<String, Health>>(emptyMap())
    val capabilityHealth: StateFlow<Map<String, Health>> = _capabilityHealth.asStateFlow()

    // Registration ID generator
    private val registrationCounter = AtomicLong(0)

    /** Register a new capability */
    fun register(capability: Capability): String {
        val id = capability.id
        if (capabilities.containsKey(id)) {
            throw IllegalArgumentException("Capability already registered: $id")
        }

        capabilities[id] = capability
        metrics[id] = Metrics(capabilityId = id)
        initializationOrder.add(id)

        _capabilityStates.value = capabilities.mapValues { (_, cap) -> cap.currentState }
        _capabilityHealth.value = capabilities.mapValues { (_, cap) -> cap.health }

        return id
    }

    /** Unregister a capability */
    fun unregister(id: String): Boolean {
        return capabilities.remove(id) != null
    }

    /** Get capability by ID */
    fun get(id: String): Capability? = capabilities[id]

    /** Get all capabilities */
    fun getAll(): List<Capability> = capabilities.values.toList()

    /** Get capabilities by category */
    fun getByCategory(category: Category): List<Capability> =
        capabilities.values.filter { it.category == category }.toList()

    /** Get capabilities by language support */
    fun getByLanguage(language: String): List<Capability> =
        capabilities.values.filter { it.languages.contains(language) }.toList()

    /** Get healthy capabilities */
    fun getHealthy(): List<Capability> =
        capabilities.values.filter { it.health == Health.HEALTHY }.toList()

    /** Get available capabilities (loaded and healthy) */
    fun getAvailable(): List<Capability> =
        capabilities.values.filter { it.currentState in setOf(State.LOADED, State.IDLE, State.ACTIVE) && it.health == Health.HEALTHY }.toList()

    /** Update capability state */
    fun updateState(id: String, state: State): Boolean {
        val capability = capabilities[id] ?: return false
        capability.currentState = state
        _capabilityStates.value = capabilities.mapValues { (_, cap) -> cap.currentState }
        return true
    }

    /** Update capability health */
    fun updateHealth(id: String, health: Health): Boolean {
        val capability = capabilities[id] ?: return false
        capability.health = health
        _capabilityHealth.value = capabilities.mapValues { (_, cap) -> cap.health }
        return true
    }

    /** Record capability activation */
    fun recordActivation(id: String, latencyMs: Long, ramMb: Long, cpuPercent: Double, success: Boolean, error: String? = null) {
        metrics[id]?.recordActivation(latencyMs, ramMb, cpuPercent, success, error)

        // Auto-degrade health on consecutive failures
        val m = metrics[id]
        if (m != null && m.consecutiveFailures >= 3) {
            updateHealth(id, Health.DEGRADED)
        } else if (m != null && m.consecutiveFailures >= 5) {
            updateHealth(id, Health.FAILED)
        }
    }

    /** Get capability metrics */
    fun getMetrics(id: String): Metrics? = metrics[id]

    /** Get all metrics */
    fun getAllMetrics(): List<Metrics> = metrics.values.toList()

    /** Get fallback capability */
    fun getFallback(id: String): Capability? {
        val capability = capabilities[id] ?: return null
        return capability.fallback?.let { fallbackId -> capabilities[fallbackId] }
    }

    /** Get fallback chain */
    fun getFallbackChain(id: String): List<Capability> {
        val chain = mutableListOf<Capability>()
        var currentId = id

        while (true) {
            val capability = capabilities[currentId] ?: break
            capability.fallback?.let { fallbackId ->
                val fallback = capabilities[fallbackId]
                if (fallback != null && fallback !in chain) {
                    chain.add(fallback)
                    currentId = fallbackId
                } else {
                    break
                }
            } ?: break
        }

        return chain
    }

    /** Find best capability for a task */
    fun findBest(
        category: Category,
        language: String? = null,
        minQuality: QualityTier = QualityTier.LOW,
        maxRamMb: Long = Long.MAX_VALUE,
        maxLatencyMs: Long = Long.MAX_VALUE,
        requireHealthy: Boolean = true
    ): Capability? {
        return capabilities.values
            .filter { it.category == category }
            .filter { language == null || it.languages.contains(language) }
            .filter { it.quality.ordinal >= minQuality.ordinal }
            .filter { it.ramEstimateMb <= maxRamMb }
            .filter { it.latencyMs <= maxLatencyMs }
            .filter { !requireHealthy || it.health == Health.HEALTHY }
            .filter { it.currentState in setOf(State.LOADED, State.IDLE, State.ACTIVE) }
            .maxByOrNull { it.confidence * it.quality.ordinal.toFloat() }
    }

    /** Get registry summary */
    data class RegistrySummary(
        val totalCapabilities: Int,
        val byCategory: Map<Category, Int>,
        val byState: Map<State, Int>,
        val byHealth: Map<Health, Int>,
        val healthyCount: Int,
        val availableCount: Int
    )

    fun getSummary(): RegistrySummary {
        return RegistrySummary(
            totalCapabilities = capabilities.size,
            byCategory = capabilities.values.groupingBy { it.category }.eachCount(),
            byState = capabilities.values.groupingBy { it.currentState }.eachCount(),
            byHealth = capabilities.values.groupingBy { it.health }.eachCount(),
            healthyCount = capabilities.values.count { it.health == Health.HEALTHY },
            availableCount = capabilities.values.count { it.currentState in setOf(State.LOADED, State.IDLE, State.ACTIVE) && it.health == Health.HEALTHY }
        )
    }
}

/** Global registry instance */
object CapabilityRegistryHolder {
    @Volatile private var instance: CapabilityRegistry? = null

    fun get(): CapabilityRegistry = instance ?: synchronized(this) {
        instance ?: CapabilityRegistry().also { instance = it }
    }

    fun setInstance(registry: CapabilityRegistry) {
        instance = registry
    }
}