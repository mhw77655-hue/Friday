package com.jarvis.app.onefriday.floor

import java.io.File
import java.security.MessageDigest

/**
 * ONE-5-IDENTITY-SYNC-FLOOR: Floor manifest with content hashes for floor files.
 * Tripwire - fails if any hash changes without manifest update.
 */
class FloorManifest(private val manifestFile: File) {
    data class Entry(
        val path: String,
        val hash: String
    )
    
    fun listFloorFiles(): List<String> = listOf(
        "mobile/app/src/main/java/com/jarvis/app/approval",
        "mobile/app/src/main/java/com/jarvis/app/identity/owner",
        "mobile/app/src/main/java/com/jarvis/app/cognition/workspace/ContinuityLaw.kt",
        "mobile/app/src/main/java/com/jarvis/app/security" // protected paths concept
    )
    
    fun computeHash(file: File): String {
        if (!file.exists()) return ""
        val bytes = file.readBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    fun load(): Map<String, String> {
        if (!manifestFile.exists()) return emptyMap()
        val map = mutableMapOf<String, String>()
        manifestFile.readLines().forEach { line ->
            if (line.contains("=")) {
                val parts = line.split("=", limit = 2)
                map[parts[0].trim()] = parts[1].trim()
            }
        }
        return map
    }
    
    fun save(entries: Map<String, String>) {
        manifestFile.parentFile?.mkdirs()
        val content = entries.entries.joinToString("\n") { "${it.key}=${it.value}" }
        manifestFile.writeText(content)
    }
}
