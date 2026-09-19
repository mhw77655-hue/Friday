package com.jarvis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisEngine
import com.jarvis.app.session.Session
import com.jarvis.app.session.SessionManager
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Sessions Screen — displays conversation sessions.
 * Wired to the SessionManager backend.
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
            .padding(16.dp)
    ) {
        Text(
            text = "SESSIONS",
            style = JarvisType.Label
        )

        Text(
            text = "${sessions.size} session(s)",
            style = JarvisType.Body.copy(fontSize = 14.sp),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn {
            items(sessions) { session ->
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

@Composable
private fun SessionRow(
    session: Session,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(if (isActive) JarvisColors.CoreIdle.copy(alpha = 0.15f) else JarvisColors.SurfacePanel),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "Session ${session.id.takeLast(8)}",
                            style = JarvisType.Body.copy(fontSize = 14.sp)
                        )
                        Text(
                            text = "${session.messageCount} messages • ${session.environmentId}",
                            style = JarvisType.Technical.copy(fontSize = 12.sp, color = JarvisColors.TextSecondary)
                        )
                    }
                }

                if (isActive) {
                    Text(
                        text = "ACTIVE",
                        style = JarvisType.Technical.copy(fontSize = 10.sp, color = JarvisColors.CoreIdle)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete session",
                        tint = JarvisColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Created: ${SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(java.util.Date(session.createdAt))}",
                    style = JarvisType.Technical.copy(fontSize = 10.sp, color = JarvisColors.TextSecondary)
                )
                Text(
                    text = "Updated: ${SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(java.util.Date(session.updatedAt))}",
                    style = JarvisType.Technical.copy(fontSize = 10.sp, color = JarvisColors.TextSecondary)
                )
            }
        }
    }
}

/** Engine-held process-scoped singleton (nav graph injection). */
fun getSessionManager(): SessionManager = JarvisEngine.sessionManager
    ?: throw IllegalStateException("JarvisEngine.init() must run before getSessionManager()")