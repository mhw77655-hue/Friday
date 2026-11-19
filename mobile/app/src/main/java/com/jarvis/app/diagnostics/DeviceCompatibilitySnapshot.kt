package com.jarvis.app.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Device compatibility evidence for adapting Friday to the actual handset.
 *
 * The full snapshot stays in Friday's app-private storage. The cloud projection
 * contains only technical compatibility facts; it deliberately excludes
 * hardware serials, Android IDs, accounts, location, installed-app inventories,
 * contacts, notifications, screen contents, conversations and credentials.
 */
object DeviceCompatibilitySnapshot {
    private const val DIRECTORY = "device-compatibility"
    private const val FILE_NAME = "latest.json"

    fun capture(context: Context): JSONObject {
        val app = context.applicationContext
        val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val storage = StatFs(app.filesDir.absolutePath)
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryManager = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        val batteryIntent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (batteryLevel >= 0 && batteryScale > 0) batteryLevel * 100 / batteryScale else -1
        val charging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) in setOf(
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL
        )
        val packageInfo = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0)
        }.getOrNull()
        val appInfo = app.applicationInfo

        return JSONObject()
            .put("schema", "friday-device-compatibility-v1")
            .put("captured_at_ms", System.currentTimeMillis())
            .put("app", JSONObject()
                .put("package", app.packageName)
                .put("version_name", packageInfo?.versionName ?: "unknown")
                .put("version_code", if (Build.VERSION.SDK_INT >= 28) packageInfo?.longVersionCode ?: 0L else @Suppress("DEPRECATION") (packageInfo?.versionCode ?: 0).toLong())
                .put("debuggable", appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0))
            .put("device", JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("brand", Build.BRAND)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("product", Build.PRODUCT)
                .put("hardware", Build.HARDWARE)
                .put("board", Build.BOARD)
                .put("fingerprint", Build.FINGERPRINT)
                .put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                .put("cpu_cores", Runtime.getRuntime().availableProcessors())
                .put("android_release", Build.VERSION.RELEASE)
                .put("sdk_int", Build.VERSION.SDK_INT)
                .put("security_patch", if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "unavailable"))
            .put("resources", JSONObject()
                .put("ram_total_bytes", memory.totalMem)
                .put("ram_available_bytes", memory.availMem)
                .put("ram_low", memory.lowMemory)
                .put("low_ram_device", activity.isLowRamDevice)
                .put("app_memory_class_mb", activity.memoryClass)
                .put("large_app_memory_class_mb", activity.largeMemoryClass)
                .put("process_pss_kb", Debug.getPss())
                .put("native_heap_allocated_bytes", Debug.getNativeHeapAllocatedSize())
                .put("storage_total_bytes", storage.totalBytes)
                .put("storage_available_bytes", storage.availableBytes))
            .put("power", JSONObject()
                .put("battery_percent", batteryPercent)
                .put("charging", charging)
                .put("power_save_mode", power.isPowerSaveMode)
                .put("battery_optimization_exempt", power.isIgnoringBatteryOptimizations(app.packageName))
                .put("thermal_status", thermalStatus(power)))
            .put("network", JSONObject()
                .put("internet_capability", network?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
                .put("validated", network?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
                .put("wifi", network?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
                .put("cellular", network?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
                .put("ethernet", network?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true))
            .put("permissions", JSONObject()
                .put("record_audio", granted(app, android.Manifest.permission.RECORD_AUDIO))
                .put("post_notifications", Build.VERSION.SDK_INT < 33 || granted(app, android.Manifest.permission.POST_NOTIFICATIONS))
                .put("accessibility_enabled", runCatching {
                    Settings.Secure.getInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
                }.getOrDefault(false)))
            .put("shizuku", JSONObject()
                .put("api_dependency_present", runCatching {
                    Class.forName("rikka.shizuku.Shizuku")
                    true
                }.getOrDefault(false)))
    }

    /** Persist complete technical evidence locally using AtomicFile. */
    fun persist(context: Context, snapshot: JSONObject): File {
        val directory = File(context.applicationContext.filesDir, DIRECTORY).apply { mkdirs() }
        val file = File(directory, FILE_NAME)
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(snapshot.toString(2).toByteArray(Charsets.UTF_8))
            stream.flush()
            atomic.finishWrite(stream)
        } catch (failure: Throwable) {
            atomic.failWrite(stream)
            throw failure
        }
        return file
    }

    /**
     * Explicit cloud projection. It contains compatibility facts, not personal
     * activity. The caller must still require the owner's cloud-diagnostics opt-in.
     */
    fun cloudProjection(snapshot: JSONObject): JSONObject {
        val device = snapshot.optJSONObject("device") ?: JSONObject()
        val resources = snapshot.optJSONObject("resources") ?: JSONObject()
        val power = snapshot.optJSONObject("power") ?: JSONObject()
        val network = snapshot.optJSONObject("network") ?: JSONObject()
        val permissions = snapshot.optJSONObject("permissions") ?: JSONObject()
        return JSONObject()
            .put("schema", "friday-device-compatibility-cloud-v1")
            .put("app", snapshot.optJSONObject("app") ?: JSONObject())
            .put("device", JSONObject()
                .put("manufacturer", device.optString("manufacturer"))
                .put("brand", device.optString("brand"))
                .put("model", device.optString("model"))
                .put("hardware", device.optString("hardware"))
                .put("supported_abis", device.optJSONArray("supported_abis") ?: JSONArray())
                .put("cpu_cores", device.optInt("cpu_cores"))
                .put("android_release", device.optString("android_release"))
                .put("sdk_int", device.optInt("sdk_int"))
                .put("security_patch", device.optString("security_patch")))
            .put("resources", JSONObject()
                .put("ram_total_bytes", resources.optLong("ram_total_bytes"))
                .put("ram_available_bytes", resources.optLong("ram_available_bytes"))
                .put("ram_low", resources.optBoolean("ram_low"))
                .put("low_ram_device", resources.optBoolean("low_ram_device"))
                .put("app_memory_class_mb", resources.optInt("app_memory_class_mb"))
                .put("process_pss_kb", resources.optLong("process_pss_kb"))
                .put("storage_available_bytes", resources.optLong("storage_available_bytes")))
            .put("power", JSONObject()
                .put("battery_percent", power.optInt("battery_percent", -1))
                .put("charging", power.optBoolean("charging"))
                .put("power_save_mode", power.optBoolean("power_save_mode"))
                .put("battery_optimization_exempt", power.optBoolean("battery_optimization_exempt"))
                .put("thermal_status", power.optString("thermal_status")))
            .put("network", network)
            .put("permissions", permissions)
    }

    private fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun thermalStatus(power: PowerManager): String =
        if (Build.VERSION.SDK_INT >= 29) when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "unknown"
        } else "unsupported"
}
