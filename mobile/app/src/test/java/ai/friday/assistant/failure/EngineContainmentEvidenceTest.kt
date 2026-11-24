package ai.friday.assistant.failure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineContainmentEvidenceTest {
    @Test
    fun uncaughtEngineFailureExportsBoundedStackEvidenceWithoutRawMessage() = runBlocking {
        val surface = FailureSurface()
        val scope = EngineContainment.scope("evidence-test", surface, Dispatchers.Default)
        scope.launch { throw IllegalStateException("sensitive test message") }
        try {
            withTimeout(2_000) {
                while (surface.recentFailures.value.isEmpty()) delay(5)
            }
            val event = surface.recentFailures.value.first()
            assertNotNull(event.metadata["exception_chain"])
            assertTrue(event.metadata["exception_chain"].orEmpty().contains("IllegalStateException"))
            assertTrue(event.metadata["stack_frames"].orEmpty().isNotBlank())
            assertFalse(event.metadata.values.any { it.contains("sensitive test message") })
        } finally {
            scope.cancel()
        }
    }
}
