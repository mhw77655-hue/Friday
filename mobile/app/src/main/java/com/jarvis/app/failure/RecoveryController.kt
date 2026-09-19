package com.jarvis.app.failure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * Fault-containment + recovery executor.
 *
 * Owns one [CircuitBreaker] per (subsystem|operation) dependency and a bounded
 * retry scheduler. Capture sites that retry call [handle] instead of building
 * their own retry loops, so:
 *
 *  - retries are bounded and back off (no retry storms),
 *  - a repeatedly failing dependency opens its breaker and stops being
 *    hammered (DEGRADE / DISABLE_COMPONENT),
 *  - a successful probe closes the breaker and reports recovery to the surface,
 *  - the original failure is never hidden: every attempt re-reports to the
 *    surface and [FailureSurface.recover] records the outcome alongside it.
 */
class RecoveryController(
    private val surface: FailureSurface,
    private val scope: CoroutineScope
) {

    /**
     * How to recover one (subsystem, operation) dependency.
     * [action] returns true when the subsystem is healthy again.
     */
    data class RetrySpec(
        val subsystem: String,
        val operation: String,
        val maxAttempts: Int = 2,
        val backoffMs: Long = 1_500,
        val breakerThreshold: Int = 3,
        val action: () -> Boolean,
        /** Called when the breaker opens (subsystem disabled). */
        val onDisabled: (() -> Unit)? = null
    )

    private class Entry(val spec: RetrySpec, val breaker: CircuitBreaker) {
        val attempts = java.util.concurrent.atomic.AtomicInteger(0)
    }

    private val entries = ConcurrentHashMap<String, Entry>() // key = subsystem|operation
    private val running = ConcurrentHashMap.newKeySet<String>()

    /** Register a recovery path for a dependency. Idempotent (first wins). */
    fun register(spec: RetrySpec) {
        val key = keyFor(spec.subsystem, spec.operation)
        entries.computeIfAbsent(key) {
            Entry(
                spec,
                CircuitBreaker(
                    name = key,
                    failureThreshold = spec.breakerThreshold,
                    openMs = 30_000
                )
            )
        }
    }

    /**
     * A retryable failure just occurred. Reports to the surface, feeds the
     * breaker, and schedules a bounded retry when appropriate. Safe to call
     * from any thread. If no spec is registered for the dependency, the
     * failure is surfaced but nothing is retried.
     *
     * @param base report with original severity/recoverability (retryable).
     * @return the surfaced failure event.
     */
    fun handle(base: FailureReport): FailureEvent {
        val key = keyFor(base.subsystem, base.operation)
        val entry = entries[key]

        // Always surface the failure first — it must never disappear.
        val event = surface.report(base)

        if (entry == null) return event
        val breaker = entry.breaker

        // Feed the breaker.
        breaker.onFailure()

        when {
            breaker.isOpen && breaker.lastProbeSucceeded == false -> {
                // Probe failed recently: fully open, stop hammering. Mark the
                // dependency disabled/degraded so it stays visible.
                surface.report(base.copy(
                    severity = FailureSeverity.max(base.severity, FailureSeverity.DEGRADED),
                    recoveryAction = RecoveryAction.DISABLE_COMPONENT,
                    message = "${base.message} — circuit open, disabled until cooldown",
                    metadata = base.metadata + ("circuit" to "open")
                ))
                entry.spec.onDisabled?.invoke()
            }
            breaker.isOpen -> {
                // Recently opened — wait for cooldown; report degraded but do
                // not retry yet.
                surface.report(base.copy(
                    severity = FailureSeverity.max(base.severity, FailureSeverity.DEGRADED),
                    recoveryAction = RecoveryAction.WAIT_FOR_RECOVERY,
                    message = "${base.message} — circuit open, waiting to probe",
                    metadata = base.metadata + ("circuit" to "open")
                ))
            }
            else -> scheduleRetry(entry, base)
        }
        return event
    }

    /** Healthy signal for a dependency that uses [handle]: closes its breaker. */
    fun recordSuccess(subsystem: String, operation: String) {
        val entry = entries[keyFor(subsystem, operation)] ?: return
        entry.breaker.onSuccess()
        entry.attempts.set(0)
        // Surface a recovery for any open aggregate on this dependency.
        surface.currentFailures.value
            .filter { it.subsystem == subsystem && it.operation == operation }
            .forEach { agg ->
                surface.recover(agg.key, RecoveryResult.SUCCESS, "healthy again", RecoveryAction.RETRY)
            }
    }

    private fun scheduleRetry(entry: Entry, base: FailureReport) {
        val key = keyFor(base.subsystem, base.operation)
        if (!running.add(key)) return // one recovery in flight per dependency

        val attempt = entry.attempts.incrementAndGet()
        val spec = entry.spec
        if (attempt > spec.maxAttempts) {
            running.remove(key)
            // Budget exhausted — degrade the dependency.
            surface.report(base.copy(
                severity = FailureSeverity.max(base.severity, FailureSeverity.DEGRADED),
                recoveryAction = RecoveryAction.DEGRADE,
                message = "${base.message} — retries exhausted ($attempt)",
                metadata = base.metadata + ("retriesExhausted" to "true")
            ))
            spec.onDisabled?.invoke()
            return
        }

        scope.launch(Dispatchers.Default) {
            try {
                delay(spec.backoffMs * attempt) // linear backoff
                if (!isActive) return@launch
                surface.beginRecovery(base.dedupeKey, specActionOf(spec), "retry $attempt")
                val ok = try {
                    spec.action()
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    surface.report(base.copy(
                        attempt = attempt,
                        cause = t.message,
                        message = "${base.message} — retry $attempt threw"
                    ))
                    false
                }
                if (ok) {
                    entry.breaker.onSuccess()
                    entry.attempts.set(0)
                    surface.recover(
                        base.dedupeKey, RecoveryResult.SUCCESS,
                        "recovered on attempt $attempt", specActionOf(spec), base.correlationId
                    )
                } else {
                    entry.breaker.onProbeFailure()
                    surface.recover(base.dedupeKey, RecoveryResult.FAILED, "attempt $attempt failed", specActionOf(spec))
                    running.remove(key)
                    // Re-enter handle to count another failure / schedule next.
                    handle(base.copy(attempt = attempt + 1, cause = null))
                }
            } finally {
                running.remove(key)
            }
        }
    }

    private fun specActionOf(spec: RetrySpec): RecoveryAction = when (spec.backoffMs) {
        0L -> RecoveryAction.RETRY
        else -> RecoveryAction.RETRY_WITH_BACKOFF
    }

    /** Circuit state for diagnostics/tests. */
    fun breakerState(subsystem: String, operation: String): CircuitBreaker.State? =
        entries[keyFor(subsystem, operation)]?.breaker?.state

    private fun keyFor(subsystem: String, operation: String) = "$subsystem|$operation"
}
