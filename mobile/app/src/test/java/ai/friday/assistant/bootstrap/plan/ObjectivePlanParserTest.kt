package ai.friday.assistant.bootstrap.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectivePlanParserTest {
    @Test fun `extracts all canonical objective sections without inventing dependencies`() {
        val markdown = ('A'..'G').joinToString("\n\n") { id ->
            "### Objective $id — Title $id\n\nAcceptance: verify $id."
        }
        val objectives = ObjectivePlanParser.parse(markdown)
        assertEquals(('A'..'G').map { it.toString() }, objectives.map { it.id })
        assertEquals("Title A", objectives.first().title)
        assertTrue(objectives.first().markdown.contains("Acceptance: verify A."))
        assertTrue(ObjectivePlanParser.toJson(objectives).getJSONObject(0)
            .getJSONArray("dependencies").length() == 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a document without canonical objective headings`() {
        ObjectivePlanParser.parse("# Not the delivery plan")
    }

    @Test fun `parses all nine chronological roadmap phases and preserves exit gates`() {
        val sections = (0..8).joinToString("\n\n") { phase ->
            "### Phase $phase — Gate $phase\n\nWork $phase.\n\n**Exit gate:** Evidence $phase."
        }
        val markdown = "## 8. Chronological delivery roadmap — Test\n\n$sections\n\n### Continuous tracks"
        val phases = ObjectivePlanParser.parseObjectiveOnePhases(markdown)
        assertEquals((0..8).toList(), phases.map { it.id })
        assertEquals("Gate 0", phases.first().title)
        assertEquals("Evidence 0.", phases.first().exitEvidence)
        assertTrue(ObjectivePlanParser.phasesToJson(phases).getJSONObject(8)
            .getString("exit_evidence").contains("Evidence 8"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a missing roadmap phase rather than inventing it`() {
        val sections = (0..7).joinToString("\n\n") { phase ->
            "### Phase $phase — Gate $phase\n\n**Exit gate:** Evidence."
        }
        ObjectivePlanParser.parseObjectiveOnePhases(
            "## 8. Chronological delivery roadmap\n$sections\n### Continuous tracks"
        )
    }
}
