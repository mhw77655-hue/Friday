package com.jarvis.app.rea

import com.jarvis.app.cognitive.capability.Availability
import com.jarvis.app.cognitive.capability.Capability
import com.jarvis.app.cognitive.capability.CapabilityDescriptor
import com.jarvis.app.cognitive.capability.CapabilityResourceProfile
import com.jarvis.app.cognitive.capability.RiskLevel
import com.jarvis.app.cognitive.execution.ActionFailure
import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.cognitive.execution.ExecutionResult
import org.json.JSONArray
import org.json.JSONObject

/** Explicitly invoked REA capability. APK inspection never silently uploads binaries or installs code. */
class ReverseEngineeringCapability(
    private val agent: ReverseEngineeringAgent
) : Capability {
    override val descriptor = CapabilityDescriptor(
        id = "friday.reverse_engineering",
        name = "Friday Reverse Engineering Agent",
        description = "Inspect an installed app's APK splits through Shizuku-Next, inventory structure, and optionally ask configured AI advisers for a Friday-compatible capability recipe.",
        category = "development",
        resourceProfile = CapabilityResourceProfile(cpuCost = 0.12f, memoryCostMb = 48, optional = true),
        riskLevel = RiskLevel.HIGH,
        supportedOperations = setOf("rea.analyze_installed_package"),
        availability = Availability.ONLINE
    )

    override suspend fun invoke(request: ActionRequest): ExecutionResult {
        if (request.action != "rea.analyze_installed_package") {
            return ExecutionResult.fail(ActionFailure.Unsupported(request.action))
        }
        val packageName = request.context["package_name"]?.trim().orEmpty()
        if (packageName.isBlank()) {
            return failure("REA_PACKAGE_REQUIRED", "Provide the Android package name to inspect.")
        }
        val allowCloud = when (request.context["allow_cloud_analysis"]?.trim()?.lowercase()) {
            "true" -> true
            "false", null, "" -> false
            else -> return failure("REA_CLOUD_CONSENT_INVALID", "Cloud analysis consent must be explicitly true or false.")
        }
        val buildCapability = when (request.context["build_compatible_capability"]?.trim()?.lowercase()) {
            "true" -> true
            "false", null, "" -> false
            else -> return failure("REA_BUILD_FLAG_INVALID", "Capability build flag must be explicitly true or false.")
        }
        val submitCloudProposal = when (request.context["submit_cloud_proposal"]?.trim()?.lowercase()) {
            "true" -> true
            "false", null, "" -> false
            else -> return failure("REA_CLOUD_SUBMIT_FLAG_INVALID", "Cloud proposal flag must be explicitly true or false.")
        }
        if (submitCloudProposal && !allowCloud) {
            return failure("REA_CLOUD_SUBMIT_REQUIRES_RECIPE", "Cloud proposal submission requires an AI-reviewed recipe.")
        }
        if (buildCapability && !allowCloud) {
            return failure("REA_BUILD_REQUIRES_RECIPE", "Building requires an AI-reviewed compatibility recipe.")
        }
        return try {
            val result = agent.analyzeInstalledPackage(packageName, allowCloud, buildCapability, submitCloudProposal)
            val reports = JSONArray(result.reports.map { it.toJson() })
            val output = JSONObject()
                .put("packageName", result.packageName)
                .put("apkReports", reports)
                .put("recipe", result.recipe?.toJson() ?: JSONObject.NULL)
                .put("cloudAnalysisAttempted", result.cloudAnalysisAttempted)
                .put("buildStatus", result.buildStatus ?: JSONObject.NULL)
                .put("buildMessage", result.buildMessage ?: JSONObject.NULL)
                .put("builtCapabilityId", result.builtCapabilityId ?: JSONObject.NULL)
                .put("cloudProposalAccepted", result.cloudProposalAccepted)
                .put("cloudProposalRequestId", result.cloudProposalRequestId ?: JSONObject.NULL)
                .put("cloudProposalUrl", result.cloudProposalUrl ?: JSONObject.NULL)
                .put("cloudProposalMessage", result.cloudProposalMessage ?: JSONObject.NULL)
                .put("warnings", JSONArray(result.warnings))
            ExecutionResult.ok(
                output = mapOf("rea_result_json" to output.toString()),
                evidence = mapOf("apk_split_count" to result.reports.size.toString(),
                    "recipe_created" to (result.recipe != null).toString())
            )
        } catch (e: Exception) {
            failure("REA_ANALYSIS_FAILED", e.message ?: "Reverse engineering failed.")
        }
    }

    private fun failure(code: String, message: String) =
        ExecutionResult.fail(ActionFailure.Error(code, message, recoverable = true))
}
