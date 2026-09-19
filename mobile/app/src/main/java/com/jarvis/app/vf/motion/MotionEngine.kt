package com.jarvis.app.vf.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.jarvis.app.vf.tokens.JvTokens

/**
 * JARVIS Visual Foundation — Motion Engine.
 *
 * Implements `08_MOTION.md` / §3 (Motion) and §12 (Animation Timing):
 * the system's two sanctioned pulse tempos (breathing and event) and the
 * duration/easing token catalogue. Every animated brightness value in the
 * system is produced by this engine; the renderers supply only the
 * field-wobble and particle-flicker phases that drive it.
 *
 * Pure-JVM testable: [breathingBrightness] and [eventPulseBrightness] are
 * plain functions of elapsed frame-time — no Compose dependency required
 * to test them. The @Composable helpers are thin wrappers that feed those
 * envelopes from animation state.
 */
object MotionEngine {

    // --------------------------------------------------------------- durations
    /** Micro (button press, icon state) — 100–150ms (§12.1). */
    val DurMicro = JvTokens.DurMicro

    /** UI transition (panel open/close) — 250–350ms. */
    val DurTransition = JvTokens.DurTransition

    /** State change crossfade — 400–600ms (§12.1). */
    val DurStateChange = JvTokens.DurStateChange

    /** Idle breathing cycle — 3200–4000ms (§3.3). */
    val DurBreathingIdle = JvTokens.DurBreathingIdle

    /** Sleep breathing cycle — 6000–8000ms (§3.3). */
    val DurBreathingSleep = JvTokens.DurBreathingSleep

    /** Event pulse attack + decay — 150ms attack / 400–600ms decay (§3.4). */
    val DurEventPulseAttack = JvTokens.DurEventPulseAttack
    val DurEventPulseDecay = JvTokens.DurEventPulseDecay

    /** Alert sharp-pulse amplitude (§3.8): how strongly the 1Hz/2Hz spike
     *  lifts core brightness above the ambient baseline. */
    const val SharpPulseAmplitude = 0.20f

    /** Startup / Shutdown — 1.5–3.0s (§12.1). */
    val DurStartupShutdown = JvTokens.DurStartupShutdown

    /** Voice-triggered state change: halved from DurStateChange (§14.4). */
    val DurVoiceResponse = JvTokens.DurVoiceResponse

    // --------------------------------------------------------------- breathing
    /**
     * Sine breathing envelope (§3.3): brightness oscillates ±8–12%
     * around baseline, sine ease-in-out, 3.2–4.0s.
     *
     * [amplitude] in `[BreathingAmplitudeMin, BreathingAmplitudeMax]`.
     * [periodMs] in `[DurBreathingIdle, DurBreathingSleep]`.
     * [elapsedMs] monotonically advancing time in ms.
     *
     * Returns `1.0 ± amplitude` — multiply baseline brightness by this.
     */
    fun breathingBrightness(
        elapsedMs: Long,
        periodMs: Long = DurBreathingIdle,
        amplitude: Float = 0.10f
    ): Float {
        if (periodMs <= 0L) return 1f
        val phase = (elapsedMs.toDouble() / periodMs) * 2.0 * Math.PI
        return 1.0f + amplitude * Math.sin(phase).toFloat()
    }

    // --------------------------------------------------------------- event pulse
    /**
     * Event-pulse envelope (§3.4): a single sharp brightness spike layered
     * on top of the ambient breathing.
     *
     * Shape: a fast linear attack over [DurEventPulseAttack], then a decay
     * along the warning-pulse sharp ease-out curve (§12.2, [JvTokens.EaseWarningPulse])
     * over [DurEventPulseDecay]. Applying the curve to the *remaining*
     * brightness makes the spike fall fast off the peak (≈0.63 by 10% of the
     * decay) and taper to zero — never a linear triangle.
     *
     * [elapsedMs] since pulse start. Returns a multiplier (0…1) that
     * represents the pulse contribution to brightness (additive to the
     * breathing envelope).
     */
    fun eventPulseBrightness(elapsedMs: Long): Float {
        val t = elapsedMs.toFloat()
        return when {
            t < 0f -> 0f
            t < DurEventPulseAttack -> {
                // sharp attack: fast ramp 0→1 (no ease-in; §12.2 "no ease-in")
                t / DurEventPulseAttack
            }
            t < DurEventPulseAttack + DurEventPulseDecay -> {
                // sharp ease-out decay 1→0: sample the curve at the remaining
                // brightness so the drop is steepest right after the peak.
                val decayProgress = (t - DurEventPulseAttack) / DurEventPulseDecay
                JvTokens.EaseWarningPulse().transform(1f - decayProgress)
            }
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    /**
     * Sharp alert-pulse envelope (§3.8, §07 §6, §5.7): Warning ~1Hz,
     * Critical ~2Hz — distinct, non-overlapping frequencies so the alert
     * survives even in grayscale/high-contrast modes.
     *
     * Each period spikes for [spikeMs] (default 150ms) then rests silent
     * until the next cycle. The spike is a fast attack (~25% of the spike)
     * followed by a decay along the warning-pulse sharp ease-out curve
     * (§12.2) — the same "sharp" shape as the one-shot [eventPulseBrightness],
     * just repeated.
     *
     * [elapsedMs] monotonically advancing time in ms.
     * Returns a multiplier (0…1); layer it on top of the ambient breathing
     * (§3.4) — never as a replacement.
     */
    fun sharpPulse(elapsedMs: Long, hz: Float, spikeMs: Long = 150L): Float {
        if (hz <= 0f) return 0f
        val period = (1000.0 / hz).toLong()
        if (period <= 0L) return 0f
        val spike = minOf(spikeMs, period)
        val phase = elapsedMs % period
        if (phase >= spike) return 0f
        val t = phase.toFloat() / spike
        return if (t < 0.25f) {
            t / 0.25f // fast attack (first 25% of the spike; no ease-in §12.2)
        } else {
            JvTokens.EaseWarningPulse().transform(1f - (t - 0.25f) / 0.75f)
        }
    }

    // --------------------------------------------------------------- Compose helpers
    /**
     * Compose helper — returns a continuous breathing multiplier for a composable.
     * [periodMs]: breathing cycle duration (idle 3600 / sleep 7000).
     * [amplitude]: 0.08–0.12 per §3.3.
     */
    @Composable
    fun rememberBreathingBrightness(
        periodMs: Long = DurBreathingIdle,
        amplitude: Float = JvTokens.BreathingAmplitudeMax
    ): Float {
        val transition = rememberInfiniteTransition(label = "jv-breathing")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = periodMs.toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "jv-breathing-phase"
        )
        val sin = Math.sin(phase * 2 * Math.PI).toFloat()
        return 1f + amplitude * sin
    }

    /**
     * Compose helper — a single event pulse trigger (§3.4).
     * [triggerKey]: stable key that changes on each event (e.g. message count).
     * Returns a multiplier (0…1) that spikes on the trigger and decays to zero.
     *
     * True single-shot: keyed by [triggerKey], so a new event restarts the
     * envelope from zero and it stops (does not loop) after attack + decay.
     */
    @Composable
    fun rememberEventPulseBrightness(triggerKey: Any): Float {
        val elapsedMs = (DurEventPulseAttack + DurEventPulseDecay).toInt()
        val elapsed = remember { Animatable(0f) }
        LaunchedEffect(triggerKey) {
            elapsed.snapTo(0f)
            elapsed.animateTo(
                targetValue = elapsedMs.toFloat(),
                animationSpec = tween(durationMillis = elapsedMs, easing = LinearEasing)
            )
        }
        // Feed the pure envelope — 150ms attack, 500ms decay, then 0.
        return eventPulseBrightness(elapsed.value.toLong())
    }

    /**
     * Compose helper — a continuously-repeating sharp alert pulse (§3.8).
     * [hz]: pulse frequency (Warning ≈1Hz, Critical ≈2Hz, §5.7).
     *
     * Returns the current spike multiplier (0…1): a fast attack + sharp
     * ease-out decay at the top of each cycle, then silence until the next
     * spike. The cycle is driven by a single [Animatable] advancing one period
     * per loop — no infinite transition, restarted cleanly on [hz] change.
     */
    @Composable
    fun rememberSharpPulse(hz: Float): Float {
        if (hz <= 0f) return 0f
        val periodMs = (1000f / hz).toInt()
        val phase = remember { Animatable(0f) }
        LaunchedEffect(hz) {
            while (true) {
                phase.snapTo(0f)
                phase.animateTo(
                    targetValue = periodMs.toFloat(),
                    animationSpec = tween(durationMillis = periodMs, easing = LinearEasing)
                )
            }
        }
        return sharpPulse(phase.value.toLong(), hz)
    }
}
