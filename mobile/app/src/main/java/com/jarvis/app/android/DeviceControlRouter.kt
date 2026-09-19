package com.jarvis.app.android

/**
 * Resolves a [ControlAction] through the fallback chain, in order:
 *
 *   1. official API
 *   2. Intent
 *   3. MediaSession
 *   4. Accessibility
 *   5. Shizuku
 *   6. ADB
 *
 * Each attempt is logged, the first successful backend is used, and nothing is
 * ever reported done without [VerificationEvidence] reaching [VerificationStage.VERIFIED].
 */
class DeviceControlRouter(
    backends: List<ControlBackend>
) {

    private val chain: List<ControlBackend> = backends.sortedBy { ORDER.indexOf(it.id) }

    val attempts: MutableList<BackendAttempt> = mutableListOf()

    companion object {
        val ORDER = listOf(
            "official-api",
            "intent",
            "media-session",
            "accessibility",
            "shizuku",
            "adb"
        )
    }

    /** The ordered, available backends currently usable for a given action. */
    fun availableBackends(): List<ControlBackend> = chain.filter { it.available() }

    /**
     * Try each available backend in order; use the first one that returns
     * verified evidence. Returns the winning backend with evidence, or null
     * if none could verify the action.
     */
    fun execute(action: ControlAction): ControlBackend? {
        attempts.clear()
        for (backend in chain) {
            if (!backend.available()) {
                attempts.add(
                    BackendAttempt(
                        backendId = backend.id,
                        succeeded = false,
                        evidence = VerificationEvidence(VerificationStage.NOT_ATTEMPTED, backend.id, "backend unavailable"),
                        error = "unavailable"
                    )
                )
                continue
            }
            val evidence = try {
                backend.execute(action)
            } catch (e: Exception) {
                attempts.add(
                    BackendAttempt(
                        backendId = backend.id,
                        succeeded = false,
                        evidence = VerificationEvidence(VerificationStage.NOT_ATTEMPTED, backend.id, "threw"),
                        error = e.message
                    )
                )
                continue
            }
            attempts.add(BackendAttempt(backend.id, evidence.isComplete, evidence, if (evidence.isComplete) null else "not verified"))
            if (evidence.isComplete) {
                return backend
            }
        }
        return null
    }

    /** Ordered backend ids actually tried in the last [execute], for test logging. */
    fun lastTriedOrder(): List<String> = attempts.map { it.backendId }

    /** Ordered backend ids that succeeded in the last [execute], for test logging. */
    fun lastSuccessfulBackends(): List<String> = attempts.filter { it.succeeded }.map { it.backendId }
}
