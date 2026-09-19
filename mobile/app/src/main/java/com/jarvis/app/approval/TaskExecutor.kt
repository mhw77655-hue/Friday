package com.jarvis.app.approval

import android.util.Log
import com.jarvis.app.inbox.InboxTask
import com.jarvis.app.inbox.LocalTaskInbox
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Failure to apply an action. */
class ExecutionError(message: String) : Exception(message)

/** Result of applying an action. */
data class ExecResult(
    val applied: Boolean,
    val output: String? = null,
    val reason: String? = null
)

/**
 * TaskExecutor — the ONLY path through which an approved on-device action
 * applies (retires `ExecutionLayer`). Two entry points:
 *
 *  - [apply]: the pure action executor (write with snapshot-before-write,
 *    respond, noop), gated by a [GateResult].
 *  - [runTask]: the inbox-integrated path — claims a task, executes its
 *    capability through the pluggable [executeCapability] dispatcher, and
 *    transitions RUNNING → COMPLETED/FAILED in the persisted inbox.
 *
 * Audit continuity: `filesDir/logs/execution.jsonl` keeps the shape the
 * retired ExecutionLayer wrote.
 */
object TaskExecutor {
    private const val TAG = "TaskExecutor"

    private lateinit var execLogFile: File
    private lateinit var snapshotDir: File
    @Volatile private var initialized = false

    /** Capability dispatcher seam — set by CapabilityRegistry (or tests). */
    @Volatile
    var executeCapability: (suspend (capability: String, args: Map<String, Any>) -> Result<Map<String, Any>>)? = null

    fun init(filesDir: File) {
        if (initialized) return
        val logsDir = File(filesDir, "logs")
        logsDir.mkdirs()
        execLogFile = File(logsDir, "execution.jsonl")
        snapshotDir = File(filesDir, "snapshots")
        snapshotDir.mkdirs()
        initialized = true
    }

    /** Apply a gated action (the ExecutionLayer port). */
    fun apply(action: JarvisAction, decision: GateResult): ExecResult {
        if (decision !is GateResult.AutoApproved) {
            val reason = when (decision) {
                is GateResult.Pending -> "awaiting approval (${decision.approvalId})"
                is GateResult.Denied -> decision.reason
                is GateResult.AutoApproved -> "impossible"
            }
            val result = ExecResult(applied = false, reason = reason)
            log(action, result)
            return result
        }

        val result: ExecResult = try {
            when (action.type) {
                "respond" -> ExecResult(applied = true, output = action.payload)

                "write" -> {
                    val targetPath = action.targetPath
                        ?: throw ExecutionError("write action missing target_path")
                    val target = File(targetPath)
                    snapshot(target)
                    target.parentFile?.mkdirs()
                    target.writeText(action.payload ?: "")
                    ExecResult(applied = true, output = "wrote ${target.path}")
                }

                "noop" -> ExecResult(applied = true, output = "noop")

                else -> throw ExecutionError("unknown action type: ${action.type}")
            }
        } catch (e: Exception) {
            ExecResult(applied = false, reason = e.message)
        }
        log(action, result)
        return result
    }

    /**
     * Inbox-integrated execution: executes [task] through the capability
     * dispatcher and transitions the inbox entry to COMPLETED/FAILED.
     * The task must already be claimed (RUNNING) — claim via
     * `LocalTaskInbox.claimNext()`.
     */
    suspend fun runTask(task: InboxTask, inbox: LocalTaskInbox): ExecResult {
        val result: ExecResult = try {
            val dispatcher = executeCapability
            if (dispatcher == null) {
                ExecResult(
                    applied = false,
                    reason = "no capability dispatcher registered for '${task.capability}'"
                )
            } else {
                val outcome = dispatcher(task.capability, task.arguments)
                if (outcome.isSuccess) {
                    ExecResult(applied = true, output = "executed ${task.capability}")
                } else {
                    ExecResult(applied = false, reason = outcome.exceptionOrNull()?.message)
                }
            }
        } catch (e: Exception) {
            ExecResult(applied = false, reason = e.message)
        }

        if (result.applied) {
            inbox.complete(task.id, mapOf("applied" to true, "output" to (result.output ?: "")))
        } else {
            inbox.fail(task.id, result.reason ?: "execution failed")
        }
        log(task, result)
        return result
    }

    private fun snapshot(target: File) {
        if (!target.exists()) return
        val stamp = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val dest = File(snapshotDir, "${target.name}-$stamp")
        target.copyTo(dest, overwrite = true)
    }

    private fun log(action: JarvisAction, result: ExecResult) {
        if (!initialized) {
            Log.w(TAG, "apply() called before init() -- log entry dropped")
            return
        }
        val entry = JSONObject().apply {
            put("ts", isoNow())
            put("mission", action.mission)
            put("type", action.type)
            put("target_path", action.targetPath)
            put("result", JSONObject().apply {
                put("applied", result.applied)
                put("output", result.output)
                put("reason", result.reason)
            })
        }
        synchronized(this) {
            execLogFile.appendText(entry.toString() + "\n")
        }
    }

    private fun log(task: InboxTask, result: ExecResult) {
        if (!initialized) return
        val entry = JSONObject().apply {
            put("ts", isoNow())
            put("mission", task.id)
            put("type", task.capability)
            put("target_path", null)
            put("result", JSONObject().apply {
                put("applied", result.applied)
                put("output", result.output)
                put("reason", result.reason)
            })
        }
        synchronized(this) {
            execLogFile.appendText(entry.toString() + "\n")
        }
    }

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
