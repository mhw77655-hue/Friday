package com.jarvis.app.social

import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.termux.TermuxJarvisServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL AC6 — proof through
 * the EXACT production call path (TermuxJarvisServer -> LatencyPipeline ->
 * CognitiveEngine.process -> IdentityContext.gatherForTurn/Firewall ->
 * formatForPrompt -> the outgoing generation payload).
 *
 * (a) a confidential-to-one-person fact is not surfaced when the simulated
 *     interlocutor is unauthorized, and
 * (b) the trust-tier differentiated response is real — the SAME input produces
 *     different real output for a stranger-tier vs a known/trusted-tier
 *     relationship state.
 *
 * The only swapped hop is bridgeSend (recording collector instead of
 * modelManager.send) — precisely the seam CognitiveEngine.process uses,
 * exactly as in DialectIntegrationTest / IdentityInChatPipelineGroundTruthTest.
 */
class PersonRelationshipProductionPathTest {

    private fun recordingPipeline(
        server: TermuxJarvisServer,
        captured: MutableList<String>
    ): LatencyPipeline = LatencyPipeline(
        dispatch = { it() },
        scheduleDelayed = { _, _ -> },
        bridgeSend = { captured.add(it) },
        bridgeStatus = { server.modelManager.status.value },
        beginExchange = { _, _ -> null },
        express = { reply, _ -> StyledResponse.Approved(reply) },
        completeExchange = { _, _, _, _, _ -> },
        sessionContext = { null },
        cognitiveEngine = server.engine
    )

    @Test
    fun `confidential fact is blocked for the unauthorized interlocutor in the live path`(): Unit =
        runBlocking {
            val server = TermuxJarvisServer(port = 0, backendOverride = FakeModelBackend())
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            try {
                server.personRelationshipModel.registerPerson("Sara")
                server.confidentialityFirewall.recordSecret(
                    person = "Sara",
                    description = "burial",
                    value = "next to the old mango tree",
                    owner = com.jarvis.app.identity.WorldModelService.USER_NODE_NAME,
                    authorized = setOf("Sister")
                )

                // The secret genuinely lives in the galaxy graph store (it is
                // only blocked from a generation turn it must not reach).
                assertTrue(server.worldModel.getFactsAbout("Sara").any { it.predicate == "secret:burial" })

                pipeline.onUserInput("tell me about \"Sara\"")
                val ownerPayload = captured.last()
                assertTrue(
                    "the owner (Venon) still sees the confidential fact in the outgoing payload",
                    ownerPayload.contains("next to the old mango tree")
                )

                pipeline.onUserInput("tell me about \"Sara\"", ackOverride = null, interlocutor = "Sister")
                val sisterPayload = captured.last()
                assertTrue(
                    "an authorized party sees the confidential fact",
                    sisterPayload.contains("next to the old mango tree")
                )

                pipeline.onUserInput("tell me about \"Sara\"", ackOverride = null, interlocutor = "Aunt")
                val auntPayload = captured.last()
                System.err.println("### AUNT SCOPE ###")
                System.err.println(auntPayload)
                assertFalse(
                    "an unauthorized interlocutor never sees the confidential fact in the outgoing payload",
                    auntPayload.contains("next to the old mango tree")
                )
                assertFalse(auntPayload.contains("secret:burial"))
                assertTrue(
                    "the turn still flows through the identity generation seam",
                    auntPayload.contains("[Identity context]")
                )
            } finally {
                server.stop()
            }
        }

    @Test
    fun `trust tier differentiation is real for the same input`(): Unit =
        runBlocking {
            val server = TermuxJarvisServer(port = 0, backendOverride = FakeModelBackend())
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            try {
                server.personRelationshipModel.registerPerson("Alice")
                server.personRelationshipModel.setTrust("Alice", TrustTier.TRUSTED)
                server.personRelationshipModel.setTrajectory("Alice", RelationshipStage.ESTABLISHED)

                pipeline.onUserInput("who is \"Alice\"")
                val trustedPayload = captured.last()
                assertTrue(
                    "a trusted relationship renders its trust tier in the outgoing payload",
                    trustedPayload.contains("relationship: Alice trust=TRUSTED")
                )
                assertTrue(trustedPayload.contains("trajectory=ESTABLISHED"))

                server.personRelationshipModel.setTrust("Alice", TrustTier.STRANGER)

                pipeline.onUserInput("who is \"Alice\"")
                val strangerPayload = captured.last()
                assertTrue(
                    "the same input now renders the stranger tier",
                    strangerPayload.contains("relationship: Alice trust=STRANGER")
                )
                assertNotEquals(
                    "same input, different real output for a different trust tier",
                    trustedPayload, strangerPayload
                )
            } finally {
                server.stop()
            }
        }
}