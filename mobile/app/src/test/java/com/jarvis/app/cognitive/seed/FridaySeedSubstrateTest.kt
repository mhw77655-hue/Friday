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

    private fun tempJournal(): File =
        File.createTempFile("friday-seed-", ".bin").apply { delete() }
}
