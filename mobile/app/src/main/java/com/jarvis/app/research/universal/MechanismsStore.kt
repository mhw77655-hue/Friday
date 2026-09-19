package com.jarvis.app.research.universal

/**
 * Persistence interface for mechanism candidates — the "mechanisms table"
 * of the research pipeline. Implementations store and retrieve candidates;
 * the production store may back onto android.database.sqlite or HF Space DB.
 */
interface MechanismsStore {
    /** Write a candidate into the store. Returns true on success. */
    fun write(candidate: MechanismCandidate): Boolean

    /** Read all candidates currently in the store. */
    fun readAll(): List<MechanismCandidate>

    /** Read a single candidate by id, or null if not found. */
    fun readById(id: String): MechanismCandidate?

    /** Read candidates matching a predicate. */
    fun readWhere(predicate: (MechanismCandidate) -> Boolean): List<MechanismCandidate>

    /** Number of candidates in the store. */
    fun count(): Int
}
