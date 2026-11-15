package com.jarvis.app.selfstudy

import com.jarvis.app.CapabilityManifest
import com.jarvis.app.selfreconfig.SystemGraph
import com.jarvis.app.symbiosis.HostFingerprint
import com.jarvis.app.symbiosis.HostStudyRecord
import com.jarvis.app.symbiosis.LiquidHostState
import com.jarvis.app.symbiosis.LiquidSymbiosisSnapshot
import com.jarvis.app.symbiosis.OrganStrategy
import com.jarvis.app.symbiosis.SymbioticOrgan
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
                hostSnapshot = { HostStudyRecord(fingerprint, sampleCount = 7) },
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
}
