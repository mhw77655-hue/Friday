package com.jarvis.app.diagnostics

/**
 * Removes common credentials and personal contact data before diagnostic
 * strings are written to an exportable bundle. Structured event fields are
 * preferred over arbitrary log text.
 */
internal object DiagnosticRedactor {
    private val bearer = Regex("(?i)\\b(Bearer\\s+)[A-Za-z0-9._~+/-]+=*")
    private val secretAssignment = Regex(
        "(?i)\\b(api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|password|passwd|secret)\\b(\\s*[:=]\\s*)[\"']?[^\\s,\"']+"
    )
    private val email = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val urlQuery = Regex("(https?://[^\\s?]+)\\?[^\\s]+", RegexOption.IGNORE_CASE)
    private val phone = Regex("(?<!\\w)(?:\\+?\\d[\\d(). -]{7,}\\d)(?!\\w)")

    fun redact(value: String?): String? {
        if (value == null) return null
        return value
            .replace(bearer) { "${it.groupValues[1]}[REDACTED]" }
            .replace(secretAssignment) { "${it.groupValues[1]}${it.groupValues[2]}[REDACTED]" }
            .replace(urlQuery) { "${it.groupValues[1]}?[REDACTED]" }
            .replace(email, "[REDACTED_EMAIL]")
            .replace(phone, "[REDACTED_PHONE]")
            .take(500)
    }

    fun safeSignalName(value: String): String? =
        value.takeIf { it.matches(Regex("[A-Za-z0-9_.-]{1,48}")) }

    fun safeMetadataKey(value: String): Boolean = value.lowercase() in setOf(
        "model", "modelid", "provider", "statuscode", "httpstatus", "latencyms",
        "durationms", "attempt", "resource", "temperaturec", "memorymb",
        "freememorymb", "bytes", "fallback", "transport", "reasoncode"
    )
}
