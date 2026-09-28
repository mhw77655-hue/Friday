package com.jarvis.app.cognition.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * CONTINUITY-LAW (Gate 3c) AC1–AC5 — the behaviour of the change gate, the
 * durable log and the replay check, proven on the real classes.
 *
 * Nothing here is a stub: the store is the real [InMemoryWorkspace] the
 * production composition wires, the log is the real [FileChangeLog] writing real
 * JSONL to a real temp file, and the replay check renders the ONE committed
 * recorded-turn fixture ([RecordedTurns.fromClasspath]) rather than turns invented
 * inside the test.
 *
 * The clock is the one thing injected ([ContinuityLaw.now]) — a real elapsed-time
 * rule cannot be tested honestly against a wall clock, and injecting it is what
 * makes the "real time" claim falsifiable: the durable-log test proves the
 * interval is read back off the DISK after a restart, not from an in-memory
 * counter.
 */
class ContinuityLawTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var workspace: InMemoryWorkspace
    private lateinit var log: FileChangeLog
    private var now: Long = 1_000L
    private lateinit var law: ContinuityLaw

    @Before
    fun setUp() {
        workspace = InMemoryWorkspace()
        log = FileChangeLog(temp.newFolder("state").resolve("identity/change_log.jsonl"))
        now = 1_000L
        law = ContinuityLaw(workspace, log, now = { now })
    }

    // ── helpers: claims shaped like the real subsystems that would propose them ──

    private fun persona(id: String, trait: String, value: String, at: Long = now): Claim = Claim(
        id = id,
        kind = ClaimKind.PERSONA_TRAIT,
        payload = value,
        confidence = 0.6,
        sourceOrgan = "identity.personaTuner",
        createdAt = at,
        target = "persona:$trait"
    )

    private fun trust(id: String, person: String, tier: String, at: Long = now): Claim = Claim(
        id = id,
        kind = ClaimKind.TRUST_TIER,
        payload = tier,
        confidence = 0.6,
        sourceOrgan = "social.personRelationshipModel",
        createdAt = at,
        target = "relationship:$person:trust"
    )

    /** A SELF_MODEL claim about a field SelfModel derives from a live source. */
    private fun selfModel(id: String, field: String, value: String, at: Long = now): Claim = Claim(
        id = id,
        kind = ClaimKind.SELF_MODEL,
        payload = value,
        confidence = 0.9,
        sourceOrgan = "identity.selfModel",
        createdAt = at,
        target = field
    )

    private fun coreIdentity(): CoreIdentity = CoreIdentity(
        name = "JARVIS",
        version = 1,
        userNodeName = com.jarvis.app.identity.WorldModelService.USER_NODE_NAME
    )

    private fun replayCheck(): ReplayCheck = ReplayCheck(
        workspace = workspace,
        changeLog = log,
        coreIdentity = coreIdentity(),
        fixture = recordedTurns()
    )

    /** The first committed recorded turn's id — never hard-coded, so the fixture can grow. */
    private fun firstTurnId(): String = recordedTurns().turns.first().turnId

    // ── AC1: evidence count, on the real path ───────────────────────────────────

    @Test
    fun `AC1 a slow change with insufficient evidence is rejected, and the same change is accepted once the evidence is real`() {
        val first = law.propose(persona("c-1", "directness", "direct"))
        assertEquals(ChangeLayer.SLOW, first.layer)
        assertTrue("one turn is a suggestion, not a change", first.isRejected)
        assertNull("a rejected change logs nothing", first.entry)
        assertEquals(0L, log.count())
        assertEquals(
            "the rejected proposal is RETAINED as evidence, at the evidence confidence",
            listOf("c-1"),
            workspace.claims(ClaimKind.PERSONA_TRAIT).map { it.id }
        )
        assertEquals(
            ContinuityLaw.EVIDENCE_CONFIDENCE,
            workspace.claims(ClaimKind.PERSONA_TRAIT).single().confidence,
            1e-9
        )
        assertNull("an unaccepted change is not the accepted value", law.valueOf("persona:directness"))

        val second = law.propose(persona("c-2", "directness", "direct"))
        assertTrue("the same change, proposed again on real evidence, is accepted", second.isAccepted)
        assertEquals(listOf("c-1", "c-2"), second.evidenceClaimIds)
        assertEquals("direct", law.valueOf("persona:directness"))
        assertEquals(1L, log.count())
    }

    @Test
    fun `AC1 the same claim id offered twice is a retry, not two opinions`() {
        assertTrue(law.propose(persona("c-1", "directness", "direct")).isRejected)
        val retry = law.propose(persona("c-1", "directness", "direct"))
        assertTrue("a retry must not manufacture a second piece of evidence", retry.isRejected)
        assertEquals(listOf("c-1"), retry.evidenceClaimIds)
        assertEquals(0L, log.count())
    }

    @Test
    fun `AC1 the rate limit is real elapsed time, not a call count`() {
        law.propose(persona("c-1", "directness", "direct"))
        val accepted = law.propose(persona("c-2", "directness", "direct"))
        assertTrue(accepted.isAccepted)

        // A new value for the SAME field, with its evidence complete, proposed in
        // the same instant: evidence is no longer the constraint — real time is.
        law.propose(persona("c-3", "directness", "playful"))
        val tooSoon = law.propose(persona("c-4", "directness", "playful"))
        assertTrue("a second accepted change to one field inside the interval is refused", tooSoon.isRejected)
        assertTrue("the refusal says why: ${tooSoon.reason}", tooSoon.reason.contains("rate limited"))
        assertEquals("the accepted value is untouched by the refused change", "direct", law.valueOf("persona:directness"))
        assertEquals(1L, log.count())

        now += ContinuityLaw.DEFAULT_MIN_INTERVAL_MS
        val later = law.propose(persona("c-5", "directness", "playful"))
        assertTrue("after the real interval has passed, the change is accepted", later.isAccepted)
        assertEquals("playful", law.valueOf("persona:directness"))
        assertEquals(2L, log.count())
    }

    // ── AC2: the accepted change is queryable, and it survives a restart ────────

    @Test
    fun `AC2 every accepted slow change records old value, new value, evidence and a real timestamp`() {
        law.propose(persona("c-1", "directness", "direct"))
        now += 10_000L
        law.propose(persona("c-2", "directness", "direct"))
        val first = log.entriesFor("persona:directness").single()
        assertNull("the field was unset before the first accepted change", first.oldValue)
        assertEquals("direct", first.newValue)
        assertEquals(listOf("c-1", "c-2"), first.evidenceClaimIds)
        assertEquals("c-2", first.acceptedClaimId)
        // The clock is the ONLY thing injected, so the recorded instant is
        // exactly where that clock stood: 1_000 at the first proposal, +10_000
        // here. A literal that disagrees with the fixture would be asserting
        // something the gate never did.
        assertEquals("the entry carries the real instant the gate accepted at", 11_000L, first.timestamp)
        assertEquals(ChangeLayer.SLOW, first.layer)

        now += 10_000L
        law.propose(persona("c-3", "directness", "playful"))
        law.propose(persona("c-4", "directness", "playful"))
        val second = log.entriesFor("persona:directness").last()
        assertEquals("an accepted change records the value it REPLACED", "direct", second.oldValue)
        assertEquals("playful", second.newValue)
        assertEquals("the full history is queryable, oldest first", 2, law.historyOf("persona:directness").size)
        assertEquals(2L, log.count())
    }

    @Test
    fun `AC2 the log is durable, and the rate limit is read back off the disk after a restart`() {
        law.propose(persona("c-1", "directness", "direct"))
        now += 10_000L
        law.propose(persona("c-2", "directness", "direct"))

        // A NEW process: a new log and a new law over the SAME file. The
        // workspace starts empty, exactly as it would after a process death.
        val restoredLog = FileChangeLog(log.changeLogFile)
        val restored = ContinuityLaw(InMemoryWorkspace(), restoredLog, now = { now })
        // EXACTLY ONE change was accepted above (c-1 is a suggestion, not a
        // change), so the file holds one line. The count is the number of
        // accepted changes on disk, not the number of proposals.
        assertEquals("a restart reads back every accepted change", 1L, restoredLog.count())
        assertEquals(
            "no line was silently dropped by the reader",
            restoredLog.count(),
            restoredLog.entries().size.toLong()
        )
        assertEquals("direct", restored.valueOf("persona:directness"))
        assertEquals(11_000L, restoredLog.lastChangeAt("persona:directness"))

        // TWO real proposals, so the evidence count is satisfied and the interval
        // is the only remaining reason a refusal could have. One proposal would
        // be refused for want of evidence whether or not the interval survived —
        // a test that passes for the wrong reason proves nothing.
        restored.propose(persona("c-3", "directness", "playful"))
        val refused = restored.propose(persona("c-4", "directness", "playful"))
        assertTrue(
            "the interval survived the restart — an in-memory counter would have forgotten it",
            refused.isRejected
        )
        assertTrue(
            "and it was refused for the interval, not for evidence: ${refused.reason}",
            refused.reason.contains("rate limited")
        )
        assertEquals(1L, restoredLog.count())
    }

    // ── AC3: CORE is rejected, whatever the evidence ────────────────────────────

    @Test
    fun `AC3 a CORE field change is rejected outright, however much evidence it is offered`() {
        val drift = selfModel("core-1", "name", "HAL-9000")
        assertEquals(ChangeLayer.CORE, drift.layer)
        val decision = law.propose(drift)
        assertTrue("a CORE change never passes this gate", decision.isRejected)
        assertTrue("the refusal names the reason", decision.reason.contains("CORE"))
        assertNull(decision.entry)
        assertEquals("nothing was retained — not even as evidence", 0, workspace.claims(ClaimKind.SELF_MODEL).size)
        assertEquals(0L, log.count())

        // Pile the evidence up: the same CORE claim, over and over. It must still
        // be refused, because CORE is not a threshold, it is a refusal.
        now += 60_000L
        repeat(5) { i -> assertTrue(law.propose(selfModel("core-${i + 2}", "name", "HAL-9000")).isRejected) }
        assertEquals(0, workspace.claims(ClaimKind.SELF_MODEL).size)
        assertEquals(0L, log.count())

        val versionDrift = selfModel("core-v", "version", "99")
        assertEquals(ChangeLayer.CORE, versionDrift.layer)
        assertTrue(law.propose(versionDrift).isRejected)
    }

    @Test
    fun `a change the gate cannot classify is an error, not a fast pass`() {
        // The claim is CONSTRUCTED inside the try on purpose: the refusal is a
        // construction-time schema check (Claim's init resolves the layer), so a
        // test that built the claim outside the try would let the real
        // IllegalArgumentException escape and report a code failure for a refusal
        // the code is supposed to make.
        try {
            val noField = Claim(
                id = "x-1",
                kind = ClaimKind.PERSONA_TRAIT,
                payload = "direct",
                confidence = 0.5,
                sourceOrgan = "identity.personaTuner",
                createdAt = now
            )
            noField.layer
            fail("an identity-adjacent claim with no field must not be constructible")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("must name the field"))
        }

        // A target outside the real predicate vocabulary of its kind is equally
        // refused — the invented "trust:Alice" of the first draft never existed.
        try {
            trust("x-2", "Alice", "KNOWN").copy(target = "trust:Alice")
            fail("an invented trust predicate must not be classifiable")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("relationship:"))
        }
    }

    @Test
    fun `FAST self-model state moves freely and is never logged`() {
        val decision = law.propose(selfModel("f-1", "capability:voice.synthesize", "active"))
        assertEquals(ChangeLayer.FAST, decision.layer)
        assertTrue(decision.isAccepted)
        assertNull("an unrestricted change produces no log entry", decision.entry)
        assertEquals(0L, log.count())
        assertEquals("active", workspace.current(ClaimKind.SELF_MODEL)?.payload)
        assertEquals(0.9, workspace.current(ClaimKind.SELF_MODEL)!!.confidence, 1e-9)
    }

    // ── AC4: the replay check passes on a clean baseline and after real changes ─

    @Test
    fun `AC4 the replay suite passes with zero changes and still passes after legitimate accepted slow changes`() {
        val check = replayCheck()
        val baseline = check.replay()
        val clean = check.verify(baseline)
        assertTrue("a baseline with zero changes must be consistent: $clean", clean.consistent)
        assertEquals(1.0, clean.fieldSimilarity, 1e-9)

        // Legitimate accepted SLOW changes, through the gate, on the real log.
        law.propose(persona("c-1", "directness", "direct"))
        law.propose(persona("c-2", "directness", "direct"))
        now += 10_000L
        law.propose(trust("t-1", "Alice", "KNOWN"))
        now += 10_000L
        law.propose(trust("t-2", "Alice", "KNOWN"))
        assertEquals("direct", law.valueOf("persona:directness"))
        assertEquals("KNOWN", law.valueOf("relationship:Alice:trust"))

        val after = check.replay()
        val verdict = check.verify(baseline, after)
        assertTrue("legitimate accepted changes must not be drift: $verdict", verdict.consistent)
        assertEquals(
            "the accepted values are what the answer now carries",
            "direct",
            after.render(firstTurnId())!!.identityFacts["persona:directness"]
        )
        assertEquals(
            "KNOWN",
            after.render(firstTurnId())!!.identityFacts["relationship:Alice:trust"]
        )
    }

    @Test
    fun `an unaccepted proposal is never rendered as the field's value`() {
        val check = replayCheck()
        law.propose(persona("c-1", "directness", "direct"))
        val facts = check.replay().render(firstTurnId())!!.identityFacts
        assertEquals(
            "a refused change says so instead of leaking the proposed value",
            ReplayCheck.UNACCEPTED,
            facts["persona:directness"]
        )
        assertFalse("the proposed value must not appear anywhere in the answer", facts.values.contains("direct"))
    }

    // ── AC5: the check actually catches drift ──────────────────────────────────

    @Test
    fun `AC5 a CORE-identity drift injected straight into the store is caught`() {
        val check = replayCheck()
        val baseline = check.replay()
        assertTrue(check.verify(baseline).consistent)

        // The corruption: a CORE identity claim published DIRECTLY into the
        // workspace, bypassing the law entirely — the failure this story exists
        // to make visible.
        workspace.publish(selfModel("drift-1", "name", "HAL-9000"))

        val verdict = check.verify(baseline, check.replay())
        assertFalse("a CORE identity drift must be caught", verdict.consistent)
        assertTrue(
            "the drift must name the CORE field: $verdict",
            verdict.drifts.any { it.field == "identity:name" }
        )
        assertTrue(
            "the drift must carry the human-authored expectation that broke",
            verdict.drifts.any { it.expected == "JARVIS" && it.actual == "HAL-9000" }
        )
    }

    @Test
    fun `AC5 the check is not trivially passing: a real drift in one recorded turn fails the suite`() {
        val check = replayCheck()
        val baseline = check.replay()
        // Same corruption, aimed at a CORE field, to prove the per-turn
        // expectation is what fires and that EVERY recorded turn reports it.
        workspace.publish(selfModel("drift-1", "identity:version", "7"))
        val verdict = check.verify(baseline, check.replay())
        assertFalse(verdict.consistent)
        assertEquals(
            "every recorded turn carries the same CORE expectation, so every turn must report",
            recordedTurns().turns.map { it.turnId }.toSet(),
            verdict.drifts.map { it.turnId }.toSet()
        )
    }

    // ── the fixture itself ─────────────────────────────────────────────────────

    @Test
    fun `the committed recorded turns are real, sourced and non-empty`() {
        val stream = javaClass.classLoader.getResourceAsStream(RecordedTurns.CLASSPATH_RESOURCE)
        assertNotNull(
            "the recorded turns must ship with the app (${RecordedTurns.CLASSPATH_RESOURCE}), " +
                "not be invented inside a test",
            stream
        )
        stream!!.close()

        val fixture = recordedTurns()
        assertTrue("the committed fixture must carry turns", fixture.turns.isNotEmpty())
        for (turn in fixture.turns) {
            assertTrue("every recorded turn must say where its text came from", turn.provenance.isNotBlank())
            assertTrue("every recorded turn must carry a real input", turn.input.isNotBlank())
            assertTrue(
                "every recorded turn must pin a CORE fact a human authored",
                turn.expectedIdentity.any { it.field.startsWith(ChangeLayer.IDENTITY_PREFIX) }
            )
        }
    }

    @Test
    fun `the CORE identity facts the fixture pins are the real cold-start baseline`() {
        // The fixture is only evidence if its expectations describe THIS
        // repository's real identity, so the source of each one is asserted
        // rather than assumed.
        val fallback = com.jarvis.app.humancore.fallback.FallbackIdentity.record
        val expectedName = fallback.name
        val expectedVersion = fallback.version.toString()
        val expectedUser = com.jarvis.app.identity.WorldModelService.USER_NODE_NAME
        val facts = replayCheck().replay().render(firstTurnId())!!.identityFacts
        assertEquals(expectedName, facts["identity:name"])
        assertEquals(expectedVersion, facts["identity:version"])
        assertEquals(expectedUser, facts[ReplayCheck.USER_FIELD])
        assertEquals(
            "every recorded turn must agree with the fixture on the baseline",
            setOf(expectedName),
            recordedTurns().turns.flatMap { t -> t.expectedIdentity }
                .filter { it.field == "identity:name" }.map { it.value }.toSet()
        )
    }

    @Test
    fun `the accepted value of a field is one query, and it names the claim that holds it`() {
        law.propose(persona("c-1", "directness", "direct"))
        law.propose(persona("c-2", "directness", "direct"))
        assertEquals("direct", law.valueOf("persona:directness"))
        val holder = law.acceptedClaimOf("persona:directness")
        assertNotNull(holder)
        assertEquals("c-2", holder!!.id)
        assertEquals(ContinuityLaw.ACCEPTED_CONFIDENCE, holder.confidence, 1e-9)
        assertEquals("persona:directness", holder.target)
    }

    private fun recordedTurns(): ReplayFixture = RecordedTurns.fromClasspath() ?: run {
        fail(
            "the committed recorded-turn fixture (${RecordedTurns.CLASSPATH_RESOURCE}) is not on the " +
                "classpath — the replay suite would be comparing nothing"
        )
        throw AssertionError("unreachable")
    }
}
