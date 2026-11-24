package ai.friday.assistant.launchsurvival

import ai.friday.assistant.EngineReadinessGate
import ai.friday.assistant.EngineReadinessState
import ai.friday.assistant.EngineSlot
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LAUNCH-WAKE-ORDER AC3: Green-only engine readiness test.
 *
 * Requirements:
 * - Package ai.friday.assistant.launchsurvival
 * - No Android classes (runs pure JVM / unit test)
 * - Asserts:
 *   1. Wiring held back by an injected latch -> every accessor answers not-ready and none throws.
 *   2. Latch released -> the same accessors return the real objects.
 *   3. Wiring that throws midway -> state is failed-to-wake with the failure recorded and accessors still do not throw.
 *   4. Many threads asking during waking all get an ordinary answer.
 */
class EngineReadinessTest {

    private class TestTarget(val id: String)

    private class TestAccessorHarness(
        val gate: EngineReadinessGate,
        var target: TestTarget? = null
    ) {
        fun targetSlot(): EngineSlot<TestTarget> = gate.slot { target }
    }

    @Test
    fun `wiring held back by latch answers not ready and releasing latch returns real objects`() {
        val gate = EngineReadinessGate()
        val harness = TestAccessorHarness(gate)

        val latch = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        gate.markWaking()

        val asyncJob = executor.submit {
            latch.await(5, TimeUnit.SECONDS)
            harness.target = TestTarget("live-target-1")
            gate.markReady()
        }

        try {
            // Latch is held back: accessor answers not-ready, none throws
            val wakingSlot = harness.targetSlot()
            assertFalse("Slot must not be ready while waking", wakingSlot.isReady)
            assertNull("Slot value must be null while waking", wakingSlot.value)
            assertEquals("Readiness must be Waking", EngineReadinessState.Waking, wakingSlot.readiness)

            // Release the latch
            latch.countDown()
            asyncJob.get(5, TimeUnit.SECONDS)

            // Latch released: same accessor returns real object
            val readySlot = harness.targetSlot()
            assertTrue("Slot must be ready after latch is released", readySlot.isReady)
            assertNotNull("Slot value must be present", readySlot.value)
            assertEquals("live-target-1", readySlot.value?.id)
            assertEquals("Readiness must be Ready", EngineReadinessState.Ready, readySlot.readiness)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `multiple organ accessors remain unavailable until the same gate is ready`() {
        val gate = EngineReadinessGate()
        val body = TestTarget("body")
        val voice = TestTarget("voice")
        val model = TestTarget("model")

        var bodyTarget: TestTarget? = null
        var voiceTarget: TestTarget? = null
        var modelTarget: TestTarget? = null

        gate.markWaking()
        val bodySlotWhileWaking = gate.slot { bodyTarget }
        val voiceSlotWhileWaking = gate.slot { voiceTarget }
        val modelSlotWhileWaking = gate.slot { modelTarget }

        assertFalse(bodySlotWhileWaking.isReady)
        assertFalse(voiceSlotWhileWaking.isReady)
        assertFalse(modelSlotWhileWaking.isReady)
        assertEquals(EngineReadinessState.Waking, bodySlotWhileWaking.readiness)
        assertEquals(EngineReadinessState.Waking, voiceSlotWhileWaking.readiness)
        assertEquals(EngineReadinessState.Waking, modelSlotWhileWaking.readiness)

        bodyTarget = body
        voiceTarget = voice
        modelTarget = model
        gate.markReady()

        assertEquals(body, gate.slot { bodyTarget }.value)
        assertEquals(voice, gate.slot { voiceTarget }.value)
        assertEquals(model, gate.slot { modelTarget }.value)
    }

    @Test
    fun `ready gate converts a missing required organ into a failed slot`() {
        val gate = EngineReadinessGate()
        gate.markReady()

        val slot = gate.slot<TestTarget> { null }

        assertFalse(slot.isReady)
        assertNull(slot.value)
        assertTrue(slot.readiness is EngineReadinessState.FailedToWake)
        assertTrue(
            (slot.readiness as EngineReadinessState.FailedToWake)
                .error.message.orEmpty()
                .contains("organ is unavailable")
        )
    }

    @Test
    fun `ready gate preserves provider failure instead of swallowing it`() {
        val gate = EngineReadinessGate()
        gate.markReady()
        val expected = IllegalStateException("organ accessor failure")

        val slot = gate.slot<TestTarget> { throw expected }

        assertFalse(slot.isReady)
        assertNull(slot.value)
        assertTrue(slot.readiness is EngineReadinessState.FailedToWake)
        assertEquals(expected, (slot.readiness as EngineReadinessState.FailedToWake).error)
    }

    @Test
    fun `terminal readiness wait times out instead of suspending forever`() = runBlocking {
        val gate = EngineReadinessGate()
        gate.markWaking()

        val result = gate.awaitTerminal(40)

        assertNull("A stalled bootstrap is a timeout, not a fabricated failure", result)
        assertEquals(EngineReadinessState.Waking, gate.current)
    }

    @Test
    fun `terminal readiness wait returns the real failure state`() = runBlocking {
        val gate = EngineReadinessGate()
        gate.markWaking()
        val expected = IllegalStateException("simulated bootstrap failure")

        val waiter = async { gate.awaitTerminal(1_000) }
        delay(20)
        gate.markFailed(expected)

        val result = waiter.await()
        assertTrue(result is EngineReadinessState.FailedToWake)
        assertEquals(expected, (result as EngineReadinessState.FailedToWake).error)
    }

    @Test
    fun `not started gate answers with a typed not ready slot`() {
        val gate = EngineReadinessGate()

        val slot = gate.slot { TestTarget("not-ready") }

        assertFalse(slot.isReady)
        assertNull(slot.value)
        assertEquals(EngineReadinessState.NotStarted, slot.readiness)
    }

    @Test
    fun `wiring throwing midway records failed-to-wake and accessors do not throw`() {
        val gate = EngineReadinessGate()
        val harness = TestAccessorHarness(gate)

        gate.markWaking()

        val expectedError = IllegalStateException("Simulated wiring failure midway")
        try {
            // Simulate wiring threw midway
            throw expectedError
        } catch (t: Throwable) {
            gate.markFailed(t)
        }

        val state = gate.current
        assertTrue("State must be FailedToWake", state is EngineReadinessState.FailedToWake)
        val failureState = state as EngineReadinessState.FailedToWake
        assertEquals("Failure cause must be preserved", expectedError, failureState.error)

        // Accessor still does not throw, returns typed not-ready with failure state
        val slot = harness.targetSlot()
        assertFalse("Slot must not be ready on failure", slot.isReady)
        assertNull("Slot value must be null on failure", slot.value)
        assertEquals("Readiness must match failed state", failureState, slot.readiness)
    }

    @Test
    fun `many threads asking during waking all get an ordinary answer`() {
        val gate = EngineReadinessGate()
        val harness = TestAccessorHarness(gate)
        gate.markWaking()

        val threadCount = 32
        val iterationsPerThread = 500
        val pool = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val notReadyCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            pool.submit {
                startLatch.await()
                try {
                    for (j in 0 until iterationsPerThread) {
                        val slot = harness.targetSlot()
                        if (!slot.isReady && slot.readiness == EngineReadinessState.Waking && slot.value == null) {
                            notReadyCount.incrementAndGet()
                        }
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(5, TimeUnit.SECONDS)
        pool.shutdownNow()

        assertTrue("All concurrent queries should complete within timeout", finished)
        assertEquals(
            "Every concurrent query during waking must return an ordinary not-ready answer",
            threadCount * iterationsPerThread,
            notReadyCount.get()
        )
    }
}
