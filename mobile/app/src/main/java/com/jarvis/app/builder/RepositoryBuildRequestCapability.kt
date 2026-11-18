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
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * Converts a narrow technical brief into the repository's validated request
 * envelope, then dispatches only the trusted workflow. It never sends chat
 * transcripts, memory databases, provider credentials or arbitrary file data.
 */
class RepositoryBuildRequestCapability(
    private val settings: RepositoryBuildSettings,
    private val dispatchClient: GitHubWorkflowDispatchClient,
    private val now: () -> Instant = Instant::now,
    private val newRequestId: () -> String = {
        UUID.randomUUID().toString().replace("-", "").lowercase()
    }
) : Capability {
    override val descriptor = CapabilityDescriptor(
        id = "friday.repository_builder",
        name = "Friday Repository Builder",
        description = "Submit a bounded, privacy-reviewed technical development brief to the trusted GitHub Actions Builder. The phone token can dispatch only; it cannot write repository contents.",
        category = "development",
        requiredPermissions = setOf(CapabilityPermission.REPOSITORY_BUILD),
        resourceProfile = CapabilityResourceProfile(cpuCost = 0.05f, memoryCostMb = 8, optional = true),
        riskLevel = RiskLevel.HIGH,
        supportedOperations = setOf("builder.submit_repository_request"),
        availability = Availability.ONLINE
    )

    override suspend fun invoke(request: ActionRequest): ExecutionResult {
        if (request.action != "builder.submit_repository_request") {
            return ExecutionResult.fail(ActionFailure.Unsupported(request.action))
        }
        if (!settings.hasToken() || !settings.automaticRequestsEnabled()) {
            return failure("REPOSITORY_BUILDER_NOT_AUTHORIZED", "Owner has not enabled automatic repository requests.")
        }
        val raw = request.context["request_json"]
            ?: return failure("REPOSITORY_REQUEST_MISSING", "A structured technical request_json is required.")
        val brief = parseBrief(raw)
            ?: return failure(
                "REPOSITORY_REQUEST_INVALID",
                "Request must contain only intended_outcome, evidence, acceptance_criteria, risks, rollback_expectation and unresolved_questions."
            )

        val requestId = newRequestId()
        if (!GitHubWorkflowDispatchClient.REQUEST_ID.matches(requestId)) {
            return failure("REPOSITORY_REQUEST_ID_INVALID", "Could not create a valid request identifier.")
        }
        val timestamp = now().toString()
        val envelope = JSONObject()
            .put("schema_version", 1)
            .put("request_id", requestId)
            .put("created_at", timestamp)
            .put("source_revision", "main")
            .put("intended_outcome", brief.intendedOutcome)
            .put("evidence", JSONArray(brief.evidence))
            .put("acceptance_criteria", JSONArray(brief.acceptanceCriteria))
            .put("risks", JSONArray(brief.risks))
            .put("rollback_expectation", brief.rollbackExpectation)
            .put("unresolved_questions", JSONArray(brief.unresolvedQuestions))
            .put(
                "privacy_review",
                JSONObject()
                    .put("status", "approved")
                    .put("reviewed_by", "owner-enabled-on-device")
                    .put("reviewed_at", timestamp)
                    .put("aggregate_runtime_evidence_enabled", false)
                    .put("aggregate_runtime_evidence_scope", "none")
            )

        val result = dispatchClient.dispatch(requestId, envelope.toString())
        return if (result.accepted) {
            ExecutionResult.ok(
                output = mapOf(
                    "status" to "DISPATCHED",
                    "request_id" to requestId,
                    "workflow_url" to result.workflowUrl,
                    "message" to result.message
                ),
                evidence = mapOf("github_workflow_dispatch" to "accepted")
            )
        } else {
            ExecutionResult.fail(
                ActionFailure.Error(
                    code = "GITHUB_WORKFLOW_DISPATCH_FAILED",
                    message = result.message,
                    recoverable = true
                )
            )
        }
    }

    private fun parseBrief(raw: String): TechnicalBrief? = runCatching {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BRIEF_BYTES) return null
        val json = JSONObject(raw)
        val allowed = setOf(
            "intended_outcome",
            "evidence",
            "acceptance_criteria",
            "risks",
            "rollback_expectation",
            "unresolved_questions"
        )
        val keys = json.keys().asSequence().toSet()
        if (keys != allowed) return null

        val intendedOutcome = boundedString(json, "intended_outcome", 20, 4_000) ?: return null
        val evidence = boundedArray(json, "evidence", min = 1, max = 12, itemMax = 1_000) ?: return null
        val acceptance = boundedArray(json, "acceptance_criteria", min = 1, max = 12, itemMax = 600) ?: return null
        val risks = boundedArray(json, "risks", min = 0, max = 8, itemMax = 500) ?: return null
        val rollback = boundedString(json, "rollback_expectation", 10, 800) ?: return null
        val questions = boundedArray(json, "unresolved_questions", min = 0, max = 8, itemMax = 500) ?: return null

        val allText = listOf(intendedOutcome, rollback) + evidence + acceptance + risks + questions
        if (allText.any(::containsCredentialOrPersonalEmail)) return null
        TechnicalBrief(intendedOutcome, evidence, acceptance, risks, rollback, questions)
    }.getOrNull()

    private fun boundedString(json: JSONObject, key: String, min: Int, max: Int): String? {
        val value = json.optString(key).trim()
        return value.takeIf { it.length in min..max }
    }

    private fun boundedArray(
        json: JSONObject,
        key: String,
        min: Int,
        max: Int,
        itemMax: Int
    ): List<String>? {
        val array = json.optJSONArray(key) ?: return null
        if (array.length() !in min..max) return null
        val values = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isBlank() || value.length > itemMax) return null
            values += value
        }
        return values
    }

    private fun containsCredentialOrPersonalEmail(value: String): Boolean {
        val secretPatterns = listOf(
            Regex("(?i)github_pat_[A-Za-z0-9_]{20,}"),
            Regex("(?i)gh[pousr]_[A-Za-z0-9_]{20,}"),
            Regex("(?i)sk-[A-Za-z0-9_-]{20,}"),
            Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Regex("(?i)(password|api[_-]?key|access[_-]?token)\\s*[:=]\\s*\\S+"),
            Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
        )
        return secretPatterns.any { it.containsMatchIn(value) }
    }

    private fun failure(code: String, message: String) = ExecutionResult.fail(
        ActionFailure.Error(code = code, message = message, recoverable = false)
    )

    private data class TechnicalBrief(
        val intendedOutcome: String,
        val evidence: List<String>,
        val acceptanceCriteria: List<String>,
        val risks: List<String>,
        val rollbackExpectation: String,
        val unresolvedQuestions: List<String>
    )

    private companion object {
        const val MAX_BRIEF_BYTES = 24 * 1024
    }
}
