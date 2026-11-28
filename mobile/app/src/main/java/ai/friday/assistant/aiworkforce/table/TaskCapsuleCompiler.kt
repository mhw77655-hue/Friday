package ai.friday.assistant.aiworkforce.table

import java.security.MessageDigest

/**
 * Deterministic, local-only compiler for bounded cloud task context.
 *
 * The compiler accepts already-retrieved evidence, not a conversation or a
 * memory database. It selects only ranked, policy-eligible snippets and emits
 * a content-addressed task capsule. It performs no network I/O and grants no
 * tool authority; the caller must obtain task-scoped owner approval separately.
 */
class TaskCapsuleCompiler {

    enum class Sensitivity { PUBLIC, SANITIZED, PRIVATE, SENSITIVE }

    data class MemoryEvidence(
        val id: String,
        val content: String,
        val source: String,
        val provenanceId: String,
        val observedAtEpochMs: Long,
        val relevance: Double,
        val sensitivity: Sensitivity
    )

    /** References only: artifact bytes are fetched separately through an allowlist. */
    data class ArtifactReference(
        val id: String,
        val sha256: String,
        val mediaType: String,
        val sourceRevision: String,
        val label: String
    )

    data class Request(
        val taskId: String,
        val objective: String,
        val instructions: String,
        val acceptanceCriteria: List<String>,
        val constraints: List<String> = emptyList(),
        val allowedTools: List<String> = emptyList(),
        val outputSchema: String,
        val maxContextChars: Int = DEFAULT_CONTEXT_CHARS,
        val maxMemoryItems: Int = DEFAULT_MEMORY_ITEMS,
        val maxArtifactReferences: Int = DEFAULT_ARTIFACTS,
        /** Must be set only after a task-specific disclosure and owner consent. */
        val privateContextApproved: Boolean = false,
        val minimumRelevance: Double = 0.15
    )

    data class SelectedMemory(
        val id: String,
        val source: String,
        val provenanceId: String,
        val observedAtEpochMs: Long,
        val relevance: Double,
        val sensitivity: Sensitivity,
        val content: String
    )

    private data class ContextSelection(
        val text: String,
        val memories: List<SelectedMemory>,
        val artifacts: List<ArtifactReference>
    )

    data class Capsule(
        val taskId: String,
        val objective: String,
        val instructions: String,
        val acceptanceCriteria: List<String>,
        val constraints: List<String>,
        val allowedTools: List<String>,
        val outputSchema: String,
        val memories: List<SelectedMemory>,
        val artifacts: List<ArtifactReference>,
        val context: String,
        val digestSha256: String
    )

    fun compile(
        request: Request,
        retrievedMemories: List<MemoryEvidence>,
        artifactReferences: List<ArtifactReference> = emptyList()
    ): Capsule {
        validate(request)
        val objective = redactSecrets(request.objective.trim())
        val instructions = redactSecrets(request.instructions.trim())
        val criteria = request.acceptanceCriteria.map { redactSecrets(it.trim()) }
        val constraints = request.constraints.map { redactSecrets(it.trim()) }
        val schema = redactSecrets(request.outputSchema.trim())

        val memories = retrievedMemories.asSequence()
            .filter { it.id.isNotBlank() && it.source.isNotBlank() && it.provenanceId.isNotBlank() }
            .filter { it.content.isNotBlank() && it.relevance.isFinite() && it.relevance in request.minimumRelevance..1.0 }
            .filter {
                when (it.sensitivity) {
                    Sensitivity.PUBLIC, Sensitivity.SANITIZED -> true
                    Sensitivity.PRIVATE -> request.privateContextApproved
                    Sensitivity.SENSITIVE -> false
                }
            }
            .mapNotNull { evidence ->
                val safeContent = redactSecrets(evidence.content.trim())
                if (safeContent.isBlank()) null else SelectedMemory(
                    id = evidence.id.take(96),
                    source = evidence.source.take(96),
                    provenanceId = evidence.provenanceId.take(128),
                    observedAtEpochMs = evidence.observedAtEpochMs,
                    relevance = evidence.relevance,
                    sensitivity = evidence.sensitivity,
                    content = safeContent
                )
            }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<SelectedMemory> { it.relevance }.thenByDescending { it.observedAtEpochMs })
            .take(request.maxMemoryItems)
            .toList()

        val artifacts = artifactReferences.asSequence()
            .filter { it.id.isNotBlank() && it.sha256.matches(SHA256) }
            .filter { it.mediaType.isNotBlank() && it.sourceRevision.isNotBlank() && it.label.isNotBlank() }
            .distinctBy { it.id }
            .take(request.maxArtifactReferences)
            .map {
                it.copy(
                    id = it.id.take(128),
                    mediaType = it.mediaType.take(128),
                    sourceRevision = it.sourceRevision.take(160),
                    label = redactSecrets(it.label.take(240))
                )
            }
            .toList()

        val selected = buildContext(memories, artifacts, request.maxContextChars)
        val context = selected.text
        val canonical = buildString {
            field(request.taskId)
            field(objective)
            field(instructions)
            list(criteria)
            list(constraints)
            list(request.allowedTools)
            field(schema)
            field(context)
        }
        return Capsule(
            taskId = request.taskId,
            objective = objective,
            instructions = instructions,
            acceptanceCriteria = criteria,
            constraints = constraints,
            allowedTools = request.allowedTools,
            outputSchema = schema,
            memories = selected.memories,
            artifacts = selected.artifacts,
            context = context,
            digestSha256 = sha256(canonical)
        )
    }

    private fun buildContext(
        memories: List<SelectedMemory>,
        artifacts: List<ArtifactReference>,
        maxChars: Int
    ): ContextSelection {
        val output = StringBuilder()
        val includedMemories = mutableListOf<SelectedMemory>()
        val includedArtifacts = mutableListOf<ArtifactReference>()
        for (memory in memories) {
            val block = buildString {
                append("[MEMORY id=").append(memory.id)
                append(" source=").append(memory.source)
                append(" provenance=").append(memory.provenanceId)
                append(" observed_at=").append(memory.observedAtEpochMs)
                append(" relevance=").append("%.3f".format(java.util.Locale.ROOT, memory.relevance))
                append(" sensitivity=").append(memory.sensitivity.name).append("]\n")
                append(memory.content).append("\n[/MEMORY]\n")
            }
            if (output.length + block.length > maxChars) continue
            output.append(block)
            includedMemories.add(memory)
        }
        for (artifact in artifacts) {
            val block = "[ARTIFACT id=${artifact.id} sha256=${artifact.sha256} media_type=${artifact.mediaType} revision=${artifact.sourceRevision}] ${artifact.label}\n"
            if (output.length + block.length > maxChars) continue
            output.append(block)
            includedArtifacts.add(artifact)
        }
        return ContextSelection(output.toString().trim(), includedMemories, includedArtifacts)
    }

    private fun validate(request: Request) {
        require(request.taskId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{2,95}"))) { "Invalid task ID." }
        require(request.objective.isNotBlank() && request.objective.length <= 1_000) { "Invalid task objective." }
        require(request.instructions.isNotBlank() && request.instructions.length <= 4_000) { "Invalid task instructions." }
        require(request.outputSchema.isNotBlank() && request.outputSchema.length <= 4_000) { "Invalid output schema." }
        require(request.acceptanceCriteria.isNotEmpty() && request.acceptanceCriteria.size <= 12 &&
            request.acceptanceCriteria.all { it.isNotBlank() && it.length <= 500 }) { "Invalid acceptance criteria." }
        require(request.constraints.size <= 16 && request.constraints.all { it.isNotBlank() && it.length <= 500 }) { "Invalid constraints." }
        require(request.allowedTools.size <= 16 && request.allowedTools.all { it.matches(TOOL_ID) }) { "Invalid tool allowlist." }
        require(request.maxContextChars in 256..MAX_CONTEXT_CHARS) { "Invalid context budget." }
        require(request.minimumRelevance.isFinite() && request.minimumRelevance in 0.0..1.0) { "Invalid relevance threshold." }
        require(request.maxMemoryItems in 0..MAX_MEMORY_ITEMS) { "Invalid memory item budget." }
        require(request.maxArtifactReferences in 0..MAX_ARTIFACTS) { "Invalid artifact reference budget." }
    }

    private fun redactSecrets(value: String): String {
        var safe = value
        safe = safe.replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*"), "[REDACTED_BEARER_TOKEN]")
        safe = safe.replace(Regex("(?i)(api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|passwd|secret)\\s*[:=]\\s*[^\\s,;]+"),
            "$1=[REDACTED]")
        safe = safe.replace(Regex("\\b(sk-[A-Za-z0-9_-]{12,}|gh[pousr]_[A-Za-z0-9_]{12,}|github_pat_[A-Za-z0-9_]{12,})\\b"),
            "[REDACTED_CREDENTIAL]")
        return safe
    }

    private fun StringBuilder.field(value: String) {
        append(value.length).append(':').append(value)
    }

    private fun StringBuilder.list(values: List<String>) {
        append(values.size).append(':')
        values.forEach(::field)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val DEFAULT_CONTEXT_CHARS = 12_000
        const val MAX_CONTEXT_CHARS = 24_000
        const val DEFAULT_MEMORY_ITEMS = 8
        const val MAX_MEMORY_ITEMS = 24
        const val DEFAULT_ARTIFACTS = 8
        const val MAX_ARTIFACTS = 16
        private val SHA256 = Regex("[a-fA-F0-9]{64}")
        private val TOOL_ID = Regex("[a-z][a-z0-9_.-]{1,63}")
    }
}
