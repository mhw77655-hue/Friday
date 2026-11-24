package ai.friday.assistant.memory

import ai.friday.assistant.identity.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One user statement recorded in a past conversation session. This is the unit
 * of "recent experience" the background consolidation reviews — read from the
 * EXISTING conversation-session store ([ai.friday.assistant.session.SessionManager]),
 * never a second memory system.
 */
data class RecentStatement(
    val sessionId: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** Report of one background consolidation pass. */
data class ConsolidationPassReport(
    val scanned: Int,
    val promoted: Int,
    val promotions: List<UserProfile.ProfileUpdate>
)

/**
 * MEMORY-CONSOLIDATION-BACKGROUND-LOOP — the background process that lets
 * FRIDAY improve between conversations, not just during them.
 *
 * The live turn can only ever observe the CURRENT conversation under a single
 * session id (IdentityContext defaults to session "live"), so the durability
 * gate's repetition arm — "a pattern repeating across 2+ distinct sessions" —
 * can never fire from the hot path. This loop runs OFF the hot path and feeds
 * the durability gate the REAL per-session statements recorded in past
 * conversations, which is the only way that repetition rule can trigger.
 *
 * It invokes NO new rule and NO new store: promotion goes through the existing
 * [UserProfile.ingestUtterance] durability gate (explicit statement, or a
 * pattern repeated across 2+ distinct sessions) which persists onto the
 * existing [MemoryGraphStore] via [ai.friday.assistant.identity.WorldModelService].
 *
 * PersonaTuning is deliberately OUT of scope: personality only ever changes
 * from explicit live user feedback ([ai.friday.assistant.identity.PersonaTuner]),
 * and this loop must never touch it.
 *
 * Triggering: [startIdleLoop] runs a slow background idle tick in production;
 * [runConsolidationPass] is the same body exposed for manual triggering and
 * deterministic tests.
 */
class MemoryConsolidationLoop(
    private val userProfile: UserProfile,
    /** Source of recent per-session user statements (production: the real SessionManager). */
    private val recentExperience: () -> List<RecentStatement>,
    private val scope: CoroutineScope,
    private val idleIntervalMs: Long = DEFAULT_IDLE_INTERVAL_MS,
    /** Consolidation can scan many sessions and write durable memory; never inherit the UI dispatcher. */
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Reports a failed pass while the loop remains alive for a bounded retry. */
    private val onFailure: (Throwable) -> Unit = {}
) {
    init {
        require(idleIntervalMs > 0) { "idleIntervalMs must be positive" }
    }

    @Volatile
    private var idleLoopRunning = false

    fun isIdleLoopRunning(): Boolean = idleLoopRunning

    /** Start the background idle tick. Idempotent; one loop per instance. */
    @Synchronized
    fun startIdleLoop() {
        if (idleLoopRunning) return
        idleLoopRunning = true
        val job = scope.launch(workDispatcher) {
            var consecutiveFailures = 0
            while (isActive) {
                delay(idleIntervalMs)
                try {
                    runConsolidationPass()
                    consecutiveFailures = 0
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(8)
                    runCatching { onFailure(failure) }
                    // Keep the loop alive without creating a hot retry cycle.
                    val multiplier = 1L shl (consecutiveFailures - 1)
                    delay((idleIntervalMs * multiplier).coerceAtMost(MAX_FAILURE_BACKOFF_MS))
                }
            }
        }
        // invokeOnCompletion also resets the flag when the parent scope was
        // already cancelled and the coroutine body never began execution.
        job.invokeOnCompletion {
            synchronized(this) { idleLoopRunning = false }
        }
    }

    /**
     * Run one consolidation pass: review the recent per-session statements and
     * feed each through the EXISTING durability gate. Returns what was scanned
     * and what the gate promoted.
     */
    @Synchronized
    fun runConsolidationPass(): ConsolidationPassReport {
        val statements = recentExperience().sortedBy { it.timestamp }
        var promoted = 0
        val promotions = mutableListOf<UserProfile.ProfileUpdate>()
        for (statement in statements) {
            val update = userProfile.ingestUtterance(statement.text, statement.sessionId)
            if (update != null) {
                promoted++
                promotions.add(update)
            }
        }
        return ConsolidationPassReport(
            scanned = statements.size,
            promoted = promoted,
            promotions = promotions
        )
    }

    companion object {
        const val DEFAULT_IDLE_INTERVAL_MS: Long = 5 * 60_000L
        const val MAX_FAILURE_BACKOFF_MS: Long = 30 * 60_000L
    }
}