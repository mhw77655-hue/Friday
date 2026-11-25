package ai.friday.assistant

import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ai.friday.assistant.approval.GateResult
import ai.friday.assistant.approval.ExecResult
import ai.friday.assistant.approval.TaskExecutor
import ai.friday.assistant.approvals.RiskLevel
import java.io.File

/**
 * Compatibility facade for the retired ExecutionLayer API.
 *
 * All writes now pass through TaskExecutor's private-root, symlink-aware path
 * policy, snapshot bounds and bounded journal. This facade deliberately owns
 * no second write implementation or second execution log.
 */
object ExecutionLayer {
    fun init(filesDir: File) {
        TaskExecutor.init(filesDir)
    }

    @WorkerThread
    fun apply(action: FridayAction, decision: GateDecision): ExecResult {
        val taskAction = ai.friday.assistant.approval.FridayAction(
            mission = action.mission,
            type = action.type,
            targetPath = action.targetPath,
            payload = action.payload,
            capability = "legacy-execution-layer"
        )
        val gateResult = if (decision.approved) {
            GateResult.AutoApproved(RiskLevel.MEDIUM)
        } else {
            GateResult.Denied(decision.reason, RiskLevel.MEDIUM)
        }
        val result = TaskExecutor.apply(taskAction, gateResult)
        return ExecResult(
            applied = result.applied,
            output = result.output,
            reason = result.reason
        )
    }

    suspend fun applyOnIo(action: FridayAction, decision: GateDecision): ExecResult =
        withContext(Dispatchers.IO) { apply(action, decision) }
}
