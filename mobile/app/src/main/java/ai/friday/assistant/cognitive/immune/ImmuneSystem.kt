package ai.friday.assistant.cognitive.immune

import ai.friday.assistant.cognitive.CognitiveEngine.CognitiveEvent
import ai.friday.assistant.failure.BackoffPolicy
import ai.friday.assistant.failure.CircuitBreaker
import ai.friday.assistant.failure.ContainmentStatus
import ai.friday.assistant.failure.DegradationLevel
import ai.friday.assistant.failure.FailureCause
import ai.friday.assistant.failure.FailureEvent
import ai.friday.assistant.failure.FailureReport
import ai.friday.assistant.failure.FailureSurface
import ai.friday.assistant.failure.RecoveryAction
import ai.friday.assistant.failure.RecoveryResult
import ai.friday.assistant.failure.failureSignatureOf
import java.util.concurrent.ConcurrentHashMap

/**
 * ImmuneSystem — the nervous system's resilience coordinator.
 *
 * Pipeline per failure:
 *   detect → classify → contain → degrade → recover → remember
 *
 * Guarantees:
 *  - one subsystem failing never poisons unrelated subsystems (containment is
 *    per-subsystem; only dependency-graph neighbours degrade),
 *  - repeated failures stop hammering a broken dependency (per-operation
 *    CircuitBreaker),
 *  - retries and recovery attempts are bounded (BackoffPolicy + attempt caps)
 *    — no infinite retry, no infinite recovery,
 *  - every recovery attempt produces structured evidence in [ImmuneMemory],
 *  - all observations are emitted as [CognitiveEvent] subtypes on the shared
 *    bus (no second event system).
 *
 * The existing [FailureSurface] and (when wired) [RecoveryController] remain
 * authoritative for surfacing and async retry scheduling; this coordinator
 * decides and observes.
 */
class ImmuneSystem(
    val surface: FailureSurface,
    val containment: ContainmentRegistry = ContainmentRegistry(),
    val degradation: DegradedModeController = DegradedModeController(),
    val dependencyGraph: DependencyGraph = DependencyGraph(),
    val memory: ImmuneMemory = ImmuneMemory(),
    private val backoffPolicy: BackoffPolicy = BackoffPolicy(),
    private val breakerFactory: (String) -> CircuitBreaker = { key -> CircuitBreaker(name = key) },
    private val emit: (CognitiveEvent) -> Unit = {}
) {

    private val breakers = ConcurrentHashMap<String, CircuitBreaker>()
    // Prevent concurrent recovery actions for the same subsystem/operation.
    private val recoveryLocks = ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock>()
    private val recoveryAttempts = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    private val relatedCapabilityByOperation = ConcurrentHashMap<String, String>()

    fun breaker(subsystem: String, operation: String): CircuitBreaker =
        breakers.computeIfAbsent(key(subsystem, operation)) { breakerFactory(it) }

    /**
     * Observe a failure that has ALREADY been published to the canonical
     * FailureSurface. This is the live-runtime integration seam: it must not
     * publish the event again or start synchronous recovery on the caller's
     * thread. RecoveryController remains the sole async retry executor.
     */
    fun observe(event: FailureEvent) {
        val report = FailureReport(
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
        val operationKey = key(report.subsystem, report.operation)
        if (report.relatedCapability == null) relatedCapabilityByOperation.remove(operationKey)
        else relatedCapabilityByOperation[operationKey] = report.relatedCapability

        emit(CognitiveEvent.FailureDetected(event, report.failureCause))
        val cause = report.failureCause ?: FailureCause.UNKNOWN
        emit(CognitiveEvent.FailureClassified(event, cause, backoffPolicy.isRetryable(cause)))

        val dependents = dependencyGraph.affectedDependents(report.subsystem)
        val isolated = containment.setStatus(report.subsystem, ContainmentStatus.ISOLATED)
        dependents.forEach { dependent ->
            if (containment.statusOf(dependent) == ContainmentStatus.HEALTHY) {
                containment.setStatus(dependent, ContainmentStatus.DEGRADED)
            }
        }
        if (isolated != null) {
            emit(CognitiveEvent.ContainmentStarted(report.subsystem, ContainmentStatus.ISOLATED, dependents))
        }
        report.relatedCapability?.let { capability ->
            degradation.enter(capability, DegradationLevel.DEGRADED)?.let { (_, level) ->
                emit(CognitiveEvent.DegradedModeEntered(capability, level))
            }
        }
        val circuit = breaker(report.subsystem, report.operation)
        val wasClosed = circuit.isClosed
        circuit.onFailure()
        if (wasClosed && circuit.isOpen) {
            emit(CognitiveEvent.CircuitOpened(operationKey, circuit.failureCount))
        }
        // The existing RecoveryController owns retries. This observer records
        // the incident without attempting a second, competing recovery path.
        remember(report, report.recoveryAction, RecoveryResult.NOT_ATTEMPTED)
    }

    /**
     * One failure through the pipeline.
     *
     * @param recoveryFn performs ONE recovery attempt; returns true when the
     *   subsystem is healthy again. Optional — when null (or the circuit is
     *   open, or the cause is non-retryable) no recovery is attempted and the
     *   failure is contained + remembered.
     */
    fun onFailure(report: FailureReport, recoveryFn: (() -> Boolean)? = null): FailureEvent {
        val operationKey = key(report.subsystem, report.operation)
        if (report.relatedCapability == null) {
            relatedCapabilityByOperation.remove(operationKey)
        } else {
            relatedCapabilityByOperation[operationKey] = report.relatedCapability
        }
        val event = surface.report(report)
        emit(CognitiveEvent.FailureDetected(event, report.failureCause))

        // ── classify
        val cause = report.failureCause ?: FailureCause.UNKNOWN
        val retryable = backoffPolicy.isRetryable(cause)
        emit(CognitiveEvent.FailureClassified(event, cause, retryable))

        // ── contain (isolate the subsystem; degrade only its dependents)
        val dependents = dependencyGraph.affectedDependents(report.subsystem)
        val isolated = containment.setStatus(report.subsystem, ContainmentStatus.ISOLATED)
        dependents.forEach { dependent ->
            // A dependency fault may degrade a healthy dependent, but must not
            // overwrite an independent failure that already isolated it.
            if (containment.statusOf(dependent) == ContainmentStatus.HEALTHY) {
                containment.setStatus(dependent, ContainmentStatus.DEGRADED)
            }
        }
        if (isolated != null) {
            emit(CognitiveEvent.ContainmentStarted(report.subsystem, ContainmentStatus.ISOLATED, dependents))
        }
        report.relatedCapability?.let { cap ->
            degradation.enter(cap, DegradationLevel.DEGRADED)?.let { (_, lvl) ->
                emit(CognitiveEvent.DegradedModeEntered(cap, lvl))
            }
        }

        // ── circuit breaker
        val cb = breaker(report.subsystem, report.operation)
        val wasClosed = cb.isClosed
        cb.onFailure()
        if (wasClosed && cb.isOpen) {
            emit(CognitiveEvent.CircuitOpened(key(report.subsystem, report.operation), cb.failureCount))
        }

        // ── recover (bounded; never hammer an open circuit)
        if (retryable && recoveryFn != null && !cb.isOpen) {
            recoverBounded(report, recoveryFn, cb, cause)
        } else {
            remember(report, report.recoveryAction, RecoveryResult.NOT_ATTEMPTED)
        }
        return event
    }

    /**
     * Allow exactly one probe once the cooldown has elapsed. Returns whether a
     * probe was permitted and, if so, its outcome.
     */
    fun probe(subsystem: String, operation: String, recoveryFn: () -> Boolean): ProbeOutcome {
        val operationKey = key(subsystem, operation)
        val cb = breaker(subsystem, operation)
        if (!cb.probe()) return ProbeOutcome.DENIED
        emit(CognitiveEvent.CircuitHalfOpened(key(subsystem, operation)))
        val ok = try {
            recoveryFn()
        } catch (e: Exception) {
            false
        }
        return if (ok) {
            cb.onSuccess()
            // A verified recovery starts a fresh incident budget. Without
            // this reset, one exhausted incident permanently disables future
            // bounded recovery attempts for this subsystem/operation.
            recoveryAttempts[operationKey]?.set(0)
            recoverSurfaceForOperation(
                subsystem, operation, RecoveryResult.SUCCESS,
                "recovered by health probe", RecoveryAction.RETRY
            )
            markHealthy(subsystem, relatedCapabilityByOperation[operationKey])
            ProbeOutcome.RECOVERED
        } else {
            cb.onProbeFailure()
            recoverSurfaceForOperation(
                subsystem, operation, RecoveryResult.FAILED,
                "health probe failed", RecoveryAction.RETRY
            )
            emit(CognitiveEvent.CircuitOpened(key(subsystem, operation), cb.failureCount))
            ProbeOutcome.FAILED
        }
    }

    /** Healthy signal: close the breaker, restore containment, exit degradation. */
    fun onSuccess(subsystem: String, operation: String) {
        breaker(subsystem, operation).onSuccess()
        val operationKey = key(subsystem, operation)
        recoveryAttempts[operationKey]?.set(0)
        recoverSurfaceForOperation(
            subsystem, operation, RecoveryResult.SUCCESS,
            "healthy signal received", RecoveryAction.RETRY
        )
        markHealthy(subsystem, relatedCapabilityByOperation[operationKey])
    }

    /** Diagnose the current condition (deeper questions from the surface). */
    fun diagnose(event: FailureEvent): ai.friday.assistant.failure.SelfDiagnosis.DetailedDiagnosis {
        val diagnosis = ai.friday.assistant.failure.SelfDiagnosis(surface)
        return diagnosis.detailedDiagnosis(event, dependentsOf = { dependencyGraph.affectedDependents(it) })
    }

    /** Render a resource snapshot into the failure record. */
    fun resourceFailureReport(
        subsystem: String,
        operation: String,
        cause: FailureCause,
        resourceState: ai.friday.assistant.cognitive.ResourceState,
        relatedCapability: String? = null
    ): FailureReport = FailureReport(
        subsystem = subsystem,
        operation = operation,
        severity = ai.friday.assistant.failure.FailureSeverity.DEGRADED,
        category = ai.friday.assistant.failure.FailureCategory.RESOURCE,
        message = "resource pressure: ${cause.name}",
        failureCause = cause,
        relatedCapability = relatedCapability,
        resourceState = ResourceFailureHandler.snapshot(resourceState),
        recoverability = ai.friday.assistant.failure.Recoverability.RECOVERABLE
    )

    // ──────────────────────────────────────────────────────────── internals

    private fun recoverBounded(
        report: FailureReport,
        recoveryFn: () -> Boolean,
        cb: CircuitBreaker,
        cause: FailureCause
    ) {
        val key = key(report.subsystem, report.operation)
        val recoveryLock = recoveryLocks.computeIfAbsent(key) {
            java.util.concurrent.locks.ReentrantLock()
        }
        // A concurrent duplicate incident is still recorded and contained by
        // onFailure, but it must not start a second recovery action while the
        // first one is running. Arbitrary recovery actions may have side effects.
        if (!recoveryLock.tryLock()) {
            remember(report, report.recoveryAction, RecoveryResult.NOT_ATTEMPTED)
            return
        }
        try {
            val attempts = recoveryAttempts.computeIfAbsent(key) { java.util.concurrent.atomic.AtomicInteger(0) }
            val maxAttempts = backoffPolicy.maxAttempts

            while (attempts.get() < maxAttempts) {
                val attemptNumber = attempts.incrementAndGet()
                surface.beginRecovery(
                    report.dedupeKey, report.recoveryAction, "attempt $attemptNumber"
                )
                emit(CognitiveEvent.RecoveryStarted(report.subsystem, report.operation, "attempt $attemptNumber"))
                val ok = try {
                    recoveryFn()
                } catch (e: Exception) {
                    false
                }
                if (ok) {
                    cb.onSuccess()
                    attempts.set(0)
                    surface.recover(
                        report.dedupeKey, RecoveryResult.SUCCESS,
                        "recovered on attempt $attemptNumber",
                        report.recoveryAction, report.correlationId
                    )
                    emit(CognitiveEvent.RecoverySucceeded(report.subsystem, report.operation, "attempt $attemptNumber"))
                    markHealthy(report.subsystem, report.relatedCapability)
                    remember(report, report.recoveryAction, RecoveryResult.SUCCESS)
                    return
                }
                surface.recover(
                    report.dedupeKey, RecoveryResult.FAILED,
                    "attempt $attemptNumber failed",
                    report.recoveryAction, report.correlationId
                )
                emit(CognitiveEvent.RecoveryFailed(report.subsystem, report.operation, "attempt $attemptNumber"))
                if (attempts.get() < maxAttempts) {
                    // Pacing is provided by the policy value; the host may route it
                    // through the async RecoveryController. Zero in tests.
                    Thread.sleep(backoffPolicy.backoffDelayMs(attempts.get()))
                }
            }
            // Recovery budget exhausted.
            cb.onFailure()
            report.relatedCapability?.let { cap ->
                degradation.enter(cap, DegradationLevel.OFFLINE)?.let { (_, lvl) ->
                    emit(CognitiveEvent.DegradedModeEntered(cap, lvl))
                }
            }
            emit(CognitiveEvent.CapabilityUnavailable(report.relatedCapability ?: report.subsystem, report.message))
            remember(report, report.recoveryAction, RecoveryResult.FAILED)
        } finally {
            recoveryLock.unlock()
        }
    }

    private fun markHealthy(subsystem: String, relatedCapability: String?) {
        containment.setStatus(subsystem, ContainmentStatus.HEALTHY)?.let { (old, new) ->
            if (old != ContainmentStatus.HEALTHY) emit(CognitiveEvent.ContainmentStarted(subsystem, new, emptyList()))
        }
        restoreHealthyDependents(subsystem)
        // Recovery may only restore the capability that this subsystem's
        // failure actually degraded. Clearing the first degraded entry can
        // silently re-enable a different, still-broken capability.
        relatedCapability?.let { cap ->
            degradation.exit(cap)?.let { (old, _) ->
                emit(CognitiveEvent.DegradedModeExited(cap, old))
            }
        }
    }

    /**
     * Close every active failure aggregate for one recovered operation. The
     * failure history remains intact; only its current status changes.
     */
    private fun recoverSurfaceForOperation(
        subsystem: String,
        operation: String,
        result: RecoveryResult,
        message: String,
        action: RecoveryAction
    ) {
        surface.currentFailures.value
            .filter { it.subsystem == subsystem && it.operation == operation }
            .forEach { aggregate ->
                surface.recover(aggregate.key, result, message, action)
            }
    }

    /**
     * Re-evaluate transitive dependents after a dependency recovers. Restore a
     * dependent only when every declared dependency is healthy and the
     * dependent itself was merely DEGRADED (not independently ISOLATED).
     * Iterate to a fixed point so deeper dependents recover in dependency order.
     */
    private fun restoreHealthyDependents(recoveredSubsystem: String) {
        val affected = dependencyGraph.affectedDependents(recoveredSubsystem)
        var changed: Boolean
        do {
            changed = false
            affected.forEach { dependent ->
                if (containment.statusOf(dependent) != ContainmentStatus.DEGRADED) {
                    return@forEach
                }
                val dependencies = dependencyGraph.dependenciesOf(dependent)
                if (dependencies.isNotEmpty() && dependencies.all {
                        containment.statusOf(it) == ContainmentStatus.HEALTHY
                    }) {
                    containment.setStatus(dependent, ContainmentStatus.HEALTHY)?.let { (_, new) ->
                        emit(CognitiveEvent.ContainmentStarted(dependent, new, emptyList()))
                        changed = true
                    }
                }
            }
        } while (changed)
    }

    private fun remember(report: FailureReport, action: RecoveryAction, result: RecoveryResult) {
        memory.record(
            signature = failureSignatureOf(report),
            attemptedAction = action,
            result = result,
            metadata = mapOf(
                "severity" to report.severity.name,
                "cause" to (report.failureCause?.name ?: "UNKNOWN")
            )
        )
    }

    private fun key(subsystem: String, operation: String): String = "$subsystem|$operation"
}

enum class ProbeOutcome { DENIED, RECOVERED, FAILED }
