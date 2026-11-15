package com.jarvis.app.symbiosis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

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
