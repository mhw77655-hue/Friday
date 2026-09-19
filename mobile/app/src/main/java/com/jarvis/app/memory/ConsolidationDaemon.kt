package com.jarvis.app.memory

/**
 * A raw episodic entry — an individual turn/event that may or may not
 * represent durable knowledge worth promoting to the memory graph.
 */
data class RawEpisodicEntry(
    val id: String,
    var text: String,
    val timestamp: Long,
    /** Pre-computed salience of this entry [0,1]. */
    val salience: Float = 0.5f,
    /** Whether this entry has already been consolidated. */
    var consolidated: Boolean = false
)

/**
 * Result of a consolidation pass.
 */
data class ConsolidationResult(
    /** Number of durable facts promoted to the graph. */
    val promoted: Int,
    /** Number of stale entries compressed/archived. */
    val compressed: Int,
    /** Total entries scanned. */
    val scanned: Int,
    /** Whether this was a no-op (no entries to process). */
    val wasNoop: Boolean = promoted == 0 && compressed == 0
)

/**
 * Background consolidation daemon for Galaxy Memory.
 *
 * Scans raw episodic entries and:
 *  - **Promotes** durable facts (high-salience, content-rich entries) into
 *    [MemoryGraphStore] as nodes/edges, using supersession for contradictions
 *  - **Compresses** low-salience, stale entries into shorter summaries
 *    (archived, not deleted) consistent with the project's recycling principle
 *
 * This must NOT block or slow the live conversational path. It runs entirely
 * off the hot path — either via an idle-cycle scheduler (when that infra
 * exists) or via explicit trigger calls.
 */
class ConsolidationDaemon(
    private val graphStore: MemoryGraphStore,
    private val scorer: MemoryImportanceScorer,
    private val episodicStore: MutableList<RawEpisodicEntry>,
    /**
     * Minimum salience threshold for an entry to be promoted as a durable fact.
     * Entries below this threshold and stale are candidates for compression.
     */
    private val promotionSalienceThreshold: Float = 0.6f,
    /**
     * How old (in ms) an entry must be before it's considered stale
     * for compression.
     */
    private val staleThresholdMs: Long = 3_600_000L
) {
    /**
     * Run one consolidation pass. Scans all unconsolidated episodic entries:
     *  - High-salience entries are promoted to the graph store
     *  - Low-salience stale entries are compressed (marked consolidated,
     *    text replaced with a shorter summary)
     *
     * This runs entirely off the hot path — never called from the
     * conversational turn-processing path.
     */
    fun consolidate(now: Long = System.currentTimeMillis()): ConsolidationResult {
        var promoted = 0
        var compressed = 0

        for (entry in episodicStore) {
            if (entry.consolidated) continue

            if (entry.salience >= promotionSalienceThreshold) {
                // Promote durable fact to graph
                graphStore.addFact(
                    subject = "episodic",
                    predicate = "contains",
                    `object` = entry.text,
                    source = "consolidation:${entry.id}"
                )
                entry.consolidated = true
                promoted++
            } else if (now - entry.timestamp > staleThresholdMs) {
                // Compress stale entry (archive, not delete)
                entry.text = "[archived] ${entry.text.take(50)}..."
                entry.consolidated = true
                compressed++
            }
        }

        return ConsolidationResult(
            promoted = promoted,
            compressed = compressed,
            scanned = episodicStore.size
        )
    }

    /**
     * Check if consolidation would actually do work (unprocessed entries exist).
     */
    fun hasWorkPending(): Boolean = episodicStore.any { !it.consolidated }
}
