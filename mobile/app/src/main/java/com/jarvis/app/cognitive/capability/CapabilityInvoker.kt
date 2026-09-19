package com.jarvis.app.cognitive.capability

import com.jarvis.app.cognitive.CognitiveEngine.CognitiveEvent
import com.jarvis.app.cognitive.execution.ActionFailure
import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.cognitive.execution.ExecutionResult
import com.jarvis.app.cognitive.immune.ImmuneSystem
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureCause
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.Recoverability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Observability envelope for one invocation. Wraps the reused [ExecutionResult]
 * — no duplicate result type. The directive's traceability fields live here.
 */
data class CapabilityInvocation(
    val requestId: String,
    val capabilityId: String?,
    val operation: String,
    val stepId: String,
    val resolution: Resolution,
    val result: ExecutionResult?,
    val startedAt: Long,
    val durationMs: Long,
    val state: CapabilityLifecycle?,
    /** True when the immune circuit refused the call without executing. */
    val refused: Boolean = false,
    val refusalReason: String? = null
) {
    val invoked: Boolean get() = result != null
    val success: Boolean get() = result?.success == true
}

/**
 * The execution boundary. Receives a resolved (or to-be-resolved) action and
 * produces a [CapabilityInvocation]. It enforces availability, permissions,
 * deadlines, lifecycle transitions, event emission, and — critically — converts
 * every failure into the existing FailureSurface / ImmuneSystem pathway. It
 * never retries automatically and never bypasses the resilience layer.
 */
class CapabilityInvoker(
    private val registry: CapabilityRegistry,
    private val resolver: CapabilityResolver,
    private val immune: ImmuneSystem,
    private val emit: (CognitiveEvent) -> Unit = {},
    private val defaultTimeoutMs: Long = 30_000,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    /** Deterministic request id for tracing (same action → same id). */
    fun requestId(request: ActionRequest): String = "${request.planId}|${request.stepId}|${request.action}"

    suspend fun invoke(
        request: ActionRequest,
        granted: Set<CapabilityPermission> = emptySet(),
        timeoutMs: Long? = null
    ): CapabilityInvocation {
        val id = requestId(request)
        val startedAt = nowMs()

        // Structural validation — a malformed request is rejected deterministically
        // before any capability is consulted, and never reaches the immune system.
        val invalid = validate(request)
        if (invalid != null) {
            emit(CognitiveEvent.CapabilityResolved("", request.action, invalid.reason.name))
            return CapabilityInvocation(id, null, request.action, request.stepId, invalid, null, startedAt, 0, null)
        }

        val resolution = resolver.resolve(request, granted)
        val resolvedId = (resolution as? Resolution.Success)?.capability?.descriptor?.id
        emit(CognitiveEvent.CapabilityResolved(resolvedId ?: "", request.action, resolutionState(resolution)))

        return when (resolution) {
            is Resolution.Failure -> CapabilityInvocation(id, null, request.action, request.stepId, resolution, null, startedAt, 0, null)
            is Resolution.Success -> invokeResolved(request, resolution, timeoutMs, id, startedAt)
        }
    }

    /**
     * Execute a pre-resolved action without re-resolving. The caller resolved
     * first (e.g. the capability-aware executor, which needs the Resolution for
     * state + observability before committing) — this skips resolution but keeps
     * every other boundary check: validation, circuit gate, availability /
     * lifecycle, deadline, and immune routing all still apply. The executor
     * never bypasses the immune layer by calling the capability directly.
     */
    suspend fun invokeResolved(
        request: ActionRequest,
        resolution: Resolution.Success,
        timeoutMs: Long? = null
    ): CapabilityInvocation = invokeResolved(request, resolution, timeoutMs, requestId(request), nowMs())

    private suspend fun invokeResolved(
        request: ActionRequest,
        resolution: Resolution.Success,
        timeoutMs: Long?,
        id: String,
        startedAt: Long
    ): CapabilityInvocation {
        val invalid = validate(request)
        if (invalid != null) {
            return CapabilityInvocation(id, null, request.action, request.stepId, invalid, null, startedAt, 0, null)
        }
        val capability = resolution.capability
        val capabilityId = capability.descriptor.id

        // Circuit gate: when the immune circuit is open (cooldown not elapsed)
        // the call is refused WITHOUT executing — this is what actually stops
        // hammering a broken capability. A probe is allowed once the cooldown
        // elapses (allowRequest transitions to HALF_OPEN).
        val circuit = immune.breaker(capabilityId, request.action)
        if (!circuit.allowRequest()) {
            emit(CognitiveEvent.CapabilityUnavailable(capabilityId, "circuit open"))
            return CapabilityInvocation(
                id, capabilityId, request.action, request.stepId, resolution, null,
                startedAt, 0, registry.stateOf(capabilityId), refused = true, refusalReason = "circuit open"
            )
        }

        registry.transitionTo(capability.descriptor.id, CapabilityLifecycle.RUNNING)
        emit(CognitiveEvent.CapabilityInvocationStarted(capability.descriptor.id, request.action, id))

        val timeout = timeoutMs ?: defaultTimeoutMs
        val result = try {
            withTimeoutOrNull(timeout) { capability.invoke(request) }
                ?: ExecutionResult.fail(ActionFailure.Timeout(timeout))
        } catch (e: CancellationException) {
            // External cancellation is NOT a capability failure. Roll the
            // lifecycle back to AVAILABLE and rethrow so the caller's
            // cancellation wins — nothing reaches the failure surface or
            // immune system. (Timeout is handled by withTimeoutOrNull above;
            // this path is only external cancellation of the calling scope.)
            registry.transitionTo(capability.descriptor.id, CapabilityLifecycle.AVAILABLE)
            throw e
        } catch (e: Exception) {
            ExecutionResult.fail(ActionFailure.Error("PORT_EXCEPTION", e.message ?: "capability threw"))
        }
        val duration = nowMs() - startedAt

        return if (result.success) {
            registry.transitionTo(capability.descriptor.id, CapabilityLifecycle.AVAILABLE)
            immune.onSuccess(capability.descriptor.id, request.action)
            emit(CognitiveEvent.CapabilityInvocationCompleted(capability.descriptor.id, request.action, id, duration, true))
            CapabilityInvocation(id, capability.descriptor.id, request.action, request.stepId, resolution, result, startedAt, duration, CapabilityLifecycle.AVAILABLE)
        } else {
            registry.transitionTo(capability.descriptor.id, CapabilityLifecycle.FAILED)
            val failureEvent = immune.onFailure(toFailureReport(request, capability, result, id))
            emit(CognitiveEvent.CapabilityInvocationFailed(
                capability.descriptor.id, request.action, id, result.failure?.let { failureCauseOf(it) }, failureEvent.id
            ))
            CapabilityInvocation(id, capability.descriptor.id, request.action, request.stepId, resolution, result, startedAt, duration, CapabilityLifecycle.FAILED)
        }
    }

    private fun resolutionState(resolution: Resolution): String = when (resolution) {
        is Resolution.Success -> "AVAILABLE"
        is Resolution.Failure -> resolution.reason.name
    }

    /** Structural request validation — a blank identity or action is malformed
     *  and is rejected deterministically before any capability is consulted. */
    private fun validate(request: ActionRequest): Resolution.Failure? = when {
        request.action.isBlank() -> Resolution.Failure(ResolutionReason.INVALID_REQUEST, "action must not be blank")
        request.planId.isBlank() -> Resolution.Failure(ResolutionReason.INVALID_REQUEST, "planId must not be blank")
        request.stepId.isBlank() -> Resolution.Failure(ResolutionReason.INVALID_REQUEST, "stepId must not be blank")
        else -> null
    }

    private fun toFailureReport(
        request: ActionRequest,
        capability: Capability,
        result: ExecutionResult,
        requestId: String
    ): FailureReport {
        val failure = result.failure
        val recoverable = failure?.recoverable == true
        return FailureReport(
            subsystem = capability.descriptor.id,
            operation = request.action,
            severity = FailureSeverity.ERROR,
            category = FailureCategory.CAPABILITY,
            message = result.failure?.message() ?: "capability invocation failed",
            failureCause = failure?.let { failureCauseOf(it) },
            relatedCapability = capability.descriptor.id,
            relatedStepId = request.stepId,
            correlationId = request.planId,
            recoverability = if (recoverable) Recoverability.RETRYABLE else Recoverability.NONE,
            metadata = mapOf("requestId" to requestId, "planId" to request.planId)
        )
    }
}

/** Structured failure cause from the existing ActionFailure vocabulary. */
fun failureCauseOf(failure: ActionFailure): FailureCause = when (failure) {
    is ActionFailure.Timeout -> FailureCause.TIMEOUT
    is ActionFailure.Unsupported -> FailureCause.UNSUPPORTED
    is ActionFailure.Error -> FailureCause.INTERNAL_ERROR
}

private fun ActionFailure.message(): String = when (this) {
    is ActionFailure.Error -> "$code: $message"
    is ActionFailure.Timeout -> "timed out after ${durationMs}ms"
    is ActionFailure.Unsupported -> "unsupported action $action"
}
