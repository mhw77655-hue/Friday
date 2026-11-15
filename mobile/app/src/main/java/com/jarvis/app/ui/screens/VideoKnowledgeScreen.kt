package com.jarvis.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import com.jarvis.app.video.VideoIntakeRepository
import com.jarvis.app.video.VideoKnowledgeItem
import kotlinx.coroutines.launch

@Composable
fun VideoKnowledgeScreen() {
    val context = LocalContext.current
    val repository = remember(context) { VideoIntakeRepository(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<VideoKnowledgeItem>()) }
    var status by remember { mutableStateOf("Show Friday a video using Android Share, or import a downloaded video.") }

    fun refresh() {
        entries = repository.entries()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                status = "Importing video and extracting keyframes…"
                runCatching { repository.importUri(uri) }
                    .onSuccess {
                        status = "Imported. ${it.framePaths.size} keyframes are ready for visual interpretation."
                        refresh()
                    }
                    .onFailure { status = it.message ?: "Video import failed." }
            }
        }
    }


    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                status = "Reading TikTok export and collecting favourite-video links…"
                runCatching { repository.importTikTokExport(uri) }
                    .onSuccess {
                        status = "Imported ${it.size} favourite-video links. Links are saved; media still needs to be shared or otherwise made available."
                        refresh()
                    }
                    .onFailure { status = it.message ?: "TikTok export import failed." }
            }
        }
    }

    LaunchedEffect(repository) { refresh() }

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("FRIDAY · VIDEO EXPERIENCE", style = JarvisType.Title)
        Text(
            "Show her videos from TikTok or another app. Friday stores shared media privately, extracts a small set of keyframes, and keeps the source linked to her experience history.",
            style = JarvisType.Body
        )
        Button(onClick = { picker.launch(arrayOf("video/*")) }) {
            Text("Import a downloaded video")
        }
        Button(onClick = {
            exportPicker.launch(arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream"))
        }) {
            Text("Import TikTok data export")
        }
        Text(status, style = JarvisType.Technical)
        Text("VIDEO LIBRARY · ${entries.size}", style = JarvisType.Label)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = JarvisColors.SurfacePanel)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(item.title, style = JarvisType.Body)
                        Text(
                            when {
                                item.sharedUrl != null -> item.sharedUrl
                                else -> "${item.durationMs / 1000}s · ${item.width}×${item.height} · ${item.framePaths.size} keyframes"
                            },
                            style = JarvisType.Technical
                        )
                        Text(item.status, style = JarvisType.Technical)
                    }
                }
            }
        }
    }
}
