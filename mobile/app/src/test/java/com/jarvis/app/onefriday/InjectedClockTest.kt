package com.jarvis.app.onefriday

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.cognition.workspace.Claim
import com.jarvis.app.cognition.workspace.ClaimKind
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.model.ModelTier
import com.jarvis.app.model.OrganRole
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ONE-2-COMPOSITION, AC3: ONE injected time source.
 *
 * The claim under test is not "time is configurable" — it is that a 72-hour jump
 * on the HOST's clock moves three time-dependent behaviours that would not move
 * if any of them were still reading the wall clock behind the composition root's
 * back. Each of the three below therefore FAILS against the pre-story wiring:
 * with `System.currentTimeMillis()` in those three places, 72 hours never elapse
 * during a test that takes milliseconds, so every "after 72 hours" assertion
 * below would read as "no time passed" and fail.
 *
 * The three behaviours, in the order the vision names them:
 *  1. the spoken-ack gap (the turn's own elapsed-time gate),
 *  2. the model tier cooldown half-life (how long a loaded organ stays loaded),
 *  3. the SLOW layer's rate limit (how fast durable self-state may change).
 *
 * Only the host's clock is faked. Every organ, seam and gate under test is the
 * production class built by [TurnPathAssembly] — the same root the phone uses.
 */
class InjectedClockTest {

    /** 72 hours, the jump the AC names. */
    private val seventyTwoHours = 72L * 60L * 60L * 1000L

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

    private class Fixture {
        val acks: MutableList<String> = mutableListOf()

        fun ports(clock: () -> Long): JvmPlatformPorts = JvmPlatformPorts(
            storageDir = compositionDir("clock"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            stores = JvmPlatformStores(
                graphStore = TermuxJarvisServer.InMemoryGraph(),
                embeddingProvider = TermuxJarvisServer.DeterministicEmbedding(),
                memoryStore = NoMemories(),
                backend = FakeModelBackend(),
                voiceSynthesizer = SilentSpeech()
            ),
            snapshot = { ResourceSnapshot.alwaysHealthy() },
            clock = clock,
            humanCoreStorage = FileStorage(compositionDir("clock-humancore")),
            // The slow path is deliberately NOT run here: every behaviour under
            // test is a fast-path or organ-level clock rule, and isolating them
            // keeps this test about the clock rather than about the engine.
            turnWork = { },
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

    @Test
    fun `AC3 one injected clock - 72 hours moves the ack gap, the model half-life and the slow-layer rate limit`() =
        runBlocking<Unit> {
            val fixture = Fixture()
            var now = 1_000_000L
            val assembly = TurnPathAssembly.assemble(
                fixture.ports({ ResourceSnapshot.alwaysHealthy() }, { now })
            )

            // ── 1. The spoken-ack gap: a frozen clock speaks one ack ────────────
            repeat(3) { assembly.pipeline.onUserInput("hello $it") }
            assertEquals(
                "with the host clock frozen, three turns inside the ack gap speak one ack",
                1,
                fixture.acks.size
            )

            // ── 2. The model tier half-life: nothing idles out while frozen ────
            assembly.modelManager.request(OrganRole.REASONING, task = "clock test")
            assembly.modelManager.release(OrganRole.REASONING)
            assembly.modelManager.runCooldownSweep()
            assertTrue(
                "the reasoning tier was released just now, so the cooldown cannot have " +
                    "elapsed on any clock",
                assembly.modelManager.isTierLoaded(ModelTier.ON_DEMAND_REASONING)
            )

            // ── 3. The SLOW layer: two claims, then the rate limit bites ──────
            val law = assembly.continuityLaw
            assertTrue(
                "one turn is a suggestion, not a change",
                law.propose(persona("clock-1", "direct")).isRejected
            )
            assertTrue(
                "two real claims on the same value are accepted",
                law.propose(persona("clock-2", "direct")).isAccepted
            )
            assertTrue(
                "a third claim inside the interval is rate limited",
                law.propose(persona("clock-3", "direct")).isRejected
            )

            // ── The jump: 72 hours on the HOST's clock, not on the wall clock ──
            now += seventyTwoHours

            assembly.pipeline.onUserInput("hello after the gap")
            assertEquals(
                "72 host-hours past the ack gap the next turn speaks again. On a wall " +
                    "clock this turn lands milliseconds after the first and stays silent, " +
                    "which is exactly the failure this test exists to catch.",
                2,
                fixture.acks.size
            )

            assembly.modelManager.runCooldownSweep()
            assertFalse(
                "72 host-hours past the release the idle on-demand tier is swept. A wall " +
                    "clock would still read zero elapsed and keep the model loaded.",
                assembly.modelManager.isTierLoaded(ModelTier.ON_DEMAND_REASONING)
            )

            assertTrue(
                "72 host-hours past the accepted change the same value may be accepted " +
                    "again, so the rate limit is a real elapsed-time rule and not a counter",
                law.propose(persona("clock-4", "direct")).isAccepted
            )
        }

    /** A persona claim shaped like the real PersonaTuner proposal. */
    private fun persona(id: String, value: String) = Claim(
        id = id,
        kind = ClaimKind.PERSONA_TRAIT,
        payload = value,
        confidence = 0.6,
        sourceOrgan = "identity.personaTuner",
        createdAt = 1_000_000L,
        target = "persona:directness"
    )
}
