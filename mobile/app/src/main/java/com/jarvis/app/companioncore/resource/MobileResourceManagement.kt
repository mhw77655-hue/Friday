package com.jarvis.app.companioncore.resource

/**
 * §2.31 — Mobile Resource Management (core).
 *
 * The cross-cutting governor keeping the Companion Core within the zero-budget,
 * mobile-first constraint (Samsung Tab A7 / Realme 9 Pro 5G class devices).
 * Owns the quality-tier ladder (HIGH/MEDIUM/LOW) with the concrete caps every
 * renderer reads (frame rate, particle density), the concrete step-down
 * triggers (thermal throttling, battery <15% non-charging, sustained
 * frame-budget misses from the Animation Controller watchdog), and
 * `background_suspended` — full render suspension when backgrounded, while
 * the low-rate presence tick continues (plan §1.7).
 *
 * ## Anti-flap (spec §2.31)
 * Tier changes are debounced by a 10s cooldown; steps go one rung at a time.
 * Under a borderline thermal signal the tier steps down and stays there until
 * conditions clear *and* the cooldown has elapsed — never oscillation.
 *
 * ## Thermal fallback (spec §2.31 failure handling)
 * When the thermal API is unavailable, the frame-budget-miss streak is the
 * sole throttling signal — "no thermal signal" never means "no thermal
 * problem". The caller feeds [evaluate] a `thermalUnknown` flag so the
 * governor does not *step back up* purely because a missing signal looks
 * healthy.
 *
 * Pure-JVM: injectable clock, deterministic [evaluate].
 */
class MobileResourceManagement(
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    enum class QualityTier { HIGH, MEDIUM, LOW }

    /** Per-tier caps — the single place renderers read their ceiling from. */
    data class ResourceBudget(
        val targetFps: Double,
        val maxParticleDensity: Int,
        val avatarEligible: Boolean
    )

    /** Snapshot of device conditions fed to [evaluate] each tick. */
    data class DeviceConditions(
        val thermalThrottling: Boolean,
        val thermalUnknown: Boolean,
        val batteryPercent: Int,   // -1 = unknown
        val charging: Boolean,
        val consecutiveFrameMisses: Int
    )

    /** The tier ladder — caps per tier (spec §2.31 Android strategy). */
    val budgets: Map<QualityTier, ResourceBudget> = mapOf(
        QualityTier.HIGH to ResourceBudget(targetFps = 60.0, maxParticleDensity = 180, avatarEligible = true),
        QualityTier.MEDIUM to ResourceBudget(targetFps = 30.0, maxParticleDensity = 90, avatarEligible = false),
        QualityTier.LOW to ResourceBudget(targetFps = 15.0, maxParticleDensity = 24, avatarEligible = false)
    )

    var tier: QualityTier = QualityTier.HIGH
        private set

    /** The current budget — every renderer reads its cap from here. */
    val budget: ResourceBudget get() = budgets.getValue(tier)

    /** Full render suspension when the app is backgrounded (plan §1.7). */
    var backgroundSuspended: Boolean = false
        private set

    /** Reason for the last tier change (diagnostics). */
    var lastTierChangeReason: String? = null
        private set

    private var cooldownUntilMs: Long = 0L

    /** Suspend/resume rendering for background/foreground (plan §1.7). */
    fun setBackgroundSuspended(suspended: Boolean) {
        backgroundSuspended = suspended
    }

    /**
     * Evaluate current device conditions and step the tier ladder if needed.
     * Called from the Engine Tick Lane (low rate) and on lifecycle events.
     * When [DeviceConditions.thermalUnknown] is true, a "healthy" thermal
     * reading must not trigger a step-up (spec §2.31 failure handling).
     */
    fun evaluate(conditions: DeviceConditions) {
        if (backgroundSuspended) return // render suspended; don't churn the tier
        val now = nowMs()

        // Sustained frame-miss ladder: ≥30 misses steps HIGH→MEDIUM, ≥60
        // steps MEDIUM→LOW (one rung at a time, cooldown-gated).
        val missStepDown = conditions.consecutiveFrameMisses >= MISSES_LOW ||
            (tier == QualityTier.HIGH && conditions.consecutiveFrameMisses >= MISSES_MEDIUM)
        val needsStepDown = conditions.thermalThrottling ||
            (!conditions.charging && conditions.batteryPercent in 1 until BATTERY_LOW_PCT) ||
            missStepDown

        if (needsStepDown && now >= cooldownUntilMs && tier != QualityTier.LOW) {
            stepDown(reason = "step-down")
            cooldownUntilMs = now + COOLDOWN_MS
            return
        }

        val healthy = !conditions.thermalThrottling &&
            (!conditions.thermalUnknown) &&
            conditions.batteryPercent >= BATTERY_LOW_PCT &&
            conditions.consecutiveFrameMisses < MISSES_MEDIUM
        if (healthy && now >= cooldownUntilMs && tier != QualityTier.HIGH) {
            stepUp(reason = "conditions-clear")
            cooldownUntilMs = now + COOLDOWN_MS
        }
    }

    // ------------------------------------------------------------------ internals

    private fun stepDown(reason: String) {
        val next = when (tier) {
            QualityTier.HIGH -> QualityTier.MEDIUM
            QualityTier.MEDIUM -> QualityTier.LOW
            QualityTier.LOW -> QualityTier.LOW
        }
        applyTier(next, reason)
    }

    private fun stepUp(reason: String) {
        val next = when (tier) {
            QualityTier.HIGH -> QualityTier.HIGH
            QualityTier.MEDIUM -> QualityTier.HIGH
            QualityTier.LOW -> QualityTier.MEDIUM
        }
        applyTier(next, reason)
    }

    private fun applyTier(newTier: QualityTier, reason: String) {
        if (newTier == tier) return
        tier = newTier
        lastTierChangeReason = reason
    }

    companion object {
        /** Tier-change debounce (spec §2.31: minimum 10s cooldown). */
        const val COOLDOWN_MS: Long = 10_000L

        /** Battery step-down threshold (non-charging only). */
        const val BATTERY_LOW_PCT: Int = 15

        /** Sustained frame-budget misses that step to MEDIUM / LOW. */
        const val MISSES_MEDIUM: Int = 30
        const val MISSES_LOW: Int = 60
    }
}
