package com.jarvis.app.cognition.workspace

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * CONTINUITY-LAW: the replay check — a FIXTURE-based regression that asks one
 * question after any number of accepted slow changes: would the system still
 * answer a real recorded turn recognizably the same way it did on the baseline,
 * at least on its CORE identity facts?
 *
 * Why this exists: the gate decides which slow changes are ACCEPTED, and an
 * accepted change is by definition a rewrite of durable state. Nothing else in
 * the system notices when a rewrite quietly turns the system into a different
 * one. This is that noticing, and it is deliberately NOT a judgement call left
 * to a model: a human authors the expectations ([RecordedTurn.expectedIdentity])
 * once, the render is deterministic arithmetic over real state, and the verdict
 * is three reproducible checks.
 *
 * The render reads REAL state, in this order, for every recorded turn:
 *  - the CORE self-model fields from [CoreIdentity] (the live values the
 *    composition root read off [SelfModel] and the world-model user node), unless
 *    a CORE claim was published into the workspace — in which case the CLAIM wins
 *    the render and the disagreement with the live value is itself drift, which
 *    is exactly how a CORE change that bypassed the gate gets caught;
 *  - every SLOW field from the durable [ChangeLog] — the only authority for an
 *    accepted value — so an unaccepted proposal is rendered as
 *    [UNACCEPTED], never as the field's value;
 *  - every live FAST self-model claim as-is, because FAST state really is
 *    whatever the live source last said.
 *
 * The three checks in [ReplayCheck.verify]:
 *  1. every human-authored [ExpectedFact] of the turn still holds exactly;
 *  2. every CORE field's value equals the baseline's (a legitimate slow change
 *     may move a persona value; it may not move the name, the version or the
 *     user node);
 *  3. the answer still covers [MIN_FIELD_SIMILARITY] of the identity field
 *     NAMES the baseline carried — a human-set floor, so a check that passes
 *     because it compares nothing is still impossible. Retention, not set
 *     similarity: an accepted slow change legitimately ADDS a field, and a check
 *     that punished that would fail the very case it is meant to allow.
 */
class ReplayCheck(
    private val workspace: Workspace,
    private val changeLog: ChangeLog,
    private val coreIdentity: CoreIdentity,
    private val fixture: ReplayFixture
) {

    init {
        require(fixture.turns.isNotEmpty()) { "a replay check needs at least one recorded turn" }
    }

    /**
     * Replay every recorded turn against the CURRENT state and return what the
     * system would say now. This is the only function that produces the strings
     * the verdict compares.
     */
    fun replay(): ReplayResult = ReplayResult(
        fixture.turns.map { turn ->
            val facts = renderFacts()
            TurnRender(
                turnId = turn.turnId,
                input = turn.input,
                identityFacts = facts,
                response = renderResponse(turn, facts)
            )
        }
    )

    /**
     * The human-authored similarity check.
     *
     * @param baseline the [replay] captured BEFORE the changes under test.
     * @param current the replay to judge; defaults to a fresh [replay].
     */
    fun verify(baseline: ReplayResult, current: ReplayResult = replay()): ReplayVerdict {
        val drifts = mutableListOf<Drift>()
        var lowestSimilarity = 1.0

        for (turn in fixture.turns) {
            val rendered = current.render(turn.turnId)
            val before = baseline.render(turn.turnId)
            if (rendered == null || before == null) {
                drifts += Drift(
                    turn.turnId,
                    "(turn)",
                    "a rendered answer",
                    if (rendered == null) "missing from the current replay" else "missing from the baseline",
                    "the check cannot compare a turn that was not replayed"
                )
                continue
            }

            // 1. the human-authored expectations.
            for (expected in turn.expectedIdentity) {
                val actual = rendered.identityFacts[expected.field]
                if (actual != expected.value) {
                    drifts += Drift(
                        turn.turnId,
                        expected.field,
                        expected.value,
                        actual,
                        "a recorded turn's human-authored expectation no longer holds"
                    )
                }
            }

            // 2. the CORE fields must equal the baseline exactly.
            for ((field, value) in before.identityFacts) {
                if (coreField(field) != true) continue
                val actual = rendered.identityFacts[field]
                if (actual != value) {
                    drifts += Drift(
                        turn.turnId,
                        field,
                        value,
                        actual,
                        "a CORE identity fact moved away from the baseline"
                    )
                }
            }
            // A CORE field that appeared where the baseline had none is drift too.
            for (field in rendered.identityFacts.keys) {
                if (coreField(field) != true) continue
                if (!before.identityFacts.containsKey(field)) {
                    drifts += Drift(
                        turn.turnId,
                        field,
                        "(absent on the baseline)",
                        rendered.identityFacts[field],
                        "a CORE identity fact appeared that the baseline never had"
                    )
                }
            }

            // 3. recognizably the same answer: how much of what a recorded turn
            // said about identity the system still says. Measured as RETENTION of
            // the baseline's field names, not as similarity of two sets, because a
            // legitimate accepted slow change ADDS a field the baseline did not
            // have — penalising that would fail exactly the case that is allowed.
            val baselineFields = before.identityFacts.keys
            val similarity = if (baselineFields.isEmpty()) 1.0 else {
                baselineFields.count { it in rendered.identityFacts }.toDouble() / baselineFields.size
            }
            lowestSimilarity = minOf(lowestSimilarity, similarity)
            if (similarity < MIN_FIELD_SIMILARITY) {
                drifts += Drift(
                    turn.turnId,
                    "(answer shape)",
                    "at least ${(MIN_FIELD_SIMILARITY * 100).toInt()}% of the baseline's identity fields",
                    "${(similarity * 100).toInt()}%",
                    "the answer no longer covers what a recorded turn said about identity"
                )
            }
        }

        return ReplayVerdict(
            // The same drift can be caught by BOTH the human-authored
            // expectation and the CORE-vs-baseline comparison; report it once.
            consistent = drifts.isEmpty(),
            fieldSimilarity = lowestSimilarity,
            drifts = drifts.distinct()
        )
    }

    /** The identity facts a turn's answer would carry right now. */
    private fun renderFacts(): Map<String, String> {
        val facts = LinkedHashMap<String, String>()

        // CORE: the live values, unless a CORE claim reached the workspace — a
        // claim that contradicts the live source IS the drift this catches.
        facts[ChangeLayer.IDENTITY_PREFIX + "name"] =
            coreClaimValue(ChangeLayer.IDENTITY_PREFIX + "name") ?: coreIdentity.name ?: UNKNOWN
        facts[ChangeLayer.IDENTITY_PREFIX + "version"] =
            coreClaimValue(ChangeLayer.IDENTITY_PREFIX + "version")
                ?: coreIdentity.version?.toString()
                ?: UNKNOWN
        facts[USER_FIELD] = coreIdentity.userNodeName

        // SLOW: the accepted value per the durable log. A field that only has
        // unaccepted proposals says so instead of quoting one of them.
        for (field in slowFields()) {
            val accepted = changeLog.latestValue(field)
            facts[field] = accepted ?: if (hasStatement(field)) UNACCEPTED else "(unset)"
        }

        // FAST: whatever the live source last published.
        for (claim in workspace.claims(ClaimKind.SELF_MODEL)) {
            val field = claim.target ?: continue
            if (claim.layer != ChangeLayer.FAST) continue
            facts[field] = claim.payload
        }
        return facts
    }

    /** The value a directly published CORE claim asserts, or null if none does. */
    private fun coreClaimValue(field: String): String? {
        val bare = field.removePrefix(ChangeLayer.IDENTITY_PREFIX)
        if (bare.lowercase() !in ChangeLayer.SELF_MODEL_IDENTITY_FIELDS) return null
        return workspace.claims(ClaimKind.SELF_MODEL)
            .firstOrNull { it.layer == ChangeLayer.CORE && (it.target == bare || it.target == field) }
            ?.payload
    }

    /** Every field the log or the store has ever heard a SLOW statement about. */
    private fun slowFields(): List<String> {
        val fromLog = changeLog.entries().map { it.target }
        val fromClaims = workspace.claims(ClaimKind.PERSONA_TRAIT) + workspace.claims(ClaimKind.TRUST_TIER)
        return (fromLog + fromClaims.mapNotNull { it.target }).distinct().sorted()
    }

    /** Whether a real turn ever stated [field], accepted or not. */
    private fun hasStatement(field: String): Boolean =
        workspace.claims(ClaimKind.PERSONA_TRAIT).any { it.target == field } ||
            workspace.claims(ClaimKind.TRUST_TIER).any { it.target == field }

    /** Whether a rendered field name is a CORE identity fact. */
    private fun coreField(field: String): Boolean {
        if (field == USER_FIELD) return true
        if (!field.startsWith(ChangeLayer.IDENTITY_PREFIX)) return false
        val bare = field.removePrefix(ChangeLayer.IDENTITY_PREFIX)
        return bare.lowercase() in ChangeLayer.SELF_MODEL_IDENTITY_FIELDS
    }

    private fun renderResponse(turn: RecordedTurn, facts: Map<String, String>): String {
        val body = facts.entries.joinToString("; ") { "${it.key}=${it.value}" }
        return "${turn.turnId} \"${turn.input}\" -> [identity] $body"
    }

    companion object {
        /** Rendered field name for the user node this deployment serves. */
        const val USER_FIELD: String = "user"

        /** A field the system has no real value for. */
        const val UNKNOWN: String = "(unknown)"

        /**
         * How a SLOW field renders while it has real statements but no ACCEPTED
         * change: the unaccepted value is deliberately NOT shown, so an answer can
         * never quote a change the gate refused.
         */
        const val UNACCEPTED: String = "(change not accepted)"

        /**
         * The human-set floor for "recognizably the same answer": the fraction of
         * the baseline's identity fields that must still be present. A slow
         * change that retires most of what a recorded turn said about identity
         * is not a persona tweak, it is a different assistant.
         */
        const val MIN_FIELD_SIMILARITY: Double = 0.5
    }
}

/**
 * The CORE identity facts as the live composition root read them. Passed in as
 * VALUES, not as organ references, so the replay check names no organ (the same
 * discipline the shared workspace exists to enforce).
 *
 * @param name the system's own name (real: `SelfModel.identity().name`).
 * @param version the system's own version (real: `SelfModel.identity().version`).
 * @param userNodeName the user node this deployment serves (real:
 *   `WorldModelService.USER_NODE_NAME`).
 */
data class CoreIdentity(
    val name: String?,
    val version: Int?,
    val userNodeName: String
)

/** One human-authored fact a recorded turn's answer must still carry. */
data class ExpectedFact(val field: String, val value: String)

/**
 * ONE recorded turn plus what a human decided its answer must still say.
 *
 * @param provenance where the turn text actually came from, recorded per turn
 *   in the committed fixture. A replay suite is only evidence if its inputs are
 *   real; a fixture that cannot say where a turn came from is a scenario, and
 *   this class makes that distinction part of the data.
 * @param expectedIdentity the human-authored facts — always the CORE identity
 *   ones, never a SLOW field's value, because an accepted slow change is
 *   supposed to move a SLOW value and would otherwise fail the check that is
 *   meant to allow it.
 */
data class RecordedTurn(
    val turnId: String,
    val input: String,
    val provenance: String,
    val expectedIdentity: List<ExpectedFact>
)

/** A small fixed set of real recorded turns. */
data class ReplayFixture(val turns: List<RecordedTurn>)

/** One turn's answer as it would be produced right now. */
data class TurnRender(
    val turnId: String,
    val input: String,
    val identityFacts: Map<String, String>,
    val response: String
)

/** Every recorded turn replayed against one state. */
data class ReplayResult(val renders: List<TurnRender>) {
    fun render(turnId: String): TurnRender? = renders.firstOrNull { it.turnId == turnId }
}

/** One specific way the replay failed to be recognizably consistent. */
data class Drift(
    val turnId: String,
    val field: String,
    val expected: String?,
    val actual: String?,
    val detail: String
) {
    override fun toString(): String =
        "turn '$turnId' field '$field': expected '$expected' but got '$actual' ($detail)"
}

/** The outcome of the human-authored similarity check. */
data class ReplayVerdict(
    val consistent: Boolean,
    val fieldSimilarity: Double,
    val drifts: List<Drift>
)

/**
 * Reads the committed recorded-turn fixture — append-only local JSON Lines, one
 * turn per line, the same local-only shape [FileChangeLog] and
 * `JsonlTurnTraceStore` use, and for the same reason: a fixture that has to be
 * readable, diffable and reviewable as DATA.
 *
 * The turns are loaded from the app's own classpath resource ([fromClasspath]) so
 * the production composition, the JVM twin and the tests all read the SAME one
 * copy. A second copy of the fixture is a second baseline, which is precisely
 * the failure mode this story exists to remove.
 */
object RecordedTurns {

    /** The one committed location of the recorded turns, on every path. */
    const val CLASSPATH_RESOURCE: String = "replay/recorded_turns.jsonl"

    /** The recorded turns in [stream], in order. */
    fun fromStream(stream: java.io.InputStream): ReplayFixture = ReplayFixture(
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }
                .map { line -> recordedTurnFromJson(JSONObject(line)) }
                .toList()
        }
    )

    /**
     * The recorded turns packaged with the app, or null if the resource is absent
     * (a build without it). Null is disclosed rather than defaulted to an empty
     * fixture: "no recorded turns" and "no turns recorded" are different facts,
     * and [ReplayCheck] refuses to be constructed on an empty one.
     */
    fun fromClasspath(name: String = CLASSPATH_RESOURCE): ReplayFixture? {
        val stream = RecordedTurns::class.java.classLoader?.getResourceAsStream(name) ?: return null
        return stream.use { fromStream(it) }
    }

    /**
     * One recorded turn from its JSON-Lines object. A turn with no [provenance]
     * is a hard error, not a default: a fixture that cannot say where its input
     * came from is a scenario, and this is where that stops being loadable.
     */
    fun recordedTurnFromJson(json: JSONObject): RecordedTurn {
        val turnId = json.getString("turnId")
        val provenance = json.optString("provenance").trim()
        require(provenance.isNotEmpty()) {
            "recorded turn '$turnId' has no provenance: a replay fixture that cannot say where " +
                "its inputs came from is a scenario, not evidence"
        }
        return RecordedTurn(
            turnId = turnId,
            input = json.getString("input"),
            provenance = provenance,
            expectedIdentity = json.getJSONArray("expectedIdentity").let { a ->
                (0 until a.length()).map { i ->
                    val fact = a.getJSONObject(i)
                    ExpectedFact(field = fact.getString("field"), value = fact.getString("value"))
                }
            }
        )
    }

    /** One recorded turn as the JSON-Lines object [recordedTurnFromJson] reads. */
    fun recordedTurnToJson(turn: RecordedTurn): JSONObject = JSONObject()
        .put("turnId", turn.turnId)
        .put("input", turn.input)
        .put("provenance", turn.provenance)
        .put(
            "expectedIdentity",
            JSONArray().also { a ->
                turn.expectedIdentity.forEach { e ->
                    a.put(JSONObject().put("field", e.field).put("value", e.value))
                }
            }
        )
}

/** Reads and writes a [ReplayFixture] held in a local file, for authoring and for a device-local override. */
class JsonlReplayFixture(private val file: File) {

    /** The fixture file this loader reads. */
    val fixtureFile: File get() = file

    /** The recorded turns in the file, in file order; empty if the file is absent. */
    fun load(): ReplayFixture = if (!file.exists()) {
        ReplayFixture(emptyList())
    } else {
        file.inputStream().use { RecordedTurns.fromStream(it) }
    }

    /** Writes a fixture file in the format [load] reads, for authoring fixtures. */
    fun write(fixture: ReplayFixture) {
        file.parentFile?.mkdirs()
        file.writeText(
            fixture.turns.joinToString("\n") { RecordedTurns.recordedTurnToJson(it).toString() } + "\n",
            Charsets.UTF_8
        )
    }
}
