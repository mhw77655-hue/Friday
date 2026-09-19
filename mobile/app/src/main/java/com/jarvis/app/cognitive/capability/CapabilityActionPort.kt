package com.jarvis.app.cognitive.capability

import com.jarvis.app.cognitive.execution.ActionFailure
import com.jarvis.app.cognitive.execution.ActionPort
import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.cognitive.execution.ExecutionResult

/**
 * 01F — the capability-aware execution adapter.
 *
 * The execution layer (01C ExecutionEngine) stays capability-agnostic; this port
 * is the single seam that routes its requests through the Capability Fabric:
 *
 *   ActionRequest → CapabilityFabric → resolve → invoke → ExecutionResult
 *
 * Resolution failures are encoded as **non-recoverable** ExecutionResult
 * failures, so the engine never retries a missing / disabled / forbidden
 * capability. A circuit-open refusal maps to its own code (also non-retryable —
 * the immune layer owns backoff, not the engine). No capability selection logic
 * lives here beyond delegating to the fabric's resolver + invoker.
 */
class CapabilityActionPort(
    private val fabric: CapabilityFabric,
    private val granted: Set<CapabilityPermission> = emptySet(),
    private val timeoutMs: Long? = null
) : ActionPort {

    override suspend fun execute(request: ActionRequest): ExecutionResult {
        val invocation = fabric.invoker.invoke(request, granted, timeoutMs)
        return toExecutionResult(invocation)
    }

    companion object {
        /** Translate a fabric invocation into the execution layer's result
         *  vocabulary. Shared with [CapabilityExecutor]. */
        fun toExecutionResult(invocation: CapabilityInvocation): ExecutionResult = when {
            // Immune refusal — the breaker is open; the immune layer owns when
            // a probe may run. Not retryable by the engine.
            invocation.refused -> ExecutionResult.fail(
                ActionFailure.Error("CIRCUIT_OPEN", invocation.refusalReason ?: "circuit open", recoverable = false)
            )
            // The capability ran and returned a structured result (success or
            // failure) — pass it through unchanged.
            invocation.invoked -> invocation.result!!
            // Resolution never produced a runnable capability.
            else -> {
                val reason = (invocation.resolution as? Resolution.Failure)?.reason
                    ?: ResolutionReason.OPERATION_NOT_FOUND
                ExecutionResult.fail(
                    ActionFailure.Error(
                        "RESOLUTION_${reason.name}",
                        reason.name,
                        recoverable = false
                    )
                )
            }
        }
    }
}
