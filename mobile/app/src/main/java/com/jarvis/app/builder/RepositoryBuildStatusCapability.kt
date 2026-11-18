package com.jarvis.app.builder

import com.jarvis.app.cognitive.capability.Availability
import com.jarvis.app.cognitive.capability.Capability
import com.jarvis.app.cognitive.capability.CapabilityDescriptor
import com.jarvis.app.cognitive.capability.CapabilityPermission
import com.jarvis.app.cognitive.capability.CapabilityResourceProfile
import com.jarvis.app.cognitive.capability.RiskLevel
import com.jarvis.app.cognitive.execution.ActionFailure
import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.cognitive.execution.ExecutionResult

/** Read-only observation of a previously dispatched repository build request. */
class RepositoryBuildStatusCapability(
    private val settings: RepositoryBuildSettings,
    private val dispatchClient: GitHubWorkflowDispatchClient
) : Capability {
    override val descriptor = CapabilityDescriptor(
        id = "friday.repository_builder.status",
        name = "Friday Repository Build Status",
        description = "Read the status of a previously submitted Friday GitHub Actions request. This operation cannot submit or modify work.",
        category = "development",
        requiredPermissions = setOf(CapabilityPermission.REPOSITORY_BUILD_STATUS),
        resourceProfile = CapabilityResourceProfile(cpuCost = 0.02f, memoryCostMb = 4, optional = true),
        riskLevel = RiskLevel.LOW,
        supportedOperations = setOf("builder.check_repository_request"),
        availability = Availability.ONLINE
    )

    override suspend fun invoke(request: ActionRequest): ExecutionResult {
        if (request.action != "builder.check_repository_request") {
            return ExecutionResult.fail(ActionFailure.Unsupported(request.action))
        }
        if (!settings.hasToken()) {
            return ExecutionResult.fail(
                ActionFailure.Error(
                    code = "REPOSITORY_BUILDER_NOT_CONFIGURED",
                    message = "Repository-builder credential is not configured.",
                    recoverable = false
                )
            )
        }
        val requestId = request.context["request_id"]
            ?: return ExecutionResult.fail(
                ActionFailure.Error(
                    code = "REPOSITORY_REQUEST_ID_MISSING",
                    message = "request_id is required to check a repository build.",
                    recoverable = false
                )
            )
        val status = dispatchClient.checkStatus(requestId)
        return ExecutionResult.ok(
            output = buildMap {
                put("request_id", requestId)
                put("status", status.state)
                put("message", status.message)
                status.runUrl?.let { put("run_url", it) }
                status.conclusion?.let { put("conclusion", it) }
            },
            evidence = mapOf("github_workflow_status" to status.state)
        )
    }
}
