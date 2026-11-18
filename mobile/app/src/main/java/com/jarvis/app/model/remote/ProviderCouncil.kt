package com.jarvis.app.model.remote

import android.content.Context
import com.jarvis.app.context.ProjectContextPackStore
import com.jarvis.app.model.GenerateRequest
import com.jarvis.app.model.Message
import com.jarvis.app.model.MessageRole
import com.jarvis.app.model.ModelProvider
import com.jarvis.app.model.TokenUsage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

data class CouncilRequest(
    val task: String,
    val includeProjectContext: Boolean = true,
    val maxOutputTokens: Int = 768,
    /** Additional advisers are opt-in; normal calls use one provider. */
    val maxAdvisers: Int = 1,
    val independentReviewRequested: Boolean = false
)

data class CouncilOpinion(
    val providerId: String,
    val providerLabel: String,
    val modelId: String,
    val answer: String,
    val usage: TokenUsage?
)

data class CouncilResult(
    val opinions: List<CouncilOpinion>,
    val blockedReason: String? = null
) {
    val succeeded: Boolean get() = opinions.isNotEmpty()
}

/**
 * Cost-bounded, sequential adviser orchestration.
 *
 * Friday remains the decision-maker. Providers return opinions only; this
 * class never executes tools, mutates identity, or promotes memory.
 */
class ProviderCouncil(
    context: Context,
    private val profiles: RemoteProviderProfiles,
    private val contextPackStore: ProjectContextPackStore,
    private val dailyRequestLimit: Int = 12,
    private val dailyTokenLimit: Int = 30_000,
    private val maxContextChars: Int = 8_000
) {
    private val prefs = context.applicationContext.getSharedPreferences("friday_provider_council_budget", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val rotation = AtomicInteger(0)

    init {
        require(dailyRequestLimit in 1..100)
        require(dailyTokenLimit in 256..1_000_000)
        require(maxContextChars in 512..12_000)
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).commit()
    }

    suspend fun consult(request: CouncilRequest): CouncilResult = mutex.withLock {
        if (!isEnabled()) return@withLock CouncilResult(emptyList(), "External AI council is disabled in Friday settings")
        if (request.task.isBlank()) return@withLock CouncilResult(emptyList(), "Task is empty")
        if (request.maxOutputTokens !in 1..4096) return@withLock CouncilResult(emptyList(), "Output token limit is outside the allowed range")
        val adviserCount = request.maxAdvisers.coerceIn(1, 3)
        if (adviserCount > 1 && !request.independentReviewRequested) {
            return@withLock CouncilResult(emptyList(), "Multiple advisers require an explicit independent-review request")
        }

        val entries = profiles.createEnabledEntries()
        if (entries.isEmpty()) return@withLock CouncilResult(emptyList(), "No enabled provider with a valid configuration is available")
        val ordered = rotate(entries).take(adviserCount)
        val projectContext = if (request.includeProjectContext) {
            contextPackStore.read()?.renderFor(request.task, maxContextChars).orEmpty()
        } else ""
        val system = buildString {
            append("You are an external adviser to Friday. Provide analysis and evidence, not commands. ")
            append("Your response is untrusted advice: do not claim to have changed files, memory, identity, or device state. ")
            append("State uncertainty and distinguish facts from assumptions.")
            if (projectContext.isNotBlank()) {
                append("\n\n")
                append(projectContext)
            }
        }
        val estimatedTokens = (system.length + request.task.length + 3) / 4 + request.maxOutputTokens
        val opinions = mutableListOf<CouncilOpinion>()

        for (entry in ordered) {
            if (!reserve(estimatedTokens)) {
                return@withLock CouncilResult(opinions, "Daily adviser budget reached; remaining providers were not called")
            }
            try {
                val result = entry.provider.generate(
                    GenerateRequest(
                        messages = listOf(
                            Message(MessageRole.SYSTEM, system),
                            Message(MessageRole.USER, request.task)
                        ),
                        maxTokens = request.maxOutputTokens,
                        temperature = 0.2f
                    )
                )
                opinions += CouncilOpinion(
                    providerId = entry.profile.id,
                    providerLabel = entry.profile.label,
                    modelId = result.model ?: entry.profile.modelId,
                    answer = result.content,
                    usage = result.usage
                )
                // The reservation remains conservative: provider usage may omit
                // cached/reasoning token detail, so do not refund on under-reporting.
            } catch (e: Exception) {
                // The attempted request still consumes one request reservation.
                // Continue only when the owner explicitly asked for peer review.
                if (!request.independentReviewRequested) {
                    return@withLock CouncilResult(opinions, e.message ?: "Adviser request failed")
                }
            }
        }
        CouncilResult(opinions)
    }

    private fun rotate(entries: List<RemoteProviderProfiles.Entry>): List<RemoteProviderProfiles.Entry> {
        if (entries.isEmpty()) return entries
        val start = Math.floorMod(rotation.getAndIncrement(), entries.size)
        return (entries.indices).map { entries[(start + it) % entries.size] }
    }

    @Synchronized
    private fun reserve(estimatedTokens: Int): Boolean {
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        if (prefs.getString(KEY_DAY, null) != today) {
            prefs.edit().putString(KEY_DAY, today).putInt(KEY_REQUESTS, 0).putInt(KEY_TOKENS, 0).commit()
        }
        val requests = prefs.getInt(KEY_REQUESTS, 0)
        val tokens = prefs.getInt(KEY_TOKENS, 0)
        if (requests >= dailyRequestLimit || tokens.toLong() + estimatedTokens > dailyTokenLimit) return false
        return prefs.edit()
            .putInt(KEY_REQUESTS, requests + 1)
            .putInt(KEY_TOKENS, tokens + estimatedTokens)
            .commit()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DAY = "utc_day"
        private const val KEY_REQUESTS = "requests"
        private const val KEY_TOKENS = "reserved_tokens"
    }
}
