package com.jarvis.app.companioncore.render

import androidx.compose.ui.graphics.Color
import com.jarvis.app.companioncore.contract.VisualState
import com.jarvis.app.vf.reactor.ReactorEngine

/**
 * The set of animation archetypes the orb can play (§2.6, audit M-22).
 *
 * Aligned 1:1 with [VisualState] (the §1 `RenderIntent` vocabulary) — the
 * Orb State Machine produces a [RenderIntent] whose [VisualState] the
 * Animation Controller arbitrates, then resolves to a [OrbClip] for drawing.
 */
enum class OrbClip {
    IDLE_BREATHE, LISTENING_RIPPLE, THINKING_SWIRL, SPEAKING_PULSE,
    WAKE_BLOOM, SLEEP_DIM, SHUTDOWN_FADE, NOTIFICATION_GLOW;

    val visualState: VisualState
        get() = when (this) {
            IDLE_BREATHE -> VisualState.IDLE_BREATHE
            LISTENING_RIPPLE -> VisualState.LISTENING_RIPPLE
            THINKING_SWIRL -> VisualState.THINKING_SWIRL
            SPEAKING_PULSE -> VisualState.SPEAKING_PULSE
            WAKE_BLOOM -> VisualState.WAKE_BLOOM
            SLEEP_DIM -> VisualState.SLEEP_DIM
            SHUTDOWN_FADE -> VisualState.SHUTDOWN_FADE
            NOTIFICATION_GLOW -> VisualState.NOTIFICATION_GLOW
        }

    companion object {
        fun fromVisualState(state: VisualState): OrbClip = when (state) {
            VisualState.IDLE_BREATHE -> IDLE_BREATHE
            VisualState.LISTENING_RIPPLE -> LISTENING_RIPPLE
            VisualState.THINKING_SWIRL -> THINKING_SWIRL
            VisualState.SPEAKING_PULSE -> SPEAKING_PULSE
            VisualState.WAKE_BLOOM -> WAKE_BLOOM
            VisualState.SLEEP_DIM -> SLEEP_DIM
            VisualState.SHUTDOWN_FADE -> SHUTDOWN_FADE
            VisualState.NOTIFICATION_GLOW -> NOTIFICATION_GLOW
        }
    }
}

/**
 * §2.6 — the resolved orb render parameter set.
 *
 * A pure, immutable frame of what the orb should look like, produced by the
 * [OrbStateMachine] from `(presence_mode, emotion_vector, gaze/alert)` and
 * consumed by the Compose drawing path ([PresenceOrb]) and the Animation
 * Controller. Fields carry enough for the Visual Foundation renderer to draw
 * state-driven, plus the two Companion-Core-specific hooks:
 *
 * - [audioAmplitude]: the §2.6 audio-reactive input — live mic/TTS amplitude
 *   during Listening/Speaking (voice/visualization hook; source wired in a
 *   later phase, default 0).
 * - [crossfadeMs]: color transition duration (§12.3) — 0 snaps, >0 crossfades
 *   through [VisualIdentity] colors.
 */
data class OrbRenderParams(
    val clip: OrbClip,
    val reactorState: ReactorEngine.ReactorSpecState,
    /** Primary color (semantic role resolved by the state machine). */
    val color: Color,
    /** Glow strength 0..1 — from the state spec. */
    val glowIntensity: Float,
    val particleDensity: Int,
    val ringCount: Int,
    val breathingMs: Long,
    val breathingAmplitude: Float,
    val pulseMode: ReactorEngine.ReactorSpec.PulseMode,
    val crossfadeMs: Long = 0L,
    val audioAmplitude: Float = 0f
) {
    companion object {
        /** A safe resting default (Idle, gentle breathing, no pulse). */
        fun idle(): OrbRenderParams {
            val spec = ReactorEngine.spec(ReactorEngine.ReactorSpecState.IDLE)
            return OrbRenderParams(
                clip = OrbClip.IDLE_BREATHE,
                reactorState = ReactorEngine.ReactorSpecState.IDLE,
                color = com.jarvis.app.companioncore.identity.VisualIdentity.color(
                    com.jarvis.app.companioncore.identity.VisualIdentity.SemanticRole.IDLE
                ),
                glowIntensity = 0.4f,
                particleDensity = spec.particleCount,
                ringCount = spec.ringCount,
                breathingMs = spec.breathingMs,
                breathingAmplitude = spec.breathingAmplitude,
                pulseMode = spec.pulseMode
            )
        }
    }
}

