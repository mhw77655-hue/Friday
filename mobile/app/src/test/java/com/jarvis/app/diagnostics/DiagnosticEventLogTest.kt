package com.jarvis.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEventLogTest {
    @Test
    fun `deduplicates each subsystem independently across interleaved updates`() {
        val log = DiagnosticEventLog()
        assertTrue(log.record("battery", "Battery: 50%", "t1"))
        assertTrue(log.record("network", "Net: wifi", "t2"))
        assertFalse(log.record("battery", "Battery: 50%", "t3"))
        assertTrue(log.record("battery", "Battery: 49%", "t4"))

        assertEquals(
            listOf("battery" to "Battery: 50%", "network" to "Net: wifi", "battery" to "Battery: 49%"),
            log.snapshot().map { it.subsystem to it.state }
        )
    }

    @Test
    fun `microphone level and voice ping do not flood transition history`() {
        val log = DiagnosticEventLog()
        assertTrue(log.record("microphone", "Mic: energy monitor active, level 100", "t1"))
        assertFalse(log.record("microphone", "Mic: energy monitor active, level 900", "t2"))
        assertTrue(log.record("microphone", "Mic: off", "t3"))
        assertTrue(log.record("voice_process", "Voice Substrate: BOUND voice=10 app=11 ping=18ms restarts=0", "t4"))
        assertFalse(log.record("voice_process", "Voice Substrate: BOUND voice=10 app=11 ping=4ms restarts=0", "t5"))
        assertEquals(3, log.snapshot().size)
    }

    @Test
    fun `screen content is never included in diagnostic event state`() {
        val log = DiagnosticEventLog()
        log.record("screen_context", "Screen: private message from someone")
        assertEquals("available; content omitted", log.snapshot().single().state)
    }

    @Test
    fun `speech recognition transcript is excluded from exported event history`() {
        val log = DiagnosticEventLog()
        log.record("speech_recognition", "STT: heard my private bank account number 123456")
        val event = log.snapshot().single()
        assertEquals("recognition_result_received; transcript omitted", event.state)
        assertFalse(event.state.contains("123456"))
        assertFalse(event.state.contains("bank account"))
    }

    @Test
    fun `history remains bounded`() {
        val log = DiagnosticEventLog(capacity = 2)
        log.record("battery", "50%")
        log.record("network", "wifi")
        log.record("battery", "49%")
        assertEquals(2, log.snapshot().size)
        assertEquals("wifi", log.snapshot().first().state)
    }
}
