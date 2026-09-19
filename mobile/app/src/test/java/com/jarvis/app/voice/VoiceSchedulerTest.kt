package com.jarvis.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * R1 substrate — serialized-inference scheduling (G2) unit proof.
 * Plain JVM (JUnit 4): no Android runtime required.
 *
 * The single invariant: at most one task runs at a time, FIFO order,
 * regardless of how many are submitted concurrently.
 */
class VoiceSchedulerTest {

    @Test
    fun `tasks never run concurrently`() {
        val scheduler = VoiceScheduler()
        try {
            val active = AtomicInteger(0)
            val taskCount = 20
            repeat(taskCount) {
                scheduler.submit {
                    val cur = active.incrementAndGet()
                    assertTrue("concurrency exceeded 1: $cur", cur == 1)
                    Thread.sleep(5)
                    active.decrementAndGet()
                }
            }
            assertTrue("scheduler should drain", scheduler.awaitIdle(5000))
            assertEquals(taskCount.toLong(), scheduler.completed)
            assertEquals(1, scheduler.maxConcurrencyObserved)
        } finally {
            scheduler.close()
        }
    }

    @Test
    fun `tasks run in FIFO order`() {
        val scheduler = VoiceScheduler()
        try {
            val order = mutableListOf<Int>()
            repeat(10) { i ->
                scheduler.submit { order.add(i) }
            }
            assertTrue(scheduler.awaitIdle(5000))
            assertEquals((0 until 10).toList(), order)
        } finally {
            scheduler.close()
        }
    }

    @Test
    fun `submit after close is rejected and counted`() {
        val scheduler = VoiceScheduler()
        scheduler.close()
        val accepted = scheduler.submit {}
        assertTrue("post-close submit must be rejected", !accepted)
        assertEquals(1L, scheduler.rejected)
        assertEquals(0L, scheduler.completed)
    }

    @Test
    fun `idle between tasks`() {
        val scheduler = VoiceScheduler()
        try {
            assertTrue(scheduler.isIdle)
            scheduler.submit { Thread.sleep(20) }
            assertTrue(!scheduler.isIdle)
            assertTrue(scheduler.awaitIdle(1000))
            assertTrue(scheduler.isIdle)
        } finally {
            scheduler.close()
        }
    }
}
