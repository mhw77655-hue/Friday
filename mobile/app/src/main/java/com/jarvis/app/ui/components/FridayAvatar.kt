package com.jarvis.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.app.body.BodyState

/**
 * Lightweight, native Compose embodiment for Friday.
 *
 * All animation is procedural and drawn on Canvas: no video, remote avatar,
 * camera, 3D engine or additional model is required. Activity state comes from
 * the real BodyCoordinator state. Speaking motion is a visual activity cue;
 * it is not represented as phoneme-accurate lip sync.
 */
@Composable
fun FridayAvatar(
    state: BodyState,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val transition = rememberInfiniteTransition(label = "friday-avatar")
    val breathe by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathing"
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(180, delayMillis = 2400), RepeatMode.Reverse),
        label = "blink"
    )
    val speaking by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = if (state == BodyState.SPEAKING) 1f else 0.12f,
        animationSpec = infiniteRepeatable(tween(if (state == BodyState.SPEAKING) 190 else 900), RepeatMode.Reverse),
        label = "speaking"
    )
    val active = state in setOf(BodyState.LISTENING, BodyState.HEARING, BodyState.THINKING, BodyState.RETRIEVING, BodyState.RESPONDING, BodyState.SPEAKING)
    val accent = when (state) {
        BodyState.ERROR -> Color(0xFFFF806F)
        BodyState.LISTENING, BodyState.HEARING -> Color(0xFF77D9FF)
        BodyState.THINKING, BodyState.RETRIEVING, BodyState.RESPONDING -> Color(0xFFB9A0FF)
        BodyState.SPEAKING -> Color(0xFF8CE8D0)
        else -> Color(0xFF83CFFF)
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val unit = this.size.minDimension
            val scale = unit / 280f * breathe
            fun p(x: Float, y: Float) = Offset(cx + x * scale, cy + y * scale)

            // Soft ambient halo.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = if (active) 0.22f else 0.12f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = 132f * scale
                ),
                radius = 132f * scale,
                center = Offset(cx, cy)
            )
            drawCircle(accent.copy(alpha = 0.22f), 105f * scale, Offset(cx, cy), style = Stroke(1.2f * scale))
            drawCircle(accent.copy(alpha = 0.12f), 116f * scale, Offset(cx, cy), style = Stroke(1f * scale))

            // Face silhouette: a calm, abstract feminine digital presence.
            val face = Path().apply {
                moveTo(p(-62f, -78f).x, p(-62f, -78f).y)
                cubicTo(p(-83f, -67f).x, p(-83f, -67f).y, p(-88f, 4f).x, p(-88f, 4f).y, p(-61f, 65f).x, p(-61f, 65f).y)
                cubicTo(p(-42f, 94f).x, p(-42f, 94f).y, p(42f, 94f).x, p(42f, 94f).y, p(61f, 65f).x, p(61f, 65f).y)
                cubicTo(p(88f, 4f).x, p(88f, 4f).y, p(83f, -67f).x, p(83f, -67f).y, p(62f, -78f).x, p(62f, -78f).y)
                cubicTo(p(28f, -103f).x, p(28f, -103f).y, p(-28f, -103f).x, p(-28f, -103f).y, p(-62f, -78f).x, p(-62f, -78f).y)
                close()
            }
            drawPath(face, Brush.linearGradient(listOf(Color(0xFF243B52), Color(0xFF101C2C)), p(-80f, -100f), p(70f, 100f)))
            drawPath(face, accent.copy(alpha = 0.62f), style = Stroke(1.5f * scale))

            // Forehead light and cheek contours.
            drawLine(accent.copy(alpha = 0.24f), p(-42f, -68f), p(42f, -68f), 1f * scale)
            drawLine(accent.copy(alpha = 0.22f), p(-64f, 35f), p(-48f, 53f), 1f * scale)
            drawLine(accent.copy(alpha = 0.22f), p(64f, 35f), p(48f, 53f), 1f * scale)

            // Eyes and gaze: attention shifts subtly with state.
            val gazeX = if (state == BodyState.LISTENING || state == BodyState.HEARING) 2.5f else if (state == BodyState.THINKING) 3.5f else 0f
            val eyeY = -17f
            val eyeH = if (blink > 0.88f) 2.2f else 9f
            listOf(-34f, 34f).forEach { eyeX ->
                drawOval(
                    color = Color(0xFFE4F5FF).copy(alpha = 0.9f),
                    topLeft = p(eyeX - 13f, eyeY - eyeH / 2f),
                    size = Size(26f * scale, eyeH * scale)
                )
                if (eyeH > 4f) {
                    drawCircle(Color(0xFF75D9FF), 5.2f * scale, p(eyeX + gazeX, eyeY))
                    drawCircle(Color(0xFF071321), 2.5f * scale, p(eyeX + gazeX, eyeY))
                    drawCircle(Color.White.copy(alpha = 0.9f), 1.2f * scale, p(eyeX + gazeX - 1.8f, eyeY - 2f))
                }
                val brow = Path().apply {
                    moveTo(p(eyeX - 15f, eyeY - 15f).x, p(eyeX - 15f, eyeY - 15f).y)
                    quadraticTo(p(eyeX, eyeY - 23f).x, p(eyeX, eyeY - 23f).y, p(eyeX + 15f, eyeY - 15f).x, p(eyeX + 15f, eyeY - 15f).y)
                }
                drawPath(brow, accent.copy(alpha = 0.85f), style = Stroke(2f * scale, cap = StrokeCap.Round))
            }

            // Nose bridge, subtle and non-photorealistic.
            val nose = Path().apply {
                moveTo(p(0f, -2f).x, p(0f, -2f).y)
                quadraticTo(p(-4f, 17f).x, p(-4f, 17f).y, p(1f, 20f).x, p(1f, 20f).y)
            }
            drawPath(nose, Color(0xFF9DBFD3).copy(alpha = 0.55f), style = Stroke(1.4f * scale, cap = StrokeCap.Round))

            // Mouth opens only while the body is actually in SPEAKING state.
            val mouthWidth = 12f + 3f * speaking
            val mouthHeight = if (state == BodyState.SPEAKING) 2.5f + 8f * speaking else 1.5f
            drawOval(
                color = accent.copy(alpha = 0.9f),
                topLeft = p(-mouthWidth, 43f - mouthHeight / 2f),
                size = Size(mouthWidth * 2f * scale, mouthHeight * scale)
            )
            drawLine(Color.White.copy(alpha = 0.15f), p(-42f, 76f), p(42f, 76f), 1f * scale)
        }
    }
}
