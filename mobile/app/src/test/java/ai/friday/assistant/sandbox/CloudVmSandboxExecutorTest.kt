package ai.friday.assistant.sandbox

import ai.friday.assistant.selfrepair.SourcePatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class CloudVmSandboxExecutorTest {
    @Test
    fun `unavailable cloud sandbox fails closed instead of throwing or passing`() = runBlocking {
        val result = CloudVmSandboxExecutor().stressTest(
            patch = SourcePatch(emptyList()),
            candidateDescription = "test candidate"
        )

        assertFalse(result.passed)
        assertTrue(result.failures.isNotEmpty())
        assertTrue(result.log.any { it.contains("failing closed") })
    }
}
