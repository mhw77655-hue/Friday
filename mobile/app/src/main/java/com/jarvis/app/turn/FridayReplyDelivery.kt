package com.jarvis.app.turn

/**
 * Delivery-boundary hygiene for a reply already produced by Friday's
 * CognitiveEngine. This does not infer meaning, style personality, or veto
 * content based on a second fixed cognitive authority.
 *
 * It removes transport control characters and refuses to emit an empty reply.
 * Semantic evaluation and action authorization remain in Friday's cognitive
 * and local policy paths.
 */
object FridayReplyDelivery {
    const val EMPTY_REPLY_FALLBACK =
        "I couldn't produce a reply this time. Please try again."

    fun deliver(candidate: String): String {
        val normalized = buildString(candidate.length) {
            for (character in candidate) {
                when {
                    character == '\n' || character == '\t' -> append(character)
                    !character.isISOControl() -> append(character)
                    else -> Unit
                }
            }
        }.trim()

        return normalized.ifBlank { EMPTY_REPLY_FALLBACK }
    }
}
