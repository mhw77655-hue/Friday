package ai.friday.assistant.onefriday

import ai.friday.assistant.body.MemoryItem
import ai.friday.assistant.body.MemoryStorePort
import ai.friday.assistant.cognition.workspace.MentalStateClaims
import ai.friday.assistant.cognitive.Goal
import ai.friday.assistant.cognitive.GoalPriority
import ai.friday.assistant.cognitive.Subgoal
import ai.friday.assistant.cognitive.SubgoalStatus
import ai.friday.assistant.cognitive.capability.CapabilityEvidence
import ai.friday.assistant.cognitive.capability.CapabilityEvidenceLoad
import ai.friday.assistant.cognitive.capability.EvidenceOutcome
import ai.friday.assistant.emotion.EmotionHypothesis
import ai.friday.assistant.failure.FailureSurface
import ai.friday.assistant.identity.MentalStateHypothesis
import ai.friday.assistant.latency.LatencyPipeline
import ai.friday.assistant.model.AdmissionDecision
import ai.friday.assistant.model.FakeModelBackend
import ai.friday.assistant.memory.TestEmbeddingProvider
import ai.friday.assistant.model.ModelTier
import ai.friday.assistant.model.OrganRole
import ai.friday.assistant.model.OrganWakeRequest
import ai.friday.assistant.model.ResourceSnapshot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ONE-2-COMPOSITION: what the ONE composition root guarantees, proven against
 * the real production classes.
 *
 * Only genuinely host-bound leaves are faked (a directory, an in-memory graph,
 * and a fake model backend). Every organ, seam and
 * wiring under test is the production class used by the Android app. The
 * optional JVM harness is test-only.
 */
class TurnPathAssemblyTest {

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

        fun ports(
            snapshot: () -> ResourceSnapshot,
            clock: () -> Long,
            directory: File? = null
        ): JvmPlatformPorts {
            storageDir = directory ?: compositionDir("assembly")
            backend = FakeModelBackend()
            return JvmPlatformPorts(
                storageDir = storageDir,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                stores = JvmPlatformStores(
                    graphStore = FakeMemoryGraphStore(nowMs = clock),
                    embeddingProvider = TestEmbeddingProvider(),
                    memoryStore = NoMemories(),
                    backend = backend,
                ),
                snapshot = snapshot,
                clock = clock,
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
        assertEquals("Friday", assembly.selfModel.identity().name)
        assertEquals(assembly.individual.identity.id, assembly.selfModel.identity().persistentId)
        assertEquals("she/her", assembly.selfModel.identity().pronouns)
        assertTrue(assembly.componentFingerprint.keys.none { it.contains("HumanCore", ignoreCase = true) })
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
        assertNotNull("the durable capability evidence ledger", assembly.capabilityEvidenceLedger)
        assertNotNull("the real fuzzy command resolver", assembly.fuzzyCommandResolver)
        assertNotNull("the single model loading authority", assembly.modelManager)
        assertNotNull("the single admission governor", assembly.resourceGovernor)
        assertNotNull("the real anchor engine", assembly.anchorEngine)
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

    // ── Friday owns the identity and the turn path ─────────────────────────

    @Test
    fun `production composition is Friday-owned and independent of legacy Human Core`() {
        val assembly = TurnPathAssembly.assemble(
            Fixture().ports({ ResourceSnapshot.alwaysHealthy() }, { 1_000L })
        )

        val identity = assembly.selfModel.identity()
        assertEquals("Friday", identity.name)
        assertEquals("she/her", identity.pronouns)
        assertEquals(1, identity.version)
        assertEquals(
            "SelfModel and the model-free seed must expose the same persistent individual identity",
            assembly.individual.identity.id,
            identity.persistentId
        )
        assertTrue(
            "the composition fingerprint must not expose a Human Core facade",
            assembly.componentFingerprint.keys.none { it.contains("HumanCore", ignoreCase = true) }
        )
        assertEquals(
            "the identity context receives Friday's own self-model instance",
            assembly.selfModel,
            assembly.identityContext.selfModel
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

    @Test
    fun `the same individual recovers identity experience and unfinished goals after restart`() {
        val directory = compositionDir("individual-continuity")
        val first = TurnPathAssembly.assemble(
            Fixture().ports({ ResourceSnapshot.alwaysHealthy() }, { 1_000L }, directory)
        )
        val identityId = first.individual.identity.id
        val goal = Goal(
            id = "goal-continuity",
            description = "Continue the work Friday was pursuing before shutdown.",
            priority = GoalPriority.HIGH,
            createdAt = 1_000L,
            successCriteria = listOf("The same individual resumes with her unfinished goal.")
        )
        val subgoal = Subgoal(
            id = "subgoal-recovery",
            goalId = goal.id,
            description = "Restore the unfinished commitment.",
            status = SubgoalStatus.IN_PROGRESS,
            createdAt = 1_100L
        )

        first.engine.setGoal(goal)
        first.engine.addSubgoal(subgoal)
        first.individual.recordExperience("Friday observed a durable continuity test event.")

        val liveSnapshot = first.selfModel.continuitySnapshot()
        assertTrue(liveSnapshot.activeGoals.contains(goal.description))
        assertTrue(liveSnapshot.activeGoals.contains(subgoal.description))
        assertEquals(1, liveSnapshot.experience.experienceCount)

        val persistedFile = File(directory, "friday/continuity/goals.json")
        val deadline = System.currentTimeMillis() + 3_000L
        while (System.currentTimeMillis() < deadline &&
            (!persistedFile.isFile || !persistedFile.readText().contains(goal.id))
        ) {
            Thread.sleep(10L)
        }
        assertTrue("Goal state must reach durable storage.", persistedFile.isFile && persistedFile.readText().contains(goal.id))

        val restarted = TurnPathAssembly.assemble(
            Fixture().ports({ ResourceSnapshot.alwaysHealthy() }, { 2_000L }, directory)
        )
        assertEquals(identityId, restarted.individual.identity.id)
        assertEquals(goal, restarted.engine.cognitiveState.value.currentGoal)
        assertEquals(listOf(subgoal), restarted.engine.cognitiveState.value.activeSubgoals)
        assertEquals(1, restarted.individual.experience.experienceCount())
        assertTrue(restarted.selfModel.goals().contains(goal.description))
        directory.deleteRecursively()
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
        val request = OrganWakeRequest(organRole = OrganRole.REASONING.name, tier = ModelTier.ON_DEMAND_REASONING)

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

    // ── A plain JVM fixture uses the same root; Android is the only product host ──

    @Test
    fun `the JVM fixture is built by the same composition root as the Android app`() {
        val assembly = newAssembly("root")

        assertEquals(
            "the JVM host's pipeline must be the real production pipeline",
            "ai.friday.assistant.latency.LatencyPipeline",
            assembly.pipeline.javaClass.name
        )
        assertEquals(
            "the JVM host's engine must be the real production engine",
            "ai.friday.assistant.cognitive.CognitiveEngine",
            assembly.engine.javaClass.name
        )
    }

    @Test
    fun `a real turn through the JVM fixture runs the real composition`() {
        val assembly = newAssembly("turn")
        val input = "I hate the slow weather"

        assembly.pipeline.onUserInput(input)

        val reading = MentalStateClaims.read(assembly.workspace)
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
        assertTrue(
            "a real turn flows through Friday's cognitive path with no legacy facade",
            assembly.componentFingerprint.keys.none { it.contains("HumanCore", ignoreCase = true) }
        )
    }

    @Test
    fun `capability evidence survives reconstruction of the production composition`() {
        val directory = compositionDir("capability-evidence-continuity")
        val first = TurnPathAssembly.assemble(
            Fixture().ports({ ResourceSnapshot.alwaysHealthy() }, { 1_000L }, directory)
        )
        first.capabilityEvidenceLedger.record(
            CapabilityEvidence(
                capabilityId = "phone.memory.recall",
                operation = "memory.recall",
                outcome = EvidenceOutcome.SUCCEEDED,
                observedAtMs = 1_000L,
                source = "composition-test",
                verified = true,
                eventId = "composition-evidence-1"
            )
        )

        val restarted = TurnPathAssembly.assemble(
            Fixture().ports({ ResourceSnapshot.alwaysHealthy() }, { 2_000L }, directory)
        )
        val restored = restarted.capabilityEvidenceLedger.load() as CapabilityEvidenceLoad.Loaded
        assertEquals(1, restored.evidence.size)
        assertEquals("composition-evidence-1", restored.evidence.single().eventId)
        assertTrue(restored.evidence.single().verified)
        directory.deleteRecursively()
    }

    private fun newAssembly(name: String): TurnPathAssembly {
        val fixture = Fixture()
        return TurnPathAssembly.assemble(
            fixture.ports(
                snapshot = { ResourceSnapshot.alwaysHealthy() },
                clock = { 1_000L }
            )
        )
    }

    private object StarvedSnapshot : ResourceSnapshot {
        override val availableMemoryMb = 8L
        override val cpuLoadPercent = 99.0
        override val thermalLevel = 3
        override val batteryPercent = 100
        override val isCharging = true
    }
}

/**
 * A unique app-private-style directory for each composition fixture. These
 * stores are independent and no process-wide legacy Human Core singleton owns
 * their lifetime. Directories are left on disk so asynchronous test work cannot
 * race with teardown and produce misleading file-not-found failures.
 */
internal fun compositionDir(prefix: String): File =
    File(
        System.getProperty("java.io.tmpdir"),
        "onefriday-$prefix-${System.nanoTime()}"
    ).apply { mkdirs() }
