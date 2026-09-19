package com.jarvis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisEngine
import com.jarvis.app.inbox.InboxTaskStatus
import com.jarvis.app.inbox.LocalTaskInbox
import com.jarvis.app.inbox.TaskEntry
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.flow.map

/**
 * Task Inbox Screen — displays queued tasks awaiting execution.
 * Wired to the LocalTaskInbox backend.
 */
@Composable
fun TaskInboxScreen(
    taskInbox: LocalTaskInbox,
    onNavigate: (String) -> Unit
) {
    val tasks by taskInbox.tasks
        .map { list ->
            list.filter { it.status == InboxTaskStatus.PENDING || it.status == InboxTaskStatus.RUNNING || it.status == InboxTaskStatus.WAITING_APPROVAL }
                .map { TaskEntry.from(it) }
        }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
            .padding(16.dp)
    ) {
        Text(
            text = "TASK INBOX",
            style = JarvisType.Label
        )

        Text(
            text = "${tasks.size} pending task(s)",
            style = JarvisType.Body.copy(fontSize = 14.sp),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn {
            items(tasks) { task ->
                TaskRow(task = task, onNavigate = onNavigate)
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntry, onNavigate: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisColors.SurfacePanel)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = task.title,
                    style = JarvisType.Body.copy(fontSize = 14.sp)
                )
                Text(
                    text = task.status,
                    style = JarvisType.Technical.copy(fontSize = 10.sp)
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Capability: ${task.capability}",
                    style = JarvisType.Technical.copy(fontSize = 12.sp, color = JarvisColors.TextSecondary)
                )
                Text(
                    text = "Source: ${task.source}",
                    style = JarvisType.Technical.copy(fontSize = 12.sp, color = JarvisColors.TextSecondary)
                )
            }
        }
    }
}

/** Engine-held process-scoped singleton (nav graph injection). */
fun getTaskInbox(): LocalTaskInbox = JarvisEngine.taskInbox
    ?: throw IllegalStateException("JarvisEngine.init() must run before getTaskInbox()")