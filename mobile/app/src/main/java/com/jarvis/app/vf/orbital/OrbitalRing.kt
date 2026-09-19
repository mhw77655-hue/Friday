package com.jarvis.app.vf.orbital

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared orbital-ring path builder used by both reactor renderers.
 *
 * A wobbling ring — the organic orbital motion (§2.7) produced by a
 * 3-octave sum-of-sines "noise" function animated over time.
 *
 * [strokeWidthDp] is a density-independent stroke width: raw-px strokes
 * (0.5–1.8f) vanish or alias inconsistently across densities, so the ring
 * is stroked in dp and converted through the current [Density].
 */
internal fun DrawScope.drawWobblingRing(
    center: Offset,
    radiusPx: Float,
    color: Color,
    tiltRad: Double,
    angularSpeed: Float,
    noisePhase: Float,
    strokeWidthDp: Float = 1.5f
) {
    val cosTilt = cos(tiltRad).toFloat()
    val sinTilt = sin(tiltRad).toFloat()
    val wobbleAmp = radiusPx * 0.09f
    val strokeWidth = with(density) { strokeWidthDp.dp.toPx() }

    val path = Path()
    val steps = 128
    for (i in 0..steps) {
        val t = i.toFloat() / steps * 2f * Math.PI.toFloat()
        val wobble = (sin(t * 3f + noisePhase * 0.7f).toFloat() * 0.5f +
                      sin(t * 5f - noisePhase * 1.3f).toFloat() * 0.3f +
                      sin(t * 7f + noisePhase * 2.1f).toFloat() * 0.2f) * wobbleAmp
        val r = radiusPx + wobble
        val x = center.x + sin(t * angularSpeed + noisePhase * 0.05f).toFloat() * r * cosTilt
        val y = center.y + cos(t * angularSpeed + noisePhase * 0.05f).toFloat() * r * sinTilt
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}
