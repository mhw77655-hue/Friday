package com.jarvis.app.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactorTest {
    @Test
    fun redactsCredentialsAndBearerTokens() {
        val value = "Authorization: Bearer abc.def.ghi api_key=super-secret password=hunter2"
        val result = DiagnosticRedactor.redact(value).orEmpty()

        assertFalse(result.contains("abc.def.ghi"))
        assertFalse(result.contains("super-secret"))
        assertFalse(result.contains("hunter2"))
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test
    fun redactsEmailPhoneAndUrlQuery() {
        val value = "contact test@example.com or +1 (415) 555-0134; https://host.test/path?token=private"
        val result = DiagnosticRedactor.redact(value).orEmpty()

        assertFalse(result.contains("test@example.com"))
        assertFalse(result.contains("415"))
        assertFalse(result.contains("token=private"))
        assertTrue(result.contains("[REDACTED_EMAIL]"))
        assertTrue(result.contains("[REDACTED_PHONE]"))
    }

    @Test
    fun limitsDiagnosticStringLength() {
        val result = DiagnosticRedactor.redact("x".repeat(900)).orEmpty()
        assertTrue(result.length <= 500)
    }

    @Test
    fun rejectsUnstructuredSignalNames() {
        assertTrue(DiagnosticRedactor.safeSignalName("voice.status").isNullOrEmpty().not())
        assertTrue(DiagnosticRedactor.safeSignalName("screen text\nwith content") == null)
    }
}
