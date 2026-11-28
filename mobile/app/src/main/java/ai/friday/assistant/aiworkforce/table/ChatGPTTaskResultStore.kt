package ai.friday.assistant.aiworkforce.table

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import ai.friday.assistant.humancore.store.FileStorage
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Crash-safe, encrypted journal for private ChatGPT task work.
 *
 * Progress is checkpointed locally while streaming. A completed result is
 * committed before execute() returns, so a process restart can recover it
 * without spending another ChatGPT-plan request. Partial checkpoints are never
 * misrepresented as completed results.
 */
class ChatGPTTaskResultStore(context: Context) {
    enum class State { RUNNING, INCOMPLETE, COMPLETED }

    data class Checkpoint(
        val taskId: String,
        val requestId: String,
        val model: String,
        val contextDigest: String,
        val output: String,
        val responseId: String?,
        val state: State,
        val updatedAtEpochMs: Long
    ) {
        init {
            require(taskId.isNotBlank() && taskId.length <= 96)
            require(requestId.isNotBlank() && requestId.length <= 80)
            require(model.isNotBlank() && model.length <= 120)
            require(contextDigest.matches(Regex("[0-9a-f]{64}")))
            require(output.length <= ChatGPTTaskWorker.MAX_OUTPUT_CHARS)
            require(updatedAtEpochMs > 0L)
            if (state == State.COMPLETED) {
                require(!responseId.isNullOrBlank() && output.isNotBlank())
            }
        }

        fun toTaskResult(): ChatGPTTaskWorker.TaskResult {
            require(state == State.COMPLETED) { "Only a completed checkpoint can be returned as a task result." }
            return ChatGPTTaskWorker.TaskResult(
                taskId = taskId,
                responseId = requireNotNull(responseId),
                requestId = requestId,
                model = model,
                output = output,
                contextDigest = contextDigest,
                completedAtEpochMs = updatedAtEpochMs
            )
        }

        fun toJson(): JSONObject = JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("task_id", taskId)
            .put("request_id", requestId)
            .put("model", model)
            .put("context_digest", contextDigest)
            .put("output", output)
            .put("response_id", responseId ?: JSONObject.NULL)
            .put("state", state.name)
            .put("updated_at_epoch_ms", updatedAtEpochMs)

        companion object {
            private const val SCHEMA_VERSION = 1

            fun fromJson(json: JSONObject): Checkpoint {
                require(json.optInt("schema_version", -1) == SCHEMA_VERSION) {
                    "Unsupported ChatGPT task journal schema."
                }
                val state = runCatching { State.valueOf(json.getString("state")) }
                    .getOrElse { throw IllegalArgumentException("Unknown ChatGPT task journal state.") }
                return Checkpoint(
                    taskId = json.getString("task_id"),
                    requestId = json.getString("request_id"),
                    model = json.getString("model"),
                    contextDigest = json.getString("context_digest"),
                    output = json.getString("output"),
                    responseId = json.optString("response_id").takeIf { it.isNotBlank() && it != "null" },
                    state = state,
                    updatedAtEpochMs = json.getLong("updated_at_epoch_ms")
                )
            }
        }
    }

    private val root = File(context.applicationContext.filesDir, "friday-development/chatgpt-task-results")
    private val storage = FileStorage(context.applicationContext.filesDir)

    fun save(checkpoint: Checkpoint) = synchronized(lockFor(checkpoint.taskId)) {
        val file = fileFor(checkpoint.taskId)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(checkpoint.toJson().toString().toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + ciphertext
        storage.write(file, Base64.encodeToString(packed, Base64.NO_WRAP))
    }

    fun checkpoint(taskId: String, contextDigest: String): Checkpoint? =
        synchronized(lockFor(taskId)) {
            val stored = readLocked(taskId) ?: return@synchronized null
            stored.takeIf { it.contextDigest == contextDigest }
        }

    fun latestCheckpoint(taskId: String): Checkpoint? =
        synchronized(lockFor(taskId)) { readLocked(taskId) }

    fun completed(taskId: String, contextDigest: String): ChatGPTTaskWorker.TaskResult? =
        checkpoint(taskId, contextDigest)
            ?.takeIf { it.state == State.COMPLETED }
            ?.toTaskResult()

    fun clear(taskId: String) = synchronized(lockFor(taskId)) {
        fileFor(taskId).delete()
    }

    private fun readLocked(taskId: String): Checkpoint? {
        val file = fileFor(taskId)
        if (!file.isFile) return null
        try {
            val packed = Base64.decode(file.readText(Charsets.UTF_8), Base64.NO_WRAP)
            require(packed.size > GCM_IV_BYTES) { "Encrypted ChatGPT task journal is truncated." }
            val iv = packed.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = packed.copyOfRange(GCM_IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv)
            )
            val json = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
            val checkpoint = Checkpoint.fromJson(json)
            require(checkpoint.taskId == taskId) { "ChatGPT task journal identity mismatch." }
            return checkpoint
        } catch (failure: Exception) {
            throw IOException(
                "Friday's encrypted ChatGPT task journal is damaged; it was preserved and not overwritten.",
                failure
            )
        }
    }

    private fun fileFor(taskId: String): File {
        require(taskId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{2,95}"))) {
            "Invalid ChatGPT task ID for result journal."
        }
        return File(root, sha256(taskId) + ".enc")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "friday.chatgpt.task.results"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()

        private fun lockFor(taskId: String): Any = locks.computeIfAbsent(sha256(taskId)) { Any() }

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
