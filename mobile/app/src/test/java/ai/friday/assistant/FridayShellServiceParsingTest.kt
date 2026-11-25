package ai.friday.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FridayShellServiceParsingTest {
    @Test
    fun `window summary omits private text and returns structural evidence only`() {
        val privateXml = """<hierarchy><node text="bank balance 12345" package="com.bank.app"/><node text="private message"/></hierarchy>"""

        val summary = summarizeWindowHierarchy(privateXml)

        assertTrue(summary.contains("status=captured"))
        assertTrue(summary.contains("node_count=2"))
        assertTrue(summary.contains("text_omitted=true"))
        assertFalse(summary.contains("bank balance"))
        assertFalse(summary.contains("12345"))
        assertFalse(summary.contains("private message"))
    }

    @Test
    fun `empty window hierarchy is not reported as captured`() {
        assertTrue(summarizeWindowHierarchy("<hierarchy/>").contains("status=unavailable"))
    }

    @Test
    fun `doze whitelist matches exact package tokens only`() {
        assertTrue(outputContainsExactToken("""system,
ai.friday.assistant
com.android.systemui""", "ai.friday.assistant"))
        assertFalse(outputContainsExactToken("ai.friday.assistant.backup", "ai.friday.assistant"))
        assertFalse(outputContainsExactToken("xai.friday.assistant", "ai.friday.assistant"))
    }
}
