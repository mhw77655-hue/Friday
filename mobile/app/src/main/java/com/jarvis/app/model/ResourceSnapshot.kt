package com.jarvis.app.model

/**
 * A live snapshot of device resources at wake-admission time.
 *
 * Implementations: [AndroidResourceSnapshot] reads real device values
 * (ActivityManager / BatteryManager / PowerManager thermal / /proc/stat) for
 * production; deterministic unit tests inject a fake snapshot so
 * ResourceGovernor.admit is fully deterministic.
 */
interface ResourceSnapshot {
    val availableMemoryMb: Long
    val cpuLoadPercent: Double
    val thermalLevel: Int // 0 = normal, 1 = throttled, 2 = critical
    val batteryPercent: Int // 0..100
    val isCharging: Boolean

    companion object {
        /** Neutral all-clear fallback used by ResourceGovernor's unwired default. */
        fun alwaysHealthy(): ResourceSnapshot = object : ResourceSnapshot {
            override val availableMemoryMb: Long = Long.MAX_VALUE
            override val cpuLoadPercent: Double = 0.0
            override val thermalLevel: Int = 0
            override val batteryPercent: Int = 100
            override val isCharging: Boolean = true
        }
    }
}