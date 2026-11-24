package ai.friday.assistant.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.os.Build
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import org.json.JSONObject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ai.friday.assistant.aiworkforce.table.ChatGPTPlanAuth
import ai.friday.assistant.aiworkforce.table.ChatGPTPlanBackgroundConsentStore
import ai.friday.assistant.update.FridayPackageInstaller
import ai.friday.assistant.update.FridayShizukuPackageInstaller
import ai.friday.assistant.update.FridayUpdateClient
import ai.friday.assistant.update.FridayUpdateStateStore
import ai.friday.assistant.github.reporting.GitHubCredentialStore
import ai.friday.assistant.github.reporting.FridayGitHubReportClient
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType

private const val PREFS_NAME = "friday_settings"
private const val KEY_PERSONA = "persona_text"
private const val KEY_RECALL_LIMIT = "recall_limit"

private data class SettingsSnapshot(
    val jevConfigured: Boolean,
    val githubConfigured: Boolean,
    val githubAutoReportConsent: Boolean,
    val githubPendingReport: Boolean,
    val chatGptConnected: Boolean,
    val chatGptBackgroundConsent: Boolean,
    val chatGptAccountLabel: String,
    val updateStatus: String,
    val stagedUpdate: FridayUpdateClient.Result.Staged?,
    val actionTraceCount: Int,
    val phoneControlConnected: Boolean
)

/**
 * Local runtime controls, capability status and owner-exported diagnostics.
 * Repository development is an optional, owner-authorized bridge that can
 * publish isolated candidate PRs; it cannot merge or release. Local continuity
 * remains independent of GitHub.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var personaText by remember { mutableStateOf(prefs.getString(KEY_PERSONA, "") ?: "") }
    var recallLimit by remember { mutableStateOf(prefs.getString(KEY_RECALL_LIMIT, "") ?: "") }
    var jevApiKey by remember { mutableStateOf("") }
    var jevConfigured by remember { mutableStateOf(false) }
    var githubToken by remember { mutableStateOf("") }
    val githubCredentialStore = remember { GitHubCredentialStore(context) }
    var githubConfigured by remember { mutableStateOf(false) }
    val githubPendingReportStore = remember { ai.friday.assistant.github.reporting.GitHubPendingReportStore(context) }
    val githubDeviceReportConsentStore = remember { ai.friday.assistant.github.reporting.GitHubDeviceReportConsentStore(context) }
    var githubAutoReportConsent by remember { mutableStateOf(false) }
    var githubPendingReport by remember { mutableStateOf(false) }
    var githubDevelopmentStatus by remember { mutableStateOf("Development repository access has not been checked.") }
    var githubReportStatus by remember {
        mutableStateOf(if (githubConfigured) "GitHub credential is encrypted on this phone." else "GitHub reporting is not connected.")
    }
    var githubBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val chatGptAuth = remember(context) { ChatGPTPlanAuth(context) }
    val chatGptConsentStore = remember(context) { ChatGPTPlanBackgroundConsentStore(context) }
    var chatGptConnected by remember { mutableStateOf(false) }
    var chatGptBackgroundConsent by remember { mutableStateOf(false) }
    var chatGptAccountLabel by remember { mutableStateOf("") }
    var chatGptStatus by remember { mutableStateOf("Checking ChatGPT connection…") }
    var chatGptBusy by remember { mutableStateOf(false) }
    val updateStateStore = remember { FridayUpdateStateStore(context) }
    var updateStatus by remember { mutableStateOf("Checking update status…") }
    var stagedUpdate by remember { mutableStateOf<FridayUpdateClient.Result.Staged?>(null) }
    val updateInstallPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateStatus = if (context.packageManager.canRequestPackageInstalls()) {
            "Android now allows Friday to request installs. Tap Install verified update."
        } else {
            "Android did not grant this install source. Friday remains unchanged."
        }
    }
    val shizukuAvailable by ai.friday.assistant.ShizukuBridge.isAvailable.collectAsState()
    val shizukuGranted by ai.friday.assistant.ShizukuBridge.isGranted.collectAsState()
    val shizukuStatus by ai.friday.assistant.ScreenBridge.status.collectAsState()
    var shizukuSnapshot by remember { mutableStateOf("") }
    var shizukuAcceptanceReport by remember { mutableStateOf("") }
    var shizukuAcceptanceRunning by remember { mutableStateOf(false) }
    var objectiveExecutionStatus by remember { mutableStateOf("Friday is loading the canonical objectives ledger…") }

    // Bumped on ON_RESUME so runtime status and local diagnostic counts
    // refresh when the owner returns to Settings from Android system panels.
    var resumeSignal by remember { mutableIntStateOf(0) }
    var actionTraceCount by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var phoneControlConnected by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Secure-store and filesystem refresh happens asynchronously below.
                resumeSignal++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeSignal) {
        val settingsSnapshot = withContext(Dispatchers.IO) {
            SettingsSnapshot(
                jevConfigured = ai.friday.assistant.voice.JevApiKeyStore(context).read() != null,
                githubConfigured = githubCredentialStore.isConfigured(),
                githubAutoReportConsent = githubDeviceReportConsentStore.isEnabled(),
                githubPendingReport = githubPendingReportStore.read() != null,
                chatGptConnected = chatGptAuth.isConnected(),
                chatGptBackgroundConsent = chatGptConsentStore.isEnabled(),
                chatGptAccountLabel = chatGptAuth.accountLabel(),
                updateStatus = updateStateStore.lastStatus(),
                stagedUpdate = updateStateStore.loadStaged(),
                actionTraceCount = ai.friday.assistant.diagnostics.PhoneActionDiagnostics.eventCount(),
                phoneControlConnected = ai.friday.assistant.voice.FridayAccessibilityService.isEnabled()
            )
        }
        jevConfigured = settingsSnapshot.jevConfigured
        githubConfigured = settingsSnapshot.githubConfigured
        githubAutoReportConsent = settingsSnapshot.githubAutoReportConsent
        githubPendingReport = settingsSnapshot.githubPendingReport
        chatGptConnected = settingsSnapshot.chatGptConnected
        chatGptBackgroundConsent = settingsSnapshot.chatGptBackgroundConsent
        chatGptAccountLabel = settingsSnapshot.chatGptAccountLabel
        chatGptStatus = if (settingsSnapshot.chatGptConnected) "Connected." else "ChatGPT plan Seat is not connected."
        updateStatus = settingsSnapshot.updateStatus
        stagedUpdate = settingsSnapshot.stagedUpdate
        actionTraceCount = settingsSnapshot.actionTraceCount
        phoneControlConnected = settingsSnapshot.phoneControlConnected
        objectiveExecutionStatus = withContext(Dispatchers.IO) {
            val modelFile = File(context.filesDir, "friday-development/model-bootstrap.json")
            val modelSummary = if (modelFile.isFile) runCatching {
                val model = JSONObject(modelFile.readText(Charsets.UTF_8))
                "Resident model: " + model.optString("state", "UNKNOWN") +
                    " · " + model.optString("reason", "No detail recorded")
            }.getOrDefault("Resident model: status file is unreadable.")
            else "Resident model: waiting for host study and safe unmetered bootstrap."

            val ledgerFile = File(context.filesDir, "friday-development/plan/execution-ledger.json")
            val planSummary = if (!ledgerFile.isFile) {
                "The canonical plan or local execution ledger has not been imported yet. Friday will retry bootstrap."
            } else runCatching {
                val ledger = JSONObject(ledgerFile.readText(Charsets.UTF_8))
                val objectives = ledger.optJSONArray("objectives") ?: org.json.JSONArray()
                val phases = ledger.optJSONArray("phases") ?: org.json.JSONArray()
                val tasks = ledger.optJSONArray("tasks") ?: org.json.JSONArray()
                val acceptedObjectives = (0 until objectives.length()).count {
                    objectives.optJSONObject(it)?.optString("status") == "ACCEPTED"
                }
                val acceptedPhases = (0 until phases.length()).count {
                    phases.optJSONObject(it)?.optString("status") == "ACCEPTED"
                }
                val queued = (0 until tasks.length()).count {
                    tasks.optJSONObject(it)?.optString("status") == "QUEUED"
                }
                val stale = (0 until tasks.length()).count {
                    tasks.optJSONObject(it)?.optString("status") == "STALE_PLAN_CHANGED"
                }
                val decomposed = (0 until tasks.length()).count {
                    tasks.optJSONObject(it)?.optString("status") == "DECOMPOSED"
                }
                val childTasks = (0 until tasks.length()).count {
                    tasks.optJSONObject(it)?.optString("task_type") == "BOUNDED_DEVELOPMENT_STEP"
                }
                "Plan ledger verified · Objectives $acceptedObjectives/${objectives.length()} accepted · " +
                    "Objective 1 phases $acceptedPhases/${phases.length()} accepted · " +
                    "$queued work packets queued · $decomposed parent packets decomposed into $childTasks child tasks · " +
                    "$stale stale packets awaiting reassessment. Decomposed/queued means planned, not executed; " +
                    "Friday does not mark work complete without evidence."
            }.getOrElse {
                "The local objectives ledger could not be verified. Friday preserves it and will not reset its task history."
            }
            planSummary + "\n" + modelSummary
        }
    }

    var shizukuDozeStatus by remember { mutableStateOf("Not checked.") }
    var keepFridayAlive by remember(resumeSignal) {
        mutableStateOf(ai.friday.assistant.runtime.FridayRuntimeController.isEnabled(context))
    }
    var runtimeServiceRunning by remember(resumeSignal) {
        mutableStateOf(ai.friday.assistant.runtime.FridayRuntimeController.isServiceRunning(context))
    }
    var runtimeMicrophoneActive by remember(resumeSignal) {
        mutableStateOf(ai.friday.assistant.runtime.FridayRuntimeController.isMicrophoneServiceActive(context))
    }
    val batteryOptimizationIgnored = remember(resumeSignal) {
        ai.friday.assistant.runtime.FridayRuntimeController.batteryOptimizationIgnored(context)
    }
    val runtimeStartFailure = remember(resumeSignal) {
        ai.friday.assistant.runtime.FridayRuntimeController.lastStartFailure(context)
    }
    fun refreshRuntimeStatus(expectedRunning: Boolean) {
        scope.launch {
            repeat(20) {
                val snapshot = withContext(Dispatchers.IO) {
                    ai.friday.assistant.runtime.FridayRuntimeController.isServiceRunning(context) to
                        ai.friday.assistant.runtime.FridayRuntimeController.isMicrophoneServiceActive(context)
                }
                runtimeServiceRunning = snapshot.first
                runtimeMicrophoneActive = snapshot.second
                if (snapshot.first == expectedRunning) return@launch
                delay(250L)
            }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            keepFridayAlive = true
            ai.friday.assistant.runtime.FridayRuntimeController.enable(context)
            refreshRuntimeStatus(expectedRunning = true)
        } else {
            keepFridayAlive = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FridayColors.SurfaceBase)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("FRIDAY UPDATES")
        Text(
            text = "Check the official Friday release channel. Friday verifies package identity, version, APK digest and signing certificate before Android receives an update. Android may still require your approval.",
            style = FridayType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = {
                scope.launch {
                    updateStatus = "Checking the official Friday release channel…"
                    stagedUpdate = null
                    val result = withContext(Dispatchers.IO) {
                        FridayUpdateClient(context).checkAndStage()
                    }
                    when (result) {
                        is FridayUpdateClient.Result.UpToDate -> {
                            updateStateStore.clearStaged()
                            updateStatus = "Friday is up to date (version code ${result.installedVersionCode})."
                            updateStateStore.recordStatus(updateStatus)
                        }
                        is FridayUpdateClient.Result.Staged -> {
                            stagedUpdate = result
                            updateStateStore.saveStaged(result)
                            updateStatus = "Verified Friday ${result.release.versionName} is staged and ready for Android installation."
                            updateStateStore.recordStatus(updateStatus)
                        }
                        is FridayUpdateClient.Result.Rejected -> {
                            updateStatus = "Update not installed: ${result.reason}"
                            updateStateStore.recordStatus(updateStatus)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Check for Friday updates") }
        if (stagedUpdate != null) {
            Button(
                onClick = {
                    val staged = stagedUpdate
                    if (staged != null) scope.launch {
                        val useShizuku = shizukuAvailable && shizukuGranted
                        if (useShizuku) {
                            updateStatus = "Verifying release and connecting to Friday's privileged installer…"
                            updateStateStore.recordPendingHealthCheck(staged.release)
                            when (val privileged = FridayShizukuPackageInstaller(context).install(staged)) {
                                is FridayShizukuPackageInstaller.Result.Installed -> {
                                    updateStatus = "Background update installed through Shizuku UID ${privileged.uid}. Friday will verify version, source revision and signer after restart."
                                    updateStateStore.recordStatus(updateStatus)
                                }
                                is FridayShizukuPackageInstaller.Result.Rejected -> {
                                    updateStateStore.clearPendingHealthCheck()
                                    updateStatus = "Privileged update rejected: ${privileged.reason}"
                                    updateStateStore.recordStatus(updateStatus)
                                    return@launch
                                }
                                is FridayShizukuPackageInstaller.Result.Unavailable -> {
                                    updateStateStore.clearPendingHealthCheck()
                                    updateStatus = "Shizuku installer unavailable; using Android's native installer. ${privileged.reason}"
                                    updateStateStore.recordStatus(updateStatus)
                                    when (val native = FridayPackageInstaller(context).install(staged)) {
                                        is FridayPackageInstaller.Result.Submitted -> {
                                            updateStatus = "Update submitted to Android PackageInstaller (session ${native.sessionId}). Android may request confirmation."
                                            updateStateStore.recordStatus(updateStatus)
                                        }
                                        is FridayPackageInstaller.Result.PermissionRequired -> {
                                            updateStatus = "Android requires Friday's install-source permission before the fallback can continue."
                                            updateInstallPermissionLauncher.launch(native.settingsIntent)
                                        }
                                        is FridayPackageInstaller.Result.Rejected -> {
                                            updateStatus = "Native installation was not started: ${native.reason}"
                                            updateStateStore.recordStatus(updateStatus)
                                        }
                                    }
                                }
                            }
                        } else {
                            when (val native = FridayPackageInstaller(context).install(staged)) {
                                is FridayPackageInstaller.Result.Submitted -> {
                                    updateStatus = "Update submitted to Android PackageInstaller (session ${native.sessionId}). Android may request confirmation."
                                    updateStateStore.recordStatus(updateStatus)
                                }
                                is FridayPackageInstaller.Result.PermissionRequired -> {
                                    updateStatus = "Android requires Friday's install-source permission before the fallback can continue."
                                    updateInstallPermissionLauncher.launch(native.settingsIntent)
                                }
                                is FridayPackageInstaller.Result.Rejected -> {
                                    updateStatus = "Native installation was not started: ${native.reason}"
                                    updateStateStore.recordStatus(updateStatus)
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (shizukuAvailable && shizukuGranted) "Install verified update in background" else "Install verified update") }
        }
        Text(text = updateStatus, style = FridayType.Technical, modifier = Modifier.padding(bottom = 16.dp))

        SectionLabel("AI TABLE — CHATGPT PLAN SEAT")
        Text(
            text = "Connect Friday directly to your ChatGPT plan through OpenAI's official open-source sign-in. No API key or paid API fallback. Availability and usage limits depend on your ChatGPT account.",
            style = FridayType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = {
                val activity = context.findActivity()
                if (activity == null) {
                    chatGptStatus = "Friday could not obtain the foreground activity needed to open the secure sign-in flow."
                } else {
                    chatGptBusy = true
                    chatGptStatus = "Waiting for OpenAI authorization in the system browser…"
                    scope.launch {
                        try {
                            chatGptStatus = chatGptAuth.signIn(activity)
                            chatGptConnected = withContext(Dispatchers.IO) { chatGptAuth.isConnected() }
                            chatGptAccountLabel = withContext(Dispatchers.IO) { chatGptAuth.accountLabel() }
                        } catch (failure: Exception) {
                            chatGptStatus = failure.message?.take(240) ?: "ChatGPT sign-in did not complete."
                        } finally {
                            chatGptBusy = false
                        }
                    }
                }
            },
            enabled = !chatGptBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (chatGptBusy) "Waiting for ChatGPT sign-in…" else if (chatGptConnected) "Reconnect ChatGPT" else "Continue with ChatGPT") }
        Text(
            text = if (chatGptConnected) "Connected: $chatGptAccountLabel" else "ChatGPT plan Seat is not connected.",
            style = FridayType.Technical,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Allow background Builder use", style = FridayType.Body)
                Text(
                    "Explicitly authorize Friday to send eligible public task digests to ChatGPT during background work.",
                    style = FridayType.Label
                )
            }
            Switch(
                checked = chatGptBackgroundConsent,
                enabled = chatGptConnected,
                onCheckedChange = {
                    chatGptConsentStore.setEnabled(it)
                    chatGptBackgroundConsent = it
                    chatGptStatus = if (it) {
                        "Background ChatGPT plan use is enabled for explicitly shareable Builder digests."
                    } else {
                        "Background ChatGPT plan use is disabled."
                    }
                }
            )
        }
        Text(
            text = "Friday does not receive access to ChatGPT conversations or memories. Only PUBLIC, explicitly shareable Builder digests are sent. Usage limits apply; no paid fallback is attempted.",
            style = FridayType.Label,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (chatGptConnected) {
            TextButton(
                enabled = !chatGptBusy,
                onClick = {
                    chatGptConsentStore.setEnabled(false)
                    chatGptBackgroundConsent = false
                    chatGptBusy = true
                    chatGptStatus = "Revoking the ChatGPT renewable session…"
                    scope.launch {
                        try {
                            chatGptStatus = chatGptAuth.disconnect()
                        } catch (failure: Exception) {
                            chatGptStatus = "Local sign-out failed: ${failure.javaClass.simpleName}."
                        } finally {
                            chatGptConnected = withContext(Dispatchers.IO) { chatGptAuth.isConnected() }
                            chatGptAccountLabel = withContext(Dispatchers.IO) { chatGptAuth.accountLabel() }
                            chatGptBusy = false
                        }
                    }
                }
            ) { Text("Disconnect ChatGPT") }
        }
        Text(text = chatGptStatus, style = FridayType.Technical, modifier = Modifier.padding(bottom = 16.dp))

        SectionLabel("PERSONALITY")
        Text(
            text = "This changes how FRIDAY answers, not what he knows.",
            style = FridayType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = personaText,
            onValueChange = {
                personaText = it
                prefs.edit().putString(KEY_PERSONA, it).apply()
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Persona prompt", style = FridayType.Body) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FridayColors.SurfaceHairline,
                unfocusedBorderColor = FridayColors.SurfaceHairline
            )
        )

        SectionLabel("VOICE")
        Text(text = "TTS voice: en-GB (system)", style = FridayType.Technical)

        SectionLabel("JEV DEVICE-DECISION ENGINE")
        Text(
            text = "Optional TypeSafe Jev cloud decision support. Friday's local command layer handles bounded actions; an optional local model may help form more complex phone-action plans. A redacted proposed action and risk tier may be sent to Jev for review. Screen snapshots and literal text-entry payloads stay on-device. Jev cannot execute actions or grant permissions; consequential actions still require Friday's confirmation. The key is encrypted with Android Keystore.",
            style = FridayType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = jevApiKey,
            onValueChange = { jevApiKey = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("TypeSafe API key", style = FridayType.Body) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FridayColors.SurfaceHairline,
                unfocusedBorderColor = FridayColors.SurfaceHairline
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val key = jevApiKey
                    if (key.isNotBlank()) scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { ai.friday.assistant.voice.JevApiKeyStore(context).save(key) }
                        }
                        result.onSuccess { jevConfigured = true; jevApiKey = "" }
                            .onFailure { jevConfigured = false }
                    }
                },
                enabled = jevApiKey.isNotBlank()
            ) { Text("Save Jev key") }
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { ai.friday.assistant.voice.JevApiKeyStore(context).clear() }
                    jevConfigured = false
                    jevApiKey = ""
                }
            }) { Text("Remove key") }
        }
        Text(
            text = if (jevConfigured) "Jev credential stored securely; decision calls require internet." else "Jev not configured — Friday continues with local deterministic command handling.",
            style = FridayType.Technical
        )

        SectionLabel("PHONE CONTROL")
        Text(
            text = "Enable Android Accessibility only if you want Friday to perform approved on-screen actions. Android keeps this permission under your control.",
            style = FridayType.Label,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (phoneControlConnected) "Accessibility enabled" else "Accessibility not enabled", style = FridayType.Technical)
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text(if (phoneControlConnected) "Manage" else "Enable")
            }
        }


        SectionLabel("OBJECTIVES AND DEVELOPMENT")
        Text(
            text = "Friday keeps the canonical objectives and evidence ledger locally. Work is tracked across the full plan; queued packets are not counted as completed work. Repository changes still require a configured, authorized development executor.",
            style = FridayType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = objectiveExecutionStatus,
            style = FridayType.Technical,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        SectionLabel("LOCAL CONTINUITY")
        Text(
            text = "Friday keeps her identity, local memory and available on-device capabilities on this phone. Missing model assets, revoked permissions or Android resource limits can reduce individual capabilities. GitHub reporting is a separate owner-authorized channel; local continuity does not depend on it.",
            style = FridayType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SectionLabel("FRIDAY RUNTIME")
        Text(
            text = "Keep Friday's local continuity and background presence alive with an Android foreground service. Android still controls process scheduling; this is a persistence aid, not a way around force-stop or system policy.",
            style = FridayType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Keep Friday alive", style = FridayType.Body)
                Text(
                    if (keepFridayAlive) "Setting: persistent runtime enabled" else "Setting: runtime starts only while the app is active",
                    style = FridayType.Technical
                )
                Text(
                    text = when {
                        runtimeServiceRunning && keepFridayAlive && runtimeMicrophoneActive ->
                            "Live status: foreground service confirmed running · microphone owned by Friday."
                        runtimeServiceRunning && keepFridayAlive ->
                            "Live status: foreground service confirmed running · microphone inactive."
                        runtimeServiceRunning ->
                            "Live status: service is still shutting down after the stop request."
                        keepFridayAlive ->
                            "Live status: service is not running; startup may be pending or Android may have blocked it."
                        else ->
                            "Live status: foreground service stopped."
                    },
                    style = FridayType.Technical
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
                            ai.friday.assistant.runtime.FridayRuntimeController.enable(context)
                            refreshRuntimeStatus(expectedRunning = true)
                        }
                    } else {
                        keepFridayAlive = false
                        ai.friday.assistant.runtime.FridayRuntimeController.disable(context)
                        refreshRuntimeStatus(expectedRunning = false)
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = FridayColors.CoreIdle)
            )
        }
        Text(
            text = if (batteryOptimizationIgnored) {
                "Battery optimization: Friday is exempt from Doze battery optimization."
            } else {
                "Battery optimization: Android may defer or stop background work. Exempt Friday below for better persistence."
            },
            style = FridayType.Technical,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = { ai.friday.assistant.runtime.FridayRuntimeController.openBatteryOptimizationSettings(context) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open battery optimization settings") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    scope.launch {
                        shizukuDozeStatus = runCatching {
                            ai.friday.assistant.ScreenBridge.setDozeWhitelist(true)
                                ?: "Shizuku service is not connected."
                        }.getOrElse { it.message ?: "Could not enable Doze exemption." }
                    }
                },
                enabled = shizukuStatus.startsWith("bound · verified Shizuku uid=")
            ) { Text("Shizuku: protect Friday") }
            TextButton(
                onClick = {
                    scope.launch {
                        shizukuDozeStatus = runCatching {
                            ai.friday.assistant.ScreenBridge.setDozeWhitelist(false)
                                ?: "Shizuku service is not connected."
                        }.getOrElse { it.message ?: "Could not remove Doze exemption." }
                    }
                },
                enabled = shizukuStatus.startsWith("bound · verified Shizuku uid=")
            ) { Text("Remove") }
        }
        Text(
            text = "Shizuku Doze status: $shizukuDozeStatus. This changes only Android's Device Idle whitelist for Friday; it cannot override force-stop or manufacturer task killers.",
            style = FridayType.Technical,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (runtimeStartFailure.isNotBlank()) {
            Text(
                text = "Last runtime start issue: $runtimeStartFailure. Open Friday to retry.",
                style = FridayType.Technical,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Text(
            text = "After reboot Friday restores her non-microphone core where Android permits. Microphone capture is resumed only after Friday is opened in the foreground with RECORD_AUDIO granted. Force-stop, some manufacturer task killers, permission revocation and thermal shutdown cannot be defeated by an app.",
            style = FridayType.Label,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        SectionLabel("DEVICE CAPABILITIES")
        Text(
            text = when {
                !shizukuAvailable -> "Shizuku is not running. Friday remains operational without it."
                !shizukuGranted -> "Shizuku is running; Friday has not been granted access yet."
                else -> "Shizuku permission is granted. Connect Friday's privileged device capability service."
            },
            style = FridayType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = {
                when {
                    !shizukuAvailable -> ai.friday.assistant.ShizukuBridge.refresh()
                    !shizukuGranted -> ai.friday.assistant.ShizukuBridge.requestPermission()
                    else -> ai.friday.assistant.ScreenBridge.bind(context)
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
        Text(text = shizukuStatus, style = FridayType.Technical, modifier = Modifier.padding(vertical = 6.dp))
        Button(
            onClick = {
                scope.launch {
                    shizukuSnapshot = ai.friday.assistant.ScreenBridge.diagnosticsSnapshot()
                        ?: "Device service is not connected."
                }
            },
            enabled = shizukuStatus.startsWith("bound · verified Shizuku uid="),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Read live device diagnostics") }
        if (shizukuSnapshot.isNotBlank()) {
            Text(text = shizukuSnapshot, style = FridayType.Technical, modifier = Modifier.padding(top = 6.dp))
        }
        Button(
            onClick = {
                shizukuAcceptanceRunning = true
                shizukuAcceptanceReport = "Running remote identity, diagnostics, Doze add/remove/restore and harmless app-launch checks. Android Settings may open during the final check."
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            ai.friday.assistant.android.ShizukuDeviceAcceptanceRunner().run(context)
                        }
                    }
                    shizukuAcceptanceReport = result.fold(
                        onSuccess = { it },
                        onFailure = { "Device acceptance failed: " + (it.message ?: it.javaClass.simpleName) }
                    )
                    shizukuAcceptanceRunning = false
                }
            },
            enabled = shizukuGranted && !shizukuAcceptanceRunning,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (shizukuAcceptanceRunning) "Running device acceptance…" else "Run Shizuku device acceptance") }
        if (shizukuAcceptanceReport.isNotBlank()) {
            Text(text = shizukuAcceptanceReport, style = FridayType.Technical, modifier = Modifier.padding(vertical = 6.dp))
        }

        SectionLabel("LIVE TEST ACTION JOURNAL")
        Text(
            text = "Friday records bounded phone-control attempts locally. The journal stores action categories, execution routes, outcomes and durations—not spoken requests, typed text, tap labels, screen contents or credentials.",
            style = FridayType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "$actionTraceCount action events currently stored on this phone.",
            style = FridayType.Technical,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Button(
            onClick = {
                val token = githubCredentialStore.read()
                if (token == null) {
                    githubDevelopmentStatus = "Save a GitHub credential first."
                } else {
                    githubBusy = true
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                ai.friday.assistant.github.reporting.GitHubDevelopmentRepositoryClient(token)
                                    .verifyDevelopmentAccess()
                            }
                        }
                        githubDevelopmentStatus = result.fold(
                            onSuccess = { it },
                            onFailure = { it.message ?: "GitHub development access check failed." }
                        )
                        githubBusy = false
                    }
                }
            },
            enabled = githubConfigured && !githubBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Verify Friday development repository access") }
        Text(
            text = githubDevelopmentStatus,
            style = FridayType.Technical,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        SectionLabel("GITHUB DEVICE REPORTING")
        Text(
            text = "Create a fine-grained token limited to the mhw77655-hue/Friday repository, with Contents read/write, Pull requests read/write, Actions read, and Issues read/write (Metadata read is automatic). These permissions let Friday inspect the canonical plan, publish isolated candidate branches/PRs, check CI, and submit sanitized device acceptance reports. Paste it here once. Friday encrypts it with Android Keystore. Automatic reporting is OFF by default and requires the separate consent switch below. If enabled, Friday submits bounded app/build identity, host capability states, sanitized crash frames and action categories after defined milestones. It never includes conversations, speech transcripts, typed content, screen text, notifications or the token. Reports create public GitHub issues/artifacts. Anyone can read them. Enable automatic reporting only if you accept this public disclosure.",
            style = FridayType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Automatic sanitized device reports", style = FridayType.Body)
                Text(
                    if (githubAutoReportConsent) "Enabled by owner consent; reports are sent to public GitHub issues after host and Builder milestones."
                    else "Off. Friday keeps reports local until you explicitly enable this.",
                    style = FridayType.Technical
                )
            }
            Switch(
                checked = githubAutoReportConsent,
                onCheckedChange = { enabled ->
                    runCatching { githubDeviceReportConsentStore.setEnabled(enabled) }
                        .onSuccess {
                            githubAutoReportConsent = enabled
                            githubReportStatus = if (enabled) {
                                "Automatic sanitized reporting enabled by your consent."
                            } else {
                                "Automatic device reporting disabled. Existing local pending reports remain local."
                            }
                            if (enabled && githubConfigured) {
                                ai.friday.assistant.github.reporting.GitHubDeviceReportWorker.enqueue(
                                    context, "owner-consent-enabled"
                                )
                            }
                        }
                        .onFailure {
                            githubReportStatus = it.message ?: "Reporting consent could not be saved."
                        }
                }
            )
        }
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/settings/personal-access-tokens/new")
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create fine-grained GitHub token") }
        OutlinedTextField(
            value = githubToken,
            onValueChange = { githubToken = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Fine-grained GitHub token", style = FridayType.Body) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FridayColors.SurfaceHairline,
                unfocusedBorderColor = FridayColors.SurfaceHairline
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val tokenToSave = githubToken
                    if (tokenToSave.isNotBlank()) scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { githubCredentialStore.save(tokenToSave) }
                        }
                        result.onSuccess {
                            githubConfigured = true
                            githubToken = ""
                            githubReportStatus = "Credential encrypted locally. Friday's Builder queue has been started."
                            ai.friday.assistant.bootstrap.host.HostBootstrapWorker.enqueue(context)
                            ai.friday.assistant.model.storage.ResidentModelBootstrapWorker.enqueue(context)
                            ai.friday.assistant.bootstrap.plan.ObjectiveExecutionWorker.enqueue(context)
                            if (githubAutoReportConsent) {
                                ai.friday.assistant.github.reporting.GitHubDeviceReportWorker.enqueue(context, "credential-saved")
                            }
                        }.onFailure {
                            githubReportStatus = it.message ?: "Credential could not be stored securely."
                        }
                    }
                },
                enabled = githubToken.isNotBlank()
            ) { Text("Save GitHub credential") }
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { githubCredentialStore.clear() }
                    githubDeviceReportConsentStore.setEnabled(false)
                    githubAutoReportConsent = false
                    githubConfigured = false
                    githubToken = ""
                    githubReportStatus = "GitHub credential and automatic-reporting consent removed from this phone. Pending evidence remains local."
                }
            }) { Text("Remove") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    githubBusy = true
                    scope.launch {
                        val token = withContext(Dispatchers.IO) { githubCredentialStore.read() }
                        if (token == null) {
                            githubReportStatus = "Save a GitHub credential first."
                            githubBusy = false
                            return@launch
                        }
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                FridayGitHubReportClient(token).verifyRepositoryAccess()
                            }
                        }
                        githubReportStatus = result.fold(
                            onSuccess = { it },
                            onFailure = { it.message ?: "GitHub access verification failed." }
                        )
                        githubBusy = false
                    }
                },
                enabled = githubConfigured && !githubBusy
            ) { Text("Verify access") }
            Button(
                onClick = {
                    githubBusy = true
                    githubReportStatus = "Preparing and uploading the sanitized device report…"
                    scope.launch {
                        val token = withContext(Dispatchers.IO) { githubCredentialStore.read() }
                        if (token == null) {
                            githubReportStatus = "Save a GitHub credential first."
                            githubBusy = false
                            return@launch
                        }
                        val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    val report = githubPendingReportStore.read()
                                        ?: ai.friday.assistant.diagnostics.PhoneActionDiagnostics.exportJson(context)
                                            .also(githubPendingReportStore::save)
                                    val issueUrl = FridayGitHubReportClient(token).submitDeviceReport(report)
                                    githubPendingReportStore.clear()
                                    issueUrl
                                }
                            }
                            githubPendingReport = githubPendingReportStore.read() != null
                            githubReportStatus = result.fold(
                                onSuccess = { "Report uploaded successfully: $it" },
                                onFailure = { it.message ?: "GitHub report upload failed. If a report was prepared, Friday kept the exact payload for retry." }
                            )
                        githubBusy = false
                    }
                },
                enabled = githubConfigured && !githubBusy
            ) { Text(if (githubBusy) "Working…" else if (githubPendingReport) "Retry pending report" else "Upload device report") }
        }
        if (githubPendingReport && !githubBusy) {
            TextButton(
                onClick = {
                    githubPendingReportStore.clear()
                    githubPendingReport = false
                    githubReportStatus = "Pending report discarded. The next upload will capture a fresh snapshot."
                }
            ) { Text("Discard pending report and capture a fresh one") }
        }
        Text(
            text = githubReportStatus,
            style = FridayType.Technical,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        TextButton(
            onClick = {
                ai.friday.assistant.diagnostics.PhoneActionDiagnostics.clear()
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
            placeholder = { Text("Recall limit", style = FridayType.Body) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FridayColors.SurfaceHairline,
                unfocusedBorderColor = FridayColors.SurfaceHairline
            )
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = FridayType.Label, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
}

private fun android.content.Context.findActivity(): Activity? {
    var current: android.content.Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
