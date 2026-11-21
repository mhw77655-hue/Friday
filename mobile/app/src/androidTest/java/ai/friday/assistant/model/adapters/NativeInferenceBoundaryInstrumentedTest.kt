package ai.friday.assistant.model.adapters

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeInferenceBoundaryInstrumentedTest {

    @Test
    fun `native entry points fail closed for null handles`() {
        LocalInferenceEngine.loadNative()

        assertEquals(-1, LocalInferenceEngine.nativeGetState(0L))
        assertNull(LocalInferenceEngine.nativeGetHealth(0L))
        assertNull(
            LocalInferenceEngine.nativeGenerate(
                0L, "prompt", LocalInferenceEngine.Companion.NativeGenConfig(), null
            )
        )
        assertEquals(-1, LocalInferenceEngine.nativeTokenCount(0L, "prompt"))
        assertEquals(-1, LocalInferenceEngine.nativeEncode(0L, "prompt", IntArray(8)))
        assertNull(LocalInferenceEngine.nativeDecode(0L, intArrayOf(1, 2, 3)))
        LocalInferenceEngine.nativeDestroy(0L)
    }

    @Test
    fun `invalid model configuration is rejected before loading a model file`() {
        LocalInferenceEngine.loadNative()

        val handle = LocalInferenceEngine.nativeCreate(
            LocalInferenceEngine.Companion.NativeConfig(modelPath = "", nCtx = 0)
        )
        assertEquals(0L, handle)
    }
}
