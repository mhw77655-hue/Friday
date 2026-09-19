package com.jarvis.app.memory

import com.jarvis.app.identity.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One user statement recorded in a past conversation session. This is the unit
 * of "recent experience" the background consolidation reviews — read from the
 * EXISTING conversation-session store ([com.jarvis.app.session.SessionManager]),
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
 * JARVIS improve between conversations, not just during them.
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
 * existing [MemoryGraphStore] via [com.jarvis.app.identity.WorldModelService].
 *
 * PersonaTuning is deliberately OUT of scope: personality only ever changes
 * from explicit live user feedback ([com.jarvis.app.identity.PersonaTuner]),
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
    private val idleIntervalMs: Long = DEFAULT_IDLE_INTERVAL_MS
) {

    @Volatile
    private var idleLoopRunning = false

    fun isIdleLoopRunning(): Boolean = idleLoopRunning

    /** Start the background idle tick. Idempotent; one loop per instance. */
    fun startIdleLoop() {
        if (idleLoopRunning) return
        idleLoopRunning = true
        scope.launch {
            while (true) {
                delay(idleIntervalMs)
                runConsolidationPass()
            }
        }
    }

    /**
     * Run one consolidation pass: review the recent per-session statements and
     * feed each through the EXISTING durability gate. Returns what was scanned
     * and what the gate promoted.
     */
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
    }
}