package com.jarvis.app.android

import com.jarvis.app.cognitive.capability.Availability
import com.jarvis.app.cognitive.capability.Capability
import com.jarvis.app.cognitive.capability.CapabilityDescriptor
import com.jarvis.app.cognitive.execution.ActionFailure
import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.cognitive.execution.ExecutionResult

/**
 * Bridges the verified Android media-control backend into Friday's common
 * CapabilityFabric. Only low-risk media operations are exposed here; actions
 * requiring confirmation or biometric authorization must use the owner-gated
 * approval path and are never authorized by model-generated parameters.
 *
 * Providers are resolved lazily because JarvisEngine finishes constructing the
 * Android backend stack after TurnPathAssembly registers this capability.
 */
class AndroidMediaControlCapability(
    private val routerProvider: () -> DeviceControlRouter?,
    private val riskGateProvider: () -> RiskGate?
) : Capability {

    override val descriptor = CapabilityDescriptor(
        id = "android.media_control",
        name = "Android media control",
        description = "Control the active Android media session and verify the resulting backend action.",
        category = "device.media",
        supportedOperations = OPERATIONS,
        availability = Availability.ONLINE
    )

    override suspend fun invoke(request: ActionRequest): ExecutionResult {
        val actionType = ACTIONS[request.action]
            ?: return ExecutionResult.fail(ActionFailure.Unsupported(request.action))
        val router = routerProvider()
            ?: return ExecutionResult.fail(
                ActionFailure.Error(
                    code = "DEVICE_CONTROL_UNAVAILABLE",
                    message = "The Android device-control router is not initialized.",
                    recoverable = true
                )
            )
        val action = ControlAction(
            id = request.stepId,
            actionType = actionType,
            riskTier = RiskTier.LOW
        )
        val gate = riskGateProvider()
            ?: return ExecutionResult.fail(
                ActionFailure.Error(
                    code = "RISK_GATE_UNAVAILABLE",
                    message = "The Android risk gate is not initialized."
                )
            )
        when (val admission = gate.admit(action)) {
            is RiskGate.GateOutcome.Blocked -> return ExecutionResult.fail(
                ActionFailure.Error(
                    code = "RISK_GATE_BLOCKED",
                    message = admission.reason,
                    evidence = mapOf("risk_tier" to admission.tier.name)
                )
            )
            is RiskGate.GateOutcome.Allowed -> Unit
        }

        val execution = router.executeWithEvidence(action)
        val winner = execution.winner
            ?: return ExecutionResult.fail(
                ActionFailure.Error(
                    code = "DEVICE_ACTION_UNVERIFIED",
                    message = "No Android backend could verify the requested media action.",
                    recoverable = true,
                    evidence = execution.attempts.associate { attempt ->
                        attempt.backendId to (attempt.error ?: attempt.evidence.detail)
                    }
                )
            )
        val evidence = execution.attempts.lastOrNull { it.backendId == winner.id }?.evidence
            ?: return ExecutionResult.fail(
                ActionFailure.Error(
                    code = "DEVICE_EVIDENCE_MISSING",
                    message = "The backend returned without a corresponding verification record."
                )
            )
        if (!evidence.isComplete) {
            return ExecutionResult.fail(
                ActionFailure.Error(
                    code = "DEVICE_EVIDENCE_INCOMPLETE",
                    message = "The Android backend did not verify the requested action.",
                    recoverable = true,
                    evidence = mapOf("backend" to winner.id, "stage" to evidence.stage.name)
                )
            )
        }
        return ExecutionResult.ok(
            output = mapOf(
                "backend_id" to winner.id,
                "verified" to "true"
            ),
            evidence = mapOf(
                "backend_id" to winner.id,
                "verification_stage" to evidence.stage.name,
                "detail" to evidence.detail
            )
        )
    }

    companion object {
        val OPERATIONS = setOf(
            "media.play",
            "media.pause",
            "media.next_track",
            "media.previous_track"
        )

        private val ACTIONS = mapOf(
            "media.play" to ActionType.PLAY,
            "media.pause" to ActionType.PAUSE,
            "media.next_track" to ActionType.NEXT_TRACK,
            "media.previous_track" to ActionType.PREV_TRACK
        )
    }
}
