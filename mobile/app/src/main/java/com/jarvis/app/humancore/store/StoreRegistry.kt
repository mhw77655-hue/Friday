package com.jarvis.app.humancore.store

/**
 * The five logical Human Core stores, wired to a storage engine and loaded
 * together (§0.6). Each store remains independently persistable and
 * independently recoverable — this holder only coordinates lifecycle; it
 * never couples their data (§0.17).
 */
class StoreRegistry(
    val storage: StoragePort,
    private val clock: () -> Long
) {
    val identity = IdentityStore(storage, clock)
    val personality = PersonalityStore(storage, clock)
    val mood = MoodStore(storage, clock)
    val relationship = RelationshipStore(storage, clock)
    val adaptation = AdaptationStore(storage, clock)
    val dialogue = DialogueLog(storage, clock)

    /** Load or cold-start every store. Safe to call once per process. */
    fun loadAll() {
        identity.load()
        personality.load()
        mood.load()
        relationship.load()
        adaptation.load()
        dialogue.load()
    }

    /** Re-load every store from disk — used after an external import (§21.2). */
    fun reload() {
        loadAll()
    }

    /** Flush all stores to disk (session boundary, app pause, process death safety §0.14). */
    fun checkpoint() {
        identity.persist()
        personality.persist()
        mood.persist()
        relationship.persist()
        adaptation.persist()
        dialogue.persist()
    }
}
