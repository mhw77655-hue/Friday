package com.jarvis.app.approval

import android.util.Log
import com.jarvis.app.approvals.LocalApprovalQueue
import com.jarvis.app.approvals.RiskLevel
import com.jarvis.app.env.PermissionPolicy
import com.jarvis.app.env.SafetyRestrictions
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * An action the organism might take on-device (port of the retired
 * `DecisionGate.JarvisAction`). Type is `"respond" | "write" | "noop"`.
 */
data class JarvisAction(
    val mission: String? = null,
    val type: String,
    val targetPath: String? = null,
    val payload: String? = null,
    val capability: String = "unknown"
)

/** Outcome of a gate review. */
sealed interface GateResult {
    data class AutoApproved(val riskLevel: RiskLevel) : GateResult
    data class Pending(val approvalId: String, val riskLevel: RiskLevel) : GateResult
    data class Denied(val reason: String, val riskLevel: RiskLevel) : GateResult
}

/**
 * Env-derived gate configuration, set from `LiquidEnvironmentManager`
 * whenever an environment is activated (permissionPolicy + safetyRestrictions).
 */
data class GateConfig(
    val permissionPolicy: PermissionPolicy = PermissionPolicy.FULL,
    val safetyRestrictions: SafetyRestrictions = SafetyRestrictions.NONE
) {
    /**
     * Highest risk that may be auto-approved under the current policy.
     * `RiskLevel` is declared LOW < MEDIUM < HIGH < CRITICAL (ordinal order).
     */
    val autoApproveRiskUpTo: RiskLevel
        get() = when (permissionPolicy) {
            PermissionPolicy.FULL -> RiskLevel.HIGH
            PermissionPolicy.RESTRICTED -> RiskLevel.MEDIUM
            PermissionPolicy.SAFE_MODE -> RiskLevel.LOW
        }
}

/**
 * ApprovalGate — the single approval authority (retires `DecisionGate`).
 *
 * Classifies an action's risk, auto-approves under the configured policy, or
 * routes the action into the persisted [LocalApprovalQueue] for explicit
 * human confirmation (ApprovalsScreen). Denies outright under kill-switch
 * policies (writes disabled). Audit continuity: the JSONL shape of the
 * retired DecisionGate (`filesDir/logs/decision_gate.jsonl`) is preserved.
 */
object ApprovalGate {
    private const val TAG = "ApprovalGate"

    private val PROTECTED_PATTERNS = listOf(
        "manifest.json",
        "DecisionGate.kt",
        "ExecutionLayer.kt",
        "protected_paths",
        "identity.json",
        "personality.json"
    )

    @Volatile var config: GateConfig = GateConfig()
        private set

    private lateinit var logFile: File
    @Volatile private var initialized = false

    fun init(filesDir: File) {
        if (initialized) return
        val logsDir = File(filesDir, "logs")
        logsDir.mkdirs()
        logFile = File(logsDir, "decision_gate.jsonl")
        initialized = true
    }

    /** Configure from an active environment (LiquidEnvironmentManager). */
    fun configure(gateConfig: GateConfig) {
        config = gateConfig
    }

    fun configured(): GateConfig = config

    private fun matchesProtected(action: JarvisAction): Boolean {
        val target = action.targetPath ?: return false
        return PROTECTED_PATTERNS.any { target.contains(it) }
    }

    /** Classify an action's risk under the current safety restrictions. */
    fun classifyRisk(action: JarvisAction): RiskLevel {
        val risk = when {
            action.type == "write" ->
                if (matchesProtected(action)) RiskLevel.CRITICAL else RiskLevel.MEDIUM
            action.type == "respond" || action.type == "noop" -> RiskLevel.LOW
            action.capability == "SHELL_EXEC" -> RiskLevel.CRITICAL
            else -> RiskLevel.MEDIUM
        }
        // Safety restrictions escalate risk where the profile demands care.
        return when (config.safetyRestrictions) {
            SafetyRestrictions.CLOUD_ONLY -> if (action.type == "write") RiskLevel.CRITICAL else risk
            SafetyRestrictions.MAX -> RiskLevel.CRITICAL
            else -> risk
        }
    }

    /**
     * Review an action. Auto-approves when risk ≤ the policy ceiling;
     * otherwise routes into the approval queue. Writes are denied outright
     * under kill-switch policies. Suspends only when requesting approval.
     */
    suspend fun review(action: JarvisAction, approvalQueue: LocalApprovalQueue): GateResult {
        val risk = classifyRisk(action)

        // Kill-switch: writes are forbidden under READ_ONLY / MAX safety or
        // SAFE_MODE permission. This is a hard deny, not a pending approval.
        if (action.type == "write") {
            val writesForbidden = config.safetyRestrictions == SafetyRestrictions.READ_ONLY ||
                config.safetyRestrictions == SafetyRestrictions.MAX ||
                config.permissionPolicy == PermissionPolicy.SAFE_MODE
            if (writesForbidden) {
                val result = GateResult.Denied(
                    "writes disabled (${config.permissionPolicy}/${config.safetyRestrictions})",
                    risk
                )
                log(action, result)
                return result
            }
        }

        // CLOUD_ONLY requires explicit consent for every action.
        val requiresConsent = config.safetyRestrictions == SafetyRestrictions.CLOUD_ONLY
        val autoApprovable = risk.ordinal <= config.autoApproveRiskUpTo.ordinal && !requiresConsent

        val result: GateResult = if (autoApprovable) {
            GateResult.AutoApproved(risk)
        } else {
            val approval = approvalQueue.requestApproval(
                taskId = action.mission ?: "action-${System.currentTimeMillis()}",
                capability = action.capability,
                riskLevel = risk,
                description = "${action.type}${action.targetPath?.let { " -> $it" } ?: ""}",
                arguments = mapOf(
                    "type" to action.type,
                    "targetPath" to (action.targetPath ?: ""),
                    "payload" to (action.payload ?: "")
                ),
                requestedBy = "ApprovalGate"
            )
            GateResult.Pending(approval.id, risk)
        }
        log(action, result)
        return result
    }

    private fun log(action: JarvisAction, result: GateResult) {
        if (!initialized) {
            Log.w(TAG, "review() called before init() -- log entry dropped for mission=${action.mission}")
            return
        }
        val (approved, tier, reason) = when (result) {
            is GateResult.AutoApproved ->
                Triple(true, result.riskLevel.ordinal + 1, "auto-approved (${result.riskLevel})")
            is GateResult.Pending ->
                Triple(false, result.riskLevel.ordinal + 1, "requires approval (${result.approvalId})")
            is GateResult.Denied ->
                Triple(false, result.riskLevel.ordinal + 1, result.reason)
        }
        val entry = JSONObject().apply {
            put("ts", isoNow())
            put("mission", action.mission)
            put("type", action.type)
            put("target_path", action.targetPath)
            put("capability", action.capability)
            put("decision", JSONObject().apply {
                put("approved", approved)
                put("tier", tier)
                put("reason", reason)
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
