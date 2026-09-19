package com.jarvis.app.vf.orb

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.jarvis.app.vf.glow.GlowEngine.drawBloom
import com.jarvis.app.vf.glow.GlowEngine.drawIgnition
import com.jarvis.app.vf.glow.GlowEngine.drawStackedGlow
import com.jarvis.app.vf.glow.GlowEngine.drawVolumetricHaze
import com.jarvis.app.vf.motion.MotionEngine
import com.jarvis.app.vf.orbital.drawWobblingRing
import com.jarvis.app.vf.particle.ParticleEngine
import com.jarvis.app.vf.reactor.ReactorEngine
import com.jarvis.app.vf.tokens.JvTokens
import kotlin.math.cos
import kotlin.math.sin

/**
 * JARVIS Visual Foundation — Orb Renderer (§07_REACTOR.md §7).
 *
 * The reduced companion-surface render of the reactor: ignition point +
 * up to two simplified rings + emission field glow + particles. This is
 * the small always-on surface; state is communicated through color,
 * breathing (applied to core/glow brightness, §3.3), and motion — with the
 * Warning/Critical sharp 1Hz/2Hz pulse (§3.8, §5.7) layered on top.
 *
 * Ring budget is capped at two (§13.2 watch floor — the lowest level of
 * detail), while [ReactorEngine.ReactorSpec.ringCount] supplies the upper
 * bound. Breathing comes from [MotionEngine] (true sine, ±8–12%); Offline
 * renders the inert dim-ember (§9.13) with no glow/bloom/rings/particles.
 *
 * Note: state changes snap to the new state color — the crossfade-through-
 * family transition matrix ([com.jarvis.app.vf.animation.AnimationEngine])
 * is not yet wired into this renderer.
 *
 * @param state the current reactor state — the ONLY input to this
 *        renderer (state-driven, never event-scripted).
 * @param size  composable size (Dp); scales automatically.
 * @param modifier Compose modifier.
 * @param showEmissionField whether to render the emission haze
 *        (off during initial spin-up for performance).
 * @param audioAmplitude live audio amplitude 0..1 (§2.6 audio-reactive hook):
 *        in Listening/Building (Speaking) the rings pulse with the amplitude —
 *        the voice-visualization seam; 0 = no audio modulation.
 * @param colorOverride optional color to draw in place of the state color
 *        (used by the Companion Core to crossfade §12.3 transitions); null =
 *        draw the state's own color.
 */
@Composable
fun OrbRenderer(
    state: ReactorEngine.ReactorSpecState,
    size: Dp,
    modifier: Modifier = Modifier,
    showEmissionField: Boolean = true,
    audioAmplitude: Float = 0f,
    colorOverride: Color? = null,
    /**
     * Particle count ceiling from the resource governor (§2.31); null = the
     * state spec's own count (used by the demo / direct callers).
     */
    particleCountOverride: Int? = null,
    /**
     * Monotonic render time in ms that drives the animation (§2.21 tier frame
     * pacing, audit F3). When non-null the renderer computes breathing / pulse /
     * phases as pure functions of this time instead of running infinite
     * transitions — so a LOW tier genuinely renders at its capped fps (no
     * vsync invalidation between paced frames), and a backgrounded (suspended)
     * orb holds a static frame. Null = free-running animation (demo/fallback).
     */
    frameTimeMs: Float? = null
) {
    val spec = remember(state) { ReactorEngine.spec(state) }
    val baseStateColor = remember(state) { state.color.color }
    val stateColor = colorOverride ?: baseStateColor
    val isOffline = state == ReactorEngine.ReactorSpecState.OFFLINE
    val twoPi = (2 * Math.PI).toFloat()
    val paced = frameTimeMs != null
    val t = frameTimeMs ?: 0f

    // ---- Breathing (§3.3): sine ±8–12% on core brightness, via MotionEngine.
    // Skipped entirely when the state has no motion (Offline) — no 10Hz no-op.
    // Paced mode samples the same pure envelope from the render time.
    val breathing = if (spec.breathingMs > 0L && !isOffline) {
        if (paced) {
            MotionEngine.breathingBrightness(t.toLong(), spec.breathingMs, spec.breathingAmplitude)
        } else {
            MotionEngine.rememberBreathingBrightness(spec.breathingMs, spec.breathingAmplitude)
        }
    } else {
        1f
    }

    // ---- Alert sharp pulse (§3.8, §07 §6): Warning 1Hz / Critical 2Hz.
    // A sharp spike layered ON TOP of the ambient breathing (§3.4) — the
    // breathing continues underneath it, never replaced. Non-alert states
    // (pulseFactor = 1f) are unaffected.
    val pulseHz = when (spec.pulseMode) {
        ReactorEngine.ReactorSpec.PulseMode.SHARP_1HZ -> 1f
        ReactorEngine.ReactorSpec.PulseMode.SHARP_2HZ -> 2f
        else -> 0f
    }
    val pulse = when {
        pulseHz <= 0f -> 0f
        paced -> MotionEngine.sharpPulse(t.toLong(), pulseHz)
        else -> MotionEngine.rememberSharpPulse(pulseHz)
    }
    // §3.3 core brightness = ambient breathing, with the alert spike layered
    // on top; §3.4 the spike never replaces the breathing underneath it.
    val coreBrightness = breathing * (1f + pulse * MotionEngine.SharpPulseAmplitude)

    // ---- Field motion phases (wobble/flicker) exist only where the state
    // actually has rings or particles — Offline/Sleeping stay still (§9.13).
    // Paced mode: linear phase ramps over the same periods as the transitions.
    val noisePhase: Float = when {
        spec.ringCount <= 0 -> 0f
        paced -> (t % 9000f) / 9000f * twoPi
        else -> {
            val noiseTransition = rememberInfiniteTransition(label = "orb-noise")
            noiseTransition.animateFloat(
                initialValue = 0f,
                targetValue = twoPi,
                animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
                label = "orb-noise"
            ).value
        }
    }

    // ---- Precompute particles (positions stable; only opacity animates) ----
    // Particle budget comes from the governor (capped by the Orb State Machine
    // via OrbRenderParams.particleDensity); direct callers use the spec count.
    val count = particleCountOverride ?: spec.particleCount
    val flickerPhase: Float = when {
        count <= 0 -> 0f
        paced -> (t % 2600f) / 2600f * twoPi
        else -> {
            val flickerTransition = rememberInfiniteTransition(label = "orb-flicker")
            flickerTransition.animateFloat(
                initialValue = 0f,
                targetValue = twoPi,
                animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
                label = "orb-flicker"
            ).value
        }
    }
    val particles = remember(count) {
        ParticleEngine.precomputeFixed(count, seed = 42, sizeRangePx = 1.5f to 4f)
    }

    Canvas(modifier = modifier.size(size)) {
        val cx = size.value / 2f
        val cy = size.value / 2f
        val center = Offset(cx, cy)
        val radius = size.value / 2f * 0.88f

        // ---- Emission field (§4.4) ----
        if (showEmissionField && !isOffline) {
            drawVolumetricHaze(center, radius * 1.5f, stateColor, 0.25f, brightness = coreBrightness)
        }

        // ---- Layer 4: Offline ember (§9.13) — the one inert state ----
        if (isOffline) {
            // Steel at ~15% brightness, single dim ember, held static.
            // No glow field, no bloom, no haze, no rings, no particles.
            val emberRadius = radius * 0.06f
            drawCircle(
                color = JvTokens.Steel.copy(alpha = 0.55f),
                radius = emberRadius,
                center = center
            )
            drawCircle(
                color = JvTokens.Steel.copy(alpha = 0.20f),
                radius = emberRadius * 1.8f,
                center = center
            )
            return@Canvas
        }

        // ---- Glow + Bloom (§4.1–4.2) — scaled by breathing (core brightness §3.3) ----
        drawStackedGlow(center, radius * 1.2f, stateColor, maxLayers = 6, layerAlpha = 0.16f, brightness = coreBrightness)
        drawBloom(center, radius * 1.2f, stateColor, brightness = coreBrightness)

        // ---- Orbital ring (1–2, reduced view) ----
        // §9.2: while Listening/Building (Speaking), the rings pulse with live
        // audio amplitude — the voice-visualization hook (§2.6 audio-reactive).
        val audioBoost = if (audioAmplitude > 0f &&
            (state == ReactorEngine.ReactorSpecState.LISTENING || state == ReactorEngine.ReactorSpecState.BUILDING)
        ) {
            1f + audioAmplitude * 0.6f
        } else {
            1f
        }
        if (spec.ringCount > 0) {
            drawWobblingRing(center, radius * 0.52f, stateColor.copy(alpha = (0.50f * audioBoost).coerceAtMost(1f)), 12.0 * Math.PI / 180.0, spec.ringSpeedFactor, noisePhase, 1.6f)
            if (spec.ringCount > 1) {
                drawWobblingRing(center, radius * 0.68f, stateColor.copy(alpha = (0.28f * audioBoost).coerceAtMost(1f)), 30.0 * Math.PI / 180.0, spec.ringSpeedFactor * 0.65f, noisePhase, 1.2f)
            }
        }

        // ---- Ignition point (§2.2.1) ----
        drawIgnition(center, radius * 0.10f)

        // ---- Particles (§3.2) ----
        if (spec.particleDirection != ParticleEngine.ParticleDirection.NONE && particles.isNotEmpty()) {
            particles.forEach { p ->
                val lifeFrac = ((flickerPhase + p.flickerPhaseOffset) % (2 * Math.PI.toFloat())) / (2 * Math.PI.toFloat())
                val opacity = ParticleEngine.finalOpacity(state.color, lifeFrac, flickerPhase + p.flickerPhaseOffset)
                val r = p.radialFraction * radius
                val x = cx + sin(p.angle + flickerPhase * 0.12f).toFloat() * r
                val y = cy + cos(p.angle + flickerPhase * 0.12f).toFloat() * r
                drawCircle(
                    color = stateColor.copy(alpha = opacity),
                    radius = p.sizePx * 0.55f,
                    center = Offset(x, y)
                )
            }
        }
    }
}
