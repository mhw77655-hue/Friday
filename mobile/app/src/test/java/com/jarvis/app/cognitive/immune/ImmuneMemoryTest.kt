package com.jarvis.app.cognitive.immune

import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureCause
import com.jarvis.app.failure.FailureSignature
import com.jarvis.app.failure.RecoveryAction
import com.jarvis.app.failure.RecoveryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmuneMemoryTest {

    private val sig = FailureSignature("STT", "transcribe", FailureCategory.STT, FailureCause.TIMEOUT)

    @Test
    fun `failure memory recognizes a repeated signature`() {
        val memory = ImmuneMemory(nowMs = { 1_000L })
        memory.record(sig, RecoveryAction.RETRY_WITH_BACKOFF, RecoveryResult.SUCCESS)
        memory.record(sig, RecoveryAction.RETRY_WITH_BACKOFF, RecoveryResult.SUCCESS)

        val hit = memory.resembles(sig)!!
        assertEquals(2, hit.previousFailures)
        assertEquals(RecoveryAction.RETRY_WITH_BACKOFF, hit.successfulRecovery)
        assertEquals(1_000L, hit.lastSeen)
        // confidence grows with observations
        assertTrue(hit.confidence > 0.5f)
    }

    @Test
    fun `unseen signature is not recognized`() {
        val memory = ImmuneMemory()
        assertNull(memory.resembles(sig))
    }

    @Test
    fun `signature is deterministic across identical failures`() {
        val a = FailureSignature("STT", "transcribe", FailureCategory.STT, FailureCause.TIMEOUT)
        val b = FailureSignature("STT", "transcribe", FailureCategory.STT, FailureCause.TIMEOUT)
        assertEquals(a.key, b.key)
        val c = FailureSignature("STT", "transcribe", FailureCategory.STT, FailureCause.INVALID_INPUT)
        assertFalse(a.key == c.key)
    }

    @Test
    fun `evidence ledger records what was tried and whether it worked`() {
        val memory = ImmuneMemory()
        memory.record(sig, RecoveryAction.RELOAD, RecoveryResult.FAILED)
        memory.record(sig, RecoveryAction.FALLBACK, RecoveryResult.SUCCESS)

        val ledger = memory.evidenceLedger()
        assertEquals(2, ledger.size)
        assertFalse(ledger[0].succeeded)
        assertTrue(ledger[1].succeeded)
        assertEquals(RecoveryAction.FALLBACK, ledger[1].attemptedAction)
        assertEquals(sig, ledger[0].signature)
    }
}
