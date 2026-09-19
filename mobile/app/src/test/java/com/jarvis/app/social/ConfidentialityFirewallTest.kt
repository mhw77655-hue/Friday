package com.jarvis.app.social

import com.jarvis.app.identity.WorldModelService
import com.jarvis.app.termux.TermuxJarvisServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL AC3 — the closed-world
 * confidentiality gate: a secret tagged confidential-to-one-person with no
 * authorized-disclosure entry for the current interlocutor is blocked, while
 * the owner and every authorized party still see it. The gate operates on the
 * facts the generation seam would surface — the block sits BEFORE the reply is
 * generated, never as a post-hoc filter.
 */
class ConfidentialityFirewallTest {

    private fun freshFirewall(): Triple<TermuxJarvisServer.InMemoryGraph, WorldModelService, ConfidentialityFirewall> {
        val graphStore = TermuxJarvisServer.InMemoryGraph()
        val worldModel = WorldModelService(graphStore)
        return Triple(graphStore, worldModel, ConfidentialityFirewall(graphStore, worldModel))
    }

    @Test
    fun `secret content reaches only the owner and authorized parties`() {
        val (_, worldModel, firewall) = freshFirewall()
        firewall.recordSecret(
            person = "Sara",
            description = "burial",
            value = "next to the old mango tree",
            owner = WorldModelService.USER_NODE_NAME,
            authorized = setOf("Sister")
        )
        val facts = worldModel.getFactsAbout("Sara")

        assertEquals("Venon (the owner) can read the secret",
            "next to the old mango tree",
            firewall.contentFor("Sara", "burial", WorldModelService.USER_NODE_NAME))
        assertEquals("the authorized party can read the secret",
            "next to the old mango tree",
            firewall.contentFor("Sara", "burial", "Sister"))
        assertNull("an unauthorized interlocutor is blocked",
            firewall.contentFor("Sara", "burial", "Aunt"))
        assertNull("a stranger is blocked",
            firewall.contentFor("Sara", "burial", "Colleague"))

        assertTrue(firewall.filterForInterlocutor(facts, WorldModelService.USER_NODE_NAME)
            .any { it.predicate == "secret:burial" })
        assertTrue(firewall.filterForInterlocutor(facts, "Sister")
            .any { it.predicate == "secret:burial" })
        assertFalse("unauthorized turn never sees the secret content",
            firewall.filterForInterlocutor(facts, "Aunt").any { it.predicate == "secret:burial" })
    }

    @Test
    fun `secret metadata never surfaces to anyone`() {
        val (_, worldModel, firewall) = freshFirewall()
        firewall.recordSecret(
            person = "Sara",
            description = "burial",
            value = "next to the old mango tree",
            owner = WorldModelService.USER_NODE_NAME,
            authorized = setOf("Sister")
        )
        val facts = worldModel.getFactsAbout("Sara")

        for (interlocutor in listOf(WorldModelService.USER_NODE_NAME, "Sister", "Aunt")) {
            val released = firewall.filterForInterlocutor(facts, interlocutor)
            assertFalse("owner/authorized metadata predicates never leak for $interlocutor",
                released.any { it.predicate.contains(":owner") || it.predicate.contains(":authorized:") })
        }
    }

    @Test
    fun `non-secret facts pass through for every interlocutor`() {
        val (graphStore, worldModel, firewall) = freshFirewall()
        worldModel.registerEntity("Sara", com.jarvis.app.identity.EntityType.PERSON)
        graphStore.addFact(subject = "Sara", predicate = "identity", `object` = "sister", source = "test")
        firewall.recordSecret(
            person = "Sara",
            description = "burial",
            value = "cairo",
            owner = WorldModelService.USER_NODE_NAME,
            authorized = setOf("Sister")
        )
        val facts = worldModel.getFactsAbout("Sara")

        val forAunt = firewall.filterForInterlocutor(facts, "Aunt")
        assertTrue("plain facts about the person still reach any interlocutor",
            forAunt.any { it.predicate == "identity" && it.`object` == "sister" })
        assertTrue(forAunt.any { it.predicate == "type" })
    }

    @Test
    fun `owner-only secret is released only to the owner`() {
        val (_, worldModel, firewall) = freshFirewall()
        firewall.recordSecret(
            person = "Omar",
            description = "address",
            value = "cairo",
            owner = WorldModelService.USER_NODE_NAME,
            authorized = emptySet()
        )
        val facts = worldModel.getFactsAbout("Omar")
        assertTrue(firewall.filterForInterlocutor(facts, WorldModelService.USER_NODE_NAME)
            .any { it.predicate == "secret:address" })
        assertNull(firewall.contentFor("Omar", "address", "Aunt"))
    }

    @Test
    fun `orphan secret with no metadata is blocked for everyone`() {
        val (graphStore, worldModel, firewall) = freshFirewall()
        graphStore.addFact(subject = "Huda", predicate = "secret:clinic", `object` = "castle", source = "test")
        val facts = worldModel.getFactsAbout("Huda")
        assertFalse("no owner means no disclosure entry — blocked even for the subject itself",
            firewall.filterForInterlocutor(facts, "Huda").any { it.predicate == "secret:clinic" })
        assertNull(firewall.contentFor("Huda", "secret:clinic".removePrefix("secret:"), "Huda"))
    }
}