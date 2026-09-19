package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.store.RelationshipStore

/**
 * The Long-Term Bond Formation Tracker (§9b) — the ONLY writer of bond depth
 * and its milestones.
 *
 * Bond depth is a saturating growth curve over time spent together — fast
 * early growth that plateaus (§9b), NOT unbounded linear growth. It grows at
 * SESSION boundaries, never per-message (§9b Lifecycle), and a long gap
 * applies a one-time, bounded, partial decay followed by a reconnection
 * milestone rather than treating absence as total loss (§9b).
 *
 * Milestones are monotonic flags (they generally persist once set) and feed
 * the self-reflection module's sense of the relationship's history (§9b
 * Inputs/Outputs, §15).
 */
class BondFormation(
    private val relationship: RelationshipStore,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    /**
     * Finalize a session: apply any gap decay, grow bond by the session's
     * interaction count, and record milestones.
     */
    fun onSessionEnd(sessionInteractionCount: Int, now: Long = clock()) {
        val rel = relationship.read()
        val last = rel.depth.lastSessionEpochMs
        var decayApplied = false
        // "Long absence" threshold — single source of truth (RelationshipStore,
        // HUMAN_CORE_AUDIT m-18).
        if (last != null && (now - last) > RelationshipStore.LONG_GAP_THRESHOLD_MS) {
            relationship.applyGapDecay(now)
            relationship.setMilestone(RelationshipStore.BOND_MILESTONE_RECONNECTION)
            decayApplied = true
            bus.publish(
                HcEvent.BondUpdated(
                    ts = now, reason = "reconnection after long gap",
                    depth = relationship.read().depth.depth,
                    milestones = relationship.read().depth.milestones
                )
            )
        }

        // Growth resumes on the NEXT session: a reconnection boundary marks
        // the absence (and its partial decay) without immediately undoing it
        // in the same session — rebuilding is earned by further contact (§9b).
        if (sessionInteractionCount > 0 && !decayApplied) {
            relationship.growBond(sessionInteractionCount, now)
            updateMilestones(now)
        }
    }

    /** Cold-start the bond timeline when a relationship is brand new. */
    fun ensureStarted(now: Long = clock()) {
        relationship.resetRelationship(now)
    }

    private fun updateMilestones(now: Long) {
        val depth = relationship.read().depth.depth
        val changed: String? = when {
            depth >= 0.8 -> "deep_bond"
            depth >= 0.5 -> "established_rapport"
            depth >= 0.2 -> "first_rapport"
            else -> null
        }
        changed?.let { relationship.setMilestone(it) }
        bus.publish(
            HcEvent.BondUpdated(
                ts = now,
                reason = "bond depth ${"%.2f".format(depth)}",
                depth = depth,
                milestones = relationship.read().depth.milestones
            )
        )
    }
}
