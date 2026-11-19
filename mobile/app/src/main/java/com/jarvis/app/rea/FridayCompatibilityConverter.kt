package com.jarvis.app.rea

import com.jarvis.app.model.remote.CouncilRequest
import com.jarvis.app.model.remote.ProviderCouncil
import org.json.JSONArray
import org.json.JSONObject

enum class FridayIntegrationKind {
    CAPABILITY_ADAPTER, DATA_TRANSFORMER, API_CONNECTOR, ANDROID_BRIDGE, RESEARCH_ONLY
}

data class FridayCompatibilityRecipe(
    val title: String, val integrationKind: FridayIntegrationKind,
    val transferablePrinciple: String, val fridayInterfaces: List<String>,
    val implementationPlan: List<String>, val permissionRequirements: List<String>,
    val risk: String, val evidenceAndUnknowns: List<String>
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title).put("integrationKind", integrationKind.name)
        .put("transferablePrinciple", transferablePrinciple)
        .put("fridayInterfaces", JSONArray(fridayInterfaces))
        .put("implementationPlan", JSONArray(implementationPlan))
        .put("permissionRequirements", JSONArray(permissionRequirements))
        .put("risk", risk).put("evidenceAndUnknowns", JSONArray(evidenceAndUnknowns))
}

fun interface ReverseEngineeringAdviser {
    suspend fun advise(prompt: String): String?
}

/** Friday's existing authenticated multi-provider council is the REA analyst, never the executor. */
class ProviderCouncilReverseEngineeringAdviser(private val council: ProviderCouncil) : ReverseEngineeringAdviser {
    override suspend fun advise(prompt: String): String? {
        // Two independent reviewers first; a separate provider call reconciles
        // disagreements into the strict recipe schema consumed by the converter.
        val peerReview = council.consult(
            CouncilRequest(task = prompt, includeProjectContext = true,
                maxOutputTokens = 900, maxAdvisers = 2, independentReviewRequested = true)
        )
        if (!peerReview.succeeded) return null
        val reviews = peerReview.opinions.joinToString("\n\n--- INDEPENDENT REVIEW ---\n\n") { opinion ->
            "REVIEWER " + opinion.providerLabel + " (" + opinion.modelId + ")\n" + opinion.answer
        }
        val synthesis = council.consult(
            CouncilRequest(
                task = "Reconcile these independent reverse-engineering reviews. Resolve factual disagreement conservatively. Return exactly one strict JSON object matching the requested recipe schema; no markdown or executable code.\n\nORIGINAL TASK:\n" +
                    prompt + "\n\nREVIEWS:\n" + reviews,
                includeProjectContext = false,
                maxOutputTokens = 1_200,
                maxAdvisers = 1
            )
        )
        return synthesis.opinions.firstOrNull()?.answer
    }
}

/** Converts APK structural evidence into a Friday-facing engineering recipe. */
class FridayCompatibilityConverter(private val adviser: ReverseEngineeringAdviser) {
    suspend fun convert(packageName: String, reports: List<ApkArtifactInspector.Report>): FridayCompatibilityRecipe? {
        if (!PACKAGE_NAME.matches(packageName) || reports.isEmpty() || reports.size > 24) return null
        val evidence = JSONObject().put("packageName", packageName)
            .put("apkFiles", JSONArray(reports.take(4).map { it.toJson() })).toString()
        val prompt = """
            You are Friday's reverse-engineering and compatibility analyst.
            Analyze the bounded APK structural evidence. Infer transferable behaviours
            only where evidence supports them. File names do not prove implementation behaviour.
            Produce ONE Friday-compatible integration recipe as strict JSON only.
            Allowed integrationKind values: CAPABILITY_ADAPTER, DATA_TRANSFORMER,
            API_CONNECTOR, ANDROID_BRIDGE, RESEARCH_ONLY.
            Do not copy proprietary source, bypass licences, execute arbitrary shell,
            access hidden data, extract credentials, or escalate permissions.
            Prefer public APIs and documented interfaces. State unknowns explicitly.
            No executable code in this recipe.
            Required keys: title, integrationKind, transferablePrinciple, fridayInterfaces,
            implementationPlan, permissionRequirements, risk, evidenceAndUnknowns.
            Keep strings under 700 characters and arrays to at most 8 items.
            APK evidence:
            $evidence
        """.trimIndent()
        return parse(adviser.advise(prompt) ?: return null)
    }

    private fun parse(raw: String): FridayCompatibilityRecipe? = runCatching {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = JSONObject(raw.substring(start, end + 1))
        val allowed = setOf("title", "integrationKind", "transferablePrinciple", "fridayInterfaces",
            "implementationPlan", "permissionRequirements", "risk", "evidenceAndUnknowns")
        if (json.keys().asSequence().toSet() != allowed) return null
        val kind = FridayIntegrationKind.valueOf(json.getString("integrationKind"))
        val title = bounded(json.getString("title"), 3, 120) ?: return null
        val principle = bounded(json.getString("transferablePrinciple"), 10, 700) ?: return null
        val risk = bounded(json.getString("risk"), 3, 500) ?: return null
        FridayCompatibilityRecipe(title, kind, principle,
            array(json, "fridayInterfaces", 1, 8) ?: return null,
            array(json, "implementationPlan", 1, 8) ?: return null,
            array(json, "permissionRequirements", 0, 8) ?: return null,
            risk, array(json, "evidenceAndUnknowns", 1, 8) ?: return null)
    }.getOrNull()

    private fun bounded(value: String, min: Int, max: Int): String? =
        value.trim().takeIf { it.length in min..max }

    private fun array(json: JSONObject, key: String, min: Int, max: Int): List<String>? {
        val values = json.optJSONArray(key) ?: return null
        if (values.length() !in min..max) return null
        return (0 until values.length()).map { index ->
            bounded(values.getString(index), 2, 700) ?: return null
        }
    }

    companion object {
        private val PACKAGE_NAME = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")
    }
}
