package com.jarvis.app.vf.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarvis.app.vf.tokens.JvTokens
import com.jarvis.app.vf.typography.TypographyEngine

/**
 * JvButton (§05_COMPONENTS §5.1, §17.1):
 * Glass material, hairline border in state color at 20% opacity,
 * label in `label` or `body` size. On press: micro-duration brightness
 * pulse (§12.1) in state color. No shape change, no shadow.
 * Never filled/solid.
 */
@Composable
fun JvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = JvTokens.StateIdle,
    label: String = "",
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit = { Text(text = label, style = TypographyEngine.Body) }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val borderColor = accent.copy(alpha = JvTokens.AccentHairlineAlpha)
    val bg = if (isPressed) accent.copy(alpha = 0.08f) else Color.Transparent

    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = accent),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) { content() }
}

/**
 * JvTextButton (ghost variant):
 * Same rules as JvButton but no visible background at rest; press pulse only.
 */
@Composable
fun JvTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = JvTokens.StateIdle,
    label: String = "",
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val borderColor = accent.copy(alpha = JvTokens.AccentHairlineAlpha)

    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Text(text = label, style = TypographyEngine.Body.copy(color = accent))
    }
}
