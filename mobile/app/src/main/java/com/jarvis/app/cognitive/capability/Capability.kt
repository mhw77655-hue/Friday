package com.jarvis.app.cognitive.capability

import com.jarvis.app.cognitive.execution.ActionPort
import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.cognitive.execution.ExecutionResult

/**
 * Core capability contract — the single invocation seam. Deliberately minimal:
 * a future capability only needs to describe itself and handle one invocation.
 * The invocation boundary (CapabilityInvoker) enforces availability, permissions,
 * deadlines and the immune pathway — a capability is never responsible for them.
 */
interface Capability {
    val descriptor: CapabilityDescriptor

    /** Execute one action. Must encode failure in the returned [ExecutionResult]
     *  rather than throwing (a throw is treated as a non-recoverable failure). */
    suspend fun invoke(request: ActionRequest): ExecutionResult
}

/**
 * Adapter connecting the fabric to the 01C ActionPort boundary: any existing
 * [ActionPort] implementation becomes a first-class capability. This is how the
 * execution layer and the fabric stay interchangeable.
 */
class PortBackedCapability(
    override val descriptor: CapabilityDescriptor,
    private val port: ActionPort
) : Capability {
    override suspend fun invoke(request: ActionRequest): ExecutionResult = port.execute(request)
}

/**
 * Deterministic lifecycle state machine. Transitions are validated against the
 * fixed table — invalid transitions are rejected, never silently accepted.
 */
enum class CapabilityLifecycle {
    REGISTERED,
    AVAILABLE,
    RUNNING,
    PAUSED,
    DEGRADED,
    ISOLATED,
    UNAVAILABLE,
    DISABLED,
    FAILED;

    companion object {
        private val transitions: Map<CapabilityLifecycle, Set<CapabilityLifecycle>> = mapOf(
            REGISTERED to setOf(AVAILABLE, DISABLED, FAILED),
            AVAILABLE to setOf(RUNNING, PAUSED, DEGRADED, ISOLATED, UNAVAILABLE, DISABLED, FAILED),
            RUNNING to setOf(AVAILABLE, PAUSED, DEGRADED, ISOLATED, UNAVAILABLE, DISABLED, FAILED),
            PAUSED to setOf(RUNNING, AVAILABLE, DISABLED, FAILED),
            DEGRADED to setOf(AVAILABLE, RUNNING, ISOLATED, UNAVAILABLE, DISABLED, FAILED),
            ISOLATED to setOf(AVAILABLE, DEGRADED, UNAVAILABLE, DISABLED, FAILED),
            UNAVAILABLE to setOf(AVAILABLE, DEGRADED, DISABLED, FAILED),
            DISABLED to setOf(REGISTERED, AVAILABLE, FAILED),
            FAILED to setOf(AVAILABLE, ISOLATED, DISABLED, REGISTERED)
        )
    }

    /** Deterministic transition check. */
    fun canTransitionTo(target: CapabilityLifecycle): Boolean =
        target in (transitions[this] ?: emptySet())
}
