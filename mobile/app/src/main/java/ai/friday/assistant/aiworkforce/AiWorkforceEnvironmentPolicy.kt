package ai.friday.assistant.aiworkforce

import ai.friday.assistant.env.EnvironmentProfile
import ai.friday.assistant.env.OfflineBehavior
import ai.friday.assistant.env.SafetyRestrictions

/**
 * Keeps external subscription-worker use subordinate to Friday's existing
 * environment policy. Profiles with cloud-only, remote-model, read-only, or
 * maximum restrictions do not silently disclose objective context to another
 * provider. The cloud-assisted profile retains its per-request consent rule.
 */
object AiWorkforceEnvironmentPolicy {
    fun allowsExternalWorkers(profile: EnvironmentProfile?): Boolean {
        if (profile == null) return false
        if (profile.offlineBehavior == OfflineBehavior.READ_ONLY) return false
        return profile.safetyRestrictions == SafetyRestrictions.NONE
    }
}
