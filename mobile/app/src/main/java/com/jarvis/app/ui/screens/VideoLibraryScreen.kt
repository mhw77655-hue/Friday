package com.jarvis.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.app.video.FridayVideo
import com.jarvis.app.video.VideoInboxRepository
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun VideoLibraryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<FridayVideo>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    fun refresh() { videos = VideoInboxRepository.list(context) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                status = "Importing video…"
                val imported = runCatching { VideoInboxRepository.importUri(context, uri) }.getOrNull()
                refresh()
                status = if (imported != null) "Imported video successfully." else "Could not import this video. Check that it is a readable video file."
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Video Library", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Share a saved TikTok to Friday, or choose a video file below. Friday copies it into private app storage; nothing is uploaded.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = { picker.launch(arrayOf("video/*")) }) {
            Text("Choose video")
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (videos.isEmpty()) {
            Text("No videos yet. In TikTok choose Share → Friday, or save/download the clip and choose it here.", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("${videos.size} imported video(s)", style = MaterialTheme.typography.labelLarge)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(videos, key = { it.id }) { video ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(video.title, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(formatDuration(video.durationMs), style = MaterialTheme.typography.bodySmall)
                            Text("${video.framePaths.size} sampled frames", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Imported locally. Visual-semantic analysis is not yet connected to a multimodal model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
