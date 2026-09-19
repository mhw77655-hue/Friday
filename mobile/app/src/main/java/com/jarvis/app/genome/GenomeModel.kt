package com.jarvis.app.genome

/**
 * The developmental genome: a machine-readable specification from which a
 * JARVIS micro-system can be constructed, mutated, and evolved.
 *
 * A genome is immutable. Every mutation produces a *new* genome that
 * records its parent lineage, preserving the full evolutionary history.
 * The genome does not execute; it is the specification that an
 * [com.jarvis.app.microsystem.MicroSystem] is built from.
 */
data class Genome(
    val id: String,
    val version: Int = 1,
    val parentLineage: List<String> = listOf(Genome.ROOT_LINEAGE),
    val capabilities: Set<String> = emptySet(),
    val inputs: List<Port> = emptyList(),
    val outputs: List<Port> = emptyList(),
    val dependencies: Set<String> = emptySet(),
    val runtimeRequirements: RuntimeRequirements = RuntimeRequirements(),
    val modelRequirements: ModelRequirements = ModelRequirements(),
    val toolRequirements: List<ToolRequirement> = emptyList(),
    val environmentRequirements: EnvironmentRequirements = EnvironmentRequirements(),
    val communicationContract: CommunicationContract = CommunicationContract(),
    val resourceBudget: ResourceBudget = ResourceBudget(),
    val latencyBudget: LatencyBudget = LatencyBudget(),
    val safetyConstraints: Set<SafetyConstraint> = emptySet(),
    val testSuite: List<TestDescriptor> = emptyList(),
    val benchmarkSuite: List<BenchmarkDescriptor> = emptyList(),
    val fitnessMetrics: Map<String, Double> = emptyMap(),
    val mutationOperators: Set<MutationOperator> = emptySet(),
    val compatibilityRequirements: CompatibilityRequirements = CompatibilityRequirements(),
    val healthState: GenomeHealth = GenomeHealth.UNKNOWN,
    val provenance: Provenance = Provenance(author = "system")
) {

    /** Create a child genome from a mutation. */
    fun fork(
        mutationOperator: MutationOperator,
        mutationDescription: String,
        newId: String,
        newVersion: Int = version + 1,
        fitnessMetrics: Map<String, Double> = emptyMap()
    ): Genome = copy(
        id = newId,
        version = newVersion,
        parentLineage = parentLineage + id,
        fitnessMetrics = fitnessMetrics,
        mutationOperators = mutationOperators,
        healthState = GenomeHealth.TESTING,
        provenance = provenance.copy(
            createdMs = System.currentTimeMillis(),
            lastMutation = mutationDescription,
            mutationOperator = mutationOperator
        )
    )

    fun isAlive(): Boolean = healthState == GenomeHealth.HEALTHY || healthState == GenomeHealth.TESTING
    fun isAccepted(): Boolean = healthState == GenomeHealth.HEALTHY
    fun isRejected(): Boolean = healthState == GenomeHealth.REJECTED

    companion object {
        const val ROOT_LINEAGE = "ROOT"
    }
}

/** A port describes a named data channel flowing into or out of a micro-system. */
data class Port(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val description: String = ""
)

/** What the genome says about the runtime environment. */
data class RuntimeRequirements(
    val minSdk: Int = 21,
    val maxConcurrency: Int = 1,
    val requiresMainThread: Boolean = false,
    val requiresBackground: Boolean = false,
    val isolation: IsolationLevel = IsolationLevel.PROCESS_LOCAL
)

enum class IsolationLevel {
    /** Runs in the same process. */
    PROCESS_LOCAL,
    /** Runs in a separate Android process. */
    SEPARATE_PROCESS,
    /** Runs in Termux or an external environment. */
    EXTERNAL
}

/** What the genome says about models it needs. */
data class ModelRequirements(
    val models: List<ModelSlot> = emptyList()
)

data class ModelSlot(
    val id: String,
    val type: String,
    val quantization: String? = null,
    val maxMemoryMb: Long = 0,
    val required: Boolean = true
)

/** A tool the genome requires from the body's tool fabric. */
data class ToolRequirement(
    val toolId: String,
    val required: Boolean = true
)

/** What the genome says about the execution environment. */
data class EnvironmentRequirements(
    val networkAccess: Boolean = false,
    val fileAccess: Set<String> = emptySet(),
    val sensors: Set<String> = emptySet(),
    val permissions: Set<String> = emptySet(),
    val batteryMinPercent: Int = 0,
    val thermalOk: Boolean = true
)

/** The communication contract a micro-system exposes to other micro-systems. */
data class CommunicationContract(
    val inputTopics: Set<String> = emptySet(),
    val outputTopics: Set<String> = emptySet(),
    val requestTopics: Set<String> = emptySet(),
    val resultTopics: Set<String> = emptySet(),
    val confidenceThreshold: Float = 0.5f,
    val priority: Int = 0,
    val maxInflight: Int = 1
)

/** A hard budget the governor enforces. */
data class ResourceBudget(
    val maxMemoryMb: Long = 512,
    val maxCpuPercent: Double = 50.0,
    val maxEnergyPerHour: Double = 100.0,
    val priority: Int = 0,
    val isLatencySensitive: Boolean = false,
    val maxLatencyMs: Long = 1000
)

/** A latency budget — maximum acceptable time for a turn. */
data class LatencyBudget(
    val maxFirstTokenMs: Long = 5000,
    val maxFullResponseMs: Long = 30000,
    val maxTtsStartMs: Long = 2000
)

/** Safety constraints the genome enforces. */
enum class SafetyConstraint {
    NO_NETWORK,
    NO_CAMERA,
    NO_LOCATION,
    NO_CONTACTS,
    NO_SMS,
    NO_CALLS,
    MAX_FILE_SIZE_KB,
    USER_ACTION_REQUIRED,
    AUDIT_REQUIRED,
    NO_EXTERNAL_EXEC
}

/** A test descriptor (name + type, actual test code is separate). */
data class TestDescriptor(
    val name: String,
    val type: TestType,
    val description: String = ""
)

enum class TestType { UNIT, INTEGRATION, BEHAVIORAL, RESOURCE }

/** A benchmark descriptor. */
data class BenchmarkDescriptor(
    val name: String,
    val metric: String,
    val baseline: Double,
    val tolerance: Double = 0.2
)

/** The health of a genome. */
enum class GenomeHealth { UNKNOWN, TESTING, HEALTHY, DEGRADED, REJECTED, DEAD }

/** Provenance: who/what created this genome. */
data class Provenance(
    val author: String,
    val createdMs: Long = System.currentTimeMillis(),
    val lastMutation: String = "",
    val mutationOperator: MutationOperator? = null,
    val sourceContext: String = ""
)

/** How a genome was mutated. */
enum class MutationOperator {
    PARAMETER_MUTATION,
    STRATEGY_REPLACEMENT,
    COMPONENT_SUBSTITUTION,
    PIPELINE_MUTATION,
    ROUTING_MUTATION,
    ALGORITHM_MUTATION,
    TOOL_SUBSTITUTION,
    ENVIRONMENT_MUTATION,
    OPTIMIZATION_MUTATION,
    REPAIR_MUTATION,
    CROSSOVER
}

/** Compatibility matrix. */
data class CompatibilityRequirements(
    val requiresGenomeIds: Set<String> = emptySet(),
    val incompatibleGenomeIds: Set<String> = emptySet(),
    val requiredCapabilities: Set<String> = emptySet()
)
