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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.session.Session
import com.jarvis.app.session.SessionManager
import com.jarvis.app.ui.components.FridayEmptyPanel
import com.jarvis.app.ui.components.FridayMetricTile
import com.jarvis.app.ui.components.FridayScreenHeader
import com.jarvis.app.ui.components.FridaySectionHeading
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Saved conversation sessions backed by SessionManager.
 * Opening a row inspects history; it does not silently reactivate the session.
 */
@Composable
fun SessionsScreen(
    sessionManager: SessionManager,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val sessions by sessionManager.sessions.collectAsStateWithLifecycle()
    val activeSession by sessionManager.activeSession.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
    ) {
        FridayScreenHeader(
            eyebrow = "Continuity across conversations",
            title = "Sessions",
            description = "Saved conversations on this device. Open one to inspect its history without changing the active conversation."
        )

        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FridayMetricTile(
                label = "Saved",
                value = sessions.size.toString(),
                accent = JarvisColors.CoreThinking,
                modifier = Modifier.weight(1f)
            )
            FridayMetricTile(
                label = "Active",
                value = if (activeSession == null) "NONE" else "1",
                accent = JarvisColors.CoreSuccess,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                FridaySectionHeading("Saved conversations", "${sessions.size} records")
            }
            if (sessions.isEmpty()) {
                item {
                    FridayEmptyPanel(
                        title = "No saved sessions",
                        description = "Conversation sessions will appear here when they are created and persisted locally."
                    )
                }
            } else {
                items(sessions.sortedByDescending { it.updatedAt }, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        isActive = activeSession?.id == session.id,
                        onClick = { onNavigate("session_detail/${session.id}") },
                        onDelete = { scope.launch { sessionManager.deleteSession(session.id) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: Session,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) JarvisColors.SurfaceRaised else JarvisColors.SurfacePanel
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) JarvisColors.CoreIdle.copy(alpha = 0.45f) else JarvisColors.SurfaceHairline
        )
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null,
                        tint = if (isActive) JarvisColors.CoreIdle else JarvisColors.TextSecondary,
                        modifier = Modifier.padding(end = 11.dp)
                    )
                    Column {
                        Text(
                            "SESSION ${session.id.takeLast(8)}",
                            style = JarvisType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        )
                        Text(
                            "${session.messageCount} messages · ${session.environmentId}",
                            style = JarvisType.Technical.copy(color = JarvisColors.TextSecondary)
                        )
                    }
                }

                if (isActive) {
                    Text(
                        "ACTIVE",
                        style = JarvisType.Label.copy(color = JarvisColors.CoreSuccess)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete session",
                        tint = JarvisColors.TextMuted
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "CREATED  ${DateFormat.getDateTimeInstance().format(Date(session.createdAt))}",
                    style = JarvisType.Technical.copy(color = JarvisColors.TextMuted)
                )
                Text(
                    "UPDATED  ${DateFormat.getDateTimeInstance().format(Date(session.updatedAt))}",
                    style = JarvisType.Technical.copy(color = JarvisColors.TextMuted)
                )
            }
        }
    }
}

/** Engine-held process-scoped singleton (nav graph injection). */
fun getSessionManager(): SessionManager? = com.jarvis.app.ui.EngineAccess.sessionManager().value
