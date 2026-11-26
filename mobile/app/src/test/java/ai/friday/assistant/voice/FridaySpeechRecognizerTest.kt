package ai.friday.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FridaySpeechRecognizerTest {

    private class FakeRecognizerPort(
        var available: Boolean = true,
        var transcript: String? = "open the door",
        var cancelled: Boolean = false,
        var completeImmediately: Boolean = true
    ) : SpeechRecognizerPort {
        var lastLocale: Locale? = null
        var partialCallback: ((String?) -> Unit)? = null
        var resultCallback: ((String?) -> Unit)? = null

        override fun isRecognitionAvailable(): Boolean = available

        override fun recognize(
            locale: Locale,
            onPartial: (String?) -> Unit,
            onResult: (String?) -> Unit
        ) {
            lastLocale = locale
            partialCallback = onPartial
            resultCallback = onResult
            if (completeImmediately) onResult(transcript)
        }

        override fun cancel() {
            cancelled = true
        }
    }

    @Test
    fun `unavailable platform recognizer delivers null and reports unavailable`() {
        val port = FakeRecognizerPort(available = false)
        val recognizer = FridaySpeechRecognizer(port)
        var delivered: String? = "sentinel"
        recognizer.recognize(Locale.US) { delivered = it }
        assertNull(delivered)
        assertEquals("unavailable", recognizer.status)
    }

    @Test
    fun `recognition round trip delivers the platform transcript and requested locale`() {
        val port = FakeRecognizerPort(transcript = "what time is it")
        val recognizer = FridaySpeechRecognizer(port)
        var delivered: String? = null
        recognizer.recognize(Locale.US) { delivered = it }
        assertEquals("what time is it", delivered)
        assertEquals(Locale.US, port.lastLocale)
        assertEquals("heard: what time is it", recognizer.status)
    }

    @Test
    fun `partial hypotheses are surfaced but never submitted as the final transcript`() {
        val port = FakeRecognizerPort(transcript = null, completeImmediately = false)
        val recognizer = FridaySpeechRecognizer(port)
        val partials = mutableListOf<String?>()
        var final: String? = "not-finished"

        recognizer.recognize(
            locale = Locale("ar", "EG"),
            onPartial = { partials += it },
            onResult = { final = it }
        )
        port.partialCallback?.invoke("افتح الإعدادات")
        assertEquals(listOf("افتح الإعدادات"), partials)
        assertEquals("not-finished", final)
        assertEquals(Locale("ar", "EG"), port.lastLocale)

        port.resultCallback?.invoke("افتح الإعدادات")
        assertEquals(listOf("افتح الإعدادات", null), partials)
        assertEquals("افتح الإعدادات", final)
        assertEquals("heard: افتح الإعدادات", recognizer.status)
    }

    @Test
    fun `late partial and final callbacks after cancellation are ignored`() {
        val port = FakeRecognizerPort(transcript = null, completeImmediately = false)
        val recognizer = FridaySpeechRecognizer(port)
        val partials = mutableListOf<String?>()
        var final: String? = null

        recognizer.recognize(Locale.US, onPartial = { partials += it }) { final = it }
        recognizer.cancel()
        port.partialCallback?.invoke("stale words")
        port.resultCallback?.invoke("stale words")

        assertTrue(port.cancelled)
        assertEquals(listOf(null), partials)
        assertNull(final)
        assertEquals("idle", recognizer.status)
    }

    @Test
    fun `blank transcript is treated as no result never fabricated`() {
        val port = FakeRecognizerPort(transcript = "   ")
        val recognizer = FridaySpeechRecognizer(port)
        var delivered: String? = "sentinel"
        recognizer.recognize(Locale.US) { delivered = it }
        assertNull(delivered)
        assertEquals("no result", recognizer.status)
    }

    @Test
    fun `cancel releases the session and reports idle`() {
        val port = FakeRecognizerPort()
        val recognizer = FridaySpeechRecognizer(port)
        recognizer.cancel()
        assertTrue(port.cancelled)
        assertEquals("idle", recognizer.status)
    }
}
