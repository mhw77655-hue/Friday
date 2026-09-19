package com.jarvis.app.research.universal

/**
 * In-memory implementation of [MechanismsStore] for JVM unit tests.
 * Thread-safe via synchronized; no network, no Android runtime required.
 */
class InMemoryMechanismsStore : MechanismsStore {

    private val entries = mutableMapOf<String, MechanismCandidate>()

    override fun write(candidate: MechanismCandidate): Boolean {
        entries[candidate.id] = candidate
        return true
    }

    override fun readAll(): List<MechanismCandidate> = entries.values.toList()

    override fun readById(id: String): MechanismCandidate? = entries[id]

    override fun readWhere(predicate: (MechanismCandidate) -> Boolean): List<MechanismCandidate> =
        entries.values.filter(predicate)

    override fun count(): Int = entries.size
}
