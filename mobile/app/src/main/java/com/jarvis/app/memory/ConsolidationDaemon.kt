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
    /**
     * CORRECTION-CHAIN: how many currently-valid graph nodes this pass raised
     * the ACCESSIBILITY axis of. Reported separately from [promoted]/[compressed]
     * so a pass that only reinforced reachability is still visible.
     */
    val accessibilityReinforced: Int = 0,
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
 *  - **Reinforces reachability only**: CORRECTION-CHAIN — every pass raises the
 *    [MemoryNode.accessibility] of each currently-valid node by a computed step
 *    and writes back nothing else, so consolidation can never change a stored
 *    signal (in particular never the stored uncertainty).
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
    private val staleThresholdMs: Long = 3_600_000L,

    /**
     * PROVENANCE-LEDGER: optional durable local record of which source
     * memories every derived artifact came from. When wired, each pass that
     * promotes one or more entries appends ONE SUMMARY entry naming every
     * promoted entry id — the pass's consolidation summary over its sources.
     * Null keeps the pre-provenance path byte-for-byte.
     */
    private val provenanceLedger: com.jarvis.app.memory.provenance.ProvenanceLedger? = null,

    /**
     * FORGET-PROPAGATION: durable tombstone registry consulted before every
     * promotion. An entry whose text carries a tombstoned content token is
     * SUPPRESSED (marked consolidated, never promoted) so a forgotten fact is
     * never re-created by a later automatic consolidation run (AC4). Null keeps
     * the pre-forget promotion behavior byte-for-byte.
     */
    private val tombstoneStore: com.jarvis.app.memory.provenance.TombstoneStore? = null
) : com.jarvis.app.memory.provenance.ForgetPurgeable {
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
        val promotedIds = mutableListOf<String>()

        for (entry in episodicStore) {
            if (entry.consolidated) continue

            if (entry.salience >= promotionSalienceThreshold) {
                // FORGET-PROPAGATION: a tombstoned fact must not be re-created
                // by an automatic consolidation pass. The guard CONSUMES the
                // entry (suppressed, not promoted) — the forgotten content can
                // no longer reach the graph through this queue.
                if (tombstoneStore?.matchesAny(entry.text) == true) {
                    entry.consolidated = true
                    continue
                }
                // Promote durable fact to graph
                graphStore.addFact(
                    subject = "episodic",
                    predicate = "contains",
                    `object` = entry.text,
                    source = "consolidation:${entry.id}"
                )
                entry.consolidated = true
                promotedIds.add(entry.id)
                promoted++
            } else if (now - entry.timestamp > staleThresholdMs) {
                // Compress stale entry (archive, not delete)
                entry.text = "[archived] ${entry.text.take(50)}..."
                entry.consolidated = true
                compressed++
            }
        }

        // PROVENANCE-LEDGER: one per-pass SUMMARY per consolidation that
        // promoted sources — the durable record FORGET-PROPAGATION later
        // consults to find every derived artifact of a source memory.
        if (promotedIds.isNotEmpty()) {
            provenanceLedger?.record(
                derivedId = "consolidation-summary-$now",
                kind = com.jarvis.app.memory.provenance.ProvenanceKind.SUMMARY,
                sourceIds = promotedIds
            )
        }

        // CORRECTION-CHAIN: consolidation may raise ACCESSIBILITY and nothing
        // else. Each currently-valid node is copied with a computed
        // accessibility step (MemoryAccessibility.raise) and written back through
        // the store's dedicated accessibility seam — the copy touches no signal,
        // so a stored uncertainty (or any other of the six) cannot be inflated by
        // consolidating more often. Reading through query() also means a
        // superseded assertion is never reinforced: only current facts are.
        var reinforced = 0
        for (node in graphStore.query()) {
            val raised = node.withRaisedAccessibility()
            val raisedValue = raised.accessibility ?: continue
            if (raisedValue != node.accessibility && graphStore.setAccessibility(node.id, raisedValue)) {
                reinforced++
            }
        }

        return ConsolidationResult(
            promoted = promoted,
            compressed = compressed,
            scanned = episodicStore.size,
            accessibilityReinforced = reinforced
        )
    }

    /**
     * Check if consolidation would actually do work (unprocessed entries exist).
     */
    fun hasWorkPending(): Boolean = episodicStore.any { !it.consolidated }

    // FORGET-PROPAGATION: the queue byte-scan surface of a forget — remove every
    // pending entry whose text carries the forgotten content, so the daemon's own
    // episodic queue holds zero plaintext after the turn (AC1/AC2). The tombstone
    // guard above covers the promoted-after-forget re-creation path; this covers
    // the still-pending path.
    override fun purgeContaining(needle: String): Int {
        val needleLc = needle.lowercase()
        val before = episodicStore.size
        episodicStore.removeAll { it.text.lowercase().contains(needleLc) }
        return before - episodicStore.size
    }
}
