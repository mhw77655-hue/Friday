package ai.friday.assistant.humancore.protocol

/**
 * Compatibility aliases for quarantined legacy Human Core source and old
 * test fixtures. Friday's production turn path imports ai.friday.assistant.turn
 * directly and must not depend on this package.
 */
@Deprecated("Use ai.friday.assistant.turn.TurnContext; Human Core is quarantined.")
typealias SessionContext = ai.friday.assistant.turn.TurnContext

@Deprecated("Use ai.friday.assistant.turn.TurnProsody; Human Core is quarantined.")
typealias ProsodyHint = ai.friday.assistant.turn.TurnProsody
