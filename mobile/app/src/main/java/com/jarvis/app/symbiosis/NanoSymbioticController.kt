package com.jarvis.app.symbiosis

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.jarvis.app.model.AndroidResourceSnapshot
import com.jarvis.app.model.ResourceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Properties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android host adapter for NAE. It observes the host and learns bounded
 * workload statistics plus a content-free, persistent profile of this device.
 * It never requests root or changes other applications.
 */
class NanoSymbioticController(
    context: Context,
    private val storageDir: File
) {
    private val appContext = context.applicationContext
    private val device = AndroidResourceSnapshot(appContext)
    private val policy = NanoSymbioticPolicy()
    private val evidenceFile = File(storageDir, "symbiosis/workload-evidence.properties")
    private val hostStudyFile = File(storageDir, "symbiosis/host-study.properties")
    private val fingerprint = readHostFingerprint()
    private val hostStudy = HostStudy(fingerprint, loadHostStudy())
    private val liquidSymbiosis = LiquidSymbiosisEngine(
        File(storageDir, "symbiosis/liquid-organ-strategies.properties")
    )
    private var monitor: Job? = null
    @Volatile private var workerScope: CoroutineScope? = null
    @Volatile private var lastHostStudyPersistAtMs: Long = 0L

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

    /** Read-only view of what NAE has learned about this exact host profile. */
    fun hostStudySnapshot(): HostStudyRecord = hostStudy.snapshot()

    /** Persistent adaptive strategy shared with the cognitive/voice runtime. */
    fun liquidSnapshot(): LiquidSymbiosisSnapshot = liquidSymbiosis.snapshot()

    init {
        // The inference process may be started before its private model directory
        // has ever been created. Create it before any StatFs inspection; never let
        // an absent optional model directory crash the isolated inference service.
        ensureStorageDirectory(storageDir, appContext.filesDir)
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

    /** Live admission gate for large model loads and subsequent inference turns. */
    fun admitModel(modelBytes: Long, requestedContextTokens: Int): ModelAdmission {
        val host = readHost()
        val admission = policy.admitModel(applyLearnedHostReserve(host), modelBytes, requestedContextTokens)
        mutablePlan.value = admission.plan
        return admission
    }

    /** Current real host reading, adjusted only by a learned safety reserve. */
    fun resourceSnapshot(): ResourceSnapshot {
        val raw = readHost()
        val currentPlan = policy.plan(applyLearnedHostReserve(raw))
        mutablePlan.value = currentPlan
        val learnedReasoningReserve = liquidSymbiosis.additionalAdmissionReserveMb(SymbioticOrgan.REASONING)
        return object : ResourceSnapshot {
            override val availableMemoryMb: Long =
                (raw.availableMemoryMb - currentPlan.memoryReserveMb -
                    hostStudy.snapshot().additionalMemoryReserveMb - learnedReasoningReserve).coerceAtLeast(0L)
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
        val normalizedOrgan = organ.trim().lowercase()
        val liquidOrgan = when {
            normalizedOrgan.contains("reason") || normalizedOrgan.contains("model") ||
                normalizedOrgan.contains("infer") || normalizedOrgan == "llm" -> SymbioticOrgan.REASONING
            normalizedOrgan.contains("speech_input") || normalizedOrgan == "stt" ||
                normalizedOrgan.contains("recognition") || normalizedOrgan == "voice_input" -> SymbioticOrgan.SPEECH_INPUT
            normalizedOrgan.contains("speech_output") || normalizedOrgan == "tts" ||
                normalizedOrgan == "voice" || normalizedOrgan == "voice_output" -> SymbioticOrgan.SPEECH_OUTPUT
            normalizedOrgan.contains("memory") || normalizedOrgan.contains("retrieval") ||
                normalizedOrgan.contains("consolidation") -> SymbioticOrgan.MEMORY
            normalizedOrgan.contains("vision") || normalizedOrgan.contains("camera") -> SymbioticOrgan.VISION
            else -> SymbioticOrgan.BACKGROUND
        }
        liquidSymbiosis.recordOutcome(
            organ = liquidOrgan,
            succeeded = succeeded,
            latencyMs = latencyMs,
            peakMemoryMb = observedMemory
        )
        workerScope?.launch(Dispatchers.IO) {
            persistEvidence()
            refresh()
        }
    }

    private fun refresh() {
        val host = readHost()
        val now = System.currentTimeMillis()
        val processPssMb = android.os.Debug.getPss().toLong().coerceAtLeast(0L) / 1024L
        hostStudy.observe(host, processPssMb, now)
        liquidSymbiosis.observeHost(
            LiquidHostState(
                availableMemoryMb = host.availableMemoryMb,
                cpuLoad = host.cpuLoadPercent,
                thermalPressure = (host.thermalLevel / 2.0).coerceIn(0.0, 1.0),
                batteryPercent = host.batteryPercent,
                charging = host.isCharging
            )
        )
        mutablePlan.value = policy.plan(applyLearnedHostReserve(host))
        // Avoid turning a 15-second observer into a continuous flash-write loop.
        // Persist immediately on first sample, then at most once per minute.
        if (lastHostStudyPersistAtMs == 0L || now - lastHostStudyPersistAtMs >= 60_000L) {
            persistHostStudy()
            lastHostStudyPersistAtMs = now
        }
    }

    private fun applyLearnedHostReserve(host: HostResourceState): HostResourceState =
        host.copy(
            availableMemoryMb = (host.availableMemoryMb -
                hostStudy.snapshot().additionalMemoryReserveMb).coerceAtLeast(0L)
        )

    private fun readHost(): HostResourceState {
        val storageLocation = if (storageDir.isDirectory) storageDir else appContext.filesDir
        val storage = runCatching {
            android.os.StatFs(storageLocation.absolutePath).availableBytes / (1024L * 1024L)
        }.getOrDefault(0L)
        return HostResourceState(
            availableMemoryMb = device.availableMemoryMb,
            cpuLoadPercent = device.cpuLoadPercent,
            thermalLevel = device.thermalLevel,
            batteryPercent = device.batteryPercent,
            isCharging = device.isCharging,
            availableStorageMb = storage
        )
    }

    private fun readHostFingerprint(): HostFingerprint {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else ""
        return HostFingerprint(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            socModel = soc,
            board = Build.BOARD.orEmpty(),
            androidApi = Build.VERSION.SDK_INT,
            totalMemoryMb = memory.totalMem / (1024L * 1024L),
            appMemoryClassMb = manager.memoryClass,
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
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

    private fun loadHostStudy(): HostStudyRecord? {
        if (!hostStudyFile.isFile) return null
        return runCatching {
            val p = Properties()
            hostStudyFile.inputStream().use { p.load(it) }
            val savedFingerprint = HostFingerprint(
                manufacturer = p.getProperty("manufacturer", ""),
                model = p.getProperty("model", ""),
                device = p.getProperty("device", ""),
                socModel = p.getProperty("socModel", ""),
                board = p.getProperty("board", ""),
                androidApi = p.getProperty("androidApi")?.toIntOrNull() ?: -1,
                totalMemoryMb = p.getProperty("totalMemoryMb")?.toLongOrNull() ?: -1L,
                appMemoryClassMb = p.getProperty("appMemoryClassMb")?.toIntOrNull() ?: -1,
                cpuCoreCount = p.getProperty("cpuCoreCount")?.toIntOrNull() ?: -1,
                primaryAbi = p.getProperty("primaryAbi", "")
            )
            if (savedFingerprint != fingerprint) return@runCatching null
            HostStudyRecord(
                fingerprint = savedFingerprint,
                sampleCount = p.getProperty("sampleCount")?.toLongOrNull() ?: 0L,
                minimumAvailableMemoryMb = p.getProperty("minimumAvailableMemoryMb")?.toLongOrNull() ?: Long.MAX_VALUE,
                maximumCpuLoadPercent = p.getProperty("maximumCpuLoadPercent")?.toDoubleOrNull() ?: 0.0,
                averageCpuLoadEmaPercent = p.getProperty("averageCpuLoadEmaPercent")?.toDoubleOrNull() ?: 0.0,
                thermalPressureSamples = p.getProperty("thermalPressureSamples")?.toLongOrNull() ?: 0L,
                lowMemorySamples = p.getProperty("lowMemorySamples")?.toLongOrNull() ?: 0L,
                memoryPressureEma = p.getProperty("memoryPressureEma")?.toDoubleOrNull() ?: 0.0,
                thermalPressureEma = p.getProperty("thermalPressureEma")?.toDoubleOrNull() ?: 0.0,
                minimumBatteryPercent = p.getProperty("minimumBatteryPercent")?.toIntOrNull() ?: 100,
                maximumObservedProcessPssMb = p.getProperty("maximumObservedProcessPssMb")?.toLongOrNull() ?: 0L,
                lastObservedAtMs = p.getProperty("lastObservedAtMs")?.toLongOrNull() ?: 0L
            )
        }.getOrNull()
    }

    private fun persistHostStudy() {
        runCatching {
            val r = hostStudy.snapshot()
            hostStudyFile.parentFile?.mkdirs()
            val p = Properties().apply {
                setProperty("manufacturer", r.fingerprint.manufacturer)
                setProperty("model", r.fingerprint.model)
                setProperty("device", r.fingerprint.device)
                setProperty("socModel", r.fingerprint.socModel)
                setProperty("board", r.fingerprint.board)
                setProperty("androidApi", r.fingerprint.androidApi.toString())
                setProperty("totalMemoryMb", r.fingerprint.totalMemoryMb.toString())
                setProperty("appMemoryClassMb", r.fingerprint.appMemoryClassMb.toString())
                setProperty("cpuCoreCount", r.fingerprint.cpuCoreCount.toString())
                setProperty("primaryAbi", r.fingerprint.primaryAbi)
                setProperty("sampleCount", r.sampleCount.toString())
                setProperty("minimumAvailableMemoryMb", r.minimumAvailableMemoryMb.toString())
                setProperty("maximumCpuLoadPercent", r.maximumCpuLoadPercent.toString())
                setProperty("averageCpuLoadEmaPercent", r.averageCpuLoadEmaPercent.toString())
                setProperty("thermalPressureSamples", r.thermalPressureSamples.toString())
                setProperty("lowMemorySamples", r.lowMemorySamples.toString())
                setProperty("memoryPressureEma", r.memoryPressureEma.toString())
                setProperty("thermalPressureEma", r.thermalPressureEma.toString())
                setProperty("minimumBatteryPercent", r.minimumBatteryPercent.toString())
                setProperty("maximumObservedProcessPssMb", r.maximumObservedProcessPssMb.toString())
                setProperty("lastObservedAtMs", r.lastObservedAtMs.toString())
            }
            val temp = File(hostStudyFile.parentFile, hostStudyFile.name + ".tmp")
            temp.outputStream().use { p.store(it, "NAE host study; technical aggregates only") }
            if (hostStudyFile.exists()) hostStudyFile.delete()
            temp.renameTo(hostStudyFile)
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

/**
 * Makes an optional runtime directory safe to inspect. Falls back to the
 * already-created app-private files directory if the preferred path cannot
 * be created, so a storage probe can never take down inference startup.
 */
internal fun ensureStorageDirectory(preferred: File, fallback: File): File {
    if (preferred.isDirectory || (preferred.mkdirs() && preferred.isDirectory)) return preferred
    if (fallback.isDirectory || (fallback.mkdirs() && fallback.isDirectory)) return fallback
    throw IllegalStateException("No usable app-private storage directory is available.")
}

