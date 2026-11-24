package ai.friday.assistant.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineFailureHealthGateTest {
    @Test
    fun `ready engine with active engine incident is not healthy`() {
        assertTrue(EngineFailureHealthGate.hasActiveEngineFailure(listOf("ENGINE")))
    }

    @Test
    fun `FridayEngine bootstrap incident is recognized as an engine failure`() {
        assertTrue(EngineFailureHealthGate.hasActiveEngineFailure(listOf("FridayEngine")))
        assertTrue(EngineFailureHealthGate.hasActiveEngineFailure(listOf("FridayEngine_bootstrap")))
    }

    @Test
    fun `unrelated subsystem incident does not mark engine itself failed`() {
        assertFalse(EngineFailureHealthGate.hasActiveEngineFailure(listOf("VOICE", "STT")))
    }

    @Test
    fun `empty failure set is healthy with respect to engine incidents`() {
        assertFalse(EngineFailureHealthGate.hasActiveEngineFailure(emptyList()))
    }
}
