package com.jarvis.app.memory.provenance

import com.jarvis.app.memory.ConsolidationDaemon
import com.jarvis.app.memory.FakeMemoryGraphStore
import com.jarvis.app.memory.KotlinVectorStore
import com.jarvis.app.memory.MemoryImportanceScorer
import com.jarvis.app.memory.RawEpisodicEntry
import com.jarvis.app.memory.TestEmbeddingProvider
import com.jarvis.app.trace.JsonlTurnTraceStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FORGET-PROPAGATION — core semantics of the forgetting subsystem (the
 * PRODUCTION-path half lives in [com.jarvis.app.termux.ForgetProductionWiringTest]).
 *
 * These tests pin down, on the real classes, the behaviors the production wiring
 * test then proves through the real conversation path:
 *  - the tombstone registry writes only fingerprints and blocks re-learning, and
 *    an explicit remember clears it (AC4);
 *  - forget() propagates through the ledger exactly like its creation points
 *    recorded: a two-source SUMMARY is re-derived to its remaining source
 *    (AC3), a single-source SUMMARY is dropped, the index row is removed from
 *    the vector store, the trace text is redacted but the record survives
 *    (AC6), and the pending consolidation queue is purged by content;
 *  - the consolidation daemon's tombstone guard suppresses a re-incarnated
 *    entry (never re-created — AC4);
 *  - the AC5 negative control: disabled propagation forgets nothing.
 */
class ForgetPropagationTest {

    private val files = mutableListOf<File>()

    @After
    fun tearDown() {
        files.forEach { it.delete() }
    }

    private fun newFile(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "forget-$name-${System.nanoTime()}.jsonl")
            .also { files.add(it) }

    private fun newTombstoneStore(): JsonlTombstoneStore = JsonlTombstoneStore(newFile("tombstones"))

    private fun newLedger(): JsonlProvenanceLedger = JsonlProvenanceLedger(newFile("ledger"))

    private fun newTraceStore(): JsonlTurnTraceStore = JsonlTurnTraceStore(newFile("trace"))

    // ── Tombstone registry ───────────────────────────────────────────────────

    @Test
    fun `tombstone registry persists hashes only and counts them`() {
        val f = newFile("tombstones-raw")
        val store = JsonlTombstoneStore(f)
        assertEquals(0L, store.count())
        store.tombstone("M1", "Sara's locker code is 4471")
        assertEquals(1L, store.count())

        // The FILE must never carry the plaintext — only SHA-256 fingerprints
        // — so including the tombstone file in a byte scan is always safe.
        val raw = f.readText()
        assertTrue("no plaintext secret in the tombstone file", !raw.contains("4471"))
        assertTrue("content is never stored raw", !raw.contains("Sara"))

        // A simulated restart over the same file reproduces the answer.
        val reloaded = JsonlTombstoneStore(f)
        assertTrue(reloaded.isTombstoned("M1"))
        assertTrue(reloaded.matchesAny("by the way, Sara's locker code is 4471"))
    }

    @Test
    fun `matchesAny blocks incidental re-learning and remember clears it`() {
        val store = JsonlTombstoneStore(newFile("tombstones"))
        store.tombstone("M1", "Sara's locker code is 4471")

        // Any later text carrying a tombstoned token is blocked, even a passing
        // mention ("the code is 4471"), but unrelated text passes freely.
        assertTrue(store.matchesAny("can you remind me, the code is 4471"))
        assertTrue(store.matchesAny("sara's locker code"))
        assertFalse(store.matchesAny("Ahmed prefers green tea daily"))

        // Explicit remember of the SAME content clears the tombstone (AC4) —
        // the re-learning guard is genuinely lifted.
        assertEquals(1, store.remember("sara's locker code is 4471"))
        assertEquals(0L, store.count())
        assertFalse(store.isTombstoned("M1"))
        assertFalse(store.matchesAny("the code is 4471"))

        // AC4 family-lift: a deliberate remember yields the WHOLE token family —
        // a second tombstone sharing the value "4471" (here, the same secret in
        // Egyptian Arabic) also clears, while an unrelated tombstone survives.
        store.tombstone("M2", "كود الدولاب بتاع سارة هو 4471")
        store.tombstone("M3", "Ahmed planted roses in spring")
        assertEquals(1, store.remember("may I remember the code is 4471"))
        assertEquals(1L, store.count())
        assertTrue(store.matchesAny("ahmed planted roses in spring"))
        assertFalse(store.matchesAny("كود الدولاب بتاع سارة هو 4471"))
        assertFalse(store.matchesAny("the code is 4471"))
    }

    // ── Ledger redact / redactSource ─────────────────────────────────────────

    @Test
    fun `redactSource removes one source and redact drops the summary`() {
        val ledger = newLedger()
        ledger.record("consolidation-summary-1", ProvenanceKind.SUMMARY, listOf("M1", "M2"))

        // Two-source case: re-derive to the remaining source (AC3).
        assertEquals(1, ledger.redactSource("consolidation-summary-1", "M1"))
        assertEquals(setOf("M2"), ledger.sourcesOf("consolidation-summary-1"))

        // Only-source case: the summary artifact itself is dropped.
        assertEquals(1, ledger.redactSource("consolidation-summary-1", "M2"))
        assertEquals(0, ledger.redact("consolidation-summary-1"))
        assertTrue(ledger.sourcesOf("consolidation-summary-1").isEmpty())
        assertEquals(0L, ledger.count())
    }

    // ── forget() propagates through every derived artifact family ────────────

    @Test
    fun `forget re-derives the summary removes the index redacts the trace and purges the queue`() {
        val tombstone = newTombstoneStore()
        val ledger = newLedger()
        val traceStore = newTraceStore()

        val provider = TestEmbeddingProvider(dimension = 256)
        val scorer = MemoryImportanceScorer(
            embeddingProvider = provider,
            salienceFloor = 0.1f,
            baseHalfLifeMs = 1000L
        )
        val graph = FakeMemoryGraphStore()
        val vector = KotlinVectorStore(dimension = 256, provenanceLedger = ledger)
        val entries = mutableListOf(
            RawEpisodicEntry("M1", "Sara's locker code is 4471", 1000L, salience = 0.8f),
            RawEpisodicEntry("M2", "أحمد زرع ورد في الربيع", 1000L, salience = 0.8f)
        )
        val daemon = ConsolidationDaemon(
            graphStore = graph,
            scorer = scorer,
            episodicStore = entries,
            provenanceLedger = ledger,
            tombstoneStore = tombstone
        )
        val forgetter = MemoryForgetter(
            tombstoneStore = tombstone,
            ledger = ledger,
            traceStore = traceStore,
            graphStore = graph,
            vectorStore = vector,
            purgeables = listOf(daemon)
        )

        // Creation points first: SUMMARY via the real daemon, INDEX_ENTRY via
        // the real vector store, and a real trace record that carried M1.
        daemon.consolidate(now = 4242L)
        vector.upsert("M1", "Sara's locker code is 4471", provider.embed("Sara's locker code is 4471"))
        vector.upsert("M2", "أحمد زرع ورد في الربيع", provider.embed("أحمد زرع ورد في الربيع"))
        traceStore.append(
            com.jarvis.app.trace.TurnTrace.TurnTraceRecord(
                id = "turn-1-1",
                turnIndex = 1L,
                timestampMs = System.currentTimeMillis(),
                inputText = "Sara's locker code is 4471",
                decision = "DIRECT_REPLY",
                retrievedMemoryIds = listOf("M1"),
                predictions = emptyList(),
                promptSections = emptyList(),
                outputText = null,
                generationPayload = null,
                crossSessionMemories = emptyList(),
                stageTimingsMs = mapOf("embed" to 1L)
            )
        )

        val summaryId = "consolidation-summary-4242"
        assertTrue(ledger.derivedFrom("M1").any { it.kind == ProvenanceKind.SUMMARY })
        assertEquals(setOf("M1", "M2"), ledger.sourcesOf(summaryId))
        assertEquals(2, vector.contents().size)
        assertEquals(1L, traceStore.count())
        assertTrue("both fixtures reached the graph store", graph.nodeCount() >= 2L)

        // ── Forget M1: propagation through every family ─────────────────────────
        val result = forgetter.forget("M1", "Sara's locker code is 4471")
        assertTrue("propagation completed", result.completed)

        // AC3: the two-source summary is re-derived to M2 and no longer lists M1.
        assertEquals(setOf("M2"), ledger.sourcesOf(summaryId))
        assertFalse(
            "sourcesOf no longer names the forgotten id",
            ledger.sourcesOf(summaryId).contains("M1")
        )
        assertTrue("the summary still exists", ledger.sourcesOf(summaryId).isNotEmpty())

        // INDEX_ENTRY: the vector row is gone; M2's row remains.
        assertEquals(listOf("أحمد زرع ورد في الربيع"), vector.contents())
        assertEquals(0, vector.removeContaining("Sara's locker code is 4471"))

        // Graph: the fact nodes are expired (not hard-deleted — nodeCount stable).
        val live = graph.query()
        assertTrue(live.none { it.`object`.contains("4471") })
        assertTrue("row count never decreases", graph.nodeCount() >= 2L)

        // Trace: text redacted, the record survives (AC6).
        assertEquals(1L, traceStore.count())
        assertEquals("", traceStore.readRecords().single().inputText)

        // Queue: the pending entry carrying the content is purged.
        assertTrue(entries.none { it.text.contains("4471") })
        assertEquals(1, entries.size)
    }

    @Test
    fun `only-source forget drops the summary entirely`() {
        val tombstone = newTombstoneStore()
        val ledger = newLedger()
        val provider = TestEmbeddingProvider(dimension = 256)
        val graph = FakeMemoryGraphStore()
        val forgetter = MemoryForgetter(
            tombstoneStore = tombstone,
            ledger = ledger,
            graphStore = graph
        )

        ledger.record("consolidation-summary-9", ProvenanceKind.SUMMARY, listOf("M1"))
        forgetter.forget("M1", "Sara's locker code is 4471")

        assertTrue("only-source summary is dropped", ledger.sourcesOf("consolidation-summary-9").isEmpty())
        assertTrue("no derived SUMMARY survives", ledger.derivedFrom("M1").none { it.kind == ProvenanceKind.SUMMARY })
    }

    // ── AC4: the daemon guard + live write-back guard ────────────────────────

    @Test
    fun `daemon tombstone guard suppresses a re-incarnated entry`() {
        val tombstone = newTombstoneStore()
        tombstone.tombstone("M1", "Sara's locker code is 4471")

        val provider = TestEmbeddingProvider(dimension = 256)
        val scorer = MemoryImportanceScorer(embeddingProvider = provider)
        val graph = FakeMemoryGraphStore()
        val before = graph.nodeCount()
        val entries = mutableListOf(
            RawEpisodicEntry("ghost", "Sara's locker code is 4471", 1000L, salience = 0.8f)
        )
        val daemon = ConsolidationDaemon(graph, scorer, entries, tombstoneStore = tombstone)

        val result = daemon.consolidate(now = 4242L)
        assertEquals("suppressed, not promoted", 0, result.promoted)
        assertTrue("the entry is consumed as consolidated", entries.single().consolidated)
        assertEquals("no new graph node was created", before, graph.nodeCount())
        assertTrue(
            "the tombstoned fact never reached the graph",
            graph.query().none { it.`object`.contains("4471") }
        )
    }

    // ── AC5: propagation disabled forgets nothing ────────────────────────────

    @Test
    fun `disabled propagation suppresses the forget so the content survives`() {
        val tombstone = newTombstoneStore()
        val ledger = newLedger()
        val forgetter = MemoryForgetter(tombstoneStore = tombstone, ledger = ledger)
        forgetter.setPropagationEnabled(false)

        ledger.record("consolidation-summary-3", ProvenanceKind.SUMMARY, listOf("M1"))
        val result = forgetter.forget("M1", "Sara's locker code is 4471")

        assertTrue("negative control: forget was suppressed", result.suppressed)
        assertFalse("completed is false when suppressed", result.completed)
        assertEquals(0, result.derivedArtifactsHandled)
        assertEquals("summary still lists M1", setOf("M1"), ledger.sourcesOf("consolidation-summary-3"))
        assertEquals("no tombstone was written", 0L, tombstone.count())
    }

    // ── Directive parsing ────────────────────────────────────────────────────

    @Test
    fun `forget and remember fragments are parsed for English and Arabic`() {
        assertEquals("Sara's locker code", MemoryForgetter.forgetFragment("forget Sara's locker code"))
        assertEquals("Sara's locker code", MemoryForgetter.forgetFragment("Please forget Sara's locker code"))
        assertEquals("the wifi password", MemoryForgetter.forgetFragment("jarvis please forget about the wifi password"))
        assertEquals("كود الدولاب بتاع سارة", MemoryForgetter.forgetFragment("انسى كود الدولاب بتاع سارة"))
        assertEquals("كود الدولاب بتاع سارة", MemoryForgetter.forgetFragment("انسي كود الدولاب بتاع سارة"))
        assertNull("a plain statement is not a forget directive", MemoryForgetter.forgetFragment("Sara's locker code is 4471"))

        assertEquals("Sara's locker code is 4471", MemoryForgetter.rememberFragment("remember that Sara's locker code is 4471"))
        assertEquals("the wifi password", MemoryForgetter.rememberFragment("please remember the wifi password"))
        assertNull("non-remember text has no fragment", MemoryForgetter.rememberFragment("forget Sara's locker code"))
    }
}