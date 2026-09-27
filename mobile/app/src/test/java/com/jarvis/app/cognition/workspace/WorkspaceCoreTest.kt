package com.jarvis.app.cognition.workspace

import com.jarvis.app.emotion.EmotionHypothesis
import com.jarvis.app.identity.MentalStateHypothesis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COGNITION-WORKSPACE (Gate 3c) — the claim-store half of the acceptance
 * evidence: AC2 (conflicting claims, the strongest answers, the losers stay
 * queryable), AC3 (measurable decay per decayRate, and a fresh claim overtaking
 * a decayed older one without either being deleted) and AC4 (a PREDICTION claim
 * cannot exist without a baseline and a set-once outcome slot).
 */
class WorkspaceCoreTest {

    private fun claim(
        id: String,
        confidence: Double,
        createdAt: Long = 0L,
        kind: ClaimKind = ClaimKind.INTERPRETATION,
        decayRate: Double = 0.0,
        personId: String? = null,
        sourceOrgan: String = "test.organ",
        supersedes: String? = null,
        content: String = "payload-$id"
    ) = Claim(
        id = id,
        kind = kind,
        payload = content,
        confidence = confidence,
        sourceOrgan = sourceOrgan,
        personId = personId,
        decayRate = decayRate,
        createdAt = createdAt,
        supersedes = supersedes
    )

    // ── AC2: three competing interpretations of one utterance ───────────────

    @Test
    fun `the highest-confidence of three competing claims answers current and the losers remain queryable`() {
        val workspace = InMemoryWorkspace()
        val turn = 1_000L
        // One utterance, three readings — the real case the story names.
        workspace.publish(claim("c1", confidence = 0.42, createdAt = turn))
        workspace.publish(claim("c2", confidence = 0.81, createdAt = turn))
        workspace.publish(claim("c3", confidence = 0.63, createdAt = turn))

        val current = workspace.current(ClaimKind.INTERPRETATION)
        assertNotNull("a live claim must answer current()", current)
        assertEquals("the strongest reading of the turn wins", "c2", current!!.id)

        val all = workspace.claims(ClaimKind.INTERPRETATION)
        assertEquals("no competing reading is deleted — all three stay queryable", 3, all.size)
        assertEquals(listOf("c2", "c3", "c1"), all.map { it.id })
    }

    @Test
    fun `equal-confidence claims are ordered deterministically with the newest first`() {
        val workspace = InMemoryWorkspace()
        workspace.publish(claim("older", confidence = 0.5, createdAt = 100L))
        workspace.publish(claim("newer", confidence = 0.5, createdAt = 200L))

        assertEquals("newer", workspace.current(ClaimKind.INTERPRETATION)!!.id)
        assertEquals(listOf("newer", "older"), workspace.claims(ClaimKind.INTERPRETATION).map { it.id })
    }

    @Test
    fun `claims of another kind are not visible through this kind's query`() {
        val workspace = InMemoryWorkspace()
        workspace.publish(claim("mental", confidence = 0.9, kind = ClaimKind.MENTAL_STATE))
        workspace.publish(claim("window", confidence = 0.9, kind = ClaimKind.CONTEXT_WINDOW))

        assertEquals(listOf("mental"), workspace.claims(ClaimKind.MENTAL_STATE).map { it.id })
        assertEquals(listOf("window"), workspace.claims(ClaimKind.CONTEXT_WINDOW).map { it.id })
        assertNull(workspace.current(ClaimKind.INTERPRETATION))
    }

    @Test
    fun `person scoping is reserved in the schema and filterable once set`() {
        val workspace = InMemoryWorkspace()
        workspace.publish(claim("unscoped", confidence = 0.9, personId = null))
        workspace.publish(claim("scoped", confidence = 0.95, personId = "person-a"))

        // No person filter sees both; a person filter sees only that person's.
        assertEquals(2, workspace.claims(ClaimKind.INTERPRETATION).size)
        assertEquals(listOf("scoped"), workspace.claims(ClaimKind.INTERPRETATION, "person-a").map { it.id })
        assertEquals(
            "a null personId on a query means no filter, not the unscoped bucket",
            "scoped",
            workspace.current(ClaimKind.INTERPRETATION)!!.id
        )
    }

    // ── AC3: decay is measurable, and a fresh claim overtakes a decayed one ─

    @Test
    fun `confidence decays by decayRate per tick and a fresh claim overtakes the decayed one`() {
        val workspace = InMemoryWorkspace()
        workspace.publish(claim("old", confidence = 0.9, createdAt = 0L, decayRate = 0.1))

        val start = 10_000L
        workspace.tick(start)
        assertEquals(
            "the first tick only starts the decay clock",
            0.9,
            workspace.current(ClaimKind.INTERPRETATION)!!.confidence,
            1e-9
        )

        workspace.tick(start + 3 * Workspace.TICK_INTERVAL_MS)
        val decayed = workspace.current(ClaimKind.INTERPRETATION)!!
        assertEquals("three elapsed intervals cost 3 x decayRate", 0.6, decayed.confidence, 1e-9)
        assertTrue("the claim is still live at 0.6", decayed.isLive())

        // A fresh, stronger reading of the same kind arrives later.
        workspace.publish(claim("new", confidence = 0.7, createdAt = start + 3_001L))

        assertEquals(
            "the fresh claim overtakes the decayed older one",
            "new",
            workspace.current(ClaimKind.INTERPRETATION)!!.id
        )
        assertEquals(
            "the decayed claim is overtaken, never deleted",
            listOf("new", "old"),
            workspace.claims(ClaimKind.INTERPRETATION).map { it.id }
        )
    }

    @Test
    fun `a claim decayed to zero stops answering current but is still retained`() {
        val workspace = InMemoryWorkspace()
        workspace.publish(claim("fading", confidence = 0.2, decayRate = 0.1))

        val start = 50_000L
        workspace.tick(start)
        workspace.tick(start + 2 * Workspace.TICK_INTERVAL_MS)

        assertEquals(0.0, workspace.claims(ClaimKind.INTERPRETATION).single().confidence, 1e-9)
        assertNull("a zero-confidence claim no longer answers current()", workspace.current(ClaimKind.INTERPRETATION))
        assertFalse(workspace.claims(ClaimKind.INTERPRETATION).single().isLive())
    }

    @Test
    fun `a tick that moves backwards does not rewind or decay anything`() {
        val workspace = InMemoryWorkspace()
        workspace.publish(claim("steady", confidence = 0.7, decayRate = 0.5))

        workspace.tick(20_000L)
        workspace.tick(5_000L)
        assertEquals(0.7, workspace.current(ClaimKind.INTERPRETATION)!!.confidence, 1e-9)

        // The clock still moves forward from the last real instant.
        workspace.tick(20_000L + Workspace.TICK_INTERVAL_MS)
        assertEquals(0.2, workspace.current(ClaimKind.INTERPRETATION)!!.confidence, 1e-9)
    }

    @Test
    fun `a superseding claim retires the previous one without deleting it`() {
        val workspace = InMemoryWorkspace()
        workspace.publish(claim("turn-1", confidence = 0.9, createdAt = 1L))
        workspace.publish(claim("turn-2", confidence = 0.9, createdAt = 2L, supersedes = "turn-1"))

        assertEquals("turn-2", workspace.current(ClaimKind.INTERPRETATION)!!.id)
        assertEquals("both turns stay queryable", 2, workspace.claims(ClaimKind.INTERPRETATION).size)
        assertFalse(
            "the superseded claim is retained, just no longer live",
            workspace.claims(ClaimKind.INTERPRETATION).first { it.id == "turn-1" }.isLive(superseded = true)
        )
    }

    // ── AC4: a prediction must be able to prove it beat the naive baseline ──

    @Test
    fun `a PREDICTION claim cannot be constructed without the naive baseline`() {
        val failure = runCatching {
            Claim(
                id = "prediction-1",
                kind = ClaimKind.PREDICTION,
                payload = "the user will ask about the build",
                confidence = 0.6,
                sourceOrgan = "test.organ",
                createdAt = 0L,
                baseline = null
            )
        }.exceptionOrNull()

        assertNotNull("a prediction with no baseline must fail the schema check", failure)
        assertTrue(
            "the failure must be the schema check, not something incidental",
            failure is IllegalArgumentException && failure.message!!.contains("baseline")
        )
    }

    @Test
    fun `a prediction cannot hide its baseline behind a non-predictive kind`() {
        val failure = runCatching {
            claim("sneaky", confidence = 0.6).copy(baseline = "ask about the build")
        }.exceptionOrNull()
        assertNotNull("only the PREDICTION kind may carry a baseline", failure)
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `a prediction outcome is settled once by a real later turn and is then final`() {
        val workspace = InMemoryWorkspace()
        val prediction = Claim.prediction(
            id = "prediction-1",
            payload = "the user will ask about the build",
            confidence = 0.6,
            sourceOrgan = "test.organ",
            baseline = "ask about the weather",
            createdAt = 1L
        )
        workspace.publish(prediction)

        assertTrue("a fresh prediction is unresolved", !prediction.isResolved)

        val confirmed = prediction.resolved(correct = true)
        workspace.publish(confirmed)
        assertEquals(true, workspace.claims(ClaimKind.PREDICTION).single().wasCorrect)
        assertTrue(workspace.claims(ClaimKind.PREDICTION).single().isResolved)

        // A later turn cannot rewrite a settled outcome.
        val rewritten = runCatching { workspace.publish(prediction.resolved(correct = false)) }
        assertNotNull("a settled prediction outcome must stay settled", rewritten.exceptionOrNull())
        assertTrue(rewritten.exceptionOrNull() is IllegalArgumentException)
        assertEquals(
            "the settled outcome is unchanged",
            true,
            workspace.claims(ClaimKind.PREDICTION).single().wasCorrect
        )
    }

    @Test
    fun `a prediction outcome can still be settled after its confidence has decayed`() {
        val workspace = InMemoryWorkspace()
        val prediction = Claim.prediction(
            id = "prediction-1",
            payload = "the user will ask about the build",
            confidence = 1.0,
            sourceOrgan = "test.organ",
            baseline = "ask about the weather",
            createdAt = 0L,
            decayRate = 0.5
        )
        workspace.publish(prediction)
        workspace.tick(1_000L)
        workspace.tick(1_000L + 2 * Workspace.TICK_INTERVAL_MS)
        assertEquals("the stored claim has decayed", 0.0, workspace.claims(ClaimKind.PREDICTION).single().confidence, 1e-9)

        // The outcome belongs to the claim, not to its current confidence, so a
        // real later turn can still prove or disprove it.
        workspace.publish(prediction.resolved(correct = false))
        assertEquals(false, workspace.claims(ClaimKind.PREDICTION).single().wasCorrect)
    }

    @Test
    fun `an out-of-range or unattributed claim is rejected by the schema`() {
        assertNotNull(runCatching { claim("x", confidence = 1.4) }.exceptionOrNull())
        assertNotNull(runCatching { claim("x", confidence = -0.1) }.exceptionOrNull())
        assertNotNull(runCatching { claim("x", confidence = Double.NaN) }.exceptionOrNull())
        assertNotNull(runCatching { claim("x", confidence = 0.5, decayRate = -1.0) }.exceptionOrNull())
        assertNotNull(runCatching { claim("x", confidence = 0.5, sourceOrgan = " ") }.exceptionOrNull())
        assertNotNull(runCatching { claim("x", confidence = 0.5, supersedes = "x") }.exceptionOrNull())
    }

    @Test
    fun `republishing an id with different content is refused`() {
        val workspace = InMemoryWorkspace()
        workspace.publish(claim("fixed-id", confidence = 0.5, content = "first"))
        val failure = runCatching {
            workspace.publish(claim("fixed-id", confidence = 0.5, content = "second"))
        }.exceptionOrNull()
        assertNotNull("a claim id is its identity and cannot be reused for other content", failure)
        assertTrue(failure is IllegalArgumentException)
    }

    // ── the shared mental-state vocabulary both migrated organs rely on ──────

    @Test
    fun `a mental state survives the claim round trip byte for byte`() {
        val hypothesis = MentalStateHypothesis.fromEmotion(
            EmotionHypothesis(
                valence = -0.42,
                arousal = 0.61,
                dominance = 0.18,
                tension = 0.73,
                confidence = 0.88,
                likelyState = "frustrated",
                alternatives = listOf("anxious", "urgent"),
                evidence = listOf("frustrat", "stuck"),
                temporalTrend = "escalating"
            )
        )

        val workspace = InMemoryWorkspace()
        val published = MentalStateClaims.publish(workspace, hypothesis, createdAt = 42L)

        assertEquals(ClaimKind.MENTAL_STATE, published.kind)
        assertEquals("identity.mentalStateEstimator", published.sourceOrgan)
        assertEquals(
            "an anchored reading publishes its OWN confidence, not a declared prior",
            hypothesis.emotion.confidence,
            published.confidence,
            1e-9
        )
        assertEquals(
            "a reader gets back exactly what the estimator returned",
            hypothesis,
            MentalStateClaims.read(workspace)
        )
    }

    @Test
    fun `an unanchored rule-based reading publishes the declared prior, not a dead claim`() {
        val neutral = MentalStateHypothesis.neutral()
        assertEquals(0.0, neutral.emotion.confidence, 1e-9)

        val workspace = InMemoryWorkspace()
        MentalStateClaims.publish(workspace, neutral, createdAt = 7L)

        val claim = workspace.current(ClaimKind.MENTAL_STATE)!!
        assertEquals(MentalStateClaims.UNANCHORED_CONFIDENCE, claim.confidence, 1e-9)
        assertTrue("the declared prior is a live, readable claim", claim.isLive())
        assertEquals(neutral, MentalStateClaims.read(workspace))
    }

    @Test
    fun `a new turn's reading supersedes the previous one instead of deleting it`() {
        val workspace = InMemoryWorkspace()
        val first = MentalStateClaims.publish(
            workspace,
            MentalStateHypothesis("seek information", "curious", "wants a clear answer"),
            createdAt = 1L
        )
        val second = MentalStateClaims.publish(
            workspace,
            MentalStateHypothesis("get quick action", "urgent", "wants speed"),
            createdAt = 2L
        )

        assertEquals("turn 2 supersedes turn 1", first.id, second.supersedes)
        assertEquals("turn 2 is the live reading", second.id, workspace.current(ClaimKind.MENTAL_STATE)!!.id)
        assertEquals("both turns stay queryable", 2, workspace.claims(ClaimKind.MENTAL_STATE).size)
        assertEquals(
            "curious",
            MentalStateClaims.decode(workspace.claims(ClaimKind.MENTAL_STATE).first { it.id == first.id }.payload)!!.mood
        )
    }

    @Test
    fun `a payload this vocabulary did not write decodes to null rather than a guess`() {
        assertNull(MentalStateClaims.decode("not json at all"))
        assertNull(MentalStateClaims.decode("""{"goal":"only-half"}"""))
    }

    @Test
    fun `an empty assembled window still publishes a real non-blank claim`() {
        // A turn that has assembled nothing yet is a real case: a claim must
        // never be blank, and publishing must never throw mid-turn.
        val workspace = InMemoryWorkspace()
        val assembler = com.jarvis.app.cognitive.ContextWindowAssembler(
            topicTracker = com.jarvis.app.cognitive.TopicTracker(),
            salienceScorer = com.jarvis.app.cognitive.SalienceScorer(
                com.jarvis.app.cognitive.ReferenceStore()
            ),
            workspace = workspace
        )

        val window = assembler.assemble(0, 0, "")

        assertNull(window.mentalState)
        val published = workspace.current(ClaimKind.CONTEXT_WINDOW)
        assertNotNull("an empty window still publishes its claim", published)
        assertTrue("the claim payload must not be blank", published!!.payload.isNotBlank())
        assertEquals("cognitive.contextWindowAssembler", published.sourceOrgan)
    }

    /** Claim ids are minted, so ids stay unique even within one millisecond. */
    @Test
    fun `claim ids are unique per publish`() {
        assertTrue(MentalStateClaims.nextId() != MentalStateClaims.nextId())
        assertEquals(ClaimKind.MENTAL_STATE, MentalStateClaims.KIND)
        assertEquals(ClaimKind.PREDICTION, ClaimKind.PREDICTION)
        assertTrue("only the PREDICTION kind is tagged predictive", ClaimKind.PREDICTION.isPrediction)
        assertFalse(ClaimKind.MENTAL_STATE.isPrediction)
        assertEquals(ClaimKind.PREDICTION, ClaimKind.fromName("PREDICTION"))
        assertNull(ClaimKind.fromName("NOT_A_KIND"))
    }
}
