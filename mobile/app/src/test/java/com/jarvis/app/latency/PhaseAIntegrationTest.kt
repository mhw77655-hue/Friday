package com.jarvis.app.latency

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.capability.CapabilityRegistry
import com.jarvis.app.cognitive.CognitiveEngine
import com.jarvis.app.cognitive.UserModel
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.identity.IdentityContext
import com.jarvis.app.identity.IdentitySource
import com.jarvis.app.identity.MentalStateHypothesis
import com.jarvis.app.identity.PersonaTuner
import com.jarvis.app.identity.SelfModel
import com.jarvis.app.identity.StageHistorySource
import com.jarvis.app.identity.UserMentalStateEstimator
import com.jarvis.app.identity.UserProfile
import com.jarvis.app.identity.WorldModelService
import com.jarvis.app.memory.BlendedMemoryRetriever
import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.MemoryImportanceScorer
import com.jarvis.app.memory.MemoryNode
import com.jarvis.app.memory.TestEmbeddingProvider
import com.jarvis.app.model.CognitiveAdmissionPolicy
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.model.FakeResourceSnapshot
import com.jarvis.app.model.ModelManager
import com.jarvis.app.model.OrganRole
import com.jarvis.app.model.ResourceGovernor
import com.jarvis.app.model.ResourceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE-A-INTEGRATION — one real multi-turn conversation, driven ONLY through
 * the single real live entry point (LatencyPipeline.onUserInput), proves that
 * Galaxy Memory, the self/user/world/persona models, AND model federation all
 * fire together in the SAME turns — not separately re-simulated pieces.
 *
 * The live stack one turn travels:
 *
 *   LatencyPipeline.onUserInput()
 *     -> CognitiveEngine.process()
 *        ├─ memory write-back : MemoryGraphStore.addFact (durable facts)
 *        ├─ memory retrieval : BlendedMemoryRetriever via ContextWindowAssembler
 *        ├─ identity         : WorldModelService/UserProfile/UserMentalStateEstimator/
 *        │                     SelfModel/PersonaTuner (durable + ephemeral, self/user/world)
 *        └─ federation       : CognitiveAdmissionPolicy -> ModelManager.wake(REASONING)
 *                              (gated by ResourceGovernor over FakeModelBackend) -> release()
 *
 * Everything is the REAL implementation; only the test doubles are the
 * declared ones (FakeModelBackend for federation, in-memory MemoryGraphStore,
 * TestEmbeddingProvider). No GGUF / native inference occurs anywhere.
 *
 * Observables asserted in ONE conversation:
 *   - memory  : a fact stated in turn 1 is written back AND retrievable +
 *               present in a later turn's context
 *   - identity: a durable preference set in one turn feeds a later turn's
 *               context (UserProfile+WorldModel), a self-referential turn
 *               reflects real CapabilityRegistry (SelfModel), explicit persona
 *               feedback persists and feeds a later turn (PersonaTuner),
 *               ephemeral mental state is present
 *   - federation: low-doubt turns NEVER wake the reasoning organ (resident
 *               serves); a high-doubt turn wakes+loads it via FakeModelBackend
 *               and release() at turn end lets the cooldown sweep auto-unload it
 */
class PhaseAIntegrationTest {

    private class EmptyMemoryStore : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }

    /** Pure-Kotlin, zero-JDBC in-memory [MemoryGraphStore] for the live harness. */
    private class LiveFakeGraph : MemoryGraphStore {
        private val nodes = mutableListOf<MemoryNode>()
        private var idCounter = 0
        override fun addFact(subject: String, predicate: String, `object`: String, source: String): String {
            val now = System.currentTimeMillis()
            val id = "live-${++idCounter}"
            for (i in nodes.indices) {
                if (nodes[i].subject == subject && nodes[i].predicate == predicate && nodes[i].validUntil == null) {
                    nodes[i] = nodes[i].copy(validUntil = now, supersededBy = id)
                }
            }
            nodes.add(
                MemoryNode(
                    id = id, subject = subject, predicate = predicate,
                    `object` = `object`, source = source, validFrom = now
                )
            )
            return id
        }
        override fun query(subject: String?, predicate: String?, asOfTime: Long): List<MemoryNode> =
            nodes.filter { n ->
                (subject == null || n.subject == subject) &&
                    (predicate == null || n.predicate == predicate) &&
                    n.validFrom <= asOfTime && (n.validUntil == null || n.validUntil > asOfTime)
            }
        override fun getHistory(subject: String, predicate: String): List<MemoryNode> =
            nodes.filter { it.subject == subject && it.predicate == predicate }.sortedBy { it.validFrom }
        override fun nodeCount(): Long = nodes.size.toLong()
        override fun close() {}
    }

    private class MutableIdentitySource(
        var name: String? = null,
        var version: Int? = null
    ) : IdentitySource {
        override fun name(): String? = name
        override fun version(): Int? = version
    }

    /** One real live stack with EVERY Phase A seam wired into the single engine. */
    private class LiveHarness(
        val backend: FakeModelBackend = FakeModelBackend(loadLatencyMs = 20)
    ) {
        var nowMs = 0L
        var snapshot: ResourceSnapshot = FakeResourceSnapshot(
            availableMemoryMb = 8192, cpuLoadPercent = 5.0, thermalLevel = 0,
            batteryPercent = 100, isCharging = true
        )
        val sentThroughBridge = mutableListOf<String>()

        val graph = LiveFakeGraph()
        val worldModel = WorldModelService(graph)
        val userProfile = UserProfile(worldModel)
        val mentalStateEstimator = UserMentalStateEstimator(
            { MentalStateHypothesis("explore", "neutral", "") }
        )
        val registry = CapabilityRegistry()
        val selfModel = SelfModel(
            identitySource = MutableIdentitySource("jarvis", 2),
            capabilityRegistry = registry,
            stageHistory = StageHistorySource { emptyList() }
        )
        val personaTuner = PersonaTuner(worldModel)
        val identityContext = IdentityContext(
            worldModel = worldModel, userProfile = userProfile,
            mentalStateEstimator = mentalStateEstimator,
            selfModel = selfModel, personaTuner = personaTuner
        )

        val provider = TestEmbeddingProvider(dimension = 256)
        val scorer = MemoryImportanceScorer(embeddingProvider = provider)
        val retriever = BlendedMemoryRetriever(
            graphStore = graph, embeddingProvider = provider, scorer = scorer
        )

        val governor = ResourceGovernor(snapshotProvider = { snapshot })
        val modelManager = ModelManager(
            context = null, scope = CoroutineScope(Dispatchers.IO),
            backend = backend, resourceGovernor = governor,
            cooldownMs = 1_000L, clock = { nowMs }, autoSweep = false
        )
        val policy = CognitiveAdmissionPolicy()

        val engine = CognitiveEngine(
            scope = CoroutineScope(Dispatchers.IO),
            memoryStore = EmptyMemoryStore(),
            humanCore = HumanCore,
            modelManager = modelManager,
            cognitiveAdmissionPolicy = policy,
            blendedRetriever = retriever,
            graphStore = graph,
            identityContext = identityContext
        )

        val pipeline = LatencyPipeline(
            dispatch = { it() },
            scheduleDelayed = { _, _ -> },
            bridgeSend = { sentThroughBridge.add(it) },
            bridgeStatus = { "idle" },
            beginExchange = { _, _ -> null },
            express = { reply, _ -> com.jarvis.app.humancore.protocol.StyledResponse.Approved(reply) },
            completeExchange = { _, _, _, _, _ -> },
            sessionContext = { null },
            cognitiveEngine = engine
        )

        init {
            engine.updateUserModel(UserModel(knownName = "Alice"))
            registry.register(
                CapabilityRegistry.Capability(
                    id = "stt", version = "1", name = "stt", function = "stt",
                    category = CapabilityRegistry.Category.STT,
                    currentState = CapabilityRegistry.State.LOADED,
                    health = CapabilityRegistry.Health.HEALTHY
                )
            )
        }

        fun userMessage(text: String) {
            pipeline.onUserInput(text)
        }

        fun realDoubt(): Double = policy.doubt(engine.cognitiveState.value.uncertainty)

    }

    @Test
    fun `one real conversation fires memory self-user-world models and federation in the same turns`(): Unit =
        runBlocking {
            val harness = LiveHarness()
            val mm = harness.modelManager
            val backend = harness.backend

            // Bootstrap the resident tier through the single lifecycle so the
            // conversation has a resident organ to serve.
            val resident = mm.wake(OrganRole.RESIDENT, task = "live-bootstrap")
            assertEquals(1, backend.loadCount)

            // ---- Turn 1: durable memory write + durable preference/mental state ----
            harness.userMessage("I use Python and FastAPI for my projects")
            val written = harness.graph.query(subject = "user", predicate = "stated")
            assertTrue("turn 1 wrote a durable fact to MemoryGraphStore (memory write-back)", written.any { it.`object`.contains("Python") })

            // ---- Turn 2: durable user preference, set in the live turn ---- 
            harness.userMessage("I prefer concise answers to questions")
            assertEquals(
                "durable user preference persisted through the live turn",
                "concise answers to questions",
                harness.userProfile.getPreference("communicationStyle")
            )

            // ---- Turn 3: explicit persona feedback, persisted through the live turn ----
            harness.userMessage("Please be more concise")
            assertEquals("durable persona trait persisted", "low", harness.personaTuner.currentValue("verbosity"))

            // ---- Turn 4: self-referential - reflects real registry (SelfModel) ----
            harness.userMessage("Tell me about yourself")
            assertTrue(
                "self-referential turn reflects real CapabilityRegistry",
                harness.sentThroughBridge.last().contains("self: identity=jarvis v2; capabilities: stt")
            )

            // ---- Turn 5: a later turn retrieves the earlier stored memory ----
            harness.userMessage("My Python projects need careful sharing.")
            val memoryTurn = harness.sentThroughBridge.last()
            assertTrue("later real turn retrieves stored Galaxy Memory fact", memoryTurn.contains("[Cross-session memory]"))
            assertTrue("retrieved memory is the fact stated in turn 1", memoryTurn.contains("Python"))
            assertTrue("durable preference this turn (identity) co-present", harness.sentThroughBridge.last().contains("user preference communicationStyle"))
            assertTrue("ephemeral mental state this turn", memoryTurn.contains("user mental state: goal=explore"))

            // None of the memory/identity turns (1-5) woke the reasoning organ:
            // only the resident organ is loaded after them.
            assertEquals("low-doubt memory/identity turns never wake reasoning", 1, backend.loadCount)

            // ---- Turn 6: high-doubt turn -> model federation wake + release ----
            harness.userMessage("Why did that happen this time?")
            assertTrue("high-doubt turn is at/above gate threshold", harness.realDoubt() >= 0.5)
            assertEquals("high-doubt turn woke+loaded the reasoning organ via fake backend", 2, backend.loadCount)
            val reasoningHandle = backend.loaded.first { it != resident.handle }
            assertTrue("reasoning organ loaded after the live wake", backend.isLoaded(reasoningHandle))
            assertTrue(
                "federation generation is the declared fake (zero real model/GGUF)",
                backend.generate(reasoningHandle, "probe").startsWith("[fake-model-backend] echo:")
            )

            // release() at turn end + cooldown sweep auto-unload the reasoning organ.
            harness.nowMs += 5_000L
            mm.runCooldownSweep()
            assertFalse("release() + cooldown auto-unload reasoning organ", backend.isLoaded(reasoningHandle))
            assertEquals(1, backend.unloadCount)
            assertTrue("resident organ never auto-unloaded", backend.isLoaded(resident.handle))

            // The single live bridge saw every turn.
            assertEquals("six real turns through the one live entry point", 6, harness.sentThroughBridge.size)
        }
}
