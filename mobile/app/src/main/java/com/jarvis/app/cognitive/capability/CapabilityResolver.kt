package com.jarvis.app.cognitive.capability

import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.failure.DegradationLevel

/** Why an action could not be resolved to a runnable capability. */
enum class ResolutionReason {
    /** The request itself was malformed (blank action / ids) — rejected before
     *  any capability is consulted. */
    INVALID_REQUEST,
    OPERATION_NOT_FOUND,
    CAPABILITY_DISABLED,
    CAPABILITY_UNAVAILABLE,
    DEPENDENCY_UNAVAILABLE,
    PERMISSION_DENIED,
    AMBIGUOUS
}

/** Structured resolution outcome — resolution never executes anything. */
sealed class Resolution {
    data class Success(val capability: Capability, val operation: String) : Resolution()
    data class Failure(val reason: ResolutionReason, val detail: String?) : Resolution()
}

/**
 * Given an [ActionRequest], decides which capability can perform it and whether
 * it may run now (exists / enabled / available / dependencies / permissions).
 * Pure decision — no execution, no side effects.
 */
class CapabilityResolver(
    private val registry: CapabilityRegistry,
    /** Current degraded level per capability (from DegradedModeController).
     *  A capability degraded to OFFLINE/UNAVAILABLE is not runnable. */
    private val degradedLevelOf: (String) -> DegradationLevel = { DegradationLevel.FULL }
) {

    fun resolve(request: ActionRequest, granted: Set<CapabilityPermission>): Resolution {
        val candidates = registry.lookupByOperation(request.action)
        if (candidates.isEmpty()) return Resolution.Failure(ResolutionReason.OPERATION_NOT_FOUND, null)

        // Deterministic order (registry already sorts by id).
        val eligible = candidates.filter { it.isEligible() && permissionsOk(it, granted) && depsOk(it) }
        return when (eligible.size) {
            0 -> bestFailureReason(candidates, granted)
            1 -> Resolution.Success(eligible[0], request.action)
            else -> Resolution.Failure(
                ResolutionReason.AMBIGUOUS,
                "multiple eligible capabilities: ${eligible.joinToString(",") { it.descriptor.id }}"
            )
        }
    }

    /**
     * The capability is enabled, online, in a runnable lifecycle state, and not
     * resource-degraded offline. A FAILED capability stays re-invocable: the
     * caller may retry, and repeated failures are bounded by the immune circuit
     * breaker (the invoker's gate) rather than by resolution. PAUSED / ISOLATED /
     * UNAVAILABLE / DISABLED are administrative holds and are not runnable.
     */
    private fun Capability.isEligible(): Boolean {
        val d = descriptor
        val state = registry.stateOf(d.id)
        val runnableState = state == null || state == CapabilityLifecycle.REGISTERED ||
            state == CapabilityLifecycle.AVAILABLE || state == CapabilityLifecycle.DEGRADED ||
            state == CapabilityLifecycle.RUNNING || state == CapabilityLifecycle.FAILED
        val degraded = degradedLevelOf(d.id)
        val notResourceOffline = degraded != DegradationLevel.OFFLINE && degraded != DegradationLevel.UNAVAILABLE
        return d.enabled && d.availability != Availability.OFFLINE && runnableState && notResourceOffline
    }

    private fun permissionsOk(capability: Capability, granted: Set<CapabilityPermission>): Boolean =
        capability.descriptor.requiredPermissions.all { it in granted }

    private fun depsOk(capability: Capability): Boolean {
        val d = capability.descriptor
        return d.dependencies.all { dep ->
            val depCap = registry.get(dep)
            depCap != null && depCap.isEligible()
        }
    }

    /** Deterministic reason when nothing is eligible — precedence: permission,
     *  dependency, unavailable, disabled (first candidate order is sorted). */
    private fun bestFailureReason(candidates: List<Capability>, granted: Set<CapabilityPermission>): Resolution.Failure {
        val reasons = mutableListOf<ResolutionReason>()
        val disabled = candidates.filter { !it.descriptor.enabled }
        val offline = candidates.filter { it.descriptor.availability == Availability.OFFLINE }
        val noPerm = candidates.filter { it.descriptor.requiredPermissions.any { p -> p !in granted } }
        val noDep = candidates.filter { c -> !depsOk(c) }

        if (noPerm.isNotEmpty()) reasons.add(ResolutionReason.PERMISSION_DENIED)
        if (noDep.isNotEmpty()) reasons.add(ResolutionReason.DEPENDENCY_UNAVAILABLE)
        if (offline.isNotEmpty()) reasons.add(ResolutionReason.CAPABILITY_UNAVAILABLE)
        if (disabled.isNotEmpty()) reasons.add(ResolutionReason.CAPABILITY_DISABLED)

        val reason = reasons.firstOrNull() ?: ResolutionReason.CAPABILITY_UNAVAILABLE
        val detail = when (reason) {
            ResolutionReason.PERMISSION_DENIED ->
                "capability requires permissions not granted: ${noPerm.joinToString(",") { it.descriptor.id }}"
            ResolutionReason.DEPENDENCY_UNAVAILABLE ->
                "dependency unavailable: ${noDep.joinToString(",") { it.descriptor.id }}"
            else -> null
        }
        return Resolution.Failure(reason, detail)
    }
}
