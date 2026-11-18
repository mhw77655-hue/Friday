package com.jarvis.app.builder

import com.jarvis.app.cognitive.capability.Capability
import com.jarvis.app.cognitive.capability.CapabilityDescriptor
import com.jarvis.app.cognitive.capability.CapabilityResourceProfile
import com.jarvis.app.cognitive.capability.RiskLevel
import com.jarvis.app.cognitive.execution.ActionFailure
import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.cognitive.execution.ExecutionResult
import com.jarvis.app.evolution.Behavior
import org.json.JSONObject

/**
 * Exposes the on-device ToolBuilder to Friday's primary CapabilityFabric.
 * The builder accepts a structured behavioral objective and produces only
 * restricted SynthProgram capabilities; it cannot compile or load Kotlin/DEX.
 */
class ToolBuilderCapability(
    private val builder: ToolBuilder
) : Capability {
    override val descriptor = CapabilityDescriptor(
        id = "friday.builder.runtime",
        name = "Friday Builder",
        description = "Discover capability gaps and synthesize, test, promote or inspect pure runtime capabilities on this device.",
        category = "development",
        resourceProfile = CapabilityResourceProfile(cpuCost = 0.25f, memoryCostMb = 96, optional = true),
        riskLevel = RiskLevel.MEDIUM,
        supportedOperations = setOf("builder.create_tool", "builder.describe")
    )

    override suspend fun invoke(request: ActionRequest): ExecutionResult {
        return when (request.action) {
            "builder.describe" -> ExecutionResult.ok(
                output = mapOf(
                    "contract" to "Pass spec_json with capability, description, behavior, constraints and resource budgets.",
                    "execution" to "Pure SynthProgram only; no arbitrary Kotlin, DEX, network or file access.",
                    "growth" to "Promoted capabilities are retained in Friday's private workspace and restored after process restart."
                )
            )
            "builder.create_tool" -> createTool(request)
            else -> ExecutionResult.fail(ActionFailure.Unsupported(request.action))
        }
    }

    private suspend fun createTool(request: ActionRequest): ExecutionResult {
        val raw = request.context["spec_json"]
            ?: return ExecutionResult.fail(
                ActionFailure.Error(
                    code = "BUILDER_SPEC_REQUIRED",
                    message = "builder.create_tool requires a JSON object in context.spec_json."
                )
            )
        if (raw.length > 16_384) {
            return ExecutionResult.fail(
                ActionFailure.Error("BUILDER_SPEC_TOO_LARGE", "Tool specification exceeds 16 KiB.")
            )
        }

        val spec = try {
            parseSpecification(JSONObject(raw))
        } catch (failure: Throwable) {
            return ExecutionResult.fail(
                ActionFailure.Error(
                    code = "BUILDER_INVALID_SPEC",
                    message = failure.message ?: "Tool specification is invalid."
                )
            )
        }

        val result = try {
            builder.build(spec)
        } catch (failure: Throwable) {
            return ExecutionResult.fail(
                ActionFailure.Error(
                    code = "BUILDER_EXECUTION_FAILED",
                    message = failure.message ?: "Tool build failed.",
                    recoverable = true
                )
            )
        }

        val output = linkedMapOf(
            "status" to result.status,
            "built" to result.built.toString(),
            "gap_detected" to result.gapDetected.toString(),
            "message" to result.message
        )
        result.providerId?.let { output["provider_id"] = it }
        result.genomeId?.let { output["genome_id"] = it }
        result.strategy?.let { output["strategy"] = it }
        result.fitness?.let { output["fitness"] = it.toString() }
        result.rollbackPoint?.let { output["rollback_point"] = it }

        val evidence = mapOf(
            "builder" to "friday.builder.runtime",
            "candidate_count" to result.candidates.size.toString(),
            "research_candidates" to result.researchCandidates.size.toString()
        )
        return if (result.built || !result.gapDetected) {
            ExecutionResult.ok(output = output, evidence = evidence)
        } else {
            ExecutionResult.fail(
                ActionFailure.Error(
                    code = "BUILDER_NOT_PROMOTED",
                    message = result.message,
                    recoverable = result.status in setOf("EVOLUTION_REJECTED", "RESOURCE_STATE_UNAVAILABLE"),
                    evidence = evidence
                )
            )
        }
    }

    private fun parseSpecification(json: JSONObject): ToolSpecification {
        val capability = json.getString("capability")
        val description = json.getString("description")
        val behaviorJson = json.getJSONObject("behavior")
        val type = behaviorJson.getString("type").lowercase()
        val behavior = when (type) {
            "normalize" -> Behavior.Normalize(
                sourceMin = behaviorJson.getDouble("source_min"),
                sourceMax = behaviorJson.getDouble("source_max")
            )
            "clamp" -> Behavior.Clamp(
                min = behaviorJson.getDouble("min"),
                max = behaviorJson.getDouble("max")
            )
            "invert" -> Behavior.Invert(domain = behaviorJson.getDouble("domain"))
            "reverse_words" -> Behavior.ReverseWords(
                delimiter = behaviorJson.optString("delimiter", " ")
            )
            "lookup" -> {
                val tableJson = behaviorJson.optJSONObject("table") ?: JSONObject()
                val table = LinkedHashMap<String, String>()
                val keys = tableJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    table[key] = tableJson.getString(key)
                }
                Behavior.Lookup(table, behaviorJson.optString("default", ""))
            }
            "custom" -> Behavior.Custom(
                capability = capability,
                port = behaviorJson.optString("port", "input"),
                type = behaviorJson.optString("value_type", "String")
            )
            else -> throw IllegalArgumentException("unsupported behavior type: $type")
        }
        val constraintsJson = json.optJSONArray("constraints")
        val constraints = if (constraintsJson == null) emptyList() else
            (0 until constraintsJson.length()).map { constraintsJson.getString(it) }
        return ToolSpecification(
            capability = capability,
            description = description,
            behavior = behavior,
            constraints = constraints,
            maxMemoryMb = json.optLong("max_memory_mb", 64L),
            maxCpuPercent = json.optDouble("max_cpu_percent", 10.0),
            researchFirst = json.optBoolean("research_first", false),
            requireVerifiedResearch = json.optBoolean("require_verified_research", false)
        )
    }
}
