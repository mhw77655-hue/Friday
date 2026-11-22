package ai.friday.assistant.bootstrap.plan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Durable execution ledger independent of plan-sync metadata.
 *
 * Syncing a newer plan must never reset accepted work, checkpoints or task
 * history. A changed phase is explicitly returned to reassessment instead.
 */
class ObjectiveExecutionLedgerStore(context: Context) {
    private val directory = File(context.filesDir, "friday-development/plan")
    private val planFile = File(directory, "canonical-delivery-plan.md")
    private val ledgerFile = File(directory, "execution-ledger.json")

    @Synchronized
    fun reconcile(): JSONObject {
        require(planFile.isFile) { "Canonical local objectives plan is not imported yet." }
        val markdown = planFile.readText(Charsets.UTF_8)
        val objectives = ObjectivePlanParser.parse(markdown)
        val phases = ObjectivePlanParser.parseObjectiveOnePhases(markdown)
        val planHash = sha256(markdown)
        val previous = if (ledgerFile.isFile) runCatching {
            JSONObject(ledgerFile.readText(Charsets.UTF_8))
        }.getOrNull() else null
        val planMetadataFile = File(directory, "objective-ledger.json")
        if (!planMetadataFile.isFile) throw java.io.IOException("Plan metadata is not committed yet.")
        val planMetadata = runCatching {
            JSONObject(planMetadataFile.readText(Charsets.UTF_8))
        }.getOrElse { throw java.io.IOException("Plan metadata is unreadable.", it) }
        if (planMetadata.optString("content_sha256") != planHash) {
            throw java.io.IOException("Canonical plan and its integrity ledger do not match yet.")
        }
        val oldPhases = previous?.optJSONArray("phases") ?: JSONArray()
        val oldObjectives = previous?.optJSONArray("objectives") ?: JSONArray()
        val oldById = (0 until oldPhases.length()).associate { index ->
            val phase = oldPhases.getJSONObject(index)
            phase.getInt("id") to phase
        }
        val oldObjectivesById = (0 until oldObjectives.length()).associate { index ->
            val objective = oldObjectives.getJSONObject(index)
            objective.optString("id") to objective
        }
        val nextObjectives = JSONArray()
        objectives.forEach { objective ->
            val old = oldObjectivesById[objective.id]
            val hash = sha256(objective.markdown)
            val changed = old != null && old.optString("content_sha256").isNotBlank() &&
                old.optString("content_sha256") != hash
            nextObjectives.put(JSONObject()
                .put("id", objective.id)
                .put("title", objective.title)
                .put("status", if (changed) "REASSESSMENT_REQUIRED" else old?.optString("status") ?: "UNASSESSED")
                .put("content_sha256", hash)
                .put("scope_markdown", objective.markdown)
                .put("previous_status", if (changed) old?.optString("status") else JSONObject.NULL))
        }
        val nextPhases = JSONArray()
        phases.forEach { phase ->
            val old = oldById[phase.id]
            val oldHash = old?.optString("content_sha256").orEmpty()
            val changed = old != null && oldHash.isNotBlank() && oldHash != sha256(phase.markdown)
            val status = when {
                old == null -> "UNASSESSED"
                changed && old.optString("status") == "ACCEPTED" -> "REASSESSMENT_REQUIRED"
                changed -> "REASSESSMENT_REQUIRED"
                else -> old.optString("status", "UNASSESSED")
            }
            nextPhases.put(JSONObject()
                .put("id", phase.id)
                .put("title", phase.title)
                .put("status", status)
                .put("content_sha256", sha256(phase.markdown))
                .put("exit_evidence", phase.exitEvidence)
                .put("scope_markdown", phase.markdown)
                .put("previous_status", if (changed) old?.optString("status") else JSONObject.NULL)
                .put("last_verified_at_epoch_ms", if (changed) JSONObject.NULL else old?.opt("last_verified_at_epoch_ms") ?: JSONObject.NULL)
                .put("evidence_refs", if (changed) JSONArray() else old?.optJSONArray("evidence_refs") ?: JSONArray()))
        }
        val ledger = JSONObject()
            .put("schema_version", 1)
            .put("plan_sha256", planHash)
            .put("source_revision", planMetadata?.optString("source_revision_at_install")
                ?: previous?.optString("source_revision") ?: "unknown")
            .put("created_at_epoch_ms", previous?.optLong("created_at_epoch_ms") ?: System.currentTimeMillis())
            .put("updated_at_epoch_ms", System.currentTimeMillis())
            .put("objective_count", objectives.size)
            .put("objectives", nextObjectives)
            .put("phase_count", phases.size)
            .put("phases", nextPhases)
            .put("tasks", previous?.optJSONArray("tasks") ?: JSONArray())
            .put("active_task_id", previous?.opt("active_task_id") ?: JSONObject.NULL)
            .put("execution_state", "PLAN_RECONCILED_TASKS_REQUIRE_REAL_EVIDENCE")
        writeAtomically(ledgerFile, ledger.toString(2))
        return ledger
    }

    @Synchronized
    fun ensurePhaseAssessmentPackets(): JSONObject {
        val ledger = if (ledgerFile.isFile) {
            JSONObject(ledgerFile.readText(Charsets.UTF_8))
        } else reconcile()
        val phases = ledger.getJSONArray("phases")
        val objectives = ledger.getJSONArray("objectives")
        val tasks = ledger.optJSONArray("tasks") ?: JSONArray()
        val activeTaskHashes = mutableSetOf<String>()
        val activeObjectiveHashes = mutableSetOf<String>()
        val phaseById = (0 until phases.length()).associate { index ->
            val phase = phases.getJSONObject(index)
            phase.getInt("id") to phase
        }
        val objectiveById = (0 until objectives.length()).associate { index ->
            val objective = objectives.getJSONObject(index)
            objective.getString("id") to objective
        }
        for (index in 0 until tasks.length()) {
            val task = tasks.getJSONObject(index)
            if (task.optString("status") in TERMINAL_TASK_STATES) continue
            val objectiveId = task.optString("objective_id")
            val phaseId = task.optInt("phase_id", -1)
            if (phaseId >= 0) {
                val phase = phaseById[phaseId]
                val currentHash = phase?.optString("content_sha256").orEmpty()
                when {
                    phase == null || phase.optString("status") == "ACCEPTED" ->
                        task.put("status", "CANCELLED")
                            .put("updated_at_epoch_ms", System.currentTimeMillis())
                    task.optString("phase_content_sha256") != currentHash ->
                        task.put("status", "STALE_PLAN_CHANGED")
                            .put("updated_at_epoch_ms", System.currentTimeMillis())
                    else -> activeTaskHashes += "$phaseId:$currentHash"
                }
            } else {
                val objective = objectiveById[objectiveId]
                val currentHash = objective?.optString("content_sha256").orEmpty()
                when {
                    objective == null || objective.optString("status") == "ACCEPTED" ->
                        task.put("status", "CANCELLED")
                            .put("updated_at_epoch_ms", System.currentTimeMillis())
                    task.optString("scope_content_sha256") != currentHash ->
                        task.put("status", "STALE_PLAN_CHANGED")
                            .put("updated_at_epoch_ms", System.currentTimeMillis())
                    else -> activeObjectiveHashes += "$objectiveId:$currentHash"
                }
            }
        }
        for (index in 0 until phases.length()) {
            val phase = phases.getJSONObject(index)
            val phaseId = phase.getInt("id")
            val phaseHash = phase.getString("content_sha256")
            if (phase.optString("status") == "ACCEPTED" ||
                "$phaseId:$phaseHash" in activeTaskHashes
            ) continue
            tasks.put(JSONObject()
                .put("task_id", "objective-a-phase-$phaseId-assess-${phaseHash.take(8)}")
                .put("objective_id", "A")
                .put("phase_id", phaseId)
                .put("phase_content_sha256", phaseHash)
                .put("plan_sha256", ledger.getString("plan_sha256"))
                .put("task_type", "PHASE_EVIDENCE_ASSESSMENT")
                .put("goal", "Assess Phase $phaseId against its canonical exit evidence and current repository/device proof.")
                .put("acceptance_criteria", phase.getString("exit_evidence"))
                .put("scope_markdown", phase.getString("scope_markdown"))
                .put("status", "QUEUED")
                .put("executor", "NOT_ASSIGNED")
                .put("requires_repository_access", true)
                .put("dependencies", JSONArray())
                .put("dependency_state", "NOT_EXTRACTED_FROM_CANONICAL_PLAN")
                .put("checkpoint", JSONObject().put("stage", "NOT_STARTED"))
                .put("created_at_epoch_ms", System.currentTimeMillis())
                .put("updated_at_epoch_ms", System.currentTimeMillis()))
            activeTaskHashes += "$phaseId:$phaseHash"
        }
        // Objectives B-G remain independently tracked. No dependencies are
        // fabricated: the canonical plan must be reconciled before scheduling.
        for (index in 0 until objectives.length()) {
            val objective = objectives.getJSONObject(index)
            val objectiveId = objective.getString("id")
            if (objectiveId == "A" || objective.optString("status") == "ACCEPTED") continue
            val hash = objective.getString("content_sha256")
            if ("$objectiveId:$hash" in activeObjectiveHashes) continue
            tasks.put(JSONObject()
                .put("task_id", "objective-$objectiveId-assess-${hash.take(8)}")
                .put("objective_id", objectiveId)
                .put("phase_id", JSONObject.NULL)
                .put("scope_content_sha256", hash)
                .put("plan_sha256", ledger.getString("plan_sha256"))
                .put("task_type", "OBJECTIVE_SCOPE_ASSESSMENT")
                .put("goal", "Map Objective $objectiveId (${objective.getString("title")}) into evidence-backed, dependency-aware work packets.")
                .put("acceptance_criteria", "Extract the objective's actual success criteria, dependencies, blockers and measurable evidence without inventing any.")
                .put("scope_markdown", objective.getString("scope_markdown"))
                .put("status", "QUEUED")
                .put("executor", "NOT_ASSIGNED")
                .put("requires_repository_access", true)
                .put("dependencies", JSONArray())
                .put("dependency_state", "NOT_EXTRACTED_FROM_CANONICAL_PLAN")
                .put("checkpoint", JSONObject().put("stage", "NOT_STARTED"))
                .put("created_at_epoch_ms", System.currentTimeMillis())
                .put("updated_at_epoch_ms", System.currentTimeMillis()))
        }
        ledger.put("tasks", tasks)
            .put("updated_at_epoch_ms", System.currentTimeMillis())
            .put("execution_state", "ALL_OBJECTIVES_DECOMPOSED_AWAITING_EXECUTOR")
        writeAtomically(ledgerFile, ledger.toString(2))
        return ledger
    }

    @Synchronized
    fun snapshot(): JSONObject? = if (ledgerFile.isFile) {
        runCatching { JSONObject(ledgerFile.readText(Charsets.UTF_8)) }.getOrNull()
    } else null

    private fun writeAtomically(file: File, content: String) {
        if (!directory.exists() && !directory.mkdirs()) error("Cannot create execution ledger directory.")
        val temp = File(directory, file.name + ".tmp")
        temp.writeText(content, Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        private val TERMINAL_TASK_STATES = setOf("ACCEPTED", "REJECTED", "CANCELLED", "STALE_PLAN_CHANGED")
    }
}
