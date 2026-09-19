package com.jarvis.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class JarvisSpeechRecognizerTest {

    /**
     * Deterministic stand-in for the platform recognizer seam. [PlatformSpeechRecognizerPort]
     * is the real production implementation (device-only, compile-time android.jar
     * reference, never invoked on the JVM — the established seam pattern). This fake
     * drives [JarvisSpeechRecognizer] through the SAME [SpeechRecognizerPort] contract
     * the BodyCoordinator uses, proving the full round-trip flow on the JVM.
     */
    private class FakeRecognizerPort(
        var available: Boolean = true,
        var transcript: String? = "open the door",
        var cancelled: Boolean = false
    ) : SpeechRecognizerPort {
        var lastLocale: Locale? = null
        override fun isRecognitionAvailable(): Boolean = available
        override fun recognize(locale: Locale, onResult: (String?) -> Unit) {
            lastLocale = locale
            onResult(transcript)
        }
        override fun cancel() { cancelled = true }
    }

    @Test
    fun `unavailable platform recognizer delivers null and reports unavailable`() {
        val port = FakeRecognizerPort(available = false)
        val recognizer = JarvisSpeechRecognizer(port)
        var delivered: String? = "sentinel"
        recognizer.recognize(Locale.US) { delivered = it }
        assertNull(delivered)
        assertEquals("unavailable", recognizer.status)
    }

    @Test
    fun `real recognition round trip delivers the platform transcript`() {
        val port = FakeRecognizerPort(transcript = "what time is it")
        val recognizer = JarvisSpeechRecognizer(port)
        var delivered: String? = null
        recognizer.recognize(Locale.US) { delivered = it }
        assertEquals("what time is it", delivered)
        assertEquals(Locale.US, port.lastLocale)
        assertEquals("heard: what time is it", recognizer.status)
    }

    @Test
    fun `blank transcript is treated as no result, never fabricated`() {
        val port = FakeRecognizerPort(transcript = "   ")
        val recognizer = JarvisSpeechRecognizer(port)
        var delivered: String? = "sentinel"
        recognizer.recognize(Locale.US) { delivered = it }
        assertNull(delivered)
        assertEquals("no result", recognizer.status)
    }

    @Test
    fun `cancel releases the session and reports idle`() {
        val port = FakeRecognizerPort()
        val recognizer = JarvisSpeechRecognizer(port)
        recognizer.cancel()
        assertTrue(port.cancelled)
        assertEquals("idle", recognizer.status)
    }
}