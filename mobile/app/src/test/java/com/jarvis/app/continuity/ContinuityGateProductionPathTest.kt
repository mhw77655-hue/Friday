package com.jarvis.app.continuity

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
 * CONTINUITY-GATE-ENFORCED-SEAM AC5 — proof through the EXACT production call
 * path (TermuxJarvisServer -> LatencyPipeline -> CognitiveEngine.process ->
 * ContinuityGate.snapshotForTurn -> IdentityContext.gatherForTurn(snapshot) /
 * ContextWindowAssembler.assembleFrom(snapshot) -> the outgoing generation
 * payload) that every migrated contribution still reaches the real generation
 * payload after routing through the ContinuityGate instead of the prior
 * direct wiring:
 *
 *  (a) the Egyptian Arabic dialect signal travels in the payload,
 *  (b) the person/relationship + trust-tier lines reach the payload,
 *  (c) the confidentiality firewall still blocks an unauthorized interlocutor
 *      while the owner/authorized parties see the fact, and
 *  (d) trust-tier differentiation stays real — SAME input, different real
 *      output for a different relationship tier.
 *
 * The only swapped hop is bridgeSend (recording collector instead of
 * modelManager.send) — precisely the seam CognitiveEngine.process uses,
 * exactly as in DialectIntegrationTest / PersonRelationshipProductionPathTest.
 */
class ContinuityGateProductionPathTest {

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
    fun `the production gate registry carries every migrated organ contribution`(): Unit =
        runBlocking {
            val server = TermuxJarvisServer(port = 0, backendOverride = FakeModelBackend())
            try {
                val kinds = server.continuityGate.contributions().map { it.kind }.toSet()
                assertTrue(
                    "dialect organ must be registered into the gate at construction",
                    kinds.contains(ContinuityGate.SignalKind.DIALECT)
                )
                assertTrue(
                    "mental-state organ must be registered into the gate",
                    kinds.contains(ContinuityGate.SignalKind.MENTAL_STATE)
                )
                assertTrue(
                    "person-relationship organ must be registered into the gate",
                    kinds.contains(ContinuityGate.SignalKind.SOCIAL_RELATIONSHIP)
                )
                assertTrue(
                    "confidentiality organ must be registered into the gate",
                    kinds.contains(ContinuityGate.SignalKind.CONFIDENTIALITY)
                )
                assertTrue(
                    "galaxy-memory organ must be registered into the gate",
                    kinds.contains(ContinuityGate.SignalKind.GALAXY_MEMORY)
                )
                assertTrue(
                    "model-tier organ must be registered into the gate",
                    kinds.contains(ContinuityGate.SignalKind.MODEL_TIER)
                )
            } finally {
                server.stop()
            }
        }

    @Test
    fun `Egyptian Arabic dialect signal reaches the outgoing payload through the gate`(): Unit =
        runBlocking {
            val server = TermuxJarvisServer(port = 0, backendOverride = FakeModelBackend())
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            try {
                pipeline.onUserInput("ايه رايك كده")
                val payload = captured.last()
                assertTrue(
                    "identity section present on DIRECT_REPLY turn",
                    payload.contains("[Identity context]")
                )
                assertTrue(
                    "Egyptian dialect signal travels in the outgoing payload via the gate",
                    payload.contains("user dialect: ar-EG")
                )
            } finally {
                server.stop()
            }
        }

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
    fun `trust tier differentiation is real for the same input through the gate`(): Unit =
        runBlocking {
            val server = TermuxJarvisServer(port = 0, backendOverride = FakeModelBackend())
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            try {
                server.personRelationshipModel.registerPerson("Alice")
                server.personRelationshipModel.setTrust(
                    "Alice",
                    com.jarvis.app.social.TrustTier.TRUSTED
                )
                server.personRelationshipModel.setTrajectory(
                    "Alice",
                    com.jarvis.app.social.RelationshipStage.ESTABLISHED
                )

                pipeline.onUserInput("who is \"Alice\"")
                val trustedPayload = captured.last()
                assertTrue(
                    "a trusted relationship renders its trust tier in the outgoing payload",
                    trustedPayload.contains("relationship: Alice trust=TRUSTED")
                )
                assertTrue(trustedPayload.contains("trajectory=ESTABLISHED"))

                server.personRelationshipModel.setTrust(
                    "Alice",
                    com.jarvis.app.social.TrustTier.STRANGER
                )

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