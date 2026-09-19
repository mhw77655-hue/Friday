package com.jarvis.app.vf.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
 * JvNotification (§05 §5.4, §17.4): Glass panel, icon + two-tier
 * text (label + one line), auto-dismiss fading via ease-panel-out.
 * Slides in from a screen edge over DurTransition.
 */
@Composable
fun JvNotification(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = JvTokens.StateIdle,
    icon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(JvTokens.DeepSpace.copy(alpha = JvTokens.OpacityGlass))
            .border(
                shape = RoundedCornerShape(6.dp),
                width = 1.dp,
                color = accent.copy(alpha = JvTokens.AccentHairlineAlpha)
            )
            .padding(JvTokens.Space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JvTokens.Space3)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) { icon() }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(JvTokens.Space1),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title.uppercase(),
                style = TypographyEngine.Label.copy(color = accent)
            )
            Text(
                text = message,
                style = TypographyEngine.Body.copy(color = JvTokens.TextSecondary)
            )
        }
    }
}
