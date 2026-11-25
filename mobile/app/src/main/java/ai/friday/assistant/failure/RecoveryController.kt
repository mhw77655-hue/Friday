package ai.friday.assistant.failure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
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
     * [action] requests/starts recovery; [verify] independently proves health.
     */
    data class RetrySpec(
        val subsystem: String,
        val operation: String,
        val maxAttempts: Int = 2,
        val backoffMs: Long = 1_500,
        val breakerThreshold: Int = 3,
        val action: suspend () -> Boolean,
        /** Independent health oracle; action acceptance alone never means recovered. */
        val verify: suspend () -> Boolean,
        val verificationTimeoutMs: Long = 5_000,
        val verificationPollMs: Long = 100,
        /** Called when the breaker opens (subsystem disabled). */
        val onDisabled: (() -> Unit)? = null
    ) {
        init {
            require(maxAttempts >= 0) { "maxAttempts cannot be negative" }
            require(backoffMs >= 0) { "backoffMs cannot be negative" }
            require(breakerThreshold > 0) { "breakerThreshold must be positive" }
            require(verificationTimeoutMs in 1..60_000) { "verificationTimeoutMs must be 1..60000" }
            require(verificationPollMs in 1..verificationTimeoutMs) { "verificationPollMs must be within the verification timeout" }
        }
    }

    private class Entry(val spec: RetrySpec, val breaker: CircuitBreaker) {
        val attempts = java.util.concurrent.atomic.AtomicInteger(0)
        val disabledNotified = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    private val entries = ConcurrentHashMap<String, Entry>() // key = subsystem|operation
    private val running = ConcurrentHashMap.newKeySet<String>()

    // Subscribe to the canonical failure bus so every connected organ can use
    // a registered recovery path without knowing about this controller.
    // Historical events are not replayed during process startup.
    private val pendingEvents = ConcurrentHashMap<String, FailureEvent>()
    private val recoverySignals = Channel<Unit>(capacity = Channel.CONFLATED)

    // FailureSurface invokes listeners under its ordering lock. This listener
    // only stores the newest event per registered dependency and signals a
    // background consumer; it never runs recovery or disk work on the reporter.
    private val failureListener: (FailureEvent) -> Unit = { event ->
        if (event.recoverability != Recoverability.NONE &&
            event.recoverability != Recoverability.PERMANENT &&
            event.recoveryAction != RecoveryAction.USER_ACTION_REQUIRED
        ) {
            val key = keyFor(event.subsystem, event.operation)
            if (entries.containsKey(key)) {
                pendingEvents[key] = event
                recoverySignals.trySend(Unit)
            }
        }
    }

    init {
        surface.addFailureListener(replayExisting = false, listener = failureListener)
        scope.launch(Dispatchers.Default) {
            for (ignored in recoverySignals) {
                val snapshot = pendingEvents.entries.toList()
                snapshot.forEach { (key, event) ->
                    if (pendingEvents.remove(key, event)) handleReportedFailure(event)
                }
            }
        }
        scope.coroutineContext[Job]?.invokeOnCompletion {
            surface.removeFailureListener(failureListener)
            recoverySignals.close()
            pendingEvents.clear()
        }
    }

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
        // If registration happens after an active incident was published,
        // recover from its latest event rather than silently missing it.
        val active = surface.currentFailures.value.any {
            it.subsystem == spec.subsystem && it.operation == spec.operation
        }
        if (active) {
            surface.recentFailures.value.firstOrNull {
                it.subsystem == spec.subsystem && it.operation == spec.operation
            }?.let { event ->
                pendingEvents[key] = event
                recoverySignals.trySend(Unit)
            }
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
    fun handle(base: FailureReport): FailureEvent = surface.report(base)

    /** Process a just-published failure without publishing it a second time. */
    private fun handleReportedFailure(event: FailureEvent) {
        val entry = entries[keyFor(event.subsystem, event.operation)] ?: return
        if (event.recoverability == Recoverability.NONE ||
            event.recoverability == Recoverability.PERMANENT ||
            event.recoveryAction == RecoveryAction.USER_ACTION_REQUIRED
        ) return

        val base = FailureReport(
            subsystem = event.subsystem,
            operation = event.operation,
            severity = event.severity,
            category = event.category,
            message = event.message,
            source = event.source,
            cause = event.cause,
            state = event.state,
            dependency = event.dependency,
            recoverability = event.recoverability,
            correlationId = event.correlationId,
            attempt = event.attempt,
            recoveryAction = event.recoveryAction,
            metadata = event.metadata,
            userMessage = event.userMessage,
            failureCause = event.failureCause,
            relatedStepId = event.relatedStepId,
            relatedCapability = event.relatedCapability,
            environment = event.environment,
            rootCauseHypothesis = event.rootCauseHypothesis,
            resourceState = event.resourceState
        )
        if (entry.breaker.isOpen) {
            markDisabled(entry, base, "circuit is already open")
            return
        }

        // A zero-attempt policy is a deliberate fail-closed configuration.
        // Disable synchronously on the reporting path instead of scheduling a
        // coroutine that has no permitted attempt to execute.
        if (entry.spec.maxAttempts == 0) {
            val key = keyFor(base.subsystem, base.operation)
            if (!running.add(key)) return
            try {
                entry.breaker.onFailure()
                markDisabled(entry, base, "retry budget is zero; dependency disabled without an attempt")
            } finally {
                running.remove(key)
            }
            return
        }
        scheduleRetry(entry, base, countFailure = true)
    }

    /** Healthy signal for a dependency that uses [handle]: closes its breaker. */
    fun recordSuccess(subsystem: String, operation: String) {
        val entry = entries[keyFor(subsystem, operation)] ?: return
        entry.breaker.onSuccess()
        entry.attempts.set(0)
        entry.disabledNotified.set(false)
        // Surface a recovery for any open aggregate on this dependency.
        surface.currentFailures.value
            .filter { it.subsystem == subsystem && it.operation == operation }
            .forEach { agg ->
                surface.recover(agg.key, RecoveryResult.SUCCESS, "healthy again", RecoveryAction.RETRY)
            }
    }

    private fun scheduleRetry(entry: Entry, base: FailureReport, countFailure: Boolean) {
        val key = keyFor(base.subsystem, base.operation)
        if (!running.add(key)) return

        if (countFailure) entry.breaker.onFailure()
        if (entry.breaker.isOpen) {
            running.remove(key)
            markDisabled(entry, base, "circuit opened after repeated failures")
            return
        }

        val attempt = entry.attempts.incrementAndGet()
        val spec = entry.spec
        if (attempt > spec.maxAttempts) {
            running.remove(key)
            markDisabled(entry, base, "retry budget exhausted ($attempt)")
            return
        }

        scope.launch(Dispatchers.Default) {
            var nextAttempt: FailureReport? = null
            try {
                delay(spec.backoffMs * attempt) // linear backoff
                if (!isActive) return@launch
                surface.beginRecovery(base.dedupeKey, specActionOf(spec), "retry $attempt")
                var attemptFailureMessage = "attempt $attempt failed"
                val actionAccepted = try {
                    spec.action()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // Keep the retry failure on the original aggregate. A new
                    // message/key here creates a second orphaned alarm.
                    attemptFailureMessage = "retry $attempt threw: ${error.message ?: error::class.simpleName}"
                    false
                }
                val verifiedHealthy = if (actionAccepted) {
                    awaitIndependentVerification(spec)
                } else {
                    false
                }
                if (actionAccepted && !verifiedHealthy) {
                    attemptFailureMessage = "retry $attempt action accepted but independent health verification did not pass within ${spec.verificationTimeoutMs}ms"
                }
                val ok = actionAccepted && verifiedHealthy
                if (ok) {
                    entry.breaker.onSuccess()
                    entry.attempts.set(0)
                    entry.disabledNotified.set(false)
                    val recoveredKeys = surface.currentFailures.value
                        .filter { it.subsystem == base.subsystem && it.operation == base.operation }
                        .map { it.key }
                        .plus(base.dedupeKey)
                        .distinct()
                    recoveredKeys.forEach { aggregateKey ->
                        surface.recover(
                            aggregateKey, RecoveryResult.SUCCESS,
                            "recovered on attempt $attempt", specActionOf(spec), base.correlationId
                        )
                    }
                } else {
                    // A failed retry is an ordinary failed attempt while the
                    // breaker is CLOSED. Do not label it a failed HALF_OPEN
                    // probe: that would open the circuit after the first retry
                    // and silently defeat RetrySpec.maxAttempts.
                    entry.breaker.onFailure()
                    surface.recover(
                        base.dedupeKey, RecoveryResult.FAILED,
                        attemptFailureMessage, specActionOf(spec), base.correlationId
                    )
                    if (entry.breaker.isOpen) {
                        markDisabled(entry, base, "circuit opened after repeated retry failures")
                    } else {
                        // Defer scheduling until after this coroutine releases
                        // its running marker. Removing it early lets another
                        // caller schedule work whose marker this coroutine's
                        // finally block could then accidentally remove.
                        nextAttempt = base.copy(attempt = attempt + 1)
                    }
                }
            } catch (cancelled: CancellationException) {
                // Cancellation is not a recovery failure, but it must not
                // leave the shared failure surface stuck in RECOVERING.
                surface.recover(
                    base.dedupeKey, RecoveryResult.NOT_ATTEMPTED,
                    "retry $attempt cancelled", specActionOf(spec), base.correlationId
                )
                throw cancelled
            } finally {
                running.remove(key)
            }
            nextAttempt?.let { scheduleRetry(entry, it, countFailure = false) }
        }
    }

    private fun markDisabled(entry: Entry, base: FailureReport, reason: String) {
        // Preserve the original dedupe key while escalating the existing
        // aggregate. recoverability=NONE prevents the central failure listener
        // from recursively scheduling another retry for this terminal state.
        surface.report(
            base.copy(
                severity = FailureSeverity.max(base.severity, FailureSeverity.DEGRADED),
                recoverability = Recoverability.NONE,
                recoveryAction = RecoveryAction.DISABLE_COMPONENT,
                metadata = base.metadata + ("disabledReason" to reason)
            )
        )
        val activeKeys = surface.currentFailures.value
            .filter { it.subsystem == base.subsystem && it.operation == base.operation }
            .map { it.key }
            .plus(base.dedupeKey)
            .distinct()
        activeKeys.forEach { aggregateKey ->
            surface.recover(
                aggregateKey,
                RecoveryResult.FAILED,
                reason,
                RecoveryAction.DISABLE_COMPONENT,
                base.correlationId
            )
        }
        notifyDisabledOnce(entry)
    }

    private fun notifyDisabledOnce(entry: Entry) {
        if (entry.disabledNotified.compareAndSet(false, true)) {
            runCatching { entry.spec.onDisabled?.invoke() }
        }
    }

    private suspend fun awaitIndependentVerification(spec: RetrySpec): Boolean {
        val deadlineNanos = System.nanoTime() +
            java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(spec.verificationTimeoutMs)
        while (true) {
            val healthy = try {
                spec.verify()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (healthy) return true

            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0) return false
            val remainingMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1)
            delay(minOf(spec.verificationPollMs, remainingMs))
        }
    }

    private fun specActionOf(spec: RetrySpec): RecoveryAction = when (spec.backoffMs) {
        0L -> RecoveryAction.RETRY
        else -> RecoveryAction.RETRY_WITH_BACKOFF
    }

    /** Circuit state for diagnostics/tests. */
    fun breakerState(subsystem: String, operation: String): CircuitBreaker.State? =
        entries[keyFor(subsystem, operation)]?.breaker?.state

    /** Cumulative failure observations, exposed for recovery diagnostics and invariant tests. */
    fun breakerFailureCount(subsystem: String, operation: String): Int? =
        entries[keyFor(subsystem, operation)]?.breaker?.failureCount

    private fun keyFor(subsystem: String, operation: String) = "$subsystem|$operation"
}
