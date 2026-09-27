package com.jarvis.app.cognition.workspace

/**
 * CONTINUITY-LAW: the record of what an ACCEPTED slow change changed.
 *
 * A change that rewrites durable identity-adjacent state is only defensible if
 * the old value, the new value, the evidence it was accepted on and the instant
 * it was accepted are all still answerable afterwards. So an accepted
 * [ChangeLayer.SLOW] change appends one [ChangeLogEntry] here, and the log is
 * QUERIED, not just printed: [entriesFor] (the full history of one field),
 * [latestValue] (what a field is now) and [lastChangeAt] (when it last moved,
 * which is what the rate limit itself reads) are part of the interface, so the
 * gate depends on the record rather than on an in-memory counter that a restart
 * would forget.
 *
 * The interface is storage-agnostic; [FileChangeLog] is the durable local
 * implementation and the one the production composition wires.
 */
data class ChangeLogEntry(
    /** Unique id of this entry. */
    val id: String,
    /** The field that changed, e.g. "persona:directness". */
    val target: String,
    /** The layer the change was accepted under. Only SLOW changes are logged. */
    val layer: ChangeLayer,
    /** The value the field held before, or null when the field was unset. */
    val oldValue: String?,
    /** The value the field holds now. */
    val newValue: String,
    /**
     * Ids of the REAL workspace claims the change was accepted on — the
     * accumulated evidence, not a restatement of it.
     */
    val evidenceClaimIds: List<String>,
    /**
     * Id of the claim that now HOLDS the accepted value. It is the last of
     * [evidenceClaimIds] (the turn whose proposal was accepted), recorded
     * separately so "what does this field hold, and which claim says so?" is one
     * query instead of a guess at claim ordering.
     */
    val acceptedClaimId: String,
    /** Real instant the change was accepted, in millis. */
    val timestamp: Long
) {
    init {
        require(id.isNotBlank()) { "change-log entry id must be non-blank" }
        require(target.isNotBlank()) { "change-log entry must name the field it changed" }
        require(newValue.isNotBlank()) { "change-log entry for '$target' must record a new value" }
        require(timestamp >= 0L) { "change-log entry for '$target' must carry a real instant" }
        require(evidenceClaimIds.isNotEmpty()) {
            "accepted change to '$target' must record the evidence claims it was accepted on"
        }
        require(evidenceClaimIds.none { it.isBlank() }) {
            "change-log entry for '$target' has a blank evidence claim id"
        }
        require(acceptedClaimId in evidenceClaimIds) {
            "the claim holding '$target' must be one of the evidence claims it was accepted on"
        }
    }
}

/**
 * A durable, queryable history of accepted slow changes. Implementations must
 * survive a restart: a new instance over the same storage has to answer
 * [entries], [entriesFor] and [lastChangeAt] identically.
 */
interface ChangeLog {
    /** Append one accepted-change entry. */
    fun append(entry: ChangeLogEntry)

    /** Every entry, oldest first. */
    fun entries(): List<ChangeLogEntry>

    /** The full accepted-change history of one field, oldest first. */
    fun entriesFor(target: String): List<ChangeLogEntry>

    /** The most recent accepted-change instant for [target], or null if it never changed. */
    fun lastChangeAt(target: String): Long?

    /** The value [target] holds according to the log, or null if it never changed. */
    fun latestValue(target: String): String?
}
