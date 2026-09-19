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
import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.MemoryNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE-A-SELF-USER-WORLD-WIRING-AUDIT — confirms the Stage 03 identity
 * subsystems (WorldModelService, UserModel durable+ephemeral, SelfModel,
 * PersonaTuning) are genuinely wired into the REAL live conversation turn.
 *
 * The live entry point a user message travels is:
 *
 *   ConversationViewModel.send() -> LatencyLayer.onUserInput()
 *     -> LatencyPipeline.onUserInput() -> CognitiveEngine.process()
 *
 * (LatencyPipeline.onUserInput is the pure-JVM live turn entry point; dispatching
 * inline makes each onUserInput drive one real turn synchronously.)
 *
 * Walking the REAL current code (pre-fix):
 *   - WorldModelService / UserProfile / SelfModel / PersonaTuner /
 *     UserMentalStateEstimator are referenced NOWHERE in the live path — only
 *     within the identity package itself. UserMentalStateEstimator appears only
 *     as the optional ContextWindowAssembler.mentalStateEstimator seam
 *     (ContextWindowAssembler.kt:42), which CognitiveEngine never supplied.
 *   - JarvisEngine.init (JarvisEngine.kt:186-192) constructs CognitiveEngine
 *     with none of these.
 *   => GENUINELY MISSING: none of the four Stage-03 identity subsystems fired
 *      in the live turn.
 *
 * This test proves, AFTER the fix, through the real entry point:
 *   - a durable user preference set in one real turn changes a later real turn's
 *     context (UserProfile durable);
 *   - real WorldModelService user-node facts appear in live-turn context;
 *   - a self-referential real turn reflects real CapabilityRegistry state;
 *   - a durable persona trait adjustment from explicit feedback influences a
 *     later real turn's context;
 *   - the ephemeral per-turn UserMentalStateEstimator feeds the live context.
 */
class PhaseAIdentityWiringLiveTest {

    /** Deterministic empty in-memory [MemoryStorePort] — no Android, no disk. */
    private class EmptyMemoryStore : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }

    /** Pure-Kotlin, zero-JDBC in-memory [MemoryGraphStore] for the live harness. */
    private class LiveFakeGraph : MemoryGraphStore {
        private val nodes = mutableListOf<MemoryNode>()
        private var idCounter = 0
        override fun addFact(subject: String, predicate: String, `object`: String, source: String) {
            val now = System.currentTimeMillis()
            for (i in nodes.indices) {
                if (nodes[i].subject == subject && nodes[i].predicate == predicate && nodes[i].validUntil == null) {
                    nodes[i] = nodes[i].copy(validUntil = now)
                }
            }
            nodes.add(
                MemoryNode(
                    id = "live-${++idCounter}", subject = subject, predicate = predicate,
                    `object` = `object`, source = source, validFrom = now
                )
            )
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

    /** Mutable fake [IdentitySource] mirroring live HumanCore fields. */
    private class MutableIdentitySource(
        var name: String? = null,
        var version: Int? = null
    ) : IdentitySource {
        override fun name(): String? = name
        override fun version(): Int? = version
    }

    private class LiveHarness {
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
            worldModel = worldModel,
            userProfile = userProfile,
            mentalStateEstimator = mentalStateEstimator,
            selfModel = selfModel,
            personaTuner = personaTuner
        )
        val sentThroughBridge = mutableListOf<String>()

        val engine = CognitiveEngine(
            scope = CoroutineScope(Dispatchers.IO),
            memoryStore = EmptyMemoryStore(),
            humanCore = HumanCore,
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
    }

    @Test
    fun `durable preference set in one real turn feeds a later real turn's context`(): Unit =
        runBlocking {
            val harness = LiveHarness()

            // Turn 1: user states an explicit durable preference through the
            // real entry point -> written to the user node as a durable fact.
            harness.userMessage("I prefer concise answers to questions")
            assertEquals(
                "durable preference persisted via the real live path",
                "concise answers to questions",
                harness.userProfile.getPreference("communicationStyle")
            )

            // A later real turn's generated context carries the durable
            // preference + the user-node world fact (WorldModelService).
            harness.userMessage("What should you call me?")
            val last = harness.sentThroughBridge.last()
            assertTrue("identity context section is present", last.contains("[Identity context]"))
            assertTrue(
                "durable user preference changes a later real turn's context",
                last.contains("user preference communicationStyle")
            )
            assertTrue(
                "world-model user-node fact feeds the live turn",
                last.contains("world: Venon preference:communicationStyle")
            )
        }

    @Test
    fun `self-referential real turn reflects real CapabilityRegistry state`(): Unit =
        runBlocking {
            val harness = LiveHarness()

            harness.userMessage("What can you do for me?")
            val last = harness.sentThroughBridge.last()
            assertTrue("identity context section present for self-referential turn", last.contains("[Identity context]"))
            assertTrue(
                "self-model capabilities reflect real CapabilityRegistry state",
                last.contains("self: identity=jarvis v2; capabilities: stt")
            )
        }

    @Test
    fun `explicit persona feedback in one real turn influences a later real turn`(): Unit =
        runBlocking {
            val harness = LiveHarness()

            // Explicit persona feedback through the live entry point.
            harness.userMessage("Please be more direct with me")
            assertEquals("directness", "high", harness.personaTuner.currentValue("directness"))

            // A later real turn carries the durable persona trait in context.
            harness.userMessage("Remind me of the plan")
            val last = harness.sentThroughBridge.last()
            assertTrue(last.contains("persona trait directness = high"))
            assertTrue(last.contains("world: Venon persona:directness"))
        }

    @Test
    fun `ephemeral mental state estimator feeds the live context`(): Unit =
        runBlocking {
            val harness = LiveHarness()

            harness.userMessage("I am feeling uncertain about the schedule")
            val last = harness.sentThroughBridge.last()
            assertTrue(
                "ephemeral per-turn mental state feeds the live context",
                last.contains("user mental state: goal=explore")
            )
            // Ephemeral by construction: the estimator itself never writes, so
            // no durable preference or persona attrs resulted from this turn.
            assertTrue(harness.userProfile.allPreferences().isEmpty())
            assertTrue(harness.personaTuner.adjustments().isEmpty())
        }
}
