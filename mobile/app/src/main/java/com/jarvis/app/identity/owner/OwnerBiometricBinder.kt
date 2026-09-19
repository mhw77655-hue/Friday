package com.jarvis.app.identity.owner

import com.jarvis.app.humancore.protocol.OwnerBinding

/**
 * The real typed owner-binding API. Given an Android `BiometricPrompt`
 * authorization result ([BiometricAuthResult]) it binds the owner relationship
 * to the stable owner identity ([ownerKey]) through [OwnerBindingPort].
 *
 * Authorization rules (fail-closed):
 *  - only [BiometricAuthResult.Success] can bind — [Error] and [Cancelled]
 *    never touch the store;
 *  - the binding is keyed to [OwnerBinding.OWNER_RELATIONSHIP_ID] and
 *    [ownerKey], never to a device id: authority is the authenticated human,
 *    independent of which physical device is in use;
 *  - the single-owner relationship refuses a different [ownerKey] once bound
 *    ([OwnerBindingOutcome.Conflict]) — re-authenticating the SAME owner just
 *    refreshes the binding timestamp.
 *
 * [ownerKey] defaults to the stable user-model owner identity
 * ([com.jarvis.app.identity.WorldModelService.USER_NODE_NAME] — the durable,
 * device-independent owner key), so production callers bind the owner without
 * supplying an identifier of their own.
 */
class OwnerBiometricBinder(
    private val target: OwnerBindingPort,
    private val ownerKey: String = com.jarvis.app.identity.WorldModelService.USER_NODE_NAME
) {

    fun bind(result: BiometricAuthResult): OwnerBindingOutcome {
        val existing = target.ownerBinding()
        if (existing != null && existing.ownerKey != ownerKey) {
            return OwnerBindingOutcome.Conflict(existingOwner = existing)
        }
        return when (result) {
            is BiometricAuthResult.Success -> {
                val binding = OwnerBinding(
                    relationshipId = OwnerBinding.OWNER_RELATIONSHIP_ID,
                    ownerKey = ownerKey,
                    authenticator = result.authenticators.label,
                    authenticatedAtEpochMs = result.authenticatedAtEpochMs
                )
                val accepted = target.bindOwner(binding)
                if (!accepted) {
                    OwnerBindingOutcome.Conflict(existingOwner = existing)
                } else if (existing == null) {
                    OwnerBindingOutcome.Bound(binding)
                } else {
                    OwnerBindingOutcome.ReAuthenticated(binding)
                }
            }
            is BiometricAuthResult.Error ->
                OwnerBindingOutcome.NotAuthorized(result.errorCode, result.errorMessage)
            BiometricAuthResult.Cancelled -> OwnerBindingOutcome.Cancelled
        }
    }

    fun isOwnerBound(): Boolean = target.ownerBinding() != null

    fun currentBinding(): OwnerBinding? = target.ownerBinding()
}

/** Typed result of [OwnerBiometricBinder.bind]. */
sealed class OwnerBindingOutcome {
    /** The owner relationship was newly bound to the owner identity. */
    data class Bound(val binding: OwnerBinding) : OwnerBindingOutcome()

    /** The same owner re-authenticated; the binding timestamp was refreshed. */
    data class ReAuthenticated(val binding: OwnerBinding) : OwnerBindingOutcome()

    /**
     * A different owner identity attempted to bind an already-bound owner
     * relationship — refused; [existingOwner] is the incumbent binding.
     */
    data class Conflict(val existingOwner: OwnerBinding?) : OwnerBindingOutcome()

    /** The biometric authorization failed; nothing was bound. */
    data class NotAuthorized(val errorCode: Int, val errorMessage: String) : OwnerBindingOutcome()

    /** The user cancelled the prompt; nothing was bound. */
    object Cancelled : OwnerBindingOutcome()
}