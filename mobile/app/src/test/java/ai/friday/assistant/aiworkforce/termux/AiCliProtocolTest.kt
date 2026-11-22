package ai.friday.assistant.aiworkforce.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiCliProtocolTest {
    @Test
    fun `Codex worker uses ChatGPT-authenticated CLI with read-only sandbox and stdin prompt`() {
        val command = AiCliProtocol.command(AiCliWorkerId.OPENAI_CODEX)
        assertEquals("exec codex exec --json --sandbox read-only -", command)
        assertFalse(command.contains("API_KEY"))
    }

    @Test
    fun `Claude worker uses subscription CLI in print mode with tools disabled`() {
        val command = AiCliProtocol.command(AiCliWorkerId.ANTHROPIC_CLAUDE)
        assertEquals("exec claude -p --output-format json --tools ''", command)
        assertFalse(command.contains("ANTHROPIC_API_KEY"))
    }

    @Test
    fun `Codex JSONL response extracts final agent message`() {
        val output = """
            {"type":"item.completed","item":{"type":"reasoning","text":"internal"}}
            {"type":"item.completed","item":{"type":"agent_message","text":"Friday, Codex is connected."}}
            {"type":"turn.completed","usage":{"input_tokens":12,"output_tokens":7}}
        """.trimIndent()
        assertEquals(
            "Friday, Codex is connected.",
            AiCliProtocol.extractResponse(AiCliWorkerId.OPENAI_CODEX, output)
        )
    }

    @Test
    fun `Claude JSON response extracts result text`() {
        val output = """{"type":"result","subtype":"success","result":"Friday, Claude is connected."}"""
        assertEquals(
            "Friday, Claude is connected.",
            AiCliProtocol.extractResponse(AiCliWorkerId.ANTHROPIC_CLAUDE, output)
        )
    }

    @Test
    fun `malformed response is preserved for diagnosis instead of silently discarded`() {
        val output = "CLI returned a non-JSON diagnostic"
        assertEquals(
            output,
            AiCliProtocol.extractResponse(AiCliWorkerId.OPENAI_CODEX, output)
        )
        assertEquals(
            output,
            AiCliProtocol.extractResponse(AiCliWorkerId.ANTHROPIC_CLAUDE, output)
        )
    }
}
