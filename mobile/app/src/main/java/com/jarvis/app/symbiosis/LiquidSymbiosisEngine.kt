package com.jarvis.app.symbiosis

import java.io.File
import java.util.Properties
import kotlin.math.max
import kotlin.math.min

enum class SymbioticOrgan { REASONING, SPEECH_INPUT, SPEECH_OUTPUT, MEMORY, VISION, BACKGROUND }

data class OrganStrategy(
    val contextBudget: Int = 2048,
    val concurrency: Int = 1,
    val chunkSize: Int = 256,
    val backgroundWeight: Double = 0.25,
    val successfulRuns: Long = 0L,
    val failedRuns: Long = 0L,
    val latencyEmaMs: Double = 0.0,
    val memoryPeakMb: Long = 0L,
    val rewardEma: Double = 0.0
)

data class LiquidHostState(
    val availableMemoryMb: Long,
    val cpuLoad: Double,
    val thermalPressure: Double,
    val batteryPercent: Int,
    val charging: Boolean
)

data class LiquidSymbiosisSnapshot(
    val generation: Long,
    val host: LiquidHostState?,
    val organs: Map<SymbioticOrgan, OrganStrategy>,
    val lastAdaptation: String,
    val lastPredictionError: Double
)

/**
 * JEPA-inspired online predictive core. It predicts compact environment
 * representations rather than reconstructing raw audio, text or pixels.
 * This is a lightweight on-device predictor, not a pretrained neural JEPA.
 */
class JepaLatentPredictor(
    private val dimensions: Int = 6,
    private val learningRate: Double = 0.035,
    private val targetMomentum: Double = 0.98
) {
    private var target = DoubleArray(dimensions)
    private var weights = Array(dimensions) { DoubleArray(dimensions + 1) }
    private var samples = 0L
    private var errorEma = 0.0

    @Synchronized
    fun observe(current: DoubleArray, action: Double, next: DoubleArray): Double {
        require(current.size == dimensions && next.size == dimensions)
        val x = current.copyOf() + doubleArrayOf(action.coerceIn(-1.0, 1.0))
        val prediction = DoubleArray(dimensions) { row ->
            weights[row].indices.sumOf { col -> weights[row][col] * x[col] }
        }
        for (i in 0 until dimensions) {
            target[i] = if (samples == 0L) next[i]
                else targetMomentum * target[i] + (1.0 - targetMomentum) * next[i]
        }
        var squaredError = 0.0
        for (row in 0 until dimensions) {
            val error = prediction[row] - target[row]
            squaredError += error * error
            for (col in x.indices) weights[row][col] -= learningRate * error * x[col]
        }
        val rmse = kotlin.math.sqrt(squaredError / dimensions)
        errorEma = if (samples == 0L) rmse else errorEma * 0.9 + rmse * 0.1
        samples++
        return rmse
    }

    @Synchronized
    fun predict(current: DoubleArray, action: Double): DoubleArray {
        require(current.size == dimensions)
        val x = current.copyOf() + doubleArrayOf(action.coerceIn(-1.0, 1.0))
        return DoubleArray(dimensions) { row ->
            weights[row].indices.sumOf { col -> weights[row][col] * x[col] }
        }
    }

    @Synchronized fun predictionErrorEma(): Double = errorEma
    @Synchronized fun sampleCount(): Long = samples
}

/** Persistent online adaptation driven by real host observations and organ outcomes. */
class LiquidSymbiosisEngine(private val persistenceFile: File? = null) {
    private val strategies = SymbioticOrgan.entries.associateWith { OrganStrategy() }.toMutableMap()
    private var host: LiquidHostState? = null
    private var generation = 0L
    private var lastAdaptation = "Awaiting live phone observations"
    private val predictor = JepaLatentPredictor()
    private var previousLatent: DoubleArray? = null
    private var previousAction = 0.0
    private var predictionError = 0.0

    init { restore() }

    @Synchronized
    fun observeHost(state: LiquidHostState): LiquidSymbiosisSnapshot {
        host = state.copy(
            availableMemoryMb = state.availableMemoryMb.coerceAtLeast(0L),
            cpuLoad = state.cpuLoad.coerceIn(0.0, 100.0),
            thermalPressure = state.thermalPressure.coerceIn(0.0, 1.0),
            batteryPercent = state.batteryPercent.coerceIn(0, 100)
        )
        val latent = encode(host!!)
        previousLatent?.let { previous -> predictionError = predictor.observe(previous, previousAction, latent) }
        previousLatent = latent
        adaptToHost(host!!)
        persist()
        return snapshot()
    }

    @Synchronized
    fun recordOutcome(
        organ: SymbioticOrgan,
        succeeded: Boolean,
        latencyMs: Long,
        peakMemoryMb: Long = 0L,
        qualityReward: Double? = null
    ): LiquidSymbiosisSnapshot {
        val old = strategies.getValue(organ)
        val runs = old.successfulRuns + old.failedRuns
        val reward = qualityReward?.coerceIn(-1.0, 1.0) ?: if (succeeded) 0.25 else -0.75
        val nextReward = if (runs == 0L) reward else old.rewardEma * 0.85 + reward * 0.15
        val latency = latencyMs.coerceAtLeast(0L).toDouble()
        strategies[organ] = old.copy(
            successfulRuns = old.successfulRuns + if (succeeded) 1L else 0L,
            failedRuns = old.failedRuns + if (succeeded) 0L else 1L,
            latencyEmaMs = if (runs == 0L) latency else old.latencyEmaMs * 0.8 + latency * 0.2,
            memoryPeakMb = max(old.memoryPeakMb, peakMemoryMb.coerceAtLeast(0L)),
            rewardEma = nextReward
        )
        previousAction = if (succeeded) 0.5 else -0.5
        adaptOrgan(organ)
        generation++
        lastAdaptation = "$organ learned from ${if (succeeded) "success" else "failure"}"
        persist()
        return snapshot()
    }

    @Synchronized fun strategy(organ: SymbioticOrgan): OrganStrategy = strategies.getValue(organ)

    @Synchronized
    fun snapshot() = LiquidSymbiosisSnapshot(generation, host, strategies.toMap(), lastAdaptation, predictionError)

    private fun adaptToHost(state: LiquidHostState) {
        val constrained = state.availableMemoryMb < 1400L || state.thermalPressure >= 0.65 || state.cpuLoad >= 88.0
        val healthy = state.availableMemoryMb >= 2600L && state.thermalPressure < 0.25 && state.cpuLoad < 65.0
        if (constrained) {
            val reasoning = strategies.getValue(SymbioticOrgan.REASONING)
            strategies[SymbioticOrgan.REASONING] = reasoning.copy(
                contextBudget = max(512, reasoning.contextBudget - 256), concurrency = 1
            )
            strategies[SymbioticOrgan.VISION] = strategies.getValue(SymbioticOrgan.VISION).copy(backgroundWeight = 0.0)
            strategies[SymbioticOrgan.BACKGROUND] = strategies.getValue(SymbioticOrgan.BACKGROUND).copy(backgroundWeight = 0.0)
            val speech = strategies.getValue(SymbioticOrgan.SPEECH_OUTPUT)
            strategies[SymbioticOrgan.SPEECH_OUTPUT] = speech.copy(chunkSize = max(96, speech.chunkSize - 32))
            lastAdaptation = "Host pressure: reduced reasoning and background footprint"
            generation++
        } else if (healthy) {
            val reasoning = strategies.getValue(SymbioticOrgan.REASONING)
            strategies[SymbioticOrgan.REASONING] = reasoning.copy(
                contextBudget = min(4096, reasoning.contextBudget + 64),
                concurrency = if (state.charging && state.batteryPercent >= 60) 2 else 1
            )
            val speech = strategies.getValue(SymbioticOrgan.SPEECH_OUTPUT)
            strategies[SymbioticOrgan.SPEECH_OUTPUT] = speech.copy(chunkSize = min(512, speech.chunkSize + 16))
            strategies[SymbioticOrgan.BACKGROUND] = strategies.getValue(SymbioticOrgan.BACKGROUND).copy(
                backgroundWeight = if (state.charging) 0.5 else 0.15
            )
            lastAdaptation = "Host recovered: gradually restored capability budgets"
            generation++
        } else {
            lastAdaptation = "Host stable: retained learned operating strategy"
        }
    }

    private fun adaptOrgan(organ: SymbioticOrgan) {
        val old = strategies.getValue(organ)
        val failing = old.failedRuns > old.successfulRuns && old.failedRuns >= 2
        val consistentlyGood = old.successfulRuns >= 3 && old.rewardEma > 0.15
        strategies[organ] = when {
            failing -> old.copy(
                contextBudget = max(512, old.contextBudget - 256), concurrency = 1,
                chunkSize = max(96, old.chunkSize - 32), backgroundWeight = 0.0
            )
            consistentlyGood && (host?.availableMemoryMb ?: 0L) >= 2400L &&
                (host?.thermalPressure ?: 1.0) < 0.5 -> old.copy(
                contextBudget = min(4096, old.contextBudget + 128),
                chunkSize = min(512, old.chunkSize + 16)
            )
            else -> old
        }
    }

    private fun encode(s: LiquidHostState): DoubleArray = doubleArrayOf(
        (s.availableMemoryMb / 4096.0).coerceIn(0.0, 1.0),
        s.cpuLoad / 100.0, s.thermalPressure, s.batteryPercent / 100.0,
        if (s.charging) 1.0 else 0.0,
        (strategies.getValue(SymbioticOrgan.REASONING).contextBudget / 4096.0).coerceIn(0.0, 1.0)
    )

    private fun restore() {
        val file = persistenceFile?.takeIf { it.isFile } ?: return
        runCatching {
            val p = Properties()
            file.inputStream().use(p::load)
            generation = p.getProperty("generation")?.toLongOrNull() ?: 0L
            lastAdaptation = p.getProperty("lastAdaptation") ?: lastAdaptation
            SymbioticOrgan.entries.forEach { organ ->
                val prefix = "organ.${organ.name}."
                val base = strategies.getValue(organ)
                strategies[organ] = base.copy(
                    contextBudget = p.getProperty(prefix + "context")?.toIntOrNull() ?: base.contextBudget,
                    concurrency = p.getProperty(prefix + "concurrency")?.toIntOrNull() ?: base.concurrency,
                    chunkSize = p.getProperty(prefix + "chunk")?.toIntOrNull() ?: base.chunkSize,
                    backgroundWeight = p.getProperty(prefix + "background")?.toDoubleOrNull() ?: base.backgroundWeight,
                    successfulRuns = p.getProperty(prefix + "success")?.toLongOrNull() ?: 0L,
                    failedRuns = p.getProperty(prefix + "failure")?.toLongOrNull() ?: 0L,
                    latencyEmaMs = p.getProperty(prefix + "latency")?.toDoubleOrNull() ?: 0.0,
                    memoryPeakMb = p.getProperty(prefix + "memory")?.toLongOrNull() ?: 0L,
                    rewardEma = p.getProperty(prefix + "reward")?.toDoubleOrNull() ?: 0.0
                )
            }
        }
    }

    private fun persist() {
        val file = persistenceFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val p = Properties()
            p.setProperty("generation", generation.toString())
            p.setProperty("lastAdaptation", lastAdaptation)
            strategies.forEach { (organ, value) ->
                val prefix = "organ.${organ.name}."
                p.setProperty(prefix + "context", value.contextBudget.toString())
                p.setProperty(prefix + "concurrency", value.concurrency.toString())
                p.setProperty(prefix + "chunk", value.chunkSize.toString())
                p.setProperty(prefix + "background", value.backgroundWeight.toString())
                p.setProperty(prefix + "success", value.successfulRuns.toString())
                p.setProperty(prefix + "failure", value.failedRuns.toString())
                p.setProperty(prefix + "latency", value.latencyEmaMs.toString())
                p.setProperty(prefix + "memory", value.memoryPeakMb.toString())
                p.setProperty(prefix + "reward", value.rewardEma.toString())
            }
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.outputStream().use { p.store(it, "Friday Nano Liquid Symbiosis state") }
            if (file.exists()) file.delete()
            temp.renameTo(file)
        }
    }
}
