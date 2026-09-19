package com.jarvis.app.env

import com.jarvis.app.humancore.store.FileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists environment profiles to filesDir/envs/environments.json
 * Uses atomic write (temp file + rename) via FileStorage pattern.
 */
class EnvironmentRepository(
    private val fileStorage: FileStorage
) {
    private val envDir = fileStorage.baseDir.resolve("envs")
    private val profilesFile: File = envDir.resolve("environments.json")
    private val activeFile: File = envDir.resolve("active.json")

    init {
        envDir.mkdirs()
    }

    /** Load all saved profiles (built-in + custom) */
    suspend fun loadProfiles(): List<EnvironmentProfile> = withContext(Dispatchers.IO) {
        if (!profilesFile.exists()) {
            return@withContext EnvironmentProfile.BUILT_IN_PROFILES
        }
        try {
            val jsonStr = profilesFile.readText()
            val custom = EnvironmentProfile.fromJsonList(jsonStr)
            // Merge: built-ins always present, custom override by id
            val builtInMap = EnvironmentProfile.BUILT_IN_PROFILES.associateBy { it.id }
            val customMap = custom.associateBy { it.id }
            val allIds = builtInMap.keys + customMap.keys
            allIds.map { id ->
                customMap[id] ?: builtInMap[id]!!
            }
        } catch (e: Exception) {
            // Corrupted/partial file: fall back to the built-ins rather than
            // crashing boot. The next save rebuilds the file from live state.
            EnvironmentProfile.BUILT_IN_PROFILES
        }
    }

    /** Save custom profiles (built-ins are not persisted) */
    suspend fun saveProfiles(profiles: List<EnvironmentProfile>) = withContext(Dispatchers.IO) {
        val custom = profiles.filter { !it.isBuiltIn }
        val jsonStr = EnvironmentProfile.toJsonList(custom)
        fileStorage.write(profilesFile, jsonStr)
    }

    /** Save active profile ID */
    suspend fun saveActiveProfileId(profileId: String) = withContext(Dispatchers.IO) {
        fileStorage.write(activeFile, profileId)
    }

    /** Load active profile ID */
    suspend fun loadActiveProfileId(): String? = withContext(Dispatchers.IO) {
        if (!activeFile.exists()) return@withContext null
        activeFile.readText().trim().takeIf { it.isNotBlank() }
    }
}
