package com.jarvis.app.env

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * A privacy-minimised, point-in-time description of the Android host.
 *
 * This is evidence about the host, not permission to act on it. "Supported"
 * means the platform advertises a feature; "granted" means the app currently
 * holds a runtime permission; neither proves that an operation succeeds.
 */
data class AndroidHostSnapshot(
    val schemaVersion: Int,
    val capturedAtEpochMs: Long,
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val apiLevel: Int,
    val securityPatch: String?,
    val supportedAbis: List<String>,
    val appVersionName: String,
    val appVersionCode: Long,
    val sourceRevision: String,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val appStorageTotalBytes: Long,
    val appStorageAvailableBytes: Long,
    val batteryPercent: Int?,
    val powerSaveMode: Boolean,
    val thermalStatus: Int?,
    val networkTransport: String,
    val features: Map<String, Boolean>,
    val permissions: Map<String, Boolean>,
    val accessibilityServiceEnabled: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val shizukuBinderReachable: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema_version", schemaVersion)
        put("captured_at_epoch_ms", capturedAtEpochMs)
        put("device", JSONObject()
            .put("manufacturer", manufacturer)
            .put("model", model)
            .put("device", device)
            .put("android_release", androidRelease)
            .put("api_level", apiLevel)
            .put("security_patch", securityPatch ?: JSONObject.NULL)
            .put("supported_abis", JSONArray(supportedAbis)))
        put("app", JSONObject()
            .put("version_name", appVersionName)
            .put("version_code", appVersionCode)
            .put("source_revision", sourceRevision))
        put("resources", JSONObject()
            .put("total_ram_bytes", totalRamBytes)
            .put("available_ram_bytes", availableRamBytes)
            .put("app_storage_total_bytes", appStorageTotalBytes)
            .put("app_storage_available_bytes", appStorageAvailableBytes)
            .put("battery_percent", batteryPercent ?: JSONObject.NULL)
            .put("power_save_mode", powerSaveMode)
            .put("thermal_status", thermalStatus ?: JSONObject.NULL))
        put("network_transport", networkTransport)
        put("features", JSONObject(features))
        put("permissions", JSONObject(permissions))
        put("integration_routes", JSONObject()
            .put("friday_accessibility_service_enabled", accessibilityServiceEnabled)
            .put("battery_optimization_ignored", batteryOptimizationIgnored)
            .put("shizuku_binder_reachable", shizukuBinderReachable))
        put("privacy", JSONObject()
            .put("includes_location", false)
            .put("includes_device_identifiers", false)
            .put("includes_accounts_or_contacts", false)
            .put("includes_notifications_or_screen_content", false)
            .put("includes_conversation_or_transcripts", false)
            .put("includes_credentials", false))
    }

    companion object {
        const val SCHEMA_VERSION = 1

        @Suppress("DEPRECATION")
        private fun packageInfoVersionCode(info: android.content.pm.PackageInfo): Long =
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

        fun capture(context: Context, sourceRevision: String): AndroidHostSnapshot {
            val app = context.applicationContext
            val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
            val stat = StatFs(app.filesDir.absolutePath)
            val battery = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryRaw = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
            val versionCode = packageInfoVersionCode(packageInfo)
            val accessibilityServices = runCatching {
                Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    .orEmpty().split(':').any { it.substringBefore('/').equals(app.packageName, ignoreCase = true) }
            }.getOrDefault(false)
            val batteryIgnored = if (Build.VERSION.SDK_INT >= 23) power.isIgnoringBatteryOptimizations(app.packageName) else true
            val thermal = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else null
            val network = when {
                caps == null -> "offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
                else -> "other"
            }
            fun has(feature: String) = app.packageManager.hasSystemFeature(feature)
            fun granted(permission: String) =
                ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

            return AndroidHostSnapshot(
                schemaVersion = SCHEMA_VERSION,
                capturedAtEpochMs = System.currentTimeMillis(),
                manufacturer = Build.MANUFACTURER.take(48),
                model = Build.MODEL.take(64),
                device = Build.DEVICE.take(64),
                androidRelease = Build.VERSION.RELEASE.orEmpty().take(24),
                apiLevel = Build.VERSION.SDK_INT,
                securityPatch = if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else null,
                supportedAbis = Build.SUPPORTED_ABIS.take(8),
                appVersionName = packageInfo.versionName.orEmpty(),
                appVersionCode = versionCode,
                sourceRevision = sourceRevision.take(40),
                totalRamBytes = memory.totalMem,
                availableRamBytes = memory.availMem,
                appStorageTotalBytes = stat.totalBytes,
                appStorageAvailableBytes = stat.availableBytes,
                batteryPercent = batteryRaw.takeIf { it in 0..100 },
                powerSaveMode = power.isPowerSaveMode,
                thermalStatus = thermal,
                networkTransport = network,
                features = mapOf(
                    "microphone_hardware" to has(PackageManager.FEATURE_MICROPHONE),
                    "camera_any" to has(PackageManager.FEATURE_CAMERA_ANY),
                    "camera_front" to has(PackageManager.FEATURE_CAMERA_FRONT),
                    "camera_flash" to has(PackageManager.FEATURE_CAMERA_FLASH),
                    "bluetooth" to has(PackageManager.FEATURE_BLUETOOTH),
                    "bluetooth_le" to has(PackageManager.FEATURE_BLUETOOTH_LE),
                    "wifi" to has(PackageManager.FEATURE_WIFI),
                    "telephony" to has(PackageManager.FEATURE_TELEPHONY),
                    "touchscreen" to has(PackageManager.FEATURE_TOUCHSCREEN)
                ),
                permissions = mapOf(
                    "record_audio" to granted(Manifest.permission.RECORD_AUDIO),
                    "camera" to granted(Manifest.permission.CAMERA),
                    "post_notifications" to (Build.VERSION.SDK_INT < 33 || granted("android.permission.POST_NOTIFICATIONS"))
                ),
                accessibilityServiceEnabled = accessibilityServices,
                batteryOptimizationIgnored = batteryIgnored,
                shizukuBinderReachable = runCatching { rikka.shizuku.Shizuku.pingBinder() }.getOrDefault(false)
            )
        }
    }
}
