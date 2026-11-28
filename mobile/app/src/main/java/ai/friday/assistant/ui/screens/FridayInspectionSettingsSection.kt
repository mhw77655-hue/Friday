package ai.friday.assistant.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import ai.friday.assistant.diagnostics.FridayInspectionRelayRuntime
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
import kotlinx.coroutines.launch

@Composable
internal fun FridayInspectionSettingsSection(context: Context) {
    val appContext = context.applicationContext
    var serverUrl by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var enrolled by remember { mutableStateOf(FridayInspectionRelayRuntime.isEnrolled(appContext)) }
    var enabled by remember { mutableStateOf(FridayInspectionRelayRuntime.isEnabled(appContext)) }
    var status by remember { mutableStateOf(FridayInspectionRelayRuntime.status(appContext)) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refresh() {
        enrolled = FridayInspectionRelayRuntime.isEnrolled(appContext)
        enabled = FridayInspectionRelayRuntime.isEnabled(appContext)
        status = FridayInspectionRelayRuntime.status(appContext)
        if (enabled) FridayInspectionRelayRuntime.start(appContext)
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
        Text(
            "External inspection is OFF by default. Pairing alone does not start a connection. If you explicitly turn this on, Friday opens an encrypted outbound session so the authorised inspection service can request a small technical snapshot: app version, Android version, coarse subsystem readiness, and available RAM/storage.",
            style = FridayType.Label,
            color = FridayColors.TextPrimary
        )
        Text(
            "Never shared by this channel: conversations, prompts, transcripts, audio, screen contents, notifications, contacts, location, personal files, private memories, credentials, or other apps' contents. Friday's local memory, identity, reasoning and self-diagnostics continue independently when external inspection is OFF. Turning it off closes the session immediately; Revoke additionally deletes this phone's pairing key.",
            style = FridayType.Label,
            color = FridayColors.TextPrimary
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Private inspection server HTTPS URL") },
            placeholder = { Text("https://your-friday-service.up.railway.app") },
            singleLine = true
        )
        OutlinedTextField(
            value = pairingCode,
            onValueChange = { pairingCode = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("One-time pairing code") },
            singleLine = true
        )
        Button(
            onClick = {
                busy = true
                scope.launch {
                    val result = runCatching {
                        FridayInspectionRelayRuntime.enroll(appContext, serverUrl, pairingCode)
                    }
                    status = result.fold(
                        onSuccess = {
                            pairingCode = ""
                            "Phone paired. External inspection remains OFF until you explicitly enable it."
                        },
                        onFailure = { it.message ?: "Could not pair this phone." }
                    )
                    refresh()
                    busy = false
                }
            },
            enabled = !busy && serverUrl.isNotBlank() && pairingCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Pairing…" else "Pair this phone") }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Allow external inspection session", style = FridayType.Body)
                Text("Default OFF · only runs after this explicit opt-in", style = FridayType.Label)
            }
            Switch(
                checked = enabled,
                enabled = enrolled && !busy,
                onCheckedChange = { next ->
                    if (next) {
                        FridayInspectionRelayRuntime.enable(appContext)
                        status = "External inspection enabled by your action."
                        refresh()
                    } else {
                        busy = true
                        scope.launch {
                            FridayInspectionRelayRuntime.disable(appContext)
                            refresh()
                            busy = false
                        }
                    }
                }
            )
        }
        Text("Connection status: $status", style = FridayType.Technical, color = FridayColors.CoreIdle)
        Button(
            onClick = {
                busy = true
                scope.launch {
                    runCatching { FridayInspectionRelayRuntime.revoke(appContext) }
                    refresh()
                    busy = false
                }
            },
            enabled = enrolled && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Revoke phone inspection access") }
    }
}
