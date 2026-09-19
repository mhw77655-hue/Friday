package com.jarvis.app.vf.reactor

import com.jarvis.app.vf.animation.AnimationEngine
import com.jarvis.app.vf.particle.ParticleEngine
import com.jarvis.app.vf.tokens.JvReactorStateColor
import com.jarvis.app.vf.tokens.JvTokens

/**
 * JARVIS Visual Foundation — Reactor Engine (pure model).
 *
 * Implements `07_REACTOR.md` §6 (state specification table):
 * maps the 13 reactor states (§9) to a concrete [ReactorSpec] —
 * ring counts/speeds, particle direction/density, breathing params,
 * pulse mode, and facet-flicker flag. The spec is consumed by
 * [OrbRenderer] and [ReactorRenderer].
 *
 * Pure JVM — no Compose dependency; fully unit-testable.
 */
object ReactorEngine {

    /**
     * Reactor states from the Visual Bible §9 (12 distinct visual states;
     * Building and Executing share one visual definition §9.9).
     */
    enum class ReactorSpecState(val color: JvReactorStateColor) {
        IDLE(JvReactorStateColor.IDLE),
        LISTENING(JvReactorStateColor.LISTENING),
        THINKING(JvReactorStateColor.THINKING),
        RESEARCH(JvReactorStateColor.RESEARCH),
        PLANNING(JvReactorStateColor.PLANNING),
        CODING(JvReactorStateColor.CODING),
        BUILDING(JvReactorStateColor.BUILDING),
        LEARNING(JvReactorStateColor.LEARNING),
        WARNING(JvReactorStateColor.WARNING),
        CRITICAL(JvReactorStateColor.CRITICAL),
        SLEEPING(JvReactorStateColor.SLEEPING),
        OFFLINE(JvReactorStateColor.OFFLINE);

        /** §9.9: Building and Executing are visually identical. */
        val isBuildingFamily: Boolean get() = this == BUILDING

        /** §9.3–9.9: Thinking family states carry facet flicker. */
        val hasFlicker: Boolean get() = when (this) {
            THINKING, RESEARCH, PLANNING, CODING, LEARNING -> true
            else -> false
        }
    }

    /**
     * Resolved render specification for one frame. Consumed directly
     * by [OrbRenderer] and [ReactorRenderer].
     */
    data class ReactorSpec(
        val state: ReactorSpecState,
        val breathingMs: Long,
        val breathingAmplitude: Float,
        val ringSpeedFactor: Float,
        val particleDirection: ParticleEngine.ParticleDirection,
        val particleCount: Int,
        val pulseMode: PulseMode,
        val hasFlicker: Boolean,
        val isBuildingFamily: Boolean,
        val researchScanMs: Long?,
        val ringAlignment: Boolean,
        val ringCount: Int
    ) {
        enum class PulseMode { NONE, BREATHING, AUDIO, SHARP_1HZ, SHARP_2HZ }
    }

    /** Resolve a [ReactorSpec] from the current [state]. */
    fun spec(state: ReactorSpecState): ReactorSpec {
        val color = state.color
        val family = AnimationEngine.stateBreathingMs(color) // breathing ms
        val breathingMs = when (state) {
            ReactorSpecState.IDLE -> JvTokens.DurBreathingIdle
            ReactorSpecState.SLEEPING -> JvTokens.DurBreathingSleep
            ReactorSpecState.OFFLINE -> 0L
            else -> JvTokens.DurBreathingIdle
        }
        val breathingAmp = when (state) {
            ReactorSpecState.SLEEPING -> JvTokens.BreathingAmplitudeMin
            ReactorSpecState.OFFLINE -> 0f
            else -> JvTokens.BreathingAmplitudeMax * 0.83f  // ~10% in the middle of ±8–12
        }
        val pulseMode = when (state) {
            ReactorSpecState.WARNING -> ReactorSpec.PulseMode.SHARP_1HZ
            ReactorSpecState.CRITICAL -> ReactorSpec.PulseMode.SHARP_2HZ
            ReactorSpecState.OFFLINE -> ReactorSpec.PulseMode.NONE
            ReactorSpecState.LISTENING -> ReactorSpec.PulseMode.AUDIO
            else -> ReactorSpec.PulseMode.BREATHING
        }
        val researchScan = when (state) {
            ReactorSpecState.RESEARCH -> 2000L  // §9.4: outward-scanning ring every 2s
            else -> null
        }
        val ringAlignment = state == ReactorSpecState.PLANNING  // §9.5
        val rings = when {
            state == ReactorSpecState.OFFLINE -> 0
            state == ReactorSpecState.SLEEPING -> 2
            else -> JvTokens.RingCountPhone
        }

        return ReactorSpec(
            state = state,
            breathingMs = breathingMs,
            breathingAmplitude = breathingAmp,
            ringSpeedFactor = AnimationEngine.stateRingSpeedFactor(color),
            particleDirection = ParticleEngine.stateDirection(color),
            particleCount = ParticleEngine.stateCount(color),
            pulseMode = pulseMode,
            hasFlicker = state.hasFlicker,
            isBuildingFamily = state == ReactorSpecState.BUILDING,
            researchScanMs = researchScan,
            ringAlignment = ringAlignment,
            ringCount = rings
        )
    }
}
