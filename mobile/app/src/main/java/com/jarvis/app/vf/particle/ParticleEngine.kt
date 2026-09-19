package com.jarvis.app.vf.particle

import com.jarvis.app.vf.tokens.JvReactorStateColor
import kotlin.math.cos
import kotlin.math.sin

/**
 * JARVIS Visual Foundation — Particle Engine.
 *
 * Implements `09_PARTICLES.md`: particle emission along field lines,
 * lifecycle fades (15% in / 25% out §3.2), orbital geometry, and
 * the direction grammar (inward = consuming, outward = producing,
 * spiral-in = learning, drift-out = listening).
 *
 * Particles are **energy** (§02 §2.4) — always animated, following
 * gravitational-lens curvature §2.7, never scattering randomly.
 * Density encodes state activity (§9 §3): idle has almost none;
 * thinking/research has the most.
 *
 * Pure-JVM testable: `ParticleField` is a plain-kotlin computation
 * of positions, opacities, and sizes for a given state+time — no
 * Compose dependency required for unit tests.
 */
object ParticleEngine {

    /**
     * Particle count budgets per state (§09 §3).
     * These are the maximum counts; actual rendering may be reduced
     * for performance tier (§2.31).
     */
    fun stateCount(state: JvReactorStateColor): Int = when (state) {
        JvReactorStateColor.IDLE -> 40
        JvReactorStateColor.LISTENING -> 60
        JvReactorStateColor.THINKING -> 140
        JvReactorStateColor.RESEARCH -> 180
        JvReactorStateColor.PLANNING -> 140
        JvReactorStateColor.CODING -> 120
        JvReactorStateColor.BUILDING -> 140
        JvReactorStateColor.LEARNING -> 140
        JvReactorStateColor.WARNING -> 80
        JvReactorStateColor.CRITICAL -> 100
        JvReactorStateColor.SLEEPING -> 0
        JvReactorStateColor.OFFLINE -> 0
    }

    /**
     * Particle direction per state (§09 §4).
     * - INWARD: consuming input (thinking, analysis, learning)
     * - OUTWARD: producing output (building, executing, speaking)
     * - DRIFT_OUT: sparse, outward drift (listening)
     * - SPIRAL_IN: deliberate absorption into crystal core (learning)
     * - NONE: no motion (sleep, offline)
     */
    fun stateDirection(state: JvReactorStateColor): ParticleDirection = when (state) {
        JvReactorStateColor.THINKING -> ParticleDirection.INWARD
        JvReactorStateColor.RESEARCH -> ParticleDirection.INWARD
        JvReactorStateColor.PLANNING -> ParticleDirection.INWARD
        JvReactorStateColor.LEARNING -> ParticleDirection.SPIRAL_IN
        JvReactorStateColor.BUILDING -> ParticleDirection.OUTWARD
        JvReactorStateColor.CODING -> ParticleDirection.OUTWARD
        JvReactorStateColor.LISTENING -> ParticleDirection.DRIFT_OUT
        JvReactorStateColor.WARNING -> ParticleDirection.INWARD
        JvReactorStateColor.CRITICAL -> ParticleDirection.INWARD
        JvReactorStateColor.SLEEPING -> ParticleDirection.NONE
        JvReactorStateColor.OFFLINE -> ParticleDirection.NONE
        JvReactorStateColor.IDLE -> ParticleDirection.DRIFT_OUT  // very sparse, outward drift
    }

    /** Direction semantics: inward = consuming input; outward = producing output. */
    enum class ParticleDirection {
        INWARD,   // toward core (input consumed)
        OUTWARD,  // away from core (output produced)
        DRIFT_OUT,// sparse outward drift (listening/idle)
        SPIRAL_IN,// slow absorption into crystal core (learning)
        NONE      // no motion (sleep/offline)
    }

    /**
     * A single particle's state at one frame — position (relative to center),
     * size, opacity, and any per-particle data (flicker phase).
     */
    data class Particle(
        /** Angle in radians [0, 2π). */
        val angle: Float,
        /** Radial offset as a fraction of the current ring radius [0.35–1.25]. */
        val radialFraction: Float,
        /** Pixel size — varies by depth and state. */
        val sizePx: Float,
        /** Per-particle flicker phase offset (0–2π). */
        val flickerPhaseOffset: Float
    )

    /**
     * A field snapshot for one frame — a list of particles with computed
     * absolute positions, opacity, and size. This is the renderable output
     * of the particle engine. Computed once per frame, consumed by the
     * particle renderer.
     */
    data class ParticleField(
        val count: Int,
        val positions: List<FloatArray>  // [x, y, sizePx, opacity]
    )

    /**
     * Pre-compute the fixed particle field: angle, radial offset, size,
     * flicker-phase offset. Positions are computed at render time from
     * these parameters plus the frame's motion state.
     *
     * This matches the existing Orb.kt pattern (particle positions are
     * computed once via `remember` and only opacity is animated per frame).
     */
    fun precomputeFixed(count: Int, seed: Int = 42, sizeRangePx: Pair<Float, Float> = 2f to 5f): List<Particle> {
        val rng = java.util.Random(seed.toLong())
        return List(count) {
            Particle(
                angle = rng.nextFloat() * (2f * Math.PI).toFloat(),
                radialFraction = 0.35f + rng.nextFloat() * 0.9f, // 0.35x – 1.25x radius
                sizePx = sizeRangePx.first + rng.nextFloat() * (sizeRangePx.second - sizeRangePx.first),
                flickerPhaseOffset = rng.nextFloat() * (2f * Math.PI).toFloat()
            )
        }
    }

    /**
     * Lifecycle opacity: particles fade in over first ~15% of life
     * and fade out over last ~25% (§3.2). Linear segments.
     *
     * [lifeFraction] ∈ [0,1].
     */
    fun lifecycleOpacity(lifeFraction: Float): Float {
        return when {
            lifeFraction < 0.15f -> lifeFraction / 0.15f  // fade in (0→1 over 15%)
            lifeFraction < 0.75f -> 1f                    // fully opaque
            lifeFraction < 1.0f  -> (1f - lifeFraction) / 0.25f  // fade out (1→0 over 25%)
            else -> 0f
        }
    }

    /**
     * Compute absolute pixel position for one particle at one frame,
     * given the direction mode, time, and ring radius.
     */
    fun position(
        particle: Particle,
        time: Float,  // seconds
        direction: ParticleDirection,
        ringRadiusPx: Float,
        angularSpeed: Float, // radians per second
    ): FloatArray {
        val baseAngle = particle.angle + time * angularSpeed * when (direction) {
            ParticleDirection.INWARD -> -0.3f   // slow inward drift
            ParticleDirection.OUTWARD -> 0.2f   // slow outward drift
            ParticleDirection.DRIFT_OUT -> 0.15f
            ParticleDirection.SPIRAL_IN -> -0.4f
            ParticleDirection.NONE -> 0f
        }
        val r = particle.radialFraction * ringRadiusPx
        return floatArrayOf(
            (sin(baseAngle) * r).toFloat(),
            (cos(baseAngle) * r).toFloat()
        )
    }

    /**
     * Per-state particle base opacity, modulated by direction.
     * Base opacity is the maximum; lifecycle and flicker reduce it.
     */
    fun stateBaseOpacity(state: JvReactorStateColor): Float = when (state) {
        JvReactorStateColor.IDLE -> 0.25f
        JvReactorStateColor.LISTENING -> 0.35f
        JvReactorStateColor.THINKING -> 0.55f
        JvReactorStateColor.RESEARCH -> 0.60f
        JvReactorStateColor.PLANNING -> 0.55f
        JvReactorStateColor.CODING -> 0.50f
        JvReactorStateColor.BUILDING -> 0.50f
        JvReactorStateColor.LEARNING -> 0.55f
        JvReactorStateColor.WARNING -> 0.40f
        JvReactorStateColor.CRITICAL -> 0.45f
        JvReactorStateColor.SLEEPING -> 0f
        JvReactorStateColor.OFFLINE -> 0f
    }

    /**
     * Final per-particle opacity: base × lifecycle × flicker.
     * [flickerPhase] advances the per-particle flicker each frame.
     */
    fun finalOpacity(
        state: JvReactorStateColor,
        lifeFraction: Float,
        flickerPhase: Float,
        flickerDepth: Float = 0.15f
    ): Float {
        val base = stateBaseOpacity(state)
        val life = lifecycleOpacity(lifeFraction)
        val flicker = 1f - flickerDepth * (0.5f + 0.5f * kotlin.math.cos(flickerPhase)).toFloat()
        return (base * life * flicker).coerceIn(0f, 1f)
    }
}
