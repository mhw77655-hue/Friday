package com.jarvis.app.memory

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Composite importance scorer for Galaxy Memory nodes.
 *
 * Computes a score from four signals:
 *  - **Recency**: exponential time-decay since creation/last access
 *  - **Frequency**: saturating access count (diminishing returns past ~5)
 *  - **Semantic relevance**: binary-quantized Hamming similarity to current
 *    context via [EmbeddingProvider] (same mechanism as the vector store's KNN)
 *  - **Salience/valence**: a real intensity signal (e.g. LLM-tagged or rule-tagged)
 *
 * The composite score drives decay rate: high-salience memories decay slower
 * (longer half-life), low-salience memories decay faster — matching an
 * Ebbinghaus-style exponential decay curve.
 *
 * Nothing above [salienceFloor] is ever hard-deleted regardless of age;
 * only its retrieval ranking drops.
 */
class MemoryImportanceScorer(
    private val embeddingProvider: EmbeddingProvider,
    /**
     * Minimum salience value. Memories at or above this floor are never
     * pruned, only decayed in ranking.
     */
    val salienceFloor: Float = 0.1f,
    /** Half-life in milliseconds for a memory with neutral (0.5) salience. */
    val baseHalfLifeMs: Long = 3_600_000L // 1 hour default
) {
    companion object {
        /** Saturation ceiling for frequency scoring. */
        private const val FREQ_SATURATION = 5f
        /** Weight constants for composite score. */
        private const val W_RECENCY = 0.3f
        private const val W_FREQUENCY = 0.15f
        private const val W_RELEVANCE = 0.35f
        private const val W_SALIENCE = 0.2f
    }

    /**
     * Compute composite importance of [node] given [currentContext] text.
     *
     * @param node the memory node to score
     * @param currentContext the current conversation context (for relevance)
     * @param now current timestamp (injectable for testing)
     */
    fun score(node: MemoryNode, currentContext: String, now: Long = System.currentTimeMillis()): Float {
        val recency = recencyScore(node.validFrom, now)
        val frequency = frequencyScore(node.accessCount)
        val relevance = relevanceScore(node, currentContext)
        val salience = node.salience.coerceIn(0f, 1f)

        return W_RECENCY * recency +
            W_FREQUENCY * frequency +
            W_RELEVANCE * relevance +
            W_SALIENCE * salience
    }

    /**
     * Compute the current post-decay strength of [node] at [now].
     *
     * High-salience memories decay slower (longer half-life);
     * low-salience memories decay faster. The result is never zero
     * if the node is above [salienceFloor].
     */
    fun decayedStrength(node: MemoryNode, now: Long = System.currentTimeMillis()): Float {
        val elapsedMs = max(0, now - node.validFrom)
        val halfLifeMs = halfLifeForSalience(node.salience)
        val decayRate = ln(2.0) / halfLifeMs.toDouble()
        var strength = exp(-decayRate * elapsedMs).toFloat()

        // Floor: memories above salienceFloor retain a minimum nonzero strength
        if (node.salience >= salienceFloor) {
            strength = max(strength, salienceFloor)
        }
        return strength.coerceIn(0f, 1f)
    }

    /**
     * Compute the half-life for a given salience value.
     * High salience → longer half-life (decays slower).
     * Low salience → shorter half-life (decays faster).
     */
    private fun halfLifeForSalience(salience: Float): Long {
        // Linearly scale half-life: salience=0 → 0.25x base, salience=1 → 4x base
        val factor = 0.25f + salience * 3.75f
        return (baseHalfLifeMs * factor).toLong().coerceAtLeast(1000L)
    }

    private fun recencyScore(validFrom: Long, now: Long): Float {
        val elapsedMs = max(0, now - validFrom)
        // Exponential decay over 1 hour
        val decayRate = ln(2.0) / baseHalfLifeMs.toDouble()
        return exp(-decayRate * elapsedMs).toFloat().coerceIn(0f, 1f)
    }

    private fun frequencyScore(accessCount: Int): Float {
        // Saturating curve: count/(count + k) — diminishing returns past k
        // At count=1: 1/(1+4)=0.2, count=5: 5/9=0.56, count=20: 20/24=0.83
        val k = 4f
        return (accessCount.toFloat() / (accessCount.toFloat() + k)).coerceIn(0f, 1f)
    }

    private fun relevanceScore(node: MemoryNode, currentContext: String): Float {
        if (currentContext.isBlank()) return 0f
        return try {
            // Semantic relevance is computed via the PRODUCTION embedding
            // provider's binary-quantized representation, exactly like the
            // nearest-neighbour search in the Android-native BLOB store:
            // binary-quantize both vectors (sign-of-value, 1 bit/dim) and take
            // (1 - normalized Hamming distance). This is the same mechanism
            // the vector store uses for KNN, so a node's relevance score ranks
            // consistently with how it would be retrieved by Hamming search.
            val nodeVec = node.embedding ?: embeddingProvider.embed(node.`object`)
            val ctxVec = embeddingProvider.embed(currentContext)
            val nodeQuant = EmbeddingMath.binaryQuantize(nodeVec)
            val ctxQuant = EmbeddingMath.binaryQuantize(ctxVec)
            EmbeddingMath.hammingSimilarity(nodeQuant, ctxQuant)
        } catch (_: Throwable) {
            0f
        }
    }
}
