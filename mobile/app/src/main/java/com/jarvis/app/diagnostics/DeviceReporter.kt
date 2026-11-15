package com.jarvis.app.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import com.jarvis.app.BuildConfig
import com.jarvis.app.Telemetry
import com.jarvis.app.failure.FailureEvent
import com.jarvis.app.failure.FailureSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DiagnosticExportResult(
    val fileName: String,
    val byteCount: Long,
    val location: String
)

/**
 * Builds a bounded evidence bundle and records lightweight resource history.
 * No network request is made by this reporter.
 */
object DeviceReporter {
    private const val MIME_ZIP = "application/zip"
    private const val MAX_TELEMETRY_BYTES = 512 * 1024
    private const val MAX_FAILURE_EVENTS = 100
    private const val SAMPLE_INTERVAL_MS = 60_000L
    private const val MB = 1024L * 1024L

    private val samplingLock = Any()
    private var samplingJob: Job? = null

    /**
     * Keep resource samples while Friday is foregrounded. This gives the
     * uploaded report a short time-series for thermal/memory problems instead
     * of only a single reading at export time.
     */
    fun startSampling(context: Context, scope: CoroutineScope) {
        synchronized(samplingLock) {
            if (samplingJob?.isActive == true) return
            val appContext = context.applicationContext
            samplingJob = scope.launch {
                recordResourceSample(appContext)
                while (isActive) {
                    delay(SAMPLE_INTERVAL_MS)
                    recordResourceSample(appContext)
                }
            }
        }
    }

    fun stopSampling() {
        synchronized(samplingLock) {
            samplingJob?.cancel()
            samplingJob = null
        }
    }

    private fun recordResourceSample(context: Context) {
        runCatching {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val batteryLevel = batteryIntent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) level * 100 / scale else -1
            } ?: -1
            val batteryTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { power?.currentThermalStatus }.getOrNull()
            } else null
            val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memory = ActivityManager.MemoryInfo()
            activity?.getMemoryInfo(memory)
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val active = connectivity?.activeNetwork
            val caps = if (active != null) connectivity?.getNetworkCapabilities(active) else null

            Telemetry.logSignal("sample_battery_pct", if (batteryLevel >= 0) batteryLevel.toString() else "unavailable")
            Telemetry.logSignal(
                "sample_battery_temp_c",
                if (batteryTemp >= 0) String.format(Locale.US, "%.1f", batteryTemp / 10.0) else "unavailable"
            )
            Telemetry.logSignal("sample_thermal_status", thermal?.let(::thermalLabel) ?: "unavailable")
            Telemetry.logSignal("sample_device_available_mb", if (memory.availMem > 0) (memory.availMem / MB).toString() else "unavailable")
            Telemetry.logSignal("sample_app_pss_mb", runCatching { (Debug.getPss() / 1024).toString() }.getOrDefault("unavailable"))
            Telemetry.logSignal("sample_network_validated", (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true).toString())
        }
    }

    fun export(
        context: Context,
        healthSignals: Map<String, String>,
        failureSnapshot: FailureSnapshot?,
        persistedFailures: List<FailureEvent> = emptyList()
    ): DiagnosticExportResult {
        val appContext = context.applicationContext
        val generatedAt = System.currentTimeMillis()
        val fileName = "friday-diagnostics-${timestamp(generatedAt)}.zip"
        val bundle = buildBundle(appContext, healthSignals, failureSnapshot, persistedFailures, generatedAt)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloads(appContext, fileName, bundle)
        } else {
            saveToAppDownloads(appContext, fileName, bundle)
        }
    }

    private fun buildBundle(
        context: Context,
        healthSignals: Map<String, String>,
        failureSnapshot: FailureSnapshot?,
        persistedFailures: List<FailureEvent>,
        generatedAt: Long
    ): ByteArray {
        val entries = linkedMapOf(
            "diagnostics.json" to buildDeviceReport(context, healthSignals, generatedAt).toString(2),
            "failures.json" to buildFailureReport(failureSnapshot, persistedFailures).toString(2),
            "telemetry.jsonl" to Telemetry.exportForDiagnostics(MAX_TELEMETRY_BYTES),
            "README.txt" to readme(generatedAt)
        )

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun buildDeviceReport(
        context: Context,
        healthSignals: Map<String, String>,
        generatedAt: Long
    ): JSONObject {
        val packageInfo = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo()
        runCatching { activityManager?.getMemoryInfo(memory) }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIntent = runCatching {
            @Suppress("DEPRECATION")
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val batteryLevel = batteryIntent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) level * 100 / scale else -1
        } ?: -1
        val batteryTempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val batteryStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            batteryStatus == BatteryManager.BATTERY_STATUS_FULL

        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = runCatching { connectivity?.activeNetwork }.getOrNull()
        val capabilities = runCatching {
            if (activeNetwork != null) connectivity?.getNetworkCapabilities(activeNetwork) else null
        }.getOrNull()

        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { powerManager?.currentThermalStatus }.getOrNull()
        } else null

        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val audioDevices = audioDevices(context)
        val cameras = cameras(context)

        val permissions = JSONObject().apply {
            put("record_audio", hasPermission(context, Manifest.permission.RECORD_AUDIO))
            put("camera", hasPermission(context, Manifest.permission.CAMERA))
            put("notification_listener_enabled", notificationListenerEnabled(context))
            put(
                "all_files_access",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
                } else {
                    JSONObject.NULL
                }
            )
        }

        val network = JSONObject().apply {
            put("available", capabilities != null)
            put("validated_internet", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false)
            put("wifi", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false)
            put("cellular", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false)
            put("ethernet", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ?: false)
            put("vpn", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false)
        }

        val memoryJson = JSONObject().apply {
            put("device_total_mb", if (memory.totalMem > 0) memory.totalMem / MB else JSONObject.NULL)
            put("device_available_mb", if (memory.availMem > 0) memory.availMem / MB else JSONObject.NULL)
            put("device_low_memory", memory.lowMemory)
            put("app_pss_mb", runCatching { Debug.getPss() / 1024 }.getOrNull() ?: JSONObject.NULL)
            put("native_heap_allocated_mb", Debug.getNativeHeapAllocatedSize() / MB)
            put("native_heap_free_mb", Debug.getNativeHeapFreeSize() / MB)
        }

        val battery = JSONObject().apply {
            put("level_percent", if (batteryLevel >= 0) batteryLevel else JSONObject.NULL)
            put("charging", charging)
            put("temperature_c", if (batteryTempTenths >= 0) batteryTempTenths / 10.0 else JSONObject.NULL)
            put("power_save_mode", powerManager?.isPowerSaveMode ?: JSONObject.NULL)
            put("thermal_status", thermalStatus?.let(::thermalLabel) ?: "unavailable")
        }

        val storage = JSONObject().apply {
            put("app_data_available_mb", stat?.availableBytes?.div(MB) ?: JSONObject.NULL)
            put("app_data_total_mb", stat?.totalBytes?.div(MB) ?: JSONObject.NULL)
        }

        val device = JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("brand", Build.BRAND)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("product", Build.PRODUCT)
            put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
        }

        val os = JSONObject().apply {
            put("release", Build.VERSION.RELEASE)
            put("sdk_int", Build.VERSION.SDK_INT)
            put("security_patch", Build.VERSION.SECURITY_PATCH)
        }

        val app = JSONObject().apply {
            put("package", context.packageName)
            put("version_name", packageInfo?.versionName ?: BuildConfig.VERSION_NAME)
            put("version_code", packageInfo?.versionCode ?: BuildConfig.VERSION_CODE)
            put("build_type", BuildConfig.BUILD_TYPE)
            put("target_sdk", context.applicationInfo.targetSdkVersion)
        }

        val signals = JSONObject().apply {
            healthSignals.toSortedMap().forEach { (key, value) ->
                val safeKey = DiagnosticRedactor.safeSignalName(key) ?: return@forEach
                put(safeKey, DiagnosticRedactor.redact(value) ?: "")
            }
        }

        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(generatedAt))

        return JSONObject().apply {
            put("schema", "friday-device-diagnostics/1")
            put("generated_at_epoch_ms", generatedAt)
            put("generated_at_utc", utc)
            put("uptime_ms", SystemClock.uptimeMillis())
            put("process_id", android.os.Process.myPid())
            put("app", app)
            put("device", device)
            put("os", os)
            put("battery_and_thermal", battery)
            put("memory", memoryJson)
            put("app_storage", storage)
            put("network", network)
            put("permissions", permissions)
            put("audio_devices", audioDevices)
            put("camera_hardware", cameras)
            put("subsystem_signals", signals)
        }
    }

    private fun buildFailureReport(
        snapshot: FailureSnapshot?,
        persistedFailures: List<FailureEvent>
    ): JSONObject {
        if (snapshot == null) {
            return JSONObject().put("available", false)
                .put("reason", "Failure surface not ready")
                .put("persisted_failure_history", failureEventsJson(persistedFailures))
        }

        val current = JSONArray()
        snapshot.currentFailures.take(MAX_FAILURE_EVENTS).forEach { failure ->
            current.put(JSONObject().apply {
                put("subsystem", failure.subsystem)
                put("operation", failure.operation)
                put("severity", failure.currentSeverity.name)
                put("category", failure.category.name)
                put("status", failure.status.name)
                put("occurrences", failure.occurrenceCount)
                put("first_seen", failure.firstSeen)
                put("latest_seen", failure.latestSeen)
                put("dependency", DiagnosticRedactor.redact(failure.dependency) ?: JSONObject.NULL)
                put("correlation_ids", JSONArray(failure.correlationIds.take(12)))
            })
        }

        val recoveries = JSONArray()
        snapshot.recentRecoveryEvents.take(MAX_FAILURE_EVENTS).forEach { event ->
            recoveries.put(JSONObject().apply {
                put("id", event.id)
                put("timestamp", event.timestamp)
                put("subsystem", event.subsystem)
                put("operation", event.operation)
                put("action", event.action.name)
                put("result", event.result.name)
                put("attempt", event.attempt)
                put("message", DiagnosticRedactor.redact(event.message))
                put("correlation_id", event.correlationId ?: JSONObject.NULL)
            })
        }

        val subsystems = JSONArray()
        snapshot.subsystemHealth.toSortedMap().forEach { (name, health) ->
            subsystems.put(JSONObject().apply {
                put("subsystem", name)
                put("status", health.status.name)
                put("active_failures", health.activeFailures)
                put("total_failures", health.totalFailures)
                put("total_recoveries", health.totalRecoveries)
                put("last_failure_at", health.lastFailureAt ?: JSONObject.NULL)
                put("last_recovery_at", health.lastRecoveryAt ?: JSONObject.NULL)
                put("last_failure_severity", health.lastFailureSeverity?.name ?: JSONObject.NULL)
            })
        }

        val overall = snapshot.systemHealth
        return JSONObject().apply {
            put("available", true)
            put("snapshot_timestamp", snapshot.timestamp)
            put("system_health", JSONObject().apply {
                put("status", overall.status.name)
                put("worst_severity", overall.worstSeverity.name)
                put("active_failure_count", overall.activeFailureCount)
                put("degraded_capability_count", overall.degradedCapabilityCount)
                put("subsystem_count", overall.subsystemCount)
                put("healthy_subsystems", overall.healthySubsystemCount)
                put("degraded_subsystems", overall.degradedSubsystemCount)
                put("down_subsystems", overall.downSubsystemCount)
            })
            put("current_failures", current)
            put("recent_failures", failureEventsJson(snapshot.recentFailures))
            put("persisted_failure_history", failureEventsJson(persistedFailures))
            put("recent_recoveries", recoveries)
            put("subsystem_health", subsystems)
            put("degraded_capabilities", JSONArray(snapshot.degradedCapabilities.sorted()))
            put("total_recent_failures_in_memory", snapshot.recentFailures.size)
        }
    }

    private fun failureEventsJson(events: List<FailureEvent>): JSONArray = JSONArray().apply {
        events.take(MAX_FAILURE_EVENTS).forEach { event ->
            put(JSONObject().apply {
                put("id", event.id)
                put("timestamp", event.timestamp)
                put("source", DiagnosticRedactor.redact(event.source))
                put("subsystem", event.subsystem)
                put("operation", event.operation)
                put("severity", event.severity.name)
                put("category", event.category.name)
                put("message", DiagnosticRedactor.redact(event.message))
                put("cause", DiagnosticRedactor.redact(event.cause) ?: JSONObject.NULL)
                put("state", DiagnosticRedactor.redact(event.state) ?: JSONObject.NULL)
                put("dependency", DiagnosticRedactor.redact(event.dependency) ?: JSONObject.NULL)
                put("failure_cause", event.failureCause?.name ?: JSONObject.NULL)
                put("recoverability", event.recoverability.name)
                put("correlation_id", event.correlationId ?: JSONObject.NULL)
                put("attempt", event.attempt)
                put("recovery_action", event.recoveryAction.name)
                put("related_capability", event.relatedCapability ?: JSONObject.NULL)
                put("environment", DiagnosticRedactor.redact(event.environment) ?: JSONObject.NULL)
                val safeMetadata = JSONObject()
                event.metadata.filterKeys(DiagnosticRedactor::safeMetadataKey).forEach { (key, value) ->
                    safeMetadata.put(key, DiagnosticRedactor.redact(value))
                }
                put("metadata", safeMetadata)
            })
        }
    }

    private fun audioDevices(context: Context): JSONArray {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return JSONArray()
        val devices = runCatching { manager.getDevices(AudioManager.GET_DEVICES_ALL).toList() }
            .getOrDefault(emptyList())
        return JSONArray().apply {
            devices.forEach { device ->
                put(JSONObject().apply {
                    put("direction", if (device.isSource) "input" else "output")
                    put("type_code", device.type)
                })
            }
        }
    }

    private fun cameras(context: Context): JSONArray {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return JSONArray()
        val cameraIds = runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList())
        return JSONArray().apply {
            cameraIds.forEach { id ->
                val info = runCatching { manager.getCameraCharacteristics(id) }.getOrNull()
                val facing = info?.get(CameraCharacteristics.LENS_FACING)
                val hardware = info?.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                put(JSONObject().apply {
                    put("facing", when (facing) {
                        CameraCharacteristics.LENS_FACING_FRONT -> "front"
                        CameraCharacteristics.LENS_FACING_BACK -> "back"
                        CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
                        else -> "unknown"
                    })
                    put("hardware_level", hardware?.let(::cameraHardwareLabel) ?: "unknown")
                })
            }
        }
    }

    private fun notificationListenerEnabled(context: Context): Boolean {
        val enabled = runCatching {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        }.getOrNull().orEmpty()
        return enabled.split(':').any { component -> component.startsWith(context.packageName + "/") }
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        runCatching { context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED }
            .getOrDefault(false)

    private fun thermalLabel(status: Int): String = when (status) {
        0 -> "none"
        1 -> "light"
        2 -> "moderate"
        3 -> "severe"
        4 -> "critical"
        5 -> "emergency"
        6 -> "shutdown"
        else -> "unknown($status)"
    }

    private fun cameraHardwareLabel(level: Int): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "legacy"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "limited"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "full"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "level_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "external"
        else -> "unknown($level)"
    }

    private fun saveToDownloads(
        context: Context,
        fileName: String,
        bytes: ByteArray
    ): DiagnosticExportResult {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_ZIP)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Friday")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Android could not create a Downloads item")

        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } ?: throw IOException("Android did not provide an output stream")
            val ready = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, ready, null, null)
            return DiagnosticExportResult(
                fileName = fileName,
                byteCount = bytes.size.toLong(),
                location = "Download/Friday"
            )
        } catch (error: Throwable) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun saveToAppDownloads(
        context: Context,
        fileName: String,
        bytes: ByteArray
    ): DiagnosticExportResult {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val folder = File(base, "Friday").apply {
            if (!exists() && !mkdirs()) throw IOException("Could not create the app Downloads folder")
        }
        val output = File(folder, fileName)
        output.writeBytes(bytes)
        return DiagnosticExportResult(
            fileName = fileName,
            byteCount = bytes.size.toLong(),
            location = "Friday app-specific Downloads/Friday"
        )
    }

    private fun readme(generatedAt: Long): String = """
        FRIDAY DEVICE DIAGNOSTIC BUNDLE
        Generated at epoch milliseconds: $generatedAt
        Resource samples: approximately once per minute while Friday is foregrounded.

        FILES
        - diagnostics.json: Android device, OS, app, resource, connectivity,
          permission, audio-device, camera-hardware, and subsystem status snapshot.
        - failures.json: bounded current, recent, and persisted failure/recovery evidence.
        - telemetry.jsonl: bounded structured runtime events and resource samples.

        PRIVACY
        This bundle does not include conversation transcripts, recognized speech
        text, raw microphone recordings, screenshots, screen contents, notification
        bodies, contacts, account identifiers, or unrestricted Android logcat.
        Common credential patterns, email addresses, and phone numbers are redacted
        from diagnostic text. Review the archive before sharing it.

        LIMITATIONS
        Hardware inventory is not proof that a microphone, speaker, or camera
        performs correctly. Resource and thermal values are samples, not a
        continuous hardware trace. A successful export proves only that the
        report was written.
    """.trimIndent() + "\n"

    private fun timestamp(time: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(time))
}
