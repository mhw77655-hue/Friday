package com.friday.app.ui.screens

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
import com.friday.app.FridayEngine
import com.friday.app.approvals.ApprovalEntry
import com.friday.app.approvals.LocalApprovalQueue
import com.friday.app.ui.theme.FridayColors
import com.friday.app.ui.theme.FridayType
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
            .background(FridayColors.SurfaceBase)
            .padding(16.dp)
    ) {
        Text(
            text = "APPROVALS",
            style = FridayType.Label
        )

        Text(
            text = "${approvals.size} pending approval(s)",
            style = FridayType.Body.copy(fontSize = 14.sp),
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
        colors = CardDefaults.cardColors(containerColor = FridayColors.SurfacePanel)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = approval.description,
                    style = FridayType.Body.copy(fontSize = 14.sp)
                )
                Text(
                    text = approval.riskLevel,
                    style = FridayType.Technical.copy(fontSize = 10.sp)
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Capability: ${approval.capability}",
                    style = FridayType.Technical.copy(fontSize = 12.sp, color = FridayColors.TextSecondary)
                )
                Text(
                    text = "Status: ${approval.status}",
                    style = FridayType.Technical.copy(fontSize = 12.sp, color = FridayColors.TextSecondary)
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Expires in: ${((approval.expiresAt - System.currentTimeMillis()) / 1000)}s",
                style = FridayType.Technical.copy(fontSize = 10.sp, color = FridayColors.TextSecondary)
            )
        }
    }
}

/** Engine-held process-scoped singleton (nav graph injection). */
fun getApprovalQueue(): LocalApprovalQueue? = com.friday.app.ui.EngineAccess.approvalQueue().value