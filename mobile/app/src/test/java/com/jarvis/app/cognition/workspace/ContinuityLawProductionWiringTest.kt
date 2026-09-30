package com.jarvis.app.cognition.workspace

import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.termux.TermuxJarvisServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CONTINUITY-LAW (Gate 3c) AC1/AC2/AC4/AC5 — the PRODUCTION-path half of the
 * acceptance evidence.
 *
 * Everything below runs against the real JVM-executable composition root,
 * [TermuxJarvisServer] — the mirror of `JarvisEngine.init` with only the
 * Android-bound stores swapped. The law, the durable log, the workspace and the
 * replay check are the objects the composition itself built, over a real data
 * directory on disk, and a real turn goes through the real [LatencyPipeline]
 * first so the state being judged is state the live path produced.
 *
 * What this proves that a unit test in isolation cannot: that the gate is wired
 * at the composition point, that its durable log lives where a restart can find
 * it, and that a second composition over the SAME data directory reads the
 * accepted change back — including the interval the rate limit is measured
 * against.
 */
class ContinuityLawProductionWiringTest {

    /**
     * The data directory is deliberately NOT a JUnit TemporaryFolder, and must
     * never become one.
     *
     * `TermuxJarvisServer` hands the FIRST data directory it is given to the
     * process-wide HumanCore singleton (`HumanCore.init` runs only while that
     * singleton is uninitialised, TermuxJarvisServer.kt:153), and the singleton
     * keeps writing to that directory for the rest of the JVM. A TemporaryFolder
     * is DELETED when its test class finishes, so every later test that touches
     * the real HumanCore then fails on
     * `FileNotFoundException: .../humancore/relationship.json.tmp`.
     *
     * That is not hypothetical: CI run 36365413999 failed 3 tests in 2 unrelated
     * classes (OwnerBiometricBindingTest, PhaseBDisconnectedSubsystemWiringTest)
     * with exactly that message, and this class was the only one in the whole
     * tree naming a `jarvis-data` folder. So the directory is unique per test
     * (which keeps each test's "no accepted change yet" assertion honest) and
     * it is left in place, the same way the second composition in
     * [the two compositions are independent] already leaves its directory.
     */
    private lateinit var dataDir: java.io.File

    private val servers = mutableListOf<TermuxJarvisServer>()

    @Before
    fun setUp() {
        dataDir = java.io.File(
            System.getProperty("java.io.tmpdir"),
            "jarvis-continuitylaw-${System.nanoTime()}"
        )
        dataDir.mkdirs()
    }

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
    }

    private fun newServer(dir: java.io.File = dataDir): TermuxJarvisServer =
        TermuxJarvisServer(
            port = 0,
            backendOverride = FakeModelBackend(),
            dataDir = dir
        ).also { servers.add(it) }

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
    fun `the production composition builds the law, the durable log and the replay check`() {
        val server = newServer()

        assertNotNull("the production composition must build the change gate", server.continuityLaw)
        assertNotNull(
            "the committed recorded turns ship with the app, so the replay check runs",
            server.replayCheck
        )
        assertTrue(
            "no accepted change means no log file — the log is written by acceptance, not pre-seeded",
            !dataDir.resolve("identity/change_log.jsonl").exists()
        )
    }

    @Test
    fun `the replay check re-renders the real recorded turns against real production state`(): Unit = runBlocking {
        val server = newServer()
        val captured = mutableListOf<String>()
        val pipeline = LatencyPipeline(
            dispatch = { it() },
            scheduleDelayed = { _, _ -> },
            bridgeSend = { captured.add(it) },
            bridgeStatus = { server.modelManager.status.value },
            beginExchange = { _, _ -> null },
            express = { reply, _ -> StyledResponse.Approved(reply) },
            completeExchange = { _, _, _, _, _ -> },
            sessionContext = { null },
            cognitiveEngine = server.engine
        )
        pipeline.onUserInput("What did we do so far?")
        assertTrue("a real turn must reach the generation seam", captured.isNotEmpty())

        val check = server.replayCheck!!
        val baseline = check.replay()
        assertTrue("the real recorded turns must replay at all", baseline.renders.isNotEmpty())
        val verdict = check.verify(baseline)
        assertTrue("a fresh production composition with zero changes must be consistent: $verdict", verdict.consistent)

        // The real CORE identity the production composition passed in, not a
        // fixture's guess: the server's SelfModel over its real HumanCore.
        val facts = baseline.render(baseline.renders.first().turnId)!!.identityFacts
        val selfModel = com.jarvis.app.identity.SelfModel(
            identitySource = com.jarvis.app.identity.HumanCoreIdentitySource(),
            capabilityRegistry = com.jarvis.app.capability.CapabilityRegistry(),
            stageHistory = com.jarvis.app.identity.StageHistorySource { emptyList() }
        )
        assertEquals("the rendered CORE name is the real SelfModel identity", selfModel.identity().name, facts["identity:name"])
        assertEquals(
            selfModel.identity().version.toString(),
            facts["identity:version"]
        )
        assertEquals(
            com.jarvis.app.identity.WorldModelService.USER_NODE_NAME,
            facts[ReplayCheck.USER_FIELD]
        )
    }

    @Test
    fun `an accepted slow change survives a restart of the real composition`() {
        val first = newServer()
        val law = first.continuityLaw!!
        val at = System.currentTimeMillis()

        assertTrue(
            "one real turn's claim is not enough",
            law.propose(persona("w-1", "directness", "direct", at)).isRejected
        )
        assertTrue(
            "a second real turn stating the same thing is",
            law.propose(persona("w-2", "directness", "direct", at + 1)).isAccepted
        )
        assertEquals("direct", law.valueOf("persona:directness"))

        val logFile = dataDir.resolve("identity/change_log.jsonl")
        assertTrue("the accepted change must be on disk", logFile.isFile)

        // A second composition over the SAME data directory: a restart, not a
        // test double.
        val restored = newServer()
        assertEquals("the accepted change is read back", "direct", restored.continuityLaw!!.valueOf("persona:directness"))
        assertEquals("with its evidence intact", 1, restored.continuityLaw!!.historyOf("persona:directness").size)

        restored.continuityLaw!!.propose(persona("w-3", "directness", "playful", at + 2))
        restored.continuityLaw!!.propose(persona("w-4", "directness", "playful", at + 3))
        // The evidence bar scales with the accepted change this field
        // already has, so the interval is once more the only remaining
        // reason a refusal could have. Too few real proposals would be
        // refused for want of evidence whether or not the interval
        // survived the restart -- a test that passes for the wrong reason
        // proves nothing.
        val tooSoon = restored.continuityLaw!!.propose(persona("w-5", "directness", "playful", at + 4))
        assertTrue(
            "the gate's real interval is measured against the RESTORED log, so an immediate rewrite is " +
                "refused: ${tooSoon.reason}",
            tooSoon.isRejected
        )
        assertEquals("direct", restored.continuityLaw!!.valueOf("persona:directness"))
    }

    @Test
    fun `a CORE identity drift injected past the gate is caught on the real composition`() {
        val server = newServer()
        val check = server.replayCheck!!
        val baseline = check.replay()

        // Bypass the law entirely: a CORE claim written straight into the
        // production workspace, which is exactly the accident ReplayCheck exists
        // to make visible.
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
        assertTrue("a CORE identity drift on the real path must be caught", !verdict.consistent)
        assertTrue(
            "the drift must name the CORE field: $verdict",
            verdict.drifts.any { it.field == "identity:name" && it.actual == "HAL-9000" }
        )
    }

    @Test
    fun `the two compositions are independent, and a fresh one starts with no accepted change`() {
        val first = newServer()
        val at = System.currentTimeMillis()
        first.continuityLaw!!.propose(persona("i-1", "directness", "direct", at))
        first.continuityLaw!!.propose(persona("i-2", "directness", "direct", at + 1))
        assertEquals("direct", first.continuityLaw!!.valueOf("persona:directness"))

        val otherDir = newServer(
            java.io.File(System.getProperty("java.io.tmpdir"), "jarvis-continuitylaw-unused-${System.nanoTime()}")
        )
        assertNull(
            "a composition over a DIFFERENT data directory shares no accepted state",
            otherDir.continuityLaw!!.valueOf("persona:directness")
        )
        assertTrue(
            "and its replay check is still clean",
            otherDir.replayCheck!!.verify(otherDir.replayCheck!!.replay()).consistent
        )
    }
}
