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
    ): Boolean = activeSubsystems.any { subsystem ->
        val normalized = subsystem.trim().replace('-', '_').uppercase()
        normalized == "ENGINE" ||
            normalized == "FRIDAYENGINE" ||
            normalized.startsWith("ENGINE_") ||
            normalized.startsWith("FRIDAYENGINE_")
    }
}
