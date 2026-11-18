package com.jarvis.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType

private data class FridayDestination(
    val title: String,
    val route: String,
    val symbol: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val groups = listOf(
        "YOUR CONTEXT" to listOf(
            FridayDestination("Memory", "memory", "M"),
            FridayDestination("Sessions", "sessions", "S"),
            FridayDestination("Environment", "environment", "E"),
            FridayDestination("Alerts", "alerts", "A")
        ),
        "CAPABILITIES" to listOf(
            FridayDestination("Tools", "tools", "T"),
            FridayDestination("Voice studio", "voice_studio", "V"),
            FridayDestination("Visual presence", "vision", "P"),
            FridayDestination("Video experience", "video_knowledge", "▶")
        ),
        "DEVELOPMENT" to listOf(
            FridayDestination("Task inbox", "task_inbox", "↗"),
            FridayDestination("Approvals", "approvals", "✓"),
            FridayDestination("Model manager", "model", "AI"),
            FridayDestination("Model-free device lab", "model_simulation", "∞"),
            FridayDestination("System", "system", "⌘")
        ),
        "PREFERENCES" to listOf(
            FridayDestination("Settings", "settings", "⚙")
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JarvisColors.SurfacePanel,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp),
                color = JarvisColors.TextMuted.copy(alpha = 0.55f),
                shape = RoundedCornerShape(100.dp)
            ) {
                Spacer(Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Explore Friday", style = JarvisType.Headline)
            Spacer(Modifier.size(5.dp))
            Text(
                "Her context, capabilities and environment.",
                style = JarvisType.Body.copy(color = JarvisColors.TextSecondary)
            )
            Spacer(Modifier.size(22.dp))

            groups.forEach { (heading, destinations) ->
                Text(
                    heading,
                    style = JarvisType.Label.copy(color = JarvisColors.CoreThinking),
                    modifier = Modifier.padding(bottom = 9.dp)
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = JarvisColors.SurfaceRaised,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, JarvisColors.SurfaceHairline)
                ) {
                    Column {
                        destinations.forEachIndexed { index, destination ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onNavigate(destination.route)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 15.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(13.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(38.dp),
                                    color = JarvisColors.CoreIdle.copy(alpha = 0.13f),
                                    shape = RoundedCornerShape(13.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            destination.symbol,
                                            style = JarvisType.Label.copy(color = JarvisColors.CoreIdle)
                                        )
                                    }
                                }
                                Text(
                                    destination.title,
                                    style = JarvisType.Body.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Text("›", style = JarvisType.Headline.copy(color = JarvisColors.TextMuted))
                            }
                            if (index != destinations.lastIndex) {
                                HorizontalDivider(
                                    color = JarvisColors.SurfaceHairline,
                                    modifier = Modifier.padding(start = 66.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.size(19.dp))
            }
        }
    }
}
