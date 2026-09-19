package com.jarvis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType

/**
 * Honest empty states — Missions/Sandbox/Memory should be built
 * alongside their real backends, not filled with fake data now.
 */
@Composable
fun MissionsScreen() {
    EmptyStateScreen(message = "No missions yet — JARVIS hasn't detected a capability gap.")
}

@Composable
fun SandboxScreen() {
    com.jarvis.app.vf.demo.VisualFoundationDemo()
}

@Composable
fun MemoryScreen() {
    EmptyStateScreen(message = "Memory isn't connected yet — local recall/archival storage exists outside this app and hasn't been wired in.")
}

@Composable
private fun EmptyStateScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = JarvisType.Body.copy(color = JarvisColors.TextSecondary),
            textAlign = TextAlign.Center
        )
    }
}
