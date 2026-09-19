package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.store.IdentityRecord
import com.jarvis.app.humancore.store.IdentityStore

/**
 * The Identity Kernel (§1): the stable layer that changes almost never.
 *
 * It is a READ-ONLY façade over the Identity store plus the behavioral rules
 * that make identity "immutable in practice": the fallback baseline (§1) and
 * the structural rule that no conversational path can write it (§0.13). This
 * class deliberately exposes no write path — the only writer in the whole
 * subsystem is [IdentityRevisionHandler], and it is invoked exclusively
 * through the developer/admin revision entry point.
 */
class IdentityKernel(private val identity: IdentityStore) {

    fun read(): IdentityRecord = identity.read()

    fun name(): String = identity.read().name

    fun selfDescription(): String = identity.read().selfDescription

    /**
     * The admin-authored boundary statements (§3). Stored, validated, and
     * revision-gated, but NOT consumed for enforcement: the Consistency Guard
     * enforces authenticity via fixed detectors mapped to value keys, so these
     * free-text statements are informational until a classifier-based check
     * exists (HUMAN_CORE_AUDIT M-3).
     */
    fun boundaries(): List<String> = identity.read().boundaries

    fun isFallback(): Boolean = identity.isFallback()

    /**
     * Identity is versioned so the rest of the subsystem can detect (and
     * re-baseline against) a revision. Consumers that cache identity-derived
     * expectations must key them on this version (§3 Synchronization).
     */
    fun version(): Int = identity.currentVersion()
}
