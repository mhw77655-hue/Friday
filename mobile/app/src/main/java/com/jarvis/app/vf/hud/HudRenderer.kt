package com.jarvis.app.vf.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.app.vf.tokens.JvTokens
import com.jarvis.app.vf.typography.TypographyEngine

/**
 * JARVIS Visual Foundation — HUD Renderer (§06_HUD).
 *
 * Implements the HUD typography contract (§7.5): floating, anchored,
 * data-mono readouts, right-aligned to their data point, with a thin
 * connecting line back to the element they describe. Always Layer-3;
 * HUD supports the focal object, it is never the focal object itself.
 *
 * At most ONE state-colored accent per readout (the salient value);
 * the rest is Steel-colored `--text-secondary`.
 */
object HudRenderer {

    /**
     * A single HUD readout — label + value pair with an anchor line.
     *
     * @param label       structural descriptor (small-caps, §7.3).
     * @param value       the data value displayed.
     * @param accent      optional state-color accent for the salient figure;
     *                    `null` = Steel default.
     * @param modifier    Compose modifier for positioning.
     */
    @Composable
    fun Readout(
        label: String,
        value: String,
        accent: Color? = null,
        modifier: Modifier = Modifier
    ) {
        Column(modifier = modifier, horizontalAlignment = Alignment.End) {
            Text(
                text = label.uppercase(),
                style = TypographyEngine.Label,
                textAlign = TextAlign.End
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                style = accent?.let { TypographyEngine.HudAccent(it) } ?: TypographyEngine.Hud,
                textAlign = TextAlign.End
            )
        }
    }

    /**
     * A floating HUD readout anchored to a data point with a connecting line.
     *
     * @param label      small-caps label (§7.3).
     * @param value      data-mono value.
     * @param accent     state-color accent for the salient figure, or null.
     * @param anchorPoint the data point the readout describes (in Canvas coordinates).
     * @param readoutOffset the offset from the anchor point to the readout position.
     * @param modifier   Compose modifier for the containing composable.
     */
    @Composable
    fun AnchoredReadout(
        label: String,
        value: String,
        accent: Color? = null,
        anchorPoint: Offset,
        readoutOffset: Offset = Offset(60f, -40f),
        modifier: Modifier = Modifier
    ) {
        // Anchor line (§8.4): thin line back to the data point
        Canvas(modifier = modifier) {
            drawLine(
                color = JvTokens.Steel.copy(alpha = 0.4f),
                start = anchorPoint,
                end = Offset(
                    anchorPoint.x + readoutOffset.x * 0.6f,
                    anchorPoint.y + readoutOffset.y * 0.6f
                ),
                strokeWidth = 1f
            )
        }
        // Readout (§7.5)
        Column(
            modifier = modifier.width(100.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = label.uppercase(),
                style = TypographyEngine.Label,
                textAlign = TextAlign.End
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                style = accent?.let { TypographyEngine.HudAccent(it) } ?: TypographyEngine.Hud,
                textAlign = TextAlign.End
            )
        }
    }

    /**
     * A real-time data stream panel (§7.5, §17.3) — rows of
     * data-mono readouts separated by hairline dividers.
     */
    @Composable
    fun DataStream(
        entries: List<Pair<String, String>>, // label → value
        accentIndex: Int = -1,
        modifier: Modifier = Modifier
    ) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entries.forEachIndexed { i, (label, value) ->
                if (i > 0) {
                    Spacer(modifier = Modifier.fillMaxWidth().width(0.5.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label.uppercase(),
                        style = TypographyEngine.Label.copy(color = JvTokens.TextSecondary),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = value,
                        style = if (i == accentIndex) TypographyEngine.HudAccent(JvTokens.StateIdle)
                                else TypographyEngine.Hud,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
