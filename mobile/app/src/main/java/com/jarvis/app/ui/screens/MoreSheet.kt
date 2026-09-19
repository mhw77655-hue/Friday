package com.jarvis.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.app.ui.components.pressScale
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType

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
        containerColor = JarvisColors.SurfacePanel
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            listOf(
                "Task Inbox" to "task_inbox",
                "Approvals" to "approvals",
                "Alerts" to "alerts",
                "Sessions" to "sessions",
                "Environment" to "environment",
                "Model Manager" to "model",
                "Memory" to "memory",
                "Tools" to "tools",
                "System" to "system",
                "Sandbox" to "sandbox",
                "Settings" to "settings"
            ).forEach { (label, route) ->
                Text(
                    text = label,
                    style = JarvisType.Body,
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
