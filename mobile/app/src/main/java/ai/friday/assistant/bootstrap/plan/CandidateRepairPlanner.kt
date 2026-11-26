package ai.friday.assistant.bootstrap.plan

import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts a failed, low-risk Builder candidate into one bounded follow-up
 * task. Failure evidence is retained as untrusted data; it never grants new
 * permissions or expands the original task's authority.
 */
internal object CandidateRepairPlanner {
    const val MAX_REPAIR_ATTEMPTS = 2
    const val MAX_REPAIR_CONTEXT_CHARS = 24_000

    fun createRepairTask(
        failedTask: JSONObject,
        failureEvidence: JSONObject,
        nowEpochMs: Long = System.currentTimeMillis()
    ): JSONObject? {
        val failedTaskId = failedTask.optString("task_id")
        val candidate = failedTask.optJSONObject("candidate") ?: return null
        val candidateSha = candidate.optString("commit_sha")
        if (!failedTaskId.matches(Regex("[A-Za-z0-9._-]{3,80}")) ||
            !candidateSha.matches(Regex("[0-9a-f]{40}"))
        ) return null

        if (!hasActionableFailureEvidence(failureEvidence, candidateSha)) return null

        // Never silently widen the authority of a candidate that required
        // owner approval or was not originally classified as low-risk.
        if (failedTask.optString("risk") != "LOW" ||
            !failedTask.optBoolean("reversible", false) ||
            failedTask.optBoolean("requires_owner_approval", true)
        ) return null

        val attempt = failedTask.optInt("repair_attempt", 0) + 1
        if (attempt > MAX_REPAIR_ATTEMPTS) return null
        val taskId = (failedTaskId.take(70) + "-repair-" + attempt).take(80)
        val rootTaskId = failedTask.optString("repair_root_task_id").ifBlank { failedTaskId }
        val repairContext = JSONObject()
            .put("schema_version", 1)
            .put("failed_task_id", failedTaskId)
            .put("repair_root_task_id", rootTaskId)
            .put("repair_attempt", attempt)
            .put("failed_candidate_commit_sha", candidateSha)
            .put("candidate", JSONObject()
                .put("commit_sha", candidateSha)
                .put("number", candidate.opt("number") ?: JSONObject.NULL)
                .put("url", candidate.optString("url"))
                .put("branch", candidate.optString("branch")))
            .put("failure_evidence", compactFailureEvidence(failureEvidence))
        val boundedContext = boundRepairContext(repairContext)

        val acceptance = JSONArray(
            failedTask.optJSONArray("acceptance_criteria")?.toString() ?: "[]"
        ).put("Address the recorded failure without weakening or deleting the original acceptance criteria.")
            .put("Re-run the exact failing checks and add a regression test that would fail before the repair.")
            .put("Preserve the original low-risk, reversible scope; do not change protected policy, signing, permissions or release controls.")

        val evidenceNeeded = JSONArray(
            failedTask.optJSONArray("evidence_needed")?.toString() ?: "[]"
        ).put("Correlate CI and isolated sandbox failure evidence with the exact failed candidate diff.")
            .put("Use REA findings only when present, provenance-bearing and relevant to the exact candidate APK.")
            .put("Record the exact regression test result and candidate commit identity.")

        return JSONObject()
            .put("task_id", taskId)
            .put("parent_task_id", failedTask.optString("parent_task_id"))
            .put("repair_of_task_id", failedTaskId)
            .put("repair_root_task_id", rootTaskId)
            .put("repair_attempt", attempt)
            .put("objective_id", failedTask.optString("objective_id"))
            .put("phase_id", failedTask.opt("phase_id") ?: JSONObject.NULL)
            .put("task_type", "BOUNDED_DEVELOPMENT_STEP")
            .put("goal", "Repair failed Builder candidate $candidateSha. Diagnose the recorded CI/sandbox failure, correlate the candidate diff and any exact-artifact REA evidence, make the smallest reversible correction, and add a regression test.")
            .put("acceptance_criteria", acceptance)
            .put("evidence_needed", evidenceNeeded)
            .put("repair_context", boundedContext)
            .put("risk", "LOW")
            .put("reversible", true)
            .put("requires_owner_approval", false)
            .put("status", "QUEUED")
            .put("executor", "LOCAL_BUILDER")
            .put("requires_repository_access", true)
            .put("dependencies", JSONArray())
            .put("dependency_state", "REPAIR_OF_FAILED_CANDIDATE")
            .put("source_model_id", failedTask.optString("source_model_id"))
            .put("checkpoint", JSONObject()
                .put("stage", "REPAIR_QUEUED_FROM_VERIFIED_FAILURE")
                .put("updated_at_epoch_ms", nowEpochMs))
            .put("created_at_epoch_ms", nowEpochMs)
            .put("updated_at_epoch_ms", nowEpochMs)
    }

    internal data class SandboxDisposition(
        val taskStatus: String,
        val queueRepair: Boolean
    )

    internal fun classifySandboxOutcome(
        candidateSha: String,
        ciFailed: Boolean,
        evidence: JSONObject
    ): SandboxDisposition {
        val summary = evidence.optJSONObject("summary") ?: JSONObject()
        val actionableDefect = isConfirmedCandidateDefect(candidateSha, evidence)
        val infrastructureFailure = summary.optString("failure_classification") == "INFRASTRUCTURE"
        val passed = evidence.optString("status") == "PASS" && !ciFailed
        val taskStatus = when {
            passed -> "SANDBOX_PASSED_AWAITING_REVIEW"
            actionableDefect -> "SANDBOX_FAILED_REQUIRES_REPAIR"
            infrastructureFailure -> "INFRASTRUCTURE_TRIAGE_REQUIRED"
            ciFailed && evidence.optString("status") == "PASS" -> "CI_FAILURE_NOT_REPRODUCED"
            else -> "FAILURE_TRIAGE_REQUIRED"
        }
        return SandboxDisposition(taskStatus = taskStatus, queueRepair = actionableDefect)
    }

    internal fun isConfirmedCandidateDefect(candidateSha: String, sandbox: JSONObject): Boolean {
        val summary = sandbox.optJSONObject("summary") ?: return false
        val apkEvidenceValid = summary.optString("apk_sha256").matches(Regex("[0-9a-f]{64}"))
        val compilerEvidenceValid =
            summary.optString("failure_evidence_kind") == "COMPILER" &&
                summary.optInt("compiler_diagnostic_count") > 0
        val testEvidenceValid =
            summary.optString("failure_evidence_kind") in setOf("TEST", "MIXED") &&
                summary.optInt("test_failure_count") > 0
        val lintEvidenceValid =
            summary.optString("failure_evidence_kind") in setOf("LINT", "MIXED") &&
                summary.optInt("lint_error_count") > 0
        return candidateSha.matches(Regex("[0-9a-f]{40}")) &&
            sandbox.optString("status") == "FAIL" &&
            summary.optString("expected_commit_sha") == candidateSha &&
            summary.optString("checked_out_commit_sha") == candidateSha &&
            summary.optString("test_profile") == "android-debug" &&
            summary.optString("failure_classification") == "ACTIONABLE_DEFECT" &&
            (apkEvidenceValid || compilerEvidenceValid || testEvidenceValid || lintEvidenceValid)
    }

    private fun hasActionableFailureEvidence(source: JSONObject, candidateSha: String): Boolean {
        val sandbox = source.optJSONObject("sandbox") ?: JSONObject()
        // A red CI check or a non-empty log excerpt is not proof of a product
        // defect. Require the exact candidate's independently classified build
        // evidence. An independent reviewer may also dispute a green candidate.
        val sandboxDefect = isConfirmedCandidateDefect(candidateSha, sandbox)
        val reviewDisputed = source.optJSONObject("review")?.optString("stance") in setOf("DISPUTE", "ADD")
        return sandboxDefect || reviewDisputed
    }

    private fun boundRepairContext(context: JSONObject): String {
        val evidence = context.getJSONObject("failure_evidence")
        val sandbox = evidence.getJSONObject("sandbox")
        val review = evidence.getJSONObject("review")
        val candidates = listOf(
            sandbox to "failure_excerpt",
            sandbox to "rea_evidence_excerpt",
            evidence to "candidate_diff",
            sandbox to "reason",
            review to "stance_reason"
        )
        var encoded = context.toString()
        while (encoded.length > MAX_REPAIR_CONTEXT_CHARS) {
            val largest = candidates
                .mapNotNull { (objectValue, key) ->
                    val value = objectValue.optString(key)
                    if (value.isBlank()) null else Triple(objectValue, key, value)
                }
                .maxByOrNull { it.third.length }
                ?: break
            val reduction = maxOf(256, encoded.length - MAX_REPAIR_CONTEXT_CHARS)
            val retained = (largest.third.length - reduction).coerceAtLeast(0)
            largest.first.put(largest.second, largest.third.take(retained))
            encoded = context.toString()
        }
        require(encoded.length <= MAX_REPAIR_CONTEXT_CHARS) {
            "Repair evidence metadata exceeds its persistence budget."
        }
        return encoded
    }

    private fun compactFailureEvidence(source: JSONObject): JSONObject {
        val ci = source.optJSONObject("ci") ?: JSONObject()
        val sandbox = source.optJSONObject("sandbox") ?: JSONObject()
        val summary = sandbox.optJSONObject("summary") ?: JSONObject()
        val review = source.optJSONObject("review") ?: JSONObject()
        val details = JSONArray()
        val sourceDetails = ci.optJSONArray("details") ?: JSONArray()
        val classificationReasons = JSONArray()
        val sourceReasons = summary.optJSONArray("failure_classification_reasons") ?: JSONArray()
        for (index in 0 until minOf(sourceReasons.length(), 12)) {
            classificationReasons.put(sourceReasons.optString(index).take(240))
        }
        val compilerDiagnostics = JSONArray()
        val sourceCompilerDiagnostics = summary.optJSONArray("compiler_diagnostics") ?: JSONArray()
        for (index in 0 until minOf(sourceCompilerDiagnostics.length(), 5)) {
            compilerDiagnostics.put(sourceCompilerDiagnostics.optString(index).take(500))
        }
        val testFailures = JSONArray()
        val sourceTestFailures = summary.optJSONArray("test_failures") ?: JSONArray()
        for (index in 0 until minOf(sourceTestFailures.length(), 5)) {
            val failure = sourceTestFailures.optJSONObject(index) ?: continue
            testFailures.put(JSONObject()
                .put("test", failure.optString("test").take(240))
                .put("kind", failure.optString("kind").take(16))
                .put("detail", failure.optString("detail").take(400)))
        }
        val lintErrors = JSONArray()
        val sourceLintErrors = summary.optJSONArray("lint_errors") ?: JSONArray()
        for (index in 0 until minOf(sourceLintErrors.length(), 5)) {
            val issue = sourceLintErrors.optJSONObject(index) ?: continue
            lintErrors.put(JSONObject()
                .put("id", issue.optString("id").take(120))
                .put("message", issue.optString("message").take(300)))
        }
        for (index in 0 until minOf(sourceDetails.length(), 20)) {
            details.put(sourceDetails.optString(index).take(240))
        }
        return JSONObject()
            .put("trigger", source.optString("trigger").take(80))
            .put("ci", JSONObject()
                .put("state", ci.optString("state").take(20))
                .put("total", ci.optInt("total"))
                .put("completed", ci.optInt("completed"))
                .put("failed", ci.optInt("failed"))
                .put("details", details))
            .put("sandbox", JSONObject()
                .put("status", sandbox.optString("status").take(20))
                .put("reason", sandbox.optString("reason").take(600))
                .put("run_id", sandbox.opt("run_id") ?: JSONObject.NULL)
                .put("run_url", sandbox.optString("run_url").take(300))
                .put("test_profile", summary.optString("test_profile").take(80))
                .put("expected_commit_sha", summary.optString("expected_commit_sha").take(40))
                .put("checked_out_commit_sha", summary.optString("checked_out_commit_sha").take(40))
                .put("apk_sha256", summary.optString("apk_sha256").take(64))
                .put("failure_excerpt", summary.optString("failure_excerpt").take(5_000))
                .put("failure_classification", summary.optString("failure_classification", "UNKNOWN").take(32))
                .put("failure_evidence_kind", summary.optString("failure_evidence_kind", "NONE").take(24))
                .put("compiler_diagnostic_count", summary.optInt("compiler_diagnostic_count"))
                .put("test_failure_count", summary.optInt("test_failure_count"))
                .put("lint_error_count", summary.optInt("lint_error_count"))
                .put("failure_classification_reasons", classificationReasons)
                .put("compiler_diagnostics", compilerDiagnostics)
                .put("test_failures", testFailures)
                .put("lint_errors", lintErrors)
                .put("rea_status", summary.optString("rea_status").take(20))
                .put("rea_report_sha256", if (summary.optString("rea_status") == "COMPLETED") summary.optString("rea_report_sha256").take(64) else "")
                .put("rea_evidence_excerpt", if (summary.optString("rea_status") == "COMPLETED") summary.optString("rea_evidence_excerpt").take(7_000) else ""))
            .put("review", JSONObject()
                .put("stance", review.optString("stance").take(20))
                .put("stance_reason", review.optString("stance_reason").take(500)))
            .put("candidate_diff", source.optString("candidate_diff")
                .ifBlank { sandbox.optString("candidate_diff") }
                .ifBlank { review.optString("candidate_diff") }.take(8_000))
    }
}
