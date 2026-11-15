package com.jarvis.app.symbiosis

import android.content.Context
import com.jarvis.app.model.AndroidResourceSnapshot
import com.jarvis.app.model.ResourceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android host adapter for NAE. It observes the host and learns bounded
 * workload statistics. It never requests root or changes other applications.
 */
class NanoSymbioticController(
    context: Context,
    private val storageDir: File
) {
    private val appContext = context.applicationContext
    private val device = AndroidResourceSnapshot(appContext)
    private val policy = NanoSymbioticPolicy()
    private val evidenceFile = File(storageDir, "symbiosis/workload-evidence.properties")
    private var monitor: Job? = null
    @Volatile private var workerScope: CoroutineScope? = null

    private val mutablePlan = MutableStateFlow(
        SymbioticPlan(
            mode = SymbioticMode.BALANCED,
            contextTokenBudget = 2048,
            maxConcurrentExpensiveTasks = 1,
            allowBackgroundConsolidation = false,
            memoryReserveMb = 256,
            reason = "Waiting for the first host measurement."
        )
    )
    val plan: StateFlow<SymbioticPlan> = mutablePlan.asStateFlow()

    init {
        loadEvidence()
        refresh()
    }

    /** Start low-frequency observation; safe to call more than once. */
    fun start(scope: CoroutineScope) {
        if (monitor?.isActive == true) return
        workerScope = scope
        monitor = scope.launch(Dispatchers.IO) {
            while (isActive) {
                refresh()
                delay(15_000L)
            }
        }
    }

    /** Current real host reading, adjusted only by a learned safety reserve. */
    fun resourceSnapshot(): ResourceSnapshot {
        val raw = readHost()
        val currentPlan = policy.plan(raw)
        mutablePlan.value = currentPlan
        return object : ResourceSnapshot {
            override val availableMemoryMb: Long =
                (raw.availableMemoryMb - currentPlan.memoryReserveMb).coerceAtLeast(0L)
            override val cpuLoadPercent: Double = raw.cpuLoadPercent
            override val thermalLevel: Int = raw.thermalLevel
            override val batteryPercent: Int = raw.batteryPercent
            override val isCharging: Boolean = raw.isCharging
        }
    }

    /**
     * Feedback from a real organ load. Only aggregate timing/success/memory
     * figures are persisted; no prompts, transcripts, app contents or files.
     */
    fun recordOutcome(
        organ: String,
        succeeded: Boolean,
        latencyMs: Long,
        peakMemoryMb: Long = 0L
    ) {
        // PSS is a process-wide estimate, not a per-model allocation. Use it
        // only as a coarse upper bound when the caller has no native peak metric.
        val observedMemory = if (peakMemoryMb > 0L) peakMemoryMb
            else android.os.Debug.getPss().toLong().coerceAtLeast(0L) / 1024L
        policy.recordOutcome(organ, succeeded, latencyMs, observedMemory)
        workerScope?.launch(Dispatchers.IO) {
            persistEvidence()
            refresh()
        }
    }

    private fun refresh() {
        mutablePlan.value = policy.plan(readHost())
    }

    private fun readHost(): HostResourceState {
        val snapshot = device
        val storage = android.os.StatFs(storageDir.absolutePath).availableBytes / (1024L * 1024L)
        return HostResourceState(
            availableMemoryMb = snapshot.availableMemoryMb,
            cpuLoadPercent = snapshot.cpuLoadPercent,
            thermalLevel = snapshot.thermalLevel,
            batteryPercent = snapshot.batteryPercent,
            isCharging = snapshot.isCharging,
            availableStorageMb = storage
        )
    }

    private fun loadEvidence() {
        if (!evidenceFile.isFile) return
        runCatching {
            val properties = Properties()
            evidenceFile.inputStream().use { properties.load(it) }
            val records = mutableMapOf<String, WorkloadEvidence>()
            properties.stringPropertyNames().mapNotNull { key ->
                val parts = properties.getProperty(key).split(',')
                if (parts.size != 5) return@mapNotNull null
                val value = WorkloadEvidence(
                    samples = parts[0].toIntOrNull() ?: 0,
                    successes = parts[1].toIntOrNull() ?: 0,
                    failureStreak = parts[2].toIntOrNull() ?: 0,
                    latencyEmaMs = parts[3].toDoubleOrNull() ?: 0.0,
                    peakMemoryMb = parts[4].toLongOrNull() ?: 0L
                )
                key to value
            }.forEach { (key, value) -> records[key] = value }
            policy.restore(records)
        }
    }

    private fun persistEvidence() {
        runCatching {
            evidenceFile.parentFile?.mkdirs()
            val properties = Properties()
            policy.allEvidence().forEach { (key, value) ->
                properties.setProperty(
                    key,
                    listOf(
                        value.samples,
                        value.successes,
                        value.failureStreak,
                        value.latencyEmaMs,
                        value.peakMemoryMb
                    ).joinToString(",")
                )
            }
            val temp = File(evidenceFile.parentFile, evidenceFile.name + ".tmp")
            temp.outputStream().use { properties.store(it, "NAE aggregate workload evidence; no user content") }
            if (evidenceFile.exists()) evidenceFile.delete()
            temp.renameTo(evidenceFile)
        }
    }
}
