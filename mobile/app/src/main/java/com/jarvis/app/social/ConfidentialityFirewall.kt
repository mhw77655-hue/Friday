package com.jarvis.app.social

import com.jarvis.app.identity.WorldModelService
import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.MemoryNode
import com.jarvis.app.memory.RankedMemory

/**
 * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL: the pre-generation
 * confidentiality gate.
 *
 * A secret is owned — it lives on the person's galaxy node as
 * `(person, "secret:<description>", value)` with an owner fact
 * `(person, "secret:<description>:owner", owner)` and one authorized-disclosure
 * fact `(person, "secret:<description>:authorized:<who>", who)` PER allowed
 * party (predicate-keyed, so multiple authorized parties coexist under the
 * store's supersession semantics).
 *
 * [filterForInterlocutor] is the CLOSED-WORLD gate: a secret's content is only
 * released to the owner or to a party with an authorized-disclosure entry; the
 * `:owner` / `:authorized:` metadata NEVER surfaces. It is a pure function over
 * the given fact list plus the interlocutor, consulted by IdentityContext
 * BEFORE the generation prompt is assembled — the block sits upstream of
 * generation, a secret that must not reach the interlocutor never reaches the
 * outgoing payload for that turn (AC3/AC6a), rather than being filtered after
 * the fact.
 *
 * Venon-mediated for now: the owner defaults to Venon (the user), and
 * authorized-disclosure parties are whoever Venon designates.
 */
class ConfidentialityFirewall(
    private val graphStore: MemoryGraphStore,
    private val worldModel: WorldModelService
) {

    private val secretPrefix = "secret:"

    /**
     * Persist one confidential fact about [person]. The content fact, the
     * owner fact, and one authorized-disclosure fact per name in
     * [authorized] all land on the SAME galaxy graph store as ordinary
     * supersession-aware triples.
     */
    fun recordSecret(
        person: String,
        description: String,
        value: String,
        owner: String = WorldModelService.USER_NODE_NAME,
        authorized: Set<String> = emptySet(),
        source: String = "social-confidentiality"
    ) {
        graphStore.addFact(subject = person, predicate = secretPrefix + description, `object` = value, source = source)
        graphStore.addFact(subject = person, predicate = "$secretPrefix$description:owner", `object` = owner, source = source)
        for (who in authorized) {
            graphStore.addFact(
                subject = person,
                predicate = "$secretPrefix$description:authorized:$who",
                `object` = who,
                source = source
            )
        }
    }

    /**
     * The content of the secret [description] about [person], or null when the
     * interlocutor has no disclosure entry (owner or authorized party).
     */
    fun contentFor(
        person: String,
        description: String,
        interlocutor: String
    ): String? {
        val meta = worldModel.getFactsAbout(person)
        val owner = meta.firstOrNull { it.predicate == "$secretPrefix$description:owner" }?.`object`
        val allowed = owner != null && (
            owner == interlocutor ||
                meta.any { it.predicate == "$secretPrefix$description:authorized:$interlocutor" }
            )
        if (!allowed) return null
        return worldModel.getFactsAbout(person)
            .firstOrNull { it.predicate == secretPrefix + description }?.`object`
    }

    /**
     * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL AC3 — the plain
     * fact-list gate consulted by the identity suffix. Given the gathered world
     * facts for this turn, release only the ones the [interlocutor] may see:
     * every non-secret fact passes, secret CONTENT facts pass only for the
     * owner/authorized parties, and secret metadata (`:owner`, `:authorized:`)
     * NEVER passes to anyone (the ledger is gate-only, not prompt content).
     */
    fun filterForInterlocutor(
        memories: List<MemoryNode>,
        interlocutor: String
    ): List<MemoryNode> =
        memories.filter { node ->
            val predicate = node.predicate
            if (!predicate.startsWith(secretPrefix)) return@filter true
            if (predicate.contains(":owner") || predicate.contains(":authorized:")) return@filter false
            contentFor(node.subject, predicate.removePrefix(secretPrefix), interlocutor) != null
        }

    /**
     * PERSON-RELATIONSHIP-AND-CONFIDENTIALITY-FIREWALL AC3 — galaxy-retrieval
     * seam gate. The OTHER real pre-generation path is the cross-session
     * galaxy: the [ContextWindowAssembler]'s [sortedMemories] (Raw
     * [RankedMemory] carrying the full [MemoryNode])
     * are gated HERE, per [interlocutor], BEFORE they are ever formatted into
     * `[Cross-session memory]` prompt lines. The same closed-world rule: a
     * confidential content fact with no owner/authorized-disclosure entry for
     * [interlocutor] never becomes a formatted cross-session memory line for
     * that turn — it cannot leak through the galaxy seam (AC3's "or ever
     * reaching generation", applied at EVERY real pre-generation seam).
     */
    fun filterRankedMemoriesForInterlocutor(
        memories: List<RankedMemory>,
        interlocutor: String
    ): List<RankedMemory> =
        memories.filter { mem ->
            val node = mem.node
            val predicate = node.predicate
            if (!predicate.startsWith(secretPrefix)) return@filter true
            // Secret metadata facts (`:owner`, `:authorized:`) are gate-only
            // ledger entries — NEVER prompt content, even for the owner.
            if (predicate.contains(":owner") || predicate.contains(":authorized:")) return@filter false
            contentFor(node.subject, predicate.removePrefix(secretPrefix), interlocutor) != null
        }
}