package com.jarvis.app.cognitive.seed

import com.jarvis.app.cognitive.Goal
import com.jarvis.app.cognitive.GoalPriority
import com.jarvis.app.cognitive.GoalStatus
import com.jarvis.app.cognitive.Subgoal
import com.jarvis.app.cognitive.SubgoalStatus
import com.jarvis.app.humancore.store.FileStorage
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** The durable goal/commitment portion of one Friday individual's continuity. */
data class IndividualGoalContinuity(
    val goal: Goal?,
    val subgoals: List<Subgoal>,
    val updatedAtMs: Long
)

sealed interface GoalContinuityLoad {
    data object Empty : GoalContinuityLoad
    data class Restored(val continuity: IndividualGoalContinuity) : GoalContinuityLoad
    data object IdentityMismatch : GoalContinuityLoad
    data class Corrupt(val reason: String) : GoalContinuityLoad
}

/**
 * Persists active goal intent against Friday's stable individual ID.
 *
 * The store deliberately persists goals and subgoals, not the transient plan
 * graph: after restart Friday must re-evaluate a plan against current
 * capabilities instead of blindly resuming stale execution steps.
 */
class IndividualGoalContinuityStore(
    directory: File,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val file = File(directory, FILE_NAME)
    private val storage = FileStorage(directory)

    fun load(individualId: String): GoalContinuityLoad {
        if (!file.isFile) return GoalContinuityLoad.Empty
        if (file.length() > MAX_FILE_BYTES) {
            return GoalContinuityLoad.Corrupt("Goal continuity file exceeds the safe size limit.")
        }
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            if (root.getInt("schema_version") != SCHEMA_VERSION) {
                return GoalContinuityLoad.Corrupt("Unsupported goal continuity schema.")
            }
            if (root.getString("individual_id") != individualId) {
                return GoalContinuityLoad.IdentityMismatch
            }
            val goal = root.optJSONObject("goal")?.let(::parseGoal)
            val array = root.optJSONArray("subgoals") ?: JSONArray()
            if (array.length() > MAX_SUBGOALS) {
                return GoalContinuityLoad.Corrupt("Too many persisted subgoals.")
            }
            val subgoals = (0 until array.length()).map { index ->
                parseSubgoal(array.getJSONObject(index))
            }
            if (goal == null && subgoals.isNotEmpty()) {
                return GoalContinuityLoad.Corrupt("Subgoals exist without a parent goal.")
            }
            if (goal != null && subgoals.any { it.goalId != goal.id }) {
                return GoalContinuityLoad.Corrupt("A persisted subgoal belongs to a different goal.")
            }
            GoalContinuityLoad.Restored(
                IndividualGoalContinuity(
                    goal = goal,
                    subgoals = subgoals,
                    updatedAtMs = root.getLong("updated_at_ms")
                )
            )
        } catch (failure: Exception) {
            GoalContinuityLoad.Corrupt(
                failure.message?.take(240) ?: "Goal continuity file is malformed."
            )
        }
    }

    fun save(individualId: String, goal: Goal?, subgoals: List<Subgoal>) {
        require(individualId.isNotBlank() && individualId.length <= 128)
        require(subgoals.size <= MAX_SUBGOALS)
        require(goal != null || subgoals.isEmpty()) { "Subgoals require a parent goal." }
        require(goal == null || subgoals.all { it.goalId == goal.id }) {
            "Every subgoal must belong to the current goal."
        }
        val root = JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("individual_id", individualId)
            .put("updated_at_ms", nowMs())
            .put("goal", goal?.let(::goalJson) ?: JSONObject.NULL)
            .put("subgoals", JSONArray().apply { subgoals.forEach { put(subgoalJson(it)) } })
        val serialized = root.toString()
        require(serialized.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) {
            "Goal continuity exceeds the safe storage limit."
        }
        storage.write(file, serialized)
    }

    private fun goalJson(goal: Goal): JSONObject {
        require(goal.id.isNotBlank() && goal.id.length <= 128)
        require(goal.description.isNotBlank() && goal.description.length <= 2_000)
        require(goal.successCriteria.size <= MAX_CRITERIA)
        return JSONObject()
            .put("id", goal.id)
            .put("description", goal.description)
            .put("priority", goal.priority.name)
            .put("created_at", goal.createdAt)
            .put("deadline", goal.deadline ?: JSONObject.NULL)
            .put("success_criteria", JSONArray(goal.successCriteria))
            .put("status", goal.status.name)
            .put("parent_goal_id", goal.parentGoalId ?: JSONObject.NULL)
    }

    private fun parseGoal(json: JSONObject): Goal {
        val criteria = json.optJSONArray("success_criteria") ?: JSONArray()
        require(criteria.length() <= MAX_CRITERIA)
        return Goal(
            id = json.getString("id").also { require(it.isNotBlank() && it.length <= 128) },
            description = json.getString("description").also { require(it.isNotBlank() && it.length <= 2_000) },
            priority = GoalPriority.valueOf(json.getString("priority")),
            createdAt = json.getLong("created_at"),
            deadline = json.optLongOrNull("deadline"),
            successCriteria = (0 until criteria.length()).map { criteria.getString(it) }
                .also { values -> require(values.all { it.length <= 500 }) },
            status = GoalStatus.valueOf(json.getString("status")),
            parentGoalId = json.optStringOrNull("parent_goal_id")
        )
    }

    private fun subgoalJson(subgoal: Subgoal): JSONObject {
        require(subgoal.id.isNotBlank() && subgoal.id.length <= 128)
        require(subgoal.description.isNotBlank() && subgoal.description.length <= 2_000)
        require(subgoal.dependencies.size <= MAX_DEPENDENCIES)
        return JSONObject()
            .put("id", subgoal.id)
            .put("goal_id", subgoal.goalId)
            .put("description", subgoal.description)
            .put("status", subgoal.status.name)
            .put("created_at", subgoal.createdAt)
            .put("estimated_effort", subgoal.estimatedEffort.toDouble())
            .put("dependencies", JSONArray(subgoal.dependencies))
    }

    private fun parseSubgoal(json: JSONObject): Subgoal {
        val dependencies = json.optJSONArray("dependencies") ?: JSONArray()
        require(dependencies.length() <= MAX_DEPENDENCIES)
        val effort = json.getDouble("estimated_effort")
        require(effort.isFinite() && effort in 0.0..10_000.0)
        return Subgoal(
            id = json.getString("id").also { require(it.isNotBlank() && it.length <= 128) },
            goalId = json.getString("goal_id").also { require(it.isNotBlank() && it.length <= 128) },
            description = json.getString("description").also { require(it.isNotBlank() && it.length <= 2_000) },
            status = SubgoalStatus.valueOf(json.getString("status")),
            createdAt = json.getLong("created_at"),
            estimatedEffort = effort.toFloat(),
            dependencies = (0 until dependencies.length()).map { dependencies.getString(it) }
                .also { values -> require(values.all { it.isNotBlank() && it.length <= 128 }) }
        )
    }

    companion object {
        const val FILE_NAME = "continuity/goals.json"
        private const val SCHEMA_VERSION = 1
        private const val MAX_FILE_BYTES = 128 * 1024
        private const val MAX_SUBGOALS = 64
        private const val MAX_CRITERIA = 32
        private const val MAX_DEPENDENCIES = 64
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else getLong(key)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)
