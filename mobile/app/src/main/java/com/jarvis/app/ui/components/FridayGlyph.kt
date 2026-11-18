package com.jarvis.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jarvis.app.ui.theme.JarvisColors

/**
 * Small, dependency-free Friday identity and navigation marks.
 * The identity mark is derived from the same open, branching topology as the
 * larger procedural embodiment; navigation marks are ordinary affordance
 * glyphs and do not imply fixed cognitive organs.
 */
@Composable
fun FridayBrandMark(
    modifier: Modifier = Modifier,
    primary: Color = JarvisColors.CoreThinking,
    secondary: Color = JarvisColors.CoreIdle
) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension
        val scaleX = size.width / 100f
        val scaleY = size.height / 100f
        fun p(x: Float, y: Float) = Offset(x * scaleX, y * scaleY)
        val stroke = Stroke(
            width = unit * 0.036f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val branches = listOf(
            listOf(p(50f, 50f), p(42f, 43f), p(33f, 32f), p(23f, 20f)),
            listOf(p(50f, 50f), p(61f, 46f), p(72f, 34f), p(82f, 19f)),
            listOf(p(50f, 50f), p(43f, 61f), p(32f, 72f), p(19f, 83f)),
            listOf(p(50f, 50f), p(61f, 60f), p(73f, 73f), p(84f, 83f))
        )
        branches.forEachIndexed { index, pts ->
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                cubicTo(pts[1].x, pts[1].y, pts[2].x, pts[2].y, pts[3].x, pts[3].y)
            }
            drawPath(path, if (index % 2 == 0) primary else secondary, style = stroke)
            drawCircle(
                color = if (index % 2 == 0) primary else secondary,
                radius = unit * 0.052f,
                center = pts[3]
            )
        }

        val nucleus = Path().apply {
            moveTo(p(50f, 39f).x, p(50f, 39f).y)
            cubicTo(p(61f, 38f).x, p(61f, 38f).y, p(64f, 47f).x, p(64f, 47f).y, p(59f, 55f).x, p(59f, 55f).y)
            cubicTo(p(55f, 64f).x, p(55f, 64f).y, p(43f, 63f).x, p(43f, 63f).y, p(39f, 54f).x, p(39f, 54f).y)
            cubicTo(p(34f, 46f).x, p(34f, 46f).y, p(40f, 39f).x, p(40f, 39f).y, p(50f, 39f).x, p(50f, 39f).y)
            close()
        }
        drawPath(nucleus, JarvisColors.SurfaceRaised.copy(alpha = 0.95f))
        drawPath(nucleus, primary.copy(alpha = 0.9f), style = Stroke(width = unit * 0.032f))
        drawCircle(Color.White.copy(alpha = 0.9f), radius = unit * 0.026f, center = p(51f, 50f))
    }
}

@Composable
fun FridayNavigationGlyph(
    kind: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.105f
        val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        fun line(a: Offset, b: Offset) = drawLine(color, a, b, strokeWidth, cap = StrokeCap.Round)

        when (kind) {
            "home" -> {
                val roof = Path().apply {
                    moveTo(p(0.12f, 0.48f).x, p(0.12f, 0.48f).y)
                    lineTo(p(0.5f, 0.15f).x, p(0.5f, 0.15f).y)
                    lineTo(p(0.88f, 0.48f).x, p(0.88f, 0.48f).y)
                }
                drawPath(roof, color, style = stroke)
                val house = Path().apply {
                    moveTo(p(0.25f, 0.43f).x, p(0.25f, 0.43f).y)
                    lineTo(p(0.25f, 0.85f).x, p(0.25f, 0.85f).y)
                    lineTo(p(0.75f, 0.85f).x, p(0.75f, 0.85f).y)
                    lineTo(p(0.75f, 0.43f).x, p(0.75f, 0.43f).y)
                }
                drawPath(house, color, style = stroke)
                line(p(0.45f, 0.85f), p(0.45f, 0.63f))
                line(p(0.55f, 0.85f), p(0.55f, 0.63f))
            }
            "talk" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.12f, h * 0.15f),
                    size = Size(w * 0.76f, h * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f),
                    style = stroke
                )
                val tail = Path().apply {
                    moveTo(p(0.34f, 0.77f).x, p(0.34f, 0.77f).y)
                    lineTo(p(0.25f, 0.9f).x, p(0.25f, 0.9f).y)
                    lineTo(p(0.53f, 0.77f).x, p(0.53f, 0.77f).y)
                }
                drawPath(tail, color, style = stroke)
                line(p(0.3f, 0.4f), p(0.7f, 0.4f))
                line(p(0.3f, 0.56f), p(0.58f, 0.56f))
            }
            "growth" -> {
                val path = Path().apply {
                    moveTo(p(0.5f, 0.88f).x, p(0.5f, 0.88f).y)
                    cubicTo(p(0.5f, 0.62f).x, p(0.5f, 0.62f).y, p(0.43f, 0.42f).x, p(0.43f, 0.42f).y, p(0.26f, 0.22f).x, p(0.26f, 0.22f).y)
                    moveTo(p(0.5f, 0.65f).x, p(0.5f, 0.65f).y)
                    cubicTo(p(0.53f, 0.46f).x, p(0.53f, 0.46f).y, p(0.68f, 0.34f).x, p(0.68f, 0.34f).y, p(0.84f, 0.2f).x, p(0.84f, 0.2f).y)
                }
                drawPath(path, color, style = stroke)
                drawOval(color, topLeft = p(0.08f, 0.13f), size = Size(w * 0.29f, h * 0.2f))
                drawOval(color, topLeft = p(0.65f, 0.08f), size = Size(w * 0.28f, h * 0.2f))
            }
            "more" -> {
                listOf(0.22f, 0.5f, 0.78f).forEach {
                    drawCircle(color, size.minDimension * 0.09f, p(it, 0.5f))
                }
            }
            "mic" -> {
                drawRoundRect(color, Offset(w * 0.37f, h * 0.08f), Size(w * 0.26f, h * 0.52f), androidx.compose.ui.geometry.CornerRadius(w * 0.13f), style = stroke)
                val arc = Path().apply {
                    moveTo(p(0.2f, 0.48f).x, p(0.2f, 0.48f).y)
                    cubicTo(p(0.2f, 0.82f).x, p(0.2f, 0.82f).y, p(0.8f, 0.82f).x, p(0.8f, 0.82f).y, p(0.8f, 0.48f).x, p(0.8f, 0.48f).y)
                }
                drawPath(arc, color, style = stroke)
                line(p(0.5f, 0.81f), p(0.5f, 0.95f))
            }
            "send" -> {
                val path = Path().apply {
                    moveTo(p(0.12f, 0.5f).x, p(0.12f, 0.5f).y)
                    lineTo(p(0.88f, 0.12f).x, p(0.88f, 0.12f).y)
                    lineTo(p(0.62f, 0.88f).x, p(0.62f, 0.88f).y)
                    lineTo(p(0.48f, 0.57f).x, p(0.48f, 0.57f).y)
                    close()
                }
                drawPath(path, color, style = stroke)
                line(p(0.48f, 0.57f), p(0.88f, 0.12f))
            }
        }
    }
}
