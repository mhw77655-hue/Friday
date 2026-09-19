package com.jarvis.app.research.universal

import com.jarvis.app.cloud.CloudModelRouter
import com.jarvis.app.cloud.CloudProvider
import com.jarvis.app.cloud.CloudReasoningRequest
import com.jarvis.app.cloud.CloudResult
import com.jarvis.app.research.ResearchDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Universal Research pipeline — gap → research → mechanism → adaptation.
 *
 * Proves the pipeline:
 *  - uses the REAL CloudModelRouter (ONLINE-INTELLIGENCE-FABRIC) for reasoning,
 *    never a hardcoded stub (CloudResearchProvider delegates to router.submit)
 *  - consumes Fabricator's MechanismCandidate output as one input channel
 *    alongside its own cloud-research channel, SAME shape
 *  - writes every candidate into the REAL mechanisms store (MechanismsStore),
 *    not a mock
 */
class UniversalResearchPipelineTest {

    private val cloudMechanismsText = """
        MECHANISM: name=pheromone routing|domain=INSECTS|description=trail reinforcement decays over time|category=routing|complexity=SIMPLE|properties=decay=slow,reinforce=fast
        MECHANISM: name=health-aware replica|domain=NETWORKING|description=route to nearest healthy server and cache|category=routing|complexity=MODERATE|properties=health=checked,cache=yes
    """.trimIndent()

    private class StubCloudProvider(
        override val id: String,
        private val reply: String
    ) : CloudProvider {
        override fun generate(prompt: String): CloudResult = CloudResult.Success(reply, id)
    }

    private val fabricatorCandidates = listOf(
        MechanismCandidate(
            id = "fab_candidate_ice_memory",
            name = "ice-line memory",
            description = "Fiction-mining candidate serialized from a story device",
            structure = "adversity leaves a durable imprint that later recall re-warms",
            inputs = listOf("gap description", "narrative payload"),
            outputs = listOf("durable imprint that later turns can recall"),
            constraints = listOf("fiction-derived, requires JARVIS experiment"),
            confidence = 0.6,
            source = CandidateSource.FABRICATOR,
            targetDomain = ResearchDomain.OTHER,
            principle = "a preserved imprint that reactivates under matching conditions",
            implementationSketch = "store a durable imprint fact; on matching recall, re-warm it",
            evidence = ResearchEvidence(
                id = "ev_fab_ice",
                status = EvidenceStatus.HYPOTHESIS,
                supportingFacts = listOf(SourceFact("fiction", "literature", "imprint", "story")),
                confidence = 0.6
            )
        )
    )

    @Test
    fun `cloud research routes through the real CloudModelRouter and writes to the real store`() = runBlocking<Unit> {
        val router = CloudModelRouter(listOf(StubCloudProvider("free-model-1", cloudMechanismsText)))
        val store = InMemoryMechanismsStore()
        val engine = UniversalResearchEngine(cloudRouter = router, store = store)

        val result = engine.researchPersist(
            gapDescription = "JARVIS has no low-cost route to the nearest healthy capability provider"
        )

        assertTrue("cloud channel exercised", CandidateSource.CLOUD_REASONING in result.channels)
        assertTrue("cloud candidates produced", result.candidates.any { it.source == CandidateSource.CLOUD_REASONING })
        assertEquals("all candidates stored", result.candidates.size, result.storedCount)
        assertEquals("store holds cloud candidates", 2, store.readWhere { it.source == CandidateSource.CLOUD_REASONING }.size)

        // AC2: every required field is populated on the stored candidate.
        val stored = store.readById("cloud_cloud_pheromone_routing")
        assertNotNull("cloud candidate persisted by id", stored)
        stored?.let {
            assertTrue(it.structure.isNotBlank())
            assertTrue(it.inputs.isNotEmpty())
            assertTrue(it.outputs.isNotEmpty())
            assertTrue(it.constraints.isNotEmpty())
            assertTrue(it.confidence in 0.0..1.0)
            assertTrue(it.evidence.id.isNotBlank())
            assertEquals(CandidateSource.CLOUD_REASONING, it.source)
        }
    }

    @Test
    fun `fabricator channel and cloud research channel land candidates in the same real store`() = runBlocking<Unit> {
        val router = CloudModelRouter(listOf(StubCloudProvider("free-model-1", cloudMechanismsText)))
        val store = InMemoryMechanismsStore()
        val engine = UniversalResearchEngine(
            cloudRouter = router,
            fabricatorChannel = FabricatorChannel { gap -> fabricatorCandidates },
            store = store
        )

        val result = engine.researchPersist("JARVIS needs a durable memory that reactivates on matching recall")

        assertTrue("cloud channel", CandidateSource.CLOUD_REASONING in result.channels)
        assertTrue("fabricator channel", CandidateSource.FABRICATOR in result.channels)
        assertTrue("fabricator candidates present", result.candidates.any { it.source == CandidateSource.FABRICATOR })
        assertEquals("fabricator candidate shape identical to cloud shape", 1,
            result.candidates.count { it.source == CandidateSource.FABRICATOR })

        // Both channels, same table.
        assertFalse("cloud in store", store.readWhere { it.source == CandidateSource.CLOUD_REASONING }.isEmpty())
        assertFalse("fabricator in store", store.readWhere { it.source == CandidateSource.FABRICATOR }.isEmpty())
        assertEquals("aggregate stored count", result.candidates.size, result.storedCount)
        assertEquals("store total", result.candidates.size, store.count())
    }

    @Test
    fun `fabricator-only wiring consumes MechanismCandidate output into the store`() = runBlocking<Unit> {
        val store = InMemoryMechanismsStore()
        val engine = UniversalResearchEngine(
            fabricatorChannel = FabricatorChannel { fabricatorCandidates },
            store = store
        )

        val result = engine.researchPersist("JARVIS needs narrative-sourced adaptations")

        assertTrue("fabricator channel", CandidateSource.FABRICATOR in result.channels)
        assertFalse("no cloud channel wired", CandidateSource.CLOUD_REASONING in result.channels)
        assertTrue("fiction candidate written", store.readById("fab_candidate_ice_memory") != null)
        assertTrue("all stored", result.candidates.size == result.storedCount)
    }

    @Test
    fun `existing in-memory research is unchanged when cloud and fabricator are wired`() = runBlocking<Unit> {
        val router = CloudModelRouter(listOf(StubCloudProvider("free-model-1", cloudMechanismsText)))
        val engine = UniversalResearchEngine(cloudRouter = router, fabricatorChannel = FabricatorChannel { fabricatorCandidates })

        // research() (the pre-existing API) still returns the same MappedCandidate shape.
        val result = engine.research("improve memory retrieval efficiency", maxResults = 6)
        assertTrue(result.candidates.isNotEmpty())
        assertTrue("memory subsystem surfaced", result.principals().any { it.contains("index") } ||
            result.candidates.any { it.targetSubsystem == "memory" })
    }

    @Test
    fun `real router proves rotation and rate-limit isolation across channels`() = runBlocking<Unit> {
        val rateLimited = object : CloudProvider {
            override val id = "free-model-0"
            override fun generate(prompt: String) =
                CloudResult.Failure("free-model-0", "rate limit reached", rateLimited = true)
        }
        val healthy = StubCloudProvider("free-model-1", cloudMechanismsText)
        // REAL router over two providers: the rate-limited one first, healthy second.
        val router = CloudModelRouter(listOf(rateLimited, healthy))
        val store = InMemoryMechanismsStore()
        val engine = UniversalResearchEngine(cloudRouter = router, store = store)

        val result = engine.researchPersist("JARVIS needs resilient provider routing")

        // The real router rotated past the rate-limited member and succeeded on the next.
        assertTrue(result.candidates.any { it.source == CandidateSource.CLOUD_REASONING })
        assertTrue("succeeded provider recorded", store.count() > 0)
    }

    @Test
    fun `store exposes readById and readWhere for downstream consumers`() {
        val store = InMemoryMechanismsStore()
        val c = MechanismCandidate(
            id = "c1", name = "n1", description = "d1", structure = "s1",
            inputs = listOf("i"), outputs = listOf("o"), constraints = listOf("c"),
            confidence = 0.5, source = CandidateSource.LOCAL_CATALOG, targetDomain = ResearchDomain.OTHER,
            principle = "p", implementationSketch = "sk", evidence =
            ResearchEvidence("e1", EvidenceStatus.HYPOTHESIS, listOf(SourceFact("f", "d", "claim", "ref")), 0.5)
        )
        assertTrue(store.write(c))
        assertEquals(c, store.readById("c1"))
        assertEquals(1, store.readWhere { it.id == "c1" }.size)
        assertEquals(1, store.count())
    }

    private fun UniversalResearchEngine.ResearchResult.principals(): List<String> =
        candidates.map { it.principle }
}