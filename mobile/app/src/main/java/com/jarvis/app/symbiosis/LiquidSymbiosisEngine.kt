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
 * Lightweight action-conditioned latent world model for phone-state dynamics.
 *
 * It keeps a fixed, normalized host-state representation (so the target cannot
 * collapse through a trainable encoder), an EMA-smoothed target representation,
 * a bounded recent-transition replay buffer, and a small linear predictor.
 * This borrows the latent-target, action-conditioning and persistent test-time
 * adaptation ideas from JEPA/seq-JEPA/JEPA-TTT; it is not a vision-scale neural JEPA.
 */
class JepaLatentPredictor(
    private val dimensions: Int = 6,
    private val actionDimensions: Int = 3,
    private val learningRate: Double = 0.015,
    private val targetMomentum: Double = 0.98,
    private val replayCapacity: Int = 24,
    private val replayPasses: Int = 2
) {
    private data class Transition(val input: DoubleArray, val target: DoubleArray)

    private var target = DoubleArray(dimensions)
    private var targetInitialized = false
    private var weights = Array(dimensions) { DoubleArray(dimensions + actionDimensions + 1) }
    private val replay = ArrayDeque<Transition>()
    private var samples = 0L
    private var errorEma = 0.0

    init {
        require(dimensions > 0 && actionDimensions > 0)
        require(learningRate > 0.0 && learningRate <= 0.1)
        require(targetMomentum >= 0.0 && targetMomentum < 1.0)
        require(replayCapacity > 0 && replayPasses > 0)
    }

    @Synchronized
    fun observe(current: DoubleArray, action: Double, next: DoubleArray): Double =
        observe(current, DoubleArray(actionDimensions) { if (it == 0) action else 0.0 }, next)

    @Synchronized
    fun observe(current: DoubleArray, action: DoubleArray, next: DoubleArray): Double {
        require(current.size == dimensions && next.size == dimensions)
        require(action.size == actionDimensions)
        require(current.all { it.isFinite() } && next.all { it.isFinite() } && action.all { it.isFinite() })

        val input = features(current, action)
        if (!targetInitialized) {
            target = next.copyOf()
            targetInitialized = true
        } else {
            for (i in 0 until dimensions) {
                target[i] = targetMomentum * target[i] + (1.0 - targetMomentum) * next[i]
            }
        }

        val teacherTarget = target.copyOf()
        val beforeUpdate = forward(input)
        val error = rootMeanSquare(beforeUpdate, teacherTarget)
        replay.addLast(Transition(input, teacherTarget))
        while (replay.size > replayCapacity) replay.removeFirst()

        // Bounded online test-time training: only a small recent replay window,
        // with a fixed number of passes, so work and memory cannot grow unbounded.
        repeat(replayPasses) {
            replay.forEach(::trainOne)
        }

        errorEma = if (samples == 0L) error else errorEma * 0.9 + error * 0.1
        samples++
        return error
    }

    @Synchronized
    fun predict(current: DoubleArray, action: Double): DoubleArray =
        predict(current, DoubleArray(actionDimensions) { if (it == 0) action else 0.0 })

    @Synchronized
    fun predict(current: DoubleArray, action: DoubleArray): DoubleArray {
        require(current.size == dimensions && action.size == actionDimensions)
        require(current.all { it.isFinite() } && action.all { it.isFinite() })
        return forward(features(current, action))
    }

    /** Bounded latent rollout for comparing candidate operating-action sequences. */
    @Synchronized
    fun rollout(current: DoubleArray, actions: List<DoubleArray>): List<DoubleArray> {
        require(current.size == dimensions)
        require(actions.size <= 8) { "A phone-side rollout is capped at eight steps" }
        var state = current.copyOf()
        return actions.map { action ->
            state = predict(state, action)
            state.copyOf()
        }
    }

    @Synchronized fun predictionErrorEma(): Double = errorEma
    @Synchronized fun sampleCount(): Long = samples
    @Synchronized fun replaySize(): Int = replay.size

    @Synchronized
    fun exportState(): Map<String, String> = buildMap {
        put("samples", samples.toString())
        put("errorEma", errorEma.toString())
        put("targetInitialized", targetInitialized.toString())
        put("target", target.joinToString(","))
        put("replaySize", replay.size.toString())
        weights.forEachIndexed { row, values -> put("weights.$row", values.joinToString(",")) }
        replay.forEachIndexed { index, transition ->
            put("replay.$index.input", transition.input.joinToString(","))
            put("replay.$index.target", transition.target.joinToString(","))
        }
    }

    @Synchronized
    fun restoreState(state: Map<String, String>) {
        samples = state["samples"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        errorEma = state["errorEma"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        targetInitialized = state["targetInitialized"]?.toBooleanStrictOrNull() ?: false
        parseVector(state["target"], dimensions)?.copyInto(target)
        weights.indices.forEach { row ->
            parseVector(state["weights.$row"], dimensions + actionDimensions + 1)
                ?.copyInto(weights[row])
        }
        replay.clear()
        val count = (state["replaySize"]?.toIntOrNull() ?: 0).coerceIn(0, replayCapacity)
        repeat(count) { index ->
            val input = parseVector(state["replay.$index.input"], dimensions + actionDimensions + 1)
            val output = parseVector(state["replay.$index.target"], dimensions)
            if (input != null && output != null) replay.addLast(Transition(input, output))
        }
    }

    private fun features(current: DoubleArray, action: DoubleArray): DoubleArray =
        current.copyOf() + action.map { it.coerceIn(-1.0, 1.0) }.toDoubleArray() + doubleArrayOf(1.0)

    private fun forward(input: DoubleArray): DoubleArray =
        DoubleArray(dimensions) { row ->
            weights[row].indices.sumOf { col -> weights[row][col] * input[col] }.coerceIn(-2.0, 2.0)
        }

    private fun trainOne(sample: Transition) {
        val prediction = forward(sample.input)
        for (row in 0 until dimensions) {
            val error = (prediction[row] - sample.target[row]).coerceIn(-0.25, 0.25)
            for (col in sample.input.indices) {
                val decay = if (col == sample.input.lastIndex) 0.0 else weights[row][col] * 0.0001
                weights[row][col] = (weights[row][col] - learningRate *
                    (error * sample.input[col] + decay)).coerceIn(-4.0, 4.0)
            }
        }
    }

    private fun rootMeanSquare(a: DoubleArray, b: DoubleArray): Double {
        val sum = a.indices.sumOf { index ->
            val delta = a[index] - b[index]
            delta * delta
        }
        return kotlin.math.sqrt(sum / dimensions)
    }

    private fun parseVector(raw: String?, expectedSize: Int): DoubleArray? {
        val values = raw?.split(",")?.mapNotNull { it.toDoubleOrNull() } ?: return null
        if (values.size != expectedSize || values.any { !it.isFinite() }) return null
        return values.toDoubleArray()
    }
}

/** Persistent online adaptation driven by real host observations and organ outcomes. */
class LiquidSymbiosisEngine(private val persistenceFile: File? = null) {
    private val strategies = SymbioticOrgan.entries.associateWith { OrganStrategy() }.toMutableMap()
    private var host: LiquidHostState? = null
    private var generation = 0L
    private var lastAdaptation = "Awaiting live phone observations"
    private val predictor = JepaLatentPredictor()
    private var previousLatent: DoubleArray? = null
    private var previousAction = doubleArrayOf(0.0, 0.0, 0.0)
    private var predictionError = 0.0
    private var lastPersistAtMs = 0L

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
        previousAction = doubleArrayOf(0.0, 0.0, 0.0)
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
        val oldContext = old.contextBudget
        val oldBackground = old.backgroundWeight
        adaptOrgan(organ)
        val updated = strategies.getValue(organ)
        previousAction = doubleArrayOf(
            reward,
            ((updated.contextBudget - oldContext) / 4096.0).coerceIn(-1.0, 1.0),
            (updated.backgroundWeight - oldBackground).coerceIn(-1.0, 1.0)
        )
        generation++
        lastAdaptation = "$organ learned from ${if (succeeded) "success" else "failure"}"
        persist(force = true)
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
            val canGrowReasoning = reasoning.successfulRuns >= 3 && reasoning.rewardEma > 0.15
            strategies[SymbioticOrgan.REASONING] = reasoning.copy(
                contextBudget = if (canGrowReasoning) min(4096, reasoning.contextBudget + 64)
                    else reasoning.contextBudget,
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
            predictor.restoreState(p.stringPropertyNames()
                .filter { it.startsWith("jepa.") }
                .associate { it.removePrefix("jepa.") to p.getProperty(it) })
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

    private fun persist(force: Boolean = false) {
        val file = persistenceFile ?: return
        val now = System.currentTimeMillis()
        if (!force && lastPersistAtMs != 0L && now - lastPersistAtMs < 60_000L) return
        runCatching {
            file.parentFile?.mkdirs()
            val p = Properties()
            p.setProperty("generation", generation.toString())
            p.setProperty("lastAdaptation", lastAdaptation)
            predictor.exportState().forEach { (key, value) -> p.setProperty("jepa.$key", value) }
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
            lastPersistAtMs = now
        }
    }
}
