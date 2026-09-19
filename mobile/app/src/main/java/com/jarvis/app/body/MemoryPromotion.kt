package com.jarvis.app.body

import android.content.Context
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.humancore.store.StoreKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import android.util.Log
import kotlin.math.abs

private const val TAG = "MemoryPromotion"

/**
 * MemoryPromotion - Evaluates and promotes conversation content to permanent memory
 *
 * Policy:
 * - Not every conversation becomes permanent memory
 * - Candidate memories evaluated for: importance, recurrence, user-specificity,
 *   future usefulness, confidence, contradiction, temporal relevance
 * - Decisions: IGNORE, SHORT_TERM, SESSION, EPISODIC, SEMANTIC, USER_PREFERENCE, VOCABULARY, IDENTITY
 */
class MemoryPromotion(
    private val memoryStore: MemoryStore,
    private val vocabularyStore: VocabularyStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val onFailure: (com.jarvis.app.failure.FailureReport) -> Unit = {}
) {

    /** Promotion decision */
    enum class PromotionDecision {
        IGNORE,           // Not worth remembering
        SHORT_TERM,       // Keep in conversation context only
        SESSION,          // Keep for current session
        EPISODIC,         // Store as episodic memory (important conversation)
        SEMANTIC,         // Store as semantic fact/knowledge
        USER_PREFERENCE,  // Store as user preference
        VOCABULARY,       // Extract vocabulary/pronunciation
        IDENTITY          // Core identity/belief (rare)
    }

    /** Candidate memory for evaluation */
    data class CandidateMemory(
        val userText: String,
        val assistantText: String,
        val context: List<MemoryItem> = emptyList(),
        val tags: List<String> = emptyList(),
        val userInitiated: Boolean = false,
        val emotionalWeight: Float = 0f,      // -1 to 1
        val taskRelevance: Float = 0f,        // 0-1
        val novelty: Float = 0f,              // 0-1 (how new is this info)
        val confidence: Float = 0.5f          // Confidence in relevance
    )

    /** Promotion configuration */
    data class PromotionConfig(
        val minImportanceForEpisodic: Float = 0.7f,
        val minRecurrenceForSemantic: Int = 3,
        val minUserSpecificityForPreference: Float = 0.8f,
        val minFutureUsefulness: Float = 0.6f,
        val contradictionThreshold: Float = 0.8f,
        val temporalRelevanceHours: Long = 168, // 1 week
        val maxPromotionsPerTurn: Int = 3,
        val enableAutoVocabulary: Boolean = true,
        val enableAutoPreference: Boolean = true
    )

    private var config = PromotionConfig()

    // Statistics
    private val _stats = MutableStateFlow<PromotionStats>(PromotionStats())
    val stats: StateFlow<PromotionStats> = _stats.asStateFlow()

    /** Evaluate a candidate memory and promote if warranted */
    suspend fun evaluateAndPromote(candidate: CandidateMemory): List<PromotionDecision> {
        val decisions = mutableListOf<PromotionDecision>()

        // 1. Check for vocabulary extraction
        if (config.enableAutoVocabulary) {
            val vocabDecision = evaluateVocabulary(candidate)
            if (vocabDecision != PromotionDecision.IGNORE) {
                decisions.add(vocabDecision)
                promoteVocabulary(candidate, vocabDecision)
            }
        }

        // 2. Check for user preference
        if (config.enableAutoPreference) {
            val prefDecision = evaluateUserPreference(candidate)
            if (prefDecision != PromotionDecision.IGNORE) {
                decisions.add(prefDecision)
                promotePreference(candidate, prefDecision)
            }
        }

        // 3. Evaluate for episodic memory
        val episodicDecision = evaluateEpisodic(candidate)
        if (episodicDecision != PromotionDecision.IGNORE) {
            decisions.add(episodicDecision)
            promoteEpisodic(candidate, episodicDecision)
        }

        // 4. Evaluate for semantic memory
        val semanticDecision = evaluateSemantic(candidate)
        if (semanticDecision != PromotionDecision.IGNORE) {
            decisions.add(semanticDecision)
            promoteSemantic(candidate, semanticDecision)
        }

        // 5. Check for identity/belief statements
        val identityDecision = evaluateIdentity(candidate)
        if (identityDecision != PromotionDecision.IGNORE) {
            decisions.add(identityDecision)
            promoteIdentity(candidate, identityDecision)
        }

        // Update stats
        _stats.update { it.record(decisions) }

        return decisions
    }

    /** Evaluate for vocabulary extraction */
    private fun evaluateVocabulary(candidate: CandidateMemory): PromotionDecision {
        // Look for explicit teaching patterns
        val userText = candidate.userText.lowercase()
        val teachPatterns = listOf(
            "pronounced", "pronounce", "say it", "say ", "the word is",
            "it's pronounced", "pronunciation is", "how to say"
        )

        if (teachPatterns.any { userText.contains(it) }) {
            return PromotionDecision.VOCABULARY
        }

        // Check for unknown words in assistant response that might need correction
        // This would be triggered by user correction in next turn
        return PromotionDecision.IGNORE
    }

    /** Evaluate for user preference */
    private fun evaluateUserPreference(candidate: CandidateMemory): PromotionDecision {
        val userText = candidate.userText.lowercase()
        val prefPatterns = listOf(
            "i prefer", "i like", "i don't like", "i hate", "i love",
            "my favorite", "i usually", "i always", "i never",
            "set my", "change my", "update my"
        )

        if (prefPatterns.any { userText.contains(it) } && candidate.confidence > 0.6f) {
            return PromotionDecision.USER_PREFERENCE
        }

        return PromotionDecision.IGNORE
    }

    /** Evaluate for episodic memory */
    private fun evaluateEpisodic(candidate: CandidateMemory): PromotionDecision {
        var score = 0f

        // Importance indicators
        if (candidate.userInitiated) score += 0.2f
        if (candidate.emotionalWeight != 0f) score += abs(candidate.emotionalWeight) * 0.2f
        if (candidate.taskRelevance > 0.5f) score += 0.2f
        if (candidate.novelty > 0.7f) score += 0.2f
        if (candidate.confidence > 0.8f) score += 0.2f

        // Length and complexity
        val totalLength = candidate.userText.length + candidate.assistantText.length
        if (totalLength > 200) score += 0.1f

        // Tags from Human Core
        if (candidate.tags.any { it in setOf("important", "decision", "commitment", "promise", "fact") }) {
            score += 0.3f
        }

        return if (score >= config.minImportanceForEpisodic) PromotionDecision.EPISODIC else PromotionDecision.IGNORE
    }

    /** Evaluate for semantic memory */
    private fun evaluateSemantic(candidate: CandidateMemory): PromotionDecision {
        // Semantic facts are generalizable knowledge
        val userText = candidate.userText.lowercase()
        val factPatterns = listOf(
            "is a", "are a", "means", "defined as", "refers to",
            "equals", "is equal to", "stands for"
        )

        // Check recurrence (would need historical tracking)
        // For now, based on pattern matching
        if (factPatterns.any { userText.contains(it) } && candidate.novelty > 0.5f) {
            return PromotionDecision.SEMANTIC
        }

        return PromotionDecision.IGNORE
    }

    /** Evaluate for identity/belief */
    private fun evaluateIdentity(candidate: CandidateMemory): PromotionDecision {
        val userText = candidate.userText.lowercase()
        val identityPatterns = listOf(
            "i am", "i believe", "my values", "i stand for",
            "i identify as", "my principle", "i will never", "i always"
        )

        if (identityPatterns.any { userText.contains(it) } && candidate.confidence > 0.8f) {
            return PromotionDecision.IDENTITY
        }

        return PromotionDecision.IGNORE
    }

    /** Promote vocabulary */
    private fun promoteVocabulary(candidate: CandidateMemory, decision: PromotionDecision) {
        // Extract word/pronunciation from teaching pattern
        val userText = candidate.userText
        // Pattern: "the word X is pronounced Y" or "X is pronounced Y"
        val patterns = listOf(
            Regex("word\\s+(\\w+)\\s+is\\s+pronounced\\s+([^\\.]+)"),
            Regex("(\\w+)\\s+is\\s+pronounced\\s+([^\\.]+)"),
            Regex("pronounce\\s+(\\w+)\\s+as\\s+([^\\.]+)"),
            Regex("say\\s+(\\w+)\\s+as\\s+([^\\.]+)")
        )

        for (pattern in patterns) {
            val match = pattern.find(userText)
            if (match != null) {
                val word = match.groupValues[1]
                val pronunciation = match.groupValues[2].trim()
                vocabularyStore.teachWord(word, pronunciation, detectLanguage(candidate.userText))
                break
            }
        }
    }

    /** Promote user preference */
    private fun promotePreference(candidate: CandidateMemory, decision: PromotionDecision) {
        // Extract preference key/value
        val userText = candidate.userText.lowercase()
        // Simple extraction - in practice would use NLP
        val prefKey = extractPreferenceKey(userText)
        val prefValue = extractPreferenceValue(userText)

        if (prefKey.isNotBlank() && prefValue.isNotBlank()) {
            memoryStore.storePreference(prefKey, prefValue)
        }
    }

    /** Promote episodic memory */
    private fun promoteEpisodic(candidate: CandidateMemory, decision: PromotionDecision) {
        val relevance = calculateEpisodicRelevance(candidate)
        memoryStore.promoteToEpisodic(
            candidate.userText,
            candidate.assistantText,
            candidate.tags,
            relevance
        )
    }

    /** Promote semantic memory */
    private fun promoteSemantic(candidate: CandidateMemory, decision: PromotionDecision) {
        // Store as fact
        val factKey = generateFactKey(candidate.userText)
        val factValue = candidate.assistantText
        memoryStore.storeFact(factKey, factValue, candidate.tags)
    }

    /** Promote identity */
    private fun promoteIdentity(candidate: CandidateMemory, decision: PromotionDecision) {
        // Store as high-relevance episodic with identity tag
        val tags = candidate.tags + listOf("identity", "belief")
        memoryStore.promoteToEpisodic(
            candidate.userText,
            candidate.assistantText,
            tags,
            0.95f
        )
    }

    /** Calculate episodic relevance score */
    private fun calculateEpisodicRelevance(candidate: CandidateMemory): Float {
        var score = 0.5f
        if (candidate.userInitiated) score += 0.1f
        if (candidate.emotionalWeight != 0f) score += abs(candidate.emotionalWeight) * 0.15f
        if (candidate.taskRelevance > 0.5f) score += 0.1f
        if (candidate.novelty > 0.7f) score += 0.1f
        if (candidate.confidence > 0.8f) score += 0.15f
        return minOf(1.0f, score)
    }

    /** Detect language from text */
    private fun detectLanguage(text: String): String {
        val hasArabic = text.any { c ->
            c >= '؀' && c <= 'ۿ' ||
            c >= 'ݐ' && c <= 'ݿ' ||
            c >= 'ࢠ' && c <= 'ࣿ' ||
            c >= 'ﭐ' && c <= '﷿' ||
            c >= 'ﹰ' && c <= '﻿'
        }
        return if (hasArabic) "ar" else "en"
    }

    /** Extract preference key from text */
    private fun extractPreferenceKey(text: String): String {
        // Simplified - in production would use NLP
        val patterns = listOf(
            Regex("prefer\\s+(\\w+)"),
            Regex("like\\s+(\\w+)"),
            Regex("favorite\\s+(\\w+)"),
            Regex("set\\s+my\\s+(\\w+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) return match.groupValues[1]
        }
        return "preference_${System.currentTimeMillis()}"
    }

    /** Extract preference value from text */
    private fun extractPreferenceValue(text: String): String {
        // Simplified
        return text.takeLast(100)
    }

    /** Generate fact key from text */
    private fun generateFactKey(text: String): String {
        return text.lowercase()
            .replace(Regex("\\W+"), "_")
            .take(50)
    }

    /** Get promotion stats */
    data class PromotionStats(
        val totalEvaluated: Long = 0,
        val promotedEpisodic: Long = 0,
        val promotedSemantic: Long = 0,
        val promotedPreference: Long = 0,
        val promotedVocabulary: Long = 0,
        val promotedIdentity: Long = 0,
        val ignored: Long = 0
    ) {
        fun record(decisions: List<PromotionDecision>): PromotionStats {
            var next = this
            next = next.copy(totalEvaluated = next.totalEvaluated + 1)
            decisions.forEach { decision ->
                next = when (decision) {
                    PromotionDecision.EPISODIC -> next.copy(promotedEpisodic = next.promotedEpisodic + 1)
                    PromotionDecision.SEMANTIC -> next.copy(promotedSemantic = next.promotedSemantic + 1)
                    PromotionDecision.USER_PREFERENCE -> next.copy(promotedPreference = next.promotedPreference + 1)
                    PromotionDecision.VOCABULARY -> next.copy(promotedVocabulary = next.promotedVocabulary + 1)
                    PromotionDecision.IDENTITY -> next.copy(promotedIdentity = next.promotedIdentity + 1)
                    else -> next.copy(ignored = next.ignored + 1)
                }
            }
            return next
        }
    }

    /** Update configuration */
    fun updateConfig(newConfig: PromotionConfig) {
        config = newConfig
    }

    /** Get current config */
    fun getConfig(): PromotionConfig = config
}