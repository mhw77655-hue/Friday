package com.jarvis.app.selfrepair

import com.jarvis.app.model.GenerateRequest
import com.jarvis.app.model.GenerateResult
import com.jarvis.app.model.FinishReason
import com.jarvis.app.model.Message
import com.jarvis.app.model.MessageRole
import com.jarvis.app.model.ModelManager

/**
 * The behavioral requirement a repair must satisfy — the ORIGINAL intent the
 * failing component was supposed to fulfill, plus its evidence-backed
 * constraints. Sent to the model alongside the raw failure transcripts.
 */
data class RepairRequirement(
    /** Component under repair, e.g. "cognitive/planning GoalPlanner.replan". */
    val component: String,
    /** The original requirement, in behavior terms (not implementation terms). */
    val requirement: String,
    /** Hard constraints any repair must respect. */
    val constraints: List<String> = emptyList()
)

/**
 * DIAGNOSE step output — parsed from the model's structured response. Every
 * field is grounded in the observed transcripts; nothing here is a hardcoded
 * rule: if the model's answer cannot be parsed, [DiagnosisEngine] fails loudly.
 */
data class RepairDiagnosis(
    val rootCause: String,
    val faultyUnit: String,
    val fixStrategy: String,
    val rationale: String
) {
    /** Compact form embedded into the repair prompt. */
    fun render(): String = buildString {
        appendLine("ROOT CAUSE: $rootCause")
        appendLine("FAULTY UNIT: $faultyUnit")
        appendLine("FIX STRATEGY: $fixStrategy")
        append("RATIONALE: $rationale")
    }
}

/**
 * DIAGNOSE step of the self-repair loop.
 *
 * Sends the ACTUAL test failure output (messages + stack traces captured by
 * [GradleXmlFailureObserver]) together with the original requirement to the
 * model authority ([ModelManager] — the same model client the rest of the
 * organism uses) and parses the structured diagnosis from the response.
 *
 * The completion seam is injectable for tests; the production constructor
 * wires `ModelManager.generate` directly.
 */
class DiagnosisEngine(
    private val complete: suspend (GenerateRequest) -> GenerateResult,
    private val maxTokens: Int = 2048,
    private val temperature: Float = 0.1f
) {

    /** Production wiring: the live model authority used by JarvisEngine. */
    constructor(
        modelManager: ModelManager,
        maxTokens: Int = 2048,
        temperature: Float = 0.1f
    ) : this(complete = { request -> modelManager.generate(request) },
             maxTokens = maxTokens, temperature = temperature)

    /** The most recent prompt sent to the model (diagnostics/tests). */
    @Volatile
    var lastPrompt: String? = null
        private set

    suspend fun diagnose(requirement: RepairRequirement, observation: TestSuiteObservation): RepairDiagnosis {
        val prompt = buildPrompt(requirement, observation)
        lastPrompt = prompt
        val result = complete(
            GenerateRequest(
                messages = listOf(
                    Message(MessageRole.SYSTEM, SYSTEM_PROMPT),
                    Message(MessageRole.USER, prompt)
                ),
                maxTokens = maxTokens,
                temperature = temperature
            )
        )
        if (result.finishReason == FinishReason.ERROR || result.content.isBlank()) {
            throw IllegalStateException(
                "diagnosis failed: ${result.finishReason} / blank content"
            )
        }
        return parseDiagnosis(result.content)
    }

    internal fun buildPrompt(requirement: RepairRequirement, observation: TestSuiteObservation): String =
        buildString {
            appendLine("COMPONENT UNDER REPAIR: ${requirement.component}")
            appendLine()
            appendLine("ORIGINAL REQUIREMENT:")
            appendLine(requirement.requirement)
            appendLine()
            if (requirement.constraints.isNotEmpty()) {
                appendLine("CONSTRAINTS:")
                requirement.constraints.forEach { appendLine("- $it") }
                appendLine()
            }
            appendLine("OBSERVED TEST FAILURES (real runner output):")
            appendLine(observation.renderFailures())
            appendLine()
            appendLine(
                """
                Respond with EXACTLY these four lines, no prose before or after:
                ROOT_CAUSE: <one paragraph grounded in the transcripts above>
                FAULTY_UNIT: <class or function most responsible>
                FIX_STRATEGY: <concrete minimal change that satisfies the requirement and constraints>
                RATIONALE: <why this is consistent with ALL observed failures>
                """.trimIndent()
            )
        }.trimEnd() + "\n"

    internal fun parseDiagnosis(text: String): RepairDiagnosis {
        fun section(tag: String): String {
            val regex = Regex("(?im)^\\s*$tag:\\s*(.+)$")
            val match = regex.find(text)
                ?: throw IllegalStateException("diagnosis response missing $tag line")
            return match.groupValues[1].trim()
        }
        val rootCause = section("ROOT_CAUSE")
        val faultyUnit = section("FAULTY_UNIT")
        val fixStrategy = section("FIX_STRATEGY")
        val rationale = section("RATIONALE")
        if (rootCause.isBlank() || faultyUnit.isBlank() || fixStrategy.isBlank()) {
            throw IllegalStateException("diagnosis response has empty sections")
        }
        return RepairDiagnosis(
            rootCause = rootCause,
            faultyUnit = faultyUnit,
            fixStrategy = fixStrategy,
            rationale = rationale
        )
    }

    private companion object {
        val SYSTEM_PROMPT = """
            You are the diagnosis engine of JARVIS's self-repair loop. You
            receive a behavioral requirement, hard constraints, and REAL test
            failure transcripts (exact assertion messages and stack traces).
            Identify the root cause consistent with ALL failures, name the
            faulty unit, and prescribe the minimal concrete repair strategy.
            Ground every claim in the transcripts; never invent evidence.
        """.trimIndent()
    }
}
