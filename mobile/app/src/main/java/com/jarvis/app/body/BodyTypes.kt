package com.jarvis.app.body

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The central nervous system state machine for JARVIS's communication body.
 * Single source of truth for all subsystem coordination.
 */
enum class BodyState {
    // Core communication states
    IDLE,
    WAKE,           // Wake word detected, waiting for command
    LISTENING,      // Actively capturing user speech
    HEARING,        // Speech captured, processing/understanding

    // Processing states
    THINKING,       // Model generating response
    RETRIEVING,     // Memory retrieval in progress

    // Output states
    RESPONDING,     // Response generated, preparing to speak
    SPEAKING,       // TTS actively playing

    // Special states
    INTERRUPTED,    // Barge-in detected during SPEAKING
    LEARNING,       // Vocabulary teaching mode
    ERROR,          // Error state (STT/TTS/model failure)

    // Presence states (parallel overlay)
    USER_PRESENT,
    USER_ABSENT
}

/** Events that drive the nervous system. */
sealed interface BodyEvent {
    // Voice pipeline
    data class WakeDetected(val confidence: Float) : BodyEvent
    object SpeechStart : BodyEvent
    data class SpeechEnd(val audio: ShortArray) : BodyEvent
    data class SttResult(val text: String, val confidence: Float, val language: String) : BodyEvent

    // Conversation
    data class UserInput(val text: String) : BodyEvent
    data class ResponseGenerated(val text: String) : BodyEvent
    data class ResponseChunk(val text: String, val isFinal: Boolean) : BodyEvent

    // Memory
    data class MemoryRetrieved(val items: List<MemoryItem>) : BodyEvent

    // TTS
    object TtsStart : BodyEvent
    data class TtsChunk(val audioFile: String) : BodyEvent
    object TtsEnd : BodyEvent

    // Interruption
    data class BargeIn(val userText: String) : BodyEvent

    // Learning
    data class TeachWord(val word: String, val pronunciation: String, val language: String) : BodyEvent
    object WordLearned : BodyEvent

    // Presence
    data class UserPresenceChanged(val present: Boolean) : BodyEvent

    // Lifecycle
    data class ModelLoaded(val provider: String) : BodyEvent
    object ModelUnloaded : BodyEvent
    data class ErrorOccurred(val message: String) : BodyEvent
    object Reset : BodyEvent
}

/** Memory item for retrieval context. */
data class MemoryItem(
    val id: String,
    val type: MemoryType,
    val content: String,
    val timestamp: Long,
    val tags: List<String>,
    val relevance: Float
)

enum class MemoryType {
    FACT,           // User facts
    EPISODIC,       // Episode/conversation memory
    VOCABULARY,     // Learned words
    PREFERENCE,     // User preferences
    CONTEXT         // Conversation context
}

/** Current conversation context. */
data class ConversationContext(
    val currentTurn: Int = 0,
    val recentUserTexts: List<String> = emptyList(),
    val recentJarvisTexts: List<String> = emptyList(),
    val activeTopic: String? = null,
    val pendingMemoryItems: List<MemoryItem> = emptyList(),
    val userLanguage: String = "en",
    val isInterrupted: Boolean = false
)

/** Visual state for the Orb renderer. */
data class VisualState(
    val bodyState: BodyState,
    val emotionValence: Float = 0f,
    val emotionArousal: Float = 0f,
    val speaking: Boolean = false,
    val listening: Boolean = false,
    val thinking: Boolean = false,
    val alertLevel: com.jarvis.app.companioncore.contract.AlertLevel = com.jarvis.app.companioncore.contract.AlertLevel.NONE,
    val userPresent: Boolean = false,
    /** Failure overlay from the nervous system: NONE = healthy. Drives the
     *  orb's warning/critical pulse so a failing body is visible even when the
     *  turn state looks idle. */
    val failureLevel: com.jarvis.app.failure.FailureSeverity = com.jarvis.app.failure.FailureSeverity.INFO
)

/** Body configuration constants. */
object BodyConfig {
    // Timeouts
    const val STT_TIMEOUT_MS = 8000L
    val MODEL_WARMUP_TIMEOUT_MS = 10000L
    val TTS_FIRST_CHUNK_TIMEOUT_MS = 2000L
    val INTERRUPT_WINDOW_MS = 500L
    val IDLE_TIMEOUT_MS = 300000L // 5 min

    // Memory
    const val MAX_CONTEXT_TURNS = 10
    const val MAX_MEMORY_ITEMS = 20
    const val MEMORY_RELEVANCE_THRESHOLD = 0.3f

    // Model lifecycle
    const val MODEL_IDLE_UNLOAD_MS = 120000L // 2 min
    const val MODEL_MAX_CONCURRENT = 1

    // Retry
    const val MAX_STT_RETRIES = 2
    const val MAX_TTS_RETRIES = 1
    const val MAX_MODEL_RETRIES = 1
}

/** Result of a memory query. */
data class MemoryQueryResult(
    val items: List<MemoryItem>,
    val query: String,
    val timestamp: Long
)

/** Vocabulary teaching entry. */
data class VocabularyEntry(
    val word: String,                      // Canonical spelling (user-taught)
    val pronunciation: String,             // IPA or phonetic hint
    val language: String,                  // "en", "ar", "ar-EG", etc.
    val taughtAt: Long,                    // Timestamp when taught
    val usageCount: Int = 0,               // How many times used

    // Enhanced fields for Phase 1
    val normalizedSpelling: String? = null, // Normalized form (lowercase, no diacritics)
    val aliases: List<String> = emptyList(), // Alternative spellings
    val ipa: String? = null,               // IPA transcription
    val confidence: Float = 1.0f,          // Confidence in pronunciation (0-1)
    val userDefined: Boolean = true,       // True if explicitly taught by user
    val source: String = "user",           // Source: "user", "inferred", "correction"
    val correctionCount: Int = 0,          // Number of times user corrected
    val usageExamples: List<String> = emptyList(), // Example sentences
    val lastUsed: Long = 0                 // Last usage timestamp
)

/** TTS segment for streaming. */
data class TtsSegment(
    val text: String,
    val language: String,
    val isFinal: Boolean
)