package com.jarvis.app.cognitive.execution

/** Structured execution lifecycle of the engine. */
enum class ExecutionStatus {
    IDLE,          // no plan loaded
    EXECUTING,     // a step is in progress
    RETRYING,      // a step failed but retry is allowed
    PAUSED,        // execution suspended between steps
    CANCELLED,     // execution stopped by request
    BLOCKED,       // no actionable step remains (blocked plan)
    COMPLETED,     // plan reached completion
    FAILED         // plan failed terminally
}

/**
 * Structured execution state — the observability contract of the executor.
 * Text logs are never used as state.
 */
data class ExecutionState(
    val planId: String? = null,
    val currentStepId: String? = null,
    val currentAction: String = "",
    val status: ExecutionStatus = ExecutionStatus.IDLE,
    /** Attempts for the CURRENT step (1 = first attempt). */
    val attemptCount: Int = 0,
    val lastResult: ExecutionResult? = null,
    val failureCause: String? = null,
    /** True while the last failure is scheduled for retry. */
    val retryable: Boolean = false,
    val replanTriggered: Boolean = false,
    val startedAt: Long = 0,
    val lastExecutedAt: Long = 0,
    val completedSteps: Int = 0,
    val failedSteps: Int = 0,
    val executedSteps: Int = 0,
    // ── 01F capability-aware fields (additive — the 01C engine never sets them) ──
    /** The id of the capability selected for the current step, when resolved. */
    val currentCapabilityId: String? = null,
    /** Structured resolution label for the current step ("AVAILABLE" or a
     *  ResolutionReason name). */
    val resolutionResult: String? = null,
    /** Structured invocation label: "SUCCESS" / "FAILURE" / "REFUSED". */
    val invocationResult: String? = null,
    /** Immutable failure identity of the current step's failure, if any. */
    val failureIdentity: String? = null,
    /** Whether an alternative capability (or replan path) existed for a failed step. */
    val alternativeAvailable: Boolean = false
) {
    val hasPlan: Boolean get() = planId != null
}

/** Bounded retry policy — retries are finite and explicit. */
data class RetryPolicy(
    /** Maximum attempts per step (>= 1). 1 means "no retry". */
    val maxAttempts: Int = 3,
    /** Delay between attempts, in ms. Tests use 0. */
    val backoffMs: Long = 0
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    }

    /** Whether a further attempt is allowed after [attemptsSoFar] attempts. */
    fun canRetry(attemptsSoFar: Int): Boolean = attemptsSoFar < maxAttempts
}

/** Explicit execution boundaries — guards against runaway loops. */
data class ExecutionConfig(
    /** Maximum steps executed per runToTerminal() call. */
    val maxStepsPerRun: Int = 100,
    /** Maximum replans permitted per run before terminal failure. */
    val maxReplans: Int = 3
) {
    init {
        require(maxStepsPerRun >= 1)
        require(maxReplans >= 1)
    }
}

/** Result of a single step execution attempt. */
data class StepOutcome(
    val stepId: String,
    val result: ExecutionResult,
    val attempt: Int,
    val nextStepId: String? = null,
    val replanned: Boolean = false
)

/** Terminal summary of a full execution run. */
data class ExecutionSummary(
    val planId: String,
    val status: ExecutionStatus,
    val stepsCompleted: Int,
    val stepsFailed: Int,
    val failureCause: String? = null,
    val replanTriggered: Boolean = false,
    val steps: List<StepOutcome> = emptyList()
)
