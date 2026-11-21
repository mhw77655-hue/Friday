package ai.friday.assistant.cognitive

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

    data class ExecutableSubgoal(
        val description: String,
        val operation: String,
        val parameters: Map<String, String> = emptyMap()
    )

    enum class ResultStatus { SUCCEEDED, FAILED, BLOCKED, SKIPPED }

    data class SubgoalEntry(
        val description: String,
        val index: Int,
        val operation: String? = null,
        val parameters: Map<String, String> = emptyMap()
    )

    data class ResultEntry(
        val subgoal: String,
        val success: Boolean,
        val detail: String,
        val timestamp: Long = System.currentTimeMillis(),
        val status: ResultStatus = if (success) ResultStatus.SUCCEEDED else ResultStatus.FAILED
    )

    private var goal: String? = null
    private val subgoals = ArrayDeque<SubgoalEntry>()
    private val history = mutableListOf<ResultEntry>()
    private var nextIndex = 0
    private var _replanCount = 0

    /** Number of replans performed so far for this task. */
    val replanCount: Int @Synchronized get() = _replanCount

    @Synchronized
    fun setGoal(goal: String) {
        this.goal = goal
    }

    @Synchronized
    fun getGoal(): String? = goal

    @Synchronized
    fun pushSubgoals(descriptions: List<String>) {
        for (desc in descriptions) {
            subgoals.addLast(SubgoalEntry(description = desc, index = nextIndex++))
        }
    }

    @Synchronized
    fun pushExecutableSubgoals(subgoals: List<ExecutableSubgoal>) {
        val validated = subgoals.map { item ->
            require(item.description.isNotBlank() && item.description.length <= 1024)
            require(item.operation.isNotBlank() && item.operation.length <= 128)
            require(item.parameters.size <= 64)
            require(item.parameters.all { (key, value) ->
                key.isNotBlank() && key.length <= 128 && value.length <= 4096
            })
            item.copy(parameters = item.parameters.toMap())
        }
        validated.forEach { item ->
            this.subgoals.addLast(
                SubgoalEntry(item.description, nextIndex++, item.operation, item.parameters)
            )
        }
    }

    @Synchronized
    fun nextSubgoal(): SubgoalEntry? {
        return if (subgoals.isEmpty()) null else subgoals.removeFirst()
    }

    @Synchronized
    fun recordResult(
        subgoal: String,
        success: Boolean,
        detail: String = "",
        status: ResultStatus = if (success) ResultStatus.SUCCEEDED else ResultStatus.FAILED
    ) {
        require(success == (status == ResultStatus.SUCCEEDED))
        history.add(ResultEntry(subgoal = subgoal, success = success, detail = detail, status = status))
    }

    @Synchronized
    fun replaceRemainingExecutableSubgoals(newSubgoals: List<ExecutableSubgoal>) {
        val validated = newSubgoals.map { item ->
            require(item.description.isNotBlank() && item.description.length <= 1024)
            require(item.operation.isNotBlank() && item.operation.length <= 128)
            require(item.parameters.size <= 64)
            require(item.parameters.all { (key, value) ->
                key.isNotBlank() && key.length <= 128 && value.length <= 4096
            })
            item.copy(parameters = item.parameters.toMap())
        }
        subgoals.clear()
        validated.forEach { item ->
            subgoals.addLast(SubgoalEntry(item.description, nextIndex++, item.operation, item.parameters))
        }
        _replanCount++
    }

    @Synchronized
    fun getHistory(): List<ResultEntry> = history.toList()

    @Synchronized
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
    @Synchronized
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
    @Synchronized
    fun pendingSubgoalDescriptions(): List<String> = subgoals.map { it.description }
}
