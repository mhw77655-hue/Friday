package com.jarvis.app.humancore.store

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

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
    // Shared across FileStorage instances: several subsystems construct their
    // own adapter for the same filesDir, so per-instance locks are insufficient.

    /** Root directory (the raw filesDir) for subsystems that manage their own
     *  subdirectories (Liquid OS inbox/approvals/alerts/sessions/env). */
    val baseDir: File = directory

    override fun read(store: StoreKind): String? {
        // Read under the same per-store lock as write/append/delete so a
        // read never observes a mid-write state (HUMAN_CORE_AUDIT m-17).
        val file = fileFor(store)
        synchronized(lockFor(file)) {
            return if (file.exists()) file.readText() else null
        }
    }

    override fun write(store: StoreKind, content: String) {
        val target = fileFor(store)
        synchronized(lockFor(target)) {
            val tmp = File(target.parentFile, target.name + ".tmp")
            writeWithFsync(tmp, content)
            atomicReplace(tmp, target)
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
        val file = fileFor(store)
        synchronized(lockFor(file)) {
            file.appendText(line + "\n")
        }
    }

    override fun delete(store: StoreKind) {
        val file = fileFor(store)
        synchronized(lockFor(file)) {
            file.delete()
        }
    }

    /** Write a raw file at an arbitrary path. Atomic (temp + rename), same
     *  durability contract as [writeWithFsync]; callers are responsible for
     *  their own locking. */
    fun write(file: File, content: String) {
        file.parentFile?.mkdirs()
        synchronized(lockFor(file)) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            writeWithFsync(tmp, content)
            atomicReplace(tmp, file)
        }
    }

    /** Append a line to a raw file at an arbitrary path (no per-store lock). */
    fun append(file: File, line: String) {
        file.parentFile?.mkdirs()
        synchronized(lockFor(file)) {
            file.appendText(line)
        }
    }

    private fun atomicReplace(tmp: File, target: File) {
        try {
            // Same-directory atomic rename. If the filesystem cannot provide
            // this guarantee, fail while preserving the previous target rather
            // than falling back to a truncating, crash-vulnerable copy.
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (failure: AtomicMoveNotSupportedException) {
            tmp.delete()
            throw IOException("Atomic replacement is not supported for ${target.name}.", failure)
        } catch (failure: IOException) {
            tmp.delete()
            throw failure
        }
    }

    private fun fileFor(store: StoreKind): File = File(dir, store.fileName)

    private fun lockFor(file: File): Any {
        val key = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        return pathLocks.computeIfAbsent(key) { Any() }
    }

    companion object {
        private val pathLocks = ConcurrentHashMap<String, Any>()
    }
}
