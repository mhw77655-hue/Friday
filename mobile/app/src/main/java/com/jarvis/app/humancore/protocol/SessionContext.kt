package com.jarvis.app.humancore.protocol

/**
 * Compatibility aliases for quarantined legacy Human Core source and old
 * test fixtures. Friday's production turn path imports com.jarvis.app.turn
 * directly and must not depend on this package.
 */
@Deprecated("Use com.jarvis.app.turn.TurnContext; Human Core is quarantined.")
typealias SessionContext = com.jarvis.app.turn.TurnContext

@Deprecated("Use com.jarvis.app.turn.TurnProsody; Human Core is quarantined.")
typealias ProsodyHint = com.jarvis.app.turn.TurnProsody
