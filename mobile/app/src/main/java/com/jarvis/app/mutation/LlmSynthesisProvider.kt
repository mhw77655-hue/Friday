package com.jarvis.app.mutation

import com.jarvis.app.mutant.SynthProgram
import com.jarvis.app.model.GenerateRequest
import com.jarvis.app.model.GenerateResult
import com.jarvis.app.model.FinishReason
import com.jarvis.app.model.Message
import com.jarvis.app.model.MessageRole
import com.jarvis.app.model.ModelManager

/**
 * The REAL LLM-backed [SynthesisProvider] — the boundary between the
 * evolution system and actual code generation.
 *
 * Unlike the deterministic strategy flavors in `SpecSynthesizer` (which hand
 * back hard-coded closures for 5 fixed operations), this provider:
 *
 *   1. builds a genuine prompt describing the required input/output behavior,
 *   2. calls the live model authority ([ModelManager] — the same model client
 *      the rest of the organism uses; no second model stack exists here),
 *   3. parses the generated text into an executable [SynthProgram]
 *      candidate + its generated test specification (§12).
 *
 * Generation is NOT closed over a fixed operation list: any capability whose
 * behavior can be expressed in the synth language can be synthesized.
 */
class LlmSynthesisProvider(
    /** The completion seam; production wires [ModelManager.generate]. */
    private val complete: suspend (GenerateRequest) -> GenerateResult,
    private val maxTokens: Int = 1024,
    private val temperature: Float = 0.2f
) : SynthesisProvider {

    /** Production wiring: the live model authority used by JarvisEngine. */
    constructor(
        modelManager: ModelManager,
        maxTokens: Int = 1024,
        temperature: Float = 0.2f
    ) : this(complete = { request -> modelManager.generate(request) },
             maxTokens = maxTokens, temperature = temperature)

    override val name: String = "llm-synthesis"

    override val isAvailable: Boolean = true

    override val supportedCapabilities: Set<String> =
        setOf("synth", "kotlin")

    private var lastUsedMs: Long = 0
    private var lastError: String? = null

    /** The most recent prompt sent to the model (diagnostics/tests). */
    @Volatile
    var lastPrompt: String? = null
        private set

    /**
     * Generate a candidate implementation as text. The artifact's source is
     * the ```synth program body (assignments + TEST lines) produced by the
     * model — parsed and validated loudly by [SynthProgram.parse].
     */
    suspend fun generateProgram(request: SynthesisRequest): SynthProgram {
        val prompt = buildPrompt(request)
        lastPrompt = prompt
        return complete(
            GenerateRequest(
                messages = listOf(
                    Message(MessageRole.SYSTEM, SYSTEM_PROMPT),
                    Message(MessageRole.USER, prompt)
                ),
                maxTokens = maxTokens,
                temperature = temperature,
                stopSequences = listOf("```")
            )
        ).let { result ->
            if (result.finishReason == FinishReason.ERROR || result.content.isBlank()) {
                throw IllegalStateException(
                    "LLM synthesis failed: ${result.finishReason} / blank content"
                )
            }
            SynthProgram.parse(result.content)
        }
    }

    /**
     * REPAIR generation: when the request carries a diagnosis, produce a
     * concrete source repair instead of a synth program. The response must be
     * ONE fenced ```patch block of FILE:/FIND:/REPLACE: edits (parsed by
     * [com.jarvis.app.selfrepair.SourcePatch]); blank or garbage responses
     * fail loudly.
     */
    suspend fun generateRepair(request: SynthesisRequest): String {
        require(request.diagnosis != null) {
            "generateRepair requires a diagnosis — use generateProgram for fresh synthesis"
        }
        val prompt = buildPrompt(request)
        lastPrompt = prompt
        val result = complete(
            GenerateRequest(
                messages = listOf(
                    Message(MessageRole.SYSTEM, REPAIR_SYSTEM_PROMPT),
                    Message(MessageRole.USER, prompt + REPAIR_FORMAT_INSTRUCTIONS)
                ),
                maxTokens = maxTokens * 4,
                temperature = temperature
            )
        )
        if (result.finishReason == FinishReason.ERROR || result.content.isBlank()) {
            throw IllegalStateException(
                "LLM repair failed: ${result.finishReason} / blank content"
            )
        }
        return result.content.trim()
    }

    override suspend fun synthesize(request: SynthesisRequest): SynthesisResult = try {
        val program = generateProgram(request)
        lastUsedMs = System.currentTimeMillis()
        lastError = null
        SynthesisResult(
            success = true,
            implementation = ImplementationArtifact(
                language = "synth",
                sourceCode = program.source +
                    if (program.tests.isEmpty()) "" else "\n" + formatTests(program.tests),
                dependencies = listOf("kotlin-runtime")
            ),
            description = program.capability.ifBlank { request.description }
        )
    } catch (t: Throwable) {
        lastError = t.message ?: t.javaClass.simpleName
        SynthesisResult(success = false, description = "", error = lastError)
    }

    override suspend fun health(): SynthesisHealth = SynthesisHealth(
        available = isAvailable,
        latencyMs = -1,
        lastUsedMs = lastUsedMs,
        error = lastError
    )

    // ------------------------------------------------------------------
    // Prompt construction — a real behavioral description of the need.
    // ------------------------------------------------------------------

    internal fun buildPrompt(request: SynthesisRequest): String {
        val ports = if (request.inputPorts.isNotEmpty()) {
            request.inputPorts.joinToString(", ") { "$it (input)" }
        } else {
            "the input variables named or implied by the description"
        }
        val constraints = request.constraints.takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { "- $it" }
            ?: "- none beyond the language rules below"
        return buildString {
            appendLine("CAPABILITY: ${request.capability.ifBlank { "unspecified" }}")
            appendLine("DESCRIPTION: ${request.description}")
            if (request.diagnosis != null) {
                appendLine()
                appendLine("DIAGNOSIS (from the observe/diagnose step — ground the repair in this):")
                appendLine(request.diagnosis)
            }
            appendLine("INPUT PORTS: $ports")
            appendLine("CONSTRAINTS:")
            appendLine(constraints)
            appendLine()
            appendLine(LANGUAGE_REFERENCE.trimIndent())
            appendLine()
            appendLine(
                """
                Respond with ONE fenced block:
                ```synth
                # capability: <capability>
                <name> = <expression>
                ...
                result = <final expression>
                TEST <name> | input <var>=<value> ... | expect result=<value>
                TEST <bad-input-name> | input | error <expected error substring>
                ```
                Include at least one failure-path TEST. No prose outside the block.
                """.trimIndent()
            )
        }.trimEnd() + "\n"
    }

    private fun formatTests(tests: List<com.jarvis.app.mutant.TestCase>): String =
        tests.joinToString("\n") { t ->
            val fallbackError = t.expectedError ?: "failure"
            buildString {
                append("TEST ${t.name} | input ")
                if (t.input.isEmpty()) {
                    append("| error $fallbackError")
                } else {
                    append(t.input.entries.joinToString(" ") {
                        val v = it.value
                        "${it.key}=" + when (v) {
                            is String -> "\"$v\""
                            else -> v.toString()
                        }
                    })
                    if (t.expectedError != null) {
                        append(" | error ${t.expectedError}")
                    } else {
                        append(" | expect ")
                        append(t.expected.entries.joinToString(" ") { (k, v) ->
                            "$k=" + when (v) {
                                is String -> "\"$v\""
                                else -> v.toString()
                            }
                        })
                    }
                }
            }
        }

    private companion object {
        val SYSTEM_PROMPT = """
            You are the code synthesis engine of JARVIS, an autonomous system
            that closes its own capability gaps. You emit ONLY executable
            candidates in the `synth` program language — pure, total, loud.
            Never explain; never emit anything outside one ```synth block.
        """.trimIndent()

        val REPAIR_SYSTEM_PROMPT = """
            You are the repair engine of JARVIS's self-repair loop. You receive
            a behavioral requirement, a grounded diagnosis, and constraints.
            You emit ONLY one ```patch block containing minimal exact
            FILE:/FIND:/REPLACE: edits that implement the fix strategy. FIND
            blocks must match existing file content exactly and uniquely.
            Never explain; never emit anything outside the ```patch block.
        """.trimIndent()

        val REPAIR_FORMAT_INSTRUCTIONS = """

            Respond with ONE fenced block per edit:
            RATIONALE: <how this patch satisfies the diagnosis>
            ```patch
            FILE: relative/path/to/File.kt
            FIND:
            <exact existing lines, unique in that file>
            REPLACE:
            <replacement lines>
            ```
            Keep edits minimal; never reformat untouched code. No prose outside
            the RATIONALE line and the ```patch block(s).
        """.trimIndent()

        val LANGUAGE_REFERENCE = """
            synth LANGUAGE REFERENCE
            - One statement per line: name = expression. Comments start with #.
            - The final answer MUST be assigned to a variable named `result`.
            - Values: numbers (doubles), "strings", true/false, lists.
            - Operators: + - * / % ^ == != <= >= < > && || ! ; '+' also
              concatenates strings/lists.
            - Builtins (pure):
              min(a,b) max(a,b) abs(x) round(x) floor(x) ceil(x) sqrt(x)
              pow(a,b) clamp(v,lo,hi) num(s) str(x) sum(list) size(x)
              split(s,sep)#drops empty segments join(list,sep) reverse(s|list)
              upper(s) lower(s) trim(s) contains(hay,needle) replace(s,a,b)
              substring(s,start,end) index(seq,i) if(cond,a,b)
            - Referencing an input that was not provided fails LOUDLY with
              'undefined variable' — use that for failure-path tests.
        """.trimIndent()
    }
}
