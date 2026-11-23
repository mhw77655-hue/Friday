package ai.friday.assistant.approval

import android.util.Log
import ai.friday.assistant.approvals.LocalApprovalQueue
import ai.friday.assistant.approvals.RiskLevel
import ai.friday.assistant.env.PermissionPolicy
import ai.friday.assistant.env.SafetyRestrictions
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * An action the organism might take on-device (port of the retired
 * `DecisionGate.FridayAction`). Type is `"respond" | "write" | "noop"`.
 */
data class FridayAction(
    val mission: String? = null,
    val type: String,
    val targetPath: String? = null,
    val payload: String? = null,
    val capability: String = "unknown"
)

/** Outcome of a gate review. */
sealed interface GateResult {
    /** Token is minted only by ApprovalGate and is bound to one exact action. */
    data class AutoApproved(val riskLevel: RiskLevel, internal val permitToken: String = "") : GateResult
    data class Approved(
        val approvalId: String,
        val riskLevel: RiskLevel,
        internal val permitToken: String = ""
    ) : GateResult
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

    private data class PermitRecord(
        val actionFingerprint: String,
        val riskLevel: RiskLevel,
        val config: GateConfig,
        val expiresAtMs: Long
    )

    private val issuedPermits = ConcurrentHashMap<String, PermitRecord>()
    private const val PERMIT_LIFETIME_MS = 60_000L

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

    private fun matchesProtected(action: FridayAction): Boolean {
        val target = action.targetPath ?: return false
        return PROTECTED_PATTERNS.any { target.contains(it) }
    }

    /** Classify an action's risk under the current safety restrictions. */
    fun classifyRisk(action: FridayAction): RiskLevel {
        // Capability risk must take precedence over the presentation/action
        // verb. Otherwise a SHELL_EXEC request labeled "respond" or "noop"
        // falls through to LOW and can be auto-approved by the FULL profile.
        val risk = when {
            action.capability.equals("SHELL_EXEC", ignoreCase = true) -> RiskLevel.CRITICAL
            action.type == "write" ->
                if (matchesProtected(action)) RiskLevel.CRITICAL else RiskLevel.MEDIUM
            action.type == "respond" || action.type == "noop" -> RiskLevel.LOW
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
    suspend fun review(action: FridayAction, approvalQueue: LocalApprovalQueue): GateResult {
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
            GateResult.AutoApproved(risk, issuePermit(action, risk))
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

    /**
     * Turn a persisted human approval into a one-use execution permit. The
     * approval must match the exact action arguments; approval IDs alone are
     * not execution authority.
     */
    suspend fun resumeApproved(
        action: FridayAction,
        approvalId: String,
        approvalQueue: LocalApprovalQueue
    ): GateResult {
        val risk = classifyRisk(action)
        val approval = approvalQueue.getApproval(approvalId)
            ?: return GateResult.Denied("approval not found", risk)
        val expectedArguments = actionArguments(action)
        val matches = approval.status == ApprovalStatus.APPROVED &&
            approval.decision == ApprovalDecision.APPROVE &&
            approval.capability == action.capability &&
            approval.arguments == expectedArguments &&
            (action.mission == null || approval.taskId == action.mission)
        if (!matches) {
            val denied = GateResult.Denied("approval does not authorize this exact action", risk)
            log(action, denied)
            return denied
        }
        if (action.type == "write" && writesForbidden()) {
            val denied = GateResult.Denied("writes disabled by current environment policy", risk)
            log(action, denied)
            return denied
        }
        val result = GateResult.Approved(approvalId, risk, issuePermit(action, risk))
        log(action, result)
        return result
    }

    /** Consume a one-use permit at the execution boundary. Forged, stale,
     * reused, action-substituted or policy-stale decisions fail closed. */
    internal fun consumePermit(action: FridayAction, decision: GateResult): Boolean {
        val token = when (decision) {
            is GateResult.AutoApproved -> decision.permitToken
            is GateResult.Approved -> decision.permitToken
            else -> return false
        }
        if (token.isBlank()) return false
        val permit = issuedPermits.remove(token) ?: return false
        if (permit.expiresAtMs < System.currentTimeMillis()) return false
        if (permit.actionFingerprint != actionFingerprint(action)) return false
        if (permit.config != config || permit.riskLevel != classifyRisk(action)) return false
        return when (decision) {
            is GateResult.AutoApproved ->
                config.safetyRestrictions != SafetyRestrictions.CLOUD_ONLY &&
                    permit.riskLevel.ordinal <= config.autoApproveRiskUpTo.ordinal &&
                    !(action.type == "write" && writesForbidden())
            is GateResult.Approved -> !(action.type == "write" && writesForbidden())
            else -> false
        }
    }

    private fun writesForbidden(): Boolean =
        config.safetyRestrictions == SafetyRestrictions.READ_ONLY ||
            config.safetyRestrictions == SafetyRestrictions.MAX ||
            config.permissionPolicy == PermissionPolicy.SAFE_MODE

    private fun issuePermit(action: FridayAction, risk: RiskLevel): String {
        val now = System.currentTimeMillis()
        issuedPermits.entries.removeIf { it.value.expiresAtMs < now }
        val token = UUID.randomUUID().toString()
        issuedPermits[token] = PermitRecord(
            actionFingerprint = actionFingerprint(action),
            riskLevel = risk,
            config = config,
            expiresAtMs = now + PERMIT_LIFETIME_MS
        )
        return token
    }

    private fun actionArguments(action: FridayAction): Map<String, Any> = mapOf(
        "type" to action.type,
        "targetPath" to (action.targetPath ?: ""),
        "payload" to (action.payload ?: "")
    )

    private fun actionFingerprint(action: FridayAction): String {
        val canonical = listOf(
            action.mission.orEmpty(),
            action.type,
            action.targetPath.orEmpty(),
            action.payload.orEmpty(),
            action.capability
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun log(action: FridayAction, result: GateResult) {
        if (!initialized) {
            Log.w(TAG, "review() called before init() -- log entry dropped for mission=${action.mission}")
            return
        }
        val (approved, tier, reason) = when (result) {
            is GateResult.AutoApproved ->
                Triple(true, result.riskLevel.ordinal + 1, "auto-approved (${result.riskLevel})")
            is GateResult.Approved ->
                Triple(true, result.riskLevel.ordinal + 1, "human-approved (${result.approvalId})")
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
