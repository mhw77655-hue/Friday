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
import com.jarvis.app.approvals.ApprovalEntry
import com.jarvis.app.approvals.LocalApprovalQueue
import com.jarvis.app.ui.components.FridayEmptyPanel
import com.jarvis.app.ui.components.FridayMetricTile
import com.jarvis.app.ui.components.FridayPanel
import com.jarvis.app.ui.components.FridayScreenHeader
import com.jarvis.app.ui.components.FridaySectionHeading
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.flow.map

/**
 * Pending approvals are read from the real LocalApprovalQueue.
 * This screen presents owner decisions; it does not grant or execute anything.
 */
@Composable
fun ApprovalsScreen(
    approvalQueue: LocalApprovalQueue,
    onNavigate: (String) -> Unit
) {
    val approvals by approvalQueue.pendingApprovals
        .map { it.map { ApprovalEntry.from(it) } }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
    ) {
        FridayScreenHeader(
            eyebrow = "Owner authority",
            title = "Approvals",
            description = "Actions waiting for an explicit permission decision. A pending request is not an executed action."
        )

        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FridayMetricTile(
                label = "Pending",
                value = approvals.size.toString(),
                accent = if (approvals.isEmpty()) JarvisColors.CoreSuccess else JarvisColors.CoreAttention,
                modifier = Modifier.weight(1f)
            )
            FridayMetricTile(
                label = "Authority",
                value = "OWNER",
                accent = JarvisColors.CoreIdle,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item { FridaySectionHeading("Waiting for a decision", "${approvals.size} requests") }
            if (approvals.isEmpty()) {
                item {
                    FridayEmptyPanel(
                        title = "No pending approvals",
                        description = "Friday has no high-risk operations currently waiting for your decision."
                    )
                }
            } else {
                items(approvals) { approval -> ApprovalRow(approval = approval) }
            }
        }
    }
}

@Composable
private fun ApprovalRow(approval: ApprovalEntry) {
    val riskColor = when (approval.riskLevel.lowercase()) {
        "critical", "high" -> JarvisColors.CoreWarning
        "medium", "elevated" -> JarvisColors.CoreAttention
        else -> JarvisColors.CoreThinking
    }
    FridayPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                approval.description,
                style = JarvisType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            Text(
                approval.riskLevel.uppercase(),
                style = JarvisType.Label.copy(color = riskColor)
            )
        }
        Text(
            "CAPABILITY  ${approval.capability}",
            style = JarvisType.Technical.copy(color = JarvisColors.TextSecondary)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("STATUS  ${approval.status}", style = JarvisType.Technical)
            Text(
                "EXPIRES  ${((approval.expiresAt - System.currentTimeMillis()) / 1000)}s",
                style = JarvisType.Technical.copy(color = JarvisColors.TextMuted)
            )
        }
    }
}

/** Engine-held process-scoped singleton (nav graph injection). */
fun getApprovalQueue(): LocalApprovalQueue? = com.jarvis.app.ui.EngineAccess.approvalQueue().value
