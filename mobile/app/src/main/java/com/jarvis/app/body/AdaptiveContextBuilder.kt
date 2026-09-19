package com.jarvis.app.body

import com.jarvis.app.model.adapters.LocalInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AdaptiveContextBuilder - Builds context for LLM with intelligent budgeting
 *
 * Principles:
 * - Retrieve the smallest amount of memory necessary to produce the best response
 * - Rank and compress context based on relevance
 * - Under resource pressure, automatically reduce context before reducing core functionality
 * - Configurable token budget
 */
class AdaptiveContextBuilder(
    private val memoryStore: MemoryStore,
    private val vocabularyStore: VocabularyStore,
    private val localInferenceEngine: LocalInferenceEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    /** Configuration for context budgeting */
    data class Config(
        val maxContextTokens: Int = 4096,           // Total context window
        val maxPromptTokens: Int = 3000,            // Max for prompt (leaves room for generation)
        val maxMemoryTokens: Int = 1500,            // Max tokens for memory retrieval
        val maxConversationTokens: Int = 1000,      // Max tokens for recent conversation
        val maxVocabularyTokens: Int = 200,         // Max tokens for vocabulary hints
        val recencyWeight: Float = 0.4f,            // Weight for recent items
        val relevanceWeight: Float = 0.4f,          // Weight for semantic relevance
        val importanceWeight: Float = 0.2f,         // Weight for importance score
        val minRelevanceThreshold: Float = 0.3f,    // Minimum relevance to include
        val compressionEnabled: Boolean = true,     // Enable context compression
        val compressionRatio: Float = 0.5f,         // Target compression ratio
    )

    private var config = Config()

    /** Simple context input (avoids dependency on human core protocol) */
    data class ConversationTurn(
        val userText: String,
        val jarvisText: String
    )

    data class SystemContextInput(
        val currentLanguage: String = "en",
        val turnCount: Int = 0,
        val activeTask: String? = null
    )

    // Context sections (for debugging/metrics)
    private val _lastContextBreakdown = MutableStateFlow<ContextBreakdown?>(null)
    val lastContextBreakdown: StateFlow<ContextBreakdown?> = _lastContextBreakdown.asStateFlow()

    private val _lastBuildTimeMs = MutableStateFlow<Long>(0)
    val lastBuildTimeMs: StateFlow<Long> = _lastBuildTimeMs.asStateFlow()

    /** Build context for a given request */
    suspend fun buildContext(
        currentTurn: String,
        conversationHistory: List<ConversationTurn>,
        systemContext: SystemContextInput,
        activeTask: String? = null,
        userProfile: Map<String, String> = emptyMap(),
        resourcePressure: Float = 0f  // 0 = no pressure, 1 = max pressure
    ): BuildResult {
        val startTime = System.currentTimeMillis()

        // Adjust budget based on resource pressure
        val effectiveConfig = adjustConfigForPressure(resourcePressure)

        // 1. Build conversation context (most recent turns)
        val conversationContext = buildConversationContext(
            conversationHistory,
            effectiveConfig.maxConversationTokens
        )

        // 2. Retrieve relevant memories
        val memoryContext = retrieveRelevantMemories(
            currentTurn,
            conversationHistory,
            activeTask,
            effectiveConfig.maxMemoryTokens
        )

        // 3. Get vocabulary hints for current language
        val vocabularyContext = buildVocabularyContext(
            currentTurn,
            systemContext.currentLanguage,
            effectiveConfig.maxVocabularyTokens
        )

        // 4. Build user profile context
        val profileContext = buildProfileContext(userProfile)

        // 5. Build system context (JARVIS state, capabilities)
        val systemContextText = buildSystemContext(systemContext)

        // 6. Combine and rank all sections
        val rankedSections = rankAndCombineSections(
            listOf(
                ContextSection("system", systemContextText, SectionPriority.CRITICAL),
                ContextSection("profile", profileContext, SectionPriority.HIGH),
                ContextSection("conversation", conversationContext, SectionPriority.HIGH),
                ContextSection("memory", memoryContext, SectionPriority.MEDIUM),
                ContextSection("vocabulary", vocabularyContext, SectionPriority.LOW)
            ),
            effectiveConfig
        )

        // 7. Apply token budget
        val finalContext = applyTokenBudget(rankedSections, effectiveConfig)

        // 8. Add current turn
        val prompt = buildPrompt(finalContext, currentTurn)

        // 9. Verify token count
        val tokenCount = localInferenceEngine.countTokens(prompt)
        val withinBudget = tokenCount <= effectiveConfig.maxPromptTokens

        val buildTime = System.currentTimeMillis() - startTime
        _lastBuildTimeMs.value = buildTime

        val breakdown = ContextBreakdown(
            systemTokens = countTokens(systemContextText),
            profileTokens = countTokens(profileContext),
            conversationTokens = countTokens(conversationContext),
            memoryTokens = countTokens(memoryContext),
            vocabularyTokens = countTokens(vocabularyContext),
            totalTokens = tokenCount,
            budgetTokens = effectiveConfig.maxPromptTokens,
            withinBudget = withinBudget,
            buildTimeMs = buildTime
        )
        _lastContextBreakdown.value = breakdown

        return BuildResult(
            prompt = prompt,
            tokenCount = tokenCount,
            withinBudget = withinBudget,
            breakdown = breakdown,
            sectionsUsed = rankedSections.filter { it.included }.map { it.name }
        )
    }

    /** Adjust config based on resource pressure */
    private fun adjustConfigForPressure(pressure: Float): Config {
        if (pressure <= 0f) return config

        // Reduce budgets proportionally
        val factor = 1f - (pressure * 0.7f)  // Max 70% reduction
        return config.copy(
            maxPromptTokens = (config.maxPromptTokens * factor).toInt(),
            maxMemoryTokens = (config.maxMemoryTokens * factor).toInt(),
            maxConversationTokens = (config.maxConversationTokens * factor).toInt(),
            maxVocabularyTokens = (config.maxVocabularyTokens * factor).toInt()
        )
    }

    /** Build conversation context from recent history */
    private fun buildConversationContext(
        history: List<ConversationTurn>,
        maxTokens: Int
    ): String {
        if (history.isEmpty()) return ""

        val builder = StringBuilder()
        builder.append("Recent conversation:\n")

        var tokenCount = 0
        // Iterate from most recent
        for (turn in history.reversed()) {
            val turnText = "User: ${turn.userText}\nJARVIS: ${turn.jarvisText}\n"
            val turnTokens = countTokens(turnText)

            if (tokenCount + turnTokens > maxTokens) break

            builder.insert(builder.indexOf("\n") + 1, turnText)
            tokenCount += turnTokens
        }

        return builder.toString()
    }

    /** Retrieve and rank relevant memories */
    private suspend fun retrieveRelevantMemories(
        currentTurn: String,
        conversationHistory: List<ConversationTurn>,
        activeTask: String?,
        maxTokens: Int
    ): String {
        // Query memory store with current turn as query
        val memories = memoryStore.queryMemories(currentTurn, limit = 20)

        if (memories.isEmpty()) return ""

        // Rank memories by composite score
        val rankedMemories = memories.map { memory ->
            val recencyScore = calculateRecencyScore(memory.timestamp)
            val relevanceScore = calculateRelevanceScore(memory.content, currentTurn, conversationHistory)
            val importanceScore = memory.relevance

            val compositeScore = config.recencyWeight * recencyScore +
                    config.relevanceWeight * relevanceScore +
                    config.importanceWeight * importanceScore

            RankedMemory(memory, compositeScore, recencyScore, relevanceScore, importanceScore)
        }.filter { it.compositeScore >= config.minRelevanceThreshold }
            .sortedByDescending { it.compositeScore }

        // Build memory context within token budget
        val builder = StringBuilder()
        builder.append("Relevant memories:\n")

        var tokenCount = 0
        for (ranked in rankedMemories) {
            val memoryText = "- ${ranked.memory.content} (${ranked.memory.type})\n"
            val memoryTokens = countTokens(memoryText)

            if (tokenCount + memoryTokens > maxTokens) break

            builder.append(memoryText)
            tokenCount += memoryTokens
        }

        return if (tokenCount > 0) builder.toString() else ""
    }

    /** Build vocabulary hints for current language */
    private fun buildVocabularyContext(
        currentTurn: String,
        language: String,
        maxTokens: Int
    ): String {
        val vocabEntries = vocabularyStore.getEntriesForLanguage(language)
            .filter { currentTurn.contains(it.word, ignoreCase = true) }
            .take(10)

        if (vocabEntries.isEmpty()) return ""

        val builder = StringBuilder()
        builder.append("Pronunciation hints:\n")

        var tokenCount = 0
        for (entry in vocabEntries) {
            val entryText = "- ${entry.word}: ${entry.pronunciation}\n"
            val entryTokens = countTokens(entryText)

            if (tokenCount + entryTokens > maxTokens) break

            builder.append(entryText)
            tokenCount += entryTokens
        }

        return builder.toString()
    }

    /** Build user profile context */
    private fun buildProfileContext(profile: Map<String, String>): String {
        if (profile.isEmpty()) return ""

        val builder = StringBuilder()
        builder.append("User profile:\n")

        for ((key, value) in profile) {
            builder.append("- $key: $value\n")
        }

        return builder.toString()
    }

    /** Build system context */
    private fun buildSystemContext(systemContext: SystemContextInput): String {
        val builder = StringBuilder()
        builder.append("System context:\n")
        builder.append("- Current time: ${java.time.Instant.now()}\n")
        builder.append("- Language: ${systemContext.currentLanguage}\n")
        builder.append("- Session turns: ${systemContext.turnCount}\n")
        builder.append("- Active task: ${systemContext.activeTask ?: "none"}\n")
        return builder.toString()
    }

    /** Rank and combine context sections */
    private fun rankAndCombineSections(
        sections: List<ContextSection>,
        effectiveConfig: Config
    ): List<RankedSection> {
        return sections.map { section ->
            val tokens = countTokens(section.content)
            val priorityScore = when (section.priority) {
                SectionPriority.CRITICAL -> 1.0f
                SectionPriority.HIGH -> 0.8f
                SectionPriority.MEDIUM -> 0.5f
                SectionPriority.LOW -> 0.2f
            }

            RankedSection(
                name = section.name,
                content = section.content,
                tokens = tokens,
                priority = section.priority,
                priorityScore = priorityScore,
                included = true
            )
        }.sortedByDescending { it.priorityScore }
    }

    /** Apply token budget to ranked sections */
    private fun applyTokenBudget(
        sections: List<RankedSection>,
        effectiveConfig: Config
    ): String {
        val builder = StringBuilder()
        var totalTokens = 0

        for (section in sections) {
            if (totalTokens + section.tokens <= effectiveConfig.maxPromptTokens) {
                builder.append(section.content).append("\n")
                totalTokens += section.tokens
            } else if (effectiveConfig.compressionEnabled && section.priority != SectionPriority.CRITICAL) {
                // Try to compress
                val compressed = compressSection(section, effectiveConfig.compressionRatio)
                val compressedTokens = countTokens(compressed)
                if (totalTokens + compressedTokens <= effectiveConfig.maxPromptTokens) {
                    builder.append(compressed).append("\n")
                    totalTokens += compressedTokens
                }
            }
        }

        return builder.toString()
    }

    /** Compress a section by summarizing */
    private fun compressSection(section: RankedSection, ratio: Float): String {
        val targetTokens = (section.tokens * ratio).toInt()
        if (targetTokens >= section.tokens) return section.content

        // Simple compression: take first and last parts
        val lines = section.content.lines().toList()
        val keepCount = maxOf(1, (lines.size * ratio).toInt())
        val firstLines = lines.take(keepCount / 2)
        val lastLines = lines.takeLast(keepCount - keepCount / 2)

        val compressed = (firstLines + listOf("... [compressed] ...") + lastLines).joinToString("\n")
        return compressed
    }

    /** Build final prompt */
    private fun buildPrompt(context: String, currentTurn: String): String {
        return """$context
Current user input: $currentTurn
Assistant:""".trimIndent()
    }

    /** Estimate token count (uses local inference engine) */
    private fun countTokens(text: String): Int {
        return if (text.isBlank()) 0 else localInferenceEngine.countTokens(text)
    }

    /** Calculate recency score (0-1) */
    private fun calculateRecencyScore(timestamp: Long): Float {
        val ageMs = System.currentTimeMillis() - timestamp
        val ageHours = ageMs / (1000 * 60 * 60)
        return when {
            ageHours < 1 -> 1.0f
            ageHours < 24 -> 0.8f
            ageHours < 168 -> 0.5f  // 1 week
            else -> 0.2f
        }
    }

    /** Calculate relevance score (0-1) */
    private fun calculateRelevanceScore(
        memoryContent: String,
        currentTurn: String,
        conversationHistory: List<ConversationTurn>
    ): Float {
        // Simple keyword overlap scoring
        val memoryWords = memoryContent.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val currentWords = currentTurn.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()

        if (memoryWords.isEmpty() || currentWords.isEmpty()) return 0f

        val overlap = memoryWords.intersect(currentWords).size
        val maxPossible = minOf(memoryWords.size, currentWords.size)

        return if (maxPossible > 0) overlap.toFloat() / maxPossible else 0f
    }

    /** Update configuration */
    fun updateConfig(newConfig: Config) {
        config = newConfig
    }

    /** Get current config */
    fun getConfig(): Config = config

    /** Data classes */
    enum class SectionPriority { CRITICAL, HIGH, MEDIUM, LOW }

    data class ContextSection(
        val name: String,
        val content: String,
        val priority: SectionPriority
    )

    data class RankedMemory(
        val memory: MemoryItem,
        val compositeScore: Float,
        val recencyScore: Float,
        val relevanceScore: Float,
        val importanceScore: Float
    )

    data class RankedSection(
        val name: String,
        val content: String,
        val tokens: Int,
        val priority: SectionPriority,
        val priorityScore: Float,
        var included: Boolean = true
    )

    data class ContextBreakdown(
        val systemTokens: Int,
        val profileTokens: Int,
        val conversationTokens: Int,
        val memoryTokens: Int,
        val vocabularyTokens: Int,
        val totalTokens: Int,
        val budgetTokens: Int,
        val withinBudget: Boolean,
        val buildTimeMs: Long
    )

    data class BuildResult(
        val prompt: String,
        val tokenCount: Int,
        val withinBudget: Boolean,
        val breakdown: ContextBreakdown,
        val sectionsUsed: List<String>
    )
}