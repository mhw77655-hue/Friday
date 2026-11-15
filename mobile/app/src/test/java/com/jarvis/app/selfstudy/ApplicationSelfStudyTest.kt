package com.jarvis.app.selfstudy

import com.jarvis.app.CapabilityManifest
import com.jarvis.app.selfreconfig.SystemGraph
import com.jarvis.app.symbiosis.HostFingerprint
import com.jarvis.app.symbiosis.HostStudyRecord
import com.jarvis.app.symbiosis.LiquidHostState
import com.jarvis.app.symbiosis.LiquidSymbiosisSnapshot
import com.jarvis.app.symbiosis.OrganStrategy
import com.jarvis.app.symbiosis.SymbioticOrgan
import com.jarvis.app.model.GenerateRequest
import com.jarvis.app.model.GenerateResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationSelfStudyTest {
    private fun graph(): SystemGraph {
        val graph = SystemGraph()
        val ids = listOf(
            "entry.latencyPipeline", "identity.root", "authorization.root",
            "approval.mechanism", "upgrade.provenance", "rollback.mechanism",
            "security.boundaries", "core.recovery", "unreachable.experimental"
        )
        ids.forEach { id ->
            graph.registerNode(SystemGraph.SystemNode(
                id = id,
                name = id,
                organType = SystemGraph.OrganType.CORE
            ))
        }
        listOf(
            "identity.root", "authorization.root", "approval.mechanism",
            "upgrade.provenance", "rollback.mechanism", "security.boundaries",
            "core.recovery"
        ).forEach { graph.markFixedInvariant(it) }
        ids.filter { it != "entry.latencyPipeline" && it != "unreachable.experimental" }
            .forEach { graph.addEdge("entry.latencyPipeline", it) }
        return graph
    }

    @Test fun `study reports live graph gaps and persists a bounded report`() {
        val dir = java.nio.file.Files.createTempDirectory("friday-study-").toFile()
        try {
            val fingerprint = HostFingerprint(
                manufacturer = "test", model = "test", device = "test",
                socModel = "test", board = "test", androidApi = 35,
                totalMemoryMb = 8192, appMemoryClassMb = 256,
                cpuCoreCount = 8, primaryAbi = "arm64-v8a"
            )
            val study = ApplicationSelfStudy(
                graph = graph(),
                reportFile = File(dir, "self-study/latest.properties"),
                capabilitySnapshot = {
                    CapabilityManifest(
                        stage = 1,
                        updated = "test",
                        capabilities = mapOf("voice" to true, "local_decision_tool" to false),
                        knownLimitations = mapOf("streaming_voice" to "not yet complete")
                    )
                },
                hostSnapshot = {
                    HostStudyRecord(
                        fingerprint = fingerprint,
                        sampleCount = 7,
                        minimumAvailableMemoryMb = 1200,
                        averageCpuLoadEmaPercent = 31.5,
                        thermalPressureSamples = 2,
                        lowMemorySamples = 3,
                        memoryPressureEma = 0.3,
                        thermalPressureEma = 0.2,
                        minimumBatteryPercent = 28,
                        maximumObservedProcessPssMb = 1900
                    )
                },
                liquidSnapshot = {
                    LiquidSymbiosisSnapshot(
                        generation = 1,
                        host = LiquidHostState(availableMemoryMb = 3000, cpuLoad = 20.0,
                            thermalPressure = 0.0, batteryPercent = 80, charging = false),
                        organs = mapOf(SymbioticOrgan.REASONING to OrganStrategy(failedRuns = 2)),
                        lastAdaptation = "test",
                        lastPredictionError = 0.1
                    )
                },
                sourceIndexSnapshot = {
                    ApplicationSourceIndex.Summary(
                        schemaVersion = 1,
                        sourceRevision = "test-revision",
                        sourceFileCount = 42,
                        declaredSymbolCount = 120,
                        packageCount = 9
                    )
                }
            )
            val report = study.study(nowMs = 1234L)
            assertEquals(1234L, report.createdAtMs)
            assertEquals(listOf("unreachable.experimental"), report.unreachableNodeIds)
            assertEquals(listOf("local_decision_tool"), report.declaredMissingCapabilities)
            assertEquals(listOf("streaming_voice"), report.knownLimitationIds)
            assertEquals(7L, report.hostSamples)
            assertEquals(fingerprint.stableKey, report.hostFingerprint)
            assertEquals(1200L, report.minimumAvailableMemoryMb)
            assertEquals(31.5, report.averageCpuLoadEmaPercent, 0.001)
            assertEquals(2L, report.thermalPressureSamples)
            assertEquals(3L, report.lowMemorySamples)
            assertEquals(512L, report.additionalMemoryReserveMb)
            assertEquals(1900L, report.maximumObservedProcessPssMb)
            assertEquals(28, report.minimumBatteryPercent)
            assertEquals(1L, report.liquidGeneration)
            assertEquals("test", report.liquidLastAdaptation)
            assertEquals(0.1, report.liquidPredictionError, 0.001)
            assertTrue(report.organStrategies["REASONING"]!!.contains("failure=2"))
            assertEquals(2L, report.organFailureCounts["REASONING"])
            assertEquals("test-revision", report.sourceRevision)
            assertEquals(42, report.sourceFileCount)
            assertEquals(120, report.sourceSymbolCount)
            assertEquals(9, report.sourcePackageCount)
            assertFalse(report.structurallyHealthy)
            assertTrue(File(dir, "self-study/latest.properties").isFile)
            assertEquals(report, study.latestReport())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun `Friday model reflection creates and restores a bounded self-build proposal`() = kotlinx.coroutines.runBlocking {
        val dir = java.nio.file.Files.createTempDirectory("friday-reflection-").toFile()
        try {
            val reportFile = File(dir, "self-study/latest.properties")
            fun createStudy() = ApplicationSelfStudy(
                graph = graph(),
                reportFile = reportFile,
                capabilitySnapshot = { null },
                hostSnapshot = { null },
                liquidSnapshot = { null },
                sourceIndexSnapshot = {
                    ApplicationSourceIndex.Summary(1, "revision-42", 10, 30, 4)
                }
            )
            val study = createStudy()
            val report = study.study(nowMs = 100L)
            var prompt = ""
            val reflection = study.reflect(
                report = report,
                complete = { request: GenerateRequest ->
                    prompt = request.messages.last().content
                    GenerateResult(
                        content = """
                            {
                              "summary":"The capability map shows a missing local host profile tool.",
                              "findings":["Source index is structural metadata, not source comprehension."],
                              "next_actions":["Inspect measured memory reserve before tuning."],
                              "candidate_capability":"host_profile_summary",
                              "candidate_description":"Summarize validated host resource observations without changing settings.",
                              "constraints":["pure function","no device side effects"],
                              "evidence_gaps":["No on-device benchmark is attached."]
                            }
                        """.trimIndent()
                    )
                },
                nowMs = 200L
            )

            assertTrue(prompt.contains("source_revision=revision-42"))
            assertTrue(prompt.contains("Analyze only this evidence"))
            assertEquals("host_profile_summary", reflection!!.candidateCapability)
            assertTrue(reflection.hasBuildableCandidate)
            assertEquals(1, reflection.findings.size)
            assertTrue(File(dir, "self-study/latest-reflection.properties").isFile)

            val restored = createStudy().latestReflection()
            assertEquals(reflection, restored)
        } finally {
            dir.deleteRecursively()
        }
    }
}
