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
data class DeviceControlExecution(
    val winner: ControlBackend?,
    val attempts: List<BackendAttempt>
)

/**
 * Resolves Android actions through an ordered fallback chain.
 *
 * Each invocation owns its own immutable attempt result. The legacy last-call
 * snapshot remains diagnostic only and is never used as the result of a
 * concurrent invocation.
 */
class DeviceControlRouter(
    backends: List<ControlBackend>
) {
    private val chain: List<ControlBackend>

    init {
        require(backends.map { it.id }.distinct().size == backends.size) {
            "Backend IDs must be unique."
        }
        chain = backends.sortedBy { backend ->
            ORDER.indexOf(backend.id).let { if (it < 0) Int.MAX_VALUE else it }
        }
    }

    private val diagnosticLock = Any()
    @Volatile private var lastAttempts: List<BackendAttempt> = emptyList()

    /** Immutable snapshot for diagnostics and legacy callers. */
    val attempts: List<BackendAttempt>
        get() = synchronized(diagnosticLock) { lastAttempts.toList() }

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
    fun availableBackends(): List<ControlBackend> = chain.filter { backend ->
        runCatching { backend.available() }.getOrDefault(false)
    }

    /** Compatibility API. Prefer [executeWithEvidence] for concurrent callers. */
    fun execute(action: ControlAction): ControlBackend? = executeWithEvidence(action).winner

    /**
     * Try every backend until one returns verified evidence. Attempts are
     * local to this invocation, so overlapping actions cannot overwrite the
     * evidence returned to either caller.
     */
    fun executeWithEvidence(action: ControlAction): DeviceControlExecution {
        val localAttempts = mutableListOf<BackendAttempt>()
        val parentTraceId = java.util.UUID.randomUUID().toString()
        var winner: ControlBackend? = null
        for (backend in chain) {
            val attemptStartedAt = System.nanoTime()
            val available = try {
                backend.available()
            } catch (failure: Exception) {
                localAttempts.add(
                    BackendAttempt(
                        backendId = backend.id,
                        succeeded = false,
                        evidence = VerificationEvidence(
                            VerificationStage.NOT_ATTEMPTED,
                            backend.id,
                            "availability check threw"
                        ),
                        error = failure.javaClass.simpleName,
                        durationMs = elapsedMs(attemptStartedAt),
                        failureStage = "availability_check"
                    )
                )
                continue
            }
            if (!available) {
                localAttempts.add(
                    BackendAttempt(
                        backendId = backend.id,
                        succeeded = false,
                        evidence = VerificationEvidence(
                            VerificationStage.NOT_ATTEMPTED,
                            backend.id,
                            "backend unavailable"
                        ),
                        error = "unavailable",
                        durationMs = elapsedMs(attemptStartedAt),
                        failureStage = "availability"
                    )
                )
                continue
            }
            val evidence = try {
                backend.execute(action)
            } catch (failure: Exception) {
                localAttempts.add(
                    BackendAttempt(
                        backendId = backend.id,
                        succeeded = false,
                        evidence = VerificationEvidence(
                            VerificationStage.NOT_ATTEMPTED,
                            backend.id,
                            "execution threw"
                        ),
                        error = failure.javaClass.simpleName,
                        durationMs = elapsedMs(attemptStartedAt),
                        failureStage = "execution"
                    )
                )
                continue
            }
            localAttempts.add(
                BackendAttempt(
                    backendId = backend.id,
                    succeeded = evidence.isComplete,
                    evidence = evidence,
                    error = if (evidence.isComplete) null else "not verified",
                    durationMs = elapsedMs(attemptStartedAt),
                    failureStage = if (evidence.isComplete) null else "verification"
                )
            )
            if (evidence.isComplete) {
                winner = backend
                break
            }
        }
        val snapshot = localAttempts.toList()
        synchronized(diagnosticLock) { lastAttempts = snapshot }

        // Persist every route considered, including unavailable and failed
        // rungs. Only explicit VERIFIED evidence is allowed to mark an event
        // verified; raw backend detail/error text is intentionally not exported.
        if (snapshot.isEmpty()) {
            com.jarvis.app.diagnostics.PhoneActionDiagnostics.record(
                action = action.actionType.name,
                route = "device_control_router",
                outcome = "unavailable",
                durationMs = 0,
                verificationState = "not_attempted",
                parentTraceId = parentTraceId
            )
        } else {
            snapshot.forEach { attempt ->
                val outcome = when {
                    attempt.succeeded -> "verified"
                    attempt.error == "unavailable" -> "unavailable"
                    attempt.error == "not verified" -> "not_verified"
                    else -> "failed"
                }
                val verificationState = when {
                    attempt.succeeded -> "verified"
                    attempt.evidence.stage == VerificationStage.NOT_ATTEMPTED -> "not_attempted"
                    else -> "not_independently_verified"
                }
                com.jarvis.app.diagnostics.PhoneActionDiagnostics.record(
                    action = action.actionType.name,
                    route = attempt.backendId,
                    outcome = outcome,
                    durationMs = attempt.durationMs,
                    verificationState = verificationState,
                    verificationStage = attempt.evidence.stage.name,
                    failureStage = attempt.failureStage,
                    parentTraceId = parentTraceId
                )
            }
        }
        return DeviceControlExecution(winner, snapshot)
    }

    private fun elapsedMs(startedAtNanos: Long): Long =
        ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)

    /** Ordered backend ids actually tried in the last completed call. */
    fun lastTriedOrder(): List<String> = attempts.map { it.backendId }

    /** Ordered backend ids that succeeded in the last completed call. */
    fun lastSuccessfulBackends(): List<String> = attempts.filter { it.succeeded }.map { it.backendId }
}
