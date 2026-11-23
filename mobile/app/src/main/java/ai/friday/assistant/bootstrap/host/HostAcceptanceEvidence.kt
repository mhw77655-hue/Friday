package ai.friday.assistant.bootstrap.host

import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts observed first-launch probes into an honest on-device acceptance
 * record. This is deliberately a foundation probe, never a declaration that
 * the full Objective 1 Builder has been accepted.
 */
object HostAcceptanceEvidence {
    const val FOUNDATION_READY = "ON_DEVICE_FOUNDATION_READY"
    const val FOUNDATION_BLOCKED = "ON_DEVICE_FOUNDATION_BLOCKED"
    const val ACCEPTANCE_SCOPE = "FOUNDATION_PROBES_ONLY_NOT_FULL_OBJECTIVE_ACCEPTANCE"

    data class Checks(
        val workspaceDirectoriesReady: Boolean,
        val workspaceReadWriteVerified: Boolean,
        val hostProfileObserved: Boolean,
        val resourcePolicyBounded: Boolean,
        val localInferenceAdmissionHonest: Boolean,
        val canonicalPlanBundled: Boolean,
        val appIdentityPresent: Boolean
    )

    fun evaluate(checks: Checks, sourceRevision: String): JSONObject {
        val values = linkedMapOf(
            "workspace_directories_ready" to checks.workspaceDirectoriesReady,
            "workspace_read_write_verified" to checks.workspaceReadWriteVerified,
            "host_profile_observed" to checks.hostProfileObserved,
            "resource_policy_bounded" to checks.resourcePolicyBounded,
            "local_inference_admission_honest" to checks.localInferenceAdmissionHonest,
            "canonical_plan_bundled" to checks.canonicalPlanBundled,
            "app_identity_present" to checks.appIdentityPresent
        )
        val results = JSONArray()
        values.forEach { (name, passed) ->
            results.put(JSONObject()
                .put("probe", name)
                .put("status", if (passed) "PASS" else "FAIL"))
        }
        val failed = values.filterValues { !it }.keys
        return JSONObject()
            .put("schema_version", 1)
            .put("scope", ACCEPTANCE_SCOPE)
            .put("status", if (failed.isEmpty()) FOUNDATION_READY else FOUNDATION_BLOCKED)
            .put("source_revision", sourceRevision)
            .put("passed_count", values.size - failed.size)
            .put("failed_count", failed.size)
            .put("probes", results)
            .put("failed_probes", JSONArray(failed.toList()))
            .put("explicitly_not_proven", JSONArray()
                .put("physical-host lifecycle fault injection")
                .put("force-stop and reboot resume")
                .put("first autonomous Builder development cycle")
                .put("full multi-provider AI workforce")
                .put("production source-change promotion and rollback"))
    }
}
