package com.jarvis.app.policy

import com.jarvis.app.body.LanguageRouter
import com.jarvis.app.capability.CapabilityRegistry
import com.jarvis.app.model.adapters.LocalInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * InferencePolicyLayer - Adaptive inference routing based on request classification
 *
 * Classifies requests into:
 * - MICRO: stop, yes/no, simple commands, simple facts
 * - NORMAL: Normal conversation
 * - COMPLEX: Requires memory, reasoning, synthesis, planning
 * - SYSTEM: Architecture, capabilities, diagnostics, self-improvement
 */
class InferencePolicyLayer(
    private val localInferenceEngine: LocalInferenceEngine,
    private val capabilityRegistry: CapabilityRegistry,
    private val languageRouter: LanguageRouter,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    /** Inference tier */
    enum class Tier {
        MICRO,      // Minimal processing, no LLM or tiny prompt
        NORMAL,     // Standard context, normal generation
        COMPLEX,    // Deep reasoning, extended context, memory retrieval
        SYSTEM      // Route through system infrastructure
    }

    /** Policy decision */
    data class PolicyDecision(
        val tier: Tier,
        val reason: String,
        val contextBudgetTokens: Int,
        val generationBudgetTokens: Int,
        val temperature: Float,
        val topP: Float,
        val topK: Int,
        val useMemoryRetrieval: Boolean,
        val useReasoning: Boolean,
        val timeoutMs: Long,
        val fallbackTier: Tier? = null
    )

    /** Classification features */
    data class ClassificationFeatures(
        val inputLength: Int,
        val wordCount: Int,
        val hasQuestionMark: Boolean,
        val hasComplexKeywords: Boolean,
        val hasMemoryKeywords: Boolean,
        val hasSystemKeywords: Boolean,
        val hasReasoningKeywords: Boolean,
        val isSimpleCommand: Boolean,
        val language: String,
        val conversationDepth: Int,
        val activeTaskComplexity: Float
    )

    /** Policy configuration */
    data class PolicyConfig(
        // Tier thresholds
        val microMaxTokens: Int = 20,
        val normalMaxTokens: Int = 200,
        val microCommands: Set<String> = setOf(
            "stop", "cancel", "wait", "pause", "resume", "continue",
            "yes", "no", "ok", "okay", "sure", "fine", "yep", "nope",
            "repeat", "again", "what", "huh", "pardon", "louder", "quieter"
        ),
        val complexKeywords: Set<String> = setOf(
            "why", "how", "explain", "reason", "because", "therefore",
            "analyze", "compare", "evaluate", "synthesize", "plan",
            "strategy", "implications", "consequences", "trade-offs"
        ),
        val memoryKeywords: Set<String> = setOf(
            "remember", "recall", "forgot", "memory", "earlier", "before",
            "last time", "previous", "history", "note", "remind"
        ),
        val systemKeywords: Set<String> = setOf(
            "jarvis", "system", "diagnostic", "capability", "model",
            "version", "architecture", "configuration", "settings",
            "performance", "benchmark", "health", "status"
        ),
        val reasoningKeywords: Set<String> = setOf(
            "think", "reason", "logic", "deduce", "infer", "conclude",
            "suppose", "assume", "hypothesis", "theory", "proof"
        ),

        // Budget allocations per tier
        val microContextTokens: Int = 512,
        val microGenerationTokens: Int = 64,
        val normalContextTokens: Int = 2048,
        val normalGenerationTokens: Int = 512,
        val complexContextTokens: Int = 4096,
        val complexGenerationTokens: Int = 1024,
        val systemContextTokens: Int = 3072,
        val systemGenerationTokens: Int = 768,

        // Sampling parameters per tier
        val microTemperature: Float = 0.3f,
        val normalTemperature: Float = 0.7f,
        val complexTemperature: Float = 0.5f,
        val systemTemperature: Float = 0.4f,

        // Timeouts per tier
        val microTimeoutMs: Long = 2000,
        val normalTimeoutMs: Long = 10000,
        val complexTimeoutMs: Long = 30000,
        val systemTimeoutMs: Long = 15000,

        // Adaptive thresholds
        val enableAdaptive: Boolean = true,
        val resourcePressureThreshold: Float = 0.7f
    )

    private var config = PolicyConfig()

    // Statistics
    private val _stats = MutableStateFlow<PolicyStats>(PolicyStats())
    val stats: StateFlow<PolicyStats> = _stats.asStateFlow()

    /** Main entry point: classify request and return policy decision */
    fun decide(input: String, context: DecisionContext): PolicyDecision {
        val features = extractFeatures(input, context)
        val tier = classify(features, context)

        _stats.update { it.record(tier) }

        return buildDecision(tier, features, context)
    }

    /** Extract classification features from input */
    private fun extractFeatures(input: String, context: DecisionContext): ClassificationFeatures {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()
        val words = lower.split(Regex("\\s+")).filter { it.isNotBlank() }

        return ClassificationFeatures(
            inputLength = trimmed.length,
            wordCount = words.size,
            hasQuestionMark = trimmed.contains('?'),
            hasComplexKeywords = config.complexKeywords.any { lower.contains(it) },
            hasMemoryKeywords = config.memoryKeywords.any { lower.contains(it) },
            hasSystemKeywords = config.systemKeywords.any { lower.contains(it) },
            hasReasoningKeywords = config.reasoningKeywords.any { lower.contains(it) },
            isSimpleCommand = config.microCommands.contains(lower) ||
                    (words.size <= 3 && config.microCommands.any { lower.startsWith(it) }),
            language = context.currentLanguage,
            conversationDepth = context.conversationTurnCount,
            activeTaskComplexity = context.activeTaskComplexity
        )
    }

    /** Classify request into tier */
    private fun classify(features: ClassificationFeatures, context: DecisionContext): Tier {
        // Check for simple commands first (highest priority)
        if (features.isSimpleCommand && features.wordCount <= 3) {
            return Tier.MICRO
        }

        // Check for system queries
        if (features.hasSystemKeywords && features.wordCount <= 20) {
            return Tier.SYSTEM
        }

        // Check for complex reasoning
        if (features.hasReasoningKeywords ||
            features.hasComplexKeywords ||
            features.activeTaskComplexity > 0.7f ||
            (features.hasMemoryKeywords && features.conversationDepth > 5)) {
            return Tier.COMPLEX
        }

        // Check for memory-intensive requests
        if (features.hasMemoryKeywords && features.conversationDepth > 2) {
            return Tier.COMPLEX
        }

        // Check for normal conversation
        if (features.wordCount > config.microMaxTokens / 4 || features.hasQuestionMark) {
            return Tier.NORMAL
        }

        // Short inputs without special keywords -> MICRO
        if (features.wordCount <= 5 && features.inputLength <= config.microMaxTokens) {
            return Tier.MICRO
        }

        // Default to NORMAL
        return Tier.NORMAL
    }

    /** Build policy decision from tier */
    private fun buildDecision(
        tier: Tier,
        features: ClassificationFeatures,
        context: DecisionContext
    ): PolicyDecision {
        return when (tier) {
            Tier.MICRO -> PolicyDecision(
                tier = Tier.MICRO,
                reason = "Simple command or short input",
                contextBudgetTokens = config.microContextTokens,
                generationBudgetTokens = config.microGenerationTokens,
                temperature = config.microTemperature,
                topP = 0.9f,
                topK = 20,
                useMemoryRetrieval = false,
                useReasoning = false,
                timeoutMs = config.microTimeoutMs,
                fallbackTier = Tier.NORMAL
            )

            Tier.NORMAL -> PolicyDecision(
                tier = Tier.NORMAL,
                reason = "Normal conversation",
                contextBudgetTokens = config.normalContextTokens,
                generationBudgetTokens = config.normalGenerationTokens,
                temperature = config.normalTemperature,
                topP = 0.9f,
                topK = 40,
                useMemoryRetrieval = features.hasMemoryKeywords,
                useReasoning = features.hasComplexKeywords,
                timeoutMs = config.normalTimeoutMs,
                fallbackTier = Tier.MICRO
            )

            Tier.COMPLEX -> PolicyDecision(
                tier = Tier.COMPLEX,
                reason = "Complex reasoning or memory required",
                contextBudgetTokens = config.complexContextTokens,
                generationBudgetTokens = config.complexGenerationTokens,
                temperature = config.complexTemperature,
                topP = 0.85f,
                topK = 50,
                useMemoryRetrieval = true,
                useReasoning = true,
                timeoutMs = config.complexTimeoutMs,
                fallbackTier = Tier.NORMAL
            )

            Tier.SYSTEM -> PolicyDecision(
                tier = Tier.SYSTEM,
                reason = "System/diagnostic query",
                contextBudgetTokens = config.systemContextTokens,
                generationBudgetTokens = config.systemGenerationTokens,
                temperature = config.systemTemperature,
                topP = 0.8f,
                topK = 30,
                useMemoryRetrieval = true,
                useReasoning = true,
                timeoutMs = config.systemTimeoutMs,
                fallbackTier = Tier.COMPLEX
            )
        }
    }

    /** Apply resource pressure adaptation */
    fun adaptForResourcePressure(decision: PolicyDecision, pressure: Float): PolicyDecision {
        if (!config.enableAdaptive || pressure < config.resourcePressureThreshold) {
            return decision
        }

        // Degrade tier under pressure
        val degradedTier = when (decision.tier) {
            Tier.COMPLEX -> Tier.NORMAL
            Tier.NORMAL -> Tier.MICRO
            Tier.SYSTEM -> Tier.NORMAL
            Tier.MICRO -> Tier.MICRO
        }

        if (degradedTier != decision.tier) {
            val degradedDecision = buildDecision(degradedTier, ClassificationFeatures(
                inputLength = 0, wordCount = 0, hasQuestionMark = false,
                hasComplexKeywords = false, hasMemoryKeywords = false,
                hasSystemKeywords = false, hasReasoningKeywords = false,
                isSimpleCommand = false, language = "en",
                conversationDepth = 0, activeTaskComplexity = 0f
            ), DecisionContext())

            return degradedDecision.copy(
                reason = "${decision.reason} (degraded due to resource pressure: ${(pressure * 100).toInt()}%)",
                fallbackTier = decision.fallbackTier
            )
        }

        return decision
    }

    /** Update configuration */
    fun updateConfig(newConfig: PolicyConfig) {
        config = newConfig
    }

    /** Get current config */
    fun getConfig(): PolicyConfig = config

    /** Decision context */
    data class DecisionContext(
        val currentLanguage: String = "en",
        val conversationTurnCount: Int = 0,
        val activeTaskComplexity: Float = 0f,
        val resourcePressure: Float = 0f,
        val userPreferences: Map<String, String> = emptyMap()
    )

    /** Policy statistics */
    data class PolicyStats(
        val totalDecisions: Long = 0,
        val microDecisions: Long = 0,
        val normalDecisions: Long = 0,
        val complexDecisions: Long = 0,
        val systemDecisions: Long = 0,
        val degradedDecisions: Long = 0
    ) {
        fun record(tier: Tier): PolicyStats = when (tier) {
            Tier.MICRO -> copy(totalDecisions = totalDecisions + 1, microDecisions = microDecisions + 1)
            Tier.NORMAL -> copy(totalDecisions = totalDecisions + 1, normalDecisions = normalDecisions + 1)
            Tier.COMPLEX -> copy(totalDecisions = totalDecisions + 1, complexDecisions = complexDecisions + 1)
            Tier.SYSTEM -> copy(totalDecisions = totalDecisions + 1, systemDecisions = systemDecisions + 1)
        }

        fun recordDegraded(): PolicyStats = copy(degradedDecisions = degradedDecisions + 1)

        val microRatio: Float get() = if (totalDecisions > 0) microDecisions.toFloat() / totalDecisions else 0f
        val normalRatio: Float get() = if (totalDecisions > 0) normalDecisions.toFloat() / totalDecisions else 0f
        val complexRatio: Float get() = if (totalDecisions > 0) complexDecisions.toFloat() / totalDecisions else 0f
    }
}