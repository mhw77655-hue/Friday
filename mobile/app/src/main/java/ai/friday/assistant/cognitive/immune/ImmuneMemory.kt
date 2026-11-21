package ai.friday.assistant.cognitive.immune

import ai.friday.assistant.failure.EnvironmentFailure
import ai.friday.assistant.failure.FailureSignature
import ai.friday.assistant.failure.RecoveryAction
import ai.friday.assistant.failure.RecoveryOutcomeRecord
import ai.friday.assistant.failure.RecoveryResult

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
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxLedgerEntries: Int = DEFAULT_MAX_LEDGER_ENTRIES
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(maxLedgerEntries > 0) { "maxLedgerEntries must be positive" }
    }

    private data class Entry(
        val signature: FailureSignature,
        var lastAttempted: RecoveryAction = RecoveryAction.NONE,
        var successfulRecovery: RecoveryAction? = null,
        var count: Int = 0,
        var lastSeen: Long = 0L
    )

    /*
     * This is a bounded in-memory working set, not the durable source of truth.
     * Durable incident/recovery history is owned by FailureStore. Keep all
     * operations under one monitor so concurrent subsystem failures cannot
     * corrupt counts, LRU ordering, or the evidence ring.
     */
    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private val ledger = java.util.ArrayDeque<RecoveryOutcomeRecord>()
    private var recordSequence = 0L

    /** Record one recovery observation keyed by signature. */
    fun record(
        signature: FailureSignature,
        attemptedAction: RecoveryAction,
        result: RecoveryResult,
        metadata: Map<String, String> = emptyMap()
    ) = synchronized(lock) {
        var entry = entries[signature.key]
        if (entry == null) {
            if (entries.size >= maxEntries) {
                val eldestKey = entries.entries.iterator().next().key
                entries.remove(eldestKey)
            }
            entry = Entry(signature)
            entries[signature.key] = entry
        } else {
            // Refresh insertion order: recently observed signatures survive.
            entries.remove(signature.key)
            entries[signature.key] = entry
        }

        entry.count++
        entry.lastSeen = nowMs()
        entry.lastAttempted = attemptedAction
        if (result == RecoveryResult.SUCCESS) entry.successfulRecovery = attemptedAction

        recordSequence++
        ledger.addLast(
            RecoveryOutcomeRecord(
                id = "rec_$recordSequence",
                signature = signature,
                timestamp = entry.lastSeen,
                failedOperation = signature.operation,
                attemptedAction = attemptedAction,
                result = result,
                succeeded = result == RecoveryResult.SUCCESS,
                metadata = metadata.toMap()
            )
        )
        while (ledger.size > maxLedgerEntries) ledger.removeFirst()
    }

    /** Recognize a previous failure by signature. Null when never seen/evicted. */
    fun resembles(signature: FailureSignature): ImmuneMemoryHit? = synchronized(lock) {
        val entry = entries[signature.key] ?: return@synchronized null
        // Reads also refresh the working-set recency.
        entries.remove(signature.key)
        entries[signature.key] = entry
        ImmuneMemoryHit(
            signature = entry.signature,
            previousFailures = entry.count,
            attemptedRecovery = entry.lastAttempted,
            successfulRecovery = entry.successfulRecovery,
            confidence = (0.5f + 0.1f * entry.count).coerceAtMost(0.95f),
            lastSeen = entry.lastSeen
        )
    }

    /** Snapshot of the bounded evidence ring, oldest first. */
    fun evidenceLedger(): List<RecoveryOutcomeRecord> = synchronized(lock) { ledger.toList() }

    fun size(): Int = synchronized(lock) { entries.size }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 512
        const val DEFAULT_MAX_LEDGER_ENTRIES = 2_048
    }
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
