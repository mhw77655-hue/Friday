package ai.friday.assistant.cognitive.seed

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * The app-local root of Friday's first independent individual.
 *
 * This object owns a persistent identity record, the experience substrate and
 * the limited model-free expression path. It accepts no ModelManager, cloud
 * client, network service, or external identity provider.
 */
class FridayIndividual(
    private val directory: File,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    data class Identity(
        val id: String,
        val name: String,
        val createdAtMs: Long,
        /** Stable per-install morphology seed for Friday's non-human renderer. */
        val morphologySeed: Long
    )

    val identity: Identity = loadOrCreateIdentity(File(directory, "identity.bin"))
    val experience: FridaySeedSubstrate = FridaySeedSubstrate(
        journal = File(directory, "experiences.bin"),
        nowMs = nowMs
    )
    val expression: FridayModelFreeMind = FridayModelFreeMind(experience, identity.name)

    fun recordExperience(text: String) = expression.observe(text)

    fun recordObservedOutcome(input: String, outcome: String) =
        expression.observeOutcome(input, outcome)

    fun expressWithoutModel(input: String): String = expression.respond(input)

    private fun loadOrCreateIdentity(file: File): Identity = synchronized(lockFor(file)) {
        directory.mkdirs()
        // JVM monitor prevents OverlappingFileLockException between threads in
        // this process; the OS file lock also serializes first launch across
        // Android processes sharing the same app-private directory.
        RandomAccessFile(File(directory, "identity.lock"), "rw").channel.use { channel ->
            channel.lock().use {
                loadOrCreateIdentityLocked(file)
            }
        }
    }

    private fun loadOrCreateIdentityLocked(file: File): Identity {
        if (file.exists()) {
            try {
                DataInputStream(FileInputStream(file)).use { input ->
                    require(input.readInt() == MAGIC) { "Unknown Friday identity record" }
                    require(input.readInt() == VERSION) { "Unsupported Friday identity version" }
                    val id = input.readUTF()
                    val name = input.readUTF()
                    val createdAt = input.readLong()
                    val morphologySeed = input.readLong()
                    require(id.isNotBlank() && name.isNotBlank() && createdAt > 0L) {
                        "Invalid Friday identity record"
                    }
                    return Identity(id, name, createdAt, morphologySeed)
                }
            } catch (failure: Exception) {
                throw IllegalStateException(
                    "Friday's local identity record is unreadable; refusing to silently create a different identity.",
                    failure
                )
            }
        }

        val created = Identity(
            id = UUID.randomUUID().toString(),
            name = "Friday",
            createdAtMs = nowMs().coerceAtLeast(1L),
            morphologySeed = SecureRandom().nextLong()
        )
        val temporary = File(directory, "identity.bin.${UUID.randomUUID()}.tmp")
        FileOutputStream(temporary, false).use { raw ->
            DataOutputStream(raw).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeUTF(created.id)
                output.writeUTF(created.name)
                output.writeLong(created.createdAtMs)
                output.writeLong(created.morphologySeed)
                output.flush()
                raw.fd.sync()
            }
        }
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (failure: Exception) {
            temporary.delete()
            if (file.exists()) return loadOrCreateIdentityLocked(file)
            throw IllegalStateException("Could not commit Friday's initial local identity record", failure)
        }
        return created
    }

    companion object {
        private val identityLocks = ConcurrentHashMap<String, Any>()
        private fun lockFor(file: File): Any {
            val key = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            return identityLocks.computeIfAbsent(key) { Any() }
        }

        private const val MAGIC = 0x46524944 // FRID
        private const val VERSION = 1
    }
}
