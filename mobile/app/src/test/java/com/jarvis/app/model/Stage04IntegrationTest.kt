package com.jarvis.app.model

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.cognitive.CognitiveEngine
import com.jarvis.app.cognitive.UserModel
import com.jarvis.app.humancore.HumanCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STAGE-04-INTEGRATION — the whole stage's acceptance proof.
 *
 * ONE integration test drives the full wake/admit/serve/cooldown/degrade cycle
 * across a realistic multi-turn conversation against the REAL components: the
 * real [ModelManager] (single tiered loading path), the real [ResourceGovernor]
 * (with a mutable injected snapshot), the real FEP/doubt gate
 * ([CognitiveAdmissionPolicy] over the engine's genuinely computed per-turn
 * [com.jarvis.app.cognitive.UncertaintyProfile]), and [FakeModelBackend]
 * throughout.
 *
 * ZERO real GGUF / native llama.cpp work happens anywhere in this test:
 * [ModelManager] is constructed with the injected [FakeModelBackend], so the
 * production [AdapterModelBackend] (and its LlamaCppAdapter/ollama/remote
 * adapters) is never even built — there is no code path capable of GGUF file
 * I/O or a native inference call. Every [FakeModelBackend.generate] result is
 * asserted to carry the fake's self-identifying "[fake-model-backend] echo:"
 * prefix, so no run output is ever mistaken for real model output.
 *
 * The immediate next stage story is a REAL llama.cpp-backed [ModelBackend]
 * satisfying the same [ModelBackendContractTest], once a GGUF model exists
 * on-device — this stage is deliberately backend-agnostic, not "federation
 * done forever".
 */
class Stage04IntegrationTest {

    /** Deterministic empty in-memory [MemoryStorePort] — no Android, no disk. */
    private class EmptyMemoryStore : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }

    @Test
    fun `full wake-admit-serve-cooldown-degrade cycle across a multi-turn conversation`(): Unit =
        runBlocking {
            val backend = FakeModelBackend(loadLatencyMs = 60, unloadLatencyMs = 10)
            var nowMs = 0L
            var snapshot: ResourceSnapshot = FakeResourceSnapshot(
                availableMemoryMb = 8192,
                cpuLoadPercent = 5.0,
                thermalLevel = 0,
                batteryPercent = 100,
                isCharging = true
            )
            val governor = ResourceGovernor(snapshotProvider = { snapshot })
            val mm = ModelManager(
                context = null,
                scope = this,
                backend = backend,
                resourceGovernor = governor,
                cooldownMs = 1_000L,
                clock = { nowMs },
                autoSweep = false
            )
            val policy = CognitiveAdmissionPolicy()
            // NOTE: the engine must NOT be given the runBlocking scope. Its
            // WorkingMemory starts an infinite decay loop on the scope it is
            // given, so handing it runBlocking's scope would make runBlocking
            // wait forever on that child and hang the whole test task.
            val engine = CognitiveEngine(
                scope = CoroutineScope(Dispatchers.IO),
                memoryStore = EmptyMemoryStore(),
                humanCore = HumanCore,
                modelManager = mm,
                cognitiveAdmissionPolicy = policy
            )

            // Realistic start-of-conversation state: the user model knows the
            // operator, so the USER_IDENTITY "unknown" does not falsely escalate
            // an ordinary turn to high doubt.
            engine.updateUserModel(UserModel(knownName = "Alice"))

            // -----------------------------------------------------------------
            // (a) Resident tier serves several low-doubt turns — the on-demand
            // path is never touched.
            // -----------------------------------------------------------------
            val resident = mm.wake(OrganRole.RESIDENT, task = "bootstrap")
            assertEquals("resident tier loads unconditionally", 1, backend.loadCount)
            assertTrue("resident handle is loaded", backend.isLoaded(resident.handle))
            val residentReply = backend.generate(resident.handle, "hi")
            assertTrue(
                "generate output is the fake's self-identifying echo — zero real model output claimed",
                residentReply.startsWith("[fake-model-backend] echo:")
            )

            val lowDoubtTurns = listOf(
                // NOTE: the engine's REFERENT unknown uses a naive substring
                // contains("it"/"that"/"this") check, so low-doubt fixtures must
                // avoid those substrings entirely ("favorite", "pitch", etc. would
                // spuriously escalate a turn to high doubt).
                "I really enjoyed the concert last night.",
                "The weather today is delightful.",
                "The park on the corner has lovely trees."
            )
            for ((index, text) in lowDoubtTurns.withIndex()) {
                val result = engine.process(userText = text, modelCall = { "resident-served: $it" })
                assertEquals(CognitiveEngine.TurnDecision.DIRECT_REPLY, result.decision)
                val servedDegraded = result.servedDegraded
                assertTrue(
                    "gate is wired, so every DIRECT_REPLY turn reports its serve tier (null would mean the gate never ran)",
                    servedDegraded != null
                )
                assertFalse("low-doubt turn must not be flagged degraded", servedDegraded!!)

                val realDoubt = policy.doubt(engine.cognitiveState.value.uncertainty)
                assertTrue("real per-turn doubt below threshold: $realDoubt", realDoubt < 0.5)

                assertEquals(
                    "on-demand path untouched during low-doubt turns (turn ${index + 1})",
                    1,
                    backend.loadCount
                )
            }

            // -----------------------------------------------------------------
            // (b) A high-doubt turn wakes the reasoning organ under ample
            // resources, serves through the fake, then the cooldown expires and
            // the sweep auto-unloads it.
            // -----------------------------------------------------------------
            val highDoubtTurn = "Why did that happen this time?"
            val b = engine.process(userText = highDoubtTurn, modelCall = { "reasoning-served: $it" })
            assertEquals(CognitiveEngine.TurnDecision.DIRECT_REPLY, b.decision)
            assertFalse("ample resources: reasoning wake is NOT degraded", b.servedDegraded!!)
            val highDoubt = policy.doubt(engine.cognitiveState.value.uncertainty)
            assertTrue("real per-turn doubt at/above threshold: $highDoubt", highDoubt >= 0.5)
            assertEquals("one resident + one reasoning load", 2, backend.loadCount)

            val reasoningHandle = backend.loaded.first { it != resident.handle }
            assertTrue("the reasoning organ is loaded after the high-doubt wake", backend.isLoaded(reasoningHandle))
            val reasoningReply = backend.generate(reasoningHandle, highDoubtTurn)
            assertTrue(
                "the reasoning organ serves through the fake backend — zero real GGUF/llama.cpp work",
                reasoningReply.startsWith("[fake-model-backend] echo:")
            )

            // Simulated time advances past the per-tier cooldown; the sweep runs.
            nowMs += 5_000L
            mm.runCooldownSweep()
            assertFalse("cooldown auto-unload invalidated the reasoning handle", backend.isLoaded(reasoningHandle))
            assertEquals("the idle on-demand organ was auto-unloaded exactly once", 1, backend.unloadCount)
            assertTrue("the RESIDENT organ is never auto-unloaded", backend.isLoaded(resident.handle))

            // -----------------------------------------------------------------
            // (c) A second high-doubt turn under injected RAM/thermal pressure is
            // DEGRADE_TO'd to the resident tier and still completes, flagged
            // internally as degraded — it never throws or hangs.
            // -----------------------------------------------------------------
            snapshot = FakeResourceSnapshot(
                availableMemoryMb = 64,
                cpuLoadPercent = 90.0,
                thermalLevel = 2,
                batteryPercent = 80,
                isCharging = true
            )
            val c = engine.process(userText = "Why did that crash again?", modelCall = { "resident-degraded: $it" })
            assertEquals(CognitiveEngine.TurnDecision.DIRECT_REPLY, c.decision)
            assertTrue(
                "governor DEGRADE_TO under pressure must be flagged internally as degraded",
                c.servedDegraded != null && c.servedDegraded!!
            )
            val pressuredDoubt = policy.doubt(engine.cognitiveState.value.uncertainty)
            assertTrue("the second high-doubt turn is genuinely high-doubt: $pressuredDoubt", pressuredDoubt >= 0.5)
            assertEquals(
                "reasoning must NOT load under pressure — the resident tier serves the degraded turn",
                2,
                backend.loadCount
            )
            assertTrue("the resident organ serves the degraded turn", backend.isLoaded(resident.handle))

            // -----------------------------------------------------------------
            // (d) Concurrent requests for the same on-demand tier share a single
            // in-flight load — no double-load for concurrent same-turn escalations.
            // -----------------------------------------------------------------
            snapshot = FakeResourceSnapshot(
                availableMemoryMb = 8192,
                cpuLoadPercent = 5.0,
                thermalLevel = 0,
                batteryPercent = 100,
                isCharging = true
            )
            val concurrent = coroutineScope {
                (1..8).map { idx ->
                    async { mm.wake(OrganRole.REASONING, task = "concurrent-turn-reasoning-$idx") }
                }.awaitAll()
            }
            assertEquals(
                "8 concurrent reasoning requests = exactly ONE new load (resident + turn-b + turn-d)",
                3,
                backend.loadCount
            )
            val handleIds = concurrent.map { it.handle.id }.distinct()
            assertEquals("all concurrent callers share the single in-flight load", 1, handleIds.size)
            assertTrue("the reasoning organ is loaded after the single-flight wake", backend.isLoaded(concurrent.first().handle))
            assertEquals(ModelTier.ON_DEMAND_REASONING, concurrent.first().servedTier)
            assertFalse("ample resources: the concurrent wake is not degraded", concurrent.first().degraded)

            // Final invariants across the whole conversation.
            assertEquals("final load count: resident + high-doubt + concurrent", 3, backend.loadCount)
            assertEquals("only the turn-(b) cooldown unload ever happened", 1, backend.unloadCount)
            assertTrue("the resident organ served the entire conversation and is still loaded", backend.isLoaded(resident.handle))
        }
}