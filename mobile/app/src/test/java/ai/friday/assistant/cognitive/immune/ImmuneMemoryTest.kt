package ai.friday.assistant.cognitive.immune

import ai.friday.assistant.failure.FailureCategory
import ai.friday.assistant.failure.FailureCause
import ai.friday.assistant.failure.FailureSignature
import ai.friday.assistant.failure.RecoveryAction
import ai.friday.assistant.failure.RecoveryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    fun `working set evicts least recently observed signatures and bounds evidence history`() {
        val memory = ImmuneMemory(nowMs = { 10L }, maxEntries = 2, maxLedgerEntries = 3)
        val first = sig
        val second = FailureSignature("TTS", "speak", FailureCategory.TTS, FailureCause.TIMEOUT)
        val third = FailureSignature("MODEL", "load", FailureCategory.MODEL, FailureCause.TIMEOUT)

        memory.record(first, RecoveryAction.RELOAD, RecoveryResult.FAILED)
        memory.record(second, RecoveryAction.FALLBACK, RecoveryResult.SUCCESS)
        assertNotNull(memory.resembles(first)) // refresh first; second is now eldest
        memory.record(third, RecoveryAction.RETRY, RecoveryResult.FAILED)

        assertEquals(2, memory.size())
        assertNull(memory.resembles(second))
        assertNotNull(memory.resembles(first))
        assertNotNull(memory.resembles(third))
        assertEquals(3, memory.evidenceLedger().size)

        memory.record(third, RecoveryAction.RETRY, RecoveryResult.FAILED)
        memory.record(third, RecoveryAction.RETRY, RecoveryResult.FAILED)
        assertEquals(3, memory.evidenceLedger().size)
        assertEquals(3, memory.resembles(third)!!.previousFailures)
        assertEquals(3, memory.evidenceLedger().size)
    }

    @Test
    fun `concurrent observations preserve exact counts and bounded history`() {
        val memory = ImmuneMemory(maxEntries = 4, maxLedgerEntries = 257)
        val workers = 8
        val perWorker = 500
        val pool = Executors.newFixedThreadPool(workers)
        try {
            val tasks = (0 until workers).map {
                java.util.concurrent.Callable {
                    repeat(perWorker) {
                        memory.record(sig, RecoveryAction.RETRY_WITH_BACKOFF, RecoveryResult.FAILED)
                    }
                }
            }
            pool.invokeAll(tasks).forEach { it.get(10, TimeUnit.SECONDS) }

            assertEquals(workers * perWorker, memory.resembles(sig)!!.previousFailures)
            assertEquals(1, memory.size())
            assertEquals(257, memory.evidenceLedger().size)
            assertEquals(257, memory.evidenceLedger().map { it.id }.toSet().size)
        } finally {
            pool.shutdownNow()
        }
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
