package com.jarvis.app.model

import com.jarvis.app.cognitive.UncertaintyProfile

/**
 * CognitiveAdmissionPolicy — the FEP wake gate. Per turn it decides whether
 * the turn needs the on-demand reasoning tier or stays on the resident tier.
 *
 * Doubt signal: the cognitive engine's EXISTING per-turn [UncertaintyProfile]
 * (intent ambiguity, surfaced unknowns, overall). This policy only projects
 * and thresholds that signal and gates the [ModelManager] wake — it computes
 * no new doubt score.
 *
 * CONTRADICTION RECORDED (grounded against the repo): the PRD premise of a
 * Stage 01B "prediction-error/doubt scoring mechanism" is inaccurate — Stage
 * 01B shipped REFERENCE-STORE / PRONOUN-RESOLUTION / AMBIGUOUS-REFERENCE
 * (see git log 3d0e6d1, 09aad35, d896b85, 79904d1) and the repo has no
 * prediction-error/doubt scorer. The real, existing per-turn doubt carrier is
 * the cognitive engine's uncertainty profile ([UncertaintyProfile]),
 * maintained in [com.jarvis.app.cognitive.CognitiveEngine.processInput]. That
 * signal is reused, unmodified; the diff introduces no second/parallel
 * doubt-scoring implementation.
 */
enum class CognitiveAdmissionDecision {
    /** Stay on the resident tier; do NOT wake the reasoning organ. */
    RESIDENT,

    /** The turn needs dense reasoning: wake the on-demand reasoning organ. */
    REASONING
}

/**
 * The per-turn outcome of the FEP wake gate. [servedTier] is below
 * [requestedTier] — and [degraded] true — exactly when the governor denied or
 * degraded the reasoning wake and the turn was served from a lower tier.
 */
data class TurnServeOutcome(
    val decision: CognitiveAdmissionDecision,
    val requestedTier: ModelTier,
    val servedTier: ModelTier,
    val degraded: Boolean,
    /** The handle the turn is served from, when the reasoning organ was woken. */
    val servedHandle: ModelHandle? = null
)

/**
 * Decides per turn whether to wake the reasoning organ. Below-threshold doubt
 * never triggers a reasoning-tier request — the resident tier handles it;
 * at-or-above-threshold doubt does. A governor DENY/DEGRADE_TO is carried back
 * on [TurnServeOutcome.degraded] so the turn completes on the served tier
 * instead of crashing or hanging.
 */
class CognitiveAdmissionPolicy(
    private val doubtThreshold: Double = 0.5
) {

    /**
     * Project the engine's existing [UncertaintyProfile] onto a scalar doubt
     * signal: the strongest of overall uncertainty, intent ambiguity, or 1.0
     * when the turn surfaced unknowns (a turn with unknowns is doubtful even
     * when overall was never populated).
     */
    fun doubt(uncertainty: UncertaintyProfile): Double = maxOf(
        uncertainty.overall.toDouble(),
        uncertainty.intentAmbiguity.toDouble(),
        if (uncertainty.unknowns.isNotEmpty()) 1.0 else 0.0
    )

    /** Below threshold stays resident; at-or-above threshold wakes reasoning. */
    fun decide(doubtSignal: Double): CognitiveAdmissionDecision =
        if (doubtSignal >= doubtThreshold) CognitiveAdmissionDecision.REASONING
        else CognitiveAdmissionDecision.RESIDENT

    /** Decide directly on the engine's existing per-turn uncertainty profile. */
    fun decide(uncertainty: UncertaintyProfile): CognitiveAdmissionDecision =
        decide(doubt(uncertainty))

    /**
     * The per-turn gate: decide and, on high doubt, wake the reasoning organ
     * through [wakeReasoning]. Returns the outcome; callers serve the turn
     * from the reported tier and may flag [degraded].
     */
    suspend fun serveTurn(
        uncertainty: UncertaintyProfile,
        wakeReasoning: suspend () -> WakeResult
    ): TurnServeOutcome = when (decide(uncertainty)) {
        CognitiveAdmissionDecision.RESIDENT -> TurnServeOutcome(
            decision = CognitiveAdmissionDecision.RESIDENT,
            requestedTier = ModelTier.RESIDENT,
            servedTier = ModelTier.RESIDENT,
            degraded = false
        )
        CognitiveAdmissionDecision.REASONING -> {
            val result = wakeReasoning()
            TurnServeOutcome(
                decision = CognitiveAdmissionDecision.REASONING,
                requestedTier = ModelTier.ON_DEMAND_REASONING,
                servedTier = result.servedTier,
                degraded = result.degraded,
                servedHandle = result.handle
            )
        }
    }
}