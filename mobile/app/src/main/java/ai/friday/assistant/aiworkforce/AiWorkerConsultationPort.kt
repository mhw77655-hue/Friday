package ai.friday.assistant.aiworkforce

/** Sanitized response from a subscription-authenticated external AI worker. */
data class AiWorkerReply(
    val providerId: String,
    val response: String,
    val executionEnvironment: String
)

/**
 * Provider-neutral consultation port used by Friday's autonomous planner.
 * Implementations must use an explicitly authorized provider session and must
 * never expose or return credential material.
 */
interface AiWorkerConsultationPort {
    suspend fun consultAvailable(prompt: String): List<AiWorkerReply>
}
