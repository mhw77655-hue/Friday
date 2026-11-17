package com.jarvis.app.modelsim

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Model-free, on-device workload laboratory.
 *
 * It measures host resources and bounded synthetic CPU/memory/storage work.
 * It does NOT load model weights, invoke an LLM, or claim synthetic work is
 * equivalent to model inference. Predictions are deliberately conservative.
 */
data class DeviceSnapshot(
    val device: String,
    val soc: String,
    val androidApi: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val appMemoryClassMb: Int,
    val freeStorageMb: Long,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val thermalStatus: Int,
    val cpuCores: Int,
    val abi: String
)

data class SyntheticBenchmark(
    val cpuOperationsPerSecond: Long,
    val memoryTouchMbPerSecond: Double,
    val storageWriteMbPerSecond: Double,
    val processPssBeforeMb: Long,
    val processPssAfterMb: Long,
    val elapsedMs: Long
)

data class ModelCandidate(
    val id: String,
    val name: String,
    val parameterLabel: String,
    val weightMb: Long,
    val runtime: String,
    val quantization: String,
    val modalities: String,
    val notes: String
)

enum class FitVerdict { LIKELY_FIT, CONDITIONAL, NOT_RECOMMENDED }

data class ModelSimulation(
    val candidate: ModelCandidate,
    val contextTokens: Int,
    val estimatedWorkingSetMb: Long,
    val availableAfterReserveMb: Long,
    val verdict: FitVerdict,
    val explanation: String
)

data class SimulationReport(
    val snapshot: DeviceSnapshot,
    val benchmark: SyntheticBenchmark?,
    val models: List<ModelSimulation>,
    val caveat: String
)

class ModelFreeDeviceSimulator(private val context: Context) {
    private val appContext = context.applicationContext
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val profileFile = File(appContext.filesDir, "model-lab/device-profile.properties")

    /** Persist only technical device/benchmark measurements, never user content. */
    fun persistSnapshot(snapshot: DeviceSnapshot) = persist(mapOf(
        "device" to snapshot.device,
        "soc" to snapshot.soc,
        "androidApi" to snapshot.androidApi.toString(),
        "totalRamMb" to snapshot.totalRamMb.toString(),
        "availableRamMb" to snapshot.availableRamMb.toString(),
        "appMemoryClassMb" to snapshot.appMemoryClassMb.toString(),
        "freeStorageMb" to snapshot.freeStorageMb.toString(),
        "batteryPercent" to snapshot.batteryPercent.toString(),
        "isCharging" to snapshot.isCharging.toString(),
        "thermalStatus" to snapshot.thermalStatus.toString(),
        "cpuCores" to snapshot.cpuCores.toString(),
        "abi" to snapshot.abi
    ))

    fun persistBenchmark(result: SyntheticBenchmark) = persist(mapOf(
        "cpuOperationsPerSecond" to result.cpuOperationsPerSecond.toString(),
        "memoryTouchMbPerSecond" to result.memoryTouchMbPerSecond.toString(),
        "storageWriteMbPerSecond" to result.storageWriteMbPerSecond.toString(),
        "processPssBeforeMb" to result.processPssBeforeMb.toString(),
        "processPssAfterMb" to result.processPssAfterMb.toString(),
        "benchmarkElapsedMs" to result.elapsedMs.toString()
    ))

    private fun persist(values: Map<String, String>) {
        runCatching {
            profileFile.parentFile?.mkdirs()
            val properties = Properties()
            if (profileFile.isFile) profileFile.inputStream().use(properties::load)
            values.forEach { (key, value) -> properties.setProperty(key, value) }
            properties.setProperty("updatedAtEpochMs", System.currentTimeMillis().toString())
            val temp = File(profileFile.parentFile, profileFile.name + ".tmp")
            FileOutputStream(temp).use { output ->
                properties.store(output, "Friday model-free device profile; technical metrics only")
                output.fd.sync()
            }
            if (profileFile.exists()) profileFile.delete()
            temp.renameTo(profileFile)
        }
    }

    fun snapshot(): DeviceSnapshot {
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val stat = StatFs(appContext.filesDir.absolutePath)
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 } ?: -1
        val charging = runCatching {
            val intent = appContext.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }.getOrDefault(false)
        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            powerManager.currentThermalStatus
        } else {
            0
        }
        val soc = if (Build.VERSION.SDK_INT >= 31) {
            listOfNotNull(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                .filter { it.isNotBlank() }.joinToString(" ").ifBlank { Build.HARDWARE }
        } else Build.HARDWARE

        return DeviceSnapshot(
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            soc = soc,
            androidApi = Build.VERSION.SDK_INT,
            totalRamMb = memory.totalMem / MIB,
            availableRamMb = memory.availMem / MIB,
            appMemoryClassMb = activityManager.memoryClass,
            freeStorageMb = stat.availableBytes / MIB,
            batteryPercent = battery,
            isCharging = charging,
            thermalStatus = thermal,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        )
    }

    suspend fun runSyntheticBenchmark(): SyntheticBenchmark = withContext(Dispatchers.Default) {
        val before = snapshot()
        require(before.thermalStatus < 3) { "Phone is too hot for the optional benchmark." }
        require(before.batteryPercent < 0 || before.batteryPercent >= 12 || before.isCharging) {
            "Battery is too low for the optional benchmark."
        }
        val start = System.nanoTime()
        val pssBefore = Debug.getPss().toLong() / 1024L

        // A bounded integer workload; no unbounded stress loop or native allocation.
        val cpuStart = System.nanoTime()
        var value = 0x13579BDF
        var iterations = 0L
        val cpuDeadline = cpuStart + 450_000_000L
        while (System.nanoTime() < cpuDeadline) {
            value = value xor (value shl 13)
            value = value xor (value ushr 17)
            value = value xor (value shl 5)
            iterations++
        }
        if (value == 0) iterations++
        val cpuElapsedNs = (System.nanoTime() - cpuStart).coerceAtLeast(1L)

        // Touch at most 16 MiB and release it immediately.
        val memoryStart = System.nanoTime()
        val blocks = Array(16) { ByteArray(1024 * 1024) }
        blocks.forEachIndexed { index, block ->
            var offset = 0
            while (offset < block.size) {
                block[offset] = (index + offset).toByte()
                offset += 4096
            }
        }
        val memoryMs = (System.nanoTime() - memoryStart).coerceAtLeast(1L) / 1_000_000.0
        val touchedMb = blocks.sumOf { it.size }.toDouble() / MIB
        @Suppress("UNUSED_VARIABLE") val keepAlive = blocks[0][0]

        // 4 MiB sequential storage probe in app-private cache; always delete.
        val probe = File(appContext.cacheDir, "model-sim-${System.nanoTime()}.tmp")
        val payload = ByteArray(64 * 1024) { (it and 0xff).toByte() }
        val storageStart = System.nanoTime()
        try {
            FileOutputStream(probe).use { out ->
                repeat(64) { out.write(payload) }
                out.fd.sync()
            }
            FileInputStream(probe).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (input.read(buffer) >= 0) Unit
            }
        } finally {
            probe.delete()
        }
        val storageMs = (System.nanoTime() - storageStart).coerceAtLeast(1L) / 1_000_000.0
        val elapsed = (System.nanoTime() - start) / 1_000_000L
        val pssAfter = Debug.getPss().toLong() / 1024L

        SyntheticBenchmark(
            cpuOperationsPerSecond = (iterations * 1_000_000_000.0 / cpuElapsedNs).roundToInt().toLong(),
            memoryTouchMbPerSecond = touchedMb * 1000.0 / memoryMs,
            storageWriteMbPerSecond = 4.0 * 1000.0 / storageMs,
            processPssBeforeMb = pssBefore,
            processPssAfterMb = pssAfter,
            elapsedMs = elapsed
        )
    }

    fun simulate(
        snapshot: DeviceSnapshot,
        contextTokens: Int = 2048,
        candidates: List<ModelCandidate> = CANDIDATES
    ): List<ModelSimulation> {
        val safeContext = contextTokens.coerceIn(512, 8192)
        // Keep at least 900 MiB for Android, the app, voice and normal foreground work.
        val reserveMb = max(900L, (snapshot.totalRamMb * 0.14).roundToInt().toLong())
        val availableForModel = (snapshot.availableRamMb - reserveMb).coerceAtLeast(0L)
        return candidates.map { candidate ->
            // Weight pages + runtime scratch + approximate KV/attention state.
            // This is a screening estimate, not a model-specific allocator measurement.
            val weightsAndMapping = (candidate.weightMb * 1.12).roundToInt().toLong()
            val parameterKnown = candidate.parameterLabel != "Unknown parameters"
            val runtimeScratch = when {
                candidate.parameterLabel.contains("4B") -> 620L
                candidate.parameterLabel.contains("3B") -> 520L
                candidate.parameterLabel.contains("2B") -> 420L
                parameterKnown -> 320L
                else -> 900L // unknown architecture: deliberately conservative
            }
            val kvMbPerToken = when {
                candidate.parameterLabel.contains("4B") -> 0.035
                candidate.parameterLabel.contains("3B") -> 0.030
                candidate.parameterLabel.contains("2B") -> 0.024
                candidate.parameterLabel.contains("1.2B") -> 0.014
                parameterKnown -> 0.020
                else -> 0.060 // unknown architecture/cache: never present as a confident fit
            }
            val contextMemory = (safeContext * kvMbPerToken).roundToInt().toLong()
            val workingSet = weightsAndMapping + runtimeScratch + contextMemory
            val verdict = when {
                snapshot.thermalStatus >= 3 || snapshot.availableRamMb < 700L ->
                    FitVerdict.NOT_RECOMMENDED
                parameterKnown && workingSet + 350L <= availableForModel -> FitVerdict.LIKELY_FIT
                workingSet <= availableForModel + 350L -> FitVerdict.CONDITIONAL
                else -> FitVerdict.NOT_RECOMMENDED
            }
            val explanation = when (verdict) {
                FitVerdict.LIKELY_FIT ->
                    "Estimated working set fits with reserve. Still requires a real model load test."
                FitVerdict.CONDITIONAL ->
                    if (parameterKnown) "Borderline: try a shorter context or unload other workloads before downloading."
                    else "Architecture/parameter count is unknown. Treat this as an unverified upper-bound estimate, not a fit verdict."
                FitVerdict.NOT_RECOMMENDED ->
                    "Estimated working set exceeds safe headroom, or current device conditions are poor."
            }
            ModelSimulation(candidate, safeContext, workingSet, availableForModel, verdict, explanation)
        }.sortedWith(compareBy<ModelSimulation> {
            when (it.verdict) {
                FitVerdict.LIKELY_FIT -> 0
                FitVerdict.CONDITIONAL -> 1
                FitVerdict.NOT_RECOMMENDED -> 2
            }
        }.thenBy { it.estimatedWorkingSetMb })
    }

    companion object {
        private const val MIB = 1024L * 1024L

        // Catalog entries are candidates for screening, not compatibility claims.
        val CANDIDATES = listOf(
            ModelCandidate(
                "lfm25-12b-q4", "LFM2.5-1.2B-Instruct", "1.2B", 731,
                "llama.cpp / GGUF", "Q4_K_M", "Text",
                "Small-footprint baseline; capability and refusal behaviour need evaluation."
            ),
            ModelCandidate(
                "qwen35-2b-q4", "Qwen3.5-2B", "2B", 1210,
                "GGUF or LiteRT-LM", "Q4_0", "Text + image architecture",
                "Strong mobile-oriented candidate; published mobile measurements use newer flagship hardware."
            ),
            ModelCandidate(
                "gemma4-e2b", "Gemma 4 E2B", "E2B", 2580,
                "LiteRT-LM", "Quantized mobile package", "Text + multimodal family",
                "Broader runtime alternative; package size and target-device kernels must be verified."
            ),
            ModelCandidate(
                "qwen35-4b-uncensored", "Qwen3.5-4B Uncensored v2", "4B", 2780,
                "GGUF / llama.cpp", "Q4_K_M", "Text; separate vision projector",
                "On-demand research candidate. Uncensored claims are partial; not refusal-free."
            )
        )

    }
}
