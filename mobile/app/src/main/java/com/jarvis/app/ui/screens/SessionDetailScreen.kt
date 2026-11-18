package com.jarvis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.session.MessageRole
import com.jarvis.app.session.Session
import com.jarvis.app.session.SessionManager
import com.jarvis.app.session.SessionMessage
import com.jarvis.app.ui.components.FridayBrandMark
import com.jarvis.app.ui.components.FridayEmptyPanel
import com.jarvis.app.ui.components.FridayScreenHeader
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import java.text.DateFormat
import java.util.Date

/**
 * Read-only inspection of a saved local conversation.
 *
 * Opening a session never reactivates it and never injects historical messages
 * into the current conversation. It is an inspection surface over SessionManager.
 */
@Composable
fun SessionDetailScreen(
    sessionId: String,
    sessionManager: SessionManager,
    onBack: () -> Unit
) {
    var savedSession by remember(sessionId) { mutableStateOf<Session?>(null) }
    var loading by remember(sessionId) { mutableStateOf(true) }

    LaunchedEffect(sessionId, sessionManager) {
        loading = true
        savedSession = runCatching { sessionManager.getSession(sessionId) }.getOrNull()
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text("‹  Sessions", style = JarvisType.Label.copy(color = JarvisColors.CoreThinking))
            }
            FridayBrandMark(Modifier.padding(8.dp).widthIn(max = 28.dp))
        }

        when {
            loading -> {
                FridayScreenHeader(
                    eyebrow = "Saved conversation",
                    title = "Opening session",
                    description = "Reading the session record from Friday's local store."
                )
            }
            savedSession == null -> {
                FridayScreenHeader(
                    eyebrow = "Saved conversation",
                    title = "Session unavailable",
                    description = "This session could not be found in Friday's local store."
                )
                Column(Modifier.padding(horizontal = 20.dp)) {
                    FridayEmptyPanel(
                        title = "No session record",
                        description = "The session may have been deleted or may no longer be available on this device."
                    )
                }
            }
            else -> {
                val saved = savedSession!!
                FridayScreenHeader(
                    eyebrow = "Saved conversation",
                    title = "Session ${saved.id.takeLast(8)}",
                    description = "Environment ${saved.environmentId} · ${saved.messageCount} stored messages"
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = JarvisColors.SurfacePanel,
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisColors.SurfaceHairline)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("SESSION RECORD", style = JarvisType.Label.copy(color = JarvisColors.CoreThinking))
                                Text(
                                    "Created · ${DateFormat.getDateTimeInstance().format(Date(saved.createdAt))}",
                                    style = JarvisType.Technical
                                )
                                Text(
                                    "Updated · ${DateFormat.getDateTimeInstance().format(Date(saved.updatedAt))}",
                                    style = JarvisType.Technical
                                )
                            }
                        }
                    }

                    if (saved.messages.isEmpty()) {
                        item {
                            FridayEmptyPanel(
                                title = "No stored messages",
                                description = "The session record exists, but it contains no message entries."
                            )
                        }
                    } else {
                        items(saved.messages, key = { it.id }) { message ->
                            SessionMessageRow(message)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionMessageRow(message: SessionMessage) {
    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            color = if (isUser) JarvisColors.UserBubble else JarvisColors.SurfacePanel,
            shape = RoundedCornerShape(
                topStart = if (isUser) 22.dp else 5.dp,
                topEnd = if (isUser) 5.dp else 22.dp,
                bottomEnd = 22.dp,
                bottomStart = 22.dp
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisColors.SurfaceHairline)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    "${message.role.name}",
                    style = JarvisType.Label.copy(
                        color = if (isUser) JarvisColors.CoreIdle else JarvisColors.CoreThinking
                    )
                )
                Text(message.content, style = JarvisType.Body)
                Text(
                    "${DateFormat.getDateTimeInstance().format(Date(message.timestamp))}",
                    style = JarvisType.Technical.copy(color = JarvisColors.TextMuted)
                )
            }
        }
    }
}
