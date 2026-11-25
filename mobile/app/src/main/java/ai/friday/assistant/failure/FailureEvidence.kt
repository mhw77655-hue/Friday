package ai.friday.assistant.failure

/**
 * Turns the evidence a failure record already carries into one bounded line for
 * the diagnosis text and the exported device report.
 *
 * Only frame identity (class, method, file, line) is taken from the stack,
 * never argument values, and every field is length-bounded.
 */
object FailureEvidence {
    /** Metadata key under which [stackTop] output is stored on a FailureReport. */
    const val STACK_TOP_KEY = "stackTop"

    /** Upper bound for the whole rendered suffix. */
    const val MAX_LENGTH = 900

    private const val MAX_CAUSE = 200
    private const val MAX_WHERE = 420
    private const val MAX_ROOT_MESSAGE = 120
    private const val DEFAULT_FRAMES = 6
    private const val MAX_CAUSE_HOPS = 8

    private val NOISE_PREFIXES = listOf(
        "kotlinx.coroutines.",
        "kotlin.coroutines.",
        "java.lang.reflect.",
        "jdk.internal."
    )

    private val WHITESPACE = Regex("\\s+")

    /**
     * The exception type, the first few application frames, and the root cause
     * when the exception wraps another one.
     */
    fun stackTop(throwable: Throwable, maxFrames: Int = DEFAULT_FRAMES): String {
        val frames = throwable.stackTrace
            .filterNot { frame -> NOISE_PREFIXES.any { prefix -> frame.className.startsWith(prefix) } }
            .take(maxFrames.coerceAtLeast(1))
            .joinToString(" < ") { frame ->
                "${frame.className.substringAfterLast('.') }.${frame.methodName}(${frame.fileName ?: "?"}:${frame.lineNumber})"
            }
        val root = rootOf(throwable)
        val rootText = if (root !== throwable) {
            " | root: ${typeName(root)}: ${singleLine(root.message.orEmpty(), MAX_ROOT_MESSAGE)}"
        } else {
            ""
        }
        return "${typeName(throwable)} at ${frames.ifEmpty { "no frames" }}$rootText"
    }

    /** A bounded, single-line suffix for a diagnosis. */
    fun describe(event: FailureEvent): String {
        val parts = mutableListOf<String>()
        event.cause?.takeIf { it.isNotBlank() }?.let { parts += "Cause: ${singleLine(it, MAX_CAUSE)}" }
        event.metadata[STACK_TOP_KEY]?.takeIf { it.isNotBlank() }?.let { parts += "Where: ${singleLine(it, MAX_WHERE)}" }
        if (parts.isEmpty()) return ""
        return (" " + parts.joinToString(". ") + ".").take(MAX_LENGTH)
    }

    private fun rootOf(throwable: Throwable): Throwable {
        var current = throwable
        var hops = 0
        while (hops < MAX_CAUSE_HOPS) {
            val next = current.cause ?: break
            if (next === current) break
            current = next
            hops++
        }
        return current
    }

    private fun typeName(throwable: Throwable): String =
        throwable.javaClass.simpleName.ifEmpty { throwable.javaClass.name }

    private fun singleLine(text: String, max: Int): String =
        text.replace(WHITESPACE, " ").trim().take(max)
}
