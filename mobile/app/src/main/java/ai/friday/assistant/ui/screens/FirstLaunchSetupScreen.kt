package ai.friday.assistant.ui.screens

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    val shizukuAuthorized = shizukuRunning && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    val requestMicrophone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshToken++ }
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshToken++ }

    // Read the token so returning from system settings invalidates this composition.
    @Suppress("UNUSED_VARIABLE") val refresh = refreshToken

    val items = listOf(
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
            if (Build.VERSION.SDK_INT >= 33) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else openAppNotificationSettings(context)
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
                shizukuAuthorized -> "Running · Friday authorized"
                shizukuRunning -> "Running · Friday not authorized"
                else -> "Not running"
            },
            when {
                shizukuAuthorized -> "Verified"
                shizukuRunning -> "Authorize Friday in Shizuku"
                else -> "Open Shizuku"
            }
        ) { openShizuku(context) }
    )

    val verifiedCount = items.count { it.status == "Enabled" || it.status == "Unrestricted by battery optimization" || it.status == "Running · Friday authorized" }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Let's configure Friday", style = MaterialTheme.typography.headlineMedium)
        Text(
            "I'll open each relevant system screen for you. When you return, I'll check what actually changed. Optional capabilities can be skipped.",
            style = MaterialTheme.typography.bodyMedium
        )
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
