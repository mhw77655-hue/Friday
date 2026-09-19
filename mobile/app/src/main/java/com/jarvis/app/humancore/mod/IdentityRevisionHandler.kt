package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.store.IdentityRevisionAuth
import com.jarvis.app.humancore.store.IdentityRevisionEntry
import com.jarvis.app.humancore.store.IdentityStore
import com.jarvis.app.humancore.store.ValueRecord

/**
 * The ONLY writer of the Identity and Values stores (§3, §4).
 *
 * Everything else in the subsystem — every module, every pipeline, every
 * parsed user message — reads identity but structurally cannot write it. The
 * guarantee is enforced three ways:
 *
 *  1. This handler is the only code path that holds an
 *     [IdentityRevisionAuth] token (constructed solely by the
 *     developer/admin revision entry point).
 *  2. The message-handling pipeline (Perception/Expression/Integration) never
 *     references this handler — it is only reachable through the explicit
 *     admin entry point on HumanCore.
 *  3. Revisions are version-gated: a revision must be based on the current
 *     version, so overlapping/out-of-order writes cannot clobber history.
 *
 * Requests like "pretend you are X" are therefore handled NOT by writing
 * identity but by the Consistency Guard declining in-character (§0.13,
 * §19). No amount of user phrasing can reach this handler.
 */
class IdentityRevisionHandler(
    private val identity: IdentityStore,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    /**
     * Apply a validated identity revision. Returns false (and writes nothing)
     * if the new record fails structural validation — a revision must never
     * be able to reduce the integrity of the system that guards it.
     *
     * @param trigger     why the revision is happening (e.g. "developer-authorised reset").
     * @param authorizer  the identity of the human authorizing the change.
     */
    fun performRevision(
        newRecord: com.jarvis.app.humancore.store.IdentityRecord,
        trigger: String,
        authorizer: String
    ): Boolean {
        if (!validate(newRecord)) return false
        val auth = IdentityRevisionAuth.authorize(trigger, authorizer)
        val now = clock()
        val entry = IdentityRevisionEntry(
            ts = now,
            trigger = trigger,
            authorizer = authorizer,
            summary = "revision ${newRecord.version}: values ${newRecord.values.size}, boundaries ${newRecord.boundaries.size}"
        )
        try {
            identity.applyRevision(newRecord, entry, auth)
        } catch (e: Exception) {
            // Version mismatch or store failure: the revision is refused and
            // the previous identity is left fully intact.
            return false
        }
        bus.publish(
            HcEvent.IdentityRevised(
                ts = clock(), reason = "identity revision authorized by $authorizer: $trigger",
                newVersion = newRecord.version, authorizer = authorizer
            )
        )
        return true
    }

    /**
     * Structural validation, independent of content taste: a revision must
     * keep the identity non-empty, values non-empty, boundaries non-empty,
     * and values well-formed. Content quality is the authorizer's
     * responsibility — this validates integrity only (§3).
     */
    private fun validate(r: com.jarvis.app.humancore.store.IdentityRecord): Boolean {
        if (r.name.isBlank()) return false
        if (r.selfDescription.isBlank()) return false
        if (r.values.isEmpty()) return false
        if (r.values.any { it.key.isBlank() || it.statement.isBlank() }) return false
        if (r.boundaries.isEmpty()) return false
        return true
    }

    /**
     * Convenience for the developer/admin path: build a record from the
     * current one plus new values, bumping the version. The caller must
     * provide the full new values list (values are replaced wholesale on a
     * revision, never merged — the old version remains in the audit trail).
     */
    fun buildRevisionRecord(
        newName: String = identity.read().name,
        newSelfDescription: String = identity.read().selfDescription,
        newValues: List<ValueRecord> = identity.read().values,
        newBoundaries: List<String> = identity.read().boundaries
    ): com.jarvis.app.humancore.store.IdentityRecord {
        val current = identity.read()
        return current.copy(
            name = newName,
            selfDescription = newSelfDescription,
            values = newValues,
            boundaries = newBoundaries,
            version = current.version + 1,
            lastRevisionEpochMs = clock()
        )
    }
}
