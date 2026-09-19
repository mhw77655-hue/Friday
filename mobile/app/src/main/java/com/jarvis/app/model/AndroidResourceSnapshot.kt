package com.jarvis.app.model

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.io.File

/**
 * Real Android-backed [ResourceSnapshot] — production implementation.
 *
 * Reads live device values:
 *  - RAM: [ActivityManager.getMemoryInfo] available bytes
 *  - CPU load: two /proc/stat samples 200ms apart (busy-delta / total-delta)
 *  - Thermal: [PowerManager.getCurrentThermalStatus] mapped to 0/1/2
 *  - Battery level + charging: sticky [Intent.ACTION_BATTERY_CHANGED] broadcast
 *
 * Only constructible on-device; the unit-test JVM cannot build it (no Android
 * runtime). It is proven by the separate on-device instrumented fixture
 * (AndroidResourceSnapshotInstrumentedFixture), NOT by the deterministic unit
 * tests, which use an injectable fake snapshot.
 */
class AndroidResourceSnapshot(private val context: Context) : ResourceSnapshot {

    override val availableMemoryMb: Long
        get() {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            return info.availMem / (1024 * 1024)
        }

    override val cpuLoadPercent: Double
        get() = readCpuLoad()

    override val thermalLevel: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                when {
                    pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL -> 2
                    pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> 1
                    else -> 0
                }
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }

    override val batteryPercent: Int
        get() {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            return if (level >= 0 && scale > 0) (level * 100) / scale else -1
        }

    override val isCharging: Boolean
        get() {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }

    private fun readCpuLoad(): Double {
        val a = readProcStat() ?: return 0.0
        Thread.sleep(200)
        val b = readProcStat() ?: return 0.0
        val idleDelta = b.idle - a.idle
        val totalDelta = b.total - a.total
        if (totalDelta <= 0) return 0.0
        return ((totalDelta - idleDelta) * 100.0) / totalDelta
    }

    private data class ProcStat(val idle: Long, val total: Long)

    private fun readProcStat(): ProcStat? = try {
        val tokens = File("/proc/stat").readLines().first().split(Regex("\\s+"))
        val ticks = tokens.drop(1).mapNotNull { it.toLongOrNull() }
        // standard layout: user nice system idle iowait irq softirq steal ...
        val idle = (ticks.getOrNull(3) ?: 0) + (ticks.getOrNull(4) ?: 0)
        ProcStat(idle = idle, total = ticks.sum())
    } catch (e: Exception) {
        null
    }
}