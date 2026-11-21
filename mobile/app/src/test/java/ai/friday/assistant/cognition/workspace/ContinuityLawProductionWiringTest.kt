package ai.friday.assistant.cognition.workspace

import ai.friday.assistant.jvmtest.JvmCompositionHarness
import ai.friday.assistant.model.FakeModelBackend
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CONTINUITY-LAW (Gate 3c) production-path evidence.
 *
 * All turns use the single LatencyPipeline created by TurnPathAssembly through
 * JvmCompositionHarness. No second CognitiveEngine or LatencyPipeline is built
 * in this test. Restart checks create a second composition over the same data
 * directory to prove durable state restoration.
 */
class ContinuityLawProductionWiringTest {

    private lateinit var dataDir: java.io.File
    private val servers = mutableListOf<JvmCompositionHarness>()
    private val backends = mutableListOf<FakeModelBackend>()

    @Before
    fun setUp() {
        dataDir = java.io.File(
            System.getProperty("java.io.tmpdir"),
            "friday-continuitylaw-${System.nanoTime()}"
        )
        dataDir.mkdirs()
    }

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
    }

    private fun newServer(dir: java.io.File = dataDir): JvmCompositionHarness {
        val backend = FakeModelBackend()
        backends.add(backend)
        return JvmCompositionHarness(
            port = 0,
            backendOverride = backend,
            dataDir = dir
        ).also { servers.add(it) }
    }

    private fun persona(id: String, trait: String, value: String, at: Long): Claim = Claim(
        id = id,
        kind = ClaimKind.PERSONA_TRAIT,
        payload = value,
        confidence = 0.6,
        sourceOrgan = "identity.personaTuner",
        createdAt = at,
        target = "persona:$trait"
    )

    @Test
    fun `the production composition builds the law the durable log and the replay check`() {
        val server = newServer()
        assertNotNull("the production composition must build the change gate", server.continuityLaw)
        assertNotNull("the production composition must build the replay check", server.replayCheck)
        assertTrue(
            "no accepted change means no log file — the log is written by acceptance, not pre-seeded",
            !dataDir.resolve("identity/change_log.jsonl").exists()
        )
    }

    @Test
    fun `the replay check re-renders real recorded turns against production state`() = runBlocking {
        val server = newServer()
        val backend = backends.last()
        server.bootstrap()
        server.pipeline.onUserInput("What did we do so far?")
        withTimeout(5_000) {
            while (backend.generateCount < 1) delay(10)
        }
        assertTrue("a real turn must reach the configured model backend", backend.generateCount > 0)

        val check = server.replayCheck!!
        val baseline = check.replay()
        assertTrue("the real recorded turns must replay at all", baseline.renders.isNotEmpty())
        val verdict = check.verify(baseline)
        assertTrue("a fresh production composition with zero changes must be consistent: $verdict", verdict.consistent)

        // Compare against the exact Friday SelfModel owned by this composition.
        val facts = baseline.render(baseline.renders.first().turnId)!!.identityFacts
        val selfModel = server.selfModel
        assertEquals("the rendered Friday name is the real SelfModel identity", selfModel.identity().name, facts["identity:name"])
        assertEquals(selfModel.identity().version.toString(), facts["identity:version"])
        assertEquals(ai.friday.assistant.identity.WorldModelService.USER_NODE_NAME, facts[ReplayCheck.USER_FIELD])
    }

    @Test
    fun `an accepted slow change survives a restart of the real composition`() {
        val first = newServer()
        val law = first.continuityLaw!!
        val at = System.currentTimeMillis()

        assertTrue("one real turn's claim is not enough", law.propose(persona("w-1", "directness", "direct", at)).isRejected)
        assertTrue("a second real turn stating the same thing is", law.propose(persona("w-2", "directness", "direct", at + 1)).isAccepted)
        assertEquals("direct", law.valueOf("persona:directness"))

        val logFile = dataDir.resolve("identity/change_log.jsonl")
        assertTrue("the accepted change must be on disk", logFile.isFile)

        val restored = newServer()
        assertEquals("the accepted change is read back", "direct", restored.continuityLaw!!.valueOf("persona:directness"))
        assertEquals("with its evidence intact", 1, restored.continuityLaw!!.historyOf("persona:directness").size)

        restored.continuityLaw!!.propose(persona("w-3", "directness", "playful", at + 2))
        val tooSoon = restored.continuityLaw!!.propose(persona("w-4", "directness", "playful", at + 3))
        assertTrue("an immediate rewrite is refused against the restored log: ${tooSoon.reason}", tooSoon.isRejected)
        assertEquals("direct", restored.continuityLaw!!.valueOf("persona:directness"))
    }

    @Test
    fun `a core identity drift injected past the gate is caught on the real composition`() {
        val server = newServer()
        val check = server.replayCheck!!
        val baseline = check.replay()

        server.workspace.publish(
            Claim(
                id = "bypass-1",
                kind = ClaimKind.SELF_MODEL,
                payload = "HAL-9000",
                confidence = 1.0,
                sourceOrgan = "somebody.who.skipped.the.gate",
                createdAt = System.currentTimeMillis(),
                target = "name"
            )
        )

        val verdict = check.verify(baseline, check.replay())
        assertTrue("a core identity drift on the real path must be caught", !verdict.consistent)
        assertTrue(
            "the drift must name the identity field: $verdict",
            verdict.drifts.any { it.field == "identity:name" && it.actual == "HAL-9000" }
        )
    }

    @Test
    fun `two compositions are independent and a fresh one starts with no accepted change`() {
        val first = newServer()
        val at = System.currentTimeMillis()
        first.continuityLaw!!.propose(persona("i-1", "directness", "direct", at))
        first.continuityLaw!!.propose(persona("i-2", "directness", "direct", at + 1))
        assertEquals("direct", first.continuityLaw!!.valueOf("persona:directness"))

        val otherDir = java.io.File(
            System.getProperty("java.io.tmpdir"),
            "friday-continuitylaw-unused-${System.nanoTime()}"
        )
        val other = newServer(otherDir)
        assertNull("a composition over a different directory shares no accepted state", other.continuityLaw!!.valueOf("persona:directness"))
        assertTrue("and its replay check is still clean", other.replayCheck!!.verify(other.replayCheck!!.replay()).consistent)
    }
}
