package com.jarvis.app.voice

import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * R1 substrate — the serialized-inference scheduling mechanism (G2).
 *
 * One worker, FIFO order: contract work that touches the (future) native
 * models is submitted here so at most one inference runs at a time — the
 * discipline that prevents CPU/GPU contention between native engines (the
 * "blocking TTS starved other native engines" root cause of 6a5df37).
 *
 * R1 runs placeholder work only; the serialization contract is unit-proven.
 */
class VoiceScheduler(private val threadName: String = "voice-scheduler") : AutoCloseable {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, threadName).apply { isDaemon = true }
    }
    private val active = AtomicInteger(0)
    private val pending = AtomicLong(0)
    private val executedCount = AtomicLong(0)
    private val rejectedCount = AtomicLong(0)
    private val maxConcurrent = AtomicInteger(0)

    /** Total completed tasks. */
    val completed: Long get() = executedCount.get()

    /** Tasks rejected because the scheduler is shut down. */
    val rejected: Long get() = rejectedCount.get()

    /** Highest concurrency ever observed — must stay 1 for a serial scheduler. */
    val maxConcurrencyObserved: Int get() = maxConcurrent.get()

    /** True when nothing is queued and nothing is running. */
    val isIdle: Boolean get() = pending.get() == 0L

    /** Enqueue [block]; runs exactly one at a time, FIFO. Returns false if shut down. */
    fun submit(block: () -> Unit): Boolean = try {
        pending.incrementAndGet()
        executor.execute {
            val cur = active.incrementAndGet()
            maxConcurrent.updateAndGet { m -> maxOf(m, cur) }
            try {
                block()
            } finally {
                // Count + release the pending slot BEFORE the active slot, so
                // once `awaitIdle`/`isIdle` sees pending==0 every completed task
                // is already counted.
                executedCount.incrementAndGet()
                active.decrementAndGet()
                pending.decrementAndGet()
            }
        }
        true
    } catch (_: RejectedExecutionException) {
        pending.decrementAndGet()
        rejectedCount.incrementAndGet()
        false
    }

    /** Block until the queue fully drains (bounded). Returns true once idle. */
    fun awaitIdle(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (pending.get() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
        return pending.get() == 0L
    }

    override fun close() {
        executor.shutdown()
    }
}
