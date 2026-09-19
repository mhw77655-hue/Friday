package com.jarvis.app.env

import com.jarvis.app.humancore.store.FileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Environment repository durability: fresh install loads the built-ins,
 * saving a custom profile persists across a "process restart" (new repository
 * on the same storage), and the active-profile selection survives too.
 */
class EnvironmentRepositoryTest {

    private fun tempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "env-test-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun repo(dir: File): EnvironmentRepository = EnvironmentRepository(FileStorage(dir))

    @Test
    fun `fresh install loads the nine built-in profiles`() = runBlocking {
        val r = repo(tempDir())
        val profiles = r.loadProfiles()
        assertEquals(9, profiles.size)
        assertTrue(profiles.all { it.isBuiltIn })
    }

    @Test
    fun `custom profile survives a simulated restart`() = runBlocking {
        val dir = tempDir()
        val now = System.currentTimeMillis()

        val r1 = repo(dir)
        val custom = EnvironmentProfile(
            id = "custom-test",
            label = "Custom Test",
            provider = ModelProviderType.OLLAMA,
            modelSource = ModelSource.Local("127.0.0.1:11434"),
            memoryPolicy = MemoryPolicy.SESSION_ONLY,
            permissionPolicy = PermissionPolicy.RESTRICTED,
            toolsAvailable = ToolCapability.LIMITED,
            syncBehavior = SyncBehavior.OFFLINE_FIRST,
            latencyPreference = LatencyPreference.BALANCED,
            offlineBehavior = OfflineBehavior.DEGRADE,
            uiBehavior = UIBehavior.NORMAL,
            safetyRestrictions = SafetyRestrictions.NONE,
            isBuiltIn = false,
            createdAt = now,
            updatedAt = now
        )
        r1.saveProfiles(listOf(custom))

        // Simulated process death: a brand-new repository on the same storage.
        val r2 = repo(dir)
        val restored = r2.loadProfiles()
        val back = restored.first { it.id == "custom-test" }
        assertEquals("Custom Test", back.label)
        assertEquals(ModelProviderType.OLLAMA, back.provider)
        assertFalse(back.isBuiltIn)
        assertEquals(MemoryPolicy.SESSION_ONLY, back.memoryPolicy)
        // Built-ins are always merged back in.
        assertEquals(10, restored.size)
    }

    @Test
    fun `active profile id survives a simulated restart`() = runBlocking {
        val dir = tempDir()
        repo(dir).saveActiveProfileId("safe")
        val restored = repo(dir).loadActiveProfileId()
        assertEquals("safe", restored)
    }

    @Test
    fun `corrupted profiles file falls back to built-ins`() = runBlocking {
        val dir = tempDir()
        // Write garbage where environments.json should be.
        FileStorage(dir).write(FileStorage(dir).baseDir.resolve("envs/environments.json"), "not json at all")
        val profiles = repo(dir).loadProfiles()
        // Corrupted custom list is dropped; built-ins remain usable.
        assertEquals(9, profiles.size)
        assertTrue(profiles.all { it.isBuiltIn })
    }

    @Test
    fun `saving an empty custom list removes nothing from the load`() = runBlocking {
        val r = repo(tempDir())
        r.saveProfiles(emptyList())
        val profiles = r.loadProfiles()
        assertEquals(9, profiles.size)
    }
}
