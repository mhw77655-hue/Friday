package com.jarvis.app.humancore.store

/**
 * The sync boundary (§21.2, §21.3) — how a Human Core state travels between
 * devices.
 *
 * The transport (Turso/libSQL, per §21.3) does not exist in this codebase
 * yet; this port defines the contract a transport will implement, and the
 * conflict resolution for that transport is REAL, tested logic (see
 * [com.jarvis.app.humancore.algo.ConflictResolver]) — not a stub. The
 * interface is deliberately content-shaped: it moves store documents, never
 * internal objects, so local and remote can be on different builds safely.
 *
 * Why the Human Core needs its own sync: personality, trust, and bond live
 * ONLY in the local Human Core (§0.4, §0.6). The backend may synchronize
 * and back them up, but the local device remains the authority on its own
 * identity and relationship. Conflict resolution therefore defaults to
 * "local wins ties" and never lets a remote identity override a newer local
 * one.
 */
interface SyncPort {

    /** Export all five stores as raw documents, keyed by store kind. */
    fun export(): Map<StoreKind, String>

    /**
     * Import remote documents, resolving conflicts per store semantics
     * (§21.2). Returns the stores that actually changed locally.
     */
    fun import(remote: Map<StoreKind, String>): List<StoreKind>
}

/**
 * Default transport: no transport. The port is unplugged until a real one
 * exists (§21.3 migration). Import is a no-op; export returns what's on
 * disk. This is honest architecture — the Human Core must still run, test,
 * and be reviewed with the sync boundary fully defined but the wire absent.
 */
class NullSyncPort(
    private val storage: StoragePort,
    private val registry: StoreRegistry
) : SyncPort {

    override fun export(): Map<StoreKind, String> {
        registry.checkpoint()
        val out = mutableMapOf<StoreKind, String>()
        StoreKind.values().forEach { kind ->
            storage.read(kind)?.let { out[kind] = it }
        }
        return out
    }

    override fun import(remote: Map<StoreKind, String>): List<StoreKind> {
        val changed = mutableListOf<StoreKind>()
        StoreKind.values().forEach { kind ->
            val local = storage.read(kind)
            val r = remote[kind]
            if (r == null) return@forEach
            val merged = com.jarvis.app.humancore.algo.ConflictResolver.resolve(kind, local, r)
            if (merged != null && merged != local) {
                storage.write(kind, merged)
                changed += kind
            }
        }
        if (changed.isNotEmpty()) registry.reload()
        return changed
    }
}
