package ai.friday.assistant.bootstrap

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ai.friday.assistant.ShizukuBridge
import ai.friday.assistant.ScreenBridge
import ai.friday.assistant.bootstrap.host.HostBootstrapWorker
import ai.friday.assistant.bootstrap.plan.ObjectiveExecutionWorker
import ai.friday.assistant.github.reporting.FridayAgentTaskRelayConsentStore
import ai.friday.assistant.github.reporting.FridayAgentTaskRelay
import ai.friday.assistant.github.reporting.AgentTaskThreadStore
import ai.friday.assistant.github.reporting.GitHubDevelopmentRepositoryClient
import ai.friday.assistant.github.reporting.FridayAgentTaskRelayPollWorker
import ai.friday.assistant.github.reporting.GitHubCredentialStore
import ai.friday.assistant.github.reporting.GitHubDeviceReportConsentStore
import ai.friday.assistant.github.reporting.GitHubDeviceReportWorker
import ai.friday.assistant.model.storage.ResidentModelBootstrapWorker
import ai.friday.assistant.update.FridayUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Single owner-triggered entry point for Friday's existing maintenance lanes.
 * Composes bounded workers; it does not turn Android into a generic remote shell
 * or claim that queued work has completed. Each lane retains its own durable
 * state, consent checks, signing boundary and verification.
 */
object FridayOneButtonMaintenance {
    data class LaunchReport(
        val queued: List<String>,
        val shizuku: String,
        val github: String,
        val relay: String,
        val deviceReporting: String
    ) {
        fun summary(): String = listOf(
            "Started " + queued.size + " maintenance lane(s): " + queued.joinToString(", ") + ".",
            shizuku, github, relay, deviceReporting,
            "Work is queued, not yet complete. Friday records each lane's actual result separately."
        ).joinToString("\n")
    }

    suspend fun start(context: Context): LaunchReport = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val queued = mutableListOf<String>()

        // Reuse the phone's existing Shizuku grant and typed Friday UserService.
        // Never pop a permission prompt or silently change the owner's grant.
        ShizukuBridge.init()
        ShizukuBridge.refresh()
        val shizuku = when {
            !ShizukuBridge.isAvailable.value -> "Shizuku is not currently reachable; local/cloud lanes can still run."
            !ShizukuBridge.isGranted.value -> "Shizuku is available but Friday's existing authorization is not granted."
            else -> {
                ScreenBridge.bind(app)
                "Shizuku authorization detected; Friday's typed device service is binding (" + ScreenBridge.status.value + ")."
            }
        }

        HostBootstrapWorker.enqueue(app)
        queued += "phone capability survey"
        ResidentModelBootstrapWorker.enqueue(app)
        queued += "resident-model readiness"
        ObjectiveExecutionWorker.enqueue(app)
        queued += "canonical Builder / repair queue"

        val update = OneTimeWorkRequestBuilder<FridayUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE_UPDATE_WORK, ExistingWorkPolicy.KEEP, update)
        queued += "signed release check"

        val githubReady = GitHubCredentialStore(app).read() != null
        val relayConsentStore = FridayAgentTaskRelayConsentStore(app)
        val relayConsent = relayConsentStore.isEnabled()
        val relayStatus = when {
            !githubReady -> "GitHub task dispatch is waiting for the phone's encrypted repository credential."
            !relayConsent -> "Public GitHub task dispatch remains off until its explicit owner consent is enabled."
            else -> {
                val trustedActors = relayConsentStore.trustedActors()
                if (trustedActors.isEmpty()) {
                    "Public task dispatch is enabled, but no trusted reply actor is configured."
                } else {
                    val revision = ai.friday.assistant.BuildConfig.SOURCE_REVISION
                        .takeIf { it.isNotBlank() && it.length <= 80 }
                        ?: ("release-" + ai.friday.assistant.BuildConfig.VERSION_NAME).take(80)
                    val taskId = "friday-maint-" +
                        ai.friday.assistant.BuildConfig.VERSION_CODE + "-" +
                        LocalDate.now().toString().replace("-", "")
                    val relay = FridayAgentTaskRelay(
                        github = GitHubDevelopmentRepositoryClient(GitHubCredentialStore(app).read()!!),
                        threads = AgentTaskThreadStore(app.filesDir),
                        trustedActors = trustedActors,
                        consentEnabled = relayConsentStore::isEnabled
                    )
                    val payload = JSONObject()
                        .put("data_classification", "public")
                        .put("request_type", "friday_maintenance")
                        .put("goal", "Continue the canonical Friday delivery plan. Inspect current main, identify the highest-value incomplete implementation slice, make a bounded source change, add or update tests, and report exact commit and CI evidence. Do not merge, publish a release, weaken security gates, or claim physical-device verification without evidence.")
                        .put("repository", "mhw77655-hue/Friday")
                        .put("source_revision", revision)
                        .put("requested_outputs", JSONArray()
                            .put("source-grounded gap analysis")
                            .put("bounded implementation and tests")
                            .put("commit, workflow and artifact references")
                            .put("explicit blockers and next action"))
                        .put("constraints", JSONArray()
                            .put("Friday remains the independent product identity; cloud models are replaceable workers.")
                            .put("Preserve signing, owner authorization, privacy and rollback boundaries.")
                            .put("Never treat a queued task or a green JVM test as physical-device proof."))
                    val thread = relay.createTask(
                        taskId = taskId,
                        title = "Friday one-button maintenance " + ai.friday.assistant.BuildConfig.VERSION_NAME,
                        sourceRevision = revision,
                        allowedTaskPayload = payload
                    )
                    FridayAgentTaskRelayPollWorker.checkNow(app)
                    queued += "public cloud engineering task"
                    queued += "trusted GitHub task-result polling"
                    "Cloud engineering task opened: " + thread.issueUrl
                }
            }
        }

        val reportConsent = GitHubDeviceReportConsentStore(app).isEnabled()
        val reportStatus = when {
            !githubReady -> "Device evidence stays local because GitHub authorization is not configured."
            !reportConsent -> "Device evidence stays local; automatic reporting consent is off."
            else -> {
                GitHubDeviceReportWorker.enqueue(app, "one-button-maintenance")
                queued += "consented redacted device report"
                "Consented, redacted device reporting queued."
            }
        }

        LaunchReport(
            queued = queued,
            shizuku = shizuku,
            github = if (githubReady) "GitHub credential is present in Friday's encrypted store." else "GitHub credential is not configured.",
            relay = relayStatus,
            deviceReporting = reportStatus
        )
    }

    private const val UNIQUE_UPDATE_WORK = "friday-one-button-signed-release-check"
}
