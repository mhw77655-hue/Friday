package com.jarvis.app.cognitive.seed

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FridaySeedSubstrateTest {

    @Test
    fun `experience associations influence a changed later context`() {
        val file = tempJournal()
        val seed = FridaySeedSubstrate(file)

        seed.observeOutcome(
            context = mapOf("texture:metal" to 1.0, "sound:click" to 1.0),
            outcome = mapOf("action:turn" to 1.0)
        )

        val inferred = seed.infer(
            mapOf("sound:click" to 1.0, "location:unseen-room" to 1.0)
        )

        assertTrue("learned action should transfer to the changed context", inferred["action:turn"] != null)
        assertTrue(seed.associationCount() > 0)
        file.delete()
    }

    @Test
    fun `learned relations survive a new runtime instance`() {
        val file = tempJournal()
        val first = FridaySeedSubstrate(file)
        first.observeOutcome(
            context = mapOf("surface:rough" to 1.0, "object:wheel" to 1.0),
            outcome = mapOf("action:push" to 1.0)
        )

        val restarted = FridaySeedSubstrate(file)
        val inferred = restarted.infer(mapOf("surface:rough" to 1.0))

        assertEquals(1, restarted.experienceCount())
        assertTrue(inferred.containsKey("action:push"))
        file.delete()
    }

    @Test
    fun `no experience means no fabricated inference`() {
        val file = tempJournal()
        val seed = FridaySeedSubstrate(file)
        assertTrue(seed.infer(mapOf("unknown:signal" to 1.0)).isEmpty())
        assertEquals(0, seed.experienceCount())
        file.delete()
    }

    @Test
    fun `invalid and excessive feature values are safely bounded`() {
        val file = tempJournal()
        val seed = FridaySeedSubstrate(file)
        val receipt = seed.observe(
            mapOf(
                "valid" to 5.0,
                "nan" to Double.NaN,
                "" to 1.0,
                "also-valid" to -0.5
            )
        )
        assertEquals(2, receipt.storedFeatures)
        assertFalse(seed.associationSnapshot().any { it.strength.isNaN() })
        file.delete()
    }


    @Test
    fun `corrupt journal is preserved and refuses writes rather than silently resetting memory`() {
        val file = tempJournal()
        val original = byteArrayOf(1, 2, 3, 4, 5, 6)
        file.writeBytes(original)

        val seed = FridaySeedSubstrate(file)

        assertFalse(seed.isJournalHealthy())
        assertEquals(original.toList(), file.readBytes().toList())
        try {
            seed.observe(mapOf("new" to 1.0))
            throw AssertionError("writes to a corrupt journal must be refused")
        } catch (_: IllegalStateException) {
            // Expected: preserve the only copy of the unreadable history.
        }
        assertEquals(original.toList(), file.readBytes().toList())
        file.delete()
    }

    @Test
    fun `separate runtime instances refresh before appending to the same journal`() {
        val file = tempJournal()
        val first = FridaySeedSubstrate(file)
        val second = FridaySeedSubstrate(file)

        val a = first.observe(mapOf("event:first" to 1.0))
        val b = second.observe(mapOf("event:second" to 1.0))
        val c = first.observe(mapOf("event:third" to 1.0))

        assertEquals(listOf(1L, 2L, 3L), listOf(a.experienceId, b.experienceId, c.experienceId))
        val restarted = FridaySeedSubstrate(file)
        assertEquals(3, restarted.experienceCount())
        assertTrue(restarted.isJournalHealthy())
        file.delete()
    }

    private fun tempJournal(): File =
        File.createTempFile("friday-seed-", ".bin").apply { delete() }
}
