package com.jarvis.app.mutant

/**
 * The executable unit of a generated candidate.
 *
 * §5: mutation operates on structured system specifications wherever
 * possible. On a phone there is no embedded Kotlin compiler, so a generated
 * candidate is not arbitrary runtime-compiled source — it is a structured,
 * runtime-interpretable specification. This is the cheapest mechanism that
 * is still *executed for real*: tests genuinely run the candidate's
 * algorithm against fixtures, measure its latency, and its failures are
 * observable. An LLM/compiler-backed SynthesisProvider can later emit
 * native code for a Termux/container backend behind the same interface.
 *
 * Every spec must be loud: it returns [SpecOutput.Failure] (or crashes,
 * which [DeterministicSpec] converts to a loud Failure) rather than
 * silently returning a wrong answer. §11: a candidate that fails silently
 * is automatically invalid.
 */
sealed interface AlgorithmSpec {
    /** The capability this spec implements. */
    val capability: String

    /** Which generation strategy produced it (multi-hybrid evolution). */
    val strategy: Strategy

    /** Human-readable description (also the provenance record). */
    val description: String

    /** The declarative "source" — persisted to the candidate workspace. */
    val source: String

    /** Execute the spec against an input map, returning a loud result. */
    fun execute(input: Map<String, Any>): SpecOutput

    enum class Strategy {
        /** Direct, exact, cheapest — the preferred tier. */
        DETERMINISTIC,
        /** Memoized / cached results — cheap repeat cost. */
        CACHED,
        /** Approximate or synonym-tolerant — used when exact fails. */
        HEURISTIC
    }

    companion object {
        val ALL_STRATEGIES: List<Strategy> = listOf(
            Strategy.DETERMINISTIC, Strategy.CACHED, Strategy.HEURISTIC
        )
    }
}

/**
 * A pure deterministic function wrapped as a spec. The [fn] must be pure
 * (no I/O, no global mutation) so it is safe to run inside the sandbox.
 * Any exception it throws is converted to a loud [SpecOutput.Failure] — a
 * crash is never silent.
 */
data class DeterministicSpec(
    override val capability: String,
    override val description: String,
    override val source: String,
    override val strategy: AlgorithmSpec.Strategy = AlgorithmSpec.Strategy.DETERMINISTIC,
    private val fn: (Map<String, Any>) -> SpecOutput
) : AlgorithmSpec {
    override fun execute(input: Map<String, Any>): SpecOutput = try {
        fn(input)
    } catch (t: Throwable) {
        SpecOutput.Failure("spec crashed: ${t.message ?: t.javaClass.simpleName}")
    }
}

/** A memoized (cached) spec: identical pure function plus a result cache. */
class CachedSpec(
    override val capability: String,
    override val description: String,
    override val source: String,
    private val delegate: DeterministicSpec
) : AlgorithmSpec {
    override val strategy: AlgorithmSpec.Strategy = AlgorithmSpec.Strategy.CACHED

    private val cache = object : java.util.concurrent.ConcurrentHashMap<Map<String, Any>, SpecOutput>() {}
    private val cacheHits = java.util.concurrent.atomic.AtomicLong(0)
    val hitCount: Long get() = cacheHits.get()

    override fun execute(input: Map<String, Any>): SpecOutput {
        cache[input]?.let { cacheHits.incrementAndGet(); return it }
        val output = delegate.execute(input)
        cache[input] = output
        return output
    }
}

/** The two kinds of loud spec result. */
sealed interface SpecOutput {
    data class Success(val output: Map<String, Any>) : SpecOutput
    data class Failure(val error: String) : SpecOutput
}

/**
 * One generated test case. The synthesis provider emits these alongside the
 * implementation, so every generated system carries its own test
 * specification (§12).
 *
 *  - [expected] set            → the spec must produce this output.
 *  - [expectedError] set       → the spec must FAIL loudly with that error
 *                                (failure-path test, §11).
 */
data class TestCase(
    val name: String,
    val input: Map<String, Any>,
    val expected: Map<String, Any> = emptyMap(),
    val expectedError: String? = null,
    val isFailureCase: Boolean = false,
    val tolerance: Double = 1e-6
)

/** The full synthesized artifact for one candidate: spec + its tests. */
data class CandidateImplementation(
    val spec: AlgorithmSpec,
    val tests: List<TestCase>,
    val language: String = "spec",
    val sourceCode: String = spec.source,
    val dependencies: List<String> = emptyList()
)

/** Result of running one test case against a spec. */
data class TestRunResult(
    val name: String,
    val passed: Boolean,
    val durationMs: Long,
    val expected: Any? = null,
    val actual: Any? = null,
    val error: String? = null
)
