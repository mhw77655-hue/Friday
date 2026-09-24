package com.jarvis.app.memory.provenance

/**
 * PROVENANCE-LEDGER: the kind of derived artifact a ledger entry records.
 *
 * A derived artifact is ANY memory-system product that was built FROM source
 * memories: a consolidation summary, a vector-index entry, a graph edge, a
 * cache entry, a per-turn trace record, or a batch of adapter bytes. The kind
 * tells the consumer which family of artifact the [ProvenanceLedger.derivedId]
 * belongs to.
 */
enum class ProvenanceKind {
    /** A consolidation pass grouped two or more source memories into one summary. */
    SUMMARY,
    /** A source memory was indexed into a vector store. */
    INDEX_ENTRY,
    /** A graph edge was created from source memories. */
    GRAPH_EDGE,
    /** A cache entry was materialized from source memories. */
    CACHE_ENTRY,
    /** A real conversation turn's trace record captured this turn's retrieved memories. */
    TRACE_RECORD,
    /** A batch of adapter input was composed from source memories. */
    ADAPTER_BATCH
}

/** One derived artifact a [ProvenanceLedger] can answer questions about. */
data class DerivedRef(
    val derivedId: String,
    val kind: ProvenanceKind
)

/**
 * PROVENANCE-LEDGER: durable local record of which source memories every
 * derived memory artifact came from.
 *
 * [record] appends one entry naming [sourceIds] as the memories behind
 * [derivedId]. [derivedFrom] reverses that — all derived artifacts (with their
 * kind) that were recorded as deriving from a given source memory — and
 * [sourcesOf] answers which source memories one derived artifact was built
 * from.
 *
 * This exists so FORGET-PROPAGATION can delete/mark the right things when a
 * source memory is corrected or forgotten: every derived artifact records its
 * provenance up front, at the real creation points (consolidation summaries,
 * vector-index entries, turn-trace records), rather than claiming a
 * capability the code does not have.
 */
interface ProvenanceLedger {
    /** Whether [record] currently appends. Reads are always available. */
    val enabled: Boolean

    /** Enable/disable recording. Disabled [record] is a no-op (negative control). */
    fun setEnabled(enabled: Boolean)

    /**
     * Append one provenance entry: [derivedId] (a derived artifact) was built
     * from [sourceIds]. No-op when disabled or not yet wired.
     */
    fun record(derivedId: String, kind: ProvenanceKind, sourceIds: List<String>)

    /**
     * Every derived artifact (derived id + kind) whose provenance names
     * [sourceId], in record order (duplicates collapsed).
     */
    fun derivedFrom(sourceId: String): List<DerivedRef>

    /** The set of source memories one derived artifact was built from. */
    fun sourcesOf(derivedId: String): Set<String>
}