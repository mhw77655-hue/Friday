package com.jarvis.app.symbiosis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Properties

class LiquidSymbiosisEngineTest {
    @Test
    fun `real host pressure changes future organ strategy`() {
        val engine = LiquidSymbiosisEngine()
        val before = engine.strategy(SymbioticOrgan.REASONING).contextBudget
        val after = engine.observeHost(
            LiquidHostState(
                availableMemoryMb = 900,
                cpuLoad = 91.0,
                thermalPressure = 0.8,
                batteryPercent = 40,
                charging = false
            )
        )
        assertTrue(after.generation > 0)
        assertTrue(engine.strategy(SymbioticOrgan.REASONING).contextBudget < before)
        assertEquals(0.0, engine.strategy(SymbioticOrgan.BACKGROUND).backgroundWeight, 0.0)
    }

    @Test
    fun `repeated failures teach an organ to reduce its operating envelope`() {
        val engine = LiquidSymbiosisEngine()
        repeat(2) {
            engine.recordOutcome(
                organ = SymbioticOrgan.SPEECH_OUTPUT,
                succeeded = false,
                latencyMs = 1800,
                peakMemoryMb = 320
            )
        }
        val strategy = engine.strategy(SymbioticOrgan.SPEECH_OUTPUT)
        assertTrue(strategy.contextBudget < 2048)
        assertEquals(1, strategy.concurrency)
        assertEquals(0.0, strategy.backgroundWeight, 0.0)
        assertEquals(320L, strategy.memoryPeakMb)
    }

    @Test
    fun `learned reasoning failures tighten real admission and host pressure pauses background work`() {
        val engine = LiquidSymbiosisEngine()
        val initialReserve = engine.additionalAdmissionReserveMb(SymbioticOrgan.REASONING)
        assertTrue(engine.backgroundWorkAllowed())

        repeat(2) {
            engine.recordOutcome(
                organ = SymbioticOrgan.REASONING,
                succeeded = false,
                latencyMs = 2400,
                peakMemoryMb = 900
            )
        }
        assertTrue(engine.additionalAdmissionReserveMb(SymbioticOrgan.REASONING) > initialReserve)

        engine.observeHost(
            LiquidHostState(
                availableMemoryMb = 900,
                cpuLoad = 92.0,
                thermalPressure = 0.8,
                batteryPercent = 30,
                charging = false
            )
        )
        assertFalse(engine.backgroundWorkAllowed())

        engine.observeHost(
            LiquidHostState(
                availableMemoryMb = 3200,
                cpuLoad = 20.0,
                thermalPressure = 0.1,
                batteryPercent = 80,
                charging = true
            )
        )
        assertTrue(engine.backgroundWorkAllowed())
    }

    @Test
    fun `persistent strategy survives runtime reconstruction`() {
        val file = File(Files.createTempDirectory("friday-liquid").toFile(), "liquid.properties")
        val first = LiquidSymbiosisEngine(file)
        first.observeHost(LiquidHostState(availableMemoryMb = 900, cpuLoad = 90.0,
            thermalPressure = 0.9, batteryPercent = 20, charging = false))
        first.recordOutcome(SymbioticOrgan.REASONING, false, 2200, 700)

        val restored = LiquidSymbiosisEngine(file)
        assertEquals(first.strategy(SymbioticOrgan.REASONING), restored.strategy(SymbioticOrgan.REASONING))
        assertEquals(first.snapshot().generation, restored.snapshot().generation)
    }

    @Test
    fun `state replacement keeps a complete durable snapshot and removes temporary file`() {
        val directory = Files.createTempDirectory("friday-atomic-state").toFile()
        val destination = File(directory, "state.properties")
        val first = Properties().apply { setProperty("generation", "1") }
        atomicWriteProperties(destination, first, "first snapshot")

        val second = Properties().apply {
            setProperty("generation", "2")
            setProperty("strategy", "constrained")
        }
        atomicWriteProperties(destination, second, "replacement snapshot")

        val restored = Properties()
        destination.inputStream().use(restored::load)
        assertEquals("2", restored.getProperty("generation"))
        assertEquals("constrained", restored.getProperty("strategy"))
        assertFalse(File(directory, "state.properties.tmp").exists())
    }

    @Test
    fun `JEPA predictor parameters can be restored after process death`() {
        val first = JepaLatentPredictor()
        val current = doubleArrayOf(0.8, 0.2, 0.1, 0.8, 1.0, 0.5)
        val next = doubleArrayOf(0.6, 0.5, 0.4, 0.7, 1.0, 0.4)
        first.observe(current, -0.5, next)
        val restored = JepaLatentPredictor()
        restored.restoreState(first.exportState())
        assertEquals(first.sampleCount(), restored.sampleCount())
        assertEquals(first.predictionErrorEma(), restored.predictionErrorEma(), 0.0)
        val a = first.predict(current, -0.5)
        val b = restored.predict(current, -0.5)
        assertTrue(a.indices.all { kotlin.math.abs(a[it] - b[it]) < 1e-9 })
    }

    @Test
    fun `action conditioned JEPA distinguishes operating actions`() {
        val predictor = JepaLatentPredictor(replayCapacity = 24)
        val state = doubleArrayOf(0.5, 0.4, 0.2, 0.8, 1.0, 0.5)
        val reduceLoad = doubleArrayOf(-0.8, -0.2, -0.5)
        val increaseCapacity = doubleArrayOf(0.8, 0.2, 0.5)
        val lowTarget = doubleArrayOf(0.3, 0.2, 0.1, 0.8, 1.0, 0.4)
        val highTarget = doubleArrayOf(0.8, 0.6, 0.1, 0.8, 1.0, 0.8)
        repeat(30) {
            predictor.observe(state, reduceLoad, lowTarget)
            predictor.observe(state, increaseCapacity, highTarget)
        }
        val low = predictor.predict(state, reduceLoad)
        val high = predictor.predict(state, increaseCapacity)
        assertTrue("different actions should produce different latent futures",
            low.indices.any { kotlin.math.abs(low[it] - high[it]) > 0.001 })
        assertEquals(24, predictor.replaySize())
    }

    @Test
    fun `JEPA latent rollout is bounded and predicts a sequence`() {
        val predictor = JepaLatentPredictor()
        val state = doubleArrayOf(0.5, 0.4, 0.2, 0.8, 1.0, 0.5)
        predictor.observe(state, doubleArrayOf(0.1, 0.0, 0.1),
            doubleArrayOf(0.6, 0.4, 0.2, 0.8, 1.0, 0.6))
        val rollout = predictor.rollout(state, listOf(
            doubleArrayOf(0.1, 0.0, 0.1),
            doubleArrayOf(0.1, 0.0, 0.1)
        ))
        assertEquals(2, rollout.size)
        assertEquals(6, rollout.last().size)
        val rejected = runCatching {
            predictor.rollout(state, List(9) { doubleArrayOf(0.0, 0.0, 0.0) })
        }
        assertTrue(rejected.isFailure)
    }

    @Test
    fun `JEPA replay window persists with predictor parameters`() {
        val first = JepaLatentPredictor()
        val state = doubleArrayOf(0.8, 0.2, 0.1, 0.8, 1.0, 0.5)
        first.observe(state, doubleArrayOf(0.2, -0.1, 0.0),
            doubleArrayOf(0.6, 0.5, 0.4, 0.7, 1.0, 0.4))
        val restored = JepaLatentPredictor()
        restored.restoreState(first.exportState())
        assertEquals(first.replaySize(), restored.replaySize())
        assertEquals(first.sampleCount(), restored.sampleCount())
        val action = doubleArrayOf(0.2, -0.1, 0.0)
        val a = first.predict(state, action)
        val b = restored.predict(state, action)
        assertTrue(a.indices.all { kotlin.math.abs(a[it] - b[it]) < 1e-9 })
    }

    @Test
    fun `predictive latent model learns from consecutive host states`() {
        val predictor = JepaLatentPredictor()
        val start = doubleArrayOf(0.8, 0.2, 0.1, 0.8, 1.0, 0.5)
        val next = doubleArrayOf(0.6, 0.5, 0.4, 0.7, 1.0, 0.4)
        val error = predictor.observe(start, -0.5, next)
        assertTrue(error >= 0.0)
        assertEquals(1L, predictor.sampleCount())
        assertEquals(6, predictor.predict(start, -0.5).size)
    }
}
