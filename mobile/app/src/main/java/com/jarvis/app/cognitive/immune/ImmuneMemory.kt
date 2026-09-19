package com.jarvis.app.cognitive.immune

import com.jarvis.app.failure.EnvironmentFailure
import com.jarvis.app.failure.FailureSignature
import com.jarvis.app.failure.RecoveryAction
import com.jarvis.app.failure.RecoveryOutcomeRecord
import com.jarvis.app.failure.RecoveryResult

/**
 * One remembered condition and its recovery history. Recognition happens by
 * deterministic [FailureSignature] — "this resembles a previous failure."
 */
data class ImmuneMemoryHit(
    val signature: FailureSignature,
    val previousFailures: Int,
    val attemptedRecovery: RecoveryAction,
    /** The recovery that previously worked, when one exists. */
    val successfulRecovery: RecoveryAction?,
    val confidence: Float,
    val lastSeen: Long
)

/** In-memory failure memory contract. (Long-term MemoryStore promotion is a
 *  future seam; this stays self-contained and deterministic.) */
class ImmuneMemory(
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private class Entry(
        val signature: FailureSignature,
        val attempts: MutableList<RecoveryAction> = mutableListOf(),
        var successfulRecovery: RecoveryAction? = null,
        var count: Int = 0,
        var lastSeen: Long = 0L
    )

    private val entries = mutableMapOf<String, Entry>()
    private val ledger = mutableListOf<RecoveryOutcomeRecord>()

    /** Record one recovery observation keyed by signature. */
    fun record(
        signature: FailureSignature,
        attemptedAction: RecoveryAction,
        result: RecoveryResult,
        metadata: Map<String, String> = emptyMap()
    ) {
        val entry = entries.getOrPut(signature.key) { Entry(signature) }
        entry.count++
        entry.lastSeen = nowMs()
        entry.attempts.add(attemptedAction)
        if (result == RecoveryResult.SUCCESS) entry.successfulRecovery = attemptedAction

        ledger.add(
            RecoveryOutcomeRecord(
                id = "rec_${signature.key.hashCode()}_${entry.count}",
                signature = signature,
                timestamp = entry.lastSeen,
                failedOperation = signature.operation,
                attemptedAction = attemptedAction,
                result = result,
                succeeded = result == RecoveryResult.SUCCESS,
                metadata = metadata
            )
        )
    }

    /** Recognize a previous failure by signature. Null when never seen. */
    fun resembles(signature: FailureSignature): ImmuneMemoryHit? {
        val entry = entries[signature.key] ?: return null
        return ImmuneMemoryHit(
            signature = entry.signature,
            previousFailures = entry.count,
            attemptedRecovery = entry.attempts.lastOrNull() ?: RecoveryAction.NONE,
            successfulRecovery = entry.successfulRecovery,
            confidence = (0.5f + 0.1f * entry.count).coerceAtMost(0.95f),
            lastSeen = entry.lastSeen
        )
    }

    /** The full evidence ledger (later becomes research/evolution data). */
    fun evidenceLedger(): List<RecoveryOutcomeRecord> = ledger.toList()

    fun size(): Int = entries.size
}

/**
 * Contract for a future isolated capability environment. NOT built here — this
 * is the abstraction the host drives so a crashed/disappeared/malformed
 * environment becomes an observable failure instead of an app crash.
 */
interface CapabilityEnvironment {
    val environmentId: String
    fun execute(action: String, input: String): EnvironmentOutcome
}

/** Structured outcome of a capability-environment execution. */
sealed class EnvironmentOutcome {
    data class Succeeded(val output: String) : EnvironmentOutcome()
    data class Failed(val failure: EnvironmentFailure) : EnvironmentOutcome()
}
