package com.jarvis.app.cognitive.execution

/**
 * The boundary between cognition and real-world capabilities.
 *
 * The execution subsystem knows only "I have an action request" — it has no
 * idea how a browser, a shell, Android, voice, vision, or any capability is
 * implemented. Future capability implementations (the Capability Fabric)
 * implement [ActionPort] and nothing more.
 */
fun interface ActionPort {
    /**
     * Execute one action. Must be deterministic from the caller's perspective
     * given the same request, and must not throw for ordinary failures — encode
     * failure in the returned [ExecutionResult]. A thrown exception is treated
     * by the executor as a non-recoverable port failure.
     */
    suspend fun execute(request: ActionRequest): ExecutionResult
}

/**
 * A structured request to perform one plan step. Contains only what an
 * arbitrary capability needs to act — never implementation detail.
 */
data class ActionRequest(
    val planId: String,
    val stepId: String,
    /** The capability-agnostic action verb (e.g. "lookup", "compute"). */
    val action: String,
    /** Human-readable description of what the step is doing. */
    val description: String = "",
    /** Key/value context relevant to the action (goal, expected outcome). */
    val context: Map<String, String> = emptyMap(),
    /** Constraints the capability must respect. */
    val constraints: List<String> = emptyList()
)

/** Structured failure information returned by a capability. */
sealed class ActionFailure {
    /** Whether retrying the same action is worth attempting. */
    open val recoverable: Boolean = false

    /** A domain error with a code and optional evidence. */
    data class Error(
        val code: String,
        val message: String,
        /** Whether retrying the same action may help. */
        override val recoverable: Boolean = false,
        val evidence: Map<String, String> = emptyMap()
    ) : ActionFailure()

    /** The action exceeded its time budget. */
    data class Timeout(val durationMs: Long) : ActionFailure()

    /** The action verb is not supported by this capability. */
    data class Unsupported(val action: String) : ActionFailure()
}

/** Structured result of executing an action. */
data class ExecutionResult(
    val success: Boolean,
    val failure: ActionFailure? = null,
    /** Output / result metadata keyed by field name. */
    val output: Map<String, String> = emptyMap(),
    /** Evidence for the outcome (logs, refs, traces) — structured, not state. */
    val evidence: Map<String, String> = emptyMap(),
    val durationMs: Long? = null,
    /** Whether a retry of the same action is likely to succeed. */
    val retryable: Boolean = false
) {
    companion object {
        fun ok(
            output: Map<String, String> = emptyMap(),
            durationMs: Long? = null,
            evidence: Map<String, String> = emptyMap()
        ): ExecutionResult = ExecutionResult(
            success = true, output = output, evidence = evidence, durationMs = durationMs, retryable = false
        )

        fun fail(
            failure: ActionFailure,
            durationMs: Long? = null
        ): ExecutionResult = ExecutionResult(
            success = false,
            failure = failure,
            durationMs = durationMs,
            retryable = failure.recoverable
        )
    }
}
