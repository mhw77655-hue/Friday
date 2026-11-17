package com.jarvis.app.cognitive.seed

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
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

    private fun loadOrCreateIdentity(file: File): Identity {
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

        directory.mkdirs()
        val created = Identity(
            id = UUID.randomUUID().toString(),
            name = "Friday",
            createdAtMs = nowMs().coerceAtLeast(1L),
            morphologySeed = SecureRandom().nextLong()
        )
        val temporary = File(directory, "identity.bin.tmp")
        DataOutputStream(FileOutputStream(temporary, false)).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeUTF(created.id)
            output.writeUTF(created.name)
            output.writeLong(created.createdAtMs)
            output.writeLong(created.morphologySeed)
            output.flush()
        }
        if (!temporary.renameTo(file)) {
            temporary.delete()
            if (file.exists()) return loadOrCreateIdentity(file)
            throw IllegalStateException("Could not commit Friday's initial local identity record")
        }
        return created
    }

    companion object {
        private const val MAGIC = 0x46524944 // FRID
        private const val VERSION = 1
    }
}
