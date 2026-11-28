package ai.friday.assistant.cloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * One-shot, task-scoped Claude delegation. The only outbound context is the
 * verified TaskCapsuleCompiler output; this worker has no access to chat history
 * or the memory database and exposes no device-mutation tools to Claude.
 */
class AnthropicTaskWorker(
    private val provider: AnthropicMessagesProvider,
    private val compiler: TaskCapsuleCompiler,
    private val approvals: AnthropicTaskApprovalStore
) {
    fun recordOwnerApproval(
        capsule: TaskCapsuleCompiler.Capsule,
        expiresAtEpochMs: Long = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
    ) {
        require(compiler.verify(capsule)) { "Task capsule digest is invalid." }
        val now = System.currentTimeMillis()
        require(expiresAtEpochMs in (now + 1)..(now + MAX_APPROVAL_LIFETIME_MS)) {
            "Task approval expiry is invalid."
        }
        approvals.approve(capsule.taskId, capsule.digestSha256, expiresAtEpochMs)
    }

    /** A failed request consumes approval; the caller must explicitly approve a retry. */
    fun execute(capsule: TaskCapsuleCompiler.Capsule): CloudResult {
        if (!compiler.verify(capsule)) {
            return CloudResult.Failure(provider.id, "Task capsule integrity verification failed.", false)
        }
        if (!approvals.consume(capsule.taskId, capsule.digestSha256, System.currentTimeMillis())) {
            return CloudResult.Failure(provider.id, "This exact task capsule has not been approved or its one-shot approval was already consumed.", false)
        }
        val prompt = JSONObject()
            .put("task_id", capsule.taskId)
            .put("objective", capsule.objective)
            .put("instructions", capsule.instructions)
            .put("acceptance_criteria", JSONArray(capsule.acceptanceCriteria))
            .put("constraints", JSONArray(capsule.constraints))
            .put("permitted_tool_identifiers", JSONArray(capsule.allowedTools))
            .put("output_schema", capsule.outputSchema)
            .put("selected_context", capsule.context)
            .put("context_sha256", capsule.digestSha256)
            .put("artifacts", JSONArray().also { array ->
                capsule.artifacts.forEach { artifact ->
                    array.put(
                        JSONObject()
                            .put("id", artifact.id)
                            .put("sha256", artifact.sha256)
                            .put("media_type", artifact.mediaType)
                            .put("source_revision", artifact.sourceRevision)
                            .put("label", artifact.label)
                    )
                }
            })
            .put("execution_boundary", "Reasoning only. Do not execute tools, mutate the phone, or modify Friday's local memory.")
        return provider.generate(prompt.toString())
    }

    companion object {
        const val MAX_APPROVAL_LIFETIME_MS = 24 * 60 * 60 * 1000L
    }
}

/** Stores only a task digest and expiry; task content and credentials are never stored here. */
class AnthropicTaskApprovalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "friday_anthropic_task_approvals",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun approve(taskId: String, digest: String, expiresAtEpochMs: Long) {
        require(taskId.isNotBlank() && digest.matches(Regex("[a-fA-F0-9]{64}")))
        check(prefs.edit()
            .putString(digestKey(taskId), digest)
            .putLong(expiryKey(taskId), expiresAtEpochMs)
            .commit()) { "Could not persist the one-shot Claude task approval." }
    }

    @Synchronized
    fun consume(taskId: String, digest: String, now: Long): Boolean {
        val approved = prefs.getString(digestKey(taskId), null)
        val expires = prefs.getLong(expiryKey(taskId), 0L)
        val valid = approved != null && constantTimeEquals(approved, digest) && expires > now
        check(prefs.edit().remove(digestKey(taskId)).remove(expiryKey(taskId)).commit()) {
            "Could not consume the Claude task approval; request was not sent."
        }
        return valid
    }

    private fun digestKey(taskId: String) = "digest_${sha256(taskId)}"
    private fun expiryKey(taskId: String) = "expiry_${sha256(taskId)}"

    private fun constantTimeEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(left.toByteArray(Charsets.US_ASCII), right.toByteArray(Charsets.US_ASCII))

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
