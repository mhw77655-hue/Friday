package com.jarvis.app.context

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Compact, versioned project knowledge shared with whichever model the owner
 * chooses. It is a curated snapshot, not a mirror of private app storage.
 */
data class ProjectContextPack(
    val schemaVersion: Int = 1,
    val projectId: String,
    val ownerVision: String,
    val identityRules: String,
    val architecture: String,
    val verifiedFacts: String,
    val activeObjectives: String,
    val knownFailures: String,
    val sourceRevision: String,
    val updatedAtMs: Long
) {
    fun renderFor(task: String, maxChars: Int = 12_000): String {
        require(maxChars in 512..24_000)
        val taskTerms = task.lowercase().split(Regex("[^a-z0-9_]+"))
            .filter { it.length >= 4 }.toSet()
        val sections = listOf(
            "OWNER VISION" to ownerVision,
            "IDENTITY AND CONTINUITY RULES" to identityRules,
            "ARCHITECTURE" to architecture,
            "VERIFIED REPOSITORY FACTS" to verifiedFacts,
            "ACTIVE OBJECTIVES" to activeObjectives,
            "KNOWN FAILURES" to knownFailures
        ).filter { it.second.isNotBlank() }
        val ranked = sections.mapIndexed { index, section ->
            val haystack = section.second.lowercase()
            val score = taskTerms.count { it in haystack }
            Triple(index, section, score)
        }.sortedWith(compareByDescending<Triple<Int, Pair<String, String>, Int>> { it.third }.thenBy { it.first })
        val header = "PROJECT CONTEXT PACK v$schemaVersion | id=$projectId | revision=$sourceRevision\n" +
            "This is owner-curated context. Treat verified facts as evidence; mark unknowns explicitly. " +
            "Do not overwrite Friday's identity or promote model suggestions to memory without verification.\n"
        val out = StringBuilder(header)
        for ((_, section, _) in ranked) {
            val block = "\n## ${section.first}\n${section.second.trim()}\n"
            if (out.length + block.length > maxChars) {
                val remaining = maxChars - out.length
                if (remaining > 160) out.append(block.take(remaining))
                break
            }
            out.append(block)
        }
        return out.toString()
    }

    fun toJson(): JSONObject = JSONObject()
        .put("schemaVersion", schemaVersion).put("projectId", projectId)
        .put("ownerVision", ownerVision).put("identityRules", identityRules)
        .put("architecture", architecture).put("verifiedFacts", verifiedFacts)
        .put("activeObjectives", activeObjectives).put("knownFailures", knownFailures)
        .put("sourceRevision", sourceRevision).put("updatedAtMs", updatedAtMs)

    companion object {
        fun fromJson(o: JSONObject) = ProjectContextPack(
            schemaVersion = o.optInt("schemaVersion", 1),
            projectId = o.getString("projectId"),
            ownerVision = o.optString("ownerVision"),
            identityRules = o.optString("identityRules"),
            architecture = o.optString("architecture"),
            verifiedFacts = o.optString("verifiedFacts"),
            activeObjectives = o.optString("activeObjectives"),
            knownFailures = o.optString("knownFailures"),
            sourceRevision = o.optString("sourceRevision"),
            updatedAtMs = o.optLong("updatedAtMs")
        )
    }
}

/** Atomic app-private persistence with a content hash for freshness checks. */
class ProjectContextPackStore(private val file: File) {
    @Synchronized
    fun write(pack: ProjectContextPack) {
        file.parentFile?.mkdirs()
        val bytes = pack.toJson().toString().toByteArray(Charsets.UTF_8)
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(bytes)
        runCatching {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Synchronized
    fun read(): ProjectContextPack? = runCatching {
        if (!file.isFile) return null
        ProjectContextPack.fromJson(JSONObject(file.readText(Charsets.UTF_8)))
    }.getOrNull()

    fun contentHash(): String? = if (!file.isFile) null else
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
}
