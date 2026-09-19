package com.jarvis.app.latency

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.cognitive.CognitiveEngine
import com.jarvis.app.cognitive.UserModel
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.model.CognitiveAdmissionPolicy
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.model.FakeResourceSnapshot
import com.jarvis.app.model.ModelManager
import com.jarvis.app.model.OrganRole
import com.jarvis.app.model.ResourceGovernor
import com.jarvis.app.model.ResourceSnapshot
import com.jarvis.app.ui.viewmodel.Turn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STAGE-04-LIVE-WIRING — proves Stage 04 is CONNECTED TO THE REAL live
 * conversation path, not just correct in isolation.
 *
 * The actual live entry point a user message travels is:
 *
 *   ConversationViewModel.send() -> LatencyLayer.onUserInput()
 *     -> LatencyPipeline.onUserInput() -> CognitiveEngine.process()
 *       (FEP wake gate -> ModelManager.wake() = request() over the ONE
 *        ModelBackend, gated by ResourceGovernor)  -> release() at turn end
 *
 * [LatencyPipeline.onUserInput] is the pure-JVM live turn entry point
 * (ConversationViewModel is a thin Android adapter over [LatencyLayer], which
 * just constructs/owns this pipeline), so this test drives the REAL pipeline
 * with the REAL [CognitiveEngine], REAL [ModelManager] over [FakeModelBackend],
 * and the REAL [ResourceGovernor] — a genuine through-the-live-path proof, not
 * a parallel harness.
 *
 * ZERO real model output / GGUF / native inference: [ModelManager] is built
 * with the injected [FakeModelBackend], so generation output self-identifies
 * as the fake's "[fake-model-backend] echo:" prefix and the production adapters
 * are never constructed.
 */
class Stage04LiveWiringTest {

    /** Deterministic empty in-memory [MemoryStorePort] — no Android, no disk. */
    private class EmptyMemoryStore : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }

    /**
     * Build the full live-turn stack: real pipeline -> real engine -> real
     * ModelManager over FakeModelBackend -> real governor with a mutable
     * injected snapshot. dispatch runs inline so a single [onUserInput] fully
     * drives the turn synchronously; clock + sweeps are hand-triggered for
     * deterministic cooldown checks.
     */
    private class LiveHarness(
        val backend: FakeModelBackend = FakeModelBackend(loadLatencyMs = 40)
    ) {
        var nowMs = 0L
        var snapshot: ResourceSnapshot = FakeResourceSnapshot(
            availableMemoryMb = 8192,
            cpuLoadPercent = 5.0,
            thermalLevel = 0,
            batteryPercent = 100,
            isCharging = true
        )
        var sentThroughBridge = 0

        val governor = ResourceGovernor(snapshotProvider = { snapshot })
        val modelManager = ModelManager(
            context = null,
            scope = CoroutineScope(Dispatchers.IO),
            backend = backend,
            resourceGovernor = governor,
            cooldownMs = 1_000L,
            clock = { nowMs },
            autoSweep = false
        )
        val policy = CognitiveAdmissionPolicy()
        // NOTE: the engine must NOT be handed runBlocking's scope — its
        // WorkingMemory starts an infinite decay loop on the scope it is given,
        // so handing it runBlocking would make runBlocking wait forever on that
        // child and hang the whole test task (see STAGE-04-INTEGRATION).
        val engine = CognitiveEngine(
            scope = CoroutineScope(Dispatchers.IO),
            memoryStore = EmptyMemoryStore(),
            humanCore = HumanCore,
            modelManager = modelManager,
            cognitiveAdmissionPolicy = policy
        )

        val pipeline = LatencyPipeline(
            dispatch = { it() },
            scheduleDelayed = { _, _ -> },
            bridgeSend = { sentThroughBridge++ },
            bridgeStatus = { "idle" },
            beginExchange = { _, _ -> null },
            express = { reply, _ -> com.jarvis.app.humancore.protocol.StyledResponse.Approved(reply) },
            completeExchange = { _, _, _, _, _ -> },
            sessionContext = { null },
            cognitiveEngine = engine
        )

        init {
            // Realistic start-of-conversation: the user model knows the
            // operator, so USER_IDENTITY "unknown" never falsely escalates an
            // ordinary turn to high doubt.
            engine.updateUserModel(UserModel(knownName = "Alice"))
        }

        fun userMessage(text: String) {
            pipeline.onUserInput(text)
        }
    }

    @Test
    fun `live path wakes the reasoning organ through FakeModelBackend only on high-doubt turns`(): Unit =
        runBlocking {
            val harness = LiveHarness()
            val backend = harness.backend
            val mm = harness.modelManager

            // Bootstrap the resident tier through the live path's single
            // lifecycle so the conversation has a resident organ to serve.
            val resident = mm.wake(OrganRole.RESIDENT, task = "live-bootstrap")
            assertEquals(1, backend.loadCount)

            // -----------------------------------------------------------------
            // Low-doubt turns: served on the resident tier, the on-demand
            // reasoning organ is NEVER woken/loaded through the live path.
            // -----------------------------------------------------------------
            val lowDoubtTurns = listOf(
                // avoid "it"/"that"/"this" substrings — the engine's REFERENT
                // unknown uses a naive substring check that would spuriously
                // escalate a turn to high doubt.
                "I really enjoyed the concert last night.",
                "The park on the corner has lovely trees."
            )
            for (text in lowDoubtTurns) {
                harness.userMessage(text)
                val realDoubt =
                    harness.policy.doubt(harness.engine.cognitiveState.value.uncertainty)
                assertTrue("live low-doubt turn is below threshold: $realDoubt", realDoubt < 0.5)
                assertEquals(
                    "live §(a): reasoning organ never loads on low-doubt turns ($text)",
                    1,
                    backend.loadCount
                )
                // resident still loaded throughout the low-doubt turns
                assertTrue(backend.isLoaded(resident.handle))
            }

            // -----------------------------------------------------------------
            // High-doubt turn: the live path wakes the reasoning organ through
            // ModelManager (== request()) over FakeModelBackend, loads it, then
            // release() at turn end lets the cooldown sweep unload it.
            // -----------------------------------------------------------------
            harness.userMessage("Why did that happen this time?")
            val highDoubt = harness.policy.doubt(harness.engine.cognitiveState.value.uncertainty)
            assertTrue("live high-doubt turn is at/above threshold: $highDoubt", highDoubt >= 0.5)
            assertEquals(
                "live §(b): high-doubt turn loads the reasoning organ via the fake backend",
                2,
                backend.loadCount
            )
            val reasoningHandle = backend.loaded.first { it != resident.handle }
            assertTrue("the reasoning organ is loaded after the live wake", backend.isLoaded(reasoningHandle))
            val reasoningReply = backend.generate(reasoningHandle, "live high-doubt")
            assertTrue(
                "generation output self-identifies as the fake — zero real model output",
                reasoningReply.startsWith("[fake-model-backend] echo:")
            )

            // release(REASONING) was called at turn end by the engine; the
            // cooldown sweep auto-unloads the idle reasoning organ.
            harness.nowMs += 5_000L
            mm.runCooldownSweep()
            assertFalse("release() + cooldown auto-unload the reasoning organ", backend.isLoaded(reasoningHandle))
            assertEquals(1, backend.unloadCount)
            assertTrue("the resident organ is never auto-unloaded", backend.isLoaded(resident.handle))

            // The bridge (live continuation seam) saw both turns.
            assertEquals(
                "the live bridge saw every routed turn (sendBlock continuation)",
                lowDoubtTurns.size + 1,
                harness.sentThroughBridge
            )
        }
}
