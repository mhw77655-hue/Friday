package com.jarvis.app.voice

/** A visible semantic target observed on the current Android accessibility tree. */
data class PhoneTargetCandidate(
    val targetKey: String,
    val text: String,
    val contentDescription: String,
    val resourceId: String
)

/**
 * Resolves only exact semantic matches and refuses to guess between distinct
 * clickable controls. Substring matches are intentionally not accepted.
 */
object PhoneTargetResolver {
    sealed interface Result {
        data class Unique(val targetKey: String) : Result
        data class Ambiguous(val count: Int) : Result
        data object Missing : Result
    }

    fun resolve(target: String, candidates: List<PhoneTargetCandidate>): Result {
        val requested = target.trim()
        if (requested.isBlank()) return Result.Missing
        val matches = candidates.asSequence()
            .filter { candidate ->
                candidate.text.equals(requested, ignoreCase = true) ||
                    candidate.contentDescription.equals(requested, ignoreCase = true) ||
                    candidate.resourceId.equals(requested, ignoreCase = true) ||
                    candidate.resourceId.endsWith("/$requested", ignoreCase = true)
            }
            .map { it.targetKey }
            .distinct()
            .toList()
        return when (matches.size) {
            0 -> Result.Missing
            1 -> Result.Unique(matches.single())
            else -> Result.Ambiguous(matches.size)
        }
    }
}
