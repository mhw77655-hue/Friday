package com.jarvis.app.vf.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
 * JvWindow (§05 §3.1, §17): full-screen or large sub-view.
 * Glass material (§6.1), hairline border in state color at 20% opacity,
 * never a solid filled border. Occupies Layer 3 as a region.
 */
@Composable
fun JvWindow(
    modifier: Modifier = Modifier,
    accent: Color = JvTokens.StateIdle,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(JvTokens.DeepSpace.copy(alpha = JvTokens.OpacityGlass))
            .border(BorderStroke(1.dp, accent.copy(alpha = JvTokens.AccentHairlineAlpha)))
            .padding(JvTokens.Space4)
    ) { content() }
}

/**
 * JvPanel (§05 §3.2, §17.2): the primary content unit.
 * Glass material, generous padding, one accent icon (top-left),
 * strict three-tier hierarchy (§03 §4): label → title → description.
 * Panels never nest more than one level deep.
 */
@Composable
fun JvPanel(
    modifier: Modifier = Modifier,
    accent: Color = JvTokens.StateIdle,
    header: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(JvTokens.DeepSpace.copy(alpha = JvTokens.OpacityGlass))
            .border(BorderStroke(1.dp, accent.copy(alpha = JvTokens.AccentHairlineAlpha)))
            .padding(JvTokens.Space6)
    ) {
        if (header != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(JvTokens.Space2),
                content = header
            )
        }
        content()
    }
}

/**
 * JvCard (§05 §3.3, §17.7): smaller, self-contained, tappable units.
 * Key visual (icon/swatch) centered above a two-line label/description pair.
 * Glass material; selected card gets accent border.
 */
@Composable
fun JvCard(
    modifier: Modifier = Modifier,
    accent: Color = JvTokens.StateIdle,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (selected) accent else JvTokens.Steel.copy(alpha = 0.2f)
    Column(
        modifier = modifier
            .background(JvTokens.DeepSpace.copy(alpha = JvTokens.OpacityGlass))
            .border(BorderStroke(1.dp, borderColor))
            .padding(JvTokens.Space3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(JvTokens.Space2)
    ) { content() }
}

/**
 * JvTieredPanel: the three-tier hierarchy (§03 §4) rendered inside
 * a Panel. Label → Title → Description. No panel may exceed this.
 */
@Composable
fun JvTieredPanel(
    label: String,
    title: String,
    description: String = "",
    modifier: Modifier = Modifier,
    accent: Color = JvTokens.StateIdle,
    icon: @Composable (() -> Unit)? = null
) {
    JvPanel(
        modifier = modifier,
        accent = accent,
        header = {
            if (icon != null) icon()
            Text(text = label.uppercase(), style = TypographyEngine.Label)
        }
    ) {
        if (title.isNotBlank()) Text(text = title, style = TypographyEngine.Heading)
        if (description.isNotBlank()) Text(text = description, style = TypographyEngine.Body.copy(color = JvTokens.TextSecondary))
    }
}
