package com.jarvis.app.model

/**
 * A request to wake a model organ in a specific tier.
 *
 * @property organRole human-readable role of the waking organ (e.g. "reasoning", "voice")
 * @property tier the tier the organ wants to wake in
 * @property essential essential wakes must be served (a user turn under doubt is
 *   essential; a background prefetch is not). Non-essential wakes may be refused
 *   outright by low-battery policy; essential wakes degrade instead of failing bare.
 */
data class OrganWakeRequest(
    val organRole: String,
    val tier: ModelTier,
    val essential: Boolean = false
)

/** The governor's answer to an organ wake request. */
sealed interface AdmissionDecision {
    /** Admitted at the requested tier. */
    object ALLOW : AdmissionDecision

    /** Refused — no fallback (only for non-essential wakes that may be skipped). */
    object DENY : AdmissionDecision

    /** Admitted, but must serve from [lowerTier] instead (degraded cognition). */
    data class DEGRADE_TO(val lowerTier: ModelTier) : AdmissionDecision
}

/**
 * Per-tier resource budget for wake admission.
 *
 * A tier has headroom when the live snapshot has AT LEAST
 * [minAvailableMemoryMb] RAM free, CPU load no higher than [maxCpuLoadPercent],
 * thermal status no worse than [maxThermalLevel], and battery no lower than
 * [minBatteryPercent] (unless charging).
 */
data class TierResourceBudget(
    val tier: ModelTier,
    val minAvailableMemoryMb: Long = 512,
    val maxCpuLoadPercent: Double = 80.0,
    val maxThermalLevel: Int = 2,
    val minBatteryPercent: Int = 10
)

/**
 * ResourceGovernor — the admission authority gating every model-organ wake.
 *
 * Evaluates a [ResourceSnapshot] against per-tier budgets:
 *  - the [ModelTier.RESIDENT] tier is ALWAYS allowed (never downgraded)
 *  - RAM / CPU / thermal pressure returns [AdmissionDecision.DEGRADE_TO] the
 *    next-lower tier — the degraded-cognition ladder, never a bare denial
 *    without a fallback
 *  - low battery while not charging denies NON-essential wakes outright but
 *    still degrades ESSENTIAL ones rather than failing them bare
 *
 * Determinism: [admit] is a pure function of (request, snapshot); tests inject
 * a fake snapshot. The unwired default snapshot provider is an all-clear
 * placeholder — ModelManager wires the live [AndroidResourceSnapshot].
 */
open class ResourceGovernor(
    private val budgets: Map<ModelTier, TierResourceBudget> = defaultTierBudgets(),
    private val snapshotProvider: () -> ResourceSnapshot =
        { ResourceSnapshot.alwaysHealthy() }
) {

    /** Admit against the live snapshot from [snapshotProvider]. */
    open fun admit(request: OrganWakeRequest): AdmissionDecision = admit(request, snapshotProvider())

    /** Admit against an explicit snapshot — deterministic for tests. */
    fun admit(request: OrganWakeRequest, snapshot: ResourceSnapshot): AdmissionDecision {
        val tier = request.tier
        if (tier == ModelTier.RESIDENT) return AdmissionDecision.ALLOW

        val budget = budgets[tier] ?: return AdmissionDecision.DENY

        // Battery: low and not charging. Essential wakes degrade rather than
        // fail bare; non-essential background wakes may be skipped outright.
        if (snapshot.batteryPercent < budget.minBatteryPercent && !snapshot.isCharging) {
            return if (request.essential) {
                AdmissionDecision.DEGRADE_TO(requireNotNull(tier.lowerTier) { "no lower tier" })
            } else {
                AdmissionDecision.DENY
            }
        }

        // RAM / CPU / thermal pressure: degrade down the ladder, never bare-deny.
        val underPressure =
            snapshot.availableMemoryMb < budget.minAvailableMemoryMb ||
                snapshot.cpuLoadPercent > budget.maxCpuLoadPercent ||
                snapshot.thermalLevel > budget.maxThermalLevel
        return if (underPressure) {
            AdmissionDecision.DEGRADE_TO(requireNotNull(tier.lowerTier) { "no lower tier" })
        } else {
            AdmissionDecision.ALLOW
        }
    }

    companion object {
        fun defaultTierBudgets(): Map<ModelTier, TierResourceBudget> = mapOf(
            ModelTier.RESIDENT to TierResourceBudget(ModelTier.RESIDENT),
            ModelTier.VOICE to TierResourceBudget(
                tier = ModelTier.VOICE,
                minAvailableMemoryMb = 384,
                maxCpuLoadPercent = 40.0,
                maxThermalLevel = 1,
                minBatteryPercent = 15
            ),
            ModelTier.VISION to TierResourceBudget(
                tier = ModelTier.VISION,
                minAvailableMemoryMb = 768,
                maxCpuLoadPercent = 55.0,
                maxThermalLevel = 1,
                minBatteryPercent = 20
            ),
            ModelTier.ON_DEMAND_REASONING to TierResourceBudget(
                tier = ModelTier.ON_DEMAND_REASONING,
                minAvailableMemoryMb = 1536,
                maxCpuLoadPercent = 75.0,
                maxThermalLevel = 1,
                minBatteryPercent = 25
            )
        )
    }
}