package ai.friday.assistant.ui.screens

import android.Manifest
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
import ai.friday.assistant.update.FridayPackageInstaller
import ai.friday.assistant.update.FridayUpdateClient
import ai.friday.assistant.update.FridayUpdateStateStore
import ai.friday.assistant.github.reporting.GitHubCredentialStore
import ai.friday.assistant.github.reporting.FridayGitHubReportClient
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType

private const val PREFS_NAME = "friday_settings"
private const val KEY_PERSONA = "persona_text"
private const val KEY_RECALL_LIMIT = "recall_limit"

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
    var jevConfigured by remember { mutableStateOf(ai.friday.assistant.voice.JevApiKeyStore(context).read() != null) }
    var githubToken by remember { mutableStateOf("") }
    val githubCredentialStore = remember { GitHubCredentialStore(context) }
    var githubConfigured by remember { mutableStateOf(githubCredentialStore.isConfigured()) }
    val githubPendingReportStore = remember { ai.friday.assistant.github.reporting.GitHubPendingReportStore(context) }
    var githubPendingReport by remember { mutableStateOf(githubPendingReportStore.read() != null) }
    var githubDevelopmentStatus by remember { mutableStateOf("Development repository access has not been checked.") }
    var githubReportStatus by remember {
        mutableStateOf(if (githubConfigured) "GitHub credential is encrypted on this phone." else "GitHub reporting is not connected.")
    }
    var githubBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val updateStateStore = remember { FridayUpdateStateStore(context) }
    var updateStatus by remember { mutableStateOf(updateStateStore.lastStatus()) }
    var stagedUpdate by remember { mutableStateOf(updateStateStore.loadStaged()) }
    val updateInstallPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.canRequestPackageInstalls()
        ) {
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
    var actionTraceCount by remember(resumeSignal) { mutableIntStateOf(ai.friday.assistant.diagnostics.PhoneActionDiagnostics.eventCount()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val phoneControlConnected = remember(resumeSignal) {
        ai.friday.assistant.voice.FridayAccessibilityService.isEnabled()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeSignal++
                updateStatus = updateStateStore.lastStatus()
                stagedUpdate = updateStateStore.loadStaged()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeSignal) {
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
                    when (val result = FridayPackageInstaller(context).install(stagedUpdate!!)) {
                        is FridayPackageInstaller.Result.Submitted -> {
                            updateStatus = "Update submitted to Android PackageInstaller (session ${result.sessionId}). Waiting for Android's result."
                            updateStateStore.recordStatus(updateStatus)
                        }
                        is FridayPackageInstaller.Result.PermissionRequired -> {
                            updateStatus = "Allow Friday as an install source in Android settings, then return and tap Install again."
                            updateInstallPermissionLauncher.launch(result.settingsIntent)
                        }
                        is FridayPackageInstaller.Result.Rejected -> {
                            updateStatus = "Android installation was not started: ${result.reason}"
                            updateStateStore.recordStatus(updateStatus)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Install verified update") }
        }
        Text(text = updateStatus, style = FridayType.Technical, modifier = Modifier.padding(bottom = 16.dp))

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
                    if (jevApiKey.isNotBlank()) {
                        runCatching { ai.friday.assistant.voice.JevApiKeyStore(context).save(jevApiKey) }
                            .onSuccess { jevConfigured = true; jevApiKey = "" }
                    }
                },
                enabled = jevApiKey.isNotBlank()
            ) { Text("Save Jev key") }
            TextButton(onClick = {
                ai.friday.assistant.voice.JevApiKeyStore(context).clear()
                jevConfigured = false
                jevApiKey = ""
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
                        ai.friday.assistant.android.ShizukuDeviceAcceptanceRunner().run(context)
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
            text = "Create a fine-grained token limited to the mhw77655-hue/Friday repository, with Contents read/write, Pull requests read/write, Actions read, and Issues read/write (Metadata read is automatic). These permissions let Friday inspect the canonical plan, publish isolated candidate branches/PRs, check CI, and submit optional device reports. Paste it here once. Friday encrypts it with Android Keystore. When you tap Upload, Friday sends a bounded report containing app/build identity, device capability states, sanitized crash frames and action categories. It never includes conversations, speech transcripts, typed content, screen text, notifications or the token. Each upload creates a GitHub issue and a validated Actions artifact. This Friday repository is public. Anyone can read uploaded GitHub issues and Actions artifacts. Upload only if you accept those sanitized device diagnostics being public.",
            style = FridayType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
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
                    runCatching { githubCredentialStore.save(githubToken) }
                        .onSuccess {
                            githubConfigured = true
                            githubToken = ""
                            githubReportStatus = "Credential encrypted locally. Verify repository access before uploading."
                        }
                        runCatching { githubCredentialStore.save(githubToken) }
                        .onSuccess {
                            githubConfigured = true
                            githubToken = ""
                            githubReportStatus = "Credential encrypted locally. Verify repository access before uploading."
                            ai.friday.assistant.bootstrap.plan.ObjectiveExecutionWorker.enqueue(context)
                        }
                        .onFailure {
                            githubReportStatus = it.message ?: "Credential could not be stored securely."
                        }
                },
                enabled = githubToken.isNotBlank()
            ) { Text("Save GitHub credential") }
            TextButton(onClick = {
                githubCredentialStore.clear()
                githubConfigured = false
                githubToken = ""
                githubReportStatus = "GitHub credential removed from this phone."
            }) { Text("Remove") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val token = githubCredentialStore.read()
                    if (token == null) {
                        githubReportStatus = "Save a GitHub credential first."
                    } else {
                        githubBusy = true
                        scope.launch {
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
                    }
                },
                enabled = githubConfigured && !githubBusy
            ) { Text("Verify access") }
            Button(
                onClick = {
                    val token = githubCredentialStore.read()
                    if (token == null) {
                        githubReportStatus = "Save a GitHub credential first."
                    } else {
                        githubBusy = true
                        githubReportStatus = "Preparing and uploading the sanitized device report…"
                        scope.launch {
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
