package com.jarvis.app.memory

/**
 * Bi-temporal knowledge graph for Galaxy Memory.
 *
 * Each node represents an entity or fact; each edge represents a relationship.
 * Every node and edge carries a real validity window ([validFrom]/[validUntil]):
 * - [validFrom] is set at creation (typically "now")
 * - [validUntil] is null (open-ended) until the fact is superseded
 * - When new information contradicts an existing fact (same subject+predicate),
 *   the old node is marked superseded ([validUntil] set) rather than deleted
 *   or overwritten — matching the project-wide principle that entities are
 *   never hard-deleted, only decayed/superseded.
 *
 * This is the interface. The PRODUCTION implementation is
 * [AndroidMemoryGraphStore] (android.database.sqlite.SQLiteDatabase — Android's
 * native SQLite, no JDBC, no glibc-native binaries); the JVM unit tests exercise
 * the same contract via a pure-Kotlin, zero-JDBC reference store
 * (FakeMemoryGraphStore in src/test).
 */
interface MemoryGraphStore {
    /**
     * Add a fact to the graph. If a currently-valid node with the same
     * [subject] and [predicate] already exists, it is superseded (its
     * validUntil is set to now) before the new node is inserted.
     */
    fun addFact(subject: String, predicate: String, `object`: String, source: String = "")

    /**
     * Query currently-valid facts matching [subject] (if non-null) and/or
     * [predicate] (if non-null). Returns only facts valid at [asOfTime]
     * (default: now).
     */
    fun query(
        subject: String? = null,
        predicate: String? = null,
        asOfTime: Long = System.currentTimeMillis()
    ): List<MemoryNode>

    /**
     * Return the full supersession chain for facts matching [subject] and
     * [predicate], ordered by validFrom ascending (oldest first).
     * Includes both currently-valid and superseded entries.
     */
    fun getHistory(subject: String, predicate: String): List<MemoryNode>

    /** Count all rows in the nodes table (for row-count-never-decreases assertions). */
    fun nodeCount(): Long

    /** Close/release resources. */
    fun close()
}

/**
 * A single node in the memory graph — an entity or fact with a validity window
 * and importance-scorer fields.
 *
 * [salience], [accessCount], and [embedding] are used by
 * [MemoryImportanceScorer] and have sensible defaults so graph-only code
 * doesn't need to set them explicitly.
 *
 * The six *signal* fields ([relevance], [importance], [uncertainty],
 * [novelty], [consent], [cost]) are the SIGNAL-SPLIT record: each is stored as
 * its OWN field and is never pre-collapsed into a single score. They default
 * to null so legacy constructions stay byte-identical; they are filled by
 * [com.jarvis.app.memory.SignalSplitScorer] via `withSignals` and must not be
 * blended by any consumer (a combined score is a later, baseline-gated story).
 */
data class MemoryNode(
    val id: String,
    val subject: String,
    val predicate: String,
    val `object`: String,
    val source: String,
    val validFrom: Long,
    val validUntil: Long? = null,
    /** How emotionally/practically significant this memory is [0,1]. */
    val salience: Float = 0.5f,
    /** How many times this memory has been accessed/reinforced. */
    val accessCount: Int = 0,
    /** Pre-computed embedding of the object text for semantic relevance scoring. */
    val embedding: FloatArray? = null,
    /** Signal: semantic match to the current conversation context [0,1]. */
    val relevance: Float? = null,
    /** Signal: inherent worth of this fact to the user, independent of surprise [0,1]. */
    val importance: Float? = null,
    /** Signal: how uncertain/hedged the stated fact is [0,1]. */
    val uncertainty: Float? = null,
    /** Signal: how new/surprising the fact is; decays with repetition [0,1]. */
    val novelty: Float? = null,
    /** Signal: whether the user explicitly stated/directed this vs. it being inferred [0,1]. */
    val consent: Float? = null,
    /** Signal: cost-to-retrieve/execute (no consumer computes it yet; 0 default) [0,1]. */
    val cost: Float? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryNode) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}
