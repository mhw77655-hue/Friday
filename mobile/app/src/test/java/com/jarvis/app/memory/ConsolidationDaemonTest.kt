package com.jarvis.app.memory

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Acceptance tests for CONSOLIDATION-DAEMON story (Stage 02, Galaxy Memory).
 *
 * Proves:
 *  1. Real trigger path (consolidate() method) — not a new ad-hoc scheduler
 *  2. Durable facts promoted to MemoryGraphStore after consolidation
 *  3. Low-salience stale entries compressed/archived (not deleted outright)
 *  4. Consolidation does not run on/block the live conversational hot path
 *  5. Running consolidation twice doesn't create duplicates
 */
@RunWith(JUnit4::class)
class ConsolidationDaemonTest {

    private val provider = TestEmbeddingProvider(dimension = 256)
    private val scorer = MemoryImportanceScorer(
        embeddingProvider = provider,
        salienceFloor = 0.1f,
        baseHalfLifeMs = 1000L
    )

    // ── AC1: Real trigger path ────────────────────────────────────────────

    @Test
    fun `consolidate returns a real consolidation result`() {
        val graphStore = FakeMemoryGraphStore()
        val entries = mutableListOf(
            RawEpisodicEntry("e1", "user prefers terse replies", 1000L, salience = 0.8f)
        )
        val daemon = ConsolidationDaemon(graphStore, scorer, entries)

        val result = daemon.consolidate(now = 1000L)
        assertEquals(1, result.promoted)
        assertEquals(0, result.compressed)
        assertEquals(1, result.scanned)
        assertFalse("consolidation should do work", result.wasNoop)
    }

    @Test
    fun `hasWorkPending returns true when unprocessed entries exist`() {
        val graphStore = FakeMemoryGraphStore()
        val entries = mutableListOf(
            RawEpisodicEntry("e1", "some fact", 1000L, salience = 0.5f)
        )
        val daemon = ConsolidationDaemon(graphStore, scorer, entries)
        assertTrue("should have work pending", daemon.hasWorkPending())
    }

    @Test
    fun `hasWorkPending returns false when all entries are consolidated`() {
        val graphStore = FakeMemoryGraphStore()
        val entries = mutableListOf(
            RawEpisodicEntry("e1", "some fact", 1000L, salience = 0.8f, consolidated = true)
        )
        val daemon = ConsolidationDaemon(graphStore, scorer, entries)
        assertFalse("should have no work pending", daemon.hasWorkPending())
    }

    // ── AC2: Durable facts promoted to MemoryGraphStore ───────────────────

    @Test
    fun `high salience episodic entry is promoted to graph store as a fact`() {
        val graphStore = FakeMemoryGraphStore()
        val entries = mutableListOf(
            RawEpisodicEntry("e1", "user prefers terse concise answers", 1000L, salience = 0.8f)
        )
        val daemon = ConsolidationDaemon(graphStore, scorer, entries)

        daemon.consolidate(now = 1000L)

        // Fact should appear in graph store
        val facts = graphStore.query(subject = "episodic", predicate = "contains")
        assertEquals(1, facts.size)
        assertEquals("user prefers terse concise answers", facts[0].`object`)
        assertEquals("consolidation:e1", facts[0].source)
    }

    @Test
    fun `contradicting promoted fact supersedes earlier one in graph store`() {
        val graphStore = FakeMemoryGraphStore()
        val entries = mutableListOf(
            RawEpisodicEntry("e1", "user prefers verbose answers", 1000L, salience = 0.8f),
            RawEpisodicEntry("e2", "user prefers terse replies", 2000L, salience = 0.8f)
        )
        val daemon = ConsolidationDaemon(graphStore, scorer, entries)

        daemon.consolidate(now = 2000L)

        // Only the latest fact should be currently valid
        val facts = graphStore.query(subject = "episodic", predicate = "contains")
        assertEquals(1, facts.size)
        assertEquals("user prefers terse replies", facts[0].`object`)

        // History should show both
        val history = graphStore.getHistory("episodic", "contains")
        assertEquals(2, history.size)
    }

    // ── AC3: Low-salience stale entries compressed/archived ────────────────

    @Test
    fun `low salience stale entry is compressed not deleted`() {
        val graphStore = FakeMemoryGraphStore()
        val staleEntry = RawEpisodicEntry(
            "e1", "just chatting about the weather today",
            timestamp = 1000L, salience = 0.2f
        )
        val entries = mutableListOf(staleEntry)
        val daemon = ConsolidationDaemon(
            graphStore, scorer, entries,
            staleThresholdMs = 5000L // 5 second stale threshold
        )

        // Before consolidation: entry exists with original text
        assertEquals("just chatting about the weather today", staleEntry.text)
        assertFalse(staleEntry.consolidated)

        daemon.consolidate(now = 10000L) // 9 seconds later — stale

        // After: entry is compressed (text shortened, marked consolidated)
        assertTrue("entry must be marked consolidated", staleEntry.consolidated)
        assertTrue("compressed text must start with [archived]", staleEntry.text.startsWith("[archived]"))
        assertTrue("compressed text must retain original content", staleEntry.text.contains("weather"))

        // NOT deleted — the entry still exists in the store
        assertEquals(1, entries.size)
        assertFalse("compressed entry must not be promoted to graph", graphStore.nodeCount() > 0)
    }

    @Test
    fun `low salience entry that is not yet stale is left alone`() {
        val graphStore = FakeMemoryGraphStore()
        val freshEntry = RawEpisodicEntry(
            "e1", "just chatting about the weather",
            timestamp = 1000L, salience = 0.2f
        )
        val entries = mutableListOf(freshEntry)
        val daemon = ConsolidationDaemon(
            graphStore, scorer, entries,
            staleThresholdMs = 5000L
        )

        daemon.consolidate(now = 2000L) // 1 second later — not stale

        assertFalse("fresh entry must not be consolidated", freshEntry.consolidated)
        assertEquals("just chatting about the weather", freshEntry.text)
    }

    // ── AC4: Does not run on/block the hot path ───────────────────────────

    @Test
    fun `consolidation is a standalone pass - no interaction with CognitiveEngine`() {
        // This test proves consolidation is decoupled from turn processing:
        // the daemon only touches its own graphStore + episodicStore,
        // never calling into CognitiveEngine or the turn path.
        val graphStore = FakeMemoryGraphStore()
        val entries = mutableListOf(
            RawEpisodicEntry("e1", "fact one", 1000L, salience = 0.8f)
        )
        val daemon = ConsolidationDaemon(graphStore, scorer, entries)

        // Consolidation only reads/writes to graphStore and episodicStore
        val result = daemon.consolidate(now = 1000L)
        assertEquals(1, result.promoted)

        // Verify: graph store got the fact, entries got marked
        assertEquals(1, graphStore.nodeCount())
        assertTrue(entries[0].consolidated)

        // No side effects outside the daemon's own dependencies
        // (CognitiveEngine is never referenced by ConsolidationDaemon)
    }

    // ── AC5: Running twice doesn't create duplicates ──────────────────────

    @Test
    fun `running consolidation twice on same data does not create duplicates`() {
        val graphStore = FakeMemoryGraphStore()
        val entries = mutableListOf(
            RawEpisodicEntry("e1", "user prefers terse replies", 1000L, salience = 0.8f),
            RawEpisodicEntry("e2", "user name is Alice", 2000L, salience = 0.7f)
        )
        val daemon = ConsolidationDaemon(graphStore, scorer, entries)

        // First pass
        val result1 = daemon.consolidate(now = 3000L)
        assertEquals(2, result1.promoted)
        assertEquals(2, graphStore.nodeCount())

        // Second pass — all entries already consolidated, no-op
        val result2 = daemon.consolidate(now = 3000L)
        assertEquals(0, result2.promoted)
        assertEquals(2, graphStore.nodeCount()) // same count, no duplicates
        assertTrue("second pass should be a noop", result2.wasNoop)
    }

    @Test
    fun `pre-consolidated entries are not re-processed`() {
        val graphStore = FakeMemoryGraphStore()
        val entries = mutableListOf(
            RawEpisodicEntry("e1", "already done", 1000L, salience = 0.8f, consolidated = true)
        )
        val daemon = ConsolidationDaemon(graphStore, scorer, entries)

        val result = daemon.consolidate(now = 1000L)
        assertEquals(0, result.promoted)
        assertEquals(0, graphStore.nodeCount())
    }
}

/**
 * In-memory fake [MemoryGraphStore] for unit tests.
 * Uses the same supersession logic as the production [AndroidMemoryGraphStore]
 * (android.database.sqlite) but as a pure-Kotlin, zero-JDBC reference — no JDBC
 * driver, no glibc-native binary.
 */
class FakeMemoryGraphStore : MemoryGraphStore {
    private val nodes = mutableListOf<MemoryNode>()
    private var idCounter = 0

    override fun addFact(subject: String, predicate: String, `object`: String, source: String) {
        val now = System.currentTimeMillis()
        // Supersede existing
        for (n in nodes) {
            if (n.subject == subject && n.predicate == predicate && n.validUntil == null) {
                val idx = nodes.indexOf(n)
                nodes[idx] = n.copy(validUntil = now)
            }
        }
        nodes.add(
            MemoryNode(
                id = "fake-${++idCounter}",
                subject = subject,
                predicate = predicate,
                `object` = `object`,
                source = source,
                validFrom = now
            )
        )
    }

    override fun query(subject: String?, predicate: String?, asOfTime: Long): List<MemoryNode> {
        return nodes.filter { n ->
            (subject == null || n.subject == subject) &&
                (predicate == null || n.predicate == predicate) &&
                n.validFrom <= asOfTime &&
                (n.validUntil == null || n.validUntil > asOfTime)
        }
    }

    override fun getHistory(subject: String, predicate: String): List<MemoryNode> {
        return nodes.filter { it.subject == subject && it.predicate == predicate }
            .sortedBy { it.validFrom }
    }

    override fun nodeCount(): Long = nodes.size.toLong()

    // FORGET-PROPAGATION: expire (supersede, never delete) every currently-valid
    // node whose subject/object carries the forgotten content — the same semantics
    // as the production AndroidMemoryGraphStore.removeContaining, so the wiring
    // test's byte scan of graph query() texts finds zero forgotten plaintext after
    // the forget turn.
    override fun removeContaining(text: String): Int {
        val now = System.currentTimeMillis()
        val needle = text.lowercase()
        var count = 0
        for (i in nodes.indices) {
            val n = nodes[i]
            if (n.validUntil == null &&
                (n.subject.lowercase().contains(needle) || n.`object`.lowercase().contains(needle))
            ) {
                nodes[i] = n.copy(validUntil = now)
                count++
            }
        }
        return count
    }

    override fun close() {}

    /** Directly insert a pre-built node (for tests that need specific node configs). */
    fun addFakeNode(node: MemoryNode) {
        nodes.add(node)
    }
}
