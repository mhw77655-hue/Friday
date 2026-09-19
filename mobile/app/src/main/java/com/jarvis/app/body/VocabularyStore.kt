package com.jarvis.app.body

import android.content.Context
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.humancore.store.StoreKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import android.util.Log

private const val TAG = "VocabularyStore"
private const val VOCAB_FILE = "body_vocabulary.json"

/**
 * Vocabulary store for user-taught words with pronunciation.
 *
 * User teaches: "Jarvis, the word is X and it's pronounced Y"
 * JARVIS stores: word, pronunciation (IPA/phonetic), language, timestamp
 * Later: TTS uses pronunciation hint when speaking the word.
 */
class VocabularyStore(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val onFailure: (com.jarvis.app.failure.FailureReport) -> Unit = {}
) {

    private val vocabulary = ConcurrentHashMap<String, VocabularyEntry>()
    private val fileStorage = FileStorage(context.filesDir)
    private val _learnedWords = MutableStateFlow<List<VocabularyEntry>>(emptyList())
    val learnedWords: StateFlow<List<VocabularyEntry>> = _learnedWords.asStateFlow()

    init {
        loadFromDisk()
    }

    /** Teach JARVIS a new word with its pronunciation. */
    fun teachWord(word: String, pronunciation: String, language: String): Boolean {
        val normalizedWord = word.trim().lowercase()
        if (normalizedWord.isBlank() || pronunciation.trim().isBlank()) return false

        val now = System.currentTimeMillis()
        val existingEntry = vocabulary[normalizedWord]

        val entry = if (existingEntry != null) {
            // Update existing entry - user corrections have higher authority
            existingEntry.copy(
                pronunciation = pronunciation,
                language = language.lowercase(),
                taughtAt = now,
                usageCount = existingEntry.usageCount + 1,
                userDefined = true,
                source = "user",
                correctionCount = existingEntry.correctionCount + 1,
                lastUsed = now
            )
        } else {
            // New entry
            VocabularyEntry(
                word = normalizedWord,
                pronunciation = pronunciation,
                language = language.lowercase(),
                taughtAt = now,
                usageCount = 1,
                normalizedSpelling = normalizedWord,
                aliases = emptyList(),
                ipa = null,
                confidence = 1.0f,
                userDefined = true,
                source = "user",
                correctionCount = 0,
                usageExamples = emptyList(),
                lastUsed = now
            )
        }

        vocabulary[normalizedWord] = entry
        persistAsync()
        updateFlow()
        return true
    }

    /** Get pronunciation for a word (for TTS). */
    fun getPronunciation(word: String, language: String): String? {
        val normalized = word.trim().lowercase()
        return vocabulary[normalized]?.takeIf { it.language == language.lowercase() }?.pronunciation
    }

    /** Check if a word is in vocabulary. */
    fun knowsWord(word: String, language: String): Boolean {
        val normalized = word.trim().lowercase()
        return vocabulary.containsKey(normalized) && vocabulary[normalized]?.language == language.lowercase()
    }

    /** Increment usage count (for relevance). */
    fun recordUsage(word: String, language: String) {
        val normalized = word.trim().lowercase()
        vocabulary[normalized]?.let { entry ->
            vocabulary[normalized] = entry.copy(
                usageCount = entry.usageCount + 1,
                lastUsed = System.currentTimeMillis()
            )
            persistAsync()
        }
    }

    /** Get all learned words for a language. */
    fun getWordsForLanguage(language: String): List<VocabularyEntry> {
        return vocabulary.values.filter { it.language == language.lowercase() }.toList()
    }

    /** Alias for getWordsForLanguage (for AdaptiveContextBuilder compatibility). */
    fun getEntriesForLanguage(language: String): List<VocabularyEntry> = getWordsForLanguage(language)

    /** Get total vocabulary count. */
    fun getTotalCount(): Int = vocabulary.size

    /** Apply vocabulary to text for TTS (replace words with pronunciation hints). */
    fun applyToText(text: String, language: String): String {
        var result = text
        val words = vocabulary.values.filter { it.language == language.lowercase() }
            .sortedByDescending { it.word.length } // Longer words first to avoid partial replacement

        for (entry in words) {
            // Use word boundaries for replacement
            val pattern = Regex("\\b${Regex.escape(entry.word)}\\b", RegexOption.IGNORE_CASE)
            // For TTS, we can embed pronunciation as SSML phoneme if supported
            // For now, just ensure the word is recognized
            result = pattern.replace(result, entry.word) // Keep original spelling, pronunciation used by TTS engine
        }
        return result
    }

    /**
     * Apply pronunciation hints as SSML phoneme tags for TTS.
     * Wraps known words with <phoneme alphabet="ipa" ph="..."> tags.
     */
    fun applyPronunciationHints(text: String): String {
        var result = text
        val allWords = vocabulary.values
            .sortedByDescending { it.word.length } // Longer words first to avoid partial replacement

        for (entry in allWords) {
            // Use word boundaries for replacement
            val pattern = Regex("\\b${Regex.escape(entry.word)}\\b", RegexOption.IGNORE_CASE)
            // Wrap with SSML phoneme tag for IPA pronunciation
            val phonemeTag = "<phoneme alphabet=\"ipa\" ph=\"${entry.pronunciation}\">${entry.word}</phoneme>"
            result = pattern.replace(result, phonemeTag)
        }
        return result
    }

    private fun updateFlow() {
        _learnedWords.value = vocabulary.values.toList().sortedByDescending { it.taughtAt }
    }

    private fun persistAsync() {
        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject()
                val arr = JSONArray()
                for (entry in vocabulary.values) {
                    arr.put(toJson(entry))
                }
                json.put("vocabulary", arr)
                fileStorage.write(StoreKind.DIALOGUE, json.toString(2)) // Reuse DIALOGUE store
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                android.util.Log.w(TAG, "Persist failed: ${t.message}")
                onFailure(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "VOCABULARY",
                        operation = "persist",
                        severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                        category = com.jarvis.app.failure.FailureCategory.PERSISTENCE,
                        message = "Vocabulary store persistence failed",
                        source = "VocabularyStore",
                        cause = t.message,
                        recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                    )
                )
            }
        }
    }

    private fun loadFromDisk() {
        scope.launch(Dispatchers.IO) {
            try {
                val content = fileStorage.read(StoreKind.DIALOGUE)
                if (content.isNullOrBlank()) return@launch

                val json = JSONObject(content)
                if (json.has("vocabulary")) {
                    val arr = json.getJSONArray("vocabulary")
                    for (i in 0 until arr.length()) {
                        val entry = fromJson(arr.getJSONObject(i))
                        vocabulary[entry.word] = entry
                    }
                }
                updateFlow()
                android.util.Log.i(TAG, "Loaded ${vocabulary.size} vocabulary entries")
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Load failed: ${e.message}")
                onFailure(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "VOCABULARY",
                        operation = "load",
                        severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                        category = com.jarvis.app.failure.FailureCategory.PERSISTENCE,
                        message = "Vocabulary store load failed — started empty",
                        source = "VocabularyStore",
                        cause = e.message,
                        recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                    )
                )
            }
        }
    }

    private fun toJson(entry: VocabularyEntry): JSONObject = JSONObject().apply {
        put("word", entry.word)
        put("pronunciation", entry.pronunciation)
        put("language", entry.language)
        put("taughtAt", entry.taughtAt)
        put("usageCount", entry.usageCount)
        put("normalizedSpelling", entry.normalizedSpelling ?: entry.word)
        put("aliases", JSONArray(entry.aliases))
        put("ipa", entry.ipa ?: "")
        put("confidence", entry.confidence)
        put("userDefined", entry.userDefined)
        put("source", entry.source)
        put("correctionCount", entry.correctionCount)
        put("usageExamples", JSONArray(entry.usageExamples))
        put("lastUsed", entry.lastUsed)
    }

    private fun fromJson(json: JSONObject): VocabularyEntry = VocabularyEntry(
        word = json.getString("word"),
        pronunciation = json.getString("pronunciation"),
        language = json.getString("language"),
        taughtAt = json.getLong("taughtAt"),
        usageCount = json.getInt("usageCount"),
        normalizedSpelling = json.optString("normalizedSpelling", json.getString("word")),
        aliases = json.optJSONArray("aliases")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
        ipa = json.optString("ipa").takeIf { it.isNotBlank() },
        confidence = json.optDouble("confidence", 1.0).toFloat(),
        userDefined = json.optBoolean("userDefined", true),
        source = json.optString("source", "user"),
        correctionCount = json.optInt("correctionCount", 0),
        usageExamples = json.optJSONArray("usageExamples")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
        lastUsed = json.optLong("lastUsed", 0)
    )
}