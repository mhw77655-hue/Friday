package ai.friday.assistant.model.adapters

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalInferenceEngineResourceConfigTest {
    @Test fun `NAE critical and eco batch budgets reach llama native config`() {
        assertEquals(64, LocalInferenceEngine.resolveBatchSize(mapOf("batch_size" to "64")))
        assertEquals(128, LocalInferenceEngine.resolveBatchSize(mapOf("batch_size" to "128")))
        assertEquals(256, LocalInferenceEngine.resolveBatchSize(mapOf("batch_size" to "256")))
        assertEquals(512, LocalInferenceEngine.resolveBatchSize(mapOf("batch_size" to "512")))
    }

    @Test fun `invalid and excessive batch sizes are bounded`() {
        assertEquals(512, LocalInferenceEngine.resolveBatchSize(emptyMap()))
        assertEquals(512, LocalInferenceEngine.resolveBatchSize(mapOf("batch_size" to "invalid")))
        assertEquals(512, LocalInferenceEngine.resolveBatchSize(mapOf("batch_size" to "9000")))
        assertEquals(32, LocalInferenceEngine.resolveBatchSize(mapOf("batch_size" to "1")))
    }
}
