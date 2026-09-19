package com.jarvis.app.genome

/**
 * Fluent builder for constructing a [Genome]. All fields have sensible
 * defaults; only the fields you set are reflected in the built genome.
 */
class GenomeBuilder(private val id: String) {

    private var version: Int = 1
    private var parentLineage: List<String> = listOf(Genome.ROOT_LINEAGE)
    private var capabilities: MutableSet<String> = mutableSetOf()
    private var inputs: MutableList<Port> = mutableListOf()
    private var outputs: MutableList<Port> = mutableListOf()
    private var dependencies: MutableSet<String> = mutableSetOf()
    private var runtimeRequirements = RuntimeRequirements()
    private var modelRequirements = ModelRequirements()
    private var toolRequirements: MutableList<ToolRequirement> = mutableListOf()
    private var environmentRequirements = EnvironmentRequirements()
    private var communicationContract = CommunicationContract()
    private var resourceBudget = ResourceBudget()
    private var latencyBudget = LatencyBudget()
    private var safetyConstraints: MutableSet<SafetyConstraint> = mutableSetOf()
    private var testSuite: MutableList<TestDescriptor> = mutableListOf()
    private var benchmarkSuite: MutableList<BenchmarkDescriptor> = mutableListOf()
    private var fitnessMetrics: MutableMap<String, Double> = mutableMapOf()
    private var mutationOperators: MutableSet<MutationOperator> = mutableSetOf()
    private var compatibilityRequirements = CompatibilityRequirements()
    private var author: String = "system"
    private var createdMs: Long = System.currentTimeMillis()
    private var lastMutation: String = ""
    private var mutationOperator: MutationOperator? = null
    private var sourceContext: String = ""

    fun version(v: Int) = apply { version = v }
    fun parentLineage(l: List<String>) = apply { parentLineage = l }
    fun capability(c: String) = apply { capabilities += c }
    fun capabilities(c: Set<String>) = apply { capabilities += c }
    fun input(name: String, type: String, nullable: Boolean = false, desc: String = "") = apply { inputs += Port(name, type, nullable, desc) }
    fun output(name: String, type: String, nullable: Boolean = false, desc: String = "") = apply { outputs += Port(name, type, nullable, desc) }
    fun dependency(d: String) = apply { dependencies += d }
    fun runtime(minSdk: Int = 21, isolation: IsolationLevel = IsolationLevel.PROCESS_LOCAL) = apply { runtimeRequirements = RuntimeRequirements(minSdk = minSdk, isolation = isolation) }
    fun model(id: String, type: String, required: Boolean = true, maxMemoryMb: Long = 0) = apply { modelRequirements = ModelRequirements(modelRequirements.models + ModelSlot(id, type, maxMemoryMb = maxMemoryMb, required = required)) }
    fun tool(toolId: String, required: Boolean = true) = apply { toolRequirements += ToolRequirement(toolId, required) }
    fun environment(network: Boolean = false, permissions: Set<String> = emptySet()) = apply { environmentRequirements = EnvironmentRequirements(networkAccess = network, permissions = permissions) }
    fun commContract(inputTopics: Set<String> = emptySet(), outputTopics: Set<String> = emptySet()) = apply { communicationContract = CommunicationContract(inputTopics = inputTopics, outputTopics = outputTopics) }
    fun resourceBudget(maxMemoryMb: Long = 512, maxCpuPercent: Double = 50.0, priority: Int = 0) = apply { resourceBudget = ResourceBudget(maxMemoryMb = maxMemoryMb, maxCpuPercent = maxCpuPercent, priority = priority) }
    fun latencyBudget(maxFirstTokenMs: Long = 5000, maxFullResponseMs: Long = 30000) = apply { latencyBudget = LatencyBudget(maxFirstTokenMs = maxFirstTokenMs, maxFullResponseMs = maxFullResponseMs) }
    fun safety(constraint: SafetyConstraint) = apply { safetyConstraints += constraint }
    fun test(name: String, type: TestType = TestType.UNIT, desc: String = "") = apply { testSuite += TestDescriptor(name, type, desc) }
    fun benchmark(name: String, metric: String, baseline: Double) = apply { benchmarkSuite += BenchmarkDescriptor(name, metric, baseline) }
    fun fitness(key: String, value: Double) = apply { fitnessMetrics[key] = value }
    fun mutationOperator(op: MutationOperator) = apply { mutationOperators += op }
    fun author(a: String) = apply { author = a }
    fun sourceContext(ctx: String) = apply { sourceContext = ctx }
    fun lineage(l: List<String>) = apply { parentLineage = l }

    fun build(): Genome = Genome(
        id = id,
        version = version,
        parentLineage = parentLineage,
        capabilities = capabilities.toSet(),
        inputs = inputs.toList(),
        outputs = outputs.toList(),
        dependencies = dependencies.toSet(),
        runtimeRequirements = runtimeRequirements,
        modelRequirements = modelRequirements,
        toolRequirements = toolRequirements.toList(),
        environmentRequirements = environmentRequirements,
        communicationContract = communicationContract,
        resourceBudget = resourceBudget,
        latencyBudget = latencyBudget,
        safetyConstraints = safetyConstraints.toSet(),
        testSuite = testSuite.toList(),
        benchmarkSuite = benchmarkSuite.toList(),
        fitnessMetrics = fitnessMetrics.toMap(),
        mutationOperators = mutationOperators.toSet(),
        compatibilityRequirements = compatibilityRequirements,
        healthState = GenomeHealth.UNKNOWN,
        provenance = Provenance(author = author, createdMs = createdMs, lastMutation = lastMutation, mutationOperator = mutationOperator, sourceContext = sourceContext)
    )
}
