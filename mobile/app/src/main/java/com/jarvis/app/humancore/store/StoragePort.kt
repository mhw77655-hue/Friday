package com.jarvis.app.humancore.store

/**
 * The persistence contract the Human Core depends on.
 *
 * Per §0.4 the Human Core defines *what* must be persisted and *the rules*
 * for conflicts — it never owns the storage engine. This port is that
 * boundary. The default implementation is file-backed ([FileStorage]); the
 * existing Turso/libSQL backend can be migrated in behind the same port once
 * it exists (§21.3) without touching any store or module.
 *
 * All writes must survive Android process death at any moment (§0.14), which
 * is the contract [StoragePort] implementations are responsible for — for
 * the file implementation that means atomic replace, never in-place edit.
 */
interface StoragePort {

    /** Raw persisted content for a store, or null if nothing is stored yet. */
    fun read(store: StoreKind): String?

    /**
     * Atomically persist the full content of a single-document store.
     * A crash at any instant must leave either the previous or the new
     * content on disk, never a torn write.
     */
    fun write(store: StoreKind, content: String)

    /** Append one line to an append-only log store (must be safe to call concurrently). */
    fun append(store: StoreKind, line: String)

    /** Remove a store entirely — used by explicit user-requested erasure (§9). */
    fun delete(store: StoreKind)
}
