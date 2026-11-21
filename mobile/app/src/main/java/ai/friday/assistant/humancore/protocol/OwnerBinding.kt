package ai.friday.assistant.humancore.protocol

/**
 * Compatibility alias for legacy Human Core fixtures. Production owner
 * authority is owned by ai.friday.assistant.identity.owner and stored by Android
 * Keystore through AndroidOwnerBindingPort.
 */
@Deprecated("Use ai.friday.assistant.identity.owner.OwnerBinding.")
typealias OwnerBinding = ai.friday.assistant.identity.owner.OwnerBinding
