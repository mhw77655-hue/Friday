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
        at: Long = 1L
    ) = CapabilityEvidence(
        capabilityId = "phone.contacts.read",
        operation = "read_contacts",
        outcome = outcome,
        observedAtMs = at,
        source = "android-runtime",
        verified = verified
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
}
