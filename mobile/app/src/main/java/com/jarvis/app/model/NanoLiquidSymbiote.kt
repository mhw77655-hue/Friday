package com.jarvis.app.model

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

/**
 * First live NAE loop: observe the host, choose a safe inference envelope,
 * measure what happened, and retain per-device experience across restarts.
 *
 * Sampling is event-driven at model load/inference, not a permanent polling
 * service. No root, Shizuku, Termux, Ollama, or broad storage permission needed.
 */
class NanoLiquidSymbiote(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("nano_liquid_symbiote", Context.MODE_PRIVATE)
    private val lock = Any()

    fun observeHost(): NanoHostState {
        val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else 50
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
                when {
                    power.currentThermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL -> 2
                    power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> 1
                    else -> 0
                }
            } catch (_: Exception) {
                0
            }
        } else 0
        val storage = try {
            StatFs(app.filesDir.absolutePath).availableBytes / (1024L * 1024L)
        } catch (_: Exception) {
            0L
        }
        return NanoHostState(
            availableMemoryMb = memory.availMem / (1024L * 1024L),
            totalMemoryMb = memory.totalMem / (1024L * 1024L),
            freeStorageMb = storage,
            thermalLevel = thermal,
            batteryPercent = batteryPercent,
            isCharging = charging,
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            lowMemory = memory.lowMemory,
            appPssMb = Debug.getPss().toLong() / 1024L
        )
    }

    fun recommend(host: NanoHostState = observeHost()): NanoLiquidProfile {
        val throughput = mutableMapOf<Int, Float>()
        val efficiency = mutableMapOf<Int, Float>()
        for (threads in 1..4) {
            val speed = preferences.getFloat("speed_ewma_$threads", 0f)
            val energy = preferences.getFloat("tokens_per_battery_percent_$threads", 0f)
            if (speed > 0f) throughput[threads] = speed
            if (energy > 0f) efficiency[threads] = energy
        }
        return NanoLiquidPolicy.recommend(host, throughput, efficiency)
    }

    /**
     * Uses actual completion-token counts from llama.cpp when available.
     * Battery efficiency accumulates across turns until Android's integer
     * battery percentage drops, avoiding fake precision on short generations.
     */
    fun recordInference(profile: NanoLiquidProfile, outputTokens: Int, elapsedMs: Long) {
        if (outputTokens <= 0 || elapsedMs <= 0) return
        val host = observeHost()
        val estimatedTokens = outputTokens.toFloat()
        val speed = estimatedTokens / (elapsedMs / 1000f)
        val key = profile.threads.coerceIn(1, 4)
        val constrained = host.thermalLevel >= 2 || host.lowMemory
        synchronized(lock) {
            val oldSpeed = preferences.getFloat("speed_ewma_$key", 0f)
            val newSpeed = if (oldSpeed <= 0f) speed else oldSpeed * 0.75f + speed * 0.25f
            val pendingKey = "pending_tokens_$key"
            val baselineKey = "battery_baseline_$key"
            val pending = preferences.getFloat(pendingKey, 0f) + estimatedTokens
            val baseline = preferences.getInt(baselineKey, -1)
            val editor = preferences.edit()
                .putInt("samples_$key", preferences.getInt("samples_$key", 0) + 1)
                .putLong("last_observed_at", System.currentTimeMillis())
                .putLong("last_available_memory_mb", host.availableMemoryMb)
                .putLong("last_app_pss_mb", host.appPssMb)
                .putInt("last_thermal_level", host.thermalLevel)
                .putInt("last_battery_percent", host.batteryPercent)
                .putBoolean("last_run_was_constrained", constrained)

            // Never reward a run performed while critically hot or low on memory.
            if (!constrained) {
                editor.putFloat("speed_ewma_$key", newSpeed)
                if (baseline < 0 || host.batteryPercent > baseline) {
                    // First observation or charging: start a fresh energy window.
                    editor.putInt(baselineKey, host.batteryPercent)
                        .putFloat(pendingKey, 0f)
                } else if (host.batteryPercent < baseline) {
                    val consumedPercent = baseline - host.batteryPercent
                    val observedEfficiency = pending / consumedPercent.toFloat()
                    val oldEfficiency = preferences.getFloat("tokens_per_battery_percent_$key", 0f)
                    val smoothed = if (oldEfficiency <= 0f) observedEfficiency
                        else oldEfficiency * 0.75f + observedEfficiency * 0.25f
                    editor.putFloat("tokens_per_battery_percent_$key", smoothed)
                        .putInt(baselineKey, host.batteryPercent)
                        .putFloat(pendingKey, 0f)
                } else {
                    editor.putFloat(pendingKey, pending)
                }
            }
            editor.apply()
        }
    }

    fun recordModelSelection(modelId: String, reason: String, contextTokens: Int) {
        preferences.edit()
            .putString("last_selected_model_id", modelId)
            .putString("last_model_selection_reason", reason)
            .putInt("last_selected_context_tokens", contextTokens)
            .putLong("last_model_selected_at", System.currentTimeMillis())
            .apply()
    }

    fun learningSummary(): Map<String, Any> = mapOf(
        "samples" to (1..4).sumOf { preferences.getInt("samples_$it", 0) },
        "lastSelectedModel" to (preferences.getString("last_selected_model_id", "not selected") ?: "not selected"),
        "lastModelSelectionReason" to (preferences.getString("last_model_selection_reason", "no model loaded yet") ?: "no model loaded yet"),
        "lastSelectedContextTokens" to preferences.getInt("last_selected_context_tokens", -1),
        "lastAvailableMemoryMb" to preferences.getLong("last_available_memory_mb", -1L),
        "lastAppPssMb" to preferences.getLong("last_app_pss_mb", -1L),
        "lastThermalLevel" to preferences.getInt("last_thermal_level", -1),
        "lastBatteryPercent" to preferences.getInt("last_battery_percent", -1),
        "lastRunConstrained" to preferences.getBoolean("last_run_was_constrained", false)
    )
}
