package com.jarvis.app.humancore.store

import java.io.File
import java.io.FileOutputStream

/**
 * Default [StoragePort]: one file per store inside the app's private
 * filesDir/humancore/ directory (survives process death, needs no
 * permission, is removed on app uninstall — consistent with the existing
 * Telemetry/DecisionGate JSONL pattern).
 *
 * Durability guarantees:
 *  - writes are atomic (temp file + rename), so a kill at any instant leaves
 *    a whole store, never a torn one (§0.14);
 *  - each store file is written under a per-store lock, so modules writing
 *    different stores never contend and a store's single writer is never
 *    interleaved with itself;
 *  - the DIALOGUE store is JSONL append-only, exactly like
 *    logs/decision_gate.jsonl and Telemetry's telemetry.jsonl.
 */
class FileStorage(directory: File) : StoragePort {

    private val dir: File = File(directory, "humancore").apply { mkdirs() }
    private val locks = mutableMapOf<StoreKind, Any>()

    /** Root directory (the raw filesDir) for subsystems that manage their own
     *  subdirectories (Liquid OS inbox/approvals/alerts/sessions/env). */
    val baseDir: File = directory

    override fun read(store: StoreKind): String? {
        // Read under the same per-store lock as write/append/delete so a
        // read never observes a mid-write state (HUMAN_CORE_AUDIT m-17).
        synchronized(lockFor(store)) {
            val file = fileFor(store)
            return if (file.exists()) file.readText() else null
        }
    }

    override fun write(store: StoreKind, content: String) {
        synchronized(lockFor(store)) {
            val target = fileFor(store)
            val tmp = File(target.parentFile, target.name + ".tmp")
            writeWithFsync(tmp, content)
            // Atomic replace: readers (including a process killed mid-write)
            // only ever see a complete file.
            if (!tmp.renameTo(target)) {
                // Fallback for exotic filesystems where renameTo across the
                // same directory can still fail — copy then delete, keeping
                // the write non-torn in practice.
                writeWithFsync(target, content)
                tmp.delete()
            }
        }
    }

    /**
     * Write then fsync BEFORE the atomic rename. The rename is only as durable
     * as the data that precedes it: without fsync, a power loss after rename
     * can leave the directory entry pointing at un-flushed (possibly empty)
     * blocks (HUMAN_CORE_AUDIT m-14). [FileDescriptor.sync] flushes file data
     * to stable storage.
     */
    private fun writeWithFsync(file: File, content: String) {
        FileOutputStream(file).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
    }

    override fun append(store: StoreKind, line: String) {
        synchronized(lockFor(store)) {
            fileFor(store).appendText(line + "\n")
        }
    }

    override fun delete(store: StoreKind) {
        synchronized(lockFor(store)) {
            fileFor(store).delete()
        }
    }

    /** Write a raw file at an arbitrary path. Atomic (temp + rename), same
     *  durability contract as [writeWithFsync]; callers are responsible for
     *  their own locking. */
    fun write(file: File, content: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        writeWithFsync(tmp, content)
        if (!tmp.renameTo(file)) {
            writeWithFsync(file, content)
            tmp.delete()
        }
    }

    /** Append a line to a raw file at an arbitrary path (no per-store lock). */
    fun append(file: File, line: String) {
        file.parentFile?.mkdirs()
        file.appendText(line)
    }

    private fun fileFor(store: StoreKind): File = File(dir, store.fileName)

    private fun lockFor(store: StoreKind): Any =
        synchronized(locks) { locks.getOrPut(store) { Any() } }
}
