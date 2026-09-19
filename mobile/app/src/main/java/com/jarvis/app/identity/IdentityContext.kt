package com.jarvis.app.identity

import com.jarvis.app.language.DialectSignal
import com.jarvis.app.memory.MemoryNode

/**
 * Phase A binding of the Stage 03 identity subsystems into ONE object the real
 * live turn can consult — no second/parallel implementation of any of the four
 * subsystems.
 *
 * It owns, and delegates directly to:
 *  - [WorldModelService]     — typed queries over the existing MemoryGraphStore
 *                              (user-node durable facts, entity/relationship
 *                              context, no new store).
 *  - [UserProfile]           — durable user preferences (explicit/repeated
 *                              signals promoted via the durability gate).
 *  - [UserMentalStateEstimator] — ephemeral per-turn mental-state hypothesis,
 *                              NEVER persisted.
 *  - [SelfModel]             — evidence-linked identity/capabilities/limitations
 *                              off the live CapabilityRegistry.
 *  - [PersonaTuner]          — durable persona trait adjustments from explicit
 *                              feedback, persisted onto the user node.
 *
 * [gatherForTurn] runs the per-turn side-effects (durable preference promotion,
 * persona adjustment) and assembles the identity context of THIS turn so the
 * CognitiveEngine can feed it into the live DIRECT_REPLY generation.
 */
class IdentityContext(
    val worldModel: WorldModelService,
    val userProfile: UserProfile,
    val mentalStateEstimator: UserMentalStateEstimator,
    val selfModel: SelfModel,
    val personaTuner: PersonaTuner,
    /**
     * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL: the per-person
     * Person/Relationship model over the same galaxy graph. When wired, each
     * mentioned PERSON contributes its profile + relationship lines to the
     * generation suffix. Null keeps the pre-social path byte-for-byte.
     */
    val personRelationshipModel: com.jarvis.app.social.PersonRelationshipModel? = null,
    /**
     * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL: the pre-generation
     * confidentiality gate. When wired, world facts are filtered by the current
     * interlocutor BEFORE the generation suffix is assembled. Null keeps the
     * pre-social path byte-for-byte.
     */
    val confidentialityFirewall: com.jarvis.app.social.ConfidentialityFirewall? = null,
    /** Session id stamped on durable user-profile observations. */
    private val sessionId: String = "live"
) {

    /** The identity-relevant signal gathered for one live turn. */
    data class Context(
        val durablePreferences: Map<String, String>,
        val worldFacts: List<MemoryNode>,
        val selfSummary: String?,
        val personaAdjustments: List<PersonaAdjustment>,
        val mentalState: MentalStateHypothesis?,
        val dialectSignal: DialectSignal?,
        /** Pre-formatted per-person profile/relationship lines for this turn. */
        val socialLines: List<String> = emptyList()
    ) {
        fun isEmpty(): Boolean =
            durablePreferences.isEmpty() && worldFacts.isEmpty() &&
                selfSummary == null && personaAdjustments.isEmpty() && mentalState == null &&
                dialectSignal == null && socialLines.isEmpty()
    }

    /**
     * Process one live turn: run the durable/user-model write-backs, then
     * assemble the identity context that should influence this turn's response.
     *
     * CONTINUITY-GATE-ENFORCED-SEAM (AC3/AC4): the generation-assembly method
     * takes ONLY a [com.jarvis.app.continuity.ContinuityGate.Snapshot] — there
     * is no other parameter surface for an organ to write a contribution
     * directly into the generation payload. Every per-turn signal this suffix
     * consumes (dialect, mental state, person/relationship + trust-tier lines,
     * the confidentiality firewall, the model-tier signal) was produced by the
     * ContinuityGate at the composition point; nothing below invokes another
     * organ's seam directly.
     *
     * @param snapshot the per-turn bundle produced by
     *   [com.jarvis.app.continuity.ContinuityGate.snapshotForTurn] — the only
     *   input this method accepts.
     */
    fun gatherForTurn(
        snapshot: com.jarvis.app.continuity.ContinuityGate.Snapshot
    ): Context {
        val userText = snapshot.userText
        // Durable user preference promotion (explicit or repeated-across-sessions).
        userProfile.ingestUtterance(userText, sessionId)
        // Durable persona trait adjustment from explicit feedback.
        personaTuner.ingest(userText)

        val durable = userProfile.allPreferences()
        val persona = personaTuner.adjustments()

        val gathered = LinkedHashSet<MemoryNode>().apply {
            // The user/model world node is always surfaced.
            addAll(worldModel.getFactsAbout(WorldModelService.USER_NODE_NAME))
            for (entity in snapshot.mentionedEntities.filter { it.isNotBlank() }) {
                addAll(worldModel.getFactsAbout(entity))
            }
        }.toList()

        // PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL AC3: the
        // firewall gate runs HERE, BEFORE the generation suffix below is
        // assembled and long before HumanCore.express sees the reply — a
        // confidential-to-one-person fact with no authorized-disclosure entry
        // for this interlocutor is blocked from ever reaching generation. The
        // firewall instance arrives ONLY via the gate's snapshot — never from
        // another parameter surface.
        val firewall = snapshot.confidentialityFirewall
        val worldFacts = if (firewall != null) {
            firewall.filterForInterlocutor(gathered, snapshot.interlocutor)
        } else {
            gathered
        }

        // Per-person profile/relationship lines travel in the snapshot from the
        // gate (contributed by the PersonRelationshipModel at construction).
        val socialLines = snapshot.socialLines

        val selfSummary = if (snapshot.selfReferential) {
            val id = selfModel.identity()
            val caps = selfModel.capabilities().take(8).joinToString(", ") { it.name }
            val lims = selfModel.limitations().take(4).joinToString(", ") { it.capabilityId }
            "identity=${id.name} v${id.version}; capabilities: ${caps.ifBlank { "(none)" }}; " +
                "limitations: ${lims.ifBlank { "(none)" }}"
        } else {
            null
        }

        // The ephemeral per-turn hypothesis rides the gate's snapshot — it is
        // computed once per turn by the gate and NEVER persisted.
        val mental = snapshot.mentalState

        return Context(
            durablePreferences = durable,
            worldFacts = worldFacts,
            selfSummary = selfSummary,
            personaAdjustments = persona,
            mentalState = mental,
            dialectSignal = snapshot.dialectSignal,
            socialLines = socialLines
        )
    }

    /** Format the gathered context as a prompt suffix for the live DIRECT_REPLY. */
    fun formatForPrompt(ctx: Context): String {
        val sb = StringBuilder("\n\n[Identity context]:\n")
        if (ctx.durablePreferences.isNotEmpty()) {
            for ((k, v) in ctx.durablePreferences) {
                sb.appendLine("- user preference $k = $v")
            }
        }
        for (node in ctx.worldFacts) {
            if (node.predicate.startsWith("profile:") || node.predicate.startsWith("relationship:")) {
                continue
            }
            sb.appendLine("- world: ${node.subject} ${node.predicate} ${node.`object`}")
        }
        for (line in ctx.socialLines) {
            sb.appendLine(line)
        }
        if (ctx.selfSummary != null) {
            sb.appendLine("- self: ${ctx.selfSummary}")
        }
        for (a in ctx.personaAdjustments) {
            sb.appendLine("- persona trait ${a.trait} = ${a.value}")
        }
        if (ctx.mentalState != null) {
            sb.appendLine("- user mental state: goal=${ctx.mentalState.goal}, mood=${ctx.mentalState.mood}, unstated=${ctx.mentalState.unstatedNeed}")
        }
        if (ctx.dialectSignal != null && ctx.dialectSignal != DialectSignal.neutral()) {
            sb.appendLine("- user dialect: ${ctx.dialectSignal.detectedLanguageMix} (register=${ctx.dialectSignal.register}, confidence=${ctx.dialectSignal.dialectConfidence})")
        }
        return sb.toString().trimEnd()
    }
}
