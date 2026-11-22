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
    @Test fun `parses all eight Objective 1 phases and preserves exit evidence`() {
        val sections = (0..7).joinToString("\\n\\n") { phase ->
            "### Phase $phase — Gate $phase\\n\\nWork $phase.\\n\\n**Exit evidence:** Evidence $phase."
        }
        val markdown = "## Objective A / Objective 1 — Test\\n\\n$sections\\n\\n## Next section"
        val phases = ObjectivePlanParser.parseObjectiveOnePhases(markdown)
        assertEquals((0..7).toList(), phases.map { it.id })
        assertEquals("Gate 0", phases.first().title)
        assertEquals("Evidence 0.", phases.first().exitEvidence)
        assertTrue(ObjectivePlanParser.phasesToJson(phases).getJSONObject(7)
            .getString("exit_evidence").contains("Evidence 7"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects missing Objective 1 phase rather than inventing it`() {
        val sections = (0..6).joinToString("\\n\\n") { phase ->
            "### Phase $phase — Gate $phase\\n\\n**Exit evidence:** Evidence."
        }
        ObjectivePlanParser.parseObjectiveOnePhases(
            "## Objective A / Objective 1\\n$sections"
        )
    }

}
