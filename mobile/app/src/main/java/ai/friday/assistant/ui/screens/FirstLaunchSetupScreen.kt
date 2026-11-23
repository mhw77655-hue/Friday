package ai.friday.assistant.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import ai.friday.assistant.voice.FridayAccessibilityService
import ai.friday.assistant.FridayNotificationListener
import ai.friday.assistant.ShizukuBridge
import ai.friday.assistant.ScreenBridge
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONObject
import rikka.shizuku.Shizuku

private data class SetupItem(
    val title: String,
    val purpose: String,
    val status: String,
    val actionLabel: String,
    val action: () -> Unit
)

/**
 * Owner-guided first launch. Every row explains the feature, opens its exact
 * Android/manager surface, and is re-evaluated when Friday resumes. No access
 * is silently enabled and no unrelated permission is requested.
 */
@Composable
fun FirstLaunchSetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshToken by remember { mutableIntStateOf(0) }
    var shizukuExecutionStatus by remember { mutableStateOf("Not checked") }
    var hostStudySummary by remember { mutableStateOf("Friday is starting her phone study…") }
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ShizukuBridge.refresh()
                refreshToken++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    val microphoneGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    val runtimeNotificationPermissionGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val notificationsEnabled = context.getSystemService(NotificationManager::class.java)
        ?.areNotificationsEnabled() == true
    val notificationsGranted = runtimeNotificationPermissionGranted && notificationsEnabled
    val accessibilityEnabled = isServiceEnabled(
        context, ComponentName(context, FridayAccessibilityService::class.java)
    )
    val notificationAccessEnabled = isServiceEnabled(
        context, ComponentName(context, FridayNotificationListener::class.java)
    )
    val batteryExempt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else true
    val shizukuManagerVersion = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                "moe.shizuku.privileged.api", PackageManager.PackageInfoFlags.of(0)
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0).versionName
        }
    }.getOrNull()
    val shizukuInstalled = shizukuManagerVersion != null
    val shizukuNextInstalled = shizukuManagerVersion?.contains("next", ignoreCase = true) == true
    val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    val shizukuAuthorized = shizukuRunning && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)


    // Read the exact local report produced by Friday's first-launch host worker.
    // A missing report is shown as queued/running, never as a successful study.
    LaunchedEffect(refreshToken) {
        repeat(10) { attempt ->
            val summary = withContext(Dispatchers.IO) {
                val root = File(context.filesDir, "friday-development")
                val reportFile = File(root, "host-bootstrap.json")
                val failureFile = File(root, "host-bootstrap-failure.json")
                val modelBootstrapFile = File(root, "model-bootstrap.json")
                val modelBootstrap = if (modelBootstrapFile.isFile) runCatching {
                    JSONObject(modelBootstrapFile.readText(Charsets.UTF_8))
                }.getOrNull() else null
                val executionFile = File(root, "plan/execution-ledger.json")
                val executionSummary = if (executionFile.isFile) runCatching {
                    val ledger = JSONObject(executionFile.readText(Charsets.UTF_8))
                    val phases = ledger.optJSONArray("phases") ?: org.json.JSONArray()
                    val objectives = ledger.optJSONArray("objectives") ?: org.json.JSONArray()
                    val tasks = ledger.optJSONArray("tasks") ?: org.json.JSONArray()
                    val acceptedObjectives = (0 until objectives.length()).count { index ->
                        objectives.optJSONObject(index)?.optString("status") == "ACCEPTED"
                    }
                    val assessed = (0 until phases.length()).count { index ->
                        phases.optJSONObject(index)?.optString("status") == "ACCEPTED"
                    }
                    val queued = (0 until tasks.length()).count { index ->
                        tasks.optJSONObject(index)?.optString("status") == "QUEUED"
                    }
                    "Objectives: $acceptedObjectives/${objectives.length()} accepted · Objective 1: $assessed/${phases.length()} phases accepted · $queued work packets queued (not yet executed)"
                }.getOrDefault("Objective 1 ledger exists but could not be read.")
                else "Objective 1: waiting for the local execution ledger."
                when {
                    reportFile.isFile -> runCatching {
                        val report = JSONObject(reportFile.readText(Charsets.UTF_8))
                        val host = report.optJSONObject("host") ?: JSONObject()
                        val capacity = report.optJSONObject("capacity") ?: JSONObject()
                        val config = report.optJSONObject("configuration") ?: JSONObject()
                        val shizuku = report.optJSONObject("integrations")
                            ?.optJSONObject("shizuku") ?: JSONObject()
                        val mode = config.optString("mode", "UNKNOWN")
                        val memory = capacity.optLong("available_memory_mb", -1L)
                        val cores = host.optInt("cpu_cores", -1)
                        val cpu = capacity.opt("cpu_load_percent")
                        val cpuLabel = if (cpu == null || cpu == JSONObject.NULL) "CPU load unavailable"
                            else "CPU " + cpu.toString() + "%"
                        val shizukuLabel = when {
                            shizuku.optBoolean("usable_by_friday") ->
                                "Shizuku " + shizuku.optString("privilege")
                            shizuku.optBoolean("binder_reachable") ->
                                "Shizuku running · authorization/privilege incomplete"
                            shizuku.optBoolean("manager_installed") -> "Shizuku installed · server stopped"
                            else -> "Shizuku not installed"
                        }
                        val modelLabel = when (modelBootstrap?.optString("state")) {
                            "MODEL_READY" -> "resident local model loaded and verified"
                            "DOWNLOADING" -> "resident local model downloading over unmetered Wi-Fi"
                            "LOADING" -> "resident local model is loading"
                            "WAITING_FOR_HOST_CAPACITY" -> "resident model waiting for host capacity"
                            "WAITING_FOR_STORAGE" -> "resident model waiting for private storage"
                            "WAITING_FOR_SAFE_DOWNLOAD_WINDOW" -> "resident model waiting for unmetered charging window"
                            "WAITING_FOR_NATIVE_RUNTIME" -> "resident model waiting for native runtime"
                            else -> if (config.optBoolean("allow_local_model_load")) {
                                "local model load eligible within live admission checks"
                            } else "local model loading not currently qualified"
                        }
                        "Phone profile: " + mode + " · " + memory + " MB available · " +
                            cores + " CPU cores · " + cpuLabel + " · " + shizukuLabel + " · " + modelLabel +
                            "\n" + executionSummary
                    }.getOrElse { "Phone study report exists but could not be read safely." }
                    failureFile.isFile -> runCatching {
                        val failure = JSONObject(failureFile.readText(Charsets.UTF_8))
                        "Phone study needs retry (" + failure.optString("failure_type", "unknown failure") +
                            "); Friday will retry."
                    }.getOrDefault("Phone study failed to record a readable status; Friday will retry.")
                    else -> "Friday is studying this phone and preparing her local configuration…\n" + executionSummary
                }
            }
            hostStudySummary = summary
            if (!summary.startsWith("Friday is studying") &&
                !summary.contains("waiting for the local execution ledger")
            ) return@LaunchedEffect
            if (attempt < 9) delay(500)
        }
    }

    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshToken++ }
    val requestMicrophone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshToken++ }
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshToken++ }

    // Re-check effective shell/root identity after authorization and every return from settings.
    LaunchedEffect(shizukuAuthorized, refreshToken) {
        if (!shizukuAuthorized) {
            shizukuExecutionStatus = if (shizukuRunning) "Friday authorization required" else "Shizuku server unavailable"
        } else if (!shizukuExecutionStatus.startsWith("bound · verified Shizuku uid=")) {
            shizukuExecutionStatus = ScreenBridge.ensureVerifiedBinding(context)
        }
    }

    val items = listOf(
        SetupItem(
            "Camera",
            "Optional visual perception. Friday requests camera access only when you choose to use a camera feature.",
            if (cameraGranted) "Enabled" else "Optional · not granted",
            if (cameraGranted) "Verified" else "Allow camera"
        ) {
            requestCamera.launch(Manifest.permission.CAMERA)
        },
        SetupItem(
            "Microphone",
            "Enables voice conversations. Friday only requests it when you choose voice.",
            if (microphoneGranted) "Enabled" else "Not granted",
            if (microphoneGranted) "Verified" else "Allow microphone"
        ) {
            if (Build.VERSION.SDK_INT >= 23) requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        },
        SetupItem(
            "Notifications",
            "Allows Friday to show task, runtime and update status.",
            if (notificationsGranted) "Enabled" else "Not granted",
            if (notificationsGranted) "Verified" else "Allow notifications"
        ) {
            if (Build.VERSION.SDK_INT >= 33 && !runtimeNotificationPermissionGranted) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openAppNotificationSettings(context)
            }
        },
        SetupItem(
            "Accessibility service",
            "Optional phone interaction through Friday's explicitly enabled accessibility service.",
            if (accessibilityEnabled) "Enabled" else "Optional · disabled",
            if (accessibilityEnabled) "Verified" else "Open accessibility settings"
        ) { openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
        SetupItem(
            "Notification access",
            "Optional notification context. Friday can still run without reading notifications.",
            if (notificationAccessEnabled) "Enabled" else "Optional · disabled",
            if (notificationAccessEnabled) "Verified" else "Open notification access settings"
        ) { openSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
        SetupItem(
            "Background battery policy",
            "Optional on devices that restrict Friday's user-enabled background runtime. Android may still stop background work.",
            if (batteryExempt) "Unrestricted by battery optimization" else "System-managed",
            if (batteryExempt) "Verified" else "Review battery settings"
        ) { openBatterySettings(context) },
        SetupItem(
            "Shizuku",
            "Optional elevated phone capability. Friday checks the live Binder and its own authorization; ADB-shell is not root.",
            when {
                shizukuAuthorized -> shizukuExecutionStatus
                shizukuRunning -> "Running · Friday not authorized"
                shizukuInstalled -> "Installed · server stopped"
                else -> "Not installed"
            },
            when {
                shizukuExecutionStatus.startsWith("bound · verified Shizuku uid=") -> "Verified"
                shizukuAuthorized -> "Verify effective privilege"
                shizukuRunning -> "Authorize Friday in Shizuku"
                shizukuInstalled -> "Open Shizuku"
                else -> "Open Shizuku setup guide"
            }
        ) {
            if (shizukuAuthorized) {
                scope.launch { shizukuExecutionStatus = ScreenBridge.ensureVerifiedBinding(context) }
            } else {
                openShizuku(context)
            }
        }
    )

    val verifiedCount = items.count { item ->
        item.status == "Enabled" ||
            item.status == "Unrestricted by battery optimization" ||
            item.status.startsWith("bound · verified Shizuku uid=")
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Let's configure Friday", style = MaterialTheme.typography.headlineMedium)
        Text(
            "I'll open each relevant system screen for you. When you return, I'll check what actually changed. Optional capabilities can be skipped.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(hostStudySummary, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = verifiedCount.toFloat() / items.size.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Text("$verifiedCount of ${items.size} setup items verified", style = MaterialTheme.typography.labelMedium)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.status, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(item.purpose, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = item.action, enabled = !item.actionLabel.startsWith("Verified")) {
                            Text(item.actionLabel)
                        }
                    }
                }
            }
        }
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
            Text("Finish setup")
        }
        Text(
            "You can change these choices later in Android Settings. Friday's core typed interaction does not depend on optional access.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun isServiceEnabled(context: Context, component: ComponentName): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    val listeners = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
    ).orEmpty()
    return enabled.split(':').any { it.equals(component.flattenToString(), ignoreCase = true) } ||
        listeners.split(':').any { it.equals(component.flattenToString(), ignoreCase = true) }
}

private fun openSettings(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openBatterySettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
    } else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openShizuku(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    if (launch != null) {
        runCatching { context.startActivity(launch) }
    } else {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
