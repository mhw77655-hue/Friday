package com.jarvis.app.env

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Environment profile modeling: the 9 built-in environments exist with the
 * documented provider/policy wiring, JSON round-trips losslessly, and a
 * corrupted profile degrades to safe defaults instead of crashing.
 */
class EnvironmentProfileTest {

    @Test
    fun `there are nine built-in environments covering the documented modes`() {
        assertEquals(9, EnvironmentProfile.BUILT_IN_PROFILES.size)
        val ids = EnvironmentProfile.BUILT_IN_PROFILES.map { it.id }
        // The modes the milestone names explicitly must be present.
        assertTrue("offline environment missing", ids.contains("offline-persona"))
        assertTrue("safe environment missing", ids.contains("safe"))
        assertTrue("local-on-device missing", ids.contains("local-on-device"))
        assertTrue("remote-model missing", ids.contains("remote-model"))
        assertTrue("everything is built-in and immutable", EnvironmentProfile.BUILT_IN_PROFILES.all { it.isBuiltIn })
    }

    @Test
    fun `offline environment runs on heuristic with no model source`() {
        val offline = EnvironmentProfile.BUILT_IN_PROFILES.first { it.id == "offline-persona" }
        assertEquals(ModelProviderType.HEURISTIC, offline.provider)
        assertTrue(offline.modelSource is ModelSource.None)
        assertEquals(OfflineBehavior.READ_ONLY, offline.offlineBehavior)
    }

    @Test
    fun `safe environment restricts permissions and tools`() {
        val safe = EnvironmentProfile.BUILT_IN_PROFILES.first { it.id == "safe" }
        assertEquals(PermissionPolicy.SAFE_MODE, safe.permissionPolicy)
        assertTrue(safe.toolsAvailable.isEmpty())
        assertEquals(OfflineBehavior.READ_ONLY, safe.offlineBehavior)
        assertEquals(SafetyRestrictions.MAX, safe.safetyRestrictions)
    }

    @Test
    fun `profile json round-trips without loss`() {
        EnvironmentProfile.BUILT_IN_PROFILES.forEach { profile ->
            val restored = EnvironmentProfile.fromJson(profile.toJson())
            assertEquals("id", profile.id, restored.id)
            assertEquals("label", profile.label, restored.label)
            assertEquals("provider", profile.provider, restored.provider)
            assertEquals("modelSource", profile.modelSource, restored.modelSource)
            assertEquals("memoryPolicy", profile.memoryPolicy, restored.memoryPolicy)
            assertEquals("permissionPolicy", profile.permissionPolicy, restored.permissionPolicy)
            assertEquals("toolsAvailable", profile.toolsAvailable, restored.toolsAvailable)
            assertEquals("syncBehavior", profile.syncBehavior, restored.syncBehavior)
            assertEquals("latencyPreference", profile.latencyPreference, restored.latencyPreference)
            assertEquals("offlineBehavior", profile.offlineBehavior, restored.offlineBehavior)
            assertEquals("uiBehavior", profile.uiBehavior, restored.uiBehavior)
            assertEquals("safetyRestrictions", profile.safetyRestrictions, restored.safetyRestrictions)
            assertEquals("isBuiltIn", profile.isBuiltIn, restored.isBuiltIn)
            assertEquals("createdAt", profile.createdAt, restored.createdAt)
            assertEquals("updatedAt", profile.updatedAt, restored.updatedAt)
        }
    }

    @Test
    fun `invalid provider name falls back to NONE instead of crashing`() {
        val json = """
            {"id":"x","label":"X","provider":"NOT_A_PROVIDER",
             "modelSource":{"type":"local","path":"llama-server:8080"},
             "memoryPolicy":"FULL","permissionPolicy":"FULL",
             "syncBehavior":"REAL_TIME","latencyPreference":"FAST",
             "offlineBehavior":"DEGRADE","uiBehavior":"NORMAL",
             "safetyRestrictions":"NONE","isBuiltIn":false}
        """.trimIndent()
        val profile = EnvironmentProfile.fromJson(org.json.JSONObject(json))
        assertEquals(ModelProviderType.NONE, profile.provider)
        assertFalse(profile.isBuiltIn)
    }
}
