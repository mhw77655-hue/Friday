package com.jarvis.app.cognitive.immune

import com.jarvis.app.cognitive.CognitiveEngine.CognitiveEvent
import com.jarvis.app.failure.BackoffPolicy
import com.jarvis.app.failure.CircuitBreaker
import com.jarvis.app.failure.ContainmentStatus
import com.jarvis.app.failure.DegradationLevel
import com.jarvis.app.failure.FailureCause
import com.jarvis.app.failure.FailureEvent
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.failure.RecoveryAction
import com.jarvis.app.failure.RecoveryResult
import com.jarvis.app.failure.failureSignatureOf
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
    private val recoveryAttempts = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()

    fun breaker(subsystem: String, operation: String): CircuitBreaker =
        breakers.computeIfAbsent(key(subsystem, operation)) { breakerFactory(it) }

    /**
     * One failure through the pipeline.
     *
     * @param recoveryFn performs ONE recovery attempt; returns true when the
     *   subsystem is healthy again. Optional — when null (or the circuit is
     *   open, or the cause is non-retryable) no recovery is attempted and the
     *   failure is contained + remembered.
     */
    fun onFailure(report: FailureReport, recoveryFn: (() -> Boolean)? = null): FailureEvent {
        val event = surface.report(report)
        emit(CognitiveEvent.FailureDetected(event, report.failureCause))

        // ── classify
        val cause = report.failureCause ?: FailureCause.UNKNOWN
        val retryable = backoffPolicy.isRetryable(cause)
        emit(CognitiveEvent.FailureClassified(event, cause, retryable))

        // ── contain (isolate the subsystem; degrade only its dependents)
        val dependents = dependencyGraph.affectedDependents(report.subsystem)
        val isolated = containment.setStatus(report.subsystem, ContainmentStatus.ISOLATED)
        dependents.forEach { containment.setStatus(it, ContainmentStatus.DEGRADED) }
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
            markHealthy(subsystem, operation, RecoveryAction.RETRY)
            ProbeOutcome.RECOVERED
        } else {
            cb.onProbeFailure()
            emit(CognitiveEvent.CircuitOpened(key(subsystem, operation), cb.failureCount))
            ProbeOutcome.FAILED
        }
    }

    /** Healthy signal: close the breaker, restore containment, exit degradation. */
    fun onSuccess(subsystem: String, operation: String) {
        breaker(subsystem, operation).onSuccess()
        markHealthy(subsystem, operation, RecoveryAction.RETRY)
    }

    /** Diagnose the current condition (deeper questions from the surface). */
    fun diagnose(event: FailureEvent): com.jarvis.app.failure.SelfDiagnosis.DetailedDiagnosis {
        val diagnosis = com.jarvis.app.failure.SelfDiagnosis(surface)
        return diagnosis.detailedDiagnosis(event, dependentsOf = { dependencyGraph.affectedDependents(it) })
    }

    /** Render a resource snapshot into the failure record. */
    fun resourceFailureReport(
        subsystem: String,
        operation: String,
        cause: FailureCause,
        resourceState: com.jarvis.app.cognitive.ResourceState,
        relatedCapability: String? = null
    ): FailureReport = FailureReport(
        subsystem = subsystem,
        operation = operation,
        severity = com.jarvis.app.failure.FailureSeverity.DEGRADED,
        category = com.jarvis.app.failure.FailureCategory.RESOURCE,
        message = "resource pressure: ${cause.name}",
        failureCause = cause,
        relatedCapability = relatedCapability,
        resourceState = ResourceFailureHandler.snapshot(resourceState),
        recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE
    )

    // ──────────────────────────────────────────────────────────── internals

    private fun recoverBounded(
        report: FailureReport,
        recoveryFn: () -> Boolean,
        cb: CircuitBreaker,
        cause: FailureCause
    ) {
        val key = key(report.subsystem, report.operation)
        val attempts = recoveryAttempts.computeIfAbsent(key) { java.util.concurrent.atomic.AtomicInteger(0) }
        val maxAttempts = backoffPolicy.maxAttempts

        while (attempts.get() < maxAttempts) {
            attempts.incrementAndGet()
            emit(CognitiveEvent.RecoveryStarted(report.subsystem, report.operation, "attempt ${attempts.get()}"))
            val ok = try {
                recoveryFn()
            } catch (e: Exception) {
                false
            }
            if (ok) {
                cb.onSuccess()
                emit(CognitiveEvent.RecoverySucceeded(report.subsystem, report.operation, "attempt ${attempts.get()}"))
                markHealthy(report.subsystem, report.operation, report.recoveryAction)
                remember(report, report.recoveryAction, RecoveryResult.SUCCESS)
                return
            }
            emit(CognitiveEvent.RecoveryFailed(report.subsystem, report.operation, "attempt ${attempts.get()}"))
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
    }

    private fun markHealthy(subsystem: String, operation: String, action: RecoveryAction) {
        containment.setStatus(subsystem, ContainmentStatus.HEALTHY)?.let { (old, new) ->
            if (old != ContainmentStatus.HEALTHY) emit(CognitiveEvent.ContainmentStarted(subsystem, new, emptyList()))
        }
        // find the related capability to exit degradation (best-effort from containment)
        degradation.all().keys.firstOrNull { degradation.levelOf(it) != DegradationLevel.FULL }?.let { cap ->
            degradation.exit(cap)?.let { (old, _) ->
                emit(CognitiveEvent.DegradedModeExited(cap, old))
            }
        }
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
