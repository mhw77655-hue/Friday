package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.algo.Clamp
import com.jarvis.app.humancore.protocol.AffectRead
import com.jarvis.app.humancore.store.MoodStore
import com.jarvis.app.humancore.store.PersonalityStore

/**
 * The Emotional Regulation module (§6) — the ONLY writer of the Mood store.
 *
 * Its job is to decide whether, and how much, an event moves JARVIS's mood.
 * It consumes the Emotional Intelligence module's *read of the user* (an
 * [AffectRead], never raw text — it must not re-derive the user's emotional
 * state from text itself, that would be "eating the EI module's job", §6
 * Inputs) and applies a bounded, empathy-coupled nudge.
 *
 * Constraints honored here:
 *  - coupling is deliberately damped — JARVIS is moved by, but not hijacked
 *    by, the user's affect (empathy without contagion, §6);
 *  - mood stays clamped to [-1, 1] via the store's hard clamps (§0.11);
 *  - the very slow baseline coupling from Personality's warmth/energy-adjacent
 *    traits never sets a baseline *equal* to a trait — it nudges only (§6);
 *  - mood can never influence values compliance (that is the Guard's
 *    domain and it ignores mood, §6 Constraints).
 */
class EmotionalRegulation(
    private val mood: MoodStore,
    private val personality: PersonalityStore,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    /**
     * How strongly JARVIS's own mood tracks the user's read affect, as a
     * fraction of the read. Kept well below 1 so the coupling never produces
     * mood swings that mirror the user one-for-one.
     */
    private val valenceCoupling: Double = 0.25
    private val arousalCoupling: Double = 0.20

    /** Session-boundary baseline coupling rate toward the personality target (§6). */
    private val baselineCouplingRate: Double = 0.05

    /** Feed a user-affect read into JARVIS's mood (empathy coupling). */
    fun observe(affect: AffectRead, now: Long = clock()) {
        if (affect.isUnknown) {
            // Low-confidence read: do not move mood on noise. This is the
            // "unknown is a valid state, never an error" rule (§7).
            return
        }
        val confidence = affect.confidence.coerceIn(0.0, 1.0)
        val valDelta = affect.valence * valenceCoupling * confidence
        val aroDelta = affect.arousal * arousalCoupling * confidence
        mood.applyNudge(valDelta, aroDelta, now)
        val effective = mood.effective(now)
        bus.publish(
            HcEvent.MoodUpdated(
                ts = now,
                reason = "empathetic coupling to user affect (confidence ${(confidence * 100).toInt()}%)",
                valence = effective.valence,
                arousal = effective.arousal
            )
        )
    }

    /**
     * Very slow baseline coupling: over many sessions, a warmer/more energetic
     * personality nudges JARVIS's mood baselines slightly positive. Called at
     * session boundaries, never per-message (§6 Lifecycle).
     *
     * The coupling is MEAN-REVERTING: the baseline is nudged toward a bounded
     * target derived from the personality traits, not accumulated past it. A
     * cumulative `+delta` formula drifts monotonically toward the +1.0 clamp
     * (~80 sessions with the default traits) and leaves JARVIS permanently
     * maximally cheerful (HUMAN_CORE_AUDIT M-5). Approaching the target
     * asymptotically keeps the "moves toward a trait, never set equal to it"
     * rule (§6) and stays bounded.
     */
    fun coupleBaselinesToPersonality(now: Long = clock()) {
        val warmth = personality.traitValue(PersonalityStore.TRAIT_WARMTH) ?: 0.60
        val curiosity = personality.traitValue(PersonalityStore.TRAIT_CURIOSITY) ?: 0.65
        val current = mood.effective(now)
        // Bounded targets from the traits (default warmth 0.6 / curiosity 0.65
        // -> valence target ~0.25, arousal target ~0.15).
        val valenceTarget = Clamp.unitSymmetric((warmth - 0.5) + (curiosity - 0.5))
        val arousalTarget = Clamp.unitSymmetric(curiosity - 0.5)
        val valenceDelta = baselineCouplingRate * (valenceTarget - current.valenceBaseline)
        val arousalDelta = baselineCouplingRate * (arousalTarget - current.arousalBaseline)
        mood.nudgeBaselines(valenceDelta, arousalDelta, now)
    }
}
