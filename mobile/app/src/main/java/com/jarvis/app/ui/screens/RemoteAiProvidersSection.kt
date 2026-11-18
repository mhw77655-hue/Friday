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
import com.jarvis.app.context.ProjectContextPack
import com.jarvis.app.context.ProjectContextPackStore
import com.jarvis.app.model.remote.ProviderCouncil
import com.jarvis.app.model.remote.RemoteProviderProfile
import com.jarvis.app.model.remote.RemoteProviderProfiles
import com.jarvis.app.model.remote.RemoteProviderProtocol
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

@Composable
fun RemoteAiProvidersSection() {
    val context = LocalContext.current
    val profiles = remember { RemoteProviderProfiles(context) }
    val packStore = remember { ProjectContextPackStore(File(context.filesDir, "friday/project_context_pack.json")) }
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf(profiles.all()) }
    var profileId by remember { mutableStateOf("openai") }
    var label by remember { mutableStateOf("OpenAI") }
    var endpoint by remember { mutableStateOf("https://api.openai.com/v1") }
    var modelId by remember { mutableStateOf("gpt-4.1-mini") }
    var apiKey by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(RemoteProviderProtocol.OPENAI_COMPATIBLE) }
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

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("AI CONNECTORS & SHARED PROJECT CONTEXT")
        Text("Connect OpenAI, Claude, Gemini or any compatible endpoint. Credentials stay encrypted on this phone. Friday remains the authority; advisers cannot execute phone actions or edit memory.")

        Text("Configured advisers")
        configured.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.label)
                    Text("${item.protocol.name} · ${item.modelId}")
                }
                TextButton(onClick = {
                    profiles.remove(item.id)
                    configured = profiles.all()
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
                            RemoteProviderProtocol.OPENAI_COMPATIBLE -> endpoint = "https://api.openai.com/v1"
                            RemoteProviderProtocol.ANTHROPIC_MESSAGES -> endpoint = "https://api.anthropic.com/v1"
                            RemoteProviderProtocol.GEMINI_GENERATE_CONTENT -> endpoint = "https://generativelanguage.googleapis.com/v1beta"
                        }
                        label = when (item) {
                            RemoteProviderProtocol.OPENAI_COMPATIBLE -> "OpenAI"
                            RemoteProviderProtocol.ANTHROPIC_MESSAGES -> "Claude"
                            RemoteProviderProtocol.GEMINI_GENERATE_CONTENT -> "Gemini"
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
            Text("Allow insecure HTTP for local/LAN only")
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
        Button(onClick = { importContext.launch(arrayOf("application/json", "text/json")) }) {
            Text("Import project context pack")
        }
        Text(contextStatus)
    }
}
