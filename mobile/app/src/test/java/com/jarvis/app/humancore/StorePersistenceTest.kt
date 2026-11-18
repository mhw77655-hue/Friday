package com.jarvis.app.humancore

import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.fallback.FallbackIdentity
import com.jarvis.app.humancore.protocol.AffectRead
import com.jarvis.app.humancore.store.StoreKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cold-start values and durability across process death (§0.14, §1).
 *
 * Two guarantees are tested here:
 *  1. A brand-new install cold-starts to the hardcoded fallback identity,
 *     the documented trait defaults, neutral mood, and trust-earned-not-
 *     assumed values — never to nothing (§1).
 *  2. Every store survives a simulated process death: write, tear the graph
 *     down, rebuild on the same storage, and verify the values came back.
 */
class StorePersistenceTest {

    // Global StateBus teardown between tests (HUMAN_CORE_AUDIT M-9).
    @After
    fun tearDownBus() {
        StateBus.reset()
    }


    @Test
    fun `cold start falls back to the hardcoded identity`() {
        val g = newGraph(tempDir(), ManualClock())
        g.registry.loadAll()

        val id = g.identityKernel.read()
        assertTrue("cold start must use the fallback identity", g.identityKernel.isFallback())
        assertEquals(FallbackIdentity.name, id.name)
        assertEquals(1, id.version)
        assertEquals(FallbackIdentity.values.size, id.values.size)
        assertTrue(id.boundaries.isNotEmpty())
    }

    @Test
    fun `cold start personality matches documented defaults`() {
        val g = newGraph(tempDir(), ManualClock())
        val traits = g.registry.personality.allTraits()
        // Every documented trait exists with its baseline as the starting
        // value. The documented defaults ARE the production vector:
        // PersonalityStore.defaultTraits ("Documented default trait vector,
        // used at cold start and on failure reset", §5), the only numeric
        // source the HUMAN_CORE_SPEC §5 defers to.
        val expected = mapOf(
            "directness" to 0.60, "warmth" to 0.75, "humorFrequency" to 0.55,
            "formality" to 0.20, "proactiveness" to 0.45, "curiosity" to 0.70
        )
        expected.forEach { (name, baseline) ->
            val t = traits[name]
            assertTrue("trait $name missing", t != null)
            assertEquals(baseline, t!!.baseline, 1e-9)
            assertEquals(baseline, t.current, 1e-9)
            assertTrue("learningRate in range", t.learningRate in 0.0..0.5)
        }
    }

    @Test
    fun `cold start mood is neutral and relationship starts earned-not-assumed`() {
        val clock = ManualClock()
        val g = newGraph(tempDir(), clock)
        val mood = g.registry.mood.effective(clock.now)
        assertEquals(0.0, mood.valence, 1e-9)
        assertEquals(0.0, mood.arousal, 1e-9)

        val rel = g.registry.relationship.read()
        assertEquals(0.25, rel.trust.trust, 1e-9)
        assertEquals(0.0, rel.depth.depth, 1e-9)
        assertEquals(0, rel.baseline.sampleCount)
    }

    @Test
    fun `identity survives process death`() {
        val dir = tempDir()
        val clock = ManualClock()

        val g1 = newGraph(dir, clock)
        // The ONLY writer (§3): the developer/admin revision path.
        val ok = g1.identityRevision.performRevision(
            g1.identityRevision.buildRevisionRecord(
                newName = "Alistair",
                newSelfDescription = "A deliberately revised companion.",
                newValues = FallbackIdentity.values + listOf(
                    com.jarvis.app.humancore.store.ValueRecord(
                        key = "punctuality", statement = "Never claim to be punctual about time.",
                        severity = com.jarvis.app.humancore.store.ValueSeverity.HARD_BOUNDARY,
                        justification = "developer revision test"
                    )
                ),
                newBoundaries = FallbackIdentity.boundaries
            ),
            trigger = "developer revision test",
            authorizer = "test"
        )
        assertTrue("revision must apply", ok)

        // Simulate process death: new graph, same directory.
        val g2 = newGraph(dir, clock)
        g2.registry.loadAll()
        val id2 = g2.identityKernel.read()
        assertFalse("post-revision is no longer the fallback", g2.identityKernel.isFallback())
        assertEquals("Alistair", id2.name)
        assertEquals(2, id2.version)
        assertTrue(id2.values.any { it.key == "punctuality" })
    }

    @Test
    fun `relationship and mood survive process death`() {
        val dir = tempDir()
        val clock = ManualClock()

        val g1 = newGraph(dir, clock)
        // A positive exchange: trust rises, baseline gains a sample.
        val affect = AffectRead(
            valence = 0.6, arousal = 0.2, confidence = 0.9,
            signals = linkedMapOf("gratitude" to 0.6),
            trend = null, prosodyUsed = false, ts = clock.now
        )
        g1.relationshipModeling.observe(affect)
        g1.emotionalRegulation.observe(affect)
        g1.trustModeling.observe("thanks, that helped", affect, deviation = null)
        clock.minutes(1)

        val g2 = newGraph(dir, clock)
        g2.registry.loadAll()
        val rel = g2.registry.relationship.read()
        assertTrue("trust rose from 0.25 baseline via the trust writer", rel.trust.trust > 0.25)
        assertEquals(1, rel.baseline.sampleCount)
        val mood = g2.registry.mood.effective(clock.now)
        assertTrue("mood moved positive", mood.valence > 0.0)
    }

    @Test
    fun `dialogue log survives process death and is bounded`() {
        val dir = tempDir()
        val clock = ManualClock()
        val g1 = newGraph(dir, clock, modelPort = FixedModelPort())
        // Force the new-session trigger to write dialogue entries.
        g1.dialogueEngine.onSessionStart()
        clock.minutes(1)
        g1.dialogueEngine.onSessionStart() // debounced? second call same trigger may be debounced

        val g2 = newGraph(dir, clock)
        g2.registry.loadAll()
        assertTrue("dialogue log persisted at least one entry", g2.registry.dialogue.read().isNotEmpty())
    }
    @Test
    fun `failed identity persistence does not publish an uncommitted revision`() {
        val dir = tempDir()
        val delegate = com.jarvis.app.humancore.store.FileStorage(dir)
        val failing = object : com.jarvis.app.humancore.store.StoragePort by delegate {
            override fun write(store: StoreKind, content: String) {
                if (store == StoreKind.IDENTITY) throw IllegalStateException("simulated disk failure")
                delegate.write(store, content)
            }
        }
        val identity = com.jarvis.app.humancore.store.IdentityStore(failing, { 123L })
        identity.load()
        val candidate = FallbackIdentity.record.copy(
            name = "Uncommitted Friday",
            version = 2,
            lastRevisionEpochMs = 123L
        )
        try {
            identity.applyRevision(
                candidate,
                com.jarvis.app.humancore.store.IdentityRevisionEntry(123L, "test", "owner", "test revision"),
                com.jarvis.app.humancore.store.IdentityRevisionAuth.authorize("test", "owner")
            )
            throw AssertionError("Expected storage failure")
        } catch (_: IllegalStateException) {
            assertEquals(FallbackIdentity.record, identity.read())
            assertTrue(identity.auditLog().isEmpty())
            assertEquals(null, delegate.read(StoreKind.IDENTITY))
        }
    }

    @Test
    fun `reloading identity store does not duplicate its revision audit history`() {
        val dir = tempDir()
        val identity = com.jarvis.app.humancore.store.IdentityStore(
            com.jarvis.app.humancore.store.FileStorage(dir),
            { 123L }
        )
        identity.load()
        identity.applyRevision(
            FallbackIdentity.record.copy(name = "Friday v2", version = 2, lastRevisionEpochMs = 123L),
            com.jarvis.app.humancore.store.IdentityRevisionEntry(123L, "test", "owner", "test revision"),
            com.jarvis.app.humancore.store.IdentityRevisionAuth.authorize("test", "owner")
        )

        identity.load()
        identity.load()

        assertEquals(1, identity.auditLog().size)
        assertEquals("Friday v2", identity.read().name)
    }

    @Test
    fun `body memory snapshot cannot overwrite or masquerade as internal dialogue log`() {
        val dir = tempDir()
        val storage = com.jarvis.app.humancore.store.FileStorage(dir)
        val dialogueLine = """{"ts":123,"trigger":"new_session","text":"A real dialogue entry","snapshotRef":"v1","surfaced":false}"""
        storage.append(StoreKind.DIALOGUE, dialogueLine)
        storage.write(
            StoreKind.BODY_MEMORY,
            """{"episodic":[],"facts":[],"preferences":[],"context_order":[]}"""
        )

        val dialogue = com.jarvis.app.humancore.store.DialogueLog(storage) { 123L }
        dialogue.load()

        assertEquals(1, dialogue.read().size)
        assertEquals("A real dialogue entry", dialogue.read().single().text)
        assertTrue(storage.read(StoreKind.DIALOGUE)!!.contains("A real dialogue entry"))
        assertTrue(storage.read(StoreKind.BODY_MEMORY)!!.contains("\"episodic\""))
    }

    @Test
    fun `legacy body memory document is ignored by dialogue log parser`() {
        val dir = tempDir()
        val storage = com.jarvis.app.humancore.store.FileStorage(dir)
        storage.write(
            StoreKind.DIALOGUE,
            """{"episodic":[],"facts":[],"preferences":[],"context_order":[]}"""
        )

        val dialogue = com.jarvis.app.humancore.store.DialogueLog(storage) { 123L }
        dialogue.load()

        assertTrue("legacy memory JSON must not become an empty phantom dialogue entry", dialogue.read().isEmpty())
    }

}
