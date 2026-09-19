package com.jarvis.app.cognitive.memory

import com.jarvis.app.body.MemoryType
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.Recoverability
import com.jarvis.app.humancore.store.StoragePort
import com.jarvis.app.humancore.store.StoreKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * ContinuityPersistence - Minimal persistence adapter for consolidated memories.
 *
 * Reuses the existing [StoragePort] (file-backed today, atomic writes) — it does
 * NOT redesign persistence. A snapshot of all consolidated memories (with
 * provenance, lifecycle state and temporal relationships) is written as one
 * JSON document under [StoreKind.CONSOLIDATED_MEMORY], so continuity survives
 * process restart.
 *
 * Failure handling: corrupt/malformed records become a structured [FailureReport]
 * and the load degrades to "start from empty" — never a crash and never a
 * poisoned store leaking into unrelated cognitive organs.
 */
class ContinuityPersistence(
    private val storage: StoragePort,
    private val onFailure: (FailureReport) -> Unit = {}
) {

    /** Maximum snapshot size guard (protects against runaway growth). */
    companion object {
        const val MAX_MEMORIES = 2000
        const val DOC_CLOCK_FIELD = "updatedAt"
    }

    /** Save a snapshot of all consolidated memories. */
    fun save(memories: List<ConsolidatedMemory>): PersistResult {
        return try {
            val trimmed = memories.take(MAX_MEMORIES)
            val doc = JSONObject()
            doc.put(DOC_CLOCK_FIELD, System.currentTimeMillis())
            doc.put("count", trimmed.size)
            val arr = JSONArray()
            for (mem in trimmed) arr.put(toJson(mem))
            doc.put("memories", arr)

            storage.write(StoreKind.CONSOLIDATED_MEMORY, doc.toString(2))
            PersistResult.SUCCESS(trimmed.size)
        } catch (e: Exception) {
            onFailure(FailureReport(
                subsystem = "cognitive.memory",
                operation = "continuity.save",
                severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.PERSISTENCE,
                message = "Consolidated memory snapshot failed: ${e.message ?: "unknown"}",
                recoverability = Recoverability.RETRYABLE
            ))
            PersistResult.FAILURE(e.message ?: "unknown")
        }
    }

    /** Load all consolidated memories. Returns empty list on corrupt data. */
    fun load(): List<ConsolidatedMemory> {
        return try {
            val content = storage.read(StoreKind.CONSOLIDATED_MEMORY) ?: return emptyList()
            if (content.isBlank()) return emptyList()
            val doc = JSONObject(content)
            val arr = doc.optJSONArray("memories") ?: return emptyList()
            val result = mutableListOf<ConsolidatedMemory>()
            for (i in 0 until arr.length()) {
                try {
                    fromJson(arr.getJSONObject(i))?.let { result.add(it) }
                } catch (e: Exception) {
                    // Skip one malformed record; keep the rest (structured failure).
                    onFailure(FailureReport(
                        subsystem = "cognitive.memory",
                        operation = "continuity.load",
                        severity = FailureSeverity.RECOVERABLE,
                        category = FailureCategory.PERSISTENCE,
                        message = "Skipped malformed consolidated memory #$i: ${e.message ?: "unknown"}",
                        recoverability = Recoverability.RETRYABLE
                    ))
                }
            }
            result
        } catch (e: Exception) {
            onFailure(FailureReport(
                subsystem = "cognitive.memory",
                operation = "continuity.load",
                severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.PERSISTENCE,
                message = "Consolidated memory load failed — started empty: ${e.message ?: "unknown"}",
                recoverability = Recoverability.RETRYABLE
            ))
            emptyList()
        }
    }

    /** Delete the snapshot (explicit erasure). */
    fun delete() {
        storage.delete(StoreKind.CONSOLIDATED_MEMORY)
    }

    // ── Serialization ────────────────────────────────────────────────────

    private fun toJson(mem: ConsolidatedMemory): JSONObject = JSONObject().apply {
        put("memoryId", mem.memoryId)
        put("content", mem.content)
        put("memoryType", mem.memoryType.name)
        put("lifecycleState", mem.lifecycleState.name)
        put("provenance", provenanceToJson(mem.provenance))
        put("temporalRelationships", JSONArray(mem.temporalRelationships.map { relationshipToJson(it) }))
        put("tags", JSONArray(mem.tags))
        put("confidence", mem.confidence.toDouble())
        put("relevance", mem.relevance.toDouble())
        put("goalAlignment", mem.goalAlignment.toDouble())
        put("uncertainty", mem.uncertainty.toDouble())
        put("createdAt", mem.createdAt)
        put("updatedAt", mem.updatedAt)
        put("lastAccessed", mem.lastAccessed)
        put("accessCount", mem.accessCount)
        mem.supersededBy?.let { put("supersededBy", it) }
        mem.supersedes?.let { put("supersedes", it) }
        put("conflictIds", JSONArray(mem.conflictIds))
        put("source", mem.source)
        put("metadata", JSONObject(mem.metadata))
    }

    private fun provenanceToJson(p: MemoryProvenance): JSONObject = JSONObject().apply {
        put("originatingExperienceId", p.originatingExperienceId)
        put("experienceSource", p.experienceSource)
        put("experienceTimestamp", p.experienceTimestamp)
        put("experienceConfidence", p.experienceConfidence.toDouble())
        put("derivationHistory", JSONArray(p.derivationHistory.map { stepToJson(it) }))
        put("currentConfidence", p.currentConfidence.toDouble())
        put("sourceReliability", p.sourceReliability.toDouble())
        put("verified", p.verified)
        put("verifiedAt", p.verifiedAt)
        p.verificationMethod?.let { put("verificationMethod", it) }
        put("provenanceTags", JSONArray(p.provenanceTags))
    }

    private fun stepToJson(s: DerivationStep): JSONObject = JSONObject().apply {
        put("stepType", s.stepType.name)
        put("description", s.description)
        put("confidenceBefore", s.confidenceBefore.toDouble())
        put("confidenceAfter", s.confidenceAfter.toDouble())
        put("timestamp", s.timestamp)
        put("source", s.source)
        put("relatedMemoryIds", JSONArray(s.relatedMemoryIds))
        put("metadata", JSONObject(s.metadata))
    }

    private fun relationshipToJson(r: TemporalRelationship): JSONObject = JSONObject().apply {
        put("relationshipId", r.relationshipId)
        put("fromMemoryId", r.fromMemoryId)
        put("toMemoryId", r.toMemoryId)
        put("relationshipType", r.relationshipType.name)
        put("establishedAt", r.establishedAt)
        put("confidence", r.confidence.toDouble())
        put("evidence", JSONArray(r.evidence))
        r.triggeringExperienceId?.let { put("triggeringExperienceId", it) }
        r.validFrom?.let { put("validFrom", it) }
        r.validUntil?.let { put("validUntil", it) }
    }

    // ── Deserialization ──────────────────────────────────────────────────

    private fun fromJson(json: JSONObject): ConsolidatedMemory? {
        val memoryType = json.optString("memoryType")?.let { safeMemoryType(it) } ?: return null
        val lifecycleState = json.optString("lifecycleState")?.let { safeLifecycleState(it) } ?: MemoryLifecycleState.CONSOLIDATED
        val provenance = json.optJSONObject("provenance")?.let { provenanceFromJson(it) } ?: return null

        return ConsolidatedMemory(
            memoryId = json.getString("memoryId"),
            content = json.getString("content"),
            memoryType = memoryType,
            lifecycleState = lifecycleState,
            provenance = provenance,
            temporalRelationships = json.optJSONArray("temporalRelationships")
                ?.let { arr -> (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { relationshipFromJson(it) } } }
                ?: emptyList(),
            tags = json.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
            confidence = json.optDouble("confidence", 0.8).toFloat(),
            relevance = json.optDouble("relevance", 0.5).toFloat(),
            goalAlignment = json.optDouble("goalAlignment", 0.0).toFloat(),
            uncertainty = json.optDouble("uncertainty", 0.0).toFloat(),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            lastAccessed = json.optLong("lastAccessed", System.currentTimeMillis()),
            accessCount = json.optInt("accessCount", 0),
            supersededBy = if (json.has("supersededBy")) json.getString("supersededBy") else null,
            supersedes = if (json.has("supersedes")) json.getString("supersedes") else null,
            conflictIds = json.optJSONArray("conflictIds")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
            source = json.optString("source", "persistence"),
            metadata = json.optJSONObject("metadata")
                ?.let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } } ?: emptyMap()
        )
    }

    private fun provenanceFromJson(json: JSONObject): MemoryProvenance? {
        return MemoryProvenance(
            originatingExperienceId = json.getString("originatingExperienceId"),
            experienceSource = json.optString("experienceSource", "unknown"),
            experienceTimestamp = json.optLong("experienceTimestamp", 0),
            experienceConfidence = json.optDouble("experienceConfidence", 0.5).toFloat(),
            derivationHistory = json.optJSONArray("derivationHistory")
                ?.let { arr -> (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { stepFromJson(it) } } }
                ?: emptyList(),
            currentConfidence = json.optDouble("currentConfidence", 0.5).toFloat(),
            sourceReliability = json.optDouble("sourceReliability", 0.8).toFloat(),
            verified = json.optBoolean("verified", false),
            verifiedAt = json.optLong("verifiedAt", 0),
            verificationMethod = if (json.has("verificationMethod")) json.getString("verificationMethod") else null,
            provenanceTags = json.optJSONArray("provenanceTags")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
        )
    }

    private fun stepFromJson(json: JSONObject): DerivationStep? {
        return DerivationStep(
            stepType = json.optString("stepType")?.let { safeStepType(it) } ?: return null,
            description = json.optString("description", ""),
            confidenceBefore = json.optDouble("confidenceBefore", 0.0).toFloat(),
            confidenceAfter = json.optDouble("confidenceAfter", 0.0).toFloat(),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            source = json.optString("source", "system"),
            relatedMemoryIds = json.optJSONArray("relatedMemoryIds")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
            metadata = json.optJSONObject("metadata")
                ?.let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } } ?: emptyMap()
        )
    }

    private fun relationshipFromJson(json: JSONObject): TemporalRelationship? {
        return TemporalRelationship(
            relationshipId = json.optString("relationshipId", "rel_restored"),
            fromMemoryId = json.getString("fromMemoryId"),
            toMemoryId = json.getString("toMemoryId"),
            relationshipType = json.optString("relationshipType")?.let { safeRelationshipType(it) } ?: return null,
            establishedAt = json.optLong("establishedAt", System.currentTimeMillis()),
            confidence = json.optDouble("confidence", 1.0).toFloat(),
            evidence = json.optJSONArray("evidence")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
            triggeringExperienceId = if (json.has("triggeringExperienceId")) json.getString("triggeringExperienceId") else null,
            validFrom = if (json.has("validFrom")) json.getLong("validFrom") else null,
            validUntil = if (json.has("validUntil")) json.getLong("validUntil") else null
        )
    }

    // ── Safe enum parsing (malformed → fallback) ─────────────────────────

    private fun safeMemoryType(name: String): MemoryType? =
        MemoryType.values().firstOrNull { it.name == name }

    private fun safeLifecycleState(name: String): MemoryLifecycleState? =
        MemoryLifecycleState.values().firstOrNull { it.name == name }

    private fun safeStepType(name: String): DerivationStepType? =
        DerivationStepType.values().firstOrNull { it.name == name }

    private fun safeRelationshipType(name: String): TemporalRelationshipType? =
        TemporalRelationshipType.values().firstOrNull { it.name == name }

    /** Result types */
    sealed interface PersistResult {
        data class SUCCESS(val savedCount: Int) : PersistResult
        data class FAILURE(val reason: String) : PersistResult
    }
}