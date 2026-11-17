package com.jarvis.app.cognitive.seed

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FridayIndividualTest {

    @Test
    fun `identity and visual morphology survive a new app runtime`() {
        val directory = File.createTempFile("friday-individual-", ".dir").apply {
            delete()
            mkdirs()
        }
        val first = FridayIndividual(directory) { 1234L }
        val firstIdentity = first.identity
        first.recordExperience("a novel local experience")

        val restarted = FridayIndividual(directory) { 9999L }

        assertEquals("Friday", restarted.identity.name)
        assertEquals(firstIdentity.id, restarted.identity.id)
        assertEquals(firstIdentity.createdAtMs, restarted.identity.createdAtMs)
        assertEquals(firstIdentity.morphologySeed, restarted.identity.morphologySeed)
        assertEquals(1, restarted.experience.experienceCount())
        directory.deleteRecursively()
    }

    @Test
    fun `each newly created individual receives a distinct persistent identity`() {
        val firstDir = File.createTempFile("friday-one-", ".dir").apply { delete(); mkdirs() }
        val secondDir = File.createTempFile("friday-two-", ".dir").apply { delete(); mkdirs() }
        val first = FridayIndividual(firstDir)
        val second = FridayIndividual(secondDir)

        assertNotEquals(first.identity.id, second.identity.id)
        assertTrue(first.identity.morphologySeed != second.identity.morphologySeed ||
            first.identity.id != second.identity.id)
        firstDir.deleteRecursively()
        secondDir.deleteRecursively()
    }
}
