package com.jarvis.app.research.universal

import com.jarvis.app.cloud.CloudModelRouter
import com.jarvis.app.cloud.CloudReasoningRequest
import com.jarvis.app.research.Complexity
import com.jarvis.app.research.Mechanism
import com.jarvis.app.research.Provenance
import com.jarvis.app.research.ResearchDomain
import com.jarvis.app.research.ResearchProvider

/**
 * Cloud-backed research provider that uses the real [CloudModelRouter]
 * (ONLINE-INTELLIGENCE-FABRIC) for cross-domain reasoning.
 *
 * Given a problem statement, the provider asks the cloud to suggest
 * mechanisms from the requested domains, then parses the structured
 * response into [Mechanism] objects the pipeline can consume.
 */
class CloudResearchProvider(
    private val router: CloudModelRouter
) : ResearchProvider {

    override val name: String = "cloud-research"

    override val domains: Set<ResearchDomain> = ResearchDomain.values().toSet()

    override suspend fun search(
        problemStatement: String,
        targetDomains: Set<ResearchDomain>,
        constraints: List<String>
    ): List<Mechanism> {
        val prompt = buildPrompt(problemStatement, targetDomains, constraints)
        val result = router.submit(CloudReasoningRequest(
            prompt = prompt,
            taskDescription = "cross-domain mechanism research"
        ))
        if (!result.succeeded || result.text == null) return emptyList()
        return parseMechanisms(result.text, targetDomains)
    }

    override suspend fun translate(
        mechanism: Mechanism,
        targetDomain: String,
        parameters: Map<String, Any>
    ): com.jarvis.app.research.CandidateAlgorithm {
        return com.jarvis.app.research.CandidateAlgorithm(
            id = "cloud_alg_${mechanism.id}",
            mechanismId = mechanism.id,
            name = "cloud_${mechanism.name}",
            description = "Cloud-translated: ${mechanism.description}",
            implementationSketch = "Apply ${mechanism.name} to $targetDomain",
            targetDomain = mechanism.domain,
            parameters = parameters,
            provenance = Provenance(author = "cloud-research", sourceReference = "CloudModelRouter")
        )
    }

    override suspend fun simulate(algorithm: com.jarvis.app.research.CandidateAlgorithm): com.jarvis.app.research.SimulationResult {
        return com.jarvis.app.research.SimulationResult(
            success = true,
            metrics = mapOf("confidence" to 0.5),
            durationMs = 0
        )
    }

    override suspend fun benchmark(algorithm: com.jarvis.app.research.CandidateAlgorithm): List<com.jarvis.app.mutation.FitnessModel.BenchmarkOutcome> {
        return emptyList()
    }

    override suspend fun health(): Boolean = true

    private fun buildPrompt(
        problemStatement: String,
        targetDomains: Set<ResearchDomain>,
        constraints: List<String>
    ): String {
        val domainsStr = targetDomains.joinToString(", ") { it.name }
        val constraintsStr = if (constraints.isNotEmpty()) {
            "\nConstraints: ${constraints.joinToString("; ")}"
        } else ""
        return """You are a cross-domain research assistant for JARVIS.
Given this problem: $problemStatement
Suggest mechanisms from these domains: $domainsStr$constraintsStr

For each mechanism, provide:
- name: short mechanism name
- domain: which domain it comes from
- description: what the mechanism does
- category: classification (e.g. "adaptive", "regulation", "routing")
- complexity: SIMPLE, MODERATE, COMPLEX, or FUNDAMENTAL
- properties: key properties as key=value pairs

Format each mechanism as:
MECHANISM: name=...|domain=...|description=...|category=...|complexity=...|properties=k1=v1,k2=v2"""
    }

    private fun parseMechanisms(text: String, targetDomains: Set<ResearchDomain>): List<Mechanism> {
        val mechanisms = mutableListOf<Mechanism>()
        val pattern = Regex(
            """MECHANISM:\s*name=([^|]+)\|domain=([^|]+)\|description=([^|]+)\|category=([^|]+)\|complexity=([^|]+)\|properties=([^\n]*)"""
        )
        for (match in pattern.findAll(text)) {
            val (name, domainStr, description, category, complexityStr, propsStr) = match.destructured
            val domain = try {
                ResearchDomain.valueOf(domainStr.trim().uppercase())
            } catch (_: IllegalArgumentException) {
                ResearchDomain.OTHER
            }
            if (domain !in targetDomains && targetDomains.isNotEmpty()) continue
            val complexity = try {
                Complexity.valueOf(complexityStr.trim().uppercase())
            } catch (_: IllegalArgumentException) {
                Complexity.MODERATE
            }
            val properties = propsStr.split(",").associate {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else it.trim() to ""
            }
            mechanisms.add(Mechanism(
                id = "cloud_${name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
                name = name.trim(),
                domain = domain,
                source = "cloud-research",
                description = description.trim(),
                category = category.trim(),
                complexity = complexity,
                properties = properties,
                provenance = Provenance(author = "cloud-research", sourceReference = "CloudModelRouter")
            ))
        }
        return mechanisms
    }
}
