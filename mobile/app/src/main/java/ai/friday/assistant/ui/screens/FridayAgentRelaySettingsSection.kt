package ai.friday.assistant.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import ai.friday.assistant.ui.EngineAccess
import ai.friday.assistant.inbox.InboxTask
import ai.friday.assistant.inbox.InboxTaskStatus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ai.friday.assistant.github.reporting.AgentTaskReplyStore
import ai.friday.assistant.github.reporting.AgentTaskThreadStore
import ai.friday.assistant.github.reporting.FridayAgentTaskRelay
import ai.friday.assistant.github.reporting.FridayAgentTaskRelayConsentStore
import ai.friday.assistant.github.reporting.FridayAgentTaskRelayPollWorker
import ai.friday.assistant.github.reporting.GitHubCredentialStore
import ai.friday.assistant.github.reporting.GitHubDevelopmentRepositoryClient
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
internal fun FridayAgentRelaySettingsSection(context: Context) {
    val appContext = context.applicationContext
    val consent = remember(appContext) { FridayAgentTaskRelayConsentStore(appContext) }
    val credentials = remember(appContext) { GitHubCredentialStore(appContext) }
    val replyStore = remember(appContext) { AgentTaskReplyStore(appContext.filesDir) }
    var enabled by remember { mutableStateOf(consent.isEnabled()) }
    var tokenConfigured by remember { mutableStateOf(credentials.read() != null) }
    var trustedActorsText by remember {
        mutableStateOf(consent.trustedActors().sorted().joinToString(", "))
    }
    var status by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var latestIssueUrl by remember { mutableStateOf("") }
    var latestReplies by remember { mutableStateOf(emptyList<ai.friday.assistant.github.reporting.StoredAgentTaskReply>()) }
    var pendingProposals by remember { mutableStateOf(emptyList<InboxTask>()) }
    var unreadCount by remember { mutableStateOf(0) }
    var lastPollLabel by remember { mutableStateOf("Not checked yet") }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refresh() {
        enabled = consent.isEnabled()
        tokenConfigured = credentials.read() != null
        val lastPoll = consent.lastPollAtEpochMs()
        lastPollLabel = if (lastPoll == 0L) "Not checked yet"
        else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastPoll))
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                replyStore.latest(3) to replyStore.unreadCount()
            }
            latestReplies = snapshot.first
            unreadCount = snapshot.second
            pendingProposals = EngineAccess.taskInbox().value
                ?.listTasks(InboxTaskStatus.WAITING_APPROVAL)
                ?.filter { it.source == "chatgpt-cloud" }
                .orEmpty()
        }
    }

    LaunchedEffect(Unit) { refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Public GitHub task relay", style = FridayType.Body, color = FridayColors.TextPrimary)
        Text(
            "This Friday repository is public. Task titles, issue bodies and replies are visible to anyone. This relay accepts public project engineering context only; never send private phone snapshots, personal details, conversations, memory, transcripts or credentials.",
            style = FridayType.Label,
            color = FridayColors.TextPrimary
        )
        Text(
            if (tokenConfigured) "GitHub credential: available on this phone"
            else "GitHub credential: not configured. Save the fine-grained token in the GitHub section above.",
            style = FridayType.Technical,
            color = FridayColors.CoreIdle
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Enable public project collaboration", style = FridayType.Body)
                Text("Default off · polls every 15 minutes when enabled", style = FridayType.Label)
            }
            Switch(
                checked = enabled,
                enabled = true,
                onCheckedChange = { next ->
                    if (next && !tokenConfigured) {
                        status = "Save the GitHub token in Settings before enabling the relay."
                    } else try {
                        if (next) {
                            val actors = trustedActorsText.split(',')
                                .map { it.trim() }.filter { it.isNotBlank() }.toSet()
                            consent.setTrustedActors(actors)
                            consent.setEnabled(true)
                            FridayAgentTaskRelayPollWorker.enable(appContext)
                            status = "Public project relay enabled. Only allowlisted GitHub actors can send accepted replies."
                        } else {
                            consent.setEnabled(false)
                            FridayAgentTaskRelayPollWorker.disable(appContext)
                            status = "Public project relay disabled."
                        }
                        enabled = next
                    } catch (failure: Exception) {
                        status = failure.message ?: "Could not change relay consent."
                        enabled = consent.isEnabled()
                    }
                }
            )
        }
        OutlinedTextField(
            value = trustedActorsText,
            onValueChange = { trustedActorsText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Trusted GitHub usernames") },
            singleLine = true
        )
        Text(
            "Comma-separated. Default: " + FridayAgentTaskRelayConsentStore.DEFAULT_TRUSTED_ACTOR,
            style = FridayType.Label
        )
        Button(
            onClick = {
                try {
                    val actors = trustedActorsText.split(',')
                        .map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    consent.setTrustedActors(actors)
                    trustedActorsText = consent.trustedActors().sorted().joinToString(", ")
                    status = "Trusted GitHub actors saved."
                } catch (failure: Exception) {
                    status = failure.message ?: "Could not save trusted GitHub actors."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save trusted actors")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (!enabled) {
                        status = "Enable public project collaboration first."
                    } else {
                        FridayAgentTaskRelayPollWorker.checkNow(appContext)
                        status = "Checking known public task threads for trusted replies."
                    }
                },
                enabled = enabled && tokenConfigured,
                modifier = Modifier.weight(1f)
            ) { Text("Check replies") }
            Button(
                onClick = {
                    if (!enabled) {
                        status = "Enable public project collaboration first."
                    } else if (!sending) {
                        sending = true
                        scope.launch {
                            status = "Creating a public collaboration handshake…"
                            try {
                                val token = withContext(Dispatchers.IO) { credentials.read() }
                                    ?: error("Save the GitHub token in Settings first.")
                                val relay = FridayAgentTaskRelay(
                                    github = GitHubDevelopmentRepositoryClient(token),
                                    threads = AgentTaskThreadStore(appContext.filesDir),
                                    trustedActors = consent.trustedActors(),
                                    consentEnabled = consent::isEnabled
                                )
                                val version = withContext(Dispatchers.IO) {
                                    runCatching {
                                        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                                    }.getOrNull().orEmpty().ifBlank { "unknown" }
                                }
                                val sourceRevision = ai.friday.assistant.BuildConfig.SOURCE_REVISION
                                    .takeIf { it.matches(Regex("[0-9a-fA-F]{40}")) }
                                    ?: "version:$version"
                                val taskId = UUID.randomUUID().toString()
                                val payload = JSONObject()
                                    .put("data_classification", "public")
                                    .put("goal", "Verify this Friday installation can exchange a public project task with its ChatGPT collaborator.")
                                    .put("allowed_evidence", JSONArray()
                                        .put("public source references")
                                        .put("public test results")
                                        .put("task protocol metadata"))
                                    .put("expected_output", "Return a brief acknowledgement and one concrete next integration step.")
                                val thread = withContext(Dispatchers.IO) {
                                    relay.createTask(
                                        taskId = taskId,
                                        title = "Friday collaboration handshake",
                                        sourceRevision = sourceRevision,
                                        allowedTaskPayload = payload
                                    )
                                }
                                latestIssueUrl = thread.issueUrl
                                status = "Handshake task created as GitHub issue #" + thread.issueNumber + ". ChatGPT can reply in that public thread."
                                FridayAgentTaskRelayPollWorker.checkNow(appContext)
                            } catch (failure: Exception) {
                                status = failure.message ?: "Could not create the collaboration task."
                            } finally {
                                sending = false
                            }
                        }
                    }
                },
                enabled = enabled && tokenConfigured && !sending,
                modifier = Modifier.weight(1f)
            ) { Text(if (sending) "Sending…" else "Send handshake") }
        }
        Text("Last check: " + lastPollLabel + " · " + unreadCount + " unread replies", style = FridayType.Technical)
        if (latestIssueUrl.isNotBlank()) {
            TextButton(onClick = {
                runCatching {
                    appContext.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(latestIssueUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) {
                Text("Open latest GitHub task")
            }
        }
        if (status.isNotBlank()) {
            Text(status, style = FridayType.Technical, color = FridayColors.TextPrimary)
        }
        if (pendingProposals.isNotEmpty()) {
            Text("Proposals awaiting your approval", style = FridayType.Body)
            pendingProposals.forEach { proposal ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(proposal.description, style = FridayType.Body, color = FridayColors.TextPrimary)
                    Text(
                        "External proposal · ${proposal.capability} · approval does not execute it",
                        style = FridayType.Label,
                        color = FridayColors.CoreIdle
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        FridayEngine.taskInbox?.reviewExternalProposal(proposal.id, accepted = true)
                                            ?: Result.failure(IllegalStateException("Friday's task inbox is not ready."))
                                    }
                                    status = result.fold(
                                        onSuccess = { "Proposal accepted for consideration. It was recorded as reviewed, not queued or executed." },
                                        onFailure = { it.message ?: "Proposal approval failed." }
                                    )
                                    refresh()
                                }
                            },
                            enabled = FridayEngine.taskInbox != null,
                            modifier = Modifier.weight(1f)
                        ) { Text("Accept for consideration") }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        FridayEngine.taskInbox?.reviewExternalProposal(proposal.id, accepted = false)
                                            ?: Result.failure(IllegalStateException("Friday's task inbox is not ready."))
                                    }
                                    status = result.fold(
                                        onSuccess = { "Proposal declined." },
                                        onFailure = { it.message ?: "Proposal rejection failed." }
                                    )
                                    refresh()
                                }
                            },
                            enabled = FridayEngine.taskInbox != null
                        ) { Text("Decline") }
                    }
                }
            }
        }
        if (latestReplies.isNotEmpty()) {
            Text("Recent ChatGPT replies", style = FridayType.Body)
            latestReplies.forEach { reply ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        reply.envelope.kind.name + " · " + reply.authorLogin + " · issue #" + reply.issueNumber,
                        style = FridayType.Label
                    )
                    Text(
                        reply.envelope.payload.optString("summary")
                            .ifBlank { reply.envelope.payload.toString() },
                        style = FridayType.Body,
                        color = FridayColors.TextPrimary
                    )
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                replyStore.markRead(reply.envelope.messageId)
                            }
                            refresh()
                        }
                    }) {
                        Text(if (reply.isRead) "Read" else "Mark read")
                    }
                }
            }
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { replyStore.markAllRead() }
                    refresh()
                }
            }) {
                Text("Mark all replies read")
            }
        }
    }
}
