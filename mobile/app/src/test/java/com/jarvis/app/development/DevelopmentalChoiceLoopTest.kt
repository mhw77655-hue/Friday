package com.jarvis.app.development

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopmentalChoiceLoopTest {
    private val options = listOf(
        DevelopmentalChoiceOption("prose", "Prefer natural connected prose."),
        DevelopmentalChoiceOption("bullets", "Prefer concise bullet points.")
    )

    @Test
    fun explicitConsequencesChangeFutureSelectionAcrossRestartWithoutTranscriptReplay() {
        val directory = java.nio.file.Files.createTempDirectory("friday-development").toFile()
        val file = File(directory, "development/behaviour.jsonl")
        var now = 1_000L
        try {
            val first = DevelopmentalChoiceLoop(
                JsonlDevelopmentJournal(file),
                nowMs = { now++ }
            )

            assertEquals("prose", first.beginTurn("Summarize the first report.", options).id)

            val afterCorrection = first.beginTurn(
                "That was too long. Use bullet points next time.",
                options
            )
            assertEquals("bullets", afterCorrection.id)

            // Reinforce the selected option from a later outcome.
            first.beginTurn("That was perfect, thanks.", options)

            val journalText = file.readText()
            assertFalse("raw source input must not be written to the developmental journal", journalText.contains("first report"))
            assertFalse("raw feedback text must not be written to the developmental journal", journalText.contains("too long"))
            assertTrue("the derived behavioural choice is recorded", journalText.contains("\"choice\":\"bullets\""))

            val restarted = DevelopmentalChoiceLoop(
                JsonlDevelopmentJournal(file),
                nowMs = { now++ }
            )
            val newSituation = restarted.beginTurn(
                "Give me the key points from an unrelated incident review.",
                options
            )
            assertEquals("the learned selection transfers to new wording and content", "bullets", newSituation.id)
            assertTrue("the selection carries evidence from previous outcomes", newSituation.evidenceCount >= 2)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun disablingTheDevelopmentalMechanismRemovesTheLearnedEffect() {
        val directory = java.nio.file.Files.createTempDirectory("friday-development-off").toFile()
        val file = File(directory, "development/behaviour.jsonl")
        try {
            val learning = DevelopmentalChoiceLoop(JsonlDevelopmentJournal(file))
            learning.beginTurn("First request.", options)
            learning.beginTurn("Use bullet points next time.", options)
            learning.beginTurn("That was perfect.", options)

            val mechanismOff = DevelopmentalChoiceLoop(
                journal = JsonlDevelopmentJournal(file),
                mechanism = DevelopmentalSelectionMechanism { candidates, _ -> candidates.first() }
            )

            assertEquals(
                "with the learning mechanism disabled, the baseline selector returns the first candidate",
                "prose",
                mechanismOff.beginTurn("A fresh unrelated request.", options).id
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun ordinaryUnrelatedConversationIsNotAutomaticallyScoredAsFeedback() {
        val directory = java.nio.file.Files.createTempDirectory("friday-development-neutral").toFile()
        val file = File(directory, "development/behaviour.jsonl")
        try {
            val loop = DevelopmentalChoiceLoop(JsonlDevelopmentJournal(file))
            loop.beginTurn("First request.", options)
            loop.beginTurn("What is the weather like in Alexandria?", options)

            assertTrue("no outcome is inferred from ordinary conversation", loop.evidenceSnapshot().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}
