package com.jarvis.app.ui.screens

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType

private const val PREFS_NAME = "jarvis_settings"
private const val KEY_PERSONA = "persona_text"
private const val KEY_RECALL_LIMIT = "recall_limit"

/**
 * Local runtime controls, capability status and owner-exported diagnostics.
 * Cloud development and public issue/PR automation are deliberately not
 * exposed here; local continuity must remain independent of GitHub.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var personaText by remember { mutableStateOf(prefs.getString(KEY_PERSONA, "") ?: "") }
    var recallLimit by remember { mutableStateOf(prefs.getString(KEY_RECALL_LIMIT, "") ?: "") }
    var jevApiKey by remember { mutableStateOf("") }
    var jevConfigured by remember { mutableStateOf(com.jarvis.app.voice.JevApiKeyStore(context).read() != null) }
    val scope = rememberCoroutineScope()
    val shizukuAvailable by com.jarvis.app.ShizukuBridge.isAvailable.collectAsState()
    val shizukuGranted by com.jarvis.app.ShizukuBridge.isGranted.collectAsState()
    val shizukuStatus by com.jarvis.app.ScreenBridge.status.collectAsState()
    var shizukuSnapshot by remember { mutableStateOf("") }

    // Bumped on ON_RESUME so runtime status and local diagnostic counts
    // refresh when the owner returns to Settings from Android system panels.
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
    var shizukuDozeStatus by remember { mutableStateOf("Not checked.") }
    var keepFridayAlive by remember(resumeSignal) {
        mutableStateOf(com.jarvis.app.runtime.FridayRuntimeController.isEnabled(context))
    }
    var runtimeServiceRunning by remember(resumeSignal) {
        mutableStateOf(com.jarvis.app.runtime.FridayRuntimeController.isServiceRunning(context))
    }
    var runtimeMicrophoneActive by remember(resumeSignal) {
        mutableStateOf(com.jarvis.app.runtime.FridayRuntimeController.isMicrophoneServiceActive(context))
    }
    val batteryOptimizationIgnored = remember(resumeSignal) {
        com.jarvis.app.runtime.FridayRuntimeController.batteryOptimizationIgnored(context)
    }
    val runtimeStartFailure = remember(resumeSignal) {
        com.jarvis.app.runtime.FridayRuntimeController.lastStartFailure(context)
    }
    fun refreshRuntimeStatus(expectedRunning: Boolean) {
        scope.launch {
            repeat(20) {
                val snapshot = withContext(Dispatchers.IO) {
                    com.jarvis.app.runtime.FridayRuntimeController.isServiceRunning(context) to
                        com.jarvis.app.runtime.FridayRuntimeController.isMicrophoneServiceActive(context)
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
            com.jarvis.app.runtime.FridayRuntimeController.enable(context)
            refreshRuntimeStatus(expectedRunning = true)
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


        SectionLabel("LOCAL CONTINUITY")
        Text(
            text = "Friday keeps her identity, local memory and available on-device capabilities on this phone. Missing model assets, revoked permissions or Android resource limits can reduce individual capabilities. No public GitHub issue, pull request or remote build is created from this screen.",
            style = JarvisType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
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
                    if (keepFridayAlive) "Setting: persistent runtime enabled" else "Setting: runtime starts only while the app is active",
                    style = JarvisType.Technical
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
                            refreshRuntimeStatus(expectedRunning = true)
                        }
                    } else {
                        keepFridayAlive = false
                        com.jarvis.app.runtime.FridayRuntimeController.disable(context)
                        refreshRuntimeStatus(expectedRunning = false)
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
                    scope.launch {
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
                    scope.launch {
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
                scope.launch {
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
private fun SectionLabel(text: String) {
    Text(text = text, style = JarvisType.Label, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
}
