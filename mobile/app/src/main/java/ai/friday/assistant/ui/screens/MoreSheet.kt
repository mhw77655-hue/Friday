package ai.friday.assistant.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ai.friday.assistant.ui.components.pressScale
import ai.friday.assistant.ui.components.GlassPanel
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType

private data class MoreDestination(val label: String, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val groups = listOf(
        "WORKSPACE" to listOf(
            MoreDestination("Task Inbox", "task_inbox"),
            MoreDestination("Approvals", "approvals"),
            MoreDestination("Alerts", "alerts"),
            MoreDestination("Sessions", "sessions"),
            MoreDestination("Environment", "environment")
        ),
        "FRIDAY" to listOf(
            MoreDestination("Individuality", "individual"),
            MoreDestination("Memory", "memory"),
            MoreDestination("Visual Presence", "vision"),
            MoreDestination("Voice Studio", "voice_studio"),
            MoreDestination("Video Experience", "video_knowledge")
        ),
        "TOOLS & SYSTEM" to listOf(
            MoreDestination("Model Manager", "model"),
            MoreDestination("Model-Free Device Lab", "model_simulation"),
            MoreDestination("AI Table", "ai_table"),
            MoreDestination("Tools", "tools"),
            MoreDestination("System", "system"),
            MoreDestination("Settings", "settings")
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent
    ) {
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Explore Friday", style = FridayType.Display)
                Text(
                    "Your workspace, continuity and connected capabilities.",
                    style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                groups.forEach { (heading, destinations) ->
                    Text(
                        heading,
                        style = FridayType.Label.copy(color = FridayColors.TextSecondary),
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )
                    destinations.forEach { destination ->
                        Text(
                            text = "›   " + destination.label,
                            style = FridayType.Body,
                            modifier = Modifier.fillMaxWidth()
                                .pressScale {
                                    onNavigate(destination.route)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 13.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
