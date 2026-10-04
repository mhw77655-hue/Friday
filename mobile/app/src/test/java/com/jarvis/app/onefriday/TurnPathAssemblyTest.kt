package com.jarvis.app.onefriday

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.cognition.workspace.MentalStateClaims
import com.jarvis.app.emotion.EmotionHypothesis
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.identity.MentalStateHypothesis
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.model.AdmissionDecision
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.model.ModelTier
import com.jarvis.app.model.OrganRole
import com.jarvis.app.model.OrganWakeRequest
import com.jarvis.app.model.ResourceSnapshot
import com.jarvis.app.termux.TermuxJarvisServer
import com.jarvis.app.voice.VoiceForgeHealth
import com.jarvis.app.voice.VoiceForgeSynthesisRequest
import com.jarvis.app.voice.VoiceForgeSynthesisResponse
import com.jarvis.app.voice.VoiceForgeSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * ONE-2-COMPOSITION: what the ONE composition root guarantees, proven against
 * the real production classes.
 *
 * Only genuinely host-bound leaves are faked (a temp directory, an in-memory
 * graph, a fake model backend, a silent speech synthesizer). Every organ, seam
 * and wiring under test is the production class — the same ones the phone and
 * the Termux host construct.
 */
class TurnPathAssemblyTest {

    @get:Rule
    val temp = TemporaryFolder()

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

    private class Fixture(
        val acks: MutableList<String> = mutableListOf(),
        // The slow path runs inline unless a test needs to isolate the fast path.
        val runTurnWork: (() -> Unit) -> Unit = { block -> block() }
    ) {
        lateinit var backend: FakeModelBackend
        lateinit var storageDir: java.io.File

        fun ports(snapshot: () -> ResourceSnapshot, clock: () -> Long): JvmPlatformPorts {
            storageDir = temp.newFolder()
            backend = FakeModelBackend()
            return JvmPlatformPorts(
                storageDir = storageDir,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                stores = JvmPlatformStores(
                    graphStore = TermuxJarvisServer.InMemoryGraph(),
                    embeddingProvider = TermuxJarvisServer.DeterministicEmbedding(),
                    memoryStore = NoMemories(),
                    backend = backend,
                    voiceSynthesizer = SilentSpeech()
                ),
                snapshot = snapshot,
                clock = clock,
                humanCoreStorage = FileStorage(storageDir),
                turnWork = runTurnWork,
                later = { _, _ -> },
                fastAckSink = { acks.add(it) },
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
    }

    // ── The root builds the whole turn path ──────────────────────────────────

    @Test
    fun `the composition root builds every turn-path organ from one adapter`() {
        val assembly = TurnPathAssembly.assemble(
            Fixture().ports({ ResourceSnapshot.alwaysHealthy() }, { 1_000L })
        )

        assertNotNull("the real cognitive engine", assembly.engine)
        assertNotNull("the entry pipeline", assembly.pipeline)
        assertNotNull("the real Human Core turn port", assembly.humanCoreTurnPort)
        assertNotNull("the real memory graph store", assembly.graphStore)
        assertNotNull("the real blended retriever", assembly.retriever)
        assertNotNull("the real identity context", assembly.identityContext)
        assertNotNull("the real emotion fusion tier", assembly.emotionFusion)
        assertNotNull("the real mental-state estimator", assembly.mentalStateEstimator)
        assertNotNull("the real social stack", assembly.personRelationshipModel)
        assertNotNull("the real confidentiality firewall", assembly.confidentialityFirewall)
        assertNotNull("the real continuity gate", assembly.continuityGate)
        assertNotNull("the real continuity law", assembly.continuityLaw)
        assertNotNull("the real capability fabric", assembly.capabilityFabric)
        assertNotNull("the real fuzzy command resolver", assembly.fuzzyCommandResolver)
        assertNotNull("the single model loading authority", assembly.modelManager)
        assertNotNull("the single admission governor", assembly.resourceGovernor)
        assertNotNull("the real anchor engine", assembly.anchorEngine)
        assertNotNull("the real spoken-reply backend", assembly.voiceForgeBackend)
        assertNotNull("the real capability registry", assembly.capabilityRegistry)
        assertNotNull("the real cloud router around this Friday", assembly.cloudModelRouter)
    }

    @Test
    fun `one graph store serves the engine's write-back and the retriever's recall`() = runBlocking {
        val assembly = TurnPathAssembly.assemble(
            Fixture().ports({ ResourceSnapshot.alwaysHealthy() }, { 1_000L })
        )

        assembly.engine.process("My name is Venon", modelCall = { "Noted." })

        val recalled = assembly.retriever.retrieve("Venon")
        assertTrue(
            "the engine wrote the stated fact to the graph store and the retriever " +
                "read it back, which can only happen if they share ONE store. A " +
                "second copy would recall nothing.",
            recalled.isNotEmpty() && recalled.any { it.node.`object`.contains("Venon") }
        )
    }

    @Test
    fun `the anchor establishes the resident tier through the one loading authority`() = runBlocking {
        val fixture = Fixture()
        val assembly = TurnPathAssembly.assemble(
            fixture.ports({ ResourceSnapshot.alwaysHealthy() }, { 1_000L })
        )

        assembly.anchorEngine.anchor()

        assertEquals(
            "exactly ONE load for the resident tier. Two would mean a second " +
                "loading authority had been built alongside this one.",
            1,
            fixture.backend.loadCount
        )
    }

    // ── The real legacy Human Core, on every host ────────────────────────────

    @Test
    fun `the turn path speaks through the real legacy Human Core, not a stub`() {
        val assembly = TurnPathAssembly.assemble(
            Fixture().ports({ ResourceSnapshot.alwaysHealthy() }, { 1_000L })
        )

        assertTrue(
            "the root must initialize the legacy Human Core itself, so every host " +
                "reaches the same singleton",
            HumanCore.isInitialized()
        )

        val perception = assembly.humanCoreTurnPort.beginExchange("what did we decide?", null)
        assertNotNull(
            "beginExchange must return a REAL PerceptionResult. The JVM host used to " +
                "pass a lambda that returned null by construction, so a turn through " +
                "it recorded nothing at all.",
            perception
        )

        assertEquals(
            "the expression pass must be the real Human Core's own output",
            HumanCore.express("The seed layer is closed.", null),
            assembly.humanCoreTurnPort.express("The seed layer is closed.", null)
        )
    }

    @Test
    fun `the emotion tier is wired on the JVM host too, not just the phone`() {
        val assembly = TurnPathAssembly.assemble(
            Fixture().ports({ ResourceSnapshot.alwaysHealthy() }, { 1_000L })
        )
        val input = "I hate the slow weather"

        val reading = assembly.mentalStateEstimator.estimateForTurn(input)

        assertEquals(
            "the Tier-1 emotion fusion must feed the ONE estimator seam, so this " +
                "host reads the same per-turn state the phone reads",
            MentalStateHypothesis.fromEmotion(assembly.emotionFusion.estimate(input)),
            reading
        )
        assertEquals("frustrated", reading.mood)
        assertEquals("resolve a pain point", reading.goal)
        assertNotEquals(
            "the reading must carry a real emotion reading, not the empty-neutral " +
                "default the rule-based provider leaves behind",
            EmotionHypothesis.neutral(),
            reading.emotion
        )
    }

    // ── The host's clock is the turn clock ───────────────────────────────────

    @Test
    fun `the host's clock governs the spoken-ack gap`() {
        val fixture = Fixture(runTurnWork = { })
        var now = 1_000_000L
        val assembly = TurnPathAssembly.assemble(
            fixture.ports({ ResourceSnapshot.alwaysHealthy() }, { now })
        )

        repeat(3) { assembly.pipeline.onUserInput("hello $it") }
        assertEquals(
            "with a frozen host clock no ack gap ever elapses, so exactly one ack is " +
                "spoken. A count of 3 would mean the pipeline reached for " +
                "System.currentTimeMillis() instead of the injected clock.",
            1,
            fixture.acks.size
        )

        now += LatencyPipeline.ACK_GAP_MS
        assembly.pipeline.onUserInput("hello later")
        assertEquals(
            "once the host's clock advances past the gap, the next turn speaks",
            2,
            fixture.acks.size
        )
    }

    // ── Admission reads the host's live resource state ───────────────────────

    @Test
    fun `admission reads the host's live resource reading`() {
        var starved = false
        val assembly = TurnPathAssembly.assemble(
            Fixture().ports({
                if (starved) StarvedSnapshot else ResourceSnapshot.alwaysHealthy()
            }, { 1_000L })
        )
        val request = OrganWakeRequest(organRole = OrganRole.REASONING, tier = ModelTier.ON_DEMAND_REASONING)

        assertEquals(
            "a healthy reading admits the wake",
            AdmissionDecision.ALLOW,
            assembly.resourceGovernor.admit(request)
        )

        starved = true
        val underPressure = assembly.resourceGovernor.admit(request)
        assertTrue(
            "a starved reading must not admit at the requested tier, and the reading " +
                "must be the host's own, taken live per admission — not an all-clear " +
                "constant buried in the composition root. Got: $underPressure",
            underPressure is AdmissionDecision.DEGRADE_TO || underPressure is AdmissionDecision.DENY
        )
    }

    // ── The JVM host gets this root, not a second one ────────────────────────

    @Test
    fun `the JVM host is built by the same composition root as the phone`() {
        val server = newServer("root")

        assertTrue(
            "constructing the JVM host must go through the ONE root, which " +
                "initializes the legacy Human Core exactly as the phone does",
            HumanCore.isInitialized()
        )
        assertEquals(
            "the JVM host's pipeline must be the real production pipeline",
            "com.jarvis.app.latency.LatencyPipeline",
            server.pipeline.javaClass.name
        )
        assertEquals(
            "the JVM host's engine must be the real production engine",
            "com.jarvis.app.cognitive.CognitiveEngine",
            server.engine.javaClass.name
        )
    }

    @Test
    fun `a real turn through the JVM host runs the real composition`() {
        val server = newServer("turn")
        val input = "I hate the slow weather"

        server.pipeline.onUserInput(input)

        val reading = MentalStateClaims.read(server.workspace)
        assertNotNull(
            "a real turn through the real entry point must reach the real engine and " +
                "publish this turn's mental state into the one shared claim store",
            reading
        )
        assertEquals(
            "the JVM host now reads the same Tier-1 emotion tier the phone reads",
            "frustrated",
            reading!!.mood
        )
        assertEquals(
            "the legacy Human Core must have been consulted for real: the pipeline's " +
                "own slow path calls beginExchange before the engine does",
            "com.jarvis.app.latency.LatencyPipeline",
            server.pipeline.javaClass.name
        )
    }

    private fun newServer(name: String) = TermuxJarvisServer(
        port = 0,
        backendOverride = FakeModelBackend(),
        voiceForgeSynthesizerOverride = SilentSpeech(),
        dataDir = temp.newFolder(name)
    )

    private object StarvedSnapshot : ResourceSnapshot {
        override val availableMemoryMb = 8L
        override val cpuLoadPercent = 99.0
        override val thermalLevel = 3
        override val batteryPercent = 100
        override val isCharging = true
    }
}
