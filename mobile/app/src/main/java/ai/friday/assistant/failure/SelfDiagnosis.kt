package ai.friday.assistant.failure

/**
 * Deterministic, evidence-first self-diagnosis. When a serious failure lands,
 * [diagnose] answers the 9 questions from the existing surface state — no AI,
 * no extra monitoring. Everything comes from [FailureSurface] data already in
 * memory, so it is cheap and always answerable.
 *
 * Answers:
 *  - what failed                          → the event itself
 *  - what dependency failed               → event.dependency
 *  - last successful stage                → the turn chain before this failure
 *  - what changed immediately before      → the prior failure (same turn or last)
 *  - is the failure isolated              → other subsystems failing in the same turn?
 *  - is it recoverable                    → event.recoverability
 *  - what fallback exists                 → recovery events / declared action
 *  - did fallback succeed                 → matching recovery result
 *  - subsystem healthy afterward          → current subsystem health
 */
class SelfDiagnosis(private val surface: FailureSurface) {

    data class Diagnosis(
        val timestamp: Long,
        val event: FailureEvent,
        val dependency: String?,
        val lastSuccessfulStage: String?,
        val changedBefore: String,
        val isolated: Boolean,
        val recoverable: Boolean,
        val fallbackAvailable: String?,
        val fallbackSucceeded: Boolean?,
        val subsystemHealthyAfter: Boolean
    ) {
        val summary: String
            get() = buildString {
                append("${event.subsystem}.${event.operation} failed (${event.severity.name}): ${event.message}")
                append(FailureEvidence.describe(event))
                event.metadata["exception_chain"]?.takeIf { it.isNotBlank() }?.let { append(" | exception=$it") }
                event.metadata["stack_frames"]?.takeIf { it.isNotBlank() }?.let { append(" | stackFrames=$it") }
                event.failureCause?.let { append(" | failureCause=${it.name}") }
                event.rootCauseHypothesis?.takeIf { it.isNotBlank() }?.let { append(" | rootCauseHypothesis=$it") }
                event.relatedStepId?.let { append(" | step=$it") }
                event.relatedCapability?.let { append(" | capability=$it") }
                event.environment?.let { append(" | environment=$it") }
                event.resourceState?.let { append(" | resources=$it") }
                dependency?.let { append(" | dep=$it") }
                lastSuccessfulStage?.let { append(" | lastOk=$it") }
                append(" | isolated=$isolated recoverable=$recoverable")
                fallbackSucceeded?.let { append(" | fallbackOk=$it" ) }
                append(" | now=${if (subsystemHealthyAfter) "healthy" else "still failing"}")
            }
    }

    /** Deterministic diagnosis from the current surface snapshot. No suspend. */
    fun diagnose(event: FailureEvent): Diagnosis {
        val snapshot = surface.snapshot()

        // Same-turn chain (the failure trail for this turn, oldest → newest).
        val turnEvents = event.correlationId?.let { cid ->
            snapshot.recentFailures.filter { it.correlationId == cid }
        }.orEmpty()

        // Last successful stage = the event's own `state` (body state when the
        // failure fired) — the stage we were in that did not complete.
        val lastSuccessfulStage = event.state

        // What changed immediately before: the prior failure in the same turn,
        // else the prior failure overall.
        val priorInTurn = turnEvents.filter { it.id != event.id }
            .maxByOrNull { it.timestamp }
        val priorOverall = snapshot.recentFailures.firstOrNull { it.id != event.id }
        val prior = priorInTurn ?: priorOverall
        val changedBefore = prior?.let {
            "previous failure ${it.subsystem}.${it.operation} (${it.severity.name}) ${
                (event.timestamp - it.timestamp).coerceAtLeast(0)
            }ms earlier"
        } ?: "no prior failure recorded"

        // Isolation: in this turn, are any OTHER subsystems failing?
        val otherSubsystemsThisTurn = turnEvents
            .filter { it.id != event.id && it.subsystem != event.subsystem }
            .map { it.subsystem }
            .distinct()
        val isolated = otherSubsystemsThisTurn.isEmpty()

        // Recoverability.
        val recoverable = event.recoverability != Recoverability.NONE &&
            event.recoverability != Recoverability.PERMANENT

        // Fallback: declared action + any recovery events for this subsystem
        // since the failure fired.
        val fallbackAction = event.recoveryAction.name
        val recentRecovery = snapshot.recentRecoveryEvents
            .firstOrNull {
                it.subsystem == event.subsystem &&
                    it.operation == event.operation &&
                    it.timestamp >= event.timestamp
            }
        val fallbackAvailable = when {
            fallbackAction != "NONE" -> fallbackAction
            recentRecovery != null -> recentRecovery.action.name
            else -> null
        }
        val fallbackSucceeded = recentRecovery?.let { it.result == RecoveryResult.SUCCESS }

        // Subsystem health afterward.
        val health = snapshot.subsystemHealth[event.subsystem]
        val subsystemHealthyAfter = health?.status == SubsystemStatus.HEALTHY

        return Diagnosis(
            timestamp = snapshot.timestamp,
            event = event,
            dependency = event.dependency,
            lastSuccessfulStage = lastSuccessfulStage,
            changedBefore = changedBefore,
            isolated = isolated,
            recoverable = recoverable,
            fallbackAvailable = fallbackAvailable,
            fallbackSucceeded = fallbackSucceeded,
            subsystemHealthyAfter = subsystemHealthyAfter
        )
    }

    // ───────────────────────────────────────────────────────────── 01D
    // Deeper answers: frequency, recurrence, location, dependents, recovery
    // history, resource-relatedness, and determinism. All derived from the
    // existing surface aggregates — still no AI, still deterministic.

    data class DetailedDiagnosis(
        val timestamp: Long,
        val event: FailureEvent,
        /** How many times this subsystem+operation has failed (all time). */
        val frequency: Int,
        /** Whether the same signature has failed more than once. */
        val recurring: Boolean,
        /** Subsystems that transitively depend on the failing subsystem. */
        val dependents: List<String>,
        /** How many recovery attempts have been made for this condition. */
        val recoveryAttempts: Int,
        /** Whether any recovery for this condition has ever succeeded. */
        val recoveryEverSucceeded: Boolean,
        /** Whether the failure mode is likely resource-driven. */
        val resourceRelated: Boolean,
        /** Whether the same failure mode will deterministically recur. */
        val likelyDeterministic: Boolean,
        /** Subsystems that failed in the same window (correlation), if any. */
        val correlatedSubsystems: List<String>
    )

    /**
     * Deterministic deep diagnosis. [dependentsOf] is provided by the caller
     * (typically a DependencyGraph) to avoid a coupling into the immune layer.
     */
    fun detailedDiagnosis(
        event: FailureEvent,
        dependentsOf: (String) -> List<String> = { emptyList() }
    ): DetailedDiagnosis {
        val snapshot = surface.snapshot()
        val aggregate = snapshot.currentFailures.firstOrNull {
            it.subsystem == event.subsystem &&
                it.operation == event.operation &&
                it.messageKey == event.message.trim()
        }
        // Diagnosis must survive successful recovery. Active aggregates are
        // authoritative while open; the bounded event/recovery trails preserve
        // recurrence and recovery evidence after an aggregate closes.
        val historicalOccurrences = snapshot.recentFailures.count {
            it.subsystem == event.subsystem &&
                it.operation == event.operation &&
                it.message.trim() == event.message.trim()
        }
        val matchingRecoveryEvents = snapshot.recentRecoveryEvents.filter {
            it.subsystem == event.subsystem && it.operation == event.operation
        }
        val frequency = maxOf(aggregate?.occurrenceCount ?: 0, historicalOccurrences).coerceAtLeast(1)
        val recurring = frequency > 1
        val recoveryAttempts = maxOf(aggregate?.recoveryAttempts ?: 0, matchingRecoveryEvents.size)
        val recoveryEverSucceeded = (aggregate?.recoverySuccesses ?: 0) > 0 ||
            matchingRecoveryEvents.any { it.result == RecoveryResult.SUCCESS }

        val resourceCauses = setOf(
            FailureCause.RESOURCE_EXHAUSTION, FailureCause.MEMORY_PRESSURE,
            FailureCause.CPU_PRESSURE, FailureCause.THERMAL_PRESSURE
        )
        val resourceRelated = event.failureCause in resourceCauses ||
            (event.resourceState != null && event.resourceState!!.isNotBlank())

        val deterministicCauses = setOf(
            FailureCause.INVALID_INPUT, FailureCause.INVALID_OUTPUT,
            FailureCause.UNSUPPORTED, FailureCause.PERMISSION_FAILURE,
            FailureCause.STATE_CORRUPTION, FailureCause.NATIVE_FAILURE,
            FailureCause.INTERNAL_ERROR
        )
        val likelyDeterministic = event.failureCause in deterministicCauses ||
            event.recoverability == Recoverability.NONE ||
            event.recoverability == Recoverability.PERMANENT

        val correlated = event.correlationId?.let { cid ->
            snapshot.recentFailures
                .filter { it.correlationId == cid && it.subsystem != event.subsystem }
                .map { it.subsystem }.distinct()
        }.orEmpty()

        return DetailedDiagnosis(
            timestamp = snapshot.timestamp,
            event = event,
            frequency = frequency,
            recurring = recurring,
            dependents = dependentsOf(event.subsystem),
            recoveryAttempts = recoveryAttempts,
            recoveryEverSucceeded = recoveryEverSucceeded,
            resourceRelated = resourceRelated,
            likelyDeterministic = likelyDeterministic,
            correlatedSubsystems = correlated
        )
    }
}
