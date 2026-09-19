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
import com.jarvis.app.approvals.ApprovalEntry
import com.jarvis.app.approvals.LocalApprovalQueue
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.flow.map

/**
 * Approvals Screen — displays pending approvals for high-risk operations.
 * Wired to the LocalApprovalQueue backend.
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
            .padding(16.dp)
    ) {
        Text(
            text = "APPROVALS",
            style = JarvisType.Label
        )

        Text(
            text = "${approvals.size} pending approval(s)",
            style = JarvisType.Body.copy(fontSize = 14.sp),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn {
            items(approvals) { approval ->
                ApprovalRow(approval = approval, onNavigate = onNavigate)
            }
        }
    }
}

@Composable
private fun ApprovalRow(approval: ApprovalEntry, onNavigate: (String) -> Unit) {
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
                    text = approval.description,
                    style = JarvisType.Body.copy(fontSize = 14.sp)
                )
                Text(
                    text = approval.riskLevel,
                    style = JarvisType.Technical.copy(fontSize = 10.sp)
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Capability: ${approval.capability}",
                    style = JarvisType.Technical.copy(fontSize = 12.sp, color = JarvisColors.TextSecondary)
                )
                Text(
                    text = "Status: ${approval.status}",
                    style = JarvisType.Technical.copy(fontSize = 12.sp, color = JarvisColors.TextSecondary)
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Expires in: ${((approval.expiresAt - System.currentTimeMillis()) / 1000)}s",
                style = JarvisType.Technical.copy(fontSize = 10.sp, color = JarvisColors.TextSecondary)
            )
        }
    }
}

/** Engine-held process-scoped singleton (nav graph injection). */
fun getApprovalQueue(): LocalApprovalQueue = JarvisEngine.approvalQueue
    ?: throw IllegalStateException("JarvisEngine.init() must run before getApprovalQueue()")