package com.jarvis.app.session

import android.content.Context
import com.jarvis.app.humancore.store.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

/**
 * SessionManager - manages conversation sessions locally.
 * Persists to filesDir/sessions/sessions.jsonl
 * Each session has messages, metadata, and environment context.
 */
class SessionManager(
    private val context: Context,
    private val fileStorage: FileStorage,
    private val scope: CoroutineScope
) {
    private val sessionsDir = fileStorage.baseDir.resolve("sessions")
    private val sessionsFile = sessionsDir.resolve("sessions.jsonl")
    private val activeSessionFile = sessionsDir.resolve("active.json")
    private val idCounter = AtomicLong(System.currentTimeMillis())

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession = _activeSession.asStateFlow()

    init {
        sessionsDir.mkdirs()
        scope.launch { loadSessions() }
    }

    /** Start a new session */
    suspend fun startSession(
        environmentId: String,
        initialMessage: String? = null
    ): Session {
        val session = Session(
            id = "session-${idCounter.incrementAndGet()}",
            environmentId = environmentId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messageCount = 0,
            metadata = mutableMapOf(),
            messages = initialMessage?.let {
                listOf(SessionMessage(
                    id = "msg-${idCounter.incrementAndGet()}",
                    role = MessageRole.USER,
                    content = it,
                    timestamp = System.currentTimeMillis()
                ))
            } ?: emptyList()
        )
        addSession(session)
        setActiveSession(session)
        return session
    }

    /** Get session by ID */
    suspend fun getSession(id: String): Session? = withContext(Dispatchers.IO) {
        _sessions.value.find { it.id == id }
    }

    /** Set active session */
    suspend fun setActiveSession(session: Session) = withContext(Dispatchers.IO) {
        _activeSession.value = session
        persistActiveSessionId(session.id)
    }

    /** Set active session by ID */
    suspend fun setActiveSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val session = _sessions.value.find { it.id == sessionId }
            ?: return@withContext Result.failure(IllegalArgumentException("Session not found"))
        setActiveSession(session)
        Result.success(Unit)
    }

    /** Add a message to the active session */
    suspend fun addMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        metadata: Map<String, String> = emptyMap()
    ): Result<SessionMessage> = withContext(Dispatchers.IO) {
        val session = _sessions.value.find { it.id == sessionId }
            ?: return@withContext Result.failure(IllegalArgumentException("Session not found"))

        val message = SessionMessage(
            id = "msg-${idCounter.incrementAndGet()}",
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            metadata = metadata
        )

        val updatedSession = session.copy(
            messages = session.messages + message,
            messageCount = session.messageCount + 1,
            updatedAt = System.currentTimeMillis()
        )
        updateSession(updatedSession)

        if (_activeSession.value?.id == sessionId) {
            _activeSession.value = updatedSession
        }

        Result.success(message)
    }

    /** End the active session */
    suspend fun endSession(): Result<Unit> = withContext(Dispatchers.IO) {
        _activeSession.value?.let { session ->
            val updated = session.copy(
                updatedAt = System.currentTimeMillis()
            )
            updateSession(updated)
        }
        _activeSession.value = null
        persistActiveSessionId(null)
        Result.success(Unit)
    }

    /** Delete a session */
    suspend fun deleteSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val session = _sessions.value.find { it.id == sessionId }
            ?: return@withContext Result.failure(IllegalArgumentException("Session not found"))
        val updated = _sessions.value.filter { it.id != sessionId }
        _sessions.value = updated
        if (_activeSession.value?.id == sessionId) {
            _activeSession.value = null
            persistActiveSessionId(null)
        }
        rewriteAllSessions()
        Result.success(Unit)
    }

    /** List sessions for an environment */
    suspend fun listSessions(environmentId: String? = null): List<Session> = withContext(Dispatchers.IO) {
        _sessions.value.filter { environmentId == null || it.environmentId == environmentId }
            .sortedByDescending { it.updatedAt }
    }

    private suspend fun addSession(session: Session) = withContext(Dispatchers.IO) {
        val current = _sessions.value
        _sessions.value = current + session
        persistSession(session)
    }

    private suspend fun updateSession(session: Session) = withContext(Dispatchers.IO) {
        val current = _sessions.value
        val index = current.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            val updated = current.toMutableList().apply { set(index, session) }
            _sessions.value = updated
            rewriteAllSessions()
        }
    }

    private suspend fun loadSessions() = withContext(Dispatchers.IO) {
        if (!sessionsFile.exists()) return@withContext
        val sessions = mutableListOf<Session>()
        sessionsFile.readLines().forEach { line ->
            if (line.isNotBlank()) {
                try {
                    sessions.add(Session.fromJson(JSONObject(line)))
                } catch (e: Exception) {
                    // Skip corrupted
                }
            }
        }
        _sessions.value = sessions

        // Load active session
        if (activeSessionFile.exists()) {
            val activeId = activeSessionFile.readText().trim()
            _activeSession.value = sessions.find { it.id == activeId }
        }
    }

    private fun persistSession(session: Session) {
        fileStorage.append(sessionsFile, session.toJson().toString())
    }

    private fun rewriteAllSessions() {
        val content = _sessions.value.joinToString("\n") { it.toJson().toString() } + "\n"
        fileStorage.write(sessionsFile, content)
    }

    private fun persistActiveSessionId(sessionId: String?) {
        fileStorage.write(activeSessionFile, sessionId ?: "")
    }

    /** UI-friendly observe method returning LiveData of all sessions */
    fun observeSessionsForUI(): LiveData<List<Session>> {
        return sessions.asLiveData(Dispatchers.Main)
    }

    /** UI-friendly observe method returning LiveData of active session */
    fun observeActiveSessionForUI(): LiveData<Session?> {
        return activeSession.asLiveData(Dispatchers.Main)
    }

    companion object {
        fun create(context: Context = android.app.Application()): SessionManager = SessionManager(
            context = context,
            fileStorage = FileStorage(context.filesDir),
            scope = CoroutineScope(Dispatchers.Main)
        )
    }
}

/** Conversation session */
data class Session(
    val id: String,
    val environmentId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
    val metadata: Map<String, String>,
    val messages: List<SessionMessage>
) {
    /** Manual JSON round-trip (kotlinx-serialization is not in the build). */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("environmentId", environmentId)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("messageCount", messageCount)
        put("metadata", JSONObject(metadata))
        put("messages", org.json.JSONArray(messages.map { it.toJson() }))
    }

    companion object {
        fun fromJson(o: JSONObject): Session = Session(
            id = o.getString("id"),
            environmentId = o.optString("environmentId"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            messageCount = o.optInt("messageCount"),
            metadata = o.optJSONObject("metadata")?.let { jo ->
                buildMap {
                    jo.keys().forEach { k -> this[k] = jo.optString(k) }
                }
            } ?: emptyMap(),
            messages = o.optJSONArray("messages")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { SessionMessage.fromJson(arr.getJSONObject(i)) }.getOrNull()
                }
            } ?: emptyList()
        )
    }
}

/** Session message */
data class SessionMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap()
) {
    /** Manual JSON round-trip (kotlinx-serialization is not in the build). */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role.name)
        put("content", content)
        put("timestamp", timestamp)
        put("metadata", JSONObject(metadata))
    }

    companion object {
        fun fromJson(o: JSONObject): SessionMessage = SessionMessage(
            id = o.getString("id"),
            role = runCatching { MessageRole.valueOf(o.getString("role")) }
                .getOrDefault(MessageRole.USER),
            content = o.optString("content"),
            timestamp = o.optLong("timestamp"),
            metadata = o.optJSONObject("metadata")?.let { jo ->
                buildMap {
                    jo.keys().forEach { k -> this[k] = jo.optString(k) }
                }
            } ?: emptyMap()
        )
    }
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}
