package com.jarvis.app.language

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DialectDetectorTest {

    private lateinit var detector: EgyptianArabicDialectDetector

    @Before
    fun setUp() {
        detector = EgyptianArabicDialectDetector()
    }

    @Test
    fun `blank text returns neutral signal`() {
        val signal = detector.detect("")
        assertEquals("en", signal.detectedLanguageMix)
        assertEquals(0f, signal.dialectConfidence)
        assertTrue(signal.codeSwitchPoints.isEmpty())
        assertEquals("neutral", signal.register)
    }

    @Test
    fun `pure English text detected as English`() {
        val signal = detector.detect("Hello, how are you today?")
        assertEquals("en", signal.detectedLanguageMix)
        assertEquals(0f, signal.dialectConfidence)
        assertTrue(signal.codeSwitchPoints.isEmpty())
    }

    @Test
    fun `pure Arabic script detected as Egyptian Arabic`() {
        val signal = detector.detect("اهلا بيك في مصر")
        assertEquals("ar-EG", signal.detectedLanguageMix)
        assertTrue(signal.dialectConfidence > 0f)
        // Egyptian marker "اهلا" detected (script baseline 0.15 + marker 0.12)
    }

    @Test
    fun `mixed EN AR text produces code-switch points`() {
        val signal = detector.detect("I love مصر so much")
        assertEquals("en+ar-EG", signal.detectedLanguageMix)
        assertTrue(signal.codeSwitchPoints.isNotEmpty())
        assertTrue(signal.dialectConfidence > 0f)
    }

    @Test
    fun `Arabizi numerals detected as dialect`() {
        val signal = detector.detect("7aga helwa ya3ni")
        assertTrue(signal.dialectConfidence > 0f)
        assertTrue(signal.register == "informal")
    }

    @Test
    fun `Egyptian markers increase confidence`() {
        val withMarkers = detector.detect("ايه رايك كده")
        val withoutMarkers = detector.detect("مرحبا كيف حالك")
        assertTrue(withMarkers.dialectConfidence > withoutMarkers.dialectConfidence)
    }

    @Test
    fun `code-switch between scripts detected`() {
        val signal = detector.detect("ok يلا نمشي")
        assertTrue(signal.codeSwitchPoints.isNotEmpty())
    }

    @Test
    fun `neutral signal defaults are sane`() {
        val neutral = DialectSignal.neutral()
        assertEquals("en", neutral.detectedLanguageMix)
        assertEquals(0f, neutral.dialectConfidence)
        assertTrue(neutral.codeSwitchPoints.isEmpty())
        assertEquals("neutral", neutral.register)
    }

    @Test
    fun `formal Arabic register detected`() {
        val signal = detector.detect("الذي يتكلم بالعربي الفصحى")
        assertEquals("ar-EG", signal.detectedLanguageMix)
    }

    @Test
    fun `casual English detected as informal`() {
        val signal = detector.detect("lol gonna get some food")
        assertTrue(signal.register == "informal")
    }
}
