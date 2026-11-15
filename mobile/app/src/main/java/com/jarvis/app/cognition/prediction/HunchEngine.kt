package com.jarvis.app.cognition.prediction

import com.jarvis.app.cognition.workspace.Claim
import com.jarvis.app.cognition.workspace.ClaimKind
import com.jarvis.app.cognition.workspace.Workspace
import com.jarvis.app.cognition.workspace.WorkspaceOrgan
import com.jarvis.app.cognition.workspace.WorkspaceTask
import com.jarvis.app.cognition.workspace.RiskLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class PendingExpectation(
    val claimId: String,
    val fromState: String,
    val predictedNext: String,
    val baselineNext: String
)

data class PredictionPerformance(
    val evaluated: Int = 0,
    val modelCorrect: Int = 0,
    val baselineCorrect: Int = 0
) {
    val modelAccuracy: Double get() = if (evaluated == 0) 0.0 else modelCorrect.toDouble() / evaluated
    val baselineAccuracy: Double get() = if (evaluated == 0) 0.0 else baselineCorrect.toDouble() / evaluated
}

data class ExpectationSnapshot(
    val transitions: Map<String, Map<String, Int>> = emptyMap(),
    val episodeIds: Map<String, Map<String, List<String>>> = emptyMap(),
    val performance: PredictionPerformance = PredictionPerformance(),
    val lastStateKey: String? = null,
    val pending: PendingExpectation? = null
)

interface ExpectationTableStore {
    fun load(): ExpectationSnapshot
    fun save(snapshot: ExpectationSnapshot)
}

/** Atomic, private-file persistence. Only hashed situation keys and counts are stored. */
class FileExpectationTableStore(private val file: File) : ExpectationTableStore {
    private val lock = Any()

    override fun load(): ExpectationSnapshot = synchronized(lock) {
        if (!file.isFile) {
            val backup = File(file.parentFile, file.name + ".bak")
            if (backup.isFile) runCatching { backup.copyTo(file, overwrite = true) }
        }
        if (!file.isFile) return@synchronized ExpectationSnapshot()
        runCatching {
            val root = JSONObject(file.readText())
            val transitionsJson = root.optJSONObject("transitions") ?: JSONObject()
            val transitions = mutableMapOf<String, Map<String, Int>>()
            val fromKeys = transitionsJson.keys()
            while (fromKeys.hasNext()) {
                val from = fromKeys.next()
                val nextJson = transitionsJson.optJSONObject(from) ?: JSONObject()
                val next = mutableMapOf<String, Int>()
                val nextKeys = nextJson.keys()
                while (nextKeys.hasNext()) {
                    val to = nextKeys.next()
                    next[to] = nextJson.optInt(to, 0).coerceAtLeast(0)
                }
                transitions[from] = next
            }
            val episodesJson = root.optJSONObject("episodeIds") ?: JSONObject()
            val episodeIds = mutableMapOf<String, Map<String, List<String>>>()
            val episodeFromKeys = episodesJson.keys()
            while (episodeFromKeys.hasNext()) {
                val from = episodeFromKeys.next()
                val nextJson = episodesJson.optJSONObject(from) ?: JSONObject()
                val nextEpisodes = mutableMapOf<String, List<String>>()
                val nextKeys = nextJson.keys()
                while (nextKeys.hasNext()) {
                    val to = nextKeys.next()
                    val values = nextJson.optJSONArray(to) ?: JSONArray()
                    nextEpisodes[to] = (0 until values.length()).map { values.getString(it) }
                }
                episodeIds[from] = nextEpisodes
            }
            val perf = root.optJSONObject("performance") ?: JSONObject()
            val pendingJson = root.optJSONObject("pending")
            ExpectationSnapshot(
                transitions = transitions,
                episodeIds = episodeIds,
                performance = PredictionPerformance(
                    evaluated = perf.optInt("evaluated", 0),
                    modelCorrect = perf.optInt("modelCorrect", 0),
                    baselineCorrect = perf.optInt("baselineCorrect", 0)
                ),
                lastStateKey = root.optString("lastStateKey").takeIf { it.isNotBlank() },
                pending = pendingJson?.let {
                    PendingExpectation(
                        claimId = it.getString("claimId"),
                        fromState = it.getString("fromState"),
                        predictedNext = it.getString("predictedNext"),
                        baselineNext = it.getString("baselineNext")
                    )
                }
            )
        }.getOrDefault(ExpectationSnapshot())
    }

    override fun save(snapshot: ExpectationSnapshot) = synchronized(lock) {
        file.parentFile?.mkdirs()
        val transitions = JSONObject()
        snapshot.transitions.forEach { (from, nextMap) ->
            val next = JSONObject()
            nextMap.forEach { (to, count) -> next.put(to, count) }
            transitions.put(from, next)
        }
        val episodeIds = JSONObject()
        snapshot.episodeIds.forEach { (from, nextMap) ->
            val next = JSONObject()
            nextMap.forEach { (to, ids) -> next.put(to, JSONArray(ids)) }
            episodeIds.put(from, next)
        }
        val root = JSONObject()
            .put("transitions", transitions)
            .put("episodeIds", episodeIds)
            .put("performance", JSONObject()
                .put("evaluated", snapshot.performance.evaluated)
                .put("modelCorrect", snapshot.performance.modelCorrect)
                .put("baselineCorrect", snapshot.performance.baselineCorrect))
            .put("lastStateKey", snapshot.lastStateKey)
        snapshot.pending?.let {
            root.put("pending", JSONObject()
                .put("claimId", it.claimId)
                .put("fromState", it.fromState)
                .put("predictedNext", it.predictedNext)
                .put("baselineNext", it.baselineNext))
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        val backup = File(file.parentFile, file.name + ".bak")
        temp.writeText(root.toString())
        if (file.exists()) {
            if (backup.exists()) backup.delete()
            if (!file.renameTo(backup)) error("Cannot preserve previous expectation table")
        }
        if (!temp.renameTo(file)) {
            if (backup.exists()) backup.renameTo(file)
            error("Cannot promote expectation table")
        }
        backup.delete()
    }
}

data class Hunch(
    val stateKey: String,
    val predictedNextState: String,
    val baselineNextState: String,
    val confidence: Double,
    val observations: Int,
    val modelAccuracy: Double,
    val baselineAccuracy: Double,
    val validatedAgainstBaseline: Boolean
)

/**
 * Outcome-without-process substrate. It learns one-step successor structure
 * from the shared workspace, publishes auditable PREDICTION claims, and never
 * owns a person model or a fixed connection to a named organ.
 *
 * The initial encoder is deliberately replaceable: a Forge-trained JEPA-style
 * representation encoder can replace fingerprint() without changing the
 * expectation table, outcome ledger, or Workspace contract.
 */
class HunchEngine(
    private val store: ExpectationTableStore,
    private val minimumStateObservations: Int = 3,
    private val validationMargin: Double = 0.05
) : WorkspaceOrgan {
    override val organId: String = "cognition.hunch"

    override fun relevance(claims: List<Claim>, task: WorkspaceTask): Int =
        if (task.riskLevel != RiskLevel.CRITICAL &&
            claims.any { it.kind == ClaimKind.MENTAL_STATE || it.kind == ClaimKind.CONTEXT_WINDOW }
        ) 40 else 0

    override fun contribute(workspace: Workspace, task: WorkspaceTask) {
        observeTurn(workspace, task.nowMillis, task.episodeId, task.taskKind)
    }

    @Synchronized
    fun observeTurn(
        workspace: Workspace,
        now: Long = System.currentTimeMillis(),
        episodeId: String = "turn:$now",
        taskKind: String = "GENERAL"
    ): Hunch? {
        // The initial encoder uses relatively stable shared state claims.
        // CONTEXT_WINDOW is intentionally excluded because its raw turn text
        // changes constantly; a Forge-trained JEPA encoder will replace this
        // exact-match bootstrap fingerprint with semantic latent state.
        val claims = listOf(
            ClaimKind.MENTAL_STATE,
            ClaimKind.SELF_MODEL,
            ClaimKind.PERSONA_TRAIT,
            ClaimKind.TRUST_TIER
        ).mapNotNull { workspace.current(it) }
        if (claims.isEmpty()) return null

        val stateKey = fingerprint(claims, taskKind)
        var snapshot = store.load()

        snapshot.pending?.let { pending ->
            val modelCorrect = pending.predictedNext == stateKey
            val baselineCorrect = pending.baselineNext == stateKey
            snapshot = snapshot.copy(
                performance = snapshot.performance.copy(
                    evaluated = snapshot.performance.evaluated + 1,
                    modelCorrect = snapshot.performance.modelCorrect + if (modelCorrect) 1 else 0,
                    baselineCorrect = snapshot.performance.baselineCorrect + if (baselineCorrect) 1 else 0
                )
            )
            workspace.claims(ClaimKind.PREDICTION)
                .firstOrNull { it.id == pending.claimId && !it.isResolved }
                ?.let { workspace.publish(it.resolved(modelCorrect)) }
        }

        val transitions = snapshot.transitions.mapValues { it.value.toMutableMap() }.toMutableMap()
        val episodeIds = snapshot.episodeIds.mapValues { (_, nextMap) ->
            nextMap.mapValues { it.value.toMutableList() }.toMutableMap()
        }.toMutableMap()
        snapshot.lastStateKey?.let { previous ->
            val next = transitions.getOrPut(previous) { mutableMapOf() }
            next[stateKey] = (next[stateKey] ?: 0) + 1
            val sourceIds = episodeIds.getOrPut(previous) { mutableMapOf() }
                .getOrPut(stateKey) { mutableListOf() }
            if (episodeId !in sourceIds) sourceIds.add(episodeId)
            while (sourceIds.size > 32) sourceIds.removeAt(0)
            while (next.size > 32) {
                val weakest = next.minByOrNull { it.value }?.key ?: break
                next.remove(weakest)
                episodeIds[previous]?.remove(weakest)
            }
        }
        while (transitions.size > 512) {
            val weakestState = transitions.minByOrNull { it.value.values.sum() }?.key ?: break
            transitions.remove(weakestState)
            episodeIds.remove(weakestState)
        }
        val globalCounts = mutableMapOf<String, Int>()
        transitions.values.forEach { counts -> counts.forEach { (next, count) ->
            globalCounts[next] = (globalCounts[next] ?: 0) + count
        } }
        val baseline = globalCounts.maxByOrNull { it.value }?.key
        val stateCounts = transitions[stateKey].orEmpty()
        val total = stateCounts.values.sum()
        val best = stateCounts.maxByOrNull { it.value }
        val confidence = if (total == 0 || best == null) 0.0 else best.value.toDouble() / total
        val baselineConfidence = if (globalCounts.values.sum() == 0 || baseline == null) 0.0
            else (globalCounts[baseline] ?: 0).toDouble() / globalCounts.values.sum()
        val performance = snapshot.performance
        val validated = performance.evaluated >= 20 &&
            performance.modelAccuracy > performance.baselineAccuracy + validationMargin

        val hunch = if (best != null && baseline != null &&
            total >= minimumStateObservations && best.key != baseline &&
            confidence > baselineConfidence + validationMargin
        ) {
            Hunch(
                stateKey = stateKey,
                predictedNextState = best.key,
                baselineNextState = baseline,
                confidence = confidence,
                observations = total,
                modelAccuracy = performance.modelAccuracy,
                baselineAccuracy = performance.baselineAccuracy,
                validatedAgainstBaseline = validated
            )
        } else null

        val priorPending = snapshot.pending
        var nextPending: PendingExpectation? = null
        if (hunch != null) {
            val claim = Claim(
                id = "hunch-${UUID.randomUUID()}",
                kind = ClaimKind.PREDICTION,
                payload = JSONObject()
                    .put("fromState", hunch.stateKey)
                    .put("predictedNextState", hunch.predictedNextState)
                    .toString(),
                confidence = hunch.confidence,
                sourceOrgan = "cognition.hunchEngine",
                decayRate = 0.01,
                createdAt = now,
                supersedes = priorPending?.claimId,
                baseline = hunch.baselineNextState
            )
            workspace.publish(claim)
            nextPending = PendingExpectation(
                claimId = claim.id,
                fromState = hunch.stateKey,
                predictedNext = hunch.predictedNextState,
                baselineNext = hunch.baselineNextState
            )
        }

        store.save(snapshot.copy(
            transitions = transitions.mapValues { it.value.toMap() },
            episodeIds = episodeIds.mapValues { (_, nextMap) ->
                nextMap.mapValues { it.value.toList() }
            },
            lastStateKey = stateKey,
            pending = nextPending
        ))
        return hunch
    }

    private fun fingerprint(claims: List<Claim>, taskKind: String): String {
        val canonical = "task:$taskKind|" + claims
            .sortedWith(compareBy<Claim> { it.kind.name }.thenBy { it.personId ?: "" })
            .joinToString("|") { claim ->
                val payloadHash = sha256(claim.payload)
                "${claim.kind.name}:${claim.personId ?: "_"}:$payloadHash"
            }
        return sha256(canonical)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
