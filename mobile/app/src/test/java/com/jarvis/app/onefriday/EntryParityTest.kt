package com.jarvis.app.onefriday

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.model.ResourceSnapshot
import com.jarvis.app.termux.TermuxJarvisServer
import com.jarvis.app.voice.VoiceForgeHealth
import com.jarvis.app.voice.VoiceForgeSynthesisRequest
import com.jarvis.app.voice.VoiceForgeSynthesisResponse
import com.jarvis.app.voice.VoiceForgeSynthesizer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ONE-2-COMPOSITION AC1: entry-point parity.
 *
 * The Android and JVM entry points must delegate cognitive construction to the
 * same TurnPathAssembly. Their only legitimate differences are the PlatformPorts
 * and the stores/sinks those ports supply. This test checks both source entry
 * points and independently builds two roots with different host supplies.
 */
class EntryParityTest {
    private class SilentSpeech : VoiceForgeSynthesizer {
        override suspend fun synthesize(request: VoiceForgeSynthesisRequest) =
            VoiceForgeSynthesisResponse.Failed("unused")
        override suspend fun health() = VoiceForgeHealth(healthy = false, error = "unused")
    }

    private class NoMemories : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }

    private fun ports(label: String, now: Long): JvmPlatformPorts {
        val root = createTempDir(prefix = "friday-entry-parity-$label-")
        return JvmPlatformPorts(
            storageDir = root,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            stores = JvmPlatformStores(
                graphStore = TermuxJarvisServer.InMemoryGraph(),
                embeddingProvider = TermuxJarvisServer.DeterministicEmbedding(),
                memoryStore = NoMemories(),
                backend = FakeModelBackend(),
                voiceSynthesizer = SilentSpeech()
            ),
            snapshot = { ResourceSnapshot.alwaysHealthy() },
            clock = { now },
            humanCoreStorage = FileStorage(File(root, "human-core")),
            turnWork = { it() },
            later = { _, _ -> },
            fastAckSink = { },
            fullReplySink = { },
            turnLogSink = { _, _ -> },
            warmer = { },
            session = { null },
            submittedSignal = { },
            ackSignal = { },
            firstSegmentSignal = { },
            utteranceDoneSignal = { },
            failureSink = { },
            failureSurface = FailureSurface()
        )
    }

    @Test
    fun `both production entry points delegate to the one root and only supply platform ports`() {
        val androidEntry = source("src/main/java/com/jarvis/app/JarvisEngine.kt")
        val jvmEntry = source("src/main/java/com/jarvis/app/termux/TermuxJarvisServer.kt")

        assertEquals(1, Regex("TurnPathAssembly\\.assemble\\(").findAll(androidEntry).count())
        assertEquals(1, Regex("TurnPathAssembly\\.assemble\\(").findAll(jvmEntry).count())
        assertTrue(androidEntry.contains("AndroidPlatformPorts("))
        assertTrue(jvmEntry.contains("JvmPlatformPorts("))

        val competingConstructors = listOf(
            Regex("\\bModelManager\\s*\\("),
            Regex("\\bCognitiveEngine\\s*\\("),
            Regex("\\bLatencyPipeline\\s*\\(")
        )
        for (pattern in competingConstructors) {
            assertFalse("Android entry constructed a second turn-path component: $pattern",
                pattern.containsMatchIn(androidEntry))
            assertFalse("JVM entry constructed a second turn-path component: $pattern",
                pattern.containsMatchIn(jvmEntry))
        }
    }

    @Test
    fun `different host supplies produce the same root-owned component fingerprint`() {
        val first = TurnPathAssembly.assemble(ports("first", 1_000L))
        val second = TurnPathAssembly.assemble(ports("second", 9_000L))

        assertEquals(
            "Only adapter-supplied leaves may differ; every root-owned turn component " +
                "must have the same concrete implementation on both hosts.",
            fingerprint(first),
            fingerprint(second)
        )
    }

    @Test
    fun `a completed JVM composition turn changes real Human Core interaction state`() {
        val server = com.jarvis.app.termux.TermuxJarvisServer(
            port = 0,
            backendOverride = FakeModelBackend(),
            voiceForgeSynthesizerOverride = SilentSpeech(),
            dataDir = createTempDir(prefix = "friday-human-core-turn-")
        )
        val before = HumanCore.snapshot()?.totalInteractions ?: 0L

        server.pipeline.onUserInput("I prefer concise answers.")
        server.pipeline.onReplyReady("I will keep the answer concise.")

        val deadline = System.nanoTime() + 3_000_000_000L
        var after = HumanCore.snapshot()?.totalInteractions ?: 0L
        while (after <= before && System.nanoTime() < deadline) {
            Thread.sleep(10)
            after = HumanCore.snapshot()?.totalInteractions ?: 0L
        }

        assertTrue(
            "The JVM composition must run the real Human Core Integration Pass; " +
                "a no-op completeExchange leaves totalInteractions unchanged (before=$before, after=$after).",
            after > before
        )
    }

    private fun fingerprint(assembly: TurnPathAssembly): List<String> =
        assembly.componentFingerprint.toSortedMap().map { (name, implementation) ->
            "$name=$implementation"
        }

    private fun source(relative: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, relative),
            File(cwd, "mobile/app/$relative"),
            File(cwd.parentFile, relative),
            File(cwd.parentFile, "mobile/app/$relative")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Could not locate production source $relative from ${cwd.absolutePath}")
        return file.readText()
    }
}
