package com.jarvis.app.failure

/**
 * Internal failure → user-facing message. Users never see raw stack traces or
 * subsystem names; the technical detail stays in the FailureEvent (visible to
 * diagnostics). Returns null for failures that should not surface to the user
 * at all (the body degrades silently — but the failure is still on the surface).
 */
object UserFailureText {

    /** Translate a report into a user-visible sentence, or null for none. */
    fun translate(report: FailureReport): String? {
        report.userMessage?.let { return it }

        return when (report.category) {
            FailureCategory.STT -> when {
                report.subsystem.contains("ARABIC") || report.dependency?.contains("ar-EG") == true ->
                    "Arabic recognition isn't available right now."
                else -> "I didn't catch that. Can you say it again?"
            }
            FailureCategory.VOICE_INPUT -> "I'm having trouble hearing you right now."
            FailureCategory.TTS -> "Voice output is temporarily unavailable."
            FailureCategory.AUDIO -> "The audio system is unavailable right now."
            FailureCategory.MODEL, FailureCategory.BRAIN -> "My local brain isn't reachable right now."
            FailureCategory.MEMORY -> "I couldn't save that memory, but I can continue."
            FailureCategory.VOCABULARY -> "I couldn't save that word, but I can continue."
            FailureCategory.PRESENCE -> null
            FailureCategory.VISUAL -> null
            FailureCategory.LATENCY -> null
            FailureCategory.RESOURCE -> "I'm running on reduced resources, so I'll be a bit slower."
            FailureCategory.LIFECYCLE -> null
            FailureCategory.PERSISTENCE -> "I couldn't save that right now."
            FailureCategory.CAPABILITY -> "One of my components is temporarily unavailable."
            FailureCategory.DEPENDENCY -> "One of my services isn't reachable right now."
            FailureCategory.CONFIGURATION -> null
            FailureCategory.INTERNAL -> null
            FailureCategory.ENVIRONMENT -> "I couldn't prepare an isolated workspace for that."
            FailureCategory.EVOLUTION -> "I couldn't evolve that capability safely right now."
            FailureCategory.SYNCHRONIZATION -> "One of my subsystems didn't stay in sync."
            FailureCategory.WAKE_WORD -> "I'm not catching my wake word right now."
            FailureCategory.LANGUAGE -> null
            FailureCategory.UNKNOWN -> null
        }?.let { onlyIfUserRelevant(report, it) }
    }

    /** Low-severity failures don't interrupt the user with a sentence. */
    private fun onlyIfUserRelevant(report: FailureReport, message: String): String? =
        if (report.severity.rank >= FailureSeverity.RECOVERABLE.rank) message else null
}
