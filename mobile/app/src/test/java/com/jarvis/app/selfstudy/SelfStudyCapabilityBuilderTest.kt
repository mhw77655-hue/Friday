package com.jarvis.app.selfstudy

import com.jarvis.app.builder.BuildResult
import com.jarvis.app.builder.ToolSpecification
import com.jarvis.app.model.ResourceSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SelfStudyCapabilityBuilderTest {
    private fun reflection(
        capability: String? = "text_token_counter",
        description: String? = "Count whitespace-separated tokens in a supplied string.",
        revision: String = "source-r1"
    ) = ApplicationSelfStudy.Reflection(
        createdAtMs = 1L,
        sourceRevision = revision,
        summary = "A bounded proposal.",
        findings = emptyList(),
        nextActions = emptyList(),
        candidateCapability = capability,
        candidateDescription = description,
        constraints = emptyList(),
        evidenceGaps = emptyList()
    )

    private fun resources(
        memory: Long = 1800L,
        thermal: Int = 0,
        battery: Int = 80,
        charging: Boolean = false
    ) = object : ResourceSnapshot {
        override val availableMemoryMb = memory
        override val cpuLoadPercent = 20.0
        override val thermalLevel = thermal
        override val batteryPercent = battery
        override val isCharging = charging
    }

    @Test fun `self-study proposals become bounded pure synth specifications`() {
        val spec = SelfStudyCapabilityPolicy.specification(reflection())!!

        assertEquals("text_token_counter", spec.capability)
        assertEquals(16L, spec.maxMemoryMb)
        assertEquals(2.0, spec.maxCpuPercent, 0.001)
        assertFalse(spec.researchFirst)
        assertTrue(spec.constraints.any { it.contains("Pure deterministic transformation") })
        assertTrue(spec.constraints.any { it.contains("No Android APIs") })
    }

    @Test fun `self-study cannot propose phone control or privileged capabilities`() {
        assertNull(
            SelfStudyCapabilityPolicy.specification(
                reflection("phone_control", "Control the Android phone using accessibility.")
            )
        )
        assertNull(
            SelfStudyCapabilityPolicy.specification(
                reflection("text_token_counter", "Read files and send messages over the network.")
            )
        )
    }

    @Test fun `invalid or incomplete reflections are not sent to the builder`() {
        assertNull(SelfStudyCapabilityPolicy.specification(reflection(null, null)))
        assertNull(SelfStudyCapabilityPolicy.specification(reflection("Bad Name", "A safe string helper.")))
        assertNull(SelfStudyCapabilityPolicy.specification(reflection("safe_helper", "   ")))
    }

    @Test fun `resource pressure prevents a self-build attempt`() = runBlocking {
        val file = File(Files.createTempDirectory("friday-self-build").toFile(), "attempt.properties")
        var calls = 0
        val controller = SelfStudyCapabilityBuilder(
            stateFile = file,
            build = { spec ->
                calls++
                BuildResult(spec, built = true, status = "PROMOTED", providerId = "p", message = "promoted")
            }
        )

        assertNull(controller.buildIfUseful(reflection(), resources(memory = 1200L)))
        assertNull(controller.buildIfUseful(reflection(), resources(thermal = 1)))
        assertNull(controller.buildIfUseful(reflection(), resources(battery = 15)))
        assertEquals(0, calls)
    }

    @Test fun `promoted capability is persisted and never rebuilt for the same source revision`() = runBlocking {
        val directory = Files.createTempDirectory("friday-self-build").toFile()
        val file = File(directory, "attempt.properties")
        var calls = 0
        val controller = SelfStudyCapabilityBuilder(
            stateFile = file,
            build = { spec ->
                calls++
                BuildResult(
                    spec = spec,
                    built = true,
                    status = "PROMOTED",
                    providerId = "provider-1",
                    genomeId = "genome-1",
                    fitness = 0.93,
                    message = "promoted and registered"
                )
            }
        )

        val first = controller.buildIfUseful(reflection(), resources())!!
        val restarted = SelfStudyCapabilityBuilder(
            stateFile = file,
            build = { spec ->
                calls++
                BuildResult(spec, built = true, status = "PROMOTED", message = "duplicate")
            }
        )
        val second = restarted.buildIfUseful(reflection(), resources())

        assertTrue(first.promoted)
        assertEquals("provider-1", first.providerId)
        assertEquals("genome-1", first.genomeId)
        assertEquals(first, second)
        assertEquals(1, calls)
    }

    @Test fun `failed candidate is rate limited and may be retried after cooldown`() = runBlocking {
        val file = File(Files.createTempDirectory("friday-self-build").toFile(), "attempt.properties")
        var now = 10_000L
        var calls = 0
        val controller = SelfStudyCapabilityBuilder(
            stateFile = file,
            build = { spec ->
                calls++
                BuildResult(spec, built = false, status = "EVOLUTION_REJECTED", message = "tests rejected it")
            },
            nowMs = { now }
        )

        val first = controller.buildIfUseful(reflection(), resources())!!
        assertFalse(first.promoted)
        assertNull(controller.buildIfUseful(reflection(), resources()))
        now += 6L * 60L * 60L * 1000L + 1L
        controller.buildIfUseful(reflection(), resources())

        assertEquals(2, calls)
    }
}
