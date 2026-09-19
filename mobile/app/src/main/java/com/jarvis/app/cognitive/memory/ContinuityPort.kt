package com.jarvis.app.cognitive.memory

import com.jarvis.app.body.MemoryType

/**
 * ContinuityPort - Minimal seam through which the cognitive substrate requests
 * a bounded reconstruction of "the current state relevant to this task."
 *
 * The CognitiveContextBuilder consumes this port so it can enrich a reasoning
 * context with active truths, superseded history, conflicts and unknowns —
 * WITHOUT depending on (or dumping) the whole memory store.
 *
 * Implementations: [ContinuityManager] (in-memory consolidated store) and
 * [MemoryContinuityAdapter] (bridges the persisted [com.jarvis.app.body.MemoryStore]).
 */
interface ContinuityPort {
    /**
     * Reconstruct a bounded continuity state for the given query/situation.
     * Must return promptly and never block on heavy runtime behavior.
     */
    suspend fun reconstructContinuity(
        query: String,
        currentGoal: String? = null,
        activeEntities: List<String> = emptyList()
    ): ContinuitySnapshot
}

/**
 * ContinuitySnapshot - Bounded, structured view of reconstructed state.
 *
 * Deliberately a projection of the full memory system — cognition asks for
 * "what is relevant now", not "everything we have ever known".
 */
data class ContinuitySnapshot(
    /** Current truths (what JARVIS believes to be true now) */
    val activeTruths: List<TruthEntry> = emptyList(),

    /** Superseded truths relevant to the query (historical context) */
    val superseded: List<TruthEntry> = emptyList(),

    /** Unresolved conflicts relevant to the query */
    val conflicts: List<ConflictEntry> = emptyList(),

    /** What cannot be established from memory right now */
    val unknowns: List<String> = emptyList(),

    /** Aggregate confidence across active truths (0.0 - 1.0), 0.0 if none */
    val overallConfidence: Float = 0.0f
) {
    val isEmpty: Boolean get() = activeTruths.isEmpty() && superseded.isEmpty() && conflicts.isEmpty()
}

/** One truth (active or superseded) in a continuity snapshot */
data class TruthEntry(
    val content: String,
    val memoryType: MemoryType,
    val confidence: Float,
    val provenance: String? = null
)

/** One unresolved conflict in a continuity snapshot */
data class ConflictEntry(
    val description: String,
    val involvedMemoryIds: List<String>,
    val confidence: Float
)