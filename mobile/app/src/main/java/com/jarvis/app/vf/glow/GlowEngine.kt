package com.jarvis.app.vf.glow

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jarvis.app.vf.tokens.JvTokens

/**
 * JARVIS Visual Foundation — Glow Engine.
 *
 * Implements `10_LIGHTING.md` §2 (glow rules), §3 (bloom), §4 (reflections).
 *
 * Glow is the layered-stack technique that produces radial emission without
 * `Modifier.blur` (which is a silent no-op below API 31 on real devices).
 * Every glow has a **traceable energy source** (§4.1) and the glow color
 * always matches the current state color exactly (§01_COLORS §3.3).
 *
 * **Bloom** (§4.2) applies only to the single brightest element on screen —
 * typically the ignition point or one active accent icon. It is rendered as
 * a wider, softer outer glow stack, never as a generic full-scene filter.
 *
 * Reflections (§4.3): metallic surfaces show soft, blurred reflections of the
 * reactor's color; glass panels reflect their own content faintly at 4–8%
 * opacity along the lower edge.
 */
object GlowEngine {

    /**
     * Draw a radial glow stack (layered translucent rings) around [center]
     * in [radius]. The visual Bible §4.1 and the existing Orb.kt both
     * use this exact technique — stacked semi-transparent strokes with
     * squared-falloff alpha, compatible across minSdk 26.
     *
     * @param center      center of the glow.
     * @param radius      maximum glow radius (px).
     * @param color       glow color — must match the current state color exactly.
     * @param maxLayers   number of concentric strokes (7 is canonical per the
     *                    existing Orb implementation).
     * @param layerAlpha  base alpha for the innermost layer; subsequent layers
     *                    have squared-falloff alpha (brightest at center, soft at
     *                    edge). Range [0.01–0.25].
     * @param brightness  multiplicative brightness factor (§3.3 breathing):
     *                    scales every layer's alpha so the glow field breathes
     *                    with the core. Default 1f = no modulation.
     */
    fun DrawScope.drawStackedGlow(
        center: Offset,
        radius: Float,
        color: Color,
        maxLayers: Int = 7,
        layerAlpha: Float = 0.18f,
        strokeScale: Float = 30f,
        brightness: Float = 1f
    ) {
        val effectiveRadius = radius.coerceAtLeast(0f)
        for (i in 0 until maxLayers) {
            val t = (i + 1).toFloat() / maxLayers
            val r = effectiveRadius * t
            val alpha = (layerAlpha * (1f - t * t) * brightness).coerceIn(0.005f, 1f)
            val stroke = Stroke(width = strokeScale * (1f - t))
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = r,
                center = center,
                style = stroke
            )
        }
    }

    /**
     * Bloom: a wider, softer glow around the brightest element. §4.2:
     * "Bloom radius scales with the element's actual brightness value, never
     * applied as a flat stylistic filter across the whole scene."
     *
     * Renders a single wider outer stack beyond the normal glow, at reduced
     * alpha. Only called for Layer-4 focal objects.
     */
    fun DrawScope.drawBloom(
        center: Offset,
        radius: Float,
        color: Color,
        bloomAlpha: Float = JvTokens.OpacityBloom,
        brightness: Float = 1f
    ) {
        val bloomRadius = radius * 2.2f
        val maxLayers = 10
        for (i in 0 until maxLayers) {
            val t = (i + 1).toFloat() / maxLayers
            val r = bloomRadius * t
            val alpha = (bloomAlpha * (1f - t) * brightness).coerceIn(0.005f, 1f)
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = r,
                center = center
            )
        }
    }

    /**
     * Draw the ignition point — the single brightest element on the system.
     * §2.2.1 / §5.6: this is the only pure-white element permitted.
     * Always a small, sharp, high-alpha circle at the geometric center.
     */
    fun DrawScope.drawIgnition(center: Offset, radius: Float) {
        val effective = radius.coerceAtLeast(1f)
        // Core of the ignition: IgnitionWhite at full alpha
        drawCircle(
            color = JvTokens.IgnitionWhite,
            radius = effective * 0.35f,
            center = center
        )
        // Bright inner glow
        drawCircle(
            color = JvTokens.IgnitionWhite.copy(alpha = 0.4f),
            radius = effective * 0.55f,
            center = center
        )
    }

    /**
     * Draw a glass panel's faint self-reflection along its lower edge (§4.3).
     * "Glass/holographic panels reflect their own content faintly at ~4–8%
     * opacity along their lower edge."
     */
    fun DrawScope.drawGlassReflection(
        panelBounds: Size,
        reflectionAlpha: Float = JvTokens.GlassSelfReflectionMax
    ) {
        val gradient = Brush.verticalGradient(
            colors = listOf(Color.Transparent, JvTokens.Steel.copy(alpha = reflectionAlpha)),
            startY = panelBounds.height * 0.7f,
            endY = panelBounds.height
        )
        drawRect(
            brush = gradient,
            topLeft = Offset(0f, panelBounds.height * 0.7f),
            size = Size(panelBounds.width, panelBounds.height * 0.3f)
        )
    }

    /**
     * Metallic reflection of the reactor's state color (§4.3).
     * "Metallic surfaces show soft, blurred reflections of the reactor's color
     * — never sharp mirror reflections, never zero reflection."
     */
    fun DrawScope.drawMetalReflection(
        surfaceCenter: Offset,
        surfaceRadius: Float,
        stateColor: Color,
        reflectionAlpha: Float = 0.08f,
        brightness: Float = 1f
    ) {
        val reflectionRadius = surfaceRadius * 0.7f
        drawCircle(
            color = stateColor.copy(alpha = (reflectionAlpha * brightness).coerceIn(0f, 1f)),
            radius = reflectionRadius,
            center = Offset(surfaceCenter.x, surfaceCenter.y + surfaceRadius * 0.15f)
        )
    }

    /**
     * Volumetric light shaft (§4.4): a soft radial haze that "exists as
     * atmosphere, not just surface color". Density scales with system activity.
     * [density] ∈ [0.0, 1.0] (idle near 0, active near 1).
     */
    fun DrawScope.drawVolumetricHaze(
        center: Offset,
        radius: Float,
        color: Color,
        density: Float,
        brightness: Float = 1f
    ) {
        val effectiveRadius = radius * (0.8f + 0.4f * density)
        val alpha = (JvTokens.OpacityBloom * density * 1.5f * brightness).coerceIn(0.002f, JvTokens.OpacityBloom * 3f)
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = effectiveRadius,
            center = center
        )
    }
}
