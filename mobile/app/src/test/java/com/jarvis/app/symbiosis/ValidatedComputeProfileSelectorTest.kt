package com.jarvis.app.symbiosis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatedComputeProfileSelectorTest {
    private val request = ComputeProfileRequest(
        workloadClass = "conversation",
        qualitySuiteId = "friday-en-v1",
        minimumQualityScore = 0.94,
        minimumValidationRuns = 5,
        requestedContextTokens = 2048,
        availableMemoryMb = 3000,
        protectedReserveMb = 900
    )

    private fun profile(
        id: String,
        latency: Long,
        quality: Double = 0.96,
        memory: Long = 1500,
        enabled: Boolean = true,
        suite: String = "friday-en-v1",
        regressions: Int = 0,
        runs: Int = 8,
        context: Int = 4096
    ) = ValidatedComputeProfile(
        profileId = id,
        modelId = "resident",
        modelRevision = "sha256:revision",
        quantization = "Q4_K_M",
        backend = "llama.cpp-cpu",
        workloadClass = "conversation",
        qualitySuiteId = suite,
        qualityScore = quality,
        criticalRegressionCount = regressions,
        validationRuns = runs,
        contextTokens = context,
        p95LatencyMs = latency,
        peakMemoryMb = memory,
        energyMilliJoules = latency * 10,
        enabled = enabled
    )

    @Test
    fun `chooses fastest profile only among quality and memory qualified candidates`() {
        val selected = ValidatedComputeProfileSelector.select(
            listOf(
                profile("fast-but-low-quality", 100, quality = 0.90),
                profile("fast-but-too-large", 110, memory = 2200),
                profile("slower-valid", 240),
                profile("fast-valid", 180)
            ),
            request
        )
        assertEquals("fast-valid", selected?.profileId)
    }

    @Test
    fun `rejects profile with any critical regression`() {
        val selected = ValidatedComputeProfileSelector.select(
            listOf(profile("unsafe-regression", 80, regressions = 1)),
            request
        )
        assertNull(selected)
    }

    @Test
    fun `does not compare scores from a different quality suite`() {
        val selected = ValidatedComputeProfileSelector.select(
            listOf(profile("other-suite", 80, suite = "different-suite")),
            request
        )
        assertNull(selected)
    }

    @Test
    fun `requires enough repeated validation runs and requested context`() {
        val selected = ValidatedComputeProfileSelector.select(
            listOf(
                profile("under-tested", 80, runs = 2),
                profile("short-context", 90, context = 1024)
            ),
            request
        )
        assertNull(selected)
    }

    @Test
    fun `never relaxes quality threshold when memory reserve leaves no room`() {
        val constrained = request.copy(availableMemoryMb = 1800, protectedReserveMb = 900)
        val selected = ValidatedComputeProfileSelector.select(
            listOf(profile("would-fit-only-by-breaking-reserve", 80, memory = 1000)),
            constrained
        )
        assertNull(selected)
    }

    @Test
    fun `disabled profiles are never selected`() {
        val selected = ValidatedComputeProfileSelector.select(
            listOf(profile("disabled", 80, enabled = false)),
            request
        )
        assertNull(selected)
    }
}
