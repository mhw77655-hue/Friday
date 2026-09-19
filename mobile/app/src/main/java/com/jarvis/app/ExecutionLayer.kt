package com.jarvis.app

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Single Execution Layer -- on-device port of core/execution_layer.py.
 *  Only path through which any on-device action (write/respond/noop)
 *  applies. Same scope note as DecisionGate.kt: code/config-modifying
 *  actions go through JarvisBrainBridge to the HF Space instead. */

class ExecutionError(message: String) : Exception(message)

data class ExecResult(
    val applied: Boolean,
    val output: String? = null,
    val reason: String? = null
)

object ExecutionLayer {
    private const val TAG = "ExecutionLayer"

    private lateinit var execLogFile: File
    private lateinit var snapshotDir: File
    @Volatile private var initialized = false

    fun init(filesDir: File) {
        if (initialized) return
        val logsDir = File(filesDir, "logs")
        logsDir.mkdirs()
        execLogFile = File(logsDir, "execution.jsonl")
        snapshotDir = File(filesDir, "snapshots")
        snapshotDir.mkdirs()
        initialized = true
    }

    fun apply(action: JarvisAction, decision: GateDecision): ExecResult {
        if (!decision.approved) {
            val result = ExecResult(applied = false, reason = decision.reason)
            log(action, decision, result)
            return result
        }

        val result: ExecResult = when (action.type) {
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
        log(action, decision, result)
        return result
    }

    private fun snapshot(target: File) {
        if (!target.exists()) return
        val stamp = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val dest = File(snapshotDir, "${target.name}.$stamp.bak")
        target.copyTo(dest, overwrite = true)
    }

    private fun log(action: JarvisAction, decision: GateDecision, result: ExecResult) {
        if (!initialized) {
            Log.w(TAG, "apply() called before init() -- log entry dropped for mission=${action.mission}")
            return
        }
        val entry = JSONObject().apply {
            put("ts", isoNow())
            put("mission", action.mission)
            put("type", action.type)
            put("decision", JSONObject().apply {
                put("approved", decision.approved)
                put("tier", decision.tier)
                put("reason", decision.reason)
            })
            put("result", JSONObject().apply {
                put("applied", result.applied)
                result.output?.let { put("output", it) }
                result.reason?.let { put("reason", it) }
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
