package com.jarvis.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.app.JarvisApplication
import com.jarvis.app.context.ProjectContextPack
import com.jarvis.app.context.ProjectContextPackSync
import com.jarvis.app.context.ProjectContextSyncResult
import com.jarvis.app.model.remote.RemoteProviderProfile
import com.jarvis.app.model.remote.RemoteProviderProtocol
import com.jarvis.app.ui.components.FridayPanel
import com.jarvis.app.ui.components.FridaySectionHeading
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun RemoteAiProvidersSection() {
    val context = LocalContext.current
    val app = context.applicationContext as JarvisApplication
    val profiles = remember { app.remoteProviderProfiles }
    val packStore = remember { app.projectContextPackStore }
    val contextSync = remember { ProjectContextPackSync(context, packStore) }
    val scope = rememberCoroutineScope()
    val council = remember { app.providerCouncil }
    var councilEnabled by remember { mutableStateOf(council.isEnabled()) }
    var configured by remember { mutableStateOf(profiles.all()) }
    var primaryProviderId by remember { mutableStateOf(profiles.primaryProviderId() ?: configured.firstOrNull { it.enabled }?.id) }
    var profileId by remember { mutableStateOf("openai") }
    var label by remember { mutableStateOf("OpenAI") }
    var endpoint by remember { mutableStateOf("https://api.openai.com/v1") }
    var modelId by remember { mutableStateOf("gpt-6-luna") }
    var apiKey by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(RemoteProviderProtocol.OPENAI_RESPONSES) }
    var allowLocalHttp by remember { mutableStateOf(false) }
    var protocolMenu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Remote advisers are optional. Friday works locally when none are configured.") }
    var contextStatus by remember { mutableStateOf(
        packStore.read()?.let { "Project context loaded: ${it.projectId} · ${it.sourceRevision}" } ?: "No project context pack imported."
    ) }
    val importContext = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read selected context file")
                val pack = ProjectContextPack.fromJson(JSONObject(json))
                packStore.write(pack)
                contextStatus = "Imported ${pack.projectId} · revision ${pack.sourceRevision}"
            }.onFailure { contextStatus = "Import failed: ${it.message}" }
        }
    }

    FridayPanel(modifier = Modifier.padding(bottom = 20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        FridaySectionHeading(
            title = "AI CONNECTORS & SHARED PROJECT CONTEXT",
            detail = "OPTIONAL"
        )
        Text(
            "Connect OpenAI, Claude, Gemini or any compatible endpoint. Credentials stay encrypted on this phone. Select a primary provider, then activate the Cloud Assisted environment to use it for live conversation. The adviser permission below remains separate.",
            style = JarvisType.Body.copy(color = JarvisColors.TextSecondary)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Allow Friday to consult external advisers")
                Text("Off by default. Enabling permits network requests using the selected task and imported context pack.")
            }
            Switch(checked = councilEnabled, enabled = configured.isNotEmpty(), onCheckedChange = {
                councilEnabled = it
                council.setEnabled(it)
                status = if (it) "External adviser calls enabled." else "External adviser calls disabled."
            })
        }

        Text("Configured advisers")
        configured.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.label)
                    Text("${item.protocol.name} · ${item.modelId}")
                    if (primaryProviderId == item.id) Text("PRIMARY LIVE REASONING PROVIDER", style = JarvisType.Technical.copy(color = JarvisColors.CoreSuccess))
                }
                TextButton(onClick = {
                    profiles.setPrimaryProvider(item.id)
                    primaryProviderId = item.id
                    status = "${item.label} selected for live Cloud Assisted conversations."
                }) { Text("Use for live conversation") }
                TextButton(onClick = {
                    profiles.remove(item.id)
                    configured = profiles.all()
                    primaryProviderId = profiles.primaryProviderId() ?: configured.firstOrNull { it.enabled }?.id
                    if (configured.isEmpty()) {
                        councilEnabled = false
                        council.setEnabled(false)
                    }
                    status = "${item.label} disconnected."
                }) { Text("Remove") }
            }
        }

        Text("Add or update provider")
        OutlinedTextField(profileId, { profileId = it }, label = { Text("Provider ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(label, { label = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row {
            TextButton(onClick = { protocolMenu = true }) { Text("Protocol: ${protocol.name}") }
            DropdownMenu(expanded = protocolMenu, onDismissRequest = { protocolMenu = false }) {
                RemoteProviderProtocol.values().forEach { item ->
                    DropdownMenuItem(text = { Text(item.name) }, onClick = {
                        protocol = item
                        when (item) {
                            RemoteProviderProtocol.OPENAI_RESPONSES -> {
                                endpoint = "https://api.openai.com/v1"
                                profileId = "openai"
                                label = "OpenAI"
                                modelId = "gpt-6-luna"
                            }
                            RemoteProviderProtocol.OPENAI_COMPATIBLE -> {
                                endpoint = "https://api.openai.com/v1"
                                profileId = "compatible"
                                label = "OpenAI-compatible"
                            }
                            RemoteProviderProtocol.ANTHROPIC_MESSAGES -> {
                                endpoint = "https://api.anthropic.com/v1"
                                profileId = "claude"
                                label = "Claude"
                                modelId = "claude-sonnet-5"
                            }
                            RemoteProviderProtocol.GEMINI_INTERACTIONS -> {
                                endpoint = "https://generativelanguage.googleapis.com/v1beta"
                                profileId = "gemini"
                                label = "Gemini"
                                modelId = "gemini-3.5-flash-lite"
                            }
                        }
                        label = when (item) {
                            RemoteProviderProtocol.OPENAI_RESPONSES -> "OpenAI"
                            RemoteProviderProtocol.OPENAI_COMPATIBLE -> "OpenAI-compatible"
                            RemoteProviderProtocol.ANTHROPIC_MESSAGES -> "Claude"
                            RemoteProviderProtocol.GEMINI_INTERACTIONS -> "Gemini"
                        }
                        protocolMenu = false
                    })
                }
            }
        }
        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("API base endpoint") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(modelId, { modelId = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(
            apiKey, { apiKey = it }, label = { Text("API key (stored encrypted)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Allow HTTP for 127.0.0.1 loopback only")
            Switch(checked = allowLocalHttp, onCheckedChange = { allowLocalHttp = it })
        }
        Button(onClick = {
            runCatching {
                val profile = RemoteProviderProfile(
                    id = profileId.trim(), label = label.trim(), protocol = protocol,
                    endpoint = endpoint.trim(), modelId = modelId.trim(),
                    allowInsecureLocalHttp = allowLocalHttp
                )
                profiles.save(profile, apiKey)
                configured = profiles.all()
                primaryProviderId = profiles.primaryProviderId() ?: configured.firstOrNull { it.enabled }?.id
                apiKey = ""
                status = "${profile.label} saved. Run a connection test before relying on it."
            }.onFailure { status = "Could not save provider: ${it.message}" }
        }, enabled = profileId.isNotBlank() && label.isNotBlank() && endpoint.isNotBlank() && modelId.isNotBlank()) {
            Text("Save provider")
        }
        Button(onClick = {
            scope.launch {
                status = "Testing configured providers…"
                val entry = profiles.createEnabledEntries().firstOrNull { it.profile.id == profileId.trim() }
                status = if (entry == null) "Save the provider and credential first." else {
                    val health = entry.provider.health()
                    if (health.isHealthy) "Connected to ${entry.profile.label} (${health.latencyMs} ms)."
                    else "Connection failed: ${health.error ?: "unknown error"}"
                }
            }
        }) { Text("Test connection") }

        Text(status)

        Text("Shared project context")
        Text("Import one owner-curated JSON context pack. Friday can then pass only task-relevant project facts to any configured adviser instead of sending your full chat or repository.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    contextStatus = "Syncing owner-curated context from Friday's GitHub repository…"
                    when (val result = contextSync.sync()) {
                        is ProjectContextSyncResult.Updated -> contextStatus = "Synced project context · revision ${result.revision}"
                        ProjectContextSyncResult.Unchanged -> contextStatus = "Project context is already current."
                        is ProjectContextSyncResult.Failed -> contextStatus = "Sync failed: ${result.reason}"
                    }
                }
            }) { Text("Sync from GitHub") }
            TextButton(onClick = { importContext.launch(arrayOf("application/json", "text/json")) }) {
                Text("Import file")
            }
        }
        Text(contextStatus, style = JarvisType.Technical.copy(color = JarvisColors.TextMuted))
        }
    }
}
