package com.jarvis.app.model.adapters

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the actual packaged JNI exports on the emulator. A compile-only
 * APK build cannot detect a mismatched Kotlin companion/native symbol name.
 */
@RunWith(AndroidJUnit4::class)
class LocalInferenceJniSmokeTest {
    @Test
    fun nativeCompanionExportsResolveAndCreateFailsSafelyForMissingModel() {
        LocalInferenceEngine.loadNative()

        assertTrue("native version export resolves", LocalInferenceEngine.nativeVersion().contains("llama.cpp"))
        assertFalse("native system-info export returns data", LocalInferenceEngine.nativeSystemInfo().isBlank())

        val missingModel = LocalInferenceEngine.Companion.NativeConfig(
            modelPath = "/data/local/tmp/friday-intentionally-missing-model.gguf"
        )
        assertEquals("missing model fails closed without an invalid handle", 0L, LocalInferenceEngine.nativeCreate(missingModel))
    }
}
