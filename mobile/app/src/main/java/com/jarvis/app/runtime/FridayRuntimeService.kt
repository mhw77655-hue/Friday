package com.jarvis.app.runtime

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jarvis.app.JarvisMic
import com.jarvis.app.MainActivity
import com.jarvis.app.ScreenBridge
import com.jarvis.app.ShizukuBridge
import com.jarvis.app.companioncore.presence.CompanionCoreHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * User-visible host for Friday's model-independent runtime.
 *
 * This is a persistence aid, not an Android-kill-switch bypass. START_STICKY
 * asks Android to recreate the service when possible; the boot receiver can
 * restore the non-microphone core when the owner enabled it. Force-stop,
 * OEM task killers, permission revocation, thermal shutdown and OS policy
 * remain authoritative.
 *
 * Microphone capture is promoted only after an explicit start from a visible
 * activity with RECORD_AUDIO granted. A sticky restart or boot restore never
 * silently reopens the microphone.
 */
class FridayRuntimeService : Service() {

    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var microphoneOwned = false

    override fun onCreate() {
        super.onCreate()
        FridayRuntimeController.setMicrophoneServiceActive(this, false)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopOwnedMicrophone()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        if (!FridayRuntimeController.isEnabled(this)) {
            stopOwnedMicrophone()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val wantsMicrophone = intent?.action == ACTION_START_WITH_MICROPHONE &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!wantsMicrophone) {
            // A core-only start (boot, sticky restart or revoked microphone
            // permission) must never leave an earlier capture loop running.
            stopOwnedMicrophone()
        }

        try {
            var type = 0
            // Microphone foreground-service typing has existed since Android
            // 11 (API 30); specialUse was added in Android 14 (API 34).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wantsMicrophone) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(wantsMicrophone),
                type
            )
        } catch (failure: RuntimeException) {
            Log.e(TAG, "Could not promote Friday runtime to foreground service", failure)
            FridayRuntimeController.recordStartFailure(this, failure.message ?: failure.javaClass.simpleName)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        runtimeScope.launch {
            runCatching {
                val core = CompanionCoreHolder.init(applicationContext)
                val appVisible = ProcessLifecycleOwner.get().lifecycle.currentState
                    .isAtLeast(Lifecycle.State.STARTED)
                if (appVisible) core.onForeground() else core.onBackground()
            }.onFailure { Log.e(TAG, "Companion Core restore failed", it) }

            // Reconnect the existing capability owner. Friday remains useful if
            // Shizuku is unavailable; no second device-control host is created.
            runCatching { ShizukuBridge.init() }
            if (ShizukuBridge.isGranted.value) {
                runCatching { ScreenBridge.bind(applicationContext) }
                    .onFailure { Log.w(TAG, "Shizuku reconnect deferred", it) }
            }

            if (wantsMicrophone) {
                // Multiple start intents can arrive during Activity onCreate /
                // onResume. Serialize the handoff so two coroutines cannot
                // race JarvisMic.start() and create duplicate capture loops.
                synchronized(this@FridayRuntimeService) {
                    if (!microphoneOwned) {
                        // If the activity started capture just before enabling
                        // this service, migrate it to the service-owned scope.
                        if (JarvisMic.isRecording.value) JarvisMic.stop()
                        JarvisMic.start(runtimeScope)
                        microphoneOwned = JarvisMic.isRecording.value
                        FridayRuntimeController.setMicrophoneServiceActive(
                            this@FridayRuntimeService,
                            microphoneOwned
                        )
                    }
                }
            }
        }

        // Android may recreate a killed service with a null Intent. In that
        // case we restore only the core; microphone access needs a fresh,
        // visible user-initiated start on Android 14+.
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Deliberately do not stop on task removal. The foreground service is
        // independent of the launcher task while the owner has enabled it.
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopOwnedMicrophone()
        runtimeScope.cancel()
        super.onDestroy()
    }

    private fun stopOwnedMicrophone() {
        if (microphoneOwned || JarvisMic.isRecording.value) {
            JarvisMic.stop()
        }
        microphoneOwned = false
        FridayRuntimeController.setMicrophoneServiceActive(this, false)
    }

    private fun buildNotification(microphone: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FridayRuntimeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Friday is active")
            .setContentText(
                if (microphone) "Persistent runtime active · microphone in use"
                else "Persistent runtime active · voice resumes when Friday is opened"
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop Friday", stop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Friday runtime",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows when Friday's persistent runtime is enabled."
                    setShowBadge(false)
                }
            )
        }
    }

    companion object {
        private const val TAG = "FridayRuntime"
        private const val CHANNEL_ID = "friday_runtime"
        private const val NOTIFICATION_ID = 7401

        const val ACTION_START_CORE = "com.jarvis.app.runtime.START_CORE"
        const val ACTION_START_WITH_MICROPHONE = "com.jarvis.app.runtime.START_WITH_MICROPHONE"
        const val ACTION_STOP = "com.jarvis.app.runtime.STOP"
    }
}

/** Owner-controlled lifecycle and battery-policy entry points. */
object FridayRuntimeController {
    private const val PREFS = "friday_runtime"
    private const val KEY_ENABLED = "keep_alive_enabled"
    private const val KEY_START_FAILURE = "last_start_failure"
    private const val KEY_MICROPHONE_ACTIVE = "microphone_service_active"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    /** True only while Android reports this app's foreground service running. */
    @Suppress("DEPRECATION")
    fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == FridayRuntimeService::class.java.name
        }
    }

    fun enable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, true).remove(KEY_START_FAILURE).apply()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // This method is called by the visible Settings screen after the
            // owner explicitly enables persistence; preserve the app's current
            // voice behavior under the foreground service.
            startWithMicrophone(context)
        } else {
            startCore(context)
        }
    }

    fun startCore(context: Context) {
        val intent = Intent(context, FridayRuntimeService::class.java)
            .setAction(FridayRuntimeService.ACTION_START_CORE)
        ContextCompat.startForegroundService(context, intent)
    }

    /** Call only from a visible activity after RECORD_AUDIO is granted. */
    fun startWithMicrophone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, true).remove(KEY_START_FAILURE).apply()
        val intent = Intent(context, FridayRuntimeService::class.java)
            .setAction(FridayRuntimeService.ACTION_START_WITH_MICROPHONE)
        ContextCompat.startForegroundService(context, intent)
    }

    fun disable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, false).apply()
        context.startService(
            Intent(context, FridayRuntimeService::class.java)
                .setAction(FridayRuntimeService.ACTION_STOP)
        )
    }

    fun isMicrophoneServiceActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MICROPHONE_ACTIVE, false)

    internal fun setMicrophoneServiceActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MICROPHONE_ACTIVE, active).apply()
    }

    fun lastStartFailure(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_START_FAILURE, "").orEmpty()

    internal fun recordStartFailure(context: Context, reason: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_START_FAILURE, reason.take(300)).apply()
    }

    fun batteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val power = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** Restores only the non-microphone core after reboot or package replacement. */
class FridayRuntimeBootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (!FridayRuntimeController.isEnabled(context)) return

        try {
            FridayRuntimeController.startCore(context)
        } catch (failure: RuntimeException) {
            FridayRuntimeController.recordStartFailure(
                context,
                "Automatic resume blocked: ${failure.message ?: failure.javaClass.simpleName}. Open Friday to resume."
            )
            Log.w("FridayRuntime", "Automatic boot resume was blocked by Android", failure)
        }
    }
}
