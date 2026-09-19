package com.jarvis.app.latency

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.cognitive.CognitiveEngine
import com.jarvis.app.cognitive.UserModel
import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.env.ModelSource
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.model.CognitiveAdmissionPolicy
import com.jarvis.app.model.FakeResourceSnapshot
import com.jarvis.app.model.ModelManager
import com.jarvis.app.model.OllamaModelBackend
import com.jarvis.app.model.OrganRole
import com.jarvis.app.model.ResourceGovernor
import com.jarvis.app.model.ResourceSnapshot
import com.jarvis.app.model.adapters.OllamaAdapter
import com.jarvis.app.model.config.ProviderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOCAL-MODEL-BACKEND-GROUND-TRUTH-AND-BUILD — AC3: a REAL user turn travels
 * the REAL production call path and comes back with the REAL model's reply.
 *
 * The live entry point a user message travels is exactly the Stage 04
 * production path:
 *
 *   LatencyPipeline.onUserInput() -> CognitiveEngine.process()
 *     (resident anchor wake -> ModelManager.wake() over the ONE ModelBackend,
 *      gated by the ResourceGovernor) -> sendBlock() -> ModelManager.send()
 *
 * Unlike Stage04LiveWiringTest (which injects the fake backend), THIS harness
 * wires the REAL [OllamaModelBackend] — real HTTP against the running Ollama
 * server at 127.0.0.1:8080 — as the ONE backend, and switches the active
 * provider to the same real Ollama transport. Every reply asserted here is the
 * model's actual output; no fake, no heuristic fallback, no echo.
 */
class OllamaModelBackendLiveWiringTest {

    /** Deterministic empty in-memory [MemoryStorePort] — no Android, no disk. */
    private class EmptyMemoryStore : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }

    /**
     * Build the full live-turn stack: real pipeline -> real engine -> real
     * ModelManager over the REAL OllamaModelBackend -> real governor with a
     * mutable injected snapshot. dispatch runs inline so a single onUserInput
     * drives the turn synchronously; the cooldown sweep is hand-triggered.
     */
    private class LiveHarness(
        val backend: OllamaModelBackend = OllamaModelBackend(
            OllamaAdapter(null).apply {
                configure(
                    ModelSource.Local("127.0.0.1:8080"),
                    ProviderConfig(modelId = "jarvis-resident:latest")
                )
            }
        )
    ) {
        var nowMs = 0L
        var snapshot: ResourceSnapshot = FakeResourceSnapshot(
            availableMemoryMb = 8192,
            cpuLoadPercent = 5.0,
            thermalLevel = 0,
            batteryPercent = 100,
            isCharging = true
        )

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
        // WorkingMemory starts an infinite decay loop on the scope it is given
        // (see STAGE-04-INTEGRATION).
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
            bridgeSend = { text -> modelManager.send(text) },
            bridgeStatus = { "idle" },
            beginExchange = { _, _ -> null },
            express = { reply, _ -> com.jarvis.app.humancore.protocol.StyledResponse.Approved(reply) },
            completeExchange = { _, _, _, _, _ -> },
            sessionContext = { null },
            cognitiveEngine = engine
        )

        init {
            // Realistic start-of-conversation: the operator is known, so
            // USER_IDENTITY "unknown" never falsely escalates an ordinary turn.
            engine.updateUserModel(UserModel(knownName = "Alice"))
        }

        /** Point the conversation send path at the real local Ollama server. */
        fun useRealActiveProvider() {
            val result = runBlocking {
                modelManager.switchProvider(
                    ModelProviderType.OLLAMA,
                    ModelSource.Local("127.0.0.1:8080"),
                    ProviderConfig(modelId = "jarvis-resident:latest")
                )
            }
            assertTrue("switching to the real Ollama provider must succeed, got: $result", result.isSuccess)
        }

        fun userMessage(text: String) {
            pipeline.onUserInput(text)
        }
    }

    @Test
    fun `AC3 a real user turn returns a real model reply through the real production call path`(): Unit =
        runBlocking {
            val harness = LiveHarness()
            val backend = harness.backend
            val mm = harness.modelManager

            harness.useRealActiveProvider()

            // Bootstrap the resident anchor through the live path's single
            // lifecycle over the REAL backend — a real registry probe.
            val resident = mm.wake(OrganRole.RESIDENT, task = "live-bootstrap")
            assertEquals("the real backend loaded the resident tier", 1, backend.loadCount)
            assertTrue("resident handle is loaded through the real backend", backend.isLoaded(resident.handle))

            // Low-doubt turn (avoids "it"/"that"/"this" — the engine's REFERENT
            // unknown uses a naive substring check that would spuriously
            // escalate).
            harness.userMessage("The weather today is quite sunny.")

            // modelManager.send() is fire-and-forget; the REAL reply lands in
            // lastReply. Cold model loads take up to ~30s on-device.
            val reply = withTimeout(120_000) {
                while (mm.lastReply.value.isBlank()) {
                    delay(500)
                }
                mm.lastReply.value
            }

            assertTrue("the live path returned a REAL non-empty reply, got '${reply.take(120)}'", reply.isNotBlank())
            assertFalse("the reply is NOT the fake-model-backend echo", reply.contains("[fake-model-backend]"))
            assertFalse(
                "the reply is NOT the heuristic canned fallback",
                reply.contains("heuristic offline mode")
            )
            // The send path went through the REAL Ollama HTTP provider, never a
            // heuristic stub: the provider switched to OLLAMA and actually
            // generated (this is the production ConversationViewModel.send path).
            assertEquals(
                "the conversation send path used the real Ollama HTTP provider",
                ModelProviderType.OLLAMA,
                mm.activeProvider.value.providerType
            )
            assertTrue("the resident anchor is still held after the turn", backend.isLoaded(resident.handle))
        }

    @Test
    fun `AC3 a high-doubt turn wakes the reasoning organ over the real backend and cooldown unloads it`(): Unit =
        runBlocking {
            val harness = LiveHarness()
            val backend = harness.backend
            val mm = harness.modelManager

            harness.useRealActiveProvider()
            val resident = mm.wake(OrganRole.RESIDENT, task = "live-bootstrap")
            assertEquals(1, backend.loadCount)

            harness.userMessage("Why did that happen this time?")
            val highDoubt = harness.policy.doubt(harness.engine.cognitiveState.value.uncertainty)
            assertTrue("the ambiguous-turn doubt is at/above threshold: $highDoubt", highDoubt >= 0.5)
            assertEquals(
                "high-doubt turn loads the reasoning organ via the real backend",
                2,
                backend.loadCount
            )
            val reasoningHandle = backend.loaded.first { it != resident.handle }
            assertTrue(
                "the reasoning organ is loaded through the real backend",
                backend.isLoaded(reasoningHandle)
            )

            // release(REASONING) at turn end + the cooldown sweep auto-unload
            // the idle reasoning organ; the resident anchor never unloads.
            harness.nowMs += 5_000L
            mm.runCooldownSweep()
            assertFalse("cooldown auto-unloads the reasoning organ", backend.isLoaded(reasoningHandle))
            assertTrue("the resident anchor is never auto-unloaded", backend.isLoaded(resident.handle))
        }
}