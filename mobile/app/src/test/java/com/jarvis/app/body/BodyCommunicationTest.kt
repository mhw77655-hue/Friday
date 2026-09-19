package com.jarvis.app.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nervous-system communication behavior — pure state machine + language routing.
 * No Android, plain JVM (JUnit 4).
 */
class BodyCommunicationTest {

    // ── State machine: the Vosk wake-word voice path ──

    @Test
    fun `wake then command drives idle to thinking`() {
        var s = BodyState.IDLE
        s = BodyStateMachine.next(s, BodyEvent.WakeDetected(1.0f))
        assertEquals(BodyState.WAKE, s)
        s = BodyStateMachine.next(s, BodyEvent.SttResult("what time is it", 1.0f, "en"))
        assertEquals(BodyState.THINKING, s)
    }

    @Test
    fun `blank stt result returns to idle instead of thinking`() {
        var s = BodyState.WAKE
        s = BodyStateMachine.next(s, BodyEvent.SttResult("", 0.8f, "en"))
        assertEquals(BodyState.IDLE, s)
    }

    @Test
    fun `reply generation completes the turn to speaking then idle`() {
        var s = BodyState.THINKING
        s = BodyStateMachine.next(s, BodyEvent.ResponseGenerated("hello"))
        assertEquals(BodyState.RESPONDING, s)
        s = BodyStateMachine.next(s, BodyEvent.TtsStart)
        assertEquals(BodyState.SPEAKING, s)
        s = BodyStateMachine.next(s, BodyEvent.TtsEnd)
        assertEquals(BodyState.IDLE, s)
    }

    @Test
    fun `streaming chunk keeps responding then final chunk speaks`() {
        var s = BodyState.THINKING
        s = BodyStateMachine.next(s, BodyEvent.ResponseChunk("first ", isFinal = false))
        assertEquals(BodyState.RESPONDING, s)
        s = BodyStateMachine.next(s, BodyEvent.ResponseChunk("part", isFinal = true))
        assertEquals(BodyState.SPEAKING, s)
    }

    // ── Interruption / barge-in ──

    @Test
    fun `wake word during speaking is a barge-in`() {
        val s = BodyStateMachine.next(BodyState.SPEAKING, BodyEvent.WakeDetected(1.0f))
        assertEquals(BodyState.INTERRUPTED, s)
    }

    @Test
    fun `user input during speaking interrupts`() {
        val s = BodyStateMachine.next(BodyState.SPEAKING, BodyEvent.UserInput("wait"))
        assertEquals(BodyState.INTERRUPTED, s)
    }

    @Test
    fun `followup command after interruption proceeds to thinking`() {
        var s = BodyState.INTERRUPTED
        s = BodyStateMachine.next(s, BodyEvent.SttResult("actual question", 1.0f, "en"))
        assertEquals(BodyState.THINKING, s)
    }

    @Test
    fun `mic button start during speech interrupts`() {
        val s = BodyStateMachine.next(BodyState.SPEAKING, BodyEvent.SpeechStart)
        assertEquals(BodyState.INTERRUPTED, s)
    }

    // ── Manual push-to-talk path ──

    @Test
    fun `speech start then end reaches hearing then thinking`() {
        var s = BodyState.IDLE
        s = BodyStateMachine.next(s, BodyEvent.SpeechStart)
        assertEquals(BodyState.LISTENING, s)
        s = BodyStateMachine.next(s, BodyEvent.SpeechEnd(shortArrayOf(1, 2, 3)))
        assertEquals(BodyState.HEARING, s)
        s = BodyStateMachine.next(s, BodyEvent.SttResult("transcribed", 0.9f, "ar"))
        assertEquals(BodyState.THINKING, s)
    }

    // ── Presence / memory / learning / error ──

    @Test
    fun `presence events map to overlay states`() {
        assertEquals(BodyState.USER_PRESENT, BodyStateMachine.next(BodyState.IDLE, BodyEvent.UserPresenceChanged(true)))
        assertEquals(BodyState.USER_ABSENT, BodyStateMachine.next(BodyState.IDLE, BodyEvent.UserPresenceChanged(false)))
    }

    @Test
    fun `memory retrieval is transient`() {
        var s = BodyState.RETRIEVING
        s = BodyStateMachine.next(s, BodyEvent.MemoryRetrieved(emptyList()))
        assertEquals(BodyState.THINKING, s)
    }

    @Test
    fun `teaching a word enters and exits learning`() {
        var s = BodyState.IDLE
        s = BodyStateMachine.next(s, BodyEvent.TeachWord("merhaba", "mer-ha-ba", "ar"))
        assertEquals(BodyState.LEARNING, s)
        s = BodyStateMachine.next(s, BodyEvent.WordLearned)
        assertEquals(BodyState.IDLE, s)
    }

    @Test
    fun `error then reset recovers`() {
        var s = BodyStateMachine.next(BodyState.SPEAKING, BodyEvent.ErrorOccurred("boom"))
        assertEquals(BodyState.ERROR, s)
        s = BodyStateMachine.next(s, BodyEvent.Reset)
        assertEquals(BodyState.IDLE, s)
    }

    @Test
    fun `unrelated events leave state unchanged`() {
        // MemoryRetrieved is only meaningful from RETRIEVING; from THINKING it is a no-op.
        assertEquals(BodyState.THINKING, BodyStateMachine.next(BodyState.THINKING, BodyEvent.MemoryRetrieved(emptyList())))
        // WordLearned only closes LEARNING.
        assertEquals(BodyState.THINKING, BodyStateMachine.next(BodyState.THINKING, BodyEvent.WordLearned))
    }

    // ── Language routing: English + Egyptian Arabic + code-switching ──

    @Test
    fun `english text detects as english`() {
        assertEquals(
            LanguageRouter.Language.ENGLISH,
            LanguageDetection.detectLanguage("hello how are you")
        )
    }

    @Test
    fun `arabic text detects as egyptian arabic`() {
        assertEquals(
            LanguageRouter.Language.EGYPTIAN_ARABIC,
            LanguageDetection.detectLanguage("إزيك يا صاحبي")
        )
    }

    @Test
    fun `code-switched text splits into per-language segments`() {
        val segments = LanguageDetection.detectSegments("hello إزيك how are you")
        assertEquals(3, segments.size)
        assertEquals("hello", segments[0].text)
        assertEquals(LanguageRouter.Language.ENGLISH, segments[0].language)
        assertEquals("إزيك", segments[1].text)
        assertEquals(LanguageRouter.Language.EGYPTIAN_ARABIC, segments[1].language)
        assertEquals("how are you", segments[2].text)
        assertEquals(LanguageRouter.Language.ENGLISH, segments[2].language)
    }

    @Test
    fun `mixed script inside one word counts as arabic`() {
        assertEquals(
            LanguageRouter.Language.EGYPTIAN_ARABIC,
            LanguageDetection.detectLanguage("say: إزيك")
        )
    }

    @Test
    fun `blank text yields no segments`() {
        assertTrue(LanguageDetection.detectSegments("   ").isEmpty())
    }

    @Test
    fun `punctuation-only text stays english`() {
        assertEquals(
            LanguageRouter.Language.ENGLISH,
            LanguageDetection.detectLanguage("?!...")
        )
    }
}
