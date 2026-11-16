package com.jarvis.app.cognitive.seed

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FridayModelFreeMindTest {

    @Test
    fun `identity response does not depend on a language model`() {
        val file = File.createTempFile("friday-mind-", ".bin").apply { delete() }
        val mind = FridayModelFreeMind(FridaySeedSubstrate(file))
        val reply = mind.respond("Who are you?")
        assertTrue(reply.contains("I'm Friday"))
        assertTrue(reply.contains("not what keeps my local runtime", ignoreCase = true))
        file.delete()
    }

    @Test
    fun `earlier experience changes later model-free expression after restart`() {
        val file = File.createTempFile("friday-mind-learning-", ".bin").apply { delete() }
        val first = FridayModelFreeMind(FridaySeedSubstrate(file))
        first.observe("the bell echoes near the window")
        first.observe("the bell rings in the hall")

        val restarted = FridayModelFreeMind(FridaySeedSubstrate(file))
        restarted.observe("the bell sounds again")
        val reply = restarted.respond("the bell sounds again")

        assertTrue("later response should expose a learned association", reply.contains("window") || reply.contains("hall") || reply.contains("echoes"))
        assertTrue("the response must label links as associations, not meanings", reply.contains("not verified meanings"))
        file.delete()
    }

    @Test
    fun `unknown natural language is handled honestly instead of fabricated`() {
        val file = File.createTempFile("friday-mind-", ".bin").apply { delete() }
        val mind = FridayModelFreeMind(FridaySeedSubstrate(file))
        val reply = mind.respond("Explain an unfamiliar theorem in detail.")
        assertTrue(reply.contains("cannot reliably interpret arbitrary natural language", ignoreCase = true))
        assertTrue(reply.contains("won't pretend", ignoreCase = true))
        file.delete()
    }
}
