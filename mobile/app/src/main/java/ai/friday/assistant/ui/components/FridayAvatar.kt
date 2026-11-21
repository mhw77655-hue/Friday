package ai.friday.assistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Friday's first non-anthropomorphic procedural embodiment.
 *
 * This is not a face, body, orb, or identity container. It is a continuously
 * moving topology: asymmetric filaments, branching relations and nodes. The
 * renderer has no LISTENING/THINKING/SPEAKING pose table. It responds to the
 * measured voice signal when available and otherwise continues a low-energy
 * autonomous visual motion. No finite body-state list defines the visual form.
 *
 * Geometry is generated locally with Compose Canvas. No remote asset, avatar
 * model, network service, or neural model is required.
 */
@Composable
fun FridayAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    voiceActive: Boolean = false,
    voiceLevel: Float = 0f,
    morphologySeed: Long = 0L
) {
    val motion = rememberInfiniteTransition(label = "friday-topology")
    val phase by motion.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "topology-phase"
    )
    val slowDrift by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7_500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "topology-drift"
    )
    val measuredLevel by animateFloatAsState(
        targetValue = if (voiceActive) voiceLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(70),
        label = "voice-energy"
    )

    val accent = Color(0xFF78DDF2)
    val secondary = Color(0xFFB6A6FF)
    val energy = (0.12f + measuredLevel * 0.88f).coerceIn(0.12f, 1f)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = this.size.width * 0.5f
            val cy = this.size.height * 0.5f
            val unit = this.size.minDimension
            val scale = unit / 280f
            val seedPhase = ((morphologySeed ushr 1) % 10_000L).toFloat() / 10_000f * (2f * PI.toFloat())
            val t = phase + seedPhase
            val drift = slowDrift
            val branchCount = 11 + ((morphologySeed ushr 4) % 5L).toInt()
            fun point(x: Float, y: Float) = Offset(cx + x * scale, cy + y * scale)

            // Diffuse spatial field, intentionally not a circular avatar boundary.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.13f * energy), Color.Transparent),
                    center = Offset(cx - 8f * scale, cy + 3f * scale),
                    radius = 132f * scale
                ),
                radius = 132f * scale,
                center = Offset(cx - 8f * scale, cy + 3f * scale)
            )

            // An irregular, open central structure rather than a head or orb.
            val nucleus = Path().apply {
                moveTo(point(-18f, -20f).x, point(-18f, -20f).y)
                cubicTo(
                    point(-2f, -34f).x, point(-2f, -34f).y,
                    point(21f, -18f).x, point(21f, -18f).y,
                    point(17f, 2f).x, point(17f, 2f).y
                )
                cubicTo(
                    point(30f, 22f).x, point(30f, 22f).y,
                    point(1f, 34f).x, point(1f, 34f).y,
                    point(-17f, 18f).x, point(-17f, 18f).y
                )
                cubicTo(
                    point(-35f, 5f).x, point(-35f, 5f).y,
                    point(-31f, -7f).x, point(-31f, -7f).y,
                    point(-18f, -20f).x, point(-18f, -20f).y
                )
                close()
            }
            drawPath(
                nucleus,
                Brush.linearGradient(
                    listOf(Color(0xFF172C42).copy(alpha = 0.9f), Color(0xFF263D59).copy(alpha = 0.42f)),
                    point(-25f, -28f),
                    point(28f, 30f)
                )
            )
            drawPath(nucleus, accent.copy(alpha = 0.66f * energy), style = Stroke(1.25f * scale))

            // Unequal filaments grow from different points and continually bend.
            // No fixed expression or finite activity-to-pose mapping exists.
            for (index in 0 until branchCount) {
                val angle = (2.0 * PI * index / branchCount + 0.19 * sin(t * 0.23 + index)).toFloat()
                val rootRadius = 17f + 5f * sin(t * 0.31f + index * 1.7f)
                val length = (66f + 29f * sin(index * 2.13f + 0.7f) + 12f * sin(t * 0.41f + index)).coerceAtLeast(35f)
                val bend = 13f * sin(t * 0.37f + index * 0.9f) + drift * 8f
                val dx = cos(angle)
                val dy = sin(angle)
                val px = -dy
                val py = dx
                val sx = dx * rootRadius
                val sy = dy * rootRadius
                val ex = dx * length + px * bend
                val ey = dy * length + py * bend
                val c1x = dx * (length * 0.38f) + px * (bend * 0.8f)
                val c1y = dy * (length * 0.38f) + py * (bend * 0.8f)
                val c2x = dx * (length * 0.73f) - px * (bend * 0.55f)
                val c2y = dy * (length * 0.73f) - py * (bend * 0.55f)

                val filament = Path().apply {
                    moveTo(point(sx, sy).x, point(sx, sy).y)
                    cubicTo(
                        point(c1x, c1y).x, point(c1x, c1y).y,
                        point(c2x, c2y).x, point(c2x, c2y).y,
                        point(ex, ey).x, point(ex, ey).y
                    )
                }
                val alpha = (0.30f + 0.42f * energy + 0.12f * sin(t + index)).coerceIn(0.12f, 0.9f)
                drawPath(filament, accent.copy(alpha = alpha), style = Stroke((0.8f + energy * 0.8f) * scale, cap = StrokeCap.Round))

                // Secondary, shorter offshoot: asymmetrical and not mirrored.
                if (index % 2 == 0) {
                    val branchAtX = sx + (ex - sx) * 0.63f
                    val branchAtY = sy + (ey - sy) * 0.63f
                    val side = if (index % 4 == 0) 1f else -1f
                    val twigLength = 17f + 9f * (0.5f + 0.5f * sin(t * 0.53f + index))
                    val tx = branchAtX + px * side * twigLength + dx * 7f
                    val ty = branchAtY + py * side * twigLength + dy * 7f
                    val twig = Path().apply {
                        moveTo(point(branchAtX, branchAtY).x, point(branchAtX, branchAtY).y)
                        quadraticTo(
                            point(branchAtX + px * side * twigLength * 0.72f, branchAtY + py * side * twigLength * 0.72f).x,
                            point(branchAtX + px * side * twigLength * 0.72f, branchAtY + py * side * twigLength * 0.72f).y,
                            point(tx, ty).x,
                            point(tx, ty).y
                        )
                    }
                    drawPath(twig, secondary.copy(alpha = 0.48f * energy), style = Stroke(0.8f * scale, cap = StrokeCap.Round))
                    drawCircle(secondary.copy(alpha = 0.62f * energy), 2.0f * scale, point(tx, ty))
                }

                // Distal nodes drift along their filaments; node size follows
                // measured audio energy without pretending to be lip-sync.
                val pulse = 2.0f + 1.8f * energy * (0.5f + 0.5f * sin(t * 1.7f + index * 1.3f))
                drawCircle(accent.copy(alpha = 0.10f * energy), pulse * 2.5f * scale, point(ex, ey))
                drawCircle(accent.copy(alpha = 0.88f * energy), pulse * scale, point(ex, ey))
            }

            // A few cross-links create a topology, not independent decorative rays.
            for (index in 0 until 6) {
                val a = (index * 2) % branchCount
                val b = (a + 3 + index % 2) % branchCount
                fun endpoint(i: Int): Offset {
                    val angle = (2.0 * PI * i / branchCount + 0.19 * sin(t * 0.23 + i)).toFloat()
                    val length = (66f + 29f * sin(i * 2.13f + 0.7f) + 12f * sin(t * 0.41f + i)).coerceAtLeast(35f)
                    val bend = 13f * sin(t * 0.37f + i * 0.9f) + drift * 8f
                    return point(cos(angle) * length - sin(angle) * bend, sin(angle) * length + cos(angle) * bend)
                }
                drawLine(
                    secondary.copy(alpha = 0.12f * energy),
                    endpoint(a),
                    endpoint(b),
                    strokeWidth = 0.65f * scale
                )
            }

            // A small internal glint moves through the structure continuously.
            val glintX = 9f * sin(t * 0.61f) + 3f * drift
            val glintY = 11f * cos(t * 0.47f)
            drawCircle(Color.White.copy(alpha = 0.72f), 2.1f * scale, point(glintX, glintY))
        }
    }
}
