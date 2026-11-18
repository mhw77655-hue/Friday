package com.jarvis.app.cognitive.capability

import com.jarvis.app.humancore.store.FileStorage
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** A recorded observation of a capability invocation. */
data class CapabilityEvidence(
    val capabilityId: String,
    val operation: String,
    val outcome: EvidenceOutcome,
    val observedAtMs: Long,
    val source: String,
    val verified: Boolean,
    val detail: String = ""
)

enum class EvidenceOutcome { SUCCEEDED, FAILED, REFUSED, BLOCKED, SKIPPED }

/** Conservative summary; evidence is not itself permission to execute anything. */
data class CapabilityKnowledge(
    val capabilityId: String,
    val observations: Int,
    val verifiedSuccesses: Int,
    val failures: Int,
    val lastObservedAtMs: Long,
    val reusable: Boolean
)

sealed interface CapabilityEvidenceLoad {
    data object Empty : CapabilityEvidenceLoad
    data class Loaded(val evidence: List<CapabilityEvidence>) : CapabilityEvidenceLoad
    data class Corrupt(val reason: String) : CapabilityEvidenceLoad
}

/**
 * Model-independent evidence ledger for capability learning.
 *
 * Only verified successes contribute toward reusable strategy confidence.
 * Refusals, blocked and skipped actions remain visible but are never counted
 * as failures or successes. The ledger never grants permissions or invokes code.
 */
class CapabilityEvidenceLedger(
    directory: File,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val file = File(directory, FILE_NAME)
    private val storage = FileStorage(directory)

    @Synchronized
    fun load(): CapabilityEvidenceLoad {
        if (!file.isFile) return CapabilityEvidenceLoad.Empty
        if (file.length() > MAX_FILE_BYTES) {
            return CapabilityEvidenceLoad.Corrupt("Capability evidence exceeds the safe size limit.")
        }
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            require(root.getInt("schema_version") == SCHEMA_VERSION) { "Unsupported capability evidence schema." }
            val array = root.getJSONArray("evidence")
            require(array.length() <= MAX_ENTRIES) { "Too many capability evidence entries." }
            val values = (0 until array.length()).map { parse(array.getJSONObject(it)) }
            CapabilityEvidenceLoad.Loaded(values)
        } catch (failure: Exception) {
            CapabilityEvidenceLoad.Corrupt(failure.message?.take(240) ?: "Malformed capability evidence.")
        }
    }

    @Synchronized
    fun record(evidence: CapabilityEvidence) {
        validate(evidence)
        val current = when (val loaded = load()) {
            CapabilityEvidenceLoad.Empty -> emptyList()
            is CapabilityEvidenceLoad.Loaded -> loaded.evidence
            is CapabilityEvidenceLoad.Corrupt -> throw IllegalStateException(
                "Refusing to overwrite corrupt capability evidence: ${loaded.reason}"
            )
        }
        val next = (current + evidence).takeLast(MAX_ENTRIES)
        val root = JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("updated_at_ms", nowMs())
            .put("evidence", JSONArray().apply { next.forEach { put(serialize(it)) } })
        val encoded = root.toString()
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) {
            "Capability evidence exceeds the safe storage limit."
        }
        storage.write(file, encoded)
    }

    @Synchronized
    fun knowledge(): List<CapabilityKnowledge> {
        val evidence = when (val loaded = load()) {
            CapabilityEvidenceLoad.Empty -> emptyList()
            is CapabilityEvidenceLoad.Loaded -> loaded.evidence
            is CapabilityEvidenceLoad.Corrupt -> return emptyList()
        }
        return evidence.groupBy { it.capabilityId }.map { (id, rows) ->
            val failures = rows.count { it.outcome == EvidenceOutcome.FAILED }
            // A later verified recovery can rehabilitate a capability. Count
            // only verified successes recorded after the most recent failure.
            val lastFailureIndex = rows.indexOfLast { it.outcome == EvidenceOutcome.FAILED }
            val verifiedSuccesses = rows.drop(lastFailureIndex + 1).count {
                it.outcome == EvidenceOutcome.SUCCEEDED && it.verified
            }
            CapabilityKnowledge(
                capabilityId = id,
                observations = rows.size,
                verifiedSuccesses = verifiedSuccesses,
                failures = failures,
                lastObservedAtMs = rows.maxOf { it.observedAtMs },
                reusable = verifiedSuccesses >= MIN_VERIFIED_SUCCESSES
            )
        }.sortedBy { it.capabilityId }
    }

    private fun validate(value: CapabilityEvidence) {
        require(value.capabilityId.isNotBlank() && value.capabilityId.length <= 128)
        require(value.operation.isNotBlank() && value.operation.length <= 128)
        require(value.source.isNotBlank() && value.source.length <= 128)
        require(value.detail.length <= 1_000)
        require(value.observedAtMs >= 0)
    }

    private fun serialize(value: CapabilityEvidence): JSONObject = JSONObject()
        .put("capability_id", value.capabilityId)
        .put("operation", value.operation)
        .put("outcome", value.outcome.name)
        .put("observed_at_ms", value.observedAtMs)
        .put("source", value.source)
        .put("verified", value.verified)
        .put("detail", value.detail)

    private fun parse(json: JSONObject): CapabilityEvidence = CapabilityEvidence(
        capabilityId = json.getString("capability_id").also { require(it.isNotBlank() && it.length <= 128) },
        operation = json.getString("operation").also { require(it.isNotBlank() && it.length <= 128) },
        outcome = EvidenceOutcome.valueOf(json.getString("outcome")),
        observedAtMs = json.getLong("observed_at_ms").also { require(it >= 0) },
        source = json.getString("source").also { require(it.isNotBlank() && it.length <= 128) },
        verified = json.getBoolean("verified"),
        detail = json.optString("detail", "").also { require(it.length <= 1_000) }
    )

    companion object {
        const val FILE_NAME = "capability/learning-evidence.json"
        private const val SCHEMA_VERSION = 1
        private const val MAX_FILE_BYTES = 512 * 1024
        private const val MAX_ENTRIES = 2_000
        private const val MIN_VERIFIED_SUCCESSES = 2
    }
}
