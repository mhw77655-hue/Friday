package com.jarvis.app.latency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the local model resident between turns ("keep the model warm") so the
 * slow path's first token is not dominated by model swap-in.
 *
 * - Periodic 1-token ping every [PING_INTERVAL_MS] while the model is idle/ok.
 * - **Backs off entirely** while the model reports failure — a down server is
 *   never hammered — and re-arms the moment a real request succeeds (status →
 *   sending/ok).
 * - [prime] fires one ping right at input time (bounded by [PRIME_COOLDOWN_MS])
 *   so the deep request starts against a warm model.
 *
 * All work is fire-and-forget on a background dispatcher. A failed warm ping is
 * a normal outcome, never a user-visible error, and never mutates the status
 * flow (warm pings go through `ModelManager.requestChat`, which is read-only
 * w.r.t. the status flow). [attach] wires the model authority — called by
 * [LatencyLayer.init] once at boot.
 */
object WarmupEngine {
    private const val PING_INTERVAL_MS = 60_000L
    private const val PRIME_COOLDOWN_MS = 30_000L
    private const val PING_TIMEOUT_MS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var modelManager: com.jarvis.app.model.ModelManager? = null
    @Volatile private var started = false
    @Volatile private var lastPrimeMs = 0L
    @Volatile private var backedOff = false
    @Volatile private var pingInFlight = false

    /** Attach the canonical model authority (LatencyLayer.init). */
    fun attach(m: com.jarvis.app.model.ModelManager) {
        modelManager = m
    }

    fun start() {
        if (started) return
        started = true
        val mm = modelManager ?: return
        // A real request succeeding (sending/ok) proves the server is up —
        // re-arm pings. An explicit failure latches backoff.
        scope.launch {
            mm.status.collect { status -> backedOff = isFailure(status) }
        }
        scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                pingIfHealthy()
            }
        }
    }

    /** Called at input time — one warm ping if the last one was a while ago. */
    fun prime() {
        val now = System.currentTimeMillis()
        if (now - lastPrimeMs < PRIME_COOLDOWN_MS) return
        lastPrimeMs = now
        pingIfHealthy()
    }

    /**
     * Pure decision (unit-testable): may we issue a warm ping right now?
     * Idle (never contacted yet) or previously-ok statuses are pingable;
     * in-flight requests (sending/retrying) are not, so a ping can never queue
     * behind or compete with the actual answer.
     */
    fun canPing(status: String, backedOff: Boolean, pingInFlight: Boolean): Boolean =
        !backedOff && !pingInFlight && (status == "idle" || status.startsWith("ok"))

    private fun pingIfHealthy() {
        val mm = modelManager ?: return
        if (!canPing(mm.status.value, backedOff, pingInFlight)) return
        pingInFlight = true
        scope.launch {
            try {
                val reply = mm.requestChat(
                    messages = listOf(
                        "system" to "You are a tiny liveness probe.",
                        "user" to "Say OK."
                    ),
                    maxTokens = 1,
                    timeoutMs = PING_TIMEOUT_MS
                )
                backedOff = reply == null
            } catch (e: Exception) {
                backedOff = true
            } finally {
                pingInFlight = false
            }
        }
    }

    private fun isFailure(status: String): Boolean =
        status.contains("unreachable") || status.contains("failed") ||
            status.contains("error") || status.contains("parse failed")
}
