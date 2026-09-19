package com.jarvis.app.genome

/**
 * Validates genomes for structural integrity and semantic correctness.
 * Returns a list of validation issues; an empty list means valid.
 */
object GenomeValidator {

    data class Issue(val field: String, val severity: Severity, val message: String) {
        enum class Severity { ERROR, WARNING }
    }

    fun validate(genome: Genome): List<Issue> = buildList {
        if (genome.id.isBlank()) add(Issue("id", Issue.Severity.ERROR, "Genome id must not be blank"))
        if (genome.version < 1) add(Issue("version", Issue.Severity.ERROR, "Version must be >= 1"))
        if (genome.parentLineage.isEmpty()) add(Issue("parentLineage", Issue.Severity.WARNING, "No parent lineage"))
        if (genome.capabilities.isEmpty()) add(Issue("capabilities", Issue.Severity.WARNING, "No capabilities declared"))
        if (genome.inputs.isEmpty() && genome.outputs.isEmpty()) {
            add(Issue("ports", Issue.Severity.WARNING, "No inputs or outputs"))
        }
        if (genome.testSuite.isEmpty()) add(Issue("testSuite", Issue.Severity.WARNING, "No tests declared"))
        if (genome.resourceBudget.maxMemoryMb <= 0) add(Issue("resourceBudget", Issue.Severity.ERROR, "Memory budget must be positive"))
        if (genome.resourceBudget.maxCpuPercent <= 0 || genome.resourceBudget.maxCpuPercent > 100) {
            add(Issue("resourceBudget", Issue.Severity.ERROR, "CPU budget must be 0-100%"))
        }
        if (genome.latencyBudget.maxFirstTokenMs <= 0) add(Issue("latencyBudget", Issue.Severity.WARNING, "Latency budget is non-positive"))
        if (genome.provenance.author.isBlank()) add(Issue("provenance", Issue.Severity.WARNING, "No author"))
        genome.dependencies.forEach { dep ->
            if (dep.isBlank()) add(Issue("dependencies", Issue.Severity.ERROR, "Empty dependency name"))
        }
        genome.inputs.zip(genome.outputs).forEach { (inp, outp) ->
            if (inp.name == outp.name) add(Issue("ports", Issue.Severity.WARNING, "Input and output share name: ${inp.name}"))
        }
    }

    fun isValid(genome: Genome): Boolean = validate(genome).none { it.severity == Issue.Severity.ERROR }

    fun warnings(genome: Genome): List<Issue> = validate(genome).filter { it.severity == Issue.Severity.WARNING }
}
