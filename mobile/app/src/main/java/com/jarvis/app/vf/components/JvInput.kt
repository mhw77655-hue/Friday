package com.jarvis.app.vf.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.jarvis.app.vf.tokens.JvTokens
import com.jarvis.app.vf.typography.TypographyEngine

/**
 * JvInput (§05 §5.2, §17.2): Glass material, bottom-hairline only
 * (no full box border) in Steel, brightening to state color on focus.
 * The caret pulses at the ambient breathing rate (§3.3) — even the
 * text cursor obeys the reactor's heartbeat.
 */
@Composable
fun JvInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = JvTokens.StateIdle,
    placeholder: String = ""
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .background(JvTokens.DeepSpace.copy(alpha = JvTokens.OpacityGlass)),
        textStyle = TypographyEngine.Body,
        placeholder = {
            if (placeholder.isNotBlank()) {
                Text(text = placeholder, style = TypographyEngine.Caption.copy(color = JvTokens.TextSecondary))
            }
        },
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            unfocusedIndicatorColor = JvTokens.Steel,
            focusedIndicatorColor = accent,
            cursorColor = accent
        ),
        singleLine = true
    )
}
