package com.jarvis.app.model.remote

import com.jarvis.app.cognitive.capability.Availability
import com.jarvis.app.cognitive.capability.Capability
import com.jarvis.app.cognitive.capability.CapabilityDescriptor
import com.jarvis.app.cognitive.capability.CapabilityPermission
import com.jarvis.app.cognitive.execution.ActionFailure
import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.cognitive.execution.ExecutionResult

/**
 * Exposes external model consultation through Friday's existing capability
 * invocation boundary. The dedicated external-AI permission is owner-controlled.
 */
class RemoteAiCouncilCapability(
    private val councilProvider: () -> ProviderCouncil?
) : Capability {
    override val descriptor = CapabilityDescriptor(
        id = "friday.ai_council",
        name = "External AI advisers",
        description = "Consult an owner-enabled OpenAI, Claude, Gemini or compatible adviser. task is required. Optional: provider_hint (configured label), source_paths (up to 3 approved paths). For explicit multi-adviser review set independent_review=true and max_advisers=2..3; default is one adviser. max_output_tokens=16..4096.",
        category = "reasoning.external_advice",
        requiredPermissions = setOf(CapabilityPermission.EXTERNAL_AI),
        supportedOperations = setOf("ai.council.consult"),
        availability = Availability.ONLINE
    )

    override suspend fun invoke(request: ActionRequest): ExecutionResult {
        if (request.action != "ai.council.consult") {
            return ExecutionResult.fail(ActionFailure.Unsupported(request.action))
        }
        val task = request.context["task"]?.takeIf { it.isNotBlank() }
            ?: request.description.takeIf { it.isNotBlank() }
            ?: return ExecutionResult.fail(ActionFailure.Error(
                code = "COUNCIL_TASK_MISSING", message = "A specific adviser question is required."
            ))
        val independentReview = request.context["independent_review"] == "true"
        val maxAdvisers = request.context["max_advisers"]?.toIntOrNull() ?: 1
        val includeContext = request.context["include_project_context"] != "false"
        val council = councilProvider() ?: return ExecutionResult.fail(ActionFailure.Error(
            code = "COUNCIL_UNAVAILABLE", message = "Friday's external adviser service is not initialized.", recoverable = true
        ))
        val result = council.consult(CouncilRequest(
            task = task,
            includeProjectContext = includeContext,
            maxOutputTokens = request.context["max_output_tokens"]?.toIntOrNull() ?: 768,
            maxAdvisers = maxAdvisers,
            independentReviewRequested = independentReview,
            sourcePaths = request.context["source_paths"]
                ?.split(Regex("[\\n,;]+"))
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.take(3)
                .orEmpty(),
            providerHint = request.context["provider_hint"]
        ))
        if (!result.succeeded) return ExecutionResult.fail(ActionFailure.Error(
            code = "COUNCIL_NO_RESPONSE",
            message = result.blockedReason ?: "No configured adviser returned a response.",
            recoverable = true
        ))
        val combined = result.opinions.mapIndexed { index, opinion ->
            "ADVISER ${index + 1}: ${opinion.providerLabel} (${opinion.modelId})\n${opinion.answer}"
        }.joinToString("\n\n")
        return ExecutionResult.ok(
            output = mapOf(
                "advice" to combined,
                "adviser_count" to result.opinions.size.toString(),
                "providers" to result.opinions.joinToString(",") { it.providerId }
            ),
            evidence = mapOf("untrusted_advice" to "true", "memory_promoted" to "false")
        )
    }
}
