package com.jarvis.app.vf.animation

import androidx.compose.ui.graphics.Color
import com.jarvis.app.vf.tokens.JvColorFamily
import com.jarvis.app.vf.tokens.JvReactorStateColor
import com.jarvis.app.vf.tokens.JvTokens

/**
 * JARVIS Visual Foundation — Animation Engine.
 *
 * Implements `18_ANIMATIONS.md` §3 (state transition matrix):
 * given a from-state and a to-state, resolves the color crossfade
 * path (direct when families share; through neutral/steel flash
 * when they do not).
 *
 * The two pulse tempos are never mixed arbitrarily (§12): breathing
 * (sine, 3.2–4s) and event (150ms attack / 500ms decay) always layer.
 *
 * Pure-JVM testable: `AnimationPath` and `resolveTransition` are
 * pure functions — no Compose dependency required for unit tests.
 */
object AnimationEngine {

    /**
     * Directly adjacent families may crossfade without passing through
     * neutral — the two-state set is determined by the visual Bible §12.3.
     */
    private val directNeighbors: Set<Pair<JvColorFamily, JvColorFamily>> = setOf(
        // idle (blue) ↔ sleep (violet)
        JvColorFamily.BLUE_NOMINAL to JvColorFamily.VIOLET_SLEEP,
        JvColorFamily.VIOLET_SLEEP to JvColorFamily.BLUE_NOMINAL,
        // idle (blue) ↔ listening (teal)
        JvColorFamily.BLUE_NOMINAL to JvColorFamily.TEAL_INPUT,
        JvColorFamily.TEAL_INPUT to JvColorFamily.BLUE_NOMINAL,
        // any amber member ↔ any amber member
        JvColorFamily.AMBER_THINKING to JvColorFamily.AMBER_THINKING,
        // amber ↔ building (near-white)
        JvColorFamily.AMBER_THINKING to JvColorFamily.NEAR_WHITE_BUILDING,
        JvColorFamily.NEAR_WHITE_BUILDING to JvColorFamily.AMBER_THINKING,
        // warning ↔ critical (both red family)
        JvColorFamily.RED_ALERT to JvColorFamily.RED_ALERT,
    )

    /**
     * The crossfade color-path for a state change, resolved from the
     * Bible §12.3 rule set. Callers animate `t` in `[0,1]` and feed
     * it to [resolvedColor].
     */
    sealed interface AnimationPath {
        /** Direct lerp from → to. */
        data class Direct(val from: JvReactorStateColor, val to: JvReactorStateColor) : AnimationPath
        /** Through neutral/Steel flash — first half from→neutral, second half neutral→to. */
        data class ThroughNeutral(val from: JvReactorStateColor, val to: JvReactorStateColor) : AnimationPath
    }

    /**
     * Resolve the animation path between two reactor states.
     *
     * §12.3 rules:
     *  - Direct: same-family members (all amber ↔ amber) or explicit neighbors.
     *  - ThroughNeutral: all other pairs, including any↔Offline and Thinking↔Warning.
     */
    fun resolveTransition(from: JvReactorStateColor, to: JvReactorStateColor): AnimationPath {
        if (from == to) return AnimationPath.Direct(from, to)
        val fromFamily = from.family()
        val toFamily = to.family()
        // same family → direct
        if (fromFamily == toFamily) return AnimationPath.Direct(from, to)
        // explicit neighbors → direct
        if (fromFamily to toFamily in directNeighbors) return AnimationPath.Direct(from, to)
        // all others → through neutral/Steel flash (§12.3: never blend amber directly into red, etc.)
        return AnimationPath.ThroughNeutral(from, to)
    }

    /**
     * Compute the resolved color for [t] ∈ `[0, 1]` along [path].
     *
     * - Direct: `lerp(from, to, t)`.
     * - ThroughNeutral: first half `lerp(from, steel, t*2)`, second half `lerp(steel, to, (t-0.5)*2)`.
     *   The neutral flash is Steel (#8A94A6), not a blank screen.
     */
    fun resolvedColor(path: AnimationPath, t: Float): Color {
        val steel = JvTokens.NeutralFlash
        return when (path) {
            is AnimationPath.Direct -> {
                val c = androidx.compose.ui.graphics.lerp(path.from.color, path.to.color, t)
                c
            }
            is AnimationPath.ThroughNeutral -> {
                if (t <= 0.5f) {
                    androidx.compose.ui.graphics.lerp(path.from.color, steel, t * 2f)
                } else {
                    androidx.compose.ui.graphics.lerp(steel, path.to.color, (t - 0.5f) * 2f)
                }
            }
        }
    }

    /**
     * The recommended frame count for the given transition, bounded by
     * the Design System's duration tokens and 60fps.
     */
    fun transitionFrameCount(path: AnimationPath, fps: Int = 60): Int {
        return when (path) {
            is AnimationPath.Direct -> (JvTokens.DurStateChange / 1000f * fps).toInt().coerceAtLeast(1)
            is AnimationPath.ThroughNeutral -> (JvTokens.DurStateChange / 1000f * fps).toInt().coerceAtLeast(1) // same total duration
        }
    }

    /**
     * Determine the "event pulse Hz" for a given reactor state (§9).
     * Returns 0 for states that do not emit event pulses (most states).
     * Warning = 1Hz, Critical = 2Hz.
     */
    fun stateEventPulseHz(state: JvReactorStateColor): Int = when (state) {
        JvReactorStateColor.WARNING -> 1
        JvReactorStateColor.CRITICAL -> 2
        else -> 0
    }

    /**
     * Determine the breathing period (ms) for a state (§3.3 / §9.12).
     * Idle → 3600ms, Sleep → 7000ms, Offline → 0 (no breathing).
     * All other states: the current context's breathing parameter (caller supplies).
     */
    fun stateBreathingMs(state: JvReactorStateColor): Long = when (state) {
        JvReactorStateColor.IDLE -> JvTokens.DurBreathingIdle
        JvReactorStateColor.SLEEPING -> JvTokens.DurBreathingSleep
        JvReactorStateColor.OFFLINE -> 0L // no motion
        else -> JvTokens.DurBreathingIdle // default; caller may override for non-Idle active states
    }

    /**
     * State ring-speed multiplier (§9): Idle slowest, Thinking +40%, Sleep
     * near-stationary, Offline none.
     */
    fun stateRingSpeedFactor(state: JvReactorStateColor): Float = when (state) {
        JvReactorStateColor.IDLE -> 1.0f
        JvReactorStateColor.LISTENING -> 1.0f       // speed driven by audio amplitude, not this factor
        JvReactorStateColor.THINKING -> 1.4f
        JvReactorStateColor.RESEARCH -> 1.4f
        JvReactorStateColor.PLANNING -> 1.4f
        JvReactorStateColor.CODING -> 1.4f
        JvReactorStateColor.BUILDING -> 1.4f
        JvReactorStateColor.LEARNING -> 1.4f
        JvReactorStateColor.WARNING -> 1.6f         // speed up + jitter (§9.10)
        JvReactorStateColor.CRITICAL -> 2.0f
        JvReactorStateColor.SLEEPING -> 0.05f
        JvReactorStateColor.OFFLINE -> 0f
    }
}
