package com.jarvis.app.onefriday

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.model.ResourceSnapshot
import com.jarvis.app.termux.TermuxJarvisServer
import com.jarvis.app.voice.VoiceForgeHealth
import com.jarvis.app.voice.VoiceForgeSynthesisRequest
import com.jarvis.app.voice.VoiceForgeSynthesisResponse
import com.jarvis.app.voice.VoiceForgeSynthesizer
import java.io.File
import java.lang.reflect.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ONE-2-COMPOSITION, AC1 and AC5: ONE composition root, exercised through the
 * two entry points and two different adapters.
 *
 * AC1 asks that "both entry points obtain their whole object graph from the
 * same composition root and differ only through the platform adapter", with a
 * test that fails if the constructed component set differs by anything other
 * than adapter-provided parts.
 *
 * The JVM cannot run the Android runtime, so the Android HALF of that is proven
 * structurally (below, test 1): the Android entry point, like the JVM entry
 * point, contains exactly one call to `TurnPathAssembly.assemble(...)` and
 * constructs none of the organs itself, so its graph is the root's graph by
 * construction. The functional HALF (tests 2) builds two real compositions —
 * the production JVM host and an independently configured test adapter — and
 * compares the component SET of both assemblies field by field; anything that
 * differs must be on the documented adapter-provided list.
 *
 * AC5 asks for two adapter implementations (Android + JVM-test); test 3 pins
 * both classes against the shared [PlatformPorts] seam.
 */
class EntryParityTest {

    // ── AC1: the graph comes from the one root, on both entry points ─────────

    @Test
    fun `AC1 both entry points obtain the whole object graph from the one composition root`() {
        val root = sourceRoot()

        listOf(
            "JarvisEngine.kt" to "the Android entry point",
            "termux/TermuxJarvisServer.kt" to "the JVM/Termux entry point"
        ).forEach { (relative, label) ->
            val src = File(root, relative).readText()
            assertEquals(
                "$label must obtain its graph once, from TurnPathAssembly.assemble",
                1,
                Regex("""TurnPathAssembly\.assemble\(""").findAll(src).count()
            )
            val organConstructors = listOf(
                "CognitiveEngine(",
                "LatencyPipeline(",
                "BlendedMemoryRetriever(",
                "UserMentalStateEstimator(",
                "FusionLayerTier1(",
                "AnchorEngine(",
                "CapabilityFabric(",
                "ContinuityLaw("
            )
            organConstructors.forEach { ctor ->
                assertFalse(
                    "$label must not build the graph itself: it should not construct $ctor; " +
                        "that organ's wiring belongs to the composition root",
                    src.contains(ctor)
                )
            }
        }
    }

    // ── AC1: two hosts through the root, identical component sets ────────────

    @Test
    fun `AC1 two hosts built through the root construct the same components, differing only where the adapter provides them`() {
        val hostAssembly = assemblyOf(
            TermuxJarvisServer(
                port = 0,
                backendOverride = FakeModelBackend(),
                dataDir = compositionDir("parity-host")
            )
        )
        val testAssembly = TurnPathAssembly.assemble(TestAdapter().ports())

        val hostComponents = componentInventory(hostAssembly)
        val testComponents = componentInventory(testAssembly)

        assertTrue(
            "a composition must carry every component the other carries, or the stories " +
                "that read them disagree. Missing/extra: " +
                (hostComponents.keys xor testComponents.keys),
            hostComponents.keys == testComponents.keys
        )

        val differing = hostComponents.keys
            .filter { hostComponents.getValue(it) != testComponents.getValue(it) }
        assertTrue(
            "components may differ only when the adapter supplies them. Differing: $differing",
            differing.all { it in ADAPTER_PROVIDED }
        )
    }

    @Test
    fun `AC1 the adapter-provided list still names real components of the root`() {
        val root = TurnPathAssembly::class.java
        val fieldNames = root.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        assertTrue(
            "every documented adapter-provided part must still be a component of the root",
            ADAPTER_PROVIDED.all { it in fieldNames }
        )
    }

    /** The parts whose class is legitimately chosen by the adapter host. */
    private val ADAPTER_PROVIDED = setOf(
        "ports",
        "failureSurface",
        "graphStore",
        "embeddingProvider",
        "memoryStore",
        "backend",
        "capabilityRegistry",
        "voiceForgeBackend"
    )

    // ── AC5: the adapter seam has an Android and a JVM implementation ────────

    @Test
    fun `AC5 the platform adapter has two implementations, Android and JVM`() {
        val seam = PlatformPorts::class.java
        listOf(
            AndroidPlatformPorts::class.java to "AndroidPlatformPorts",
            JvmPlatformPorts::class.java to "JvmPlatformPorts"
        ).forEach { (impl, name) ->
            assertTrue(
                "$name must be an implementation of the platform adapter seam",
                seam.isAssignableFrom(impl)
            )
        }
        assertNotEquals(
            "two adapters, not one class worn twice",
            AndroidPlatformPorts::class.java,
            JvmPlatformPorts::class.java
        )
    }

    // ── machinery ────────────────────────────────────────────────────────────

    private class SilentSpeech : VoiceForgeSynthesizer {
        override suspend fun synthesize(
            request: VoiceForgeSynthesisRequest
        ): VoiceForgeSynthesisResponse =
            throw UnsupportedOperationException("this test never synthesizes audio")

        override suspend fun health(): VoiceForgeHealth =
            throw UnsupportedOperationException("this test never probes the speech host")
    }

    private class NoMemories : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }

    /** A second, independently configured adapter: injected clock, silent speech. */
    private inner class TestAdapter {
        fun ports(): JvmPlatformPorts = JvmPlatformPorts(
            storageDir = compositionDir("parity-test"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            stores = JvmPlatformStores(
                graphStore = TermuxJarvisServer.InMemoryGraph(),
                embeddingProvider = TermuxJarvisServer.DeterministicEmbedding(),
                memoryStore = NoMemories(),
                backend = FakeModelBackend(),
                voiceSynthesizer = SilentSpeech()
            ),
            snapshot = { ResourceSnapshot.alwaysHealthy() },
            clock = { 1_000L },
            humanCoreStorage = com.jarvis.app.humancore.store.FileStorage(compositionDir("parity-test-hc")),
            turnWork = { },
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
            failureSurface = com.jarvis.app.failure.FailureSurface()
        )
    }

    private fun assemblyOf(server: TermuxJarvisServer): TurnPathAssembly {
        val field = TermuxJarvisServer::class.java.getDeclaredField("assembly")
        field.isAccessible = true
        val assembly = field.get(server) as TurnPathAssembly
        assertNotNull("the JVM host must hold a real composition", assembly)
        return assembly
    }

    private fun componentInventory(assembly: TurnPathAssembly): Map<String, String> =
        TurnPathAssembly::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }
            .associate { it.name to (it.get(assembly)?.javaClass?.name ?: "null") }

    /** The same layout the allowlist test trusts: module dir first, repo root after. */
    private fun sourceRoot(): File {
        val rel = "src/main/java/com/jarvis/app"
        val candidates = listOf(
            File(rel),
            File(System.getProperty("user.dir"), rel),
            File(System.getProperty("user.dir"), "mobile/app/$rel")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate the turn-path source root. Searched user.dir=" + System.getProperty("user.dir"))
    }
}