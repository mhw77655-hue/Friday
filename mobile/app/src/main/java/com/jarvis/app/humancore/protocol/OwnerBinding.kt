package com.jarvis.app.humancore.protocol

/**
 * Compatibility alias for legacy Human Core fixtures. Production owner
 * authority is owned by com.jarvis.app.identity.owner and stored by Android
 * Keystore through AndroidOwnerBindingPort.
 */
@Deprecated("Use com.jarvis.app.identity.owner.OwnerBinding.")
typealias OwnerBinding = com.jarvis.app.identity.owner.OwnerBinding
