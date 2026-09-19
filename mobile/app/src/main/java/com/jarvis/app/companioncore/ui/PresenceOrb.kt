package com.jarvis.app.companioncore.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.companioncore.render.OrbRenderParams
import com.jarvis.app.companioncore.resource.MobileResourceManagement
import com.jarvis.app.vf.orb.OrbRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * §2.6 + §2.21 — the Compose drawing path for the reactor orb.
 *
 * The single always-on presence surface. State-driven by the Presence Engine:
 * it collects the resolved [OrbRenderParams] (produced each Engine Tick by the
 * Orb State Machine) and draws through the frozen Visual Foundation
 * [OrbRenderer], whose breathing/pulse/motion come from [MotionEngine] —
 * including the already-completed Warning/Critical sharp-pulse wiring.
 *
 * ## Recomposition isolation (audit R-P2)
 * The `StateFlow` is collected INSIDE this composable, so 30Hz Engine Tick
 * emissions recompose only the orb, never the whole screen. Renderer inputs
 * are gated through [derivedStateOf]: a tick whose reactor state is unchanged
 * (e.g. an emotion/alert-only change) does not churn the renderer's keyed
 * `remember(state)` spec.
 *
 * ## §2.31 resource governance (audit F3)
 * A single paced render clock: a monotonic render time advanced only at the
 * tier's frame cadence (from the §2.31 [budget], measured via the master
 * clock's [framePeriodNanos]). The renderer samples every animated value from
 * this time instead of running infinite transitions, so a LOW tier actually
 * redraws at 15fps, and a backgrounded (suspended) orb freezes to a static
 * frame. Real frame cadence is still reported every vsync to the §2.21
 * watchdog, which feeds tier steps.
 *
 * ## §12.3 transitions
 * Color changes crossfade for [OrbRenderParams.crossfadeMs] (0 = snap).
 *
 * ## Frame-budget watchdog (audit R-P2 / §2.21)
 * A `withFrameNanos` loop measures real frame cadence and reports each frame
 * start/duration (converted to master-elapsed time via [masterTimeOf]) to
 * [onFrameReport].
 */
@Composable
fun PresenceOrb(
    params: StateFlow<OrbRenderParams>,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    showEmissionField: Boolean = true,
    onFrameReport: (frameStartElapsedNanos: Long, frameDurationNanos: Long) -> Unit = { _, _ -> },
    /**
     * §2.31 resource budget ([CompanionCore.budget]); null = no pacing cap
     * (render every vsync — the fallback/preview path).
     */
    budget: StateFlow<MobileResourceManagement.ResourceBudget?> = MutableStateFlow(null),
    /** §2.31 render suspension — whole process backgrounded (plan §1.7). */
    suspended: StateFlow<Boolean> = MutableStateFlow(false),
    /** Convert a `withFrameNanos` timestamp into master-elapsed nanoseconds. */
    masterTimeOf: (frameTimeNanos: Long) -> Long = { it },
    /** Frame period for a target fps, from the master clock (audit F3/R-P5). */
    framePeriodNanos: (targetFps: Double) -> Long =
        { fps -> if (fps > 0.0) (1_000_000_000L / fps).toLong() else 0L }
) {
    val current by params.collectAsStateWithLifecycle()
    val tierBudget by budget.collectAsStateWithLifecycle()
    val isSuspended by suspended.collectAsStateWithLifecycle()

    // Gated renderer inputs — only the fields the renderer keys on change
    // trigger a renderer-side recompute (§12.3 crossfade reads `color`).
    val reactorState by remember {
        derivedStateOf { current.reactorState }
    }
    val targetColor by remember {
        derivedStateOf { current.color }
    }
    val crossfadeMs by remember {
        derivedStateOf { current.crossfadeMs }
    }
    val audioAmplitude by remember {
        derivedStateOf { current.audioAmplitude }
    }
    // The §2.31-capped particle density flows straight through to the renderer.
    val particleDensity by remember {
        derivedStateOf { current.particleDensity }
    }

    // §12.3 — state-to-state color crossfade (0ms snaps for inert states).
    val animatedColor: Color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = crossfadeMs.toInt(), easing = LinearEasing),
        label = "presence-orb-color"
    )

    // §2.21 paced render clock (audit F3): a monotonic render time advanced
    // only at the tier's frame cadence. The renderer samples all animation
    // from this time, so a LOW tier redraws at its capped fps instead of 60fps,
    // and a backgrounded (suspended) orb holds a static frame. Frame cadence
    // is reported every vsync regardless, feeding the §2.31 watchdog.
    var renderTimeMs by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        var prevStart = -1L
        var lastSeen = -1L
        var lastRendered = -1L
        var accMs = 0L
        while (true) {
            withFrameNanos { start ->
                if (isSuspended) {
                    // Frozen: no time advance, no reports. Reset so resume does
                    // not jump the clock by the whole background gap.
                    lastSeen = -1L
                    return@withFrameNanos
                }
                if (lastSeen >= 0L) accMs += (start - lastSeen) / 1_000_000L
                lastSeen = start
                if (prevStart >= 0L) onFrameReport(masterTimeOf(prevStart), start - prevStart)
                prevStart = start
                val period = framePeriodNanos(tierBudget?.targetFps ?: 0.0)
                if (lastRendered >= 0L && start - lastRendered < period) return@withFrameNanos
                lastRendered = start
                // Commit the advanced time only when this frame actually renders
                // — so recomposition happens at the paced cadence, not every vsync.
                renderTimeMs = accMs
            }
        }
    }

    OrbRenderer(
        state = reactorState,
        size = size,
        modifier = modifier,
        showEmissionField = showEmissionField,
        audioAmplitude = audioAmplitude,
        colorOverride = animatedColor,
        particleCountOverride = particleDensity,
        frameTimeMs = renderTimeMs.toFloat()
    )
}
