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
    fun `unknown natural language is handled honestly instead of fabricated`() {
        val file = File.createTempFile("friday-mind-", ".bin").apply { delete() }
        val mind = FridayModelFreeMind(FridaySeedSubstrate(file))
        val reply = mind.respond("Explain an unfamiliar theorem in detail.")
        assertTrue(reply.contains("cannot reliably interpret arbitrary natural language", ignoreCase = true))
        assertTrue(reply.contains("won't pretend", ignoreCase = true))
        file.delete()
    }
}
