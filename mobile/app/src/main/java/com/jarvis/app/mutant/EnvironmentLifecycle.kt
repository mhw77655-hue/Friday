package com.jarvis.app.mutant

import com.jarvis.app.mutation.EnvironmentInstance
import com.jarvis.app.mutation.EnvironmentStatus

/**
 * Mutant Environment lifecycle — the single guard over a disposable habitat
 * (§4). Enforces the legal [EnvironmentStatus] transitions so a destroyed
 * environment is never reused and a preserved one is never silently recycled.
 *
 *   CREATING → READY → RUNNING → READY … → PRESERVED → DESTROYED
 *     │          │                       │          │
 *     └──────────┴──────────────→ FAILED ┴──────────┘
 *
 * A failed experiment must not contaminate production; destruction is the
 * only way out of FAILED / PRESERVED / DESTROYED.
 */
object EnvironmentLifecycle {

    private val LEGAL: Map<EnvironmentStatus, Set<EnvironmentStatus>> = mapOf(
        EnvironmentStatus.CREATING to setOf(
            EnvironmentStatus.READY, EnvironmentStatus.FAILED, EnvironmentStatus.DESTROYED
        ),
        EnvironmentStatus.READY to setOf(
            EnvironmentStatus.RUNNING, EnvironmentStatus.PRESERVED,
            EnvironmentStatus.FAILED, EnvironmentStatus.DESTROYED
        ),
        EnvironmentStatus.RUNNING to setOf(
            EnvironmentStatus.READY, EnvironmentStatus.FAILED, EnvironmentStatus.DESTROYED
        ),
        EnvironmentStatus.PRESERVED to setOf(EnvironmentStatus.DESTROYED),
        EnvironmentStatus.FAILED to setOf(EnvironmentStatus.DESTROYED),
        EnvironmentStatus.DESTROYED to emptySet()
    )

    fun canTransition(from: EnvironmentStatus, to: EnvironmentStatus): Boolean =
        to in LEGAL[from].orEmpty()

    /** Attempt the transition; returns false (state unchanged) if illegal. */
    fun transition(env: EnvironmentInstance, to: EnvironmentStatus): Boolean {
        if (!canTransition(env.status, to)) return false
        env.status = to
        env.lastActivityMs = System.currentTimeMillis()
        return true
    }

    fun isTerminal(status: EnvironmentStatus): Boolean = status == EnvironmentStatus.DESTROYED

    fun isPreserved(status: EnvironmentStatus): Boolean = status == EnvironmentStatus.PRESERVED

    fun isLive(status: EnvironmentStatus): Boolean =
        status == EnvironmentStatus.READY || status == EnvironmentStatus.RUNNING
}
