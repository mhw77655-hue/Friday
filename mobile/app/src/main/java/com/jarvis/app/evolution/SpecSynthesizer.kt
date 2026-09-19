package com.jarvis.app.evolution

import com.jarvis.app.genome.Genome
import com.jarvis.app.mutant.AlgorithmSpec
import com.jarvis.app.mutant.CachedSpec
import com.jarvis.app.mutant.CandidateImplementation
import com.jarvis.app.mutant.DeterministicSpec
import com.jarvis.app.mutant.SpecOutput
import com.jarvis.app.mutant.TestCase
import kotlin.math.roundToInt

/**
 * The declarative "behavior" of a capability — the structured specification a
 * synthesizer turns into a real executable spec (§5: generation operates on
 * structured system specifications, not raw source text).
 *
 * A future LLM/coding-agent synthesizer plugs in behind the same
 * [SpecSynthesizer] interface; this foundation demonstrates the loop with
 * harmless local capabilities whose logic the descriptor fully determines.
 * Nothing here pretends to be general code synthesis — it is the *contract*,
 * exercised end-to-end with real execution.
 */
sealed interface Behavior {

    /** Scale a Double input from [sourceMin,sourceMax] into [0,1] (clamped). */
    data class Normalize(val sourceMin: Double, val sourceMax: Double) : Behavior

    /** Clamp a Double input into [min,max]. */
    data class Clamp(val min: Double, val max: Double) : Behavior

    /** Invert a Double input within the domain [0,domain]. */
    data class Invert(val domain: Double) : Behavior

    /** Reverse the order of words (split by delimiter). */
    data class ReverseWords(val delimiter: String = " ") : Behavior

    /** Map a lookup table; unknown keys yield the default. */
    data class Lookup(val table: Map<String, String>, val default: String) : Behavior

    val capabilityName: String
        get() = when (this) {
            is Normalize -> "normalize"
            is Clamp -> "clamp"
            is Invert -> "invert"
            is ReverseWords -> "reverse_words"
            is Lookup -> "lookup"
        }

    /** The input port this behavior reads (the genome's declared input). */
    val inputName: String
        get() = when (this) {
            is ReverseWords -> "text"
            is Lookup -> "key"
            else -> "value"
        }
}

/**
 * The concrete synthesis requirement handed to [SpecSynthesizer]s. Produced by
 * the Tool Builder from a [com.jarvis.app.builder.ToolSpecification].
 */
data class CapabilityRequirement(
    val capability: String,
    val description: String,
    val behavior: Behavior,
    val constraints: List<String> = emptyList(),
    val maxMemoryMb: Long = 64,
    val maxCpuPercent: Double = 10.0
)

/**
 * Pluggable candidate synthesizer (§5, §6): a provider that turns a
 * [CapabilityRequirement] into one independent candidate implementation +
 * its generated tests. Each synthesizer is one *strategy flavor*; the
 * Candidate Generator runs several and the fitness function picks the winner.
 */
interface SpecSynthesizer {
    val name: String
    val strategy: AlgorithmSpec.Strategy

    /** Whether this synthesizer can produce a candidate for the requirement. */
    fun canHandle(genome: Genome, requirement: CapabilityRequirement): Boolean

    /** Produce the executable candidate + its generated test specification (§12). */
    suspend fun synthesize(genome: Genome, requirement: CapabilityRequirement): CandidateImplementation
}

/**
 * Shared engine for the deterministic foundation synthesizers: translates a
 * [Behavior] into an executable spec and its test suite. The same behavior is
 * implemented three independent ways (exact / cached / stepped-heuristic) so
 * multi-hybrid evolution (§6) can compare genuinely different candidates
 * against the same objective.
 */
object BehaviorSynthesizer {

    val SUPPORTED: Set<String> = setOf("normalize", "clamp", "invert", "reverse_words", "lookup")

    fun supports(behavior: Behavior): Boolean = behavior.capabilityName in SUPPORTED

    /** Declarative source — persisted into the candidate workspace. */
    fun sourceFor(behavior: Behavior): String = when (behavior) {
        is Behavior.Normalize ->
            "normalize(v) = clamp((v - ${behavior.sourceMin}) / (${behavior.sourceMax} - ${behavior.sourceMin}), 0, 1)"
        is Behavior.Clamp -> "clamp(v, ${behavior.min}, ${behavior.max})"
        is Behavior.Invert -> "invert(v) = ${behavior.domain} - v"
        is Behavior.ReverseWords -> "reverse_words(text, delimiter=\"${behavior.delimiter}\")"
        is Behavior.Lookup -> "lookup(key, ${behavior.table}, default=\"${behavior.default}\")"
    }

    /** Build the executable spec for a behavior in the requested strategy. */
    fun specFor(
        behavior: Behavior,
        strategy: AlgorithmSpec.Strategy = AlgorithmSpec.Strategy.DETERMINISTIC
    ): AlgorithmSpec {
        val deterministic = DeterministicSpec(
            capability = behavior.capabilityName,
            description = "Deterministic implementation of ${behavior.capabilityName}",
            source = sourceFor(behavior),
            strategy = AlgorithmSpec.Strategy.DETERMINISTIC,
            fn = { input -> evaluate(behavior, input, exact = true) }
        )
        return when (strategy) {
            AlgorithmSpec.Strategy.CACHED -> CachedSpec(
                behavior.capabilityName, deterministic.description, deterministic.source, deterministic
            )
            AlgorithmSpec.Strategy.HEURISTIC -> DeterministicSpec(
                capability = behavior.capabilityName,
                description = "Heuristic (stepped) approximation of ${behavior.capabilityName}",
                source = "heuristic: ${sourceFor(behavior)}",
                strategy = AlgorithmSpec.Strategy.HEURISTIC,
                fn = { input -> evaluate(behavior, input, exact = false) }
            )
            else -> deterministic
        }
    }

    /** The generated test specification for a behavior (§12: unit + boundary + failure). */
    fun testsFor(behavior: Behavior): List<TestCase> = when (behavior) {
        is Behavior.Normalize -> listOf(
            TestCase("normalize_min", mapOf("value" to behavior.sourceMin), mapOf("result" to 0.0)),
            TestCase("normalize_max", mapOf("value" to behavior.sourceMax), mapOf("result" to 1.0)),
            TestCase("normalize_mid", mapOf("value" to (behavior.sourceMin + behavior.sourceMax) / 2.0), mapOf("result" to 0.5)),
            TestCase("normalize_out_of_range_clamps", mapOf("value" to behavior.sourceMax + 10.0), mapOf("result" to 1.0)),
            TestCase("normalize_missing_input_fails", emptyMap(), expectedError = "missing input", isFailureCase = true)
        )
        is Behavior.Clamp -> listOf(
            TestCase("clamp_low", mapOf("value" to behavior.min - 5.0), mapOf("result" to behavior.min)),
            TestCase("clamp_high", mapOf("value" to behavior.max + 5.0), mapOf("result" to behavior.max)),
            TestCase("clamp_inside", mapOf("value" to (behavior.min + behavior.max) / 2.0), mapOf("result" to (behavior.min + behavior.max) / 2.0)),
            TestCase("clamp_missing_input_fails", emptyMap(), expectedError = "missing input", isFailureCase = true)
        )
        is Behavior.Invert -> listOf(
            TestCase("invert_zero", mapOf("value" to 0.0), mapOf("result" to behavior.domain)),
            TestCase("invert_domain", mapOf("value" to behavior.domain), mapOf("result" to 0.0)),
            TestCase("invert_missing_input_fails", emptyMap(), expectedError = "missing input", isFailureCase = true)
        )
        is Behavior.ReverseWords -> listOf(
            TestCase("reverse_two_words", mapOf("text" to "hello world"), mapOf("result" to "world hello")),
            TestCase("reverse_single_word", mapOf("text" to "jarvis"), mapOf("result" to "jarvis")),
            TestCase("reverse_empty", mapOf("text" to ""), mapOf("result" to "")),
            TestCase("reverse_missing_input_fails", emptyMap(), expectedError = "missing input", isFailureCase = true)
        )
        is Behavior.Lookup -> listOf(
            TestCase(
                "lookup_known",
                mapOf("key" to behavior.table.keys.firstOrNull().orEmpty()),
                mapOf("result" to behavior.table.values.firstOrNull().orEmpty())
            ),
            TestCase("lookup_unknown_default", mapOf("key" to "missing"), mapOf("result" to behavior.default)),
            TestCase("lookup_missing_input_fails", emptyMap(), expectedError = "missing input", isFailureCase = true)
        )
    }

    /** §11: every generated suite must include at least one failure-path test. */
    fun hasFailureTests(behavior: Behavior): Boolean = testsFor(behavior).any { it.isFailureCase || it.expectedError != null }

    private fun evaluate(behavior: Behavior, input: Map<String, Any>, exact: Boolean): SpecOutput = try {
        val out: Any = when (behavior) {
            is Behavior.Normalize -> {
                val v = num(input, "value") ?: return SpecOutput.Failure("missing input 'value'")
                val raw = (v - behavior.sourceMin) / (behavior.sourceMax - behavior.sourceMin)
                if (exact) raw.coerceIn(0.0, 1.0)
                else (0.25 * (raw / 0.25).roundToInt()).coerceIn(0.0, 1.0)
            }
            is Behavior.Clamp -> {
                val v = num(input, "value") ?: return SpecOutput.Failure("missing input 'value'")
                val c = v.coerceIn(behavior.min, behavior.max)
                if (exact) c else (c * 100).roundToInt() / 100.0
            }
            is Behavior.Invert -> {
                val v = num(input, "value") ?: return SpecOutput.Failure("missing input 'value'")
                val r = behavior.domain - v
                if (exact) r else (r * 100).roundToInt() / 100.0
            }
            is Behavior.ReverseWords -> {
                val text = input["text"] as? String ?: return SpecOutput.Failure("missing input 'text'")
                text.split(behavior.delimiter).filter { it.isNotEmpty() }.reversed().joinToString(behavior.delimiter)
            }
            is Behavior.Lookup -> {
                val key = input["key"] as? String ?: return SpecOutput.Failure("missing input 'key'")
                behavior.table[key] ?: behavior.default
            }
        }
        SpecOutput.Success(mapOf("result" to out))
    } catch (t: Throwable) {
        SpecOutput.Failure("spec crashed: ${t.message}")
    }

    private fun num(input: Map<String, Any>, key: String): Double? =
        (input[key] as? Number)?.toDouble()
}

/**
 * Strategy-flavored synthesizers (§6: Candidate A deterministic, B cached,
 * C heuristic). Each produces an independent candidate for the same objective.
 */
class DeterministicSpecSynthesizer : SpecSynthesizer {
    override val name: String = "deterministic"
    override val strategy: AlgorithmSpec.Strategy = AlgorithmSpec.Strategy.DETERMINISTIC
    override fun canHandle(genome: Genome, requirement: CapabilityRequirement): Boolean =
        BehaviorSynthesizer.supports(requirement.behavior)
    override suspend fun synthesize(genome: Genome, requirement: CapabilityRequirement): CandidateImplementation =
        CandidateImplementation(
            spec = BehaviorSynthesizer.specFor(requirement.behavior, AlgorithmSpec.Strategy.DETERMINISTIC),
            tests = BehaviorSynthesizer.testsFor(requirement.behavior),
            language = "spec",
            sourceCode = BehaviorSynthesizer.sourceFor(requirement.behavior),
            dependencies = listOf("kotlin-runtime")
        )
}

class CachedSpecSynthesizer : SpecSynthesizer {
    override val name: String = "cached"
    override val strategy: AlgorithmSpec.Strategy = AlgorithmSpec.Strategy.CACHED
    override fun canHandle(genome: Genome, requirement: CapabilityRequirement): Boolean =
        BehaviorSynthesizer.supports(requirement.behavior)
    override suspend fun synthesize(genome: Genome, requirement: CapabilityRequirement): CandidateImplementation =
        CandidateImplementation(
            spec = BehaviorSynthesizer.specFor(requirement.behavior, AlgorithmSpec.Strategy.CACHED),
            tests = BehaviorSynthesizer.testsFor(requirement.behavior),
            language = "spec",
            sourceCode = "cached: ${BehaviorSynthesizer.sourceFor(requirement.behavior)}",
            dependencies = listOf("kotlin-runtime")
        )
}

class HeuristicSpecSynthesizer : SpecSynthesizer {
    override val name: String = "heuristic"
    override val strategy: AlgorithmSpec.Strategy = AlgorithmSpec.Strategy.HEURISTIC
    override fun canHandle(genome: Genome, requirement: CapabilityRequirement): Boolean =
        BehaviorSynthesizer.supports(requirement.behavior)
    override suspend fun synthesize(genome: Genome, requirement: CapabilityRequirement): CandidateImplementation =
        CandidateImplementation(
            spec = BehaviorSynthesizer.specFor(requirement.behavior, AlgorithmSpec.Strategy.HEURISTIC),
            tests = BehaviorSynthesizer.testsFor(requirement.behavior),
            language = "spec",
            sourceCode = "heuristic: ${BehaviorSynthesizer.sourceFor(requirement.behavior)}",
            dependencies = listOf("kotlin-runtime")
        )
}

/** The standard multi-hybrid synthesizer set (§6). */
val DEFAULT_SYNTHESIZERS: List<SpecSynthesizer> = listOf(
    DeterministicSpecSynthesizer(),
    CachedSpecSynthesizer(),
    HeuristicSpecSynthesizer()
)
