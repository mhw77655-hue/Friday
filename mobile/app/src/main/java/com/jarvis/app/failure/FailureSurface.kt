package com.jarvis.app.failure

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Central failure surface — the single place every meaningful failure in the
 * nervous system lands. One bus, one vocabulary; the UI, the recovery system,
 * and the tests all observe the same immutable flows.
 *
 * Guarantees:
 *  - [report] is thread-safe and cheap (a lock + a couple of list ops).
 *  - Identical failures deduplicate into one [FailureAggregate] with
 *    first/latest occurrence, count, current severity, and recovery status —
 *    no error-spam.
 *  - Nothing here throws into the caller; reporting a failure can never take
 *    the body down.
 *  - Recovery success/failure is recorded and NEVER hides the original failure
 *    (the aggregate keeps its first/latest event + count).
 *  - No mutable flows are exposed publicly.
 */
class FailureSurface(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val maxRecent: Int = 128,
    private val maxCorrelationsPerAggregate: Int = 8
) {

    /** Soft turn correlation: the coordinator stamps this at each new turn.
     *  Capture sites that don't know the turn inherit it. */
    @Volatile var currentTurnId: String? = null

    private val turnCounter = AtomicLong(0)

    /** Allocate a fresh turn id for the correlation chain (TURN-<ms>-<n>). */
    fun newTurnId(): String {
        val ts = nowMs()
        val n = turnCounter.incrementAndGet()
        return "TURN-$ts-$n"
    }

    // ── Exposed immutable flows ──
    private val _currentFailures = MutableStateFlow<List<FailureAggregate>>(emptyList())
    /** Active (OPEN/RECOVERING) failure conditions, most recent first. */
    val currentFailures: StateFlow<List<FailureAggregate>> = _currentFailures.asStateFlow()

    private val _recentFailures = MutableStateFlow<List<FailureEvent>>(emptyList())
    /** Bounded ring of the most recent occurrences (newest first) — also the
     *  per-turn failure chain, since events share a correlationId. */
    val recentFailures: StateFlow<List<FailureEvent>> = _recentFailures.asStateFlow()

    private val _worstSeverity = MutableStateFlow(FailureSeverity.INFO)
    /** Highest severity among currently active failures. */
    val worstSeverity: StateFlow<FailureSeverity> = _worstSeverity.asStateFlow()

    private val _subsystemHealth = MutableStateFlow<Map<String, SubsystemHealth>>(emptyMap())
    val subsystemHealth: StateFlow<Map<String, SubsystemHealth>> = _subsystemHealth.asStateFlow()

    private val _systemHealth = MutableStateFlow(SystemHealth(
        status = SubsystemStatus.HEALTHY,
        worstSeverity = FailureSeverity.INFO,
        activeFailureCount = 0,
        degradedCapabilityCount = 0,
        subsystemCount = 0,
        healthySubsystemCount = 0,
        degradedSubsystemCount = 0,
        downSubsystemCount = 0
    ))
    val systemHealth: StateFlow<SystemHealth> = _systemHealth.asStateFlow()

    private val _degradedCapabilities = MutableStateFlow<Set<String>>(emptySet())
    /** Canonical capability keys currently degraded (e.g. "STT_ARABIC"). */
    val degradedCapabilities: StateFlow<Set<String>> = _degradedCapabilities.asStateFlow()

    private val _recoveryEvents = MutableSharedFlow<RecoveryEvent>(extraBufferCapacity = 64)
    val recoveryEvents: SharedFlow<RecoveryEvent> = _recoveryEvents.asSharedFlow()

    private val _recentRecoveryEvents = MutableStateFlow<List<RecoveryEvent>>(emptyList())
    /** Bounded ring of recovery attempts/results (newest first). */
    val recentRecoveryEvents: StateFlow<List<RecoveryEvent>> = _recentRecoveryEvents.asStateFlow()

    private val _failureCountTotal = AtomicLong(0)
    /** Total occurrences ever reported (monotonic, for diagnostics). */
    val totalFailureCount: Long get() = _failureCountTotal.get()

    // ── Internals (locked) ──
    private val lock = Any()
    private val aggregates = LinkedHashMap<String, FailureAggregate>()
    private val recentEvents = ArrayDeque<FailureEvent>()
    private val recentRecovery = ArrayDeque<RecoveryEvent>()
    private val subsystemStats = HashMap<String, MutableSubsystemStat>()
    private val subsystemToCapability = HashMap<String, String>() // subsystem → degraded capability key
    private val idCounter = AtomicLong(nowMs())

    private class MutableSubsystemStat {
        var activeFailures = 0
        var totalFailures = 0
        var totalRecoveries = 0
        var lastFailureAt: Long? = null
        var lastRecoveryAt: Long? = null
        var lastFailureSeverity: FailureSeverity? = null
    }

    // ─────────────────────────────────────────────────────────────────── reporting

    /**
     * Report one failure. Classifies (severity/category), dedupes, updates
     * health, and returns the canonical [FailureEvent] (for tests / chains).
     */
    fun report(report: FailureReport): FailureEvent {
        val correlation = report.correlationId ?: currentTurnId
        val userMessage = report.userMessage
            ?: UserFailureText.translate(report)
        val event = FailureEvent(
            id = newId("fail"),
            timestamp = nowMs(),
            source = report.source ?: report.subsystem,
            subsystem = report.subsystem,
            operation = report.operation,
            severity = report.severity,
            category = report.category,
            message = report.message,
            cause = report.cause,
            state = report.state,
            dependency = report.dependency,
            recoverability = report.recoverability,
            correlationId = correlation,
            attempt = report.attempt,
            recoveryAction = report.recoveryAction,
            metadata = report.metadata,
            userMessage = userMessage,
            failureCause = report.failureCause,
            relatedStepId = report.relatedStepId,
            relatedCapability = report.relatedCapability,
            environment = report.environment,
            rootCauseHypothesis = report.rootCauseHypothesis,
            resourceState = report.resourceState
        )
        _failureCountTotal.incrementAndGet()

        val aggregate = synchronized(lock) {
            val key = report.dedupeKey
            val existing = aggregates[key]
            val updated = if (existing != null) {
                existing.copy(
                    latestEventId = event.id,
                    latestSeen = event.timestamp,
                    occurrenceCount = existing.occurrenceCount + 1,
                    currentSeverity = FailureSeverity.max(existing.currentSeverity, report.severity),
                    status = FailureStatus.OPEN, // failed again — re-open if it had recovered
                    lastUserMessage = userMessage ?: existing.lastUserMessage,
                    correlationIds = (listOfNotNull(correlation) + existing.correlationIds)
                        .distinct().take(maxCorrelationsPerAggregate)
                )
            } else {
                FailureAggregate(
                    key = key,
                    subsystem = report.subsystem,
                    operation = report.operation,
                    messageKey = report.message.trim(),
                    latestEventId = event.id,
                    firstSeen = event.timestamp,
                    latestSeen = event.timestamp,
                    occurrenceCount = 1,
                    currentSeverity = report.severity,
                    category = report.category,
                    dependency = report.dependency,
                    status = FailureStatus.OPEN,
                    lastRecoveryAction = report.recoveryAction,
                    lastRecoveryResult = RecoveryResult.NOT_ATTEMPTED,
                    recoveryAttempts = 0,
                    recoverySuccesses = 0,
                    correlationIds = listOfNotNull(correlation),
                    lastUserMessage = userMessage
                )
            }
            aggregates[key] = updated
            updateStatsFor(event, report.subsystem, statUp = { it.activeFailures++ ; it.totalFailures++ })
            updated
        }

        pushRecent(event)
        recomputePublished(aggregateKeyChanged = aggregate.key)
        return event
    }

    /**
     * Mark a failure condition recovered (or recovery failed / component
     * disabled). Records a [RecoveryEvent]; the original failure stays in
     * history and the aggregate keeps its count — recovery never hides it.
     *
     * @return the recovery event, or null if no matching aggregate existed.
     */
    fun recover(
        aggregateKey: String,
        result: RecoveryResult,
        message: String,
        action: RecoveryAction = RecoveryAction.NONE,
        correlationId: String? = null
    ): RecoveryEvent? {
        val recoveryEvent = synchronized(lock) {
            val existing = aggregates[aggregateKey] ?: return@synchronized null
            val newStatus = when (result) {
                RecoveryResult.SUCCESS -> FailureStatus.RECOVERED
                RecoveryResult.FAILED -> FailureStatus.OPEN
                else -> existing.status
            }
            val updated = existing.copy(
                status = newStatus,
                lastRecoveryAction = action,
                lastRecoveryResult = result,
                recoveryAttempts = existing.recoveryAttempts + 1,
                recoverySuccesses = existing.recoverySuccesses + (if (result == RecoveryResult.SUCCESS) 1 else 0)
            )
            aggregates[aggregateKey] = updated
            subsystemStats[existing.subsystem]?.let {
                it.totalRecoveries++
                if (result == RecoveryResult.SUCCESS) {
                    it.activeFailures = (it.activeFailures - 1).coerceAtLeast(0)
                    it.lastRecoveryAt = nowMs()
                }
            }
            RecoveryEvent(
                id = newId("rec"),
                timestamp = nowMs(),
                subsystem = existing.subsystem,
                operation = existing.operation,
                aggregateKey = aggregateKey,
                action = action,
                result = result,
                attempt = existing.recoveryAttempts,
                message = message,
                correlationId = correlationId ?: currentTurnId
            )
        }
        if (recoveryEvent != null) {
            synchronized(lock) {
                recentRecovery.addFirst(recoveryEvent)
                while (recentRecovery.size > maxRecent) recentRecovery.removeLast()
                _recentRecoveryEvents.value = recentRecovery.toList()
            }
            _recoveryEvents.tryEmit(recoveryEvent)
            recomputePublished(aggregateKeyChanged = aggregateKey)
        }
        return recoveryEvent
    }

    /** Mark a recovery attempt as started (aggregate → RECOVERING). */
    fun beginRecovery(aggregateKey: String, action: RecoveryAction, message: String) {
        synchronized(lock) {
            val existing = aggregates[aggregateKey] ?: return
            if (!existing.isActive) return
            aggregates[aggregateKey] = existing.copy(
                status = FailureStatus.RECOVERING,
                lastRecoveryAction = action
            )
        }
        recomputePublished(aggregateKeyChanged = aggregateKey)
    }

    /** Immutable point-in-time snapshot for diagnostics / self-diagnosis. */
    fun snapshot(): FailureSnapshot = synchronized(lock) {
        FailureSnapshot(
            systemHealth = _systemHealth.value,
            currentFailures = _currentFailures.value,
            recentFailures = _recentFailures.value,
            recentRecoveryEvents = _recentRecoveryEvents.value,
            subsystemHealth = _subsystemHealth.value,
            degradedCapabilities = _degradedCapabilities.value,
            timestamp = nowMs()
        )
    }

    // ─────────────────────────────────────────────────────────────── internals

    private fun pushRecent(event: FailureEvent) = synchronized(lock) {
        recentEvents.addFirst(event)
        while (recentEvents.size > maxRecent) recentEvents.removeLast()
        _recentFailures.value = recentEvents.toList()
    }

    /** Recompute the derived flows after any aggregate/stat change. */
    private fun recomputePublished(aggregateKeyChanged: String) {
        synchronized(lock) {
            val active = aggregates.values.filter { it.isActive }.sortedByDescending { it.latestSeen }

            // Worst severity among active failures.
            val worst = active.maxByOrNull { it.currentSeverity.rank }?.currentSeverity ?: FailureSeverity.INFO
            _currentFailures.value = active
            _worstSeverity.value = worst

            // Subsystem health map.
            val health = LinkedHashMap<String, SubsystemHealth>(subsystemStats.size)
            for ((subsystem, stat) in subsystemStats) {
                val worstSeverity = stat.lastFailureSeverity
                val status = when {
                    worstSeverity == null -> SubsystemStatus.HEALTHY
                    stat.activeFailures > 0 && worstSeverity.rank >= FailureSeverity.ERROR.rank ->
                        SubsystemStatus.DOWN
                    stat.activeFailures > 0 -> SubsystemStatus.DEGRADED
                    else -> SubsystemStatus.HEALTHY
                }
                health[subsystem] = SubsystemHealth(
                    subsystem = subsystem,
                    status = status,
                    activeFailures = stat.activeFailures,
                    totalFailures = stat.totalFailures,
                    totalRecoveries = stat.totalRecoveries,
                    lastFailureAt = stat.lastFailureAt,
                    lastRecoveryAt = stat.lastRecoveryAt,
                    lastFailureSeverity = stat.lastFailureSeverity
                )
            }
            _subsystemHealth.value = health

            // Degraded capability set = subsystems with active DEGRADED+ failures.
            val degraded = active
                .filter { it.currentSeverity.rank >= FailureSeverity.DEGRADED.rank }
                .map { subsystemToCapability[it.subsystem] ?: it.subsystem }
                .toSet()
            _degradedCapabilities.value = degraded

            // Overall system health.
            val downCount = health.values.count { it.status == SubsystemStatus.DOWN }
            val degradedCount = health.values.count { it.status == SubsystemStatus.DEGRADED }
            val overall = when {
                downCount > 0 -> SubsystemStatus.DOWN
                degradedCount > 0 || active.isNotEmpty() -> SubsystemStatus.DEGRADED
                else -> SubsystemStatus.HEALTHY
            }
            _systemHealth.value = SystemHealth(
                status = overall,
                worstSeverity = worst,
                activeFailureCount = active.size,
                degradedCapabilityCount = degraded.size,
                subsystemCount = health.size,
                healthySubsystemCount = health.values.count { it.status == SubsystemStatus.HEALTHY },
                degradedSubsystemCount = degradedCount,
                downSubsystemCount = downCount
            )
        }
    }

    private fun updateStatsFor(event: FailureEvent, subsystem: String, statUp: (MutableSubsystemStat) -> Unit) {
        val stat = subsystemStats.getOrPut(subsystem) { MutableSubsystemStat() }
        statUp(stat)
        stat.lastFailureAt = event.timestamp
        stat.lastFailureSeverity = FailureSeverity.max(stat.lastFailureSeverity ?: FailureSeverity.INFO, event.severity)
    }

    /** Register the human-readable capability key for a subsystem (degradation label). */
    fun mapCapability(subsystem: String, capabilityKey: String) {
        synchronized(lock) { subsystemToCapability[subsystem] = capabilityKey }
    }

    private fun newId(prefix: String): String = "$prefix-${idCounter.incrementAndGet()}"

    /** Test/diagnostics helper: clear all state. */
    fun reset() = synchronized(lock) {
        aggregates.clear()
        recentEvents.clear()
        recentRecovery.clear()
        subsystemStats.clear()
        subsystemToCapability.clear()
        _currentFailures.value = emptyList()
        _recentFailures.value = emptyList()
        _recentRecoveryEvents.value = emptyList()
        _worstSeverity.value = FailureSeverity.INFO
        _subsystemHealth.value = emptyMap()
        _degradedCapabilities.value = emptySet()
        recomputePublished("")
    }
}
