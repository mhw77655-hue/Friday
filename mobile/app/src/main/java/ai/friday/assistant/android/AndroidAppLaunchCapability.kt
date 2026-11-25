package ai.friday.assistant.android

import ai.friday.assistant.cognitive.capability.Availability
import ai.friday.assistant.cognitive.capability.Capability
import ai.friday.assistant.cognitive.capability.CapabilityDescriptor
import ai.friday.assistant.cognitive.capability.RiskLevel
import ai.friday.assistant.cognitive.execution.ActionFailure
import ai.friday.assistant.cognitive.execution.ActionRequest
import ai.friday.assistant.cognitive.execution.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * First Android app-operation exposed to Friday's shared CapabilityFabric.
 *
 * The package must be visible as an installed launcher application. The
 * package name is validated before it reaches the typed Shizuku service; no
 * command, shell fragment, or arbitrary intent crosses the capability edge.
 * Success requires both the backend's launch readback and a second independent
 * foreground-package query from CapabilityInvoker.verifyPostcondition().
 */
class AndroidAppLaunchCapability(
    private val packageIsLaunchable: (String) -> Boolean,
    private val foregroundVerifier: suspend (String) -> Boolean,
    private val routerProvider: () -> DeviceControlRouter?,
    private val riskGateProvider: () -> RiskGate?
) : Capability {

    override val descriptor = CapabilityDescriptor(
        id = "android.app_launch",
        name = "Android app launch",
        description = "Operation app.launch: set context argument arg:package_name to an installed launcher package ID. Launch that app and independently verify its foreground activity.",
        category = "device.app",
        riskLevel = RiskLevel.LOW,
        supportedOperations = setOf(OPERATION),
        availability = Availability.ONLINE
    )

    override suspend fun invoke(request: ActionRequest): ExecutionResult = withContext(Dispatchers.IO) {
        if (request.action != OPERATION) {
            return@withContext ExecutionResult.fail(ActionFailure.Unsupported(request.action))
        }
        val packageName = packageName(request)
            ?: return@withContext failure("APP_PACKAGE_REQUIRED", "A package_name argument is required.")
        if (!PACKAGE_NAME.matches(packageName)) {
            return@withContext failure("APP_PACKAGE_INVALID", "The requested package name is malformed.")
        }
        if (!runCatching { packageIsLaunchable(packageName) }.getOrDefault(false)) {
            return@withContext failure(
                "APP_NOT_LAUNCHABLE",
                "The package is not visible as an installed launcher application."
            )
        }

        val router = routerProvider()
            ?: return@withContext failure("DEVICE_CONTROL_UNAVAILABLE", "The Android device-control router is not initialized.")
        val gate = riskGateProvider()
            ?: return@withContext failure("RISK_GATE_UNAVAILABLE", "The Android risk gate is not initialized.")

        val action = ControlAction(
            id = request.stepId,
            actionType = ActionType.LAUNCH_APP,
            packageName = packageName,
            riskTier = RiskTier.LOW
        )
        when (val admission = gate.admit(action)) {
            is RiskGate.GateOutcome.Blocked ->
                return@withContext failure("RISK_GATE_BLOCKED", admission.reason)
            is RiskGate.GateOutcome.Allowed -> Unit
        }

        val execution = router.executeWithEvidence(action)
        val winner = execution.winner
            ?: return@withContext ExecutionResult.fail(
                ActionFailure.Error(
                    code = "APP_LAUNCH_UNVERIFIED",
                    message = "No Android backend verified the requested app launch.",
                    recoverable = true,
                    evidence = execution.attempts.associate { it.backendId to (it.error ?: it.evidence.detail) }
                )
            )
        val evidence = execution.attempts.lastOrNull { it.backendId == winner.id }?.evidence
            ?: return@withContext failure("APP_LAUNCH_EVIDENCE_MISSING", "The winning backend has no matching evidence.")
        if (!evidence.isComplete || evidence.backendId != winner.id) {
            return@withContext failure("APP_LAUNCH_EVIDENCE_INCOMPLETE", "The backend did not return verified launch evidence.")
        }

        ExecutionResult.ok(
            output = mapOf(
                "package_name" to packageName,
                "backend_id" to winner.id,
                "verified" to "true"
            ),
            evidence = mapOf(
                "package_name" to packageName,
                "backend_id" to evidence.backendId,
                "verification_stage" to evidence.stage.name,
                "detail" to evidence.detail.take(160)
            )
        )
    }

    override suspend fun verifyPostcondition(
        request: ActionRequest,
        result: ExecutionResult
    ): Boolean {
        if (!result.success || request.action != OPERATION) return false
        val packageName = packageName(request) ?: return false
        if (!PACKAGE_NAME.matches(packageName)) return false
        if (result.output["package_name"] != packageName || result.evidence["package_name"] != packageName) return false
        val backendId = result.output["backend_id"] ?: return false
        if (result.evidence["backend_id"] != backendId) return false
        if (result.output["verified"] != "true") return false
        if (result.evidence["verification_stage"] != VerificationStage.VERIFIED.name) return false
        return runCatching { foregroundVerifier(packageName) }.getOrDefault(false)
    }

    private fun packageName(request: ActionRequest): String? =
        request.context["arg:package_name"]
            ?: request.context["package_name"]
            ?: request.context["packageName"]

    private fun failure(code: String, message: String): ExecutionResult =
        ExecutionResult.fail(ActionFailure.Error(code, message, recoverable = false))

    companion object {
        const val OPERATION = "app.launch"
        private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
    }
}
