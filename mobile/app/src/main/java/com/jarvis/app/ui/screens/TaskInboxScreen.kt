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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.inbox.InboxTaskStatus
import com.jarvis.app.inbox.LocalTaskInbox
import com.jarvis.app.inbox.TaskEntry
import com.jarvis.app.ui.components.FridayEmptyPanel
import com.jarvis.app.ui.components.FridayMetricTile
import com.jarvis.app.ui.components.FridayPanel
import com.jarvis.app.ui.components.FridayScreenHeader
import com.jarvis.app.ui.components.FridaySectionHeading
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.flow.map

/**
 * Task inbox backed by the real LocalTaskInbox. This view only presents
 * pending/running/approval-waiting work; it does not create or execute tasks.
 */
@Composable
fun TaskInboxScreen(
    taskInbox: LocalTaskInbox,
    onNavigate: (String) -> Unit
) {
    val tasks by taskInbox.tasks
        .map { list ->
            list.filter {
                it.status == InboxTaskStatus.PENDING ||
                    it.status == InboxTaskStatus.RUNNING ||
                    it.status == InboxTaskStatus.WAITING_APPROVAL
            }.map { TaskEntry.from(it) }
        }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val runningCount = tasks.count { it.status.equals("RUNNING", ignoreCase = true) }
    val waitingCount = tasks.count { it.status.equals("WAITING_APPROVAL", ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
    ) {
        FridayScreenHeader(
            eyebrow = "Work in progress",
            title = "Task inbox",
            description = "A transparent view of work Friday has queued, started or paused for permission."
        )

        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FridayMetricTile(
                label = "Queued",
                value = tasks.size.toString(),
                accent = JarvisColors.CoreThinking,
                modifier = Modifier.weight(1f)
            )
            FridayMetricTile(
                label = "Running",
                value = runningCount.toString(),
                accent = JarvisColors.CoreSuccess,
                modifier = Modifier.weight(1f)
            )
            FridayMetricTile(
                label = "Approval",
                value = waitingCount.toString(),
                accent = JarvisColors.CoreAttention,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item { FridaySectionHeading("Current work", "${tasks.size} tasks") }
            if (tasks.isEmpty()) {
                item {
                    FridayEmptyPanel(
                        title = "No queued work",
                        description = "Friday has no pending, running or approval-waiting tasks right now."
                    )
                }
            } else {
                items(tasks) { task -> TaskRow(task = task) }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntry) {
    val statusColor = when (task.status.uppercase()) {
        "RUNNING" -> JarvisColors.CoreSuccess
        "WAITING_APPROVAL" -> JarvisColors.CoreAttention
        else -> JarvisColors.CoreThinking
    }
    FridayPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                task.title,
                style = JarvisType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            Text(
                task.status.replace('_', ' ').uppercase(),
                style = JarvisType.Label.copy(color = statusColor)
            )
        }

        Text(
            "CAPABILITY  ${task.capability}",
            style = JarvisType.Technical.copy(color = JarvisColors.TextSecondary)
        )
        Text(
            "SOURCE  ${task.source}",
            style = JarvisType.Technical.copy(color = JarvisColors.TextMuted)
        )
    }
}

/** Engine-held process-scoped singleton (nav graph injection). */
fun getTaskInbox(): LocalTaskInbox? = com.jarvis.app.ui.EngineAccess.taskInbox().value
