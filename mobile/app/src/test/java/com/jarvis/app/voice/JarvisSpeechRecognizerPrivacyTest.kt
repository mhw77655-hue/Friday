package com.jarvis.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class JarvisSpeechRecognizerPrivacyTest {

    @Test
    fun recognizedWordsAreDeliveredButNeverStoredInDiagnosticStatus() {
        val privateTranscript = "my private spoken words"
        val port = object : SpeechRecognizerPort {
            override fun isRecognitionAvailable(): Boolean = true
            override fun recognize(locale: Locale, onResult: (String?) -> Unit) {
                onResult(privateTranscript)
            }
            override fun cancel() = Unit
        }
        val recognizer = JarvisSpeechRecognizer(port)
        var delivered: String? = null

        recognizer.recognize(Locale.US) { delivered = it }

        assertEquals(privateTranscript, delivered)
        assertEquals("recognized", recognizer.status)
        assertFalse(recognizer.status.contains(privateTranscript))
    }
}
