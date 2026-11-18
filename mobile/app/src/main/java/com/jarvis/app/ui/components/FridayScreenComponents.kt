package com.jarvis.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType

/**
 * Shared visual primitives for Friday's native screens.
 *
 * These are presentation components only. They never own state, route
 * actions, classify cognition, or decide which capability should run.
 */
@Composable
fun FridayScreenHeader(
    eyebrow: String,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FridayBrandMark(Modifier.size(27.dp))
            Text(
                eyebrow.uppercase(),
                style = JarvisType.Label.copy(color = JarvisColors.CoreThinking)
            )
        }
        Text(
            title,
            style = JarvisType.Display.copy(fontSize = 28.sp, lineHeight = 34.sp)
        )
        Text(
            description,
            style = JarvisType.Body.copy(color = JarvisColors.TextSecondary)
        )
    }
}

@Composable
fun FridaySectionHeading(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title.uppercase(), style = JarvisType.Label.copy(color = JarvisColors.CoreThinking))
        if (!detail.isNullOrBlank()) {
            Text(detail, style = JarvisType.Technical.copy(color = JarvisColors.TextMuted))
        }
    }
}

@Composable
fun FridayPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = JarvisColors.SurfacePanel,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, JarvisColors.SurfaceHairline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun FridayMetricTile(
    label: String,
    value: String,
    accent: Color = JarvisColors.CoreIdle,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = JarvisColors.SurfaceRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, JarvisColors.SurfaceHairline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                value,
                style = JarvisType.Headline.copy(fontSize = 21.sp, color = accent)
            )
            Text(
                label.uppercase(),
                style = JarvisType.Label.copy(color = JarvisColors.TextMuted)
            )
        }
    }
}

@Composable
fun FridayEmptyPanel(
    title: String,
    description: String
) {
    FridayPanel {
        Text(title, style = JarvisType.Headline.copy(fontSize = 18.sp))
        Text(description, style = JarvisType.Body.copy(color = JarvisColors.TextSecondary))
    }
}
