package com.jarvis.app.memory.provenance

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * PROVENANCE-LEDGER — core ledger semantics (same package, per the story).
 *
 * Proves the ledger itself is a real append-only local record:
 *  - [record] appends durable lines; [derivedFrom]/[sourcesOf] read them back
 *  - record order is preserved; duplicates collapse
 *  - AC3: a NEW [JsonlProvenanceLedger] over the SAME file (simulated restart)
 *    reproduces identical answers
 *  - disabling through the real path ([JsonlProvenanceLedger.setEnabled]) makes
 *    [record] a no-op; re-enabling resumes real recording
 */
class ProvenanceLedgerTest {

    private val files = mutableListOf<File>()

    @After
    fun tearDown() {
        files.forEach { it.delete() }
    }

    private fun newLedger(initiallyEnabled: Boolean = true): JsonlProvenanceLedger {
        val f = File(
            System.getProperty("java.io.tmpdir"),
            "provenance-ledger-${System.nanoTime()}.jsonl"
        )
        files.add(f)
        return JsonlProvenanceLedger(f, initiallyEnabled = initiallyEnabled)
    }

    @Test
    fun `record appends and derivedFrom returns distinct refs in record order`() {
        val ledger = newLedger()

        ledger.record("index-m1", ProvenanceKind.INDEX_ENTRY, listOf("m1"))
        ledger.record("consolidation-summary-1", ProvenanceKind.SUMMARY, listOf("m1", "m2"))
        ledger.record("trace-t0", ProvenanceKind.TRACE_RECORD, listOf("m1"))
        ledger.record("consolidation-summary-1", ProvenanceKind.SUMMARY, listOf("m1", "m2"))

        assertEquals(4L, ledger.count())

        val derived = ledger.derivedFrom("m1")
        assertEquals(
            "each distinct derived artifact appears once",
            setOf("index-m1", "consolidation-summary-1", "trace-t0"),
            derived.map { it.derivedId }.toSet()
        )
        assertEquals("record order is preserved across the read", "index-m1", derived.first().derivedId)
        assertEquals(ProvenanceKind.INDEX_ENTRY, derived.first().kind)
        assertEquals(ProvenanceKind.TRACE_RECORD, derived.last().kind)
    }

    @Test
    fun `derivedFrom only names artifacts that actually derive from the source`() {
        val ledger = newLedger()
        ledger.record("index-m2", ProvenanceKind.INDEX_ENTRY, listOf("m2"))

        assertTrue("an untouched source has no derived refs", ledger.derivedFrom("m1").isEmpty())
        assertEquals(setOf("index-m2"), ledger.derivedFrom("m2").map { it.derivedId }.toSet())
    }

    @Test
    fun `sourcesOf returns exactly the source memories behind one derived artifact`() {
        val ledger = newLedger()
        ledger.record("consolidation-summary-1", ProvenanceKind.SUMMARY, listOf("m1", "m2"))
        ledger.record("consolidation-summary-2", ProvenanceKind.SUMMARY, listOf("m3"))

        assertEquals(setOf("m1", "m2"), ledger.sourcesOf("consolidation-summary-1"))
        assertEquals(setOf("m3"), ledger.sourcesOf("consolidation-summary-2"))
        assertTrue(ledger.sourcesOf("nonexistent").isEmpty())
    }

    @Test
    fun `reload from its file reproduces identical answers over a simulated restart`() {
        val ledger = newLedger()
        ledger.record("index-m1", ProvenanceKind.INDEX_ENTRY, listOf("m1"))
        ledger.record("consolidation-summary-1", ProvenanceKind.SUMMARY, listOf("m1", "m2"))
        ledger.record("trace-t0", ProvenanceKind.TRACE_RECORD, listOf("m1", "m2"))

        val derivedBefore = ledger.derivedFrom("m1")
        val sourcesBefore = ledger.sourcesOf("consolidation-summary-1")

        // AC3: simulated restart — a NEW instance over the SAME file.
        val reloaded = JsonlProvenanceLedger(ledger.ledgerFile)
        assertEquals("AC1-style answer survives reload", derivedBefore, reloaded.derivedFrom("m1"))
        assertEquals("sourcesOf answer survives reload", sourcesBefore, reloaded.sourcesOf("consolidation-summary-1"))
        assertEquals(ledger.count(), reloaded.count())
    }

    @Test
    fun `disabled ledger records nothing through the real path and re-enabling resumes`() {
        val ledger = newLedger()
        ledger.setEnabled(false)

        ledger.record("index-m1", ProvenanceKind.INDEX_ENTRY, listOf("m1"))
        assertEquals("no provenance lines while disabled", 0L, ledger.count())
        assertTrue("no derived artifacts while disabled", ledger.derivedFrom("m1").isEmpty())

        ledger.setEnabled(true)
        ledger.record("index-m1", ProvenanceKind.INDEX_ENTRY, listOf("m1"))
        assertEquals("re-enabled ledger resumes real recording", 1L, ledger.count())
        assertEquals(setOf("index-m1"), ledger.derivedFrom("m1").map { it.derivedId }.toSet())
    }
}