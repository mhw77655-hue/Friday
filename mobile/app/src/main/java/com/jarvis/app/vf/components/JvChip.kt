package com.jarvis.app.vf.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarvis.app.vf.tokens.JvTokens
import com.jarvis.app.vf.typography.TypographyEngine

/**
 * JvChip (§05 §3.3, §17.7): small, self-contained, tappable unit.
 * Glass material, hairline border, two-line (label + value) in
 * TypographyEngine.hud font at card scale. Used for material swatches,
 * device cards, and similar grid-of-like-kind items.
 */
@Composable
fun JvChip(
    label: String,
    value: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = JvTokens.StateIdle,
    selected: Boolean = false
) {
    val borderColor = if (selected) accent else JvTokens.Steel.copy(alpha = 0.2f)

    Row(
        modifier = modifier
            .background(JvTokens.DeepSpace.copy(alpha = JvTokens.OpacityGlass))
            .border(BorderStroke(1.dp, borderColor))
            .padding(horizontal = JvTokens.Space3, vertical = JvTokens.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JvTokens.Space2)
    ) {
        Text(
            text = label.uppercase(),
            style = TypographyEngine.Label.copy(color = if (selected) accent else JvTokens.TextSecondary)
        )
        if (value != null) {
            Text(
                text = value,
                style = TypographyEngine.DataMono.copy(color = JvTokens.TextPrimary)
            )
        }
    }
}
