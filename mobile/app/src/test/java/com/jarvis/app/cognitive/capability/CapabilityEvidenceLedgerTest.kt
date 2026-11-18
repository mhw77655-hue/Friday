package com.jarvis.app.cognitive.capability

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityEvidenceLedgerTest {
    private fun tempDir(): File = File.createTempFile("friday-capability-evidence-", ".dir").apply {
        delete()
        mkdirs()
    }

    private fun evidence(
        outcome: EvidenceOutcome,
        verified: Boolean = true,
        at: Long = 1L,
        operation: String = "read_contacts"
    ) = CapabilityEvidence(
        capabilityId = "phone.contacts.read",
        operation = operation,
        outcome = outcome,
        observedAtMs = at,
        source = "android-runtime",
        verified = verified,
        eventId = "event-$at-${outcome.name}-$operation"
    )

    @Test
    fun `evidence survives recreation of the ledger`() {
        val directory = tempDir()
        CapabilityEvidenceLedger(directory).record(evidence(EvidenceOutcome.SUCCEEDED))
        val restored = CapabilityEvidenceLedger(directory).load() as CapabilityEvidenceLoad.Loaded
        assertEquals(1, restored.evidence.size)
        assertEquals(EvidenceOutcome.SUCCEEDED, restored.evidence.single().outcome)
        directory.deleteRecursively()
    }

    @Test
    fun `one success is not enough to mark a capability reusable`() {
        val directory = tempDir()
        val ledger = CapabilityEvidenceLedger(directory)
        ledger.record(evidence(EvidenceOutcome.SUCCEEDED))
        assertFalse(ledger.knowledge().single().reusable)
        directory.deleteRecursively()
    }

    @Test
    fun `only repeated verified successes establish reusable evidence`() {
        val directory = tempDir()
        val ledger = CapabilityEvidenceLedger(directory)
        ledger.record(evidence(EvidenceOutcome.SUCCEEDED, verified = false, at = 1))
        ledger.record(evidence(EvidenceOutcome.SUCCEEDED, verified = true, at = 2))
        assertFalse(ledger.knowledge().single().reusable)
        ledger.record(evidence(EvidenceOutcome.SUCCEEDED, verified = true, at = 3))
        assertTrue(ledger.knowledge().single().reusable)
        directory.deleteRecursively()
    }

    @Test
    fun `a failure prevents reuse until evidence is reconsidered`() {
        val directory = tempDir()
        val ledger = CapabilityEvidenceLedger(directory)
        ledger.record(evidence(EvidenceOutcome.SUCCEEDED, at = 1))
        ledger.record(evidence(EvidenceOutcome.SUCCEEDED, at = 2))
        assertTrue(ledger.knowledge().single().reusable)
        ledger.record(evidence(EvidenceOutcome.FAILED, at = 3))
        assertFalse(ledger.knowledge().single().reusable)
        ledger.record(evidence(EvidenceOutcome.SUCCEEDED, at = 4))
        assertFalse(ledger.knowledge().single().reusable)
        ledger.record(evidence(EvidenceOutcome.SUCCEEDED, at = 5))
        assertTrue(ledger.knowledge().single().reusable)
        directory.deleteRecursively()
    }

    @Test
    fun `refused blocked and skipped outcomes are not counted as success or failure`() {
        val directory = tempDir()
        val ledger = CapabilityEvidenceLedger(directory)
        ledger.record(evidence(EvidenceOutcome.REFUSED))
        ledger.record(evidence(EvidenceOutcome.BLOCKED))
        ledger.record(evidence(EvidenceOutcome.SKIPPED))
        val knowledge = ledger.knowledge().single()
        assertEquals(3, knowledge.observations)
        assertEquals(0, knowledge.verifiedSuccesses)
        assertEquals(0, knowledge.failures)
        assertFalse(knowledge.reusable)
        directory.deleteRecursively()
    }

    @Test
    fun `corrupt evidence is preserved and never overwritten`() {
        val directory = tempDir()
        val file = File(directory, CapabilityEvidenceLedger.FILE_NAME)
        file.parentFile?.mkdirs()
        file.writeText("{broken")
        assertTrue(CapabilityEvidenceLedger(directory).load() is CapabilityEvidenceLoad.Corrupt)
        try {
            CapabilityEvidenceLedger(directory).record(evidence(EvidenceOutcome.SUCCEEDED))
            throw AssertionError("Expected corrupt evidence to be preserved")
        } catch (_: IllegalStateException) {
            assertEquals("{broken", file.readText())
        }
        directory.deleteRecursively()
    }
    @Test
    fun `replaying the same evidence event cannot inflate confidence`() {
        val directory = tempDir()
        val ledger = CapabilityEvidenceLedger(directory)
        val sameEvent = evidence(EvidenceOutcome.SUCCEEDED, at = 10)
        ledger.record(sameEvent)
        ledger.record(sameEvent)
        val knowledge = ledger.knowledge().single()
        assertEquals(1, knowledge.observations)
        assertEquals(1, knowledge.verifiedSuccesses)
        assertFalse(knowledge.reusable)
        directory.deleteRecursively()
    }

    @Test
    fun `confidence is isolated per operation even within one capability`() {
        val directory = tempDir()
        val ledger = CapabilityEvidenceLedger(directory)
        ledger.record(evidence(EvidenceOutcome.SUCCEEDED, at = 1, operation = "read_contacts"))
        ledger.record(evidence(EvidenceOutcome.SUCCEEDED, at = 2, operation = "read_contacts"))
        ledger.record(evidence(EvidenceOutcome.SUCCEEDED, at = 1, operation = "delete_contacts"))
        val knowledge = ledger.knowledge().associateBy { it.operation }
        assertTrue(knowledge.getValue("read_contacts").reusable)
        assertFalse(knowledge.getValue("delete_contacts").reusable)
        directory.deleteRecursively()
    }

    @Test
    fun `separate ledger instances serialize writes to the same file`() {
        val directory = tempDir()
        val start = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(12)
        repeat(12) { index ->
            Thread {
                try {
                    start.await()
                    CapabilityEvidenceLedger(directory).record(
                        evidence(EvidenceOutcome.SUCCEEDED, at = index.toLong() + 1)
                    )
                } finally {
                    done.countDown()
                }
            }.start()
        }
        start.countDown()
        assertTrue("all writers should finish", done.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val loaded = CapabilityEvidenceLedger(directory).load() as CapabilityEvidenceLoad.Loaded
        assertEquals(12, loaded.evidence.size)
        directory.deleteRecursively()
    }

    @Test
    fun `schema one evidence migrates with deterministic event identity`() {
        val directory = tempDir()
        val file = File(directory, CapabilityEvidenceLedger.FILE_NAME)
        file.parentFile?.mkdirs()
        file.writeText(
            """{"schema_version":1,"evidence":[{"capability_id":"phone.contacts.read","operation":"read_contacts","outcome":"SUCCEEDED","observed_at_ms":42,"source":"legacy","verified":true,"detail":""}]}"""
        )

        val ledger = CapabilityEvidenceLedger(directory)
        val first = ledger.load() as CapabilityEvidenceLoad.Loaded
        val second = ledger.load() as CapabilityEvidenceLoad.Loaded
        assertEquals(first.evidence.single().eventId, second.evidence.single().eventId)
        assertTrue(first.evidence.single().eventId.isNotBlank())

        ledger.record(evidence(EvidenceOutcome.SUCCEEDED, at = 43))
        val rewritten = org.json.JSONObject(file.readText())
        assertEquals(2, rewritten.getInt("schema_version"))
        directory.deleteRecursively()
    }

}
