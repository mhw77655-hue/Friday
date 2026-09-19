package com.jarvis.app.vf.reactor

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.app.vf.glow.GlowEngine.drawBloom
import com.jarvis.app.vf.glow.GlowEngine.drawIgnition
import com.jarvis.app.vf.glow.GlowEngine.drawMetalReflection
import com.jarvis.app.vf.glow.GlowEngine.drawStackedGlow
import com.jarvis.app.vf.glow.GlowEngine.drawVolumetricHaze
import com.jarvis.app.vf.motion.MotionEngine
import com.jarvis.app.vf.orbital.drawWobblingRing
import com.jarvis.app.vf.particle.ParticleEngine
import com.jarvis.app.vf.tokens.JvTokens
import kotlin.math.cos
import kotlin.math.sin

/**
 * JARVIS Visual Foundation — Full Reactor Renderer (§07_REACTOR.md).
 *
 * The complete four-register composition: base plate (Metal, Layer 2),
 * orbital ring system (Crystal + Energy, Layer 2–3), crystal core
 * (Crystal, Layer 3–4), ignition point + emission field (Energy, Layer 4),
 * and particles (Energy, Layer 1–3). Renders at full fidelity for
 * tablet/desktop/workstation contexts.
 *
 * For the phone-primary reduced view, use [OrbRenderer] instead.
 *
 * The renderer is driven by [ReactorEngine.ReactorSpec] (§1.5: state-driven,
 * never event-scripted): breathing is applied to core/glow brightness
 * (§3.3), the Warning/Critical sharp 1Hz/2Hz pulse is layered on top of it
 * (§3.8, §5.7), ring count comes from `spec.ringCount`, and Offline renders
 * the inert dim-ember (§9.13). Remaining spec signatures not yet drawn here —
 * facet flicker, the research scan ring, and Planning's ring-alignment beat —
 * are not consumed yet.
 */
@Composable
fun ReactorRenderer(
    state: ReactorEngine.ReactorSpecState,
    size: Dp,
    modifier: Modifier = Modifier,
    showBasePlate: Boolean = true,
    audioAmplitude: Float = 0f,
    colorOverride: Color? = null
) {
    val spec = remember(state) { ReactorEngine.spec(state) }
    val baseColor = remember(state) { state.color.color }
    val stateColor = colorOverride ?: baseColor
    val isOffline = state == ReactorEngine.ReactorSpecState.OFFLINE

    // ---- Breathing (§3.3): sine ±8–12% on core brightness, via MotionEngine.
    // Skipped entirely when the state has no motion (Offline) — no 10Hz no-op.
    val breathing = if (spec.breathingMs > 0L && !isOffline) {
        MotionEngine.rememberBreathingBrightness(spec.breathingMs, spec.breathingAmplitude)
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
    val pulse = if (pulseHz > 0f) MotionEngine.rememberSharpPulse(pulseHz) else 0f
    // §3.3 core brightness = ambient breathing, with the alert spike layered
    // on top; §3.4 the spike never replaces the breathing underneath it.
    val coreBrightness = breathing * (1f + pulse * MotionEngine.SharpPulseAmplitude)

    // ---- Field motion phases (wobble/flicker) exist only where the state
    // actually has rings or particles — Offline stays still (§9.13).
    val noisePhase: Float
    val flickerPhase: Float
    if (spec.ringCount > 0) {
        val noiseTransition = rememberInfiniteTransition(label = "reactor-noise")
        noisePhase = noiseTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
            label = "noisePhase"
        ).value
    } else {
        noisePhase = 0f
    }
    if (spec.particleCount > 0) {
        val flickerTransition = rememberInfiniteTransition(label = "reactor-flicker")
        flickerPhase = flickerTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "flickerPhase"
        ).value
    } else {
        flickerPhase = 0f
    }

    // ---- Precompute fixed particles ----
    val particles = remember(spec.particleCount) {
        ParticleEngine.precomputeFixed(spec.particleCount, seed = 42, sizeRangePx = 2f to 5f)
    }

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(size.value / 2f, size.value / 2f)
        val baseRadius = size.value / 2f * 0.92f

        // ---- Layer 0/1: Emission field (volumetric haze) ----
        if (!isOffline) {
            drawVolumetricHaze(center, baseRadius * 1.6f, stateColor, 0.3f, brightness = coreBrightness)
        }

        // ---- Layer 2: Base plate (Metal, §6.2) — hardware, present in all states ----
        if (showBasePlate) {
            // Dark gunmetal platform
            drawCircle(
                color = JvTokens.Gunmetal,
                radius = baseRadius * 1.15f,
                center = center
            )
            if (!isOffline) {
                // Concentric machining rings (§2.1) — density-aware hairline
                val machiningStroke = with(density) { 0.5.dp.toPx() }
                for (i in 1..4) {
                    val r = baseRadius * (1.02f + i * 0.03f)
                    drawCircle(
                        color = JvTokens.Gunmetal.copy(alpha = 0.15f),
                        radius = r,
                        center = center,
                        style = Stroke(machiningStroke)
                    )
                }
                // Metal reflection of state color (§4.3)
                drawMetalReflection(center, baseRadius * 1.1f, stateColor, 0.06f, brightness = coreBrightness)
            }
        }

        // ---- Layer 4: Offline ember (§9.13) — the one inert state ----
        if (isOffline) {
            // Steel at ~15% brightness, single dim ember, held static.
            // No glow field, no bloom, no haze, no rings, no particles.
            val emberRadius = baseRadius * 0.06f
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

        // ---- Layer 1/2: Glow (§4.1) — brightest-only bloom on Layer-4 object.
        // Both scale with breathing: core brightness oscillates ±8–12% (§3.3).
        drawStackedGlow(center, baseRadius * 1.3f, stateColor, maxLayers = 7, layerAlpha = 0.18f, brightness = coreBrightness)
        // Bloom: always brightest element (§4.2)
        drawBloom(center, baseRadius * 1.3f, stateColor, brightness = coreBrightness)

        // ---- Layer 2/3: Orbital rings — count driven by the spec (§13) ----
        val ringCount = spec.ringCount.coerceIn(0, JvTokens.RingCountPhone)
        val innerCount = minOf(2, ringCount)
        val outerCount = ringCount - innerCount

        // §9.2: Listening/Building (Speaking) rings pulse with live audio
        // amplitude — the voice-visualization hook (§2.6 audio-reactive).
        val audioBoost = if (audioAmplitude > 0f &&
            (state == ReactorEngine.ReactorSpecState.LISTENING || state == ReactorEngine.ReactorSpecState.BUILDING)
        ) {
            1f + audioAmplitude * 0.6f
        } else {
            1f
        }

        val innerRingColor = stateColor.copy(alpha = (0.55f * audioBoost).coerceAtMost(1f))
        val outerRingColor = stateColor.copy(alpha = (0.30f * audioBoost).coerceAtMost(1f))
        val innerSpeed = spec.ringSpeedFactor
        val outerSpeed = spec.ringSpeedFactor * 0.6f

        // Inner ring cluster: tight, fast, bright
        for (i in 0 until innerCount) {
            val tilt = (i * 25.0 + 10.0) * Math.PI / 180.0
            val radius = baseRadius * (0.38f + i * 0.06f)
            val speed = innerSpeed * (1f + i * 0.2f)
            drawWobblingRing(center, radius, innerRingColor, tilt, speed, noisePhase, strokeWidthDp = 1.8f)
        }

        // Outer ring cluster: wider, slower, dimmer
        for (i in 0 until outerCount) {
            val tilt = (35.0 + i * 20.0) * Math.PI / 180.0
            val radius = baseRadius * (0.58f + i * 0.10f)
            val speed = outerSpeed * (1f + i * 0.15f)
            drawWobblingRing(center, radius, outerRingColor, tilt, speed, noisePhase, strokeWidthDp = 1.4f)
        }

        // ---- Layer 3: Crystal core shell (§6.3) — brightness breathes (§3.3) ----
        drawCircle(
            color = baseColor.copy(alpha = (JvTokens.OpacityCrystal * coreBrightness).coerceIn(0f, 1f)),
            radius = baseRadius * 0.22f,
            center = center
        )
        // Internal light / facet hint
        drawCircle(
            color = baseColor.copy(alpha = (0.30f * coreBrightness).coerceIn(0f, 1f)),
            radius = baseRadius * 0.15f,
            center = center
        )

        // ---- Layer 4: Ignition point (§2.2.1 / §5.6) ----
        drawIgnition(center, baseRadius * 0.10f)

        // ---- Layer 1–3: Particles (§3.2) ----
        if (spec.particleDirection != ParticleEngine.ParticleDirection.NONE) {
            particles.forEach { p ->
                val lifeFraction = ((flickerPhase + p.flickerPhaseOffset) % (2f * Math.PI.toFloat())) / (2f * Math.PI.toFloat())
                val opacity = ParticleEngine.finalOpacity(state.color, lifeFraction, flickerPhase + p.flickerPhaseOffset)
                val r = p.radialFraction * baseRadius
                val x = center.x + sin(p.angle + flickerPhase * 0.15f).toFloat() * r
                val y = center.y + cos(p.angle + flickerPhase * 0.15f).toFloat() * r
                drawCircle(
                    color = stateColor.copy(alpha = opacity),
                    radius = p.sizePx * 0.6f,
                    center = Offset(x, y)
                )
            }
        }
    }
}
