package com.jarvis.app.symbiosis

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * Nano-liquid symbiosis: a bounded, feedback-driven controller that adapts
 * inference and background work to the phone's current condition.
 *
 * This class deliberately does not train or rewrite model weights. It learns
 * operational preferences from measured outcomes and persists them through
 * SymbiosisProfileStore supplied by the app (DataStore/Room implementation).
 * All decisions are reversible and remain inside Android's public APIs.
 */
data class HostCondition(
    val availableRamMb: Long,
    val totalRamMb: Long,
    val availableStorageMb: Long,
    val batteryPercent: Int,
    val charging: Boolean,
    val powerSave: Boolean,
    val thermalStatus: Int,
    val appInForeground: Boolean,
    val networkAvailable: Boolean
)

enum class CognitiveWorkload { CHAT, REASONING, CODING, PERCEPTION, MAINTENANCE }

data class ModelCandidate(
    val id: String,
    val weightSizeMb: Int,
    val minimumFreeRamMb: Int,
    val qualityTier: Int,
    val supportsToolUse: Boolean = false
)

data class InferenceEnvelope(
    val modelId: String?,
    val contextTokens: Int,
    val maxNewTokens: Int,
    val cpuThreads: Int,
    val keepModelResident: Boolean,
    val allowVoice: Boolean,
    val allowPerception: Boolean,
    val allowBackgroundLearning: Boolean,
    val deferWork: Boolean,
    val explanation: String
)

data class ExperienceSample(
    val latencyMs: Long,
    val generatedTokens: Int,
    val successful: Boolean,
    val userCorrection: Boolean = false,
    val taskAbandoned: Boolean = false
)

data class SymbiosisProfile(
    val sampleCount: Long = 0,
    val latencyEmaMs: Double = 0.0,
    val successEma: Double = 0.75,
    val correctionEma: Double = 0.0,
    val preferredThreadCount: Int = 2,
    val preferredContextTokens: Int = 4096
)

interface SymbiosisProfileStore {
    fun load(): SymbiosisProfile
    fun save(profile: SymbiosisProfile)
}

/**
 * The policy is intentionally conservative: thermal pressure, low memory,
 * low battery and foreground activity outrank speed. Experience feedback
 * tunes only within hard safety/resource bounds.
 */
class LiquidSymbiosisController(
    private val store: SymbiosisProfileStore,
    private val candidates: () -> List<ModelCandidate>,
    private val availableProcessors: Int = Runtime.getRuntime().availableProcessors()
) {
    private val profile = AtomicReference(store.load())

    fun observe(condition: HostCondition, workload: CognitiveWorkload, sample: ExperienceSample) {
        val old = profile.get()
        val alpha = if (old.sampleCount < 8) 0.28 else 0.10
        val latency = if (old.sampleCount == 0L) sample.latencyMs.toDouble()
            else old.latencyEmaMs * (1.0 - alpha) + sample.latencyMs * alpha
        val successValue = if (sample.successful && !sample.taskAbandoned) 1.0 else 0.0
        val correctionValue = if (sample.userCorrection) 1.0 else 0.0
        val success = old.successEma * (1.0 - alpha) + successValue * alpha
        val correction = old.correctionEma * (1.0 - alpha) + correctionValue * alpha

        // If work is slow, reduce parallelism before consuming more memory.
        // If it is fast and stable, cautiously allow one additional thread.
        val threadDelta = when {
            latency > 8_000.0 || !sample.successful -> -1
            latency in 1.0..2_500.0 && sample.successful && !sample.userCorrection -> 1
            else -> 0
        }
        val threads = (old.preferredThreadCount + threadDelta)
            .coerceIn(1, max(1, min(availableProcessors, 6)))
        val contextDelta = when {
            correction > 0.35 -> -512
            success > 0.92 && latency < 3_000.0 -> 512
            else -> 0
        }
        val context = (old.preferredContextTokens + contextDelta).coerceIn(2048, 16384)
        val next = SymbiosisProfile(
            sampleCount = old.sampleCount + 1,
            latencyEmaMs = latency,
            successEma = success,
            correctionEma = correction,
            preferredThreadCount = threads,
            preferredContextTokens = context
        )
        profile.set(next)
        store.save(next)
    }

    fun plan(condition: HostCondition, workload: CognitiveWorkload): InferenceEnvelope {
        val p = profile.get()
        val severeThermal = condition.thermalStatus >= 4
        val moderateThermal = condition.thermalStatus >= 2
        val lowBattery = condition.batteryPercent <= 15 && !condition.charging
        val memoryCritical = condition.availableRamMb < 650
        val memoryTight = condition.availableRamMb < 1200
        val storageTight = condition.availableStorageMb < 1200
        val foreground = condition.appInForeground

        val defer = severeThermal || memoryCritical ||
            (lowBattery && workload == CognitiveWorkload.MAINTENANCE) ||
            (!foreground && workload == CognitiveWorkload.MAINTENANCE && condition.powerSave)

        val eligible = candidates().filter {
            it.minimumFreeRamMb <= condition.availableRamMb &&
                it.weightSizeMb.toLong() + 512L <= condition.availableStorageMb
        }.sortedWith(compareByDescending<ModelCandidate> { it.qualityTier }
            .thenBy { it.weightSizeMb })

        val chosen = when {
            memoryCritical -> null
            memoryTight -> eligible.minByOrNull { it.weightSizeMb }?.id
            else -> eligible.firstOrNull()?.id
        }

        val baseContext = when {
            memoryCritical -> 0
            memoryTight -> 2048
            moderateThermal || lowBattery -> min(3072, p.preferredContextTokens)
            else -> p.preferredContextTokens
        }
        val context = if (chosen == null) 0 else baseContext.coerceIn(2048, 16384)
        val threads = when {
            severeThermal || memoryTight || lowBattery -> 1
            moderateThermal || condition.powerSave -> min(2, p.preferredThreadCount)
            else -> p.preferredThreadCount
        }.coerceIn(1, max(1, min(availableProcessors, 6)))

        val explanation = when {
            memoryCritical -> "Memory is critically low; unload inference and wait for headroom."
            severeThermal -> "Thermal pressure is high; defer compute until the phone cools."
            storageTight -> "Storage is tight; do not download or create additional model files."
            memoryTight -> "Use the smallest eligible model, short context and one CPU worker."
            lowBattery -> "Conserve battery; defer maintenance and reduce inference cost."
            moderateThermal -> "Reduce context and parallelism until thermal pressure clears."
            else -> "Host has headroom; use the best eligible local model within learned limits."
        }

        return InferenceEnvelope(
            modelId = if (defer) null else chosen,
            contextTokens = if (defer) 0 else context,
            maxNewTokens = when (workload) {
                CognitiveWorkload.CHAT -> 384
                CognitiveWorkload.REASONING, CognitiveWorkload.CODING -> 768
                CognitiveWorkload.PERCEPTION -> 192
                CognitiveWorkload.MAINTENANCE -> 256
            }.let { if (lowBattery || moderateThermal) min(it, 256) else it },
            cpuThreads = threads,
            keepModelResident = foreground && !memoryTight && !severeThermal && !lowBattery,
            allowVoice = !memoryCritical && !severeThermal,
            allowPerception = foreground && !memoryCritical && !severeThermal && !lowBattery,
            allowBackgroundLearning = !foreground && !condition.powerSave &&
                !lowBattery && !moderateThermal && condition.availableRamMb >= 1800,
            deferWork = defer || chosen == null,
            explanation = explanation
        )
    }
}

/** Reads only public Android signals; it does not request special permissions. */
class AndroidHostProbe(private val context: Context) {
    fun sample(appInForeground: Boolean): HostCondition {
        val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val storage = StatFs(context.filesDir.absolutePath)
        val batteryIntent: Intent? = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 50
        val charging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) in
            setOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL)
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else 0
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        return HostCondition(
            availableRamMb = memory.availMem / (1024L * 1024L),
            totalRamMb = memory.totalMem / (1024L * 1024L),
            availableStorageMb = storage.availableBytes / (1024L * 1024L),
            batteryPercent = batteryPercent,
            charging = charging,
            powerSave = power.isPowerSaveMode,
            thermalStatus = thermal,
            appInForeground = appInForeground,
            networkAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        )
    }
}
