package com.jarvis.app.cognitive.seed

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.LinkedHashMap
import kotlin.math.abs

/**
 * Friday's first model-independent adaptive substrate.
 *
 * It is deliberately not a state machine, personality script, language model,
 * or closed ontology. Experiences are represented as arbitrary named numeric
 * features. Repeated co-occurrence and explicitly observed outcomes create
 * revisable associations that can be queried in a different context.
 *
 * The substrate is a small experiment, not a claim of general intelligence or
 * consciousness. It is local, deterministic, bounded, and restart-persistent.
 */
class FridaySeedSubstrate(
    private val journal: File,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    data class Experience(
        val id: Long,
        val timestampMs: Long,
        val features: Map<String, Double>,
        val outcomes: Map<String, Double>
    )

    data class Association(
        val source: String,
        val target: String,
        val strength: Double,
        val observations: Long
    )

    data class LearningReceipt(
        val experienceId: Long,
        val storedFeatures: Int,
        val changedAssociations: Int
    )

    private data class LinkKey(val source: String, val target: String)
    private data class Link(var mean: Double = 0.0, var observations: Long = 0L) {
        fun update(value: Double) {
            observations += 1L
            mean += (value - mean) / observations.toDouble()
            mean = mean.coerceIn(-1.0, 1.0)
        }
    }

    private val experiences = ArrayList<Experience>()
    private val links = LinkedHashMap<LinkKey, Link>()
    private var nextId = 1L
    private var previousFeatures: Map<String, Double> = emptyMap()

    init {
        load()
    }

    /**
     * Store one experience and update associative links between its features
     * and the previous experience. This permits a later, changed context to
     * reactivate related material without a language model.
     */
    @Synchronized
    fun observe(features: Map<String, Double>): LearningReceipt {
        val clean = cleanFeatures(features)
        val experience = Experience(nextId++, nowMs(), clean, emptyMap())
        val changed = associate(previousFeatures, clean)
        append(experience)
        experiences.add(experience)
        trimInMemory()
        previousFeatures = clean
        return LearningReceipt(experience.id, clean.size, changed)
    }

    /**
     * Record an observed outcome (not a predicted one). The resulting
     * source→outcome relations can influence future inference.
     */
    @Synchronized
    fun observeOutcome(
        context: Map<String, Double>,
        outcome: Map<String, Double>
    ): LearningReceipt {
        val cleanContext = cleanFeatures(context)
        val cleanOutcome = cleanFeatures(outcome)
        val experience = Experience(nextId++, nowMs(), cleanContext, cleanOutcome)
        val changed = associate(cleanContext, cleanOutcome)
        append(experience)
        experiences.add(experience)
        trimInMemory()
        previousFeatures = cleanContext
        return LearningReceipt(experience.id, cleanContext.size + cleanOutcome.size, changed)
    }

    /** Infer likely related/outcome features from a new context. */
    @Synchronized
    fun infer(context: Map<String, Double>, limit: Int = 12): Map<String, Double> {
        val clean = cleanFeatures(context)
        if (clean.isEmpty() || links.isEmpty()) return emptyMap()

        val totals = LinkedHashMap<String, Double>()
        val weights = LinkedHashMap<String, Double>()
        for ((source, value) in clean) {
            for ((key, link) in links) {
                if (key.source != source || key.target in clean) continue
                val confidence = link.observations.toDouble() / (link.observations + 2.0)
                val contribution = value * link.mean * confidence
                totals[key.target] = (totals[key.target] ?: 0.0) + contribution
                weights[key.target] = (weights[key.target] ?: 0.0) + abs(value) * confidence
            }
        }
        return totals.mapValues { (target, total) ->
            (total / (weights[target] ?: 1.0).coerceAtLeast(1.0)).coerceIn(-1.0, 1.0)
        }.filterValues { abs(it) >= MIN_SIGNAL }
            .entries.sortedByDescending { abs(it.value) }
            .take(limit.coerceIn(1, MAX_FEATURES))
            .associate { it.key to it.value }
    }

    @Synchronized
    fun recentExperiences(limit: Int = 20): List<Experience> =
        experiences.takeLast(limit.coerceIn(0, MAX_EXPERIENCES)).toList()

    @Synchronized
    fun associationSnapshot(limit: Int = 200): List<Association> =
        links.entries.asSequence()
            .map { (key, value) -> Association(key.source, key.target, value.mean, value.observations) }
            .sortedByDescending { it.observations }
            .take(limit.coerceIn(0, MAX_LINKS))
            .toList()

    @Synchronized
    fun experienceCount(): Int = experiences.size

    @Synchronized
    fun associationCount(): Int = links.size

    /**
     * Store an explicitly stated, user-correctable fact as a typed relation.
     * The original conversation is not retained; only the selected value is.
     */
    @Synchronized
    fun rememberFact(key: String, value: String): Boolean {
        val safeKey = normalizeFactKey(key)
        if (safeKey.isBlank()) return false
        val valuePrefix = "fact-value:$safeKey:"
        val safeValue = value.trim().replace(Regex("\\s+"), " ")
            .take((MAX_KEY_LENGTH - valuePrefix.length).coerceAtLeast(0))
        if (safeValue.isBlank()) return false
        observeOutcome(
            context = mapOf("fact:$safeKey" to 1.0),
            outcome = mapOf("$valuePrefix$safeValue" to 1.0)
        )
        return true
    }

    /** Return the most recently explicitly stored value for a fact key. */
    @Synchronized
    fun recallFact(key: String): String? {
        val safeKey = normalizeFactKey(key)
        if (safeKey.isBlank()) return null
        val prefix = "fact-value:$safeKey:"
        return experiences.asReversed().firstNotNullOfOrNull { experience ->
            if ("fact:$safeKey" !in experience.features) null
            else experience.outcomes.keys.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
        }
    }

    private fun normalizeFactKey(key: String): String =
        key.trim().lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .take(48)

    private fun associate(source: Map<String, Double>, target: Map<String, Double>): Int {
        if (source.isEmpty() || target.isEmpty()) return 0
        var changed = 0
        for ((sourceKey, sourceValue) in source) {
            for ((targetKey, targetValue) in target) {
                if (sourceKey == targetKey) continue
                val key = LinkKey(sourceKey, targetKey)
                val link = links.getOrPut(key) { Link() }
                link.update((sourceValue * targetValue).coerceIn(-1.0, 1.0))
                changed += 1
                if (links.size >= MAX_LINKS) return changed
            }
        }
        return changed
    }

    private fun cleanFeatures(input: Map<String, Double>): Map<String, Double> =
        input.entries.asSequence()
            .filter { it.key.isNotBlank() && it.key.length <= MAX_KEY_LENGTH && it.value.isFinite() }
            .take(MAX_FEATURES)
            .associate { it.key.trim() to it.value.coerceIn(-1.0, 1.0) }
            .filterValues { abs(it) >= MIN_SIGNAL }

    private fun append(experience: Experience) {
        journal.parentFile?.mkdirs()
        if (!journal.exists() || journal.length() == 0L) {
            FileOutputStream(journal, false).use { raw ->
                DataOutputStream(BufferedOutputStream(raw)).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(VERSION)
                    output.flush()
                    raw.fd.sync()
                }
            }
        }
        val bytes = encode(experience)
        FileOutputStream(journal, true).use { raw ->
            DataOutputStream(BufferedOutputStream(raw)).use { output ->
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()
                raw.fd.sync()
            }
        }
    }

    private fun encode(experience: Experience): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeLong(experience.id)
            out.writeLong(experience.timestampMs)
            writeFeatures(out, experience.features)
            writeFeatures(out, experience.outcomes)
        }
        return bytes.toByteArray()
    }

    private fun writeFeatures(out: DataOutputStream, values: Map<String, Double>) {
        out.writeInt(values.size)
        values.forEach { (key, value) ->
            out.writeUTF(key)
            out.writeDouble(value)
        }
    }

    private fun readFeatures(input: DataInputStream): Map<String, Double> {
        val count = input.readInt()
        require(count in 0..MAX_FEATURES) { "Invalid feature count" }
        val values = LinkedHashMap<String, Double>()
        repeat(count) {
            val key = input.readUTF()
            val value = input.readDouble()
            if (key.isNotBlank() && key.length <= MAX_KEY_LENGTH && value.isFinite()) {
                values[key] = value.coerceIn(-1.0, 1.0)
            }
        }
        return values
    }

    private fun load() {
        if (!journal.exists() || journal.length() == 0L) return
        var validLength = 0L
        try {
            DataInputStream(BufferedInputStream(FileInputStream(journal))).use { input ->
                require(input.readInt() == MAGIC) { "Unknown Friday seed journal" }
                require(input.readInt() == VERSION) { "Unsupported Friday seed journal version" }
                validLength = HEADER_BYTES.toLong()
                while (true) {
                    val size = try { input.readInt() } catch (_: EOFException) { break }
                    if (size !in 1..MAX_RECORD_BYTES) break
                    val payload = ByteArray(size)
                    try {
                        input.readFully(payload)
                        val record = DataInputStream(ByteArrayInputStream(payload)).use { recordInput ->
                            val id = recordInput.readLong()
                            val timestamp = recordInput.readLong()
                            val features = readFeatures(recordInput)
                            val outcomes = readFeatures(recordInput)
                            Experience(id, timestamp, features, outcomes)
                        }
                        experiences.add(record)
                        associate(previousFeatures, record.features)
                        associate(record.features, record.outcomes)
                        previousFeatures = record.features
                        nextId = maxOf(nextId, record.id + 1L)
                        validLength += 4L + size
                    } catch (_: Exception) {
                        break
                    }
                    if (experiences.size > MAX_EXPERIENCES) experiences.removeAt(0)
                }
            }
        } catch (_: Exception) {
            // Preserve the corrupt file for diagnostics; do not silently erase it.
            experiences.clear()
            links.clear()
            previousFeatures = emptyMap()
            nextId = 1L
        }
        if (validLength >= HEADER_BYTES && journal.length() > validLength) {
            runCatching { java.io.RandomAccessFile(journal, "rw").use { it.setLength(validLength) } }
        }
    }

    private fun trimInMemory() {
        while (experiences.size > MAX_EXPERIENCES) experiences.removeAt(0)
        while (links.size > MAX_LINKS) links.remove(links.keys.first())
    }

    companion object {
        private const val MAGIC = 0x46525344 // FRSD
        private const val VERSION = 1
        private const val HEADER_BYTES = 8
        private const val MAX_RECORD_BYTES = 64 * 1024
        private const val MAX_FEATURES = 48
        private const val MAX_KEY_LENGTH = 128
        private const val MAX_EXPERIENCES = 2_000
        private const val MAX_LINKS = 8_000
        private const val MIN_SIGNAL = 0.025
    }
}
