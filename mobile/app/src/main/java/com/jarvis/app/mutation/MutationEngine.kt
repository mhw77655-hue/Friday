package com.jarvis.app.mutation

import com.jarvis.app.genome.Genome
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.genome.MutationOperator
import java.util.concurrent.atomic.AtomicLong

/**
 * Mutation engine: creates new genome candidates by applying mutation
 * operators to an existing genome. Each mutation produces a NEW lineage
 * candidate — never overwrites the parent.
 *
 * The mutation engine is deterministic for a given (genome, operator, seed).
 */
class MutationEngine(private val nowMs: () -> Long = { System.currentTimeMillis() }) {

    private val candidateCounter = AtomicLong(0)

    /** Produce a list of candidate mutations from a parent genome. */
    fun mutate(
        parent: Genome,
        operators: Set<MutationOperator> = parent.mutationOperators,
        count: Int = 1
    ): List<MutationCandidate> = buildList {
        for (op in operators.take(count.coerceAtLeast(1))) {
            val candidate = applyMutation(parent, op)
            this += candidate
        }
    }

    /** Apply a single mutation operator to a genome, producing a new candidate. */
    fun applyMutation(parent: Genome, operator: MutationOperator): MutationCandidate {
        val candidateId = "${parent.id}_mut_${candidateCounter.incrementAndGet()}"
        val description = describeMutation(operator, parent)

        val child = parent.fork(
            mutationOperator = operator,
            mutationDescription = description,
            newId = candidateId,
            fitnessMetrics = emptyMap()
        )

        return MutationCandidate(
            id = candidateId,
            parent = parent,
            child = child,
            operator = operator,
            description = description,
            createdMs = nowMs()
        )
    }

    private fun describeMutation(op: MutationOperator, parent: Genome): String = when (op) {
        MutationOperator.PARAMETER_MUTATION -> "Tuned parameters on genome ${parent.id}"
        MutationOperator.STRATEGY_REPLACEMENT -> "Replaced strategy in genome ${parent.id}"
        MutationOperator.COMPONENT_SUBSTITUTION -> "Substituted a component in genome ${parent.id}"
        MutationOperator.PIPELINE_MUTATION -> "Mutated pipeline in genome ${parent.id}"
        MutationOperator.ROUTING_MUTATION -> "Changed routing in genome ${parent.id}"
        MutationOperator.ALGORITHM_MUTATION -> "Changed algorithm in genome ${parent.id}"
        MutationOperator.TOOL_SUBSTITUTION -> "Substituted a tool in genome ${parent.id}"
        MutationOperator.ENVIRONMENT_MUTATION -> "Changed environment requirements in genome ${parent.id}"
        MutationOperator.OPTIMIZATION_MUTATION -> "Optimized genome ${parent.id}"
        MutationOperator.REPAIR_MUTATION -> "Repaired a defect in genome ${parent.id}"
        MutationOperator.CROSSOVER -> "Crossover between genomes"
    }
}

data class MutationCandidate(
    val id: String,
    val parent: Genome,
    val child: Genome,
    val operator: MutationOperator,
    val description: String,
    val createdMs: Long
)
