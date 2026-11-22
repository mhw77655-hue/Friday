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
}
