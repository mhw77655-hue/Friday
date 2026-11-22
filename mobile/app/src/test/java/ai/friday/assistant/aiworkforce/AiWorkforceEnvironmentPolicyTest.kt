package ai.friday.assistant.aiworkforce

import ai.friday.assistant.env.EnvironmentProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkforceEnvironmentPolicyTest {
    @Test fun `allows external workforce in normal local builder and research profiles`() {
        val allowed = setOf("local-on-device", "lan-workspace", "research", "builder")
        EnvironmentProfile.BUILT_IN_PROFILES
            .filter { it.id in allowed }
            .forEach { assertTrue(it.id, AiWorkforceEnvironmentPolicy.allowsExternalWorkers(it)) }
    }

    @Test fun `blocks external workforce in cloud-only remote and restrictive profiles`() {
        val blocked = setOf("remote-model", "cloud-assisted", "offline-persona", "monitor", "safe")
        EnvironmentProfile.BUILT_IN_PROFILES
            .filter { it.id in blocked }
            .forEach { assertFalse(it.id, AiWorkforceEnvironmentPolicy.allowsExternalWorkers(it)) }
    }

    @Test fun `blocks external workforce when active profile is unknown`() {
        assertFalse(AiWorkforceEnvironmentPolicy.allowsExternalWorkers(null))
    }
}
