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
        }.getOrElse {
            // Never replace a damaged execution ledger with a clean-looking one:
            // the old task history/checkpoints may be the only recovery evidence.
            throw java.io.IOException("Execution ledger is damaged; preserving it for recovery.", it)
        } else null
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
            val taskStatus = task.optString("status")
            val objectiveId = task.optString("objective_id")
            val phaseId = task.optInt("phase_id", -1)
            if (taskStatus in TERMINAL_TASK_STATES) {
                if (taskStatus == "DECOMPOSED") {
                    if (phaseId >= 0) {
                        val hash = phaseById[phaseId]?.optString("content_sha256").orEmpty()
                        if (hash.isNotBlank() && task.optString("phase_content_sha256") == hash) {
                            activeTaskHashes += "$phaseId:$hash"
                        }
                    } else {
                        val hash = objectiveById[objectiveId]?.optString("content_sha256").orEmpty()
                        if (hash.isNotBlank() && task.optString("scope_content_sha256") == hash) {
                            activeObjectiveHashes += "$objectiveId:$hash"
                        }
                    }
                }
                continue
            }
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
    fun nextQueuedParentTask(): JSONObject? {
        val ledger = snapshot() ?: return null
        val tasks = ledger.optJSONArray("tasks") ?: return null
        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            if (task.optString("status") == "QUEUED" &&
                task.optString("task_type") in PARENT_TASK_TYPES
            ) return JSONObject(task.toString())
        }
        return null
    }

    @Synchronized
    fun hasQueuedParentTasks(): Boolean = nextQueuedParentTask() != null

    @Synchronized
    fun recordDecomposition(
        parentTaskId: String,
        modelId: String,
        items: JSONArray
    ) {
        val ledger = snapshot() ?: error("Execution ledger is not initialized.")
        val tasks = ledger.getJSONArray("tasks")
        val parentIndex = (0 until tasks.length()).firstOrNull { index ->
            tasks.getJSONObject(index).optString("task_id") == parentTaskId
        } ?: error("Parent task no longer exists.")
        val parent = tasks.getJSONObject(parentIndex)
        require(parent.optString("status") == "QUEUED") {
            "Parent task is no longer queued for decomposition."
        }
        require(items.length() in 1..4) { "A decomposition must contain one to four bounded work items." }
        val itemIds = (0 until items.length()).map { items.getJSONObject(it).getString("id") }.toSet()
        require(itemIds.size == items.length()) { "Decomposition contains duplicate child ids." }
        val childIds = JSONArray()
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val childId = parentTaskId + "-" + item.getString("id")
            val dependencies = item.optJSONArray("dependencies") ?: JSONArray()
            for (depIndex in 0 until dependencies.length()) {
                require(dependencies.getString(depIndex) in itemIds) {
                    "A child dependency points outside this decomposition batch."
                }
            }
            val childDependencies = JSONArray()
            for (depIndex in 0 until dependencies.length()) {
                childDependencies.put(parentTaskId + "-" + dependencies.getString(depIndex))
            }
            val child = JSONObject()
                .put("task_id", childId)
                .put("parent_task_id", parentTaskId)
                .put("objective_id", parent.optString("objective_id"))
                .put("phase_id", parent.opt("phase_id") ?: JSONObject.NULL)
                .put("task_type", "BOUNDED_DEVELOPMENT_STEP")
                .put("goal", item.getString("goal"))
                .put("acceptance_criteria", item.getJSONArray("acceptance_criteria"))
                .put("evidence_needed", item.getJSONArray("evidence_needed"))
                .put("risk", item.getString("risk"))
                .put("reversible", item.getBoolean("reversible"))
                .put("requires_owner_approval", item.getBoolean("requires_owner_approval"))
                .put("status", "QUEUED")
                .put("executor", "LOCAL_BUILDER")
                .put("requires_repository_access", true)
                .put("dependencies", childDependencies)
                .put("dependency_state", item.optString("dependency_state", "UNRESOLVED"))
                .put("source_model_id", modelId)
                .put("checkpoint", JSONObject().put("stage", "NOT_STARTED"))
                .put("created_at_epoch_ms", System.currentTimeMillis())
                .put("updated_at_epoch_ms", System.currentTimeMillis())
            if ((0 until tasks.length()).none { tasks.getJSONObject(it).optString("task_id") == childId }) {
                tasks.put(child)
            }
            childIds.put(childId)
        }
        parent.put("status", "DECOMPOSED")
            .put("child_task_ids", childIds)
            .put("decomposed_by_model", modelId)
            .put("decomposed_at_epoch_ms", System.currentTimeMillis())
            .put("updated_at_epoch_ms", System.currentTimeMillis())
        ledger.put("tasks", tasks)
            .put("updated_at_epoch_ms", System.currentTimeMillis())
            .put("execution_state", "WORK_BREAKDOWN_READY_LOCAL_BUILDER")
        writeAtomically(ledgerFile, ledger.toString(2))
    }


    @Synchronized
    fun nextQueuedChildTask(): JSONObject? {
        val tasks = snapshot()?.optJSONArray("tasks") ?: return null
        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            if (task.optString("task_type") != "BOUNDED_DEVELOPMENT_STEP" ||
                task.optString("status") != "QUEUED" ||
                !task.optBoolean("reversible", false) ||
                task.optString("risk") != "LOW" ||
                task.optBoolean("requires_owner_approval", true) ||
                !ObjectiveTaskDependencyGate.areSatisfied(task, tasks)
            ) continue
            return JSONObject(task.toString())
        }
        return null
    }

    @Synchronized
    fun recordCandidatePublished(taskId: String, candidate: JSONObject) {
        val ledger = snapshot() ?: error("Execution ledger is not initialized.")
        val tasks = ledger.optJSONArray("tasks") ?: error("Task ledger is missing.")
        val task = (0 until tasks.length()).map { tasks.getJSONObject(it) }
            .firstOrNull { it.optString("task_id") == taskId }
            ?: error("Builder task no longer exists.")
        require(task.optString("status") == "EXECUTING") {
            "Builder task is no longer executing."
        }
        task.put("status", "CANDIDATE_PUBLISHED")
            .put("candidate", candidate)
            .put("checkpoint", JSONObject()
                .put("stage", "PULL_REQUEST_OPENED_CI_PENDING")
                .put("updated_at_epoch_ms", System.currentTimeMillis()))
            .put("updated_at_epoch_ms", System.currentTimeMillis())
        ledger.put("updated_at_epoch_ms", System.currentTimeMillis())
            .put("execution_state", "CANDIDATE_PUBLISHED_AWAITING_INDEPENDENT_VERIFICATION")
        writeAtomically(ledgerFile, ledger.toString(2))
    }

    @Synchronized
    fun nextCandidateForVerification(): JSONObject? {
        val tasks = snapshot()?.optJSONArray("tasks") ?: return null
        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            if (task.optString("status") == "CANDIDATE_PUBLISHED") {
                return JSONObject(task.toString())
            }
        }
        return null
    }

    @Synchronized
    fun nextCandidateForSandbox(): JSONObject? {
        val tasks = snapshot()?.optJSONArray("tasks") ?: return null
        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            val status = task.optString("status")
            val sandbox = task.optJSONObject("sandbox")
            val awaitingSandbox = status in setOf("CI_PASSED_AWAITING_SANDBOX", "CI_PASSED_AWAITING_REVIEW") ||
                (status == "CI_FAILED_AWAITING_SANDBOX_TRIAGE" && sandbox?.optString("status").isNullOrBlank())
            if (awaitingSandbox) return JSONObject(task.toString())
        }
        return null
    }

    @Synchronized
    fun recordCandidateSandboxDispatched(taskId: String, runId: Long?) {
        val ledger = snapshot() ?: error("Execution ledger is not initialized.")
        val tasks = ledger.optJSONArray("tasks") ?: error("Task ledger is missing.")
        val task = (0 until tasks.length()).map { tasks.getJSONObject(it) }
            .firstOrNull { it.optString("task_id") == taskId }
            ?: error("Builder task no longer exists.")
        require(task.optString("status") in setOf(
            "CI_PASSED_AWAITING_SANDBOX",
            "CI_PASSED_AWAITING_REVIEW",
            "CI_FAILED_AWAITING_SANDBOX_TRIAGE"
        )) { "Candidate is not awaiting isolated sandbox verification." }
        val ciFailed = task.optJSONObject("ci")?.optString("state") == "FAILURE"
        task.put("status", if (ciFailed) "CI_FAILED_AWAITING_SANDBOX_TRIAGE" else "CI_PASSED_AWAITING_SANDBOX")
            .put("sandbox", JSONObject()
                .put("state", "DISPATCHED")
                .put("run_id", runId ?: JSONObject.NULL)
                .put("dispatched_at_epoch_ms", System.currentTimeMillis()))
            .put("checkpoint", JSONObject()
                .put("stage", "ISOLATED_SANDBOX_DISPATCHED")
                .put("updated_at_epoch_ms", System.currentTimeMillis()))
            .put("updated_at_epoch_ms", System.currentTimeMillis())
        ledger.put("updated_at_epoch_ms", System.currentTimeMillis())
        writeAtomically(ledgerFile, ledger.toString(2))
    }

    @Synchronized
    fun recordCandidateSandboxResult(taskId: String, evidence: JSONObject) {
        val ledger = snapshot() ?: error("Execution ledger is not initialized.")
        val tasks = ledger.optJSONArray("tasks") ?: error("Task ledger is missing.")
        val task = (0 until tasks.length()).map { tasks.getJSONObject(it) }
            .firstOrNull { it.optString("task_id") == taskId }
            ?: error("Builder task no longer exists.")
        require(task.optString("status") in setOf(
            "CI_PASSED_AWAITING_SANDBOX",
            "CI_PASSED_AWAITING_REVIEW",
            "CI_FAILED_AWAITING_SANDBOX_TRIAGE"
        )) { "Candidate is not awaiting isolated sandbox verification." }
        val ciFailed = task.optJSONObject("ci")?.optString("state") == "FAILURE"
        val candidateSha = task.optJSONObject("candidate")?.optString("commit_sha").orEmpty()
        val disposition = CandidateRepairPlanner.classifySandboxOutcome(candidateSha, ciFailed, evidence)
        val actionableDefect = disposition.queueRepair
        val nextStatus = disposition.taskStatus
        task.put("status", nextStatus)
            .put("sandbox", evidence)
            .put("checkpoint", JSONObject()
                .put("stage", nextStatus)
                .put("updated_at_epoch_ms", System.currentTimeMillis()))
            .put("updated_at_epoch_ms", System.currentTimeMillis())
        val repairQueued = if (actionableDefect) appendRepairTask(
            tasks,
            task,
            JSONObject()
                .put("trigger", if (ciFailed) "candidate_ci_and_confirmed_sandbox_defect" else "candidate_sandbox_defect")
                .put("ci", task.optJSONObject("ci") ?: JSONObject())
                .put("sandbox", evidence)
        ) else false
        ledger.put("updated_at_epoch_ms", System.currentTimeMillis())
            .put("execution_state", if (repairQueued) "FAILED_CANDIDATE_REPAIR_QUEUED" else nextStatus)
        writeAtomically(ledgerFile, ledger.toString(2))
    }

    @Synchronized
    fun nextCandidateForReview(): JSONObject? {
        val tasks = snapshot()?.optJSONArray("tasks") ?: return null
        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            if (task.optString("status") == "SANDBOX_PASSED_AWAITING_REVIEW") {
                return JSONObject(task.toString())
            }
        }
        return null
    }

    @Synchronized
    fun recordCandidateReview(taskId: String, review: JSONObject) {
        val ledger = snapshot() ?: error("Execution ledger is not initialized.")
        val tasks = ledger.optJSONArray("tasks") ?: error("Task ledger is missing.")
        val task = (0 until tasks.length()).map { tasks.getJSONObject(it) }
            .firstOrNull { it.optString("task_id") == taskId }
            ?: error("Builder task no longer exists.")
        require(task.optString("status") in setOf(
            "CI_PASSED_AWAITING_REVIEW",
            "SANDBOX_PASSED_AWAITING_REVIEW"
        )) { "Candidate is not awaiting independent review." }
        val stance = review.optString("stance")
        val nextStatus = when (stance) {
            "AGREE" -> "VERIFIED_CANDIDATE_AWAITING_OWNER_REVIEW"
            "DISPUTE", "ADD" -> "REVIEW_REQUIRES_REWORK"
            else -> "REVIEW_INCOMPLETE"
        }
        task.put("status", nextStatus)
            .put("independent_review", review)
            .put("checkpoint", JSONObject()
                .put("stage", nextStatus)
                .put("updated_at_epoch_ms", System.currentTimeMillis()))
            .put("updated_at_epoch_ms", System.currentTimeMillis())
        val repairQueued = if (stance == "DISPUTE" || stance == "ADD") appendRepairTask(
            tasks,
            task,
            JSONObject()
                .put("trigger", "independent_candidate_review")
                .put("ci", task.optJSONObject("ci") ?: JSONObject())
                .put("sandbox", task.optJSONObject("sandbox") ?: JSONObject())
                .put("review", review)
        ) else false
        ledger.put("updated_at_epoch_ms", System.currentTimeMillis())
            .put("execution_state", if (repairQueued) "REVIEW_REPAIR_QUEUED" else nextStatus)
        writeAtomically(ledgerFile, ledger.toString(2))
    }

    @Synchronized
    fun recordCandidateCiResult(taskId: String, checks: JSONObject) {
        val ledger = snapshot() ?: error("Execution ledger is not initialized.")
        val tasks = ledger.optJSONArray("tasks") ?: error("Task ledger is missing.")
        val task = (0 until tasks.length()).map { tasks.getJSONObject(it) }
            .firstOrNull { it.optString("task_id") == taskId }
            ?: error("Builder task no longer exists.")
        require(task.optString("status") == "CANDIDATE_PUBLISHED") {
            "Candidate is no longer awaiting CI."
        }
        val state = checks.optString("state")
        require(state in setOf("PENDING", "SUCCESS", "FAILURE")) { "Unknown CI state." }
        if (state != "PENDING") {
            task.put("status", if (state == "SUCCESS") "CI_PASSED_AWAITING_SANDBOX" else "CI_FAILED_AWAITING_SANDBOX_TRIAGE")
        }
        task.put("ci", checks)
            .put("checkpoint", JSONObject()
                .put("stage", when (state) {
                    "SUCCESS" -> "CI_PASSED_AWAITING_ISOLATED_SANDBOX"
                    "FAILURE" -> "CI_FAILED_REPAIR_REQUIRED"
                    else -> "CI_PENDING"
                })
                .put("updated_at_epoch_ms", System.currentTimeMillis()))
            .put("updated_at_epoch_ms", System.currentTimeMillis())
        ledger.put("updated_at_epoch_ms", System.currentTimeMillis())
        writeAtomically(ledgerFile, ledger.toString(2))
    }

    private fun appendRepairTask(
        tasks: JSONArray,
        failedTask: JSONObject,
        evidence: JSONObject
    ): Boolean {
        val repair = CandidateRepairPlanner.createRepairTask(failedTask, evidence) ?: return false
        val repairId = repair.optString("task_id")
        if ((0 until tasks.length()).any { tasks.optJSONObject(it)?.optString("task_id") == repairId }) {
            return false
        }
        tasks.put(repair)
        return true
    }

    @Synchronized
    fun recoverInterruptedTasks(processInstanceId: String, nowEpochMs: Long = System.currentTimeMillis()): Int {
        require(processInstanceId.isNotBlank())
        val ledger = snapshot() ?: return 0
        val tasks = ledger.optJSONArray("tasks") ?: return 0
        var recovered = 0
        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            val recordedProcess = task.optString("execution_process_id")
            val updatedAt = task.optLong("updated_at_epoch_ms", 0L)
            if (!ObjectiveTaskRecoveryPolicy.shouldRecover(
                    status = task.optString("status"),
                    recordedProcessId = recordedProcess,
                    currentProcessId = processInstanceId,
                    updatedAtEpochMs = updatedAt,
                    nowEpochMs = nowEpochMs,
                    staleAfterMs = EXECUTION_LEASE_STALE_MS
                )
            ) continue
            task.put("status", "QUEUED")
                .put("last_recovery_reason", "Recovered an interrupted Builder task after process-generation change or stale execution lease.")
                .put("checkpoint", JSONObject()
                    .put("stage", "RECOVERED_AFTER_INTERRUPTION")
                    .put("updated_at_epoch_ms", nowEpochMs))
                .put("updated_at_epoch_ms", nowEpochMs)
            recovered++
        }
        if (recovered > 0) {
            ledger.put("tasks", tasks)
                .put("updated_at_epoch_ms", nowEpochMs)
                .put("execution_state", "INTERRUPTED_TASKS_RECOVERED")
            writeAtomically(ledgerFile, ledger.toString(2))
        }
        return recovered
    }

    @Synchronized
    fun markChildTaskExecuting(taskId: String, processInstanceId: String): Boolean {
        val ledger = snapshot() ?: return false
        val tasks = ledger.optJSONArray("tasks") ?: return false
        val task = (0 until tasks.length()).map { tasks.getJSONObject(it) }
            .firstOrNull { it.optString("task_id") == taskId } ?: return false
        if (task.optString("status") != "QUEUED") return false
        require(processInstanceId.isNotBlank())
        task.put("status", "EXECUTING")
            .put("execution_process_id", processInstanceId)
            .put("checkpoint", JSONObject()
                .put("stage", "LOCAL_CANDIDATE_GENERATION")
                .put("updated_at_epoch_ms", System.currentTimeMillis()))
            .put("updated_at_epoch_ms", System.currentTimeMillis())
        ledger.put("updated_at_epoch_ms", System.currentTimeMillis())
        writeAtomically(ledgerFile, ledger.toString(2))
        return true
    }

    @Synchronized
    fun recordChildTaskFailure(taskId: String, reason: String) {
        val ledger = snapshot() ?: return
        val tasks = ledger.optJSONArray("tasks") ?: return
        val task = (0 until tasks.length()).map { tasks.getJSONObject(it) }
            .firstOrNull { it.optString("task_id") == taskId } ?: return
        if (task.optString("status") == "EXECUTING") {
            task.put("status", "QUEUED")
                .put("last_builder_error", reason.take(400))
                .put("builder_attempts", task.optInt("builder_attempts", 0) + 1)
                .put("checkpoint", JSONObject()
                    .put("stage", "RETRYABLE_FAILURE")
                    .put("updated_at_epoch_ms", System.currentTimeMillis()))
                .put("updated_at_epoch_ms", System.currentTimeMillis())
            ledger.put("updated_at_epoch_ms", System.currentTimeMillis())
            writeAtomically(ledgerFile, ledger.toString(2))
        }
    }

    @Synchronized
    fun recordDecompositionFailure(parentTaskId: String, reason: String) {
        val ledger = snapshot() ?: return
        val tasks = ledger.optJSONArray("tasks") ?: return
        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            if (task.optString("task_id") == parentTaskId && task.optString("status") == "QUEUED") {
                task.put("decomposition_attempts", task.optInt("decomposition_attempts", 0) + 1)
                    .put("last_decomposition_error", reason.take(400))
                    .put("updated_at_epoch_ms", System.currentTimeMillis())
                break
            }
        }
        ledger.put("updated_at_epoch_ms", System.currentTimeMillis())
        writeAtomically(ledgerFile, ledger.toString(2))
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
        private val TERMINAL_TASK_STATES = setOf("ACCEPTED", "REJECTED", "CANCELLED", "STALE_PLAN_CHANGED", "DECOMPOSED")
        private val PARENT_TASK_TYPES = setOf("PHASE_EVIDENCE_ASSESSMENT", "OBJECTIVE_SCOPE_ASSESSMENT")
        private const val EXECUTION_LEASE_STALE_MS = 6 * 60 * 60 * 1000L
    }
}
