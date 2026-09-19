package com.jarvis.app.vf.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * JvDialog (§05 §5.5, §17.5): Full Window treatment (§8.1),
 * always centered reactor mini-render at top if system-action dialog.
 * Glass, hairline state border, content follows three-tier hierarchy.
 * Dialogs are never purely typographic when a state is involved.
 */
@Composable
fun JvDialog(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = JvTokens.StateIdle,
    header: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(JvTokens.DeepSpace.copy(alpha = JvTokens.OpacityGlass))
            .border(
                shape = RoundedCornerShape(6.dp),
                width = 1.dp,
                color = accent.copy(alpha = JvTokens.AccentHairlineAlpha)
            )
            .padding(JvTokens.Space6),
        verticalArrangement = Arrangement.spacedBy(JvTokens.Space4)
    ) {
        // Mini-render of the relevant state (§17.5)
        if (header != null) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { header() }
            Spacer(modifier = Modifier.height(JvTokens.Space2))
        }

        // Three-tier: label → title → description
        Text(
            text = title,
            style = TypographyEngine.Heading.copy(color = JvTokens.TextPrimary),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = message,
            style = TypographyEngine.Body.copy(color = JvTokens.TextSecondary),
            modifier = Modifier.fillMaxWidth()
        )

        if (footer != null) {
            Spacer(modifier = Modifier.height(JvTokens.Space2))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { footer() }
        }
    }
}
