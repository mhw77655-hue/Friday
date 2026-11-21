package ai.friday.assistant.identity.owner

/**
 * Durable, non-biometric owner authority record.
 *
 * The record stores only the owner key, relationship id, authenticator class
 * and authorization time. It never stores biometric samples or device ids.
 */
data class OwnerBinding(
    val relationshipId: String,
    val ownerKey: String,
    val authenticator: String,
    val authenticatedAtEpochMs: Long
) {
    companion object {
        const val OWNER_RELATIONSHIP_ID = "owner"
    }
}
