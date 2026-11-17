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
    fun `common hay greeting is understood without a model`() {
        val file = File.createTempFile("friday-mind-", ".bin").apply { delete() }
        val mind = FridayModelFreeMind(FridaySeedSubstrate(file))
        assertTrue(mind.respond("hay").startsWith("Hey. I'm here"))
        file.delete()
    }

    @Test
    fun `explicit personal facts persist and can be recalled after restart`() {
        val file = File.createTempFile("friday-facts-", ".bin").apply { delete() }
        val first = FridayModelFreeMind(FridaySeedSubstrate(file))
        assertTrue(first.respond("my name is Veno").contains("remember that your name is Veno"))
        first.respond("I like dark chocolate")

        val restarted = FridayModelFreeMind(FridaySeedSubstrate(file))
        assertTrue(restarted.respond("what's my name?").contains("Your name is Veno"))
        assertTrue(restarted.respond("what do I like?").contains("dark chocolate"))
        file.delete()
    }

    @Test
    fun `long explicit facts are truncated to the journal key limit instead of silently disappearing`() {
        val file = File.createTempFile("friday-long-fact-", ".bin").apply { delete() }
        val substrate = FridaySeedSubstrate(file)
        val value = "preference ".repeat(30).trim()
        assertTrue(substrate.rememberFact("user_note", value))
        val restored = FridaySeedSubstrate(file)
        val recalled = restored.recallFact("user_note")
        assertTrue(recalled != null)
        assertTrue(value.startsWith(recalled!!))
        file.delete()
    }

    @Test
    fun `simple arithmetic is composed locally without a model`() {
        val file = File.createTempFile("friday-math-", ".bin").apply { delete() }
        val mind = FridayModelFreeMind(FridaySeedSubstrate(file))
        assertTrue(mind.respond("12 * 3?").contains("36"))
        file.delete()
    }

    @Test
    fun `unknown natural language is handled honestly instead of fabricated`() {
        val file = File.createTempFile("friday-mind-", ".bin").apply { delete() }
        val mind = FridayModelFreeMind(FridaySeedSubstrate(file))
        val reply = mind.respond("Explain an unfamiliar theorem in detail.")
        assertTrue(reply.contains("don't yet have enough grounded language structure", ignoreCase = true))
        assertTrue(reply.contains("won't pretend", ignoreCase = true))
        file.delete()
    }
}
