package com.jarvis.app.nervous

import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.microsystem.CallableMicroSystem
import com.jarvis.app.microsystem.MicroSystemContract
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.microsystem.OperationResult
import com.jarvis.app.microsystem.SpecMicroSystem

/**
 * Capability Router (§9 routing layer, §17 model agnosticism): decides which
 * organism handles an event, preferring the *cheapest valid* implementation.
 *
 * Cost tiers follow §3: deterministic logic (0) → cached (1) → heuristics (2)
 * → model-backed (3+). A deterministic local algorithm is always routed ahead
 * of an LLM for the same capability. The router never performs the work — it
 * hands the request to the organism through [CallableMicroSystem.call].
 *
 * When nothing is registered for a capability the result carries
 * [RouteResult.gapDetected] so the nervous system can trigger the evolution
 * layer (§15). A provider that fails is skipped loudly (canonical surface) and
 * the next-cheapest is tried — degraded operation, never silent failure.
 */
class CapabilityRouter(
    private val registry: MicroSystemRegistry,
    private val failureSurface: FailureSurface? = null
) {

    /** «Who provides this capability?» — cheapest-valid first (§17). */
    fun providers(capability: String): List<MicroSystemContract> =
        registry.provides(capability).sortedBy { costTier(it) }

    fun hasProvider(capability: String): Boolean = registry.provides(capability).isNotEmpty()

    /** Route a request to the cheapest valid provider with fallback. */
    suspend fun route(capability: String, inputs: Map<String, Any>): RouteResult {
        val candidates = providers(capability)
        if (candidates.isEmpty()) {
            return RouteResult(success = false, capability = capability, gapDetected = true)
        }
        var lastError: String? = null
        for (system in candidates) {
            val callable = system as? CallableMicroSystem
            if (callable == null) {
                failureSurface?.report(
                    FailureReport(
                        subsystem = "NERVOUS",
                        operation = "route:$capability",
                        severity = FailureSeverity.RECOVERABLE,
                        category = FailureCategory.SYNCHRONIZATION,
                        message = "Provider ${system.id} is not callable",
                        source = "CapabilityRouter"
                    )
                )
                continue
            }
            val result = callable.call(inputs)
            if (result.success) {
                return RouteResult(
                    success = true,
                    capability = capability,
                    providerId = system.id,
                    result = result
                )
            }
            lastError = result.error ?: "no error"
            failureSurface?.report(
                FailureReport(
                    subsystem = "NERVOUS",
                    operation = "route:$capability",
                    severity = FailureSeverity.RECOVERABLE,
                    category = FailureCategory.SYNCHRONIZATION,
                    message = "Provider ${system.id} failed: $lastError",
                    source = "CapabilityRouter"
                )
            )
        }
        return RouteResult(
            success = false,
            capability = capability,
            providerId = candidates.firstOrNull()?.id,
            lastError = lastError
        )
    }

    /**
     * Cheapest-valid cost tier for a provider (§3, §17). A spec-backed
     * organism reports its strategy; anything model-bearing is expensive.
     */
    fun costTier(system: MicroSystemContract): Int {
        val spec = (system as? SpecMicroSystem)?.spec
        if (spec != null) {
            return when (spec.strategy) {
                com.jarvis.app.mutant.AlgorithmSpec.Strategy.DETERMINISTIC -> 0
                com.jarvis.app.mutant.AlgorithmSpec.Strategy.CACHED -> 1
                com.jarvis.app.mutant.AlgorithmSpec.Strategy.HEURISTIC -> 2
            }
        }
        return if (system.genome.modelRequirements.models.isEmpty()) 0 else 3
    }
}

/** Outcome of routing one capability request. */
data class RouteResult(
    val success: Boolean,
    val capability: String,
    val gapDetected: Boolean = false,
    val providerId: String? = null,
    val result: OperationResult? = null,
    val lastError: String? = null
)
