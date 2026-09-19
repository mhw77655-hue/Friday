package com.jarvis.app.cognitive

/**
 * TaskWorkingMemory — Holds the current goal, ordered subgoals, and execution
 * history for one in-flight multi-step task. Not persisted across app restarts.
 *
 * API:
 * - [setGoal] sets the top-level goal text
 * - [pushSubgoals] pushes an ordered list of subgoal descriptions
 * - [nextSubgoal] pops and returns the next pending subgoal, or null if empty
 * - [recordResult] appends a result to the execution history
 * - [getHistory] returns the full execution history in order
 * - [clear] resets all state
 */
class TaskWorkingMemory {

    data class SubgoalEntry(
        val description: String,
        val index: Int
    )

    data class ResultEntry(
        val subgoal: String,
        val success: Boolean,
        val detail: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private var goal: String? = null
    private val subgoals = ArrayDeque<SubgoalEntry>()
    private val history = mutableListOf<ResultEntry>()
    private var nextIndex = 0
    private var _replanCount = 0

    /** Number of replans performed so far for this task. */
    val replanCount: Int get() = _replanCount

    fun setGoal(goal: String) {
        this.goal = goal
    }

    fun getGoal(): String? = goal

    fun pushSubgoals(descriptions: List<String>) {
        for (desc in descriptions) {
            subgoals.addLast(SubgoalEntry(description = desc, index = nextIndex++))
        }
    }

    fun nextSubgoal(): SubgoalEntry? {
        return if (subgoals.isEmpty()) null else subgoals.removeFirst()
    }

    fun recordResult(subgoal: String, success: Boolean, detail: String = "") {
        history.add(ResultEntry(subgoal = subgoal, success = success, detail = detail))
    }

    fun getHistory(): List<ResultEntry> = history.toList()

    fun clear() {
        goal = null
        subgoals.clear()
        history.clear()
        nextIndex = 0
        _replanCount = 0
    }

    /**
     * Replace all remaining pending subgoals with a new ordered list.
     * Increments the replan counter. Returns the new subgoal descriptions.
     */
    fun replaceRemainingSubgoals(newDescriptions: List<String>): List<String> {
        subgoals.clear()
        for (desc in newDescriptions) {
            subgoals.addLast(SubgoalEntry(description = desc, index = nextIndex++))
        }
        _replanCount++
        return newDescriptions
    }

    /**
     * Snapshot of remaining pending subgoals (for passing failure context to
     * the replan LLM call). Does not consume them.
     */
    fun pendingSubgoalDescriptions(): List<String> = subgoals.map { it.description }
}
