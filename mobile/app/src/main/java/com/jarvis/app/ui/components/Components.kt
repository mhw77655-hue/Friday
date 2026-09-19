package com.jarvis.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.app.JarvisEngine
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.delay

/**
 * JarvisEngine.capabilities is a plain nullable property loaded async on
 * its own thread (confirmed by DiagnosticsViewModel's own poll loop:
 * `while (JarvisEngine.capabilities == null && attempts < 20) { delay(100) }`),
 * NOT a StateFlow. This polls it the same way instead of calling
 * collectAsStateWithLifecycle() on something that isn't a Flow.
 *
 * ASSUMPTION (unverified — JarvisEngine.kt source wasn't available when
 * this was written): the loaded object exposes `.capabilities` as a
 * Map<String, Boolean>, matching the capability_manifest.json shape.
 * If the real property/field name differs, only the one line marked
 * below needs to change.
 */
@Composable
fun rememberCapabilitySnapshot(): State<Map<String, Boolean>?> {
    return produceState<Map<String, Boolean>?>(initialValue = null) {
        while (true) {
            value = JarvisEngine.capabilities?.capabilities // <- verify field name against real JarvisEngine.kt
            delay(300)
        }
    }
}

/**
 * Living-UI pass: dot color now crossfades (300ms, matches design spec
 * §25 "state-chip color transitions, never instant snap") instead of
 * snapping on recomposition. Colors that mean "confident/alive" get a
 * slow subtle pulse on top of the crossfade — this is still
 * state-driven, not decorative: it reads as "alive" only when the
 * underlying signal actually is.
 *
 * Explicit allowlist, not a denylist. SystemScreen's genericDotColor()
 * falls through to CoreUnknown (gray) for any status it doesn't
 * recognize — that's a "not confident" signal, same family as
 * CoreOffline, not an active one. A denylist that only excluded
 * Offline/Warning would have pulsed CoreUnknown too, which is wrong:
 * design principle #4 is "alive or not, confident or not" — pulsing
 * a gray "unknown" dot says confident when the color itself says the
 * opposite.
 */
private fun isConfidentState(color: Color) =
    color == JarvisColors.CoreSuccess ||
        color == JarvisColors.CoreIdle ||
        color == JarvisColors.CoreThinking

@Composable
fun StateChip(label: String, dotColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Dot(dotColor, pulse = isConfidentState(dotColor))
        Text(text = label, style = JarvisType.Technical)
    }
}

@Composable
fun CapabilityRow(
    name: String,
    active: Boolean,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dot = if (active) JarvisColors.CoreSuccess else JarvisColors.CoreOffline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, style = JarvisType.Technical)
        Dot(dot, pulse = active)
    }
}

@Composable
fun VerificationBadge(verified: Boolean, modifier: Modifier = Modifier) {
    val color = if (verified) JarvisColors.CoreSuccess else JarvisColors.CoreWarning
    val label = if (verified) "verified" else "unverified"
    Row(
        modifier = modifier
            .background(JarvisColors.SurfacePanel, RoundedCornerShape(4.dp))
            .border(1.dp, JarvisColors.SurfaceHairline, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Dot(color, size = 6.dp)
        Text(text = label, style = JarvisType.Label)
    }
}

@Composable
private fun Dot(color: Color, size: Dp = 8.dp, pulse: Boolean = false) {
    // Crossfade: 300ms tween, matches design spec's "never instant snap" rule.
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "dotColorCrossfade"
    )

    val pulseAlpha = if (pulse) {
        val infinite = rememberInfiniteTransition(label = "dotPulse")
        val alpha by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotPulseAlpha"
        )
        alpha
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .size(size)
            .alpha(pulseAlpha)
            .background(animatedColor, CircleShape)
    )
}

/**
 * Horizon progress arc — sits below the Orb, fills left-to-right with
 * `progress`. Deliberately fed by something REAL (capability
 * active/total ratio from HomeScreen) rather than a placeholder number.
 * The Mission screen has no real progress signal yet (it's an honest
 * empty state — see UnstartedScreens.kt), so this does NOT try to show
 * mission progress; that would be exactly the "decorative animation
 * with no data behind it" the design spec rules out. Capability
 * completion is the one progress-shaped number that's actually real
 * right now.
 */
@Composable
fun HorizonArc(progress: Float, color: Color, modifier: Modifier = Modifier, width: Dp = 200.dp) {
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "horizonProgress"
    )
    androidx.compose.foundation.Canvas(modifier = modifier.size(width = width, height = 6.dp)) {
        val y = this.size.height / 2f
        drawLine(
            color = JarvisColors.SurfaceHairline,
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(this.size.width, y),
            strokeWidth = 2f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(this.size.width * animatedProgress, y),
            strokeWidth = 2f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

/**
 * Shared press-scale micro-interaction for tappable rows (Home's
 * capability summary bar, More sheet items). Replaces plain
 * Modifier.clickable — scales down to 0.97x on press, eases back to 1x
 * on release. No spring/bounce overshoot per design spec §25 ("motion
 * communicates state changes, not delight-for-its-own-sake") — this is
 * a deliberately restrained press acknowledgment, not a bounce effect.
 */
@Composable
fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "pressScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
