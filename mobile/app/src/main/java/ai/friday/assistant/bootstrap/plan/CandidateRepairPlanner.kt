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
        val evidence = JSONObject(failureEvidence.toString())
        val repairContext = JSONObject()
            .put("schema_version", 1)
            .put("failed_task_id", failedTaskId)
            .put("repair_root_task_id", rootTaskId)
            .put("repair_attempt", attempt)
            .put("failed_candidate_commit_sha", candidateSha)
            .put("candidate", JSONObject(candidate.toString()))
            .put("failure_evidence", evidence)
        val boundedContext = repairContext.toString().take(MAX_REPAIR_CONTEXT_CHARS)

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
            .put("executor", "BUILDER_NOT_CONNECTED")
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
}
