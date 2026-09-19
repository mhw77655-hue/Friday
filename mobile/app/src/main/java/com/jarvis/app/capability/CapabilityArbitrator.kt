package com.jarvis.app.capability

import com.jarvis.app.body.LanguageRouter
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.genome.Genome
import com.jarvis.app.genome.ResourceBudget
import com.jarvis.app.resource.ResourceGovernor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CapabilityArbitrator - Selects capabilities via the nervous system
 *
 * Selection criteria:
 * - TASK + QUALITY_REQUIREMENT + LATENCY_REQUIREMENT + RESOURCE_BUDGET +
 *   CURRENT_HEALTH + AVAILABLE_MODELS + LANGUAGE + USER_CONTEXT
 *
 * Uses existing nervous system (BodyCoordinator) rather than creating a second orchestrator
 */
class CapabilityArbitrator(
    private val capabilityRegistry: CapabilityRegistry,
    private val resourceGovernor: ResourceGovernor,
    private val languageRouter: LanguageRouter,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    /** Arbitration request */
    data class ArbitrationRequest(
        val task: TaskType,
        val language: String? = null,
        val minQuality: CapabilityRegistry.QualityTier = CapabilityRegistry.QualityTier.MEDIUM,
        val maxLatencyMs: Long = Long.MAX_VALUE,
        val maxRamMb: Long = Long.MAX_VALUE,
        val requireStreaming: Boolean = false,
        val userContext: Map<String, String> = emptyMap(),
        val fallbackAllowed: Boolean = true
    )

    /** Task types for capability selection */
    enum class TaskType {
        WAKE_WORD_DETECTION,
        SPEECH_RECOGNITION,
        LANGUAGE_DETECTION,
        TEXT_GENERATION,
        REASONING,
        MEMORY_RETRIEVAL,
        MEMORY_PROMOTION,
        SPEECH_SYNTHESIS,
        VOCABULARY_LOOKUP,
        VISUAL_RENDERING,
        SYSTEM_DIAGNOSTICS,
        CONVERSATION_MANAGEMENT
    }

    /** Arbitration result */
    sealed class ArbitrationResult {
        data class Selected(
            val capability: CapabilityRegistry.Capability,
            val reason: String,
            val fallbackChain: List<CapabilityRegistry.Capability>
        ) : ArbitrationResult()

        data class Degraded(
            val capability: CapabilityRegistry.Capability,
            val reason: String,
            val originalChoice: CapabilityRegistry.Capability,
            val degradationReason: String
        ) : ArbitrationResult()

        data class Failed(
            val reason: String,
            val triedCapabilities: List<CapabilityRegistry.Capability>
        ) : ArbitrationResult()

        data class NotNeeded(
            val reason: String
        ) : ArbitrationResult() // e.g., "stop" command doesn't need LLM
    }

    /** Arbitration config */
    data class ArbitrationConfig(
        val enableDegradation: Boolean = true,
        val enableFallback: Boolean = true,
        val maxFallbackAttempts: Int = 2,
        val resourceBufferPercent: Float = 0.1f, // Keep 10% buffer
        val healthThreshold: CapabilityRegistry.Health = CapabilityRegistry.Health.HEALTHY
    )

    private var config = ArbitrationConfig()

    // Metrics
    private val _arbitrationStats = MutableStateFlow<ArbitrationStats>(ArbitrationStats())
    val arbitrationStats: StateFlow<ArbitrationStats> = _arbitrationStats.asStateFlow()

    /** Main arbitration entry point */
    suspend fun arbitrate(request: ArbitrationRequest): ArbitrationResult = withContext(Dispatchers.IO) {
        // Special cases that don't need LLM
        if (isSimpleCommand(request)) {
            _arbitrationStats.update { it.recordSimpleCommand() }
            return@withContext ArbitrationResult.NotNeeded("Simple command handled without LLM")
        }

        // Determine required capability category
        val category = mapTaskToCategory(request.task)

        // Find best capability
        val bestCapability = capabilityRegistry.findBest(
            category = category,
            language = request.language,
            minQuality = request.minQuality,
            maxRamMb = request.maxRamMb,
            maxLatencyMs = request.maxLatencyMs,
            requireHealthy = true
        )

        if (bestCapability == null) {
            // Try with relaxed constraints
            val relaxed = capabilityRegistry.findBest(
                category = category,
                language = request.language,
                minQuality = CapabilityRegistry.QualityTier.LOW,
                maxRamMb = Long.MAX_VALUE,
                maxLatencyMs = Long.MAX_VALUE,
                requireHealthy = false
            )

            if (relaxed != null && config.enableDegradation) {
                _arbitrationStats.update { it.recordDegraded() }
                return@withContext ArbitrationResult.Degraded(
                    capability = relaxed,
                    reason = "Using degraded capability due to resource constraints",
                    originalChoice = bestCapability!!,
                    degradationReason = "No healthy capability met requirements"
                )
            }

            _arbitrationStats.update { it.recordFailed() }
            return@withContext ArbitrationResult.Failed(
                reason = "No suitable capability found for ${category.name}",
                triedCapabilities = capabilityRegistry.getByCategory(category)
            )
        }

        // Check resource availability
        val admission = resourceGovernor.requestAdmission(
            genome = Genome(
                id = "arbitration_${bestCapability.id}",
                version = 1,
                resourceBudget = ResourceBudget(
                    maxMemoryMb = bestCapability.ramEstimateMb,
                    maxCpuPercent = bestCapability.cpuEstimatePercent
                )
            ),
            caller = "CapabilityArbitrator"
        )

        if (!admission.admitted && config.enableDegradation) {
            // Try fallback chain
            val fallbackChain = capabilityRegistry.getFallbackChain(bestCapability.id)
            for (fallback in fallbackChain) {
                val fallbackAdmission = resourceGovernor.requestAdmission(
                    genome = Genome(
                        id = "arbitration_${fallback.id}",
                        version = 1,
                        resourceBudget = ResourceBudget(
                            maxMemoryMb = fallback.ramEstimateMb,
                            maxCpuPercent = fallback.cpuEstimatePercent
                        )
                    ),
                    caller = "CapabilityArbitrator"
                )

                if (fallbackAdmission.admitted) {
                    _arbitrationStats.update { it.recordFallback() }
                    return@withContext ArbitrationResult.Degraded(
                        capability = fallback,
                        reason = "Fallback due to resource constraints",
                        originalChoice = bestCapability,
                        degradationReason = "Primary capability exceeds resource budget"
                    )
                }
            }

            _arbitrationStats.update { it.recordFailed() }
            return@withContext ArbitrationResult.Failed(
                reason = "Insufficient resources for ${bestCapability.name}",
                triedCapabilities = listOf(bestCapability)
            )
        }

        // Check capability health
        if (bestCapability.health != CapabilityRegistry.Health.HEALTHY) {
            if (config.enableFallback && request.fallbackAllowed) {
                val fallbackChain = capabilityRegistry.getFallbackChain(bestCapability.id)
                for (fallback in fallbackChain) {
                    if (fallback.health == CapabilityRegistry.Health.HEALTHY) {
                        _arbitrationStats.update { it.recordFallback() }
                        return@withContext ArbitrationResult.Degraded(
                            capability = fallback,
                            reason = "Fallback due to primary capability health: ${bestCapability.health}",
                            originalChoice = bestCapability,
                            degradationReason = "Primary capability unhealthy"
                        )
                    }
                }
            }

            if (config.enableDegradation) {
                _arbitrationStats.update { it.recordDegraded() }
                return@withContext ArbitrationResult.Degraded(
                    capability = bestCapability,
                    reason = "Using degraded capability: ${bestCapability.health}",
                    originalChoice = bestCapability,
                    degradationReason = "Capability health is ${bestCapability.health}"
                )
            }
        }

        // Success - return selected capability with fallback chain
        val fallbackChain = if (config.enableFallback && request.fallbackAllowed) {
            capabilityRegistry.getFallbackChain(bestCapability.id)
        } else emptyList()

        _arbitrationStats.update { it.recordSuccess() }
        ArbitrationResult.Selected(
            capability = bestCapability,
            reason = "Best match for ${request.task.name} (${category.name})",
            fallbackChain = fallbackChain
        )
    }

    /** Check if request is a simple command that doesn't need LLM */
    private fun isSimpleCommand(request: ArbitrationRequest): Boolean {
        if (request.task != TaskType.TEXT_GENERATION && request.task != TaskType.REASONING) {
            return false
        }

        val userText = request.userContext["current_input"]?.lowercase() ?: ""
        val simpleCommands = setOf(
            "stop", "cancel", "wait", "pause", "resume", "continue",
            "yes", "no", "ok", "okay", "sure", "fine",
            "repeat", "again", "what", "huh", "pardon",
            "hello", "hi", "hey", "thanks", "thank you"
        )

        return simpleCommands.any { userText.trim() == it || userText.trim().startsWith("$it ") }
    }

    /** Map task type to capability category */
    private fun mapTaskToCategory(task: TaskType): CapabilityRegistry.Category = when (task) {
        TaskType.WAKE_WORD_DETECTION -> CapabilityRegistry.Category.WAKE_WORD
        TaskType.SPEECH_RECOGNITION -> CapabilityRegistry.Category.STT
        TaskType.LANGUAGE_DETECTION -> CapabilityRegistry.Category.LANGUAGE
        TaskType.TEXT_GENERATION, TaskType.REASONING -> CapabilityRegistry.Category.LLM
        TaskType.MEMORY_RETRIEVAL, TaskType.MEMORY_PROMOTION -> CapabilityRegistry.Category.MEMORY
        TaskType.SPEECH_SYNTHESIS -> CapabilityRegistry.Category.TTS
        TaskType.VOCABULARY_LOOKUP -> CapabilityRegistry.Category.VOCABULARY
        TaskType.VISUAL_RENDERING -> CapabilityRegistry.Category.VISUAL
        TaskType.SYSTEM_DIAGNOSTICS -> CapabilityRegistry.Category.SYSTEM
        TaskType.CONVERSATION_MANAGEMENT -> CapabilityRegistry.Category.SYSTEM
    }

    /** Update arbitration config */
    fun updateConfig(newConfig: ArbitrationConfig) {
        config = newConfig
    }

    /** Get current config */
    fun getConfig(): ArbitrationConfig = config

    /** Arbitration statistics */
    data class ArbitrationStats(
        val totalArbitrations: Long = 0,
        val successful: Long = 0,
        val degraded: Long = 0,
        val fallback: Long = 0,
        val failed: Long = 0,
        val simpleCommands: Long = 0
    ) {
        fun recordSuccess() = copy(totalArbitrations = totalArbitrations + 1, successful = successful + 1)

        fun recordDegraded() = copy(totalArbitrations = totalArbitrations + 1, degraded = degraded + 1)

        fun recordFallback() = copy(totalArbitrations = totalArbitrations + 1, fallback = fallback + 1)

        fun recordFailed() = copy(totalArbitrations = totalArbitrations + 1, failed = failed + 1)

        fun recordSimpleCommand() = copy(totalArbitrations = totalArbitrations + 1, simpleCommands = simpleCommands + 1)

        val successRate: Float get() = if (totalArbitrations > 0) successful.toFloat() / totalArbitrations else 0f
    }
}