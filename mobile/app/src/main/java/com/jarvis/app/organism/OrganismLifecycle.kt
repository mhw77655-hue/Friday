package com.jarvis.app.organism

import com.jarvis.app.microsystem.MicroSystemContract

/**
 * Organism lifecycle (§18) — the dormancy contract that keeps the phone a
 * communication body and not a permanently-running autonomous server.
 *
 * Layered over a [MicroSystemContract], it adds the phases the nervous system
 * needs to unload and reload organisms on demand:
 *
 *   DORMANT → INITIALIZING → ACTIVE ⇄ SUSPENDED
 *                 │  │          │      │
 *                 │  └──────────┤      │
 *                 └──→ DEGRADED ┴──────┘  →  DISABLED
 *
 * - ACTIVE holds resources; SUSPENDED releases them but keeps registration
 *   and state; DORMANT is registered-but-unloaded (nothing resident).
 * - No phase transition performs the organism's work — it only brackets
 *   initialize()/shutdown() so the nervous system never reaches inside.
 */
class OrganismLifecycle(private val system: MicroSystemContract) {

    @Volatile private var phase: OrganismPhase = OrganismPhase.DORMANT
    val currentPhase: OrganismPhase get() = phase

    private val legal: Map<OrganismPhase, Set<OrganismPhase>> = mapOf(
        OrganismPhase.DORMANT to setOf(OrganismPhase.INITIALIZING, OrganismPhase.DISABLED),
        OrganismPhase.INITIALIZING to setOf(
            OrganismPhase.ACTIVE, OrganismPhase.SUSPENDED,
            OrganismPhase.DEGRADED, OrganismPhase.DISABLED
        ),
        OrganismPhase.ACTIVE to setOf(
            OrganismPhase.SUSPENDED, OrganismPhase.DEGRADED, OrganismPhase.DISABLED
        ),
        OrganismPhase.SUSPENDED to setOf(
            OrganismPhase.ACTIVE, OrganismPhase.INITIALIZING,
            OrganismPhase.DORMANT, OrganismPhase.DISABLED
        ),
        OrganismPhase.DEGRADED to setOf(
            OrganismPhase.ACTIVE, OrganismPhase.SUSPENDED, OrganismPhase.DISABLED
        ),
        OrganismPhase.DISABLED to emptySet()
    )

    fun canTransition(to: OrganismPhase): Boolean = to in legal[phase].orEmpty()

    /** Load the organism (initialize) and move it to active. */
    suspend fun activate(): Boolean {
        val target = when (phase) {
            OrganismPhase.DORMANT, OrganismPhase.SUSPENDED -> OrganismPhase.INITIALIZING
            else -> OrganismPhase.ACTIVE
        }
        return transition(target) { system.initialize() }.also {
            if (it) transition(OrganismPhase.ACTIVE) {}
        }
    }

    /** Release the organism's resources but keep registration + state (§18). */
    suspend fun suspend(): Boolean =
        transition(OrganismPhase.SUSPENDED) { system.shutdown() }

    /** Terminal: permanently disable the organism. */
    suspend fun disable(): Boolean =
        transition(OrganismPhase.DISABLED) { system.shutdown() }

    fun markDegraded() {
        if (canTransition(OrganismPhase.DEGRADED)) phase = OrganismPhase.DEGRADED
    }

    private suspend fun transition(to: OrganismPhase, action: suspend () -> Unit): Boolean {
        if (!canTransition(to)) return false
        val prev = phase
        phase = to
        return try {
            action()
            true
        } catch (t: Throwable) {
            phase = prev
            false
        }
    }
}

/** Dormancy phases for an organism (§18). */
enum class OrganismPhase {
    /** Registered but not loaded — no resources held. */
    DORMANT,
    INITIALIZING,
    /** Loaded and handling requests. */
    ACTIVE,
    /** Unloaded from memory; registration + state retained. */
    SUSPENDED,
    DEGRADED,
    DISABLED
}
