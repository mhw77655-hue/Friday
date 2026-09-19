package com.jarvis.app.model.adapters

import com.jarvis.app.model.GenerateRequest
import com.jarvis.app.model.GenerateResult
import com.jarvis.app.model.LoadRequest
import com.jarvis.app.model.LoadResult
import com.jarvis.app.model.ModelInfo
import com.jarvis.app.model.ModelProvider
import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.model.ProviderHealth
import com.jarvis.app.model.StreamEvent
import com.jarvis.app.model.TokenUsage
import com.jarvis.app.model.UnloadRequest
import com.jarvis.app.model.UnloadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * HeuristicAdapter - offline fallback using deterministic keyword-based responses.
 * Used when no model is available or in offline/safe modes.
 * Based on the existing HeuristicModelPort in ModelPort.kt
 */
class HeuristicAdapter : ModelProvider {
    override val providerType = ModelProviderType.HEURISTIC

    override suspend fun generate(request: GenerateRequest): GenerateResult = withContext(Dispatchers.IO) {
        val lastUserMessage = request.messages.lastOrNull { it.role == com.jarvis.app.model.MessageRole.USER }?.content ?: ""
        val response = generateHeuristicResponse(lastUserMessage)
        GenerateResult(
            content = response,
            finishReason = com.jarvis.app.model.FinishReason.STOP,
            usage = TokenUsage(0, 0, 0)
        )
    }

    override fun stream(request: GenerateRequest) = flow {
        val lastUserMessage = request.messages.lastOrNull { it.role == com.jarvis.app.model.MessageRole.USER }?.content ?: ""
        val response = generateHeuristicResponse(lastUserMessage)

        // Simulate streaming by emitting word by word
        var accumulated = ""
        response.split(" ").forEach { word ->
            accumulated += word + " "
            emit(StreamEvent.Token(word + " ", accumulated.trim()))
            kotlinx.coroutines.delay(50) // Small delay for effect
        }
        emit(StreamEvent.Done(TokenUsage(0, 0, 0)))
    }.flowOn(Dispatchers.IO)

    override suspend fun health(): ProviderHealth = withContext(Dispatchers.IO) {
        ProviderHealth(
            isHealthy = true,
            latencyMs = 0,
            modelLoaded = true,
            currentModel = "heuristic-fallback",
            error = null
        )
    }

    override suspend fun load(request: LoadRequest): LoadResult = withContext(Dispatchers.IO) {
        LoadResult(true, request.modelId)
    }

    override suspend fun unload(request: UnloadRequest): UnloadResult = withContext(Dispatchers.IO) {
        UnloadResult(true, request.modelId)
    }

    override suspend fun modelInfo(): ModelInfo = withContext(Dispatchers.IO) {
        ModelInfo(
            id = "heuristic-fallback",
            name = "Heuristic Fallback (Offline)",
            providerType = providerType,
            contextWindow = 2048,
            maxOutputTokens = 512,
            supportsStreaming = true,
            supportsTools = false,
            supportsVision = false
        )
    }

    override suspend fun tokenUsage(): TokenUsage = withContext(Dispatchers.IO) {
        TokenUsage(0, 0, 0)
    }

    private fun generateHeuristicResponse(input: String): String {
        val lower = input.lowercase()
        return when {
            lower.contains("status") -> "System status: All systems nominal. HumanCore active. Voice stack: Vosk + Sherpa + TTS. CompanionCore: Presence engine running."
            lower.contains("hello") || lower.contains("hi ") || lower == "hi" -> "Hello. I'm JARVIS, running in heuristic mode (offline fallback). How can I help?"
            lower.contains("who are you") || lower.contains("what are you") -> "I am JARVIS, an AI assistant. Currently running in heuristic offline mode with no model backend connected."
            lower.contains("help") -> "Available: status, help, time, date. In heuristic mode, I can only respond to known patterns."
            lower.contains("time") -> "Current time: ${java.time.LocalTime.now()}"
            lower.contains("date") -> "Today's date: ${java.time.LocalDate.now()}"
            lower.contains("model") -> "Current model: Heuristic Fallback (offline). No LLM backend connected."
            lower.contains("environment") -> "Current environment: Offline Persona (heuristic mode)."
            else -> "I'm running in heuristic offline mode. I can answer: status, help, time, date. For full conversation, connect a model backend."
        }
    }
}