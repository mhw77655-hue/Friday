package ai.friday.assistant.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.friday.assistant.ui.components.pressScale
import ai.friday.assistant.ui.components.GlassPanel
import androidx.compose.ui.graphics.Color
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent
    ) {
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            listOf(
                "Task Inbox" to "task_inbox",
                "Approvals" to "approvals",
                "Alerts" to "alerts",
                "Sessions" to "sessions",
                "Environment" to "environment",
                "Model Manager" to "model",
                "Model-Free Device Lab" to "model_simulation",
                "Visual Presence" to "vision",
                "Video Experience" to "video_knowledge",
                "Voice Studio" to "voice_studio",
                "AI Table" to "ai_table",
                "Memory" to "memory",
                "Tools" to "tools",
                "System" to "system",
                "Settings" to "settings"
            ).forEach { (label, route) ->
                Text(
                    text = label,
                    style = FridayType.Body,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale {
                            onNavigate(route)
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
        }
        }
    }
}
