package ai.friday.assistant.github.reporting

import android.content.Context
import java.util.UUID
import org.json.JSONObject

/**
 * Owner-triggered bridge from Friday's Builder to repository-owned REA workflows.
 * Workflow runs continue independently if Friday closes; task identity is persisted
 * so Friday can resume polling and privately ingest the resulting evidence packet.
 */
class ReaInvestigationCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val packetStore = ReaAssimilationPacketStore(appContext)

    data class Snapshot(
        val investigationId: String,
        val runId: Long?,
        val status: String,
        val conclusion: String?,
        val url: String?,
        val summary: String?,
        val workflowKind: String = WORKFLOW_ANDROID,
        val packetAvailable: Boolean = false,
        val packetError: String? = null
    )

    fun current(): Snapshot? {
        val id = prefs.getString(KEY_INVESTIGATION_ID, null) ?: return null
        val workflowKind = prefs.getString(KEY_WORKFLOW_KIND, WORKFLOW_ANDROID) ?: WORKFLOW_ANDROID
        return Snapshot(
            investigationId = id,
            runId = prefs.getLong(KEY_RUN_ID, -1L).takeIf { it > 0L },
            status = prefs.getString(KEY_STATUS, "dispatched") ?: "dispatched",
            conclusion = prefs.getString(KEY_CONCLUSION, null),
            url = prefs.getString(KEY_URL, null),
            summary = prefs.getString(KEY_SUMMARY, null),
            workflowKind = workflowKind,
            packetAvailable = workflowKind == WORKFLOW_NEOMAS && packetStore.exists(id),
            packetError = prefs.getString(KEY_PACKET_ERROR, null)
        )
    }

    /** Dispatch the fixed Android APK REA workflow. */
    fun start(): Snapshot {
        ensureNoActiveInvestigation()
        val token = GitHubCredentialStore(appContext).read()
            ?: throw IllegalStateException("Save Friday's GitHub token before starting REA.")
        val id = newInvestigationId()
        val dispatchedRunId = GitHubDevelopmentRepositoryClient(token).dispatchReaInvestigation(id)
        persistDispatch(id, WORKFLOW_ANDROID, dispatchedRunId)
        return current()!!
    }

    /** Dispatch the reviewed Neomas reference investigation from inside Friday. */
    fun startNeomas(targetUrl: String = "https://neomas.app/"): Snapshot {
        ensureNoActiveInvestigation()
        val token = GitHubCredentialStore(appContext).read()
            ?: throw IllegalStateException("Save Friday's GitHub token before starting REA.")
        val id = newInvestigationId()
        val dispatchedRunId = GitHubDevelopmentRepositoryClient(token)
            .dispatchNeomasInvestigation(id, targetUrl)
        persistDispatch(id, WORKFLOW_NEOMAS, dispatchedRunId)
        return current()!!
    }

    /**
     * Resume the exact workflow and ingest the bounded Friday packet after a
     * successful Neomas run. Failed or incomplete runs never enter the inbox.
     */
    fun refresh(): Snapshot? {
        val before = current() ?: return null
        val token = GitHubCredentialStore(appContext).read()
            ?: throw IllegalStateException("Friday's GitHub token is unavailable.")
        val client = GitHubDevelopmentRepositoryClient(token)
        val run = if (before.workflowKind == WORKFLOW_NEOMAS) {
            client.findNeomasInvestigationRun(before.investigationId)
        } else {
            client.findReaInvestigationRun(before.investigationId)
        } ?: return before.copy(status = "queued")

        var summary = before.summary
        var packetError: String? = before.packetError
        if (run.status == "completed" && run.conclusion == "success") {
            if (before.workflowKind == WORKFLOW_NEOMAS && !packetStore.exists(before.investigationId)) {
                try {
                    val packetJson = client.downloadNeomasAssimilationPacket(run.id)
                    val stored = packetStore.save(before.investigationId, packetJson)
                    summary = JSONObject()
                        .put("packet_id", stored.packetId)
                        .put("target_locator", stored.targetLocator)
                        .put("finding_count", stored.findingCount)
                        .put("evidence_count", stored.evidenceCount)
                        .toString()
                    packetError = null
                } catch (failure: Exception) {
                    packetError = failure.message ?: "Friday could not ingest the REA evidence packet."
                }
            } else if (before.workflowKind == WORKFLOW_ANDROID && summary == null) {
                summary = runCatching { client.downloadReaInvestigationSummary(run.id) }.getOrNull()
            }
        }

        val editor = prefs.edit()
            .putLong(KEY_RUN_ID, run.id)
            .putString(KEY_STATUS, run.status)
            .putString(KEY_CONCLUSION, run.conclusion)
            .putString(KEY_URL, run.url)
        if (summary != null) editor.putString(KEY_SUMMARY, summary)
        if (packetError != null) editor.putString(KEY_PACKET_ERROR, packetError)
        else editor.remove(KEY_PACKET_ERROR)
        check(editor.commit()) { "Could not save REA workflow status." }
        return current()
    }

    /** Return the validated, private packet for Friday's adaptation planner. */
    fun readAssimilationPacket(): String? {
        val snapshot = current() ?: return null
        if (snapshot.workflowKind != WORKFLOW_NEOMAS || !snapshot.packetAvailable) return null
        return packetStore.read(snapshot.investigationId)
    }

    fun clear() {
        check(
            prefs.edit()
                .remove(KEY_INVESTIGATION_ID)
                .remove(KEY_RUN_ID)
                .remove(KEY_STATUS)
                .remove(KEY_CONCLUSION)
                .remove(KEY_URL)
                .remove(KEY_SUMMARY)
                .remove(KEY_WORKFLOW_KIND)
                .remove(KEY_PACKET_ERROR)
                .commit()
        ) { "Could not clear the REA investigation state." }
    }

    private fun ensureNoActiveInvestigation() {
        val active = current()
        check(active == null || active.status !in setOf("dispatched", "queued", "in_progress")) {
            "A Friday REA investigation is already queued or running; refresh its status first."
        }
    }

    private fun persistDispatch(id: String, workflowKind: String, runId: Long?) {
        check(
            prefs.edit()
                .putString(KEY_INVESTIGATION_ID, id)
                .putLong(KEY_RUN_ID, runId ?: -1L)
                .putString(KEY_STATUS, "dispatched")
                .putString(KEY_WORKFLOW_KIND, workflowKind)
                .remove(KEY_CONCLUSION)
                .remove(KEY_URL)
                .remove(KEY_SUMMARY)
                .remove(KEY_PACKET_ERROR)
                .commit()
        ) { "REA was dispatched but its task id could not be saved; check GitHub Actions before retrying." }
    }

    private fun newInvestigationId() = UUID.randomUUID().toString().replace("-", "")

    companion object {
        private const val PREFS_NAME = "friday_rea_investigation"
        private const val KEY_INVESTIGATION_ID = "investigation_id"
        private const val KEY_RUN_ID = "run_id"
        private const val KEY_STATUS = "status"
        private const val KEY_CONCLUSION = "conclusion"
        private const val KEY_URL = "url"
        private const val KEY_SUMMARY = "summary_json"
        private const val KEY_WORKFLOW_KIND = "workflow_kind"
        private const val KEY_PACKET_ERROR = "packet_error"
        private const val WORKFLOW_ANDROID = "android"
        private const val WORKFLOW_NEOMAS = "neomas"
    }
}
