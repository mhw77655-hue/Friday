package com.jarvis.app.cognitive

/**
 * Detects topic/segment boundaries across conversation turns using
 * TextTiling-style lexical-overlap similarity (Jaccard index over
 * keyword sets, stopwords removed).
 *
 * After each turn, the tracker computes similarity between the current
 * turn's keywords and the rolling window of recent turns. When similarity
 * drops below [similarityThreshold], a new segment boundary is recorded.
 *
 * No LLM calls — this is a cheap lexical signal, not semantic understanding.
 */
class TopicTracker(
    /** Rolling window size: number of prior turns to compare against. */
    val windowSize: Int = 3,
    /** Jaccard similarity threshold; when average overlap drops below this, a new segment fires. */
    val similarityThreshold: Double = 0.15,
    /** Minimum keyword set size to perform similarity check. */
    private val minKeywords: Int = 2
) {

    /** Current topic segment id (starts at 0). */
    private var segmentId: Int = 0

    /** True only for the turn that triggered a new segment boundary. */
    private var segmentChanged: Boolean = false

    /** Was the previous turn a boundary? If so, skip this turn's check. */
    private var previousTurnBoundary: Boolean = false

    /** Rolling window of keyword sets from recent turns (oldest first). */
    private val window = ArrayDeque<Set<String>>()

    /** Turn records indexed by segment id, preserving insertion order. */
    private val turnsBySegment = mutableMapOf<Int, MutableList<TurnRecord>>()

    /** A recorded turn. */
    data class TurnRecord(val turnIndex: Long, val text: String)

    /**
     * Record a conversation turn. Computes lexical overlap against
     * the rolling window and fires a segment boundary when similarity
     * drops below [similarityThreshold].
     */
    fun recordTurn(turnIndex: Long, text: String) {
        val keywords = extractKeywords(text)
        segmentChanged = false

        // Skip boundary detection on the turn immediately following a boundary
        // to let the window accumulate turns in the new segment.
        val canCheck = window.size >= 1 && keywords.size >= minKeywords && !previousTurnBoundary

        if (canCheck) {
            val avgSim = averageSimilarity(keywords)
            if (avgSim < similarityThreshold) {
                segmentId++
                segmentChanged = true
                window.clear()
            }
        }

        previousTurnBoundary = segmentChanged

        // Store the turn text for the current segment
        turnsBySegment.getOrPut(segmentId) { mutableListOf() }
            .add(TurnRecord(turnIndex, text))

        window.addLast(keywords)
        if (window.size > windowSize) {
            window.removeFirst()
        }
    }

    /** Current topic segment id. */
    fun currentSegmentId(): Int = segmentId

    /** True only on the turn that triggered a new segment boundary. */
    fun didSegmentChange(): Boolean = segmentChanged

    /** All turn records belonging to the current segment, in order. */
    fun currentSegmentTurns(): List<TurnRecord> =
        turnsBySegment[segmentId]?.toList() ?: emptyList()

    /** All turn records for a specific segment id. */
    fun segmentTurns(segmentId: Int): List<TurnRecord> =
        turnsBySegment[segmentId]?.toList() ?: emptyList()

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    /**
     * Compute average Jaccard similarity between [candidate] and every
     * keyword set currently in the rolling window.
     */
    internal fun averageSimilarity(candidate: Set<String>): Double {
        if (window.isEmpty()) return 1.0
        val sims = window.map { jaccard(candidate, it) }
        return sims.average()
    }

    /** Jaccard similarity between two sets. */
    internal fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    companion object {
        /** Basic English stopwords (high-frequency, low-signal). */
        private val STOPWORDS = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "can", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "through", "during",
            "before", "after", "above", "below", "between", "out", "off", "over",
            "under", "again", "further", "then", "once", "here", "there", "when",
            "where", "why", "how", "all", "both", "each", "few", "more", "most",
            "other", "some", "such", "no", "nor", "not", "only", "own", "same",
            "so", "than", "too", "very", "just", "and", "but", "or", "if",
            "this", "that", "these", "those", "it", "its", "my", "your", "his",
            "her", "our", "their", "what", "which", "who", "whom",
            "i", "me", "you", "he", "she", "we", "they", "them",
            "about", "up", "also", "now", "don", "get", "got", "going",
            "want", "need", "like", "know", "think", "make", "say", "said",
            "tell", "told", "let", "come", "take", "give", "use", "find",
            "right", "well", "way", "even", "new", "want", "because",
            "look", "good", "first", "last", "long", "great", "little",
            "still", "much", "back", "keep", "made", "put", "thing"
        )

        /**
         * Extract keywords from text: lowercase, tokenize, remove stopwords,
         * keep words >= 3 characters.
         */
        fun extractKeywords(text: String): Set<String> {
            return text.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length >= 3 && it !in STOPWORDS }
                .toSet()
        }
    }
}
