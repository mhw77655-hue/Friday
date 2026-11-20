package com.jarvis.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jarvis.app.ui.components.StateChip
import com.jarvis.app.ui.components.pressScale
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType

private const val PREFS_NAME = "jarvis_settings"
private const val KEY_PERSONA = "persona_text"
private const val KEY_OFFLINE_MODE = "offline_mode"
private const val KEY_RECALL_LIMIT = "recall_limit"

/**
 * All three fields now persist to SharedPreferences (jarvis_settings),
 * a plain local file under the app's data dir -- no network, no new
 * dependency. Survives process death and app restart.
 *
 * NOTE: offlineMode is now saved and reloaded correctly, but nothing
 * else in the app reads it yet -- it doesn't actually force
 * JarvisBrainBridge/CloudVoiceClient into local-only mode. That's a
 * separate wiring job, not done here.
 *
 * PERMISSIONS section now does one real check: ObsidianSync.logTurn()
 * writes to /storage/emulated/0/JarvisSync/vault, which needs "All
 * files access" (MANAGE_EXTERNAL_STORAGE) on API 30+. That's a special
 * permission -- it can't be requested through a normal runtime
 * permission dialog, only by sending the user to a system Settings
 * screen (ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION). Shizuku/mic/
 * notifications below are still unwired, matching the existing note.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var personaText by remember { mutableStateOf(prefs.getString(KEY_PERSONA, "") ?: "") }
    var offlineMode by remember { mutableStateOf(prefs.getBoolean(KEY_OFFLINE_MODE, true)) }
    var recallLimit by remember { mutableStateOf(prefs.getString(KEY_RECALL_LIMIT, "") ?: "") }
    var jevApiKey by remember { mutableStateOf("") }
    var jevConfigured by remember { mutableStateOf(com.jarvis.app.voice.JevApiKeyStore(context).read() != null) }
    val githubTokenStore = remember { com.jarvis.app.github.GitHubTokenStore(context) }
    var githubToken by remember { mutableStateOf("") }
    var fridayBuildInstructions by remember { mutableStateOf("") }
    var githubConnected by remember { mutableStateOf(githubTokenStore.isConfigured()) }
    var githubBusy by remember { mutableStateOf(false) }
    var githubStatus by remember { mutableStateOf(if (githubConnected) "GitHub token stored securely." else "Not connected.") }
    val githubDeviceChannel = remember { com.jarvis.app.github.GitHubDeviceChannel(context) }
    var githubChannelConnected by remember { mutableStateOf(githubDeviceChannel.isConnected()) }
    var githubChannelStatus by remember { mutableStateOf(if (githubChannelConnected) githubDeviceChannel.issueUrl().orEmpty() else "Phone channel stopped.") }
    val githubScope = rememberCoroutineScope()
    val shizukuAvailable by com.jarvis.app.ShizukuBridge.isAvailable.collectAsState()
    val shizukuGranted by com.jarvis.app.ShizukuBridge.isGranted.collectAsState()
    val shizukuStatus by com.jarvis.app.ScreenBridge.status.collectAsState()
    var shizukuSnapshot by remember { mutableStateOf("") }

    // Bumped on ON_RESUME so the storage-permission row re-checks after
    // the user comes back from the system "All files access" screen --
    // isExternalStorageManager() is a plain call, not observable state,
    // so without this the row would show the stale pre-grant value
    // until some unrelated recomposition happened to occur.
    var resumeSignal by remember { mutableIntStateOf(0) }
    var actionTraceCount by remember(resumeSignal) { mutableIntStateOf(com.jarvis.app.diagnostics.PhoneActionDiagnostics.eventCount()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val phoneControlConnected = remember(resumeSignal) {
        com.jarvis.app.voice.FridayAccessibilityService.isEnabled()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeSignal++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasStorageAccess = remember(resumeSignal) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // pre-API 30: legacy external storage, no special grant needed here
        }
    }

    var autonomousEvolution by remember(resumeSignal) {
        mutableStateOf(com.jarvis.app.github.FridayEvolutionScheduler.isEnabled(context))
    }
    var shareAggregateEvidence by remember(resumeSignal) {
        mutableStateOf(com.jarvis.app.github.FridayEvolutionScheduler.isAggregateEvidenceEnabled(context))
    }
    var shizukuDozeStatus by remember { mutableStateOf("Not checked.") }
    var keepFridayAlive by remember(resumeSignal) {
        mutableStateOf(com.jarvis.app.runtime.FridayRuntimeController.isEnabled(context))
    }
    val batteryOptimizationIgnored = remember(resumeSignal) {
        com.jarvis.app.runtime.FridayRuntimeController.batteryOptimizationIgnored(context)
    }
    val runtimeStartFailure = remember(resumeSignal) {
        com.jarvis.app.runtime.FridayRuntimeController.lastStartFailure(context)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            keepFridayAlive = true
            com.jarvis.app.runtime.FridayRuntimeController.enable(context)
        } else {
            keepFridayAlive = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("PERSONALITY")
        Text(
            text = "This changes how JARVIS answers, not what he knows.",
            style = JarvisType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = personaText,
            onValueChange = {
                personaText = it
                prefs.edit().putString(KEY_PERSONA, it).apply()
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Persona prompt", style = JarvisType.Body) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisColors.SurfaceHairline,
                unfocusedBorderColor = JarvisColors.SurfaceHairline
            )
        )

        SectionLabel("VOICE")
        Text(text = "TTS voice: en-GB (system)", style = JarvisType.Technical)

        SectionLabel("JEV DEVICE-DECISION ENGINE")
        Text(
            text = "Optional TypeSafe Jev cloud decision support. Friday's local command layer handles bounded actions; an optional local model may help form more complex phone-action plans. A redacted proposed action and risk tier may be sent to Jev for review. Screen snapshots and literal text-entry payloads stay on-device. Jev cannot execute actions or grant permissions; consequential actions still require Friday's confirmation. The key is encrypted with Android Keystore.",
            style = JarvisType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = jevApiKey,
            onValueChange = { jevApiKey = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("TypeSafe API key", style = JarvisType.Body) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisColors.SurfaceHairline,
                unfocusedBorderColor = JarvisColors.SurfaceHairline
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (jevApiKey.isNotBlank()) {
                        runCatching { com.jarvis.app.voice.JevApiKeyStore(context).save(jevApiKey) }
                            .onSuccess { jevConfigured = true; jevApiKey = "" }
                    }
                },
                enabled = jevApiKey.isNotBlank()
            ) { Text("Save Jev key") }
            TextButton(onClick = {
                com.jarvis.app.voice.JevApiKeyStore(context).clear()
                jevConfigured = false
                jevApiKey = ""
            }) { Text("Remove key") }
        }
        Text(
            text = if (jevConfigured) "Jev credential stored securely; decision calls require internet." else "Jev not configured — Friday continues with local deterministic command handling.",
            style = JarvisType.Technical
        )

        SectionLabel("PHONE CONTROL")
        Text(
            text = "Enable Android Accessibility only if you want Friday to perform approved on-screen actions. Android keeps this permission under your control.",
            style = JarvisType.Label,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (phoneControlConnected) "Accessibility enabled" else "Accessibility not enabled", style = JarvisType.Technical)
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text(if (phoneControlConnected) "Manage" else "Enable")
            }
        }


        SectionLabel("GITHUB DEVELOPMENT LINK")
        Text(
            text = "Connect Friday to her public GitHub repository. A fine-grained token is encrypted with Android Keystore. Grant only Actions: Read and write, Pull requests: Read, and Issues: Read and write for the Friday repository. Do NOT grant Contents: Write.",
            style = JarvisType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = githubToken,
            onValueChange = { githubToken = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("github_pat_…", style = JarvisType.Body) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisColors.SurfaceHairline,
                unfocusedBorderColor = JarvisColors.SurfaceHairline
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    runCatching { githubTokenStore.save(githubToken) }
                        .onSuccess {
                            githubConnected = true
                            githubToken = ""
                            runCatching {
                                com.jarvis.app.github.DevelopmentRequestOutboxScheduler.ensureScheduled(context, appendIfActive = true)
                            }
                            runCatching {
                                com.jarvis.app.github.FridayEvolutionScheduler.enable(context)
                            }
                                .onSuccess {
                                    autonomousEvolution = true
                                    githubStatus = "GitHub token saved securely. Friday's autonomous development is enabled and the first cycle is queued."
                                }
                                .onFailure {
                                    autonomousEvolution = com.jarvis.app.github.FridayEvolutionScheduler.isEnabled(context)
                                    githubStatus = "GitHub token saved securely, but the first development cycle could not be scheduled: ${it.message ?: "unknown error"}"
                                }
                        }
                        .onFailure { githubStatus = it.message ?: "Could not save GitHub token." }
                },
                enabled = githubToken.isNotBlank()
            ) { Text("Save GitHub token") }
            TextButton(onClick = {
                githubScope.launch {
                    runCatching { githubDeviceChannel.disconnect() }
                    com.jarvis.app.github.GitHubDeviceChannelScheduler.disable(context)
                    com.jarvis.app.github.FridayEvolutionScheduler.disable(context)
                    autonomousEvolution = false
                    githubTokenStore.clear()
                    githubConnected = false
                    githubChannelConnected = false
                    githubToken = ""
                    githubStatus = "GitHub token removed and phone polling stopped."
                }
            }) { Text("Disconnect") }
        }
        TextButton(onClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new"))
            )
        }) { Text("Create fine-grained token") }

        Text(
            text = "Write what you want Friday to become or build. Your words are sent as the actual development requirement to the repository coding worker; the worker inspects the project, changes code, adds tests and builds a draft APK. It cannot silently install or publish a release.",
            style = JarvisType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = fridayBuildInstructions,
            onValueChange = { fridayBuildInstructions = it.take(3500) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Friday, build…", style = JarvisType.Body) },
            minLines = 4,
            maxLines = 8,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisColors.SurfaceHairline,
                unfocusedBorderColor = JarvisColors.SurfaceHairline
            )
        )
        Button(
            onClick = {
                githubScope.launch {
                    githubBusy = true
                    githubStatus = "Sending your build instructions and a bounded device-health snapshot to Friday's coding worker…"
                    runCatching {
                        com.jarvis.app.github.GitHubDevelopmentClient(context)
                            .submitBuildRequest(fridayBuildInstructions)
                    }
                        .onSuccess {
                            githubStatus = it
                            fridayBuildInstructions = ""
                        }
                        .onFailure { githubStatus = it.message ?: "Build request failed." }
                    githubBusy = false
                }
            },
            enabled = githubConnected && !githubBusy && fridayBuildInstructions.trim().length >= 12,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (githubBusy) "Sending…" else "Friday, build from my words") }

        Button(
            onClick = {
                githubScope.launch {
                    githubBusy = true
                    runCatching { com.jarvis.app.github.GitHubDevelopmentClient(context).checkLatestProposal() }
                        .onSuccess { githubStatus = it }
                        .onFailure { githubStatus = it.message ?: "Could not check proposal." }
                    githubBusy = false
                }
            },
            enabled = githubConnected && !githubBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Check latest repair proposal") }
        Text(
            text = githubStatus,
            style = JarvisType.Technical,
            modifier = Modifier.padding(top = 6.dp)
        )

        SectionLabel("AUTONOMOUS DEVELOPMENT")
        Text(
            text = "Saving a valid GitHub token starts this automatically: Friday queues her first development cycle and then checks in about every 12 hours. She asks the repository builder to inspect her vision and implementation, make one evidence-backed improvement, test it and open a draft proposal. When this switch is on, a previous-process crash is also sent to the public repository as a bounded exception-type and stack trace report so the Builder can diagnose it. Exception message text, conversations, screen contents and files are not uploaded. Pausing this switch stops crash uploads even while the GitHub token remains stored. Existing open proposals block duplicate cycles. GitHub Actions is extra compute, not Friday's identity; aggregate device evidence remains a separate opt-in.",
            style = JarvisType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Let Friday develop herself", style = JarvisType.Body)
                Text(
                    if (autonomousEvolution) "Owner-authorized · 12-hour cycle" else "Off · no scheduled build requests",
                    style = JarvisType.Technical
                )
            }
            Switch(
                checked = autonomousEvolution,
                enabled = githubConnected,
                onCheckedChange = { enabled ->
                    autonomousEvolution = enabled
                    if (enabled) {
                        com.jarvis.app.github.FridayEvolutionScheduler.enable(context)
                    } else {
                        com.jarvis.app.github.FridayEvolutionScheduler.disable(context)
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = JarvisColors.CoreIdle)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Share aggregate self-study evidence", style = JarvisType.Body)
                Text(
                    if (shareAggregateEvidence) "Owner-authorized · content-free runtime metrics"
                    else "Off · scheduled cycles use vision and repository evidence only",
                    style = JarvisType.Technical
                )
            }
            Switch(
                checked = shareAggregateEvidence,
                enabled = githubConnected,
                onCheckedChange = { enabled ->
                    shareAggregateEvidence = enabled
                    com.jarvis.app.github.FridayEvolutionScheduler.setAggregateEvidenceEnabled(context, enabled)
                },
                colors = SwitchDefaults.colors(checkedTrackColor = JarvisColors.CoreIdle)
            )
        }
        Text(
            text = "This separate opt-in lets Friday's builder use aggregate evidence from her own runtime: experience/association counts, plan outcome counts (including skipped and blocked), capability availability, self-study health and resource trends. It never sends raw memories, remembered facts, conversation text, user prompt history, user file contents, location, contacts, notifications, screen contents, microphone, camera or unique device identifiers. Because this repository is public, the aggregate technical summary is public too. Every change remains a draft PR; Friday still cannot merge, sign, release or install her APK.",
            style = JarvisType.Label,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )
        Text(
            text = "Privacy: the repository is public. Sending a request publishes a bounded technical snapshot (device model, Android/app version, available RAM/storage, battery, thermal and network type) in the workflow input. No conversations, location, contacts, notifications, microphone, camera, screen contents, files or unique device IDs are sent. Friday never merges or installs a worker patch.",
            style = JarvisType.Label,
            modifier = Modifier.padding(top = 8.dp)
        )

        SectionLabel("LIVE PHONE DIAGNOSTICS CHANNEL")
        Text(
            text = "Friday can keep a phone-initiated connection to this repository using WorkManager. It checks GitHub about every 15 minutes when Android permits background work. The only remote command accepted is diagnostics.snapshot, and only comments written by the repository owner are considered.",
            style = JarvisType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = {
                githubScope.launch {
                    githubBusy = true
                    runCatching { githubDeviceChannel.connect() }
                        .onSuccess {
                            com.jarvis.app.github.GitHubDeviceChannelScheduler.enable(context)
                            githubChannelConnected = true
                            githubChannelStatus = "Connected: $it. Background polling enabled."
                        }
                        .onFailure { githubChannelStatus = it.message ?: "Could not connect device channel." }
                    githubBusy = false
                }
            },
            enabled = githubConnected && !githubBusy && !githubChannelConnected,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Connect phone to GitHub") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    githubScope.launch {
                        githubBusy = true
                        runCatching { githubDeviceChannel.poll() }
                            .onSuccess { githubChannelStatus = it }
                            .onFailure { githubChannelStatus = it.message ?: "Phone channel poll failed." }
                        githubBusy = false
                    }
                },
                enabled = githubChannelConnected && !githubBusy
            ) { Text("Check now") }
            TextButton(
                onClick = {
                    githubScope.launch {
                        githubBusy = true
                        runCatching { githubDeviceChannel.disconnect() }
                            .onSuccess { githubChannelStatus = it }
                            .onFailure { githubChannelStatus = it.message ?: "Disconnect failed." }
                        com.jarvis.app.github.GitHubDeviceChannelScheduler.disable(context)
                        githubChannelConnected = false
                        githubBusy = false
                    }
                },
                enabled = githubChannelConnected && !githubBusy
            ) { Text("Stop channel") }
        }
        if (githubChannelConnected) {
            TextButton(onClick = {
                githubDeviceChannel.issueUrl()?.let { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }) { Text("Open GitHub command channel") }
        }
        Text(
            text = githubChannelStatus,
            style = JarvisType.Technical,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = "This channel uses a public GitHub issue as its command queue. Diagnostic replies are public and contain only device model, Android/app version, RAM/storage, battery, thermal and network transport. No remote shell, arbitrary file access, microphone, camera, screen capture, location, contacts, notifications, or silent app actions are supported. Disconnect closes the issue and stops polling.",
            style = JarvisType.Label,
            modifier = Modifier.padding(top = 8.dp)
        )

        SectionLabel("FRIDAY RUNTIME")
        Text(
            text = "Keep Friday's local continuity and background presence alive with an Android foreground service. Android still controls process scheduling; this is a persistence aid, not a way around force-stop or system policy.",
            style = JarvisType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Keep Friday alive", style = JarvisType.Body)
                Text(
                    if (keepFridayAlive) "Persistent runtime enabled" else "Runtime starts only while the app is active",
                    style = JarvisType.Technical
                )
            }
            Switch(
                checked = keepFridayAlive,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            keepFridayAlive = true
                            com.jarvis.app.runtime.FridayRuntimeController.enable(context)
                        }
                    } else {
                        keepFridayAlive = false
                        com.jarvis.app.runtime.FridayRuntimeController.disable(context)
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = JarvisColors.CoreIdle)
            )
        }
        Text(
            text = if (batteryOptimizationIgnored) {
                "Battery optimization: Friday is exempt from Doze battery optimization."
            } else {
                "Battery optimization: Android may defer or stop background work. Exempt Friday below for better persistence."
            },
            style = JarvisType.Technical,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = { com.jarvis.app.runtime.FridayRuntimeController.openBatteryOptimizationSettings(context) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open battery optimization settings") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    githubScope.launch {
                        shizukuDozeStatus = runCatching {
                            com.jarvis.app.ScreenBridge.setDozeWhitelist(true)
                                ?: "Shizuku service is not connected."
                        }.getOrElse { it.message ?: "Could not enable Doze exemption." }
                    }
                },
                enabled = shizukuStatus.startsWith("bound · verified Shizuku uid=")
            ) { Text("Shizuku: protect Friday") }
            TextButton(
                onClick = {
                    githubScope.launch {
                        shizukuDozeStatus = runCatching {
                            com.jarvis.app.ScreenBridge.setDozeWhitelist(false)
                                ?: "Shizuku service is not connected."
                        }.getOrElse { it.message ?: "Could not remove Doze exemption." }
                    }
                },
                enabled = shizukuStatus.startsWith("bound · verified Shizuku uid=")
            ) { Text("Remove") }
        }
        Text(
            text = "Shizuku Doze status: $shizukuDozeStatus. This changes only Android's Device Idle whitelist for Friday; it cannot override force-stop or manufacturer task killers.",
            style = JarvisType.Technical,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (runtimeStartFailure.isNotBlank()) {
            Text(
                text = "Last runtime start issue: $runtimeStartFailure. Open Friday to retry.",
                style = JarvisType.Technical,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Text(
            text = "After reboot Friday restores her non-microphone core where Android permits. Microphone capture is resumed only after Friday is opened in the foreground with RECORD_AUDIO granted. Force-stop, some manufacturer task killers, permission revocation and thermal shutdown cannot be defeated by an app.",
            style = JarvisType.Label,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        SectionLabel("DEVICE CAPABILITIES")
        Text(
            text = when {
                !shizukuAvailable -> "Shizuku is not running. Friday remains operational without it."
                !shizukuGranted -> "Shizuku is running; Friday has not been granted access yet."
                else -> "Shizuku permission is granted. Connect Friday's privileged device capability service."
            },
            style = JarvisType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = {
                when {
                    !shizukuAvailable -> com.jarvis.app.ShizukuBridge.refresh()
                    !shizukuGranted -> com.jarvis.app.ShizukuBridge.requestPermission()
                    else -> com.jarvis.app.ScreenBridge.bind(context)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    !shizukuAvailable -> "Check Shizuku connection"
                    !shizukuGranted -> "Grant Friday Shizuku access"
                    else -> "Connect Friday to the phone"
                }
            )
        }
        Text(text = shizukuStatus, style = JarvisType.Technical, modifier = Modifier.padding(vertical = 6.dp))
        Button(
            onClick = {
                githubScope.launch {
                    shizukuSnapshot = com.jarvis.app.ScreenBridge.diagnosticsSnapshot()
                        ?: "Device service is not connected."
                }
            },
            enabled = shizukuStatus.startsWith("bound · verified Shizuku uid="),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Read live device diagnostics") }
        if (shizukuSnapshot.isNotBlank()) {
            Text(text = shizukuSnapshot, style = JarvisType.Technical, modifier = Modifier.padding(top = 6.dp))
        }

        SectionLabel("PERMISSIONS")
        StorageAccessRow(
            granted = hasStorageAccess,
            onTapGrant = { openAllFilesAccessSettings(context) }
        )

        SectionLabel("OFFLINE MODE")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Force local-only", style = JarvisType.Body, modifier = Modifier.weight(1f))
            Switch(
                checked = offlineMode,
                onCheckedChange = {
                    offlineMode = it
                    prefs.edit().putBoolean(KEY_OFFLINE_MODE, it).apply()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = JarvisColors.CoreIdle)
            )
        }

        SectionLabel("LIVE TEST ACTION JOURNAL")
        Text(
            text = "Friday records phone-control attempts locally so failures can be diagnosed here. The journal stores action categories, execution routes, outcomes and durations—not spoken requests, typed text, tap labels, screen contents or credentials. Executor-reported success is deliberately marked unverified until a separate outcome check exists.",
            style = JarvisType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "$actionTraceCount action events currently stored on this phone.",
            style = JarvisType.Technical,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = {
                val report = com.jarvis.app.diagnostics.PhoneActionDiagnostics.exportText(context)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Friday live test report", report))
                actionTraceCount = com.jarvis.app.diagnostics.PhoneActionDiagnostics.eventCount()
                Toast.makeText(context, "Friday diagnostic report copied. Paste it into the development conversation.", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Copy live test report") }
        Button(
            onClick = {
                val report = com.jarvis.app.diagnostics.PhoneActionDiagnostics.exportText(context)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Friday live test report")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                context.startActivity(Intent.createChooser(share, "Send Friday report to ChatGPT"))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Send report to ChatGPT") }
        Text(
            text = "Choose ChatGPT in the Android share sheet, open this same development conversation, and send the report. If ChatGPT is not offered, use Copy live test report and paste it here. The report includes action traces, app/build identity, device capability status and sanitized crash evidence—not your spoken words or screen contents.",
            style = JarvisType.Technical,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        TextButton(
            onClick = {
                com.jarvis.app.diagnostics.PhoneActionDiagnostics.clear()
                actionTraceCount = 0
                Toast.makeText(context, "Local action journal cleared.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Clear local journal") }

        SectionLabel("MEMORY RULES")
        OutlinedTextField(
            value = recallLimit,
            onValueChange = {
                recallLimit = it
                prefs.edit().putString(KEY_RECALL_LIMIT, it).apply()
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Recall limit", style = JarvisType.Body) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisColors.SurfaceHairline,
                unfocusedBorderColor = JarvisColors.SurfaceHairline
            )
        )
    }
}

@Composable
private fun StorageAccessRow(granted: Boolean, onTapGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .let { base -> if (granted) base else base.pressScale(onTapGrant) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = "Storage (Obsidian vault)", style = JarvisType.Body)
            Text(
                text = if (granted) {
                    "All files access granted — JARVIS can write to /storage/emulated/0/JarvisSync/vault"
                } else {
                    "Not granted — tap to open system settings and allow All files access"
                },
                style = JarvisType.Label
            )
        }
        StateChip(
            label = if (granted) "granted" else "tap to grant",
            dotColor = if (granted) JarvisColors.CoreSuccess else JarvisColors.CoreWarning
        )
    }
}

/**
 * MANAGE_EXTERNAL_STORAGE can't be requested via a normal permission
 * dialog -- ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION with a
 * package: URI takes the user straight to this app's specific toggle
 * (not the general "All files access" list), one tap to grant. Falls
 * back to the general list screen if the app-specific intent isn't
 * resolvable on a given OEM's settings app.
 */
private fun openAllFilesAccessSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val appSpecific = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    val resolved = appSpecific.resolveActivity(context.packageManager) != null
    val intent = if (resolved) {
        appSpecific
    } else {
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }
    context.startActivity(intent)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = JarvisType.Label, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
}
