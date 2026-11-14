package com.jarvis.app.development

import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * An option is a candidate behaviour for this particular decision.
 *
 * The caller supplies the option set. These are not a taxonomy of Friday's
 * permanent abilities; another caller or a later mechanism may offer different
 * candidates without changing the learning substrate.
 */
data class DevelopmentalChoiceOption(
    val id: String,
    val instruction: String
)

/** The selected candidate and the evidence count that influenced its selection. */
data class DevelopmentalChoice(
    val id: String,
    val instruction: String,
    val evidenceCount: Int
)

data class ChoiceOutcomeEvidence(
    val positive: Double = 0.0,
    val negative: Double = 0.0
) {
    val count: Int get() = (positive + negative).toInt()
}

/**
 * Replaceable candidate-selection mechanism.
 *
 * The contract is deliberately small: the mechanism sees the candidates and
 * accumulated outcome evidence, and returns one of the candidates. It does not
 * own identity, memory, motivation, or any permanent cognitive function.
 */
fun interface DevelopmentalSelectionMechanism {
    fun select(
        options: List<DevelopmentalChoiceOption>,
        evidence: Map<String, ChoiceOutcomeEvidence>
    ): DevelopmentalChoiceOption
}

/**
 * A provisional outcome-weighted selector.
 *
 * It uses a smoothed success estimate for each caller-supplied option. This is
 * an experiment in turning consequences into a changed future choice, not a
 * claim that reinforcement counts are the final way Friday will learn.
 */
class OutcomeWeightedSelection : DevelopmentalSelectionMechanism {
    override fun select(
        options: List<DevelopmentalChoiceOption>,
        evidence: Map<String, ChoiceOutcomeEvidence>
    ): DevelopmentalChoiceOption {
        require(options.isNotEmpty()) { "At least one developmental choice is required" }
        require(options.map { it.id }.distinct().size == options.size) {
            "Developmental choice ids must be unique within a decision"
        }

        return options.maxByOrNull { option ->
            val result = evidence[option.id] ?: ChoiceOutcomeEvidence()
            (result.positive + 1.0) / (result.positive + result.negative + 2.0)
        }!!
    }
}

/**
 * Append-only local journal. Records are deliberately limited to choice ids,
 * outcome signals, timestamps and provenance ids; raw conversation text is
 * never copied into developmental state.
 */
class JsonlDevelopmentJournal(private val file: File) {
    private val lock = Any()

    fun readRecords(): List<JSONObject> = synchronized(lock) {
        if (!file.isFile) return@synchronized emptyList()
        file.readLines(Charsets.UTF_8).mapNotNull { line ->
            if (line.isBlank()) null else runCatching { JSONObject(line) }.getOrNull()
        }
    }

    fun append(record: JSONObject) = synchronized(lock) {
        file.parentFile?.mkdirs()
        file.appendText(record.toString() + "\n", Charsets.UTF_8)
    }

    fun location(): String = file.absolutePath
}

/**
 * The first live developmental experiment.
 *
 * A later user reaction is attributed to the last choice Friday made. Only
 * explicit, high-signal feedback is accepted; ordinary conversation is not
 * silently treated as approval or disapproval. The learned state changes which
 * caller-supplied behaviour is selected on later turns.
 *
 * This is intentionally a replaceable candidate mechanism. The journal
 * contains no transcript, so replaying the original episode is not how the
 * later choice is produced.
 */
class DevelopmentalChoiceLoop(
    private val journal: JsonlDevelopmentJournal,
    private val mechanism: DevelopmentalSelectionMechanism = OutcomeWeightedSelection(),
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val lock = Any()
    private val evidence = linkedMapOf<String, ChoiceOutcomeEvidence>()
    private var lastChoiceId: String? = null

    init {
        restore()
    }

    fun beginTurn(
        userInput: String,
        options: List<DevelopmentalChoiceOption>,
        acceptFeedback: Boolean = true
    ): DevelopmentalChoice = synchronized(lock) {
        require(options.isNotEmpty()) { "At least one developmental choice is required" }
        require(options.map { it.id }.distinct().size == options.size) {
            "Developmental choice ids must be unique within a decision"
        }

        if (acceptFeedback) applyExplicitFeedback(userInput, options)

        val selected = mechanism.select(options, evidence.toMap())
        require(options.any { it.id == selected.id }) {
            "The developmental mechanism selected an option that was not offered"
        }

        journal.append(
            JSONObject()
                .put("event", "choice")
                .put("id", UUID.randomUUID().toString())
                .put("at", nowMs())
                .put("choice", selected.id)
        )
        lastChoiceId = selected.id

        DevelopmentalChoice(
            id = selected.id,
            instruction = selected.instruction,
            evidenceCount = evidence[selected.id]?.count ?: 0
        )
    }

    /** Read-only evidence projection for diagnostics and tests. */
    fun evidenceSnapshot(): Map<String, ChoiceOutcomeEvidence> = synchronized(lock) {
        evidence.toMap()
    }

    private fun applyExplicitFeedback(
        input: String,
        options: List<DevelopmentalChoiceOption>
    ) {
        val signal = parseFeedback(input) ?: return
        val previous = lastChoiceId

        if (previous != null) {
            val reinforcesPrevious = signal.preferredChoiceId == previous
            recordOutcome(
                choiceId = previous,
                reward = if (reinforcesPrevious) 1.0 else signal.previousReward,
                signal = if (reinforcesPrevious) "explicit-preference-reinforcement" else signal.provenance
            )
        }

        signal.preferredChoiceId?.let { preferred ->
            if (options.any { it.id == preferred } && preferred != previous) {
                recordOutcome(
                    choiceId = preferred,
                    reward = 1.0,
                    signal = "explicit-preference"
                )
            }
        }
    }

    private fun recordOutcome(choiceId: String, reward: Double, signal: String) {
        val old = evidence[choiceId] ?: ChoiceOutcomeEvidence()
        evidence[choiceId] = if (reward >= 0.0) {
            old.copy(positive = old.positive + reward)
        } else {
            old.copy(negative = old.negative + -reward)
        }

        journal.append(
            JSONObject()
                .put("event", "feedback")
                .put("id", UUID.randomUUID().toString())
                .put("at", nowMs())
                .put("choice", choiceId)
                .put("reward", reward)
                .put("signal", signal)
        )
    }

    private fun restore() = synchronized(lock) {
        journal.readRecords().forEach { record ->
            when (record.optString("event")) {
                "choice" -> {
                    lastChoiceId = record.optString("choice").takeIf { it.isNotBlank() }
                }
                "feedback" -> {
                    val choiceId = record.optString("choice")
                    if (choiceId.isNotBlank()) {
                        val reward = record.optDouble("reward", Double.NaN)
                        if (reward.isFinite()) {
                            val old = evidence[choiceId] ?: ChoiceOutcomeEvidence()
                            evidence[choiceId] = if (reward >= 0.0) {
                                old.copy(positive = old.positive + reward)
                            } else {
                                old.copy(negative = old.negative + -reward)
                            }
                        }
                    }
                }
            }
        }
    }

    private data class FeedbackSignal(
        val previousReward: Double,
        val provenance: String,
        val preferredChoiceId: String? = null
    )

    /**
     * Deliberately conservative first parser. It recognises explicit correction
     * and approval language, not sentiment in general. It can be replaced by a
     * better learned or model-mediated interpreter without changing the journal.
     */
    private fun parseFeedback(input: String): FeedbackSignal? {
        val text = input.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val asksForBullets = listOf(
            "use bullets", "use bullet points", "make it bullet points",
            "give me bullets", "write it as bullets"
        ).any { text.contains(it) }

        val asksForProse = listOf(
            "use paragraphs", "use prose", "write it in paragraphs",
            "make it prose"
        ).any { text.contains(it) }

        if (asksForBullets) {
            return FeedbackSignal(
                previousReward = -1.0,
                provenance = "explicit-format-correction",
                preferredChoiceId = "bullets"
            )
        }
        if (asksForProse) {
            return FeedbackSignal(
                previousReward = -1.0,
                provenance = "explicit-format-correction",
                preferredChoiceId = "prose"
            )
        }

        val negative = listOf(
            "too long", "too verbose", "too detailed", "too much detail",
            "not like that", "not what i asked", "that was wrong",
            "that was bad", "don't do that", "do not do that"
        ).any { text.contains(it) }

        if (negative) {
            return FeedbackSignal(-1.0, "explicit-negative-evaluation")
        }

        val positive = listOf(
            "that was good", "that was great", "that was perfect",
            "that's better", "that is better", "i like that",
            "perfect thanks", "great thanks"
        ).any { text.startsWith(it) } || text == "exactly"

        if (positive) {
            return FeedbackSignal(1.0, "explicit-positive-evaluation")
        }

        return null
    }
}
