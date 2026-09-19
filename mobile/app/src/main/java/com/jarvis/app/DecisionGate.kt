package com.jarvis.app

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Single Decision Gate -- on-device port of core/decision_gate.py.
 *  Scope: on-device-only actions (Shizuku, ADB, local file writes).
 *  Anything code/config-modifying still goes through JarvisBrainBridge
 *  to the HF Space's approve_proposed_fix() gate -- this is NOT a
 *  replacement for that, it's the on-device counterpart for the domain
 *  Python never runs in (Termux has no runtime role in the shipped app). */

data class JarvisAction(
    val mission: String? = null,
    val type: String,              // "respond" | "write" | "noop"
    val targetPath: String? = null,
    val payload: String? = null
)

data class GateDecision(
    val approved: Boolean,
    val tier: Int,
    val reason: String
)

object DecisionGate {
    private const val TAG = "DecisionGate"

    private val PROTECTED_PATTERNS = listOf(
        "manifest.json",
        "DecisionGate.kt",
        "ExecutionLayer.kt",
        "protected_paths"
    )

    private lateinit var logFile: File
    @Volatile private var initialized = false

    fun init(filesDir: File) {
        if (initialized) return
        val logsDir = File(filesDir, "logs")
        logsDir.mkdirs()
        logFile = File(logsDir, "decision_gate.jsonl")
        initialized = true
    }

    private fun isTier2(action: JarvisAction): Boolean {
        val target = action.targetPath ?: return false
        return PROTECTED_PATTERNS.any { target.contains(it) }
    }

    fun review(action: JarvisAction): GateDecision {
        val tier = if (isTier2(action)) 2 else 1
        val decision = if (tier == 1) {
            GateDecision(approved = true, tier = 1, reason = "auto-approved (Tier 1)")
        } else {
            GateDecision(
                approved = false,
                tier = 2,
                reason = "Tier 2 requires explicit human confirmation -- not wired until later phase"
            )
        }
        log(action, decision)
        return decision
    }

    private fun log(action: JarvisAction, decision: GateDecision) {
        if (!initialized) {
            Log.w(TAG, "review() called before init() -- log entry dropped for mission=${action.mission}")
            return
        }
        val entry = JSONObject().apply {
            put("ts", isoNow())
            put("mission", action.mission)
            put("type", action.type)
            put("target_path", action.targetPath)
            put("decision", JSONObject().apply {
                put("approved", decision.approved)
                put("tier", decision.tier)
                put("reason", decision.reason)
            })
        }
        synchronized(this) {
            logFile.appendText(entry.toString() + "\n")
        }
    }

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
