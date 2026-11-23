package ai.friday.assistant.diagnostics

/**
 * Prevents a stale "Ready" composition-root flag from masking an active
 * engine-level incident in the authoritative FailureSurface.
 *
 * Readiness means construction completed; it does not mean the engine is
 * currently healthy. Both signals must agree before diagnostics may say PASS.
 */
object EngineFailureHealthGate {
    fun hasActiveEngineFailure(
        activeSubsystems: Iterable<String>
    ): Boolean = activeSubsystems.any { it.equals("ENGINE", ignoreCase = true) }
}
