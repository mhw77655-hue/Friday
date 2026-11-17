package com.jarvis.app.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class JevActionDecision(
    val choice: String,
    val confidence: Double,
    val probabilities: Map<String, Double>,
    val riskScore: Double = 0.0,
    val riskConfidence: Double = 1.0,
    val explicitAuthorization: Double = 1.0,
    val targetClarity: Double = 1.0
)

fun interface JevApiKeyProvider {
    fun getApiKey(): String?
}

/**
 * Official TypeSafe System One REST adapter. Jev returns bounded typed decisions;
 * Android permissions and Friday's local policy remain the final authority.
 */
class JevDecisionClient(
    private val keyProvider: JevApiKeyProvider,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build(),
    private val model: String = "jev-latest"
) {
    suspend fun judgePhoneAction(
        utterance: String,
        actionDescription: String,
        risk: String
    ): JevActionDecision? = withContext(Dispatchers.IO) {
        val apiKey = keyProvider.getApiKey()?.takeIf { it.isNotBlank() } ?: return@withContext null
        val state = JSONObject()
            .put("spoken_request", utterance.take(1200))
            .put("proposed_action", actionDescription.take(400))
            .put("risk_tier", risk)
            .put("execution_context", "Friday Android phone assistant; only user-authorized Android APIs/accessibility actions are available")
        // Jev's typed primitives are evaluated in parallel against one state.
        // Choice selects the route, Score assesses consequence severity, and
        // Nouls separately check explicit authorization and target clarity.
        val questions = JSONObject()
            .put(
                "decision",
                JSONObject()
                    .put("type", "choice")
                    .put("instructions", "Should Friday execute this proposed phone action now, ask the user for confirmation, or refuse it?")
                    .put(
                        "criteria",
                        JSONObject()
                            .put("execute", "The request clearly authorizes this low-risk action and its target is unambiguous")
                            .put("confirm", "The action is consequential, ambiguous, could expose or alter private information, or needs explicit confirmation")
                            .put("reject", "The request is unsafe, unauthorized, deceptive, or outside the declared action")
                    )
            )
            .put(
                "risk_assessment",
                JSONObject()
                    .put("type", "score")
                    .put("instructions", "How severe are the consequences if Friday performs this exact proposed action incorrectly?")
                    .put("criteria", org.json.JSONArray()
                        .put("Negligible: reversible navigation or harmless display change")
                        .put("Low: minor reversible setting change with no private data exposure")
                        .put("High: affects private data, communications, accounts, purchases, or important device state")
                        .put("Critical: destructive, security-sensitive, financial, or difficult-to-reverse consequences"))
            )
            .put(
                "explicit_authorization",
                JSONObject()
                    .put("type", "noul")
                    .put("instructions", "Does the spoken request explicitly ask Friday to perform this exact proposed action now?")
                    .put("criteria", JSONObject()
                        .put("true", "The user directly and clearly requested this action")
                        .put("false", "The action is inferred, implied, broader than requested, or not authorized"))
            )
            .put(
                "target_clarity",
                JSONObject()
                    .put("type", "noul")
                    .put("instructions", "Does the request identify one unique target for this proposed action?")
                    .put("criteria", JSONObject()
                        .put("true", "The target is explicit and uniquely identified")
                        .put("false", "The target is missing, ambiguous, inferred, or one of several possible targets"))
            )
        val payload = JSONObject()
            .put("model", model)
            .put("state", state)
            .put("questions", questions)
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val root = JSONObject(response.body?.string().orEmpty())
                val answer = root.getJSONObject("answers").getJSONObject("decision")
                val probabilities = answer.optJSONObject("probabilities")
                val map = buildMap {
                    probabilities?.keys()?.forEach { key ->
                        put(key, probabilities.optDouble(key, 0.0))
                    }
                }
                val risk = root.getJSONObject("answers").optJSONObject("risk_assessment")
                val authorization = root.getJSONObject("answers").optJSONObject("explicit_authorization")
                val target = root.getJSONObject("answers").optJSONObject("target_clarity")
                JevActionDecision(
                    choice = answer.optString("choice", "confirm"),
                    confidence = answer.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
                    probabilities = map,
                    riskScore = risk?.optDouble("score", 3.0) ?: 3.0,
                    riskConfidence = risk?.optDouble("confidence", 0.0)?.coerceIn(0.0, 1.0) ?: 0.0,
                    explicitAuthorization = authorization?.optDouble("noul", 0.0)?.coerceIn(0.0, 1.0) ?: 0.0,
                    targetClarity = target?.optDouble("noul", 0.0)?.coerceIn(0.0, 1.0) ?: 0.0
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val ENDPOINT = "https://api.typesafe.ai/v1/systemone"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** Fail-closed policy around model output. Typed output is not proof of truth. */
object JevPhoneActionPolicy {
    fun mayExecute(decision: JevActionDecision, minimumConfidence: Double = 0.90): Boolean =
        decision.choice == "execute" &&
            decision.confidence >= minimumConfidence &&
            (decision.probabilities["execute"] ?: 0.0) >= minimumConfidence &&
            decision.riskScore <= 0.25 &&
            decision.riskConfidence >= 0.85 &&
            decision.explicitAuthorization >= 0.95 &&
            decision.targetClarity >= 0.95

    fun requiresConfirmation(decision: JevActionDecision): Boolean =
        decision.choice == "confirm" ||
            decision.confidence < 0.90 ||
            decision.riskScore > 0.25 ||
            decision.riskConfidence < 0.85 ||
            decision.explicitAuthorization < 0.95 ||
            decision.targetClarity < 0.95

    fun shouldReject(decision: JevActionDecision): Boolean = decision.choice == "reject"
}
