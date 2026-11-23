package ai.friday.assistant.bootstrap.plan

import org.json.JSONArray
import org.json.JSONObject

/**
 * A child task may run only after every declared prerequisite has an explicit
 * ACCEPTED ledger state. Missing dependencies and merely published/CI-passed
 * candidates remain blocked; a candidate is not an accepted capability.
 */
internal object ObjectiveTaskDependencyGate {
    fun areSatisfied(task: JSONObject, allTasks: JSONArray): Boolean {
        val dependencies = task.optJSONArray("dependencies") ?: JSONArray()
        if (dependencies.length() == 0) return true

        val statusById = HashMap<String, String>()
        for (index in 0 until allTasks.length()) {
            val item = allTasks.optJSONObject(index) ?: continue
            val id = item.optString("task_id").takeIf { it.isNotBlank() } ?: continue
            statusById[id] = item.optString("status")
        }

        for (index in 0 until dependencies.length()) {
            val dependencyId = dependencies.optString(index).takeIf { it.isNotBlank() } ?: return false
            if (statusById[dependencyId] != "ACCEPTED") return false
        }
        return true
    }
}

/** Process-generation and lease policy for crash-safe task recovery. */
internal object ObjectiveTaskRecoveryPolicy {
    fun shouldRecover(
        status: String,
        recordedProcessId: String,
        currentProcessId: String,
        updatedAtEpochMs: Long,
        nowEpochMs: Long,
        staleAfterMs: Long
    ): Boolean {
        if (status != "EXECUTING") return false
        if (recordedProcessId.isBlank() || recordedProcessId != currentProcessId) return true
        return updatedAtEpochMs <= 0L || nowEpochMs - updatedAtEpochMs >= staleAfterMs
    }
}
