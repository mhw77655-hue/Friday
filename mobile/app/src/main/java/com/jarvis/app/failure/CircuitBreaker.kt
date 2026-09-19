package com.jarvis.app.failure

import java.util.ArrayDeque

/**
 * Per-dependency circuit breaker: CLOSED → OPEN after [failureThreshold]
 * failures, → HALF_OPEN after [openMs] cooldown (allowing one probe),
 * → CLOSED on a successful probe, or back to OPEN on probe failure.
 *
 * Purpose: stop hammering a known-bad dependency (retry storms) while still
 * allowing controlled recovery. Extremely lightweight — two counters and a
 * small failure-timestamp deque.
 */
class CircuitBreaker(
    val name: String,
    private val failureThreshold: Int = 3,
    private val openMs: Long = 30_000,
    private val windowMs: Long = 60_000,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private val lock = Any()
    private val recentFailureTimes = ArrayDeque<Long>()

    @Volatile var state: State = State.CLOSED
        private set
    @Volatile var openedAt: Long = 0L
        private set
    @Volatile var lastProbeSucceeded: Boolean? = null
        private set
    @Volatile var failureCount: Int = 0
        private set
    // ── 01D: added observability (additive) ──
    /** How many half-open probes have been attempted since construction. */
    @Volatile var probeAttempts: Int = 0
        private set
    /** How many times this breaker has transitioned into OPEN. */
    @Volatile var trippedCount: Int = 0
        private set

    val isOpen: Boolean get() = state == State.OPEN
    val isClosed: Boolean get() = state == State.CLOSED
    val isHalfOpen: Boolean get() = state == State.HALF_OPEN

    /** Milliseconds until the cooldown elapses and a probe may be sent. */
    fun cooldownRemainingMs(nowMs: Long = this.nowMs()): Long =
        if (state != State.OPEN) 0L else (openMs - (nowMs - openedAt)).coerceAtLeast(0L)

    /** Record a failure; opens the breaker when the windowed threshold is crossed. */
    fun onFailure() = synchronized(lock) {
        failureCount++
        val now = nowMs()
        recentFailureTimes.addLast(now)
        // Drop failures older than the sliding window.
        while (recentFailureTimes.isNotEmpty() && now - recentFailureTimes.first() > windowMs) {
            recentFailureTimes.removeFirst()
        }
        if (state == State.HALF_OPEN || state == State.OPEN) return@synchronized
        if (recentFailureTimes.size >= failureThreshold) {
            state = State.OPEN
            openedAt = now
            trippedCount++
        }
    }

    /**
     * Whether a request is allowed through now. When OPEN and the cooldown has
     * elapsed, transition to HALF_OPEN and allow exactly one probe.
     */
    fun allowRequest(): Boolean {
        if (state == State.CLOSED) return true
        if (state == State.OPEN) {
            val now = nowMs()
            if (now - openedAt >= openMs) {
                state = State.HALF_OPEN
                return true // one probe
            }
            return false
        }
        // HALF_OPEN: allow the probe.
        return true
    }

    /** A success (probe or request) closes the breaker. */
    fun onSuccess() = synchronized(lock) {
        recentFailureTimes.clear()
        state = State.CLOSED
        lastProbeSucceeded = true
    }

    /** A failed probe re-opens the breaker, restarting the cooldown. */
    fun onProbeFailure() = synchronized(lock) {
        state = State.OPEN
        openedAt = nowMs()
        lastProbeSucceeded = false
        trippedCount++
    }

    /**
     * Half-open probe: advance the breaker to HALF_OPEN when the cooldown has
     * elapsed and a probe is allowed (caller then calls [onSuccess] or
     * [onProbeFailure]). Returns true when the probe may proceed.
     */
    fun probe(nowMs: Long = this.nowMs()): Boolean = synchronized(lock) {
        when (state) {
            State.CLOSED -> true
            State.OPEN -> {
                if (nowMs - openedAt >= openMs) {
                    state = State.HALF_OPEN
                    probeAttempts++
                    true
                } else {
                    false
                }
            }
            State.HALF_OPEN -> {
                probeAttempts++
                true
            }
        }
    }

    fun reset() = synchronized(lock) {
        recentFailureTimes.clear()
        failureCount = 0
        state = State.CLOSED
        openedAt = 0L
        lastProbeSucceeded = null
        probeAttempts = 0
    }
}
