package com.jarvis.app.failure

/**
 * Canonical failure vocabulary for the JARVIS nervous system.
 *
 * Every meaningful subsystem failure becomes one immutable [FailureEvent].
 * [FailureReport] is the capture-site shape subsystems pass in; the
 * [FailureSurface] turns it into a [FailureEvent] + a deduplicated
 * [FailureAggregate] + per-subsystem [SubsystemHealth].
 */
object FailureModel

// ───────────────────────────────────────────────────────────────────────── severity

/** Impact of a failure, from informational to fatal-to-the-body. */
enum class FailureSeverity(val rank: Int) {
    INFO(0),
    WARNING(1),
    /** Transient fault, retryable without user impact (e.g. a one-off STT timeout). */
    RECOVERABLE(2),
    /** Capability unavailable, but the body keeps working degraded (e.g. no Arabic STT). */
    DEGRADED(3),
    /** A required operation failed (e.g. a turn's TTS did not speak). */
    ERROR(4),
    /** The nervous system itself or a core lifeline is down. */
    CRITICAL(5);

    companion object {
        fun max(a: FailureSeverity, b: FailureSeverity): FailureSeverity =
            if (a.rank >= b.rank) a else b
    }
}

// ─────────────────────────────────────────────────────────────────────── taxonomy

/** Subsystem taxonomy for the communication body. */
enum class FailureCategory {
    VOICE_INPUT,        // mic capture
    WAKE_WORD,          // wake-word detection
    STT,                // speech-to-text (Vosk / sherpa / platform)
    LANGUAGE,           // language detection / routing
    BRAIN,              // reasoning / reply generation
    MODEL,              // model fabric (load/unload/generate)
    TTS,                // text-to-speech
    AUDIO,              // audio resources (focus, playback, record)
    MEMORY,             // memory store (facts/episodic/context)
    VOCABULARY,         // vocabulary store
    PRESENCE,           // presence monitor
    VISUAL,             // orb / visual foundation
    LATENCY,            // latency layer / turn pipeline
    RESOURCE,           // RAM / thermal / battery / resource tier
    LIFECYCLE,          // start/stop/shutdown of a subsystem
    PERSISTENCE,        // disk persistence
    DEPENDENCY,         // an external dependency (server, engine, model asset)
    CAPABILITY,         // a registered capability in the fabric
    CONFIGURATION,      // config / capability manifest
    INTERNAL,           // internal invariant broken
    ENVIRONMENT,        // isolated execution environment (creation/teardown)
    EVOLUTION,          // genome / mutation / synthesis / evolution loop
    SYNCHRONIZATION,    // sync fabric / micro-system federation
    UNKNOWN
}

// ────────────────────────────────────────────────────────────────── recoverability

/** How much latitude the recovery system has for this failure. */
enum class Recoverability {
    /** No automatic recovery makes sense (config error, permanent). */
    NONE,
    /** Safe to retry a couple of times quickly. */
    RETRYABLE,
    /** Recovery possible after a delay / reinit / fallback. */
    RECOVERABLE,
    /** Will never recover without an external change (language pack, permission). */
    PERMANENT
}

/** The strategy a recovery system should apply. */
enum class RecoveryAction {
    NONE,
    RETRY,
    RETRY_WITH_BACKOFF,
    FALLBACK,
    RELOAD,
    RESET_COMPONENT,
    REINITIALIZE,
    DEGRADE,
    DISABLE_COMPONENT,
    WAIT_FOR_RECOVERY,
    USER_ACTION_REQUIRED,
    ESCALATE
}

enum class RecoveryResult {
    PENDING,
    NOT_ATTEMPTED,
    SUCCESS,
    FAILED,
    SUPERSEDED
}

/** Lifecycle of a deduplicated failure aggregate. */
enum class FailureStatus {
    /** Failure is active right now. */
    OPEN,
    /** A recovery attempt is in flight. */
    RECOVERING,
    /** Recovery succeeded; the condition is resolved. */
    RECOVERED,
    /** The subsystem was deliberately disabled after repeated failure. */
    CLOSED
}

// ───────────────────────────────────────────────────────────────────────── events

/**
 * One immutable failure occurrence. Everything needed to answer:
 * what / where / when / why / what was JARVIS doing / what state /
 * what dependency / what was attempted / what fallback / did it recover /
 * how many times / current health.
 */
data class FailureEvent(
    val id: String,
    val timestamp: Long,
    val source: String,
    val subsystem: String,
    val operation: String,
    val severity: FailureSeverity,
    val category: FailureCategory,
    val message: String,
    val cause: String? = null,
    val state: String? = null,
    val dependency: String? = null,
    val recoverability: Recoverability = Recoverability.RETRYABLE,
    val correlationId: String? = null,
    val attempt: Int = 1,
    val recoveryAction: RecoveryAction = RecoveryAction.NONE,
    val metadata: Map<String, String> = emptyMap(),
    val userMessage: String? = null,
    // ── 01D: structured failure taxonomy + context (all defaulted, additive) ──
    /** Structured failure MODE (orthogonal to [category], which is the subsystem). */
    val failureCause: FailureCause? = null,
    val relatedStepId: String? = null,
    val relatedCapability: String? = null,
    val environment: String? = null,
    val rootCauseHypothesis: String? = null,
    /** Compact resource snapshot at failure time (key=value). */
    val resourceState: String? = null
)

/**
 * Capture-site shape. Subsystems report a [FailureReport]; the surface
 * stamps id/timestamp/correlation and owns the aggregate bookkeeping.
 */
data class FailureReport(
    val subsystem: String,
    val operation: String,
    val severity: FailureSeverity,
    val category: FailureCategory,
    val message: String,
    val source: String? = null,
    val cause: String? = null,
    val state: String? = null,
    val dependency: String? = null,
    val recoverability: Recoverability = Recoverability.RETRYABLE,
    val correlationId: String? = null,
    val attempt: Int = 1,
    val recoveryAction: RecoveryAction = RecoveryAction.NONE,
    val metadata: Map<String, String> = emptyMap(),
    val userMessage: String? = null,
    // ── 01D: structured failure taxonomy + context (all defaulted, additive) ──
    val failureCause: FailureCause? = null,
    val relatedStepId: String? = null,
    val relatedCapability: String? = null,
    val environment: String? = null,
    val rootCauseHypothesis: String? = null,
    val resourceState: String? = null
) {
    /** Canonical dedup key — same subsystem+operation+message collapse to one aggregate. */
    val dedupeKey: String
        get() = "$subsystem|$operation|${message.trim()}"
}

/**
 * Deduplicated, current state for one failure condition. Repeated identical
 * failures collapse here: first/latest occurrence, count, current severity,
 * recovery status, and the recent turns it appeared in.
 */
data class FailureAggregate(
    val key: String,
    val subsystem: String,
    val operation: String,
    val messageKey: String,
    val latestEventId: String,
    val firstSeen: Long,
    val latestSeen: Long,
    val occurrenceCount: Int,
    val currentSeverity: FailureSeverity,
    val category: FailureCategory,
    val dependency: String?,
    val status: FailureStatus,
    val lastRecoveryAction: RecoveryAction,
    val lastRecoveryResult: RecoveryResult,
    val recoveryAttempts: Int,
    val recoverySuccesses: Int,
    val correlationIds: List<String>,
    val lastUserMessage: String?
) {
    val isActive: Boolean get() = status == FailureStatus.OPEN || status == FailureStatus.RECOVERING
}

// ────────────────────────────────────────────────────────────────────── health

enum class SubsystemStatus { HEALTHY, DEGRADED, DOWN, UNKNOWN }

data class SubsystemHealth(
    val subsystem: String,
    val status: SubsystemStatus,
    val activeFailures: Int,
    val totalFailures: Int,
    val totalRecoveries: Int,
    val lastFailureAt: Long?,
    val lastRecoveryAt: Long?,
    val lastFailureSeverity: FailureSeverity?
)

/** Overall nervous-system health, recomputed on every change. */
data class SystemHealth(
    val status: SubsystemStatus,
    val worstSeverity: FailureSeverity,
    val activeFailureCount: Int,
    val degradedCapabilityCount: Int,
    val subsystemCount: Int,
    val healthySubsystemCount: Int,
    val degradedSubsystemCount: Int,
    val downSubsystemCount: Int
)

/** One recovery attempt or result — the "what happened afterward" trail. */
data class RecoveryEvent(
    val id: String,
    val timestamp: Long,
    val subsystem: String,
    val operation: String,
    val aggregateKey: String,
    val action: RecoveryAction,
    val result: RecoveryResult,
    val attempt: Int,
    val message: String,
    val correlationId: String? = null
)

/** Immutable point-in-time snapshot for diagnostics/self-diagnosis. */
data class FailureSnapshot(
    val systemHealth: SystemHealth,
    val currentFailures: List<FailureAggregate>,
    val recentFailures: List<FailureEvent>,
    val recentRecoveryEvents: List<RecoveryEvent>,
    val subsystemHealth: Map<String, SubsystemHealth>,
    val degradedCapabilities: Set<String>,
    val timestamp: Long
)

// ═══════════════════════════════════════════════════════════════════════════════
// 01D — Immune & resilience model (all additive)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Structured failure MODE — the failure taxonomy, orthogonal to [FailureCategory]
 * (which names the subsystem). A subsystem can fail in many modes; the mode
 * drives retryability, containment, and diagnosis.
 */
enum class FailureCause {
    TIMEOUT,
    RESOURCE_EXHAUSTION,
    MEMORY_PRESSURE,
    CPU_PRESSURE,
    THERMAL_PRESSURE,
    INVALID_INPUT,
    INVALID_OUTPUT,
    UNSUPPORTED,
    DEPENDENCY_FAILURE,
    PROCESS_FAILURE,
    NATIVE_FAILURE,
    IO_FAILURE,
    STATE_CORRUPTION,
    PERMISSION_FAILURE,
    INTERNAL_ERROR,
    UNKNOWN
}

/** Containment status of one subsystem/operation. */
enum class ContainmentStatus {
    HEALTHY,
    ISOLATED,
    DEGRADED,
    UNAVAILABLE,
    RECOVERING
}

/** Explicit degradation level — a subsystem can shrink instead of vanishing. */
enum class DegradationLevel {
    FULL,
    DEGRADED,
    LIMITED,
    OFFLINE,
    UNAVAILABLE,
    RECOVERING
}

/**
 * Deterministic failure signature for immune memory: two subsystems failing in
 * the same way collapse to the same signature regardless of timestamps.
 */
data class FailureSignature(
    val subsystem: String,
    val operation: String,
    val category: FailureCategory,
    val failureCause: FailureCause?
) {
    val key: String = "$subsystem|$operation|${category.name}|${failureCause?.name ?: "UNKNOWN"}"
}

/** A signature stamped onto a report (safe for absent fields). */
fun failureSignatureOf(report: FailureReport): FailureSignature = FailureSignature(
    subsystem = report.subsystem,
    operation = report.operation,
    category = report.category,
    failureCause = report.failureCause
)

/**
 * One recovery observation — the evidence trail for later research/evolution.
 * Records what failed, what was tried, the outcome, and whether it worked.
 */
data class RecoveryOutcomeRecord(
    val id: String,
    val signature: FailureSignature,
    val timestamp: Long,
    val failedOperation: String,
    val attemptedAction: RecoveryAction,
    val result: RecoveryResult,
    val succeeded: Boolean,
    val correlationId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Contract for isolated capability environments (01D prepares the abstraction;
 * real mutated environments are NOT built). A host that drives such an
 * environment must be able to observe its terminal states.
 */
sealed class EnvironmentFailure {
    data class ProcessFailed(val processId: String, val exitCode: Int, val signal: String? = null) : EnvironmentFailure()
    data class Unavailable(val environmentId: String, val reason: String) : EnvironmentFailure()
    data class MalformedOutput(val expected: String, val received: String) : EnvironmentFailure()
    data class ResourceExceeded(val resource: String, val limit: String) : EnvironmentFailure()
}

/** Map an environment terminal state onto the failure model. */
fun EnvironmentFailure.toFailureCause(): FailureCause = when (this) {
    is EnvironmentFailure.ProcessFailed -> FailureCause.PROCESS_FAILURE
    is EnvironmentFailure.Unavailable -> FailureCause.DEPENDENCY_FAILURE
    is EnvironmentFailure.MalformedOutput -> FailureCause.INVALID_OUTPUT
    is EnvironmentFailure.ResourceExceeded -> FailureCause.RESOURCE_EXHAUSTION
}
