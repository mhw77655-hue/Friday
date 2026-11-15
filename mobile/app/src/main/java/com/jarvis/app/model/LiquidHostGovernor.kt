package com.jarvis.app.model

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import com.jarvis.app.model.storage.ModelPathResolver
import java.io.File

/** Android-cooperative NAE host adapter; no root or OS modification. */
class LiquidHostGovernor(private val context: Context) {
    data class Snapshot(
        val totalRamMb: Long, val availableRamMb: Long, val freeStorageMb: Long,
        val batteryPercent: Int, val charging: Boolean, val thermalStatus: Int, val cpuCores: Int
    )
    data class Admission(val allowed: Boolean, val reason: String, val snapshot: Snapshot)

    fun snapshot(): Snapshot {
        val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also { activity.getMemoryInfo(it) }
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val storage = StatFs(context.filesDir.absolutePath)
        val thermal = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else PowerManager.THERMAL_STATUS_NONE
        return Snapshot(
            totalRamMb = memory.totalMem / MB,
            availableRamMb = memory.availMem / MB,
            freeStorageMb = storage.availableBytes / MB,
            batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100),
            charging = battery.isCharging,
            thermalStatus = thermal,
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        )
    }

    fun admit(modelId: String, modelPath: String? = null): Admission {
        val host = snapshot()
        val fileName = ModelPathResolver.modelFileName(modelId)
        val model = modelPath?.takeIf { it.isNotBlank() }?.let { File(it) } ?: File(context.filesDir, "models/$fileName")
        val modelMb = if (model.isFile) model.length() / MB else 0L
        val requiredMb = (modelMb * 1.35).toLong() + 500L
        val deep = modelId.contains("qwen", true) || modelId.contains("reason", true) || modelId.contains("dolphin", true)
        val thermalBlocked = host.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE && deep
        val batteryBlocked = host.batteryPercent in 1..8 && !host.charging && deep
        val allowed = host.availableRamMb >= requiredMb && !thermalBlocked && !batteryBlocked
        val reason = when {
            thermalBlocked -> "deep model deferred: phone thermal state is severe"
            batteryBlocked -> "deep model deferred: battery is critically low"
            host.availableRamMb < requiredMb -> "not enough available RAM: need about $requiredMb MB, have ${host.availableRamMb} MB"
            else -> "admitted"
        }
        return Admission(allowed, reason, host)
    }

    fun inferenceParameters(modelId: String): Map<String, String> {
        val host = snapshot()
        val deep = modelId.contains("qwen", true) || modelId.contains("reason", true)
        val lowPower = (!host.charging && host.batteryPercent < 20) || host.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        val contextWindow = when {
            host.availableRamMb < 1_800L -> 1024
            host.availableRamMb < 2_800L -> 2048
            host.availableRamMb < 4_800L -> 4096
            else -> if (deep) 8192 else 4096
        }
        val threads = (host.cpuCores - 1).coerceIn(2, 6).let { if (lowPower) minOf(it, 2) else it }
        return mapOf("context_window" to contextWindow.toString(), "threads" to threads.toString(), "gpu_layers" to "0")
    }

    companion object { private const val MB = 1024L * 1024L }
}
