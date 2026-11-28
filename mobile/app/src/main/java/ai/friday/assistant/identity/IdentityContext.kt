package ai.friday.assistant.identity

import ai.friday.assistant.language.DialectSignal
import ai.friday.assistant.memory.MemoryNode

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
    val personRelationshipModel: ai.friday.assistant.social.PersonRelationshipModel? = null,
    /**
     * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL: the pre-generation
     * confidentiality gate. When wired, world facts are filtered by the current
     * interlocutor BEFORE the generation suffix is assembled. Null keeps the
     * pre-social path byte-for-byte.
     */
    val confidentialityFirewall: ai.friday.assistant.social.ConfidentialityFirewall? = null,
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
     * takes ONLY a [ai.friday.assistant.continuity.ContinuityGate.Snapshot] — there
     * is no other parameter surface for an organ to write a contribution
     * directly into the generation payload. Every per-turn signal this suffix
     * consumes (dialect, mental state, person/relationship + trust-tier lines,
     * the confidentiality firewall, the model-tier signal) was produced by the
     * ContinuityGate at the composition point; nothing below invokes another
     * organ's seam directly.
     *
     * @param snapshot the per-turn bundle produced by
     *   [ai.friday.assistant.continuity.ContinuityGate.snapshotForTurn] — the only
     *   input this method accepts.
     */
    fun gatherForTurn(
        snapshot: ai.friday.assistant.continuity.ContinuityGate.Snapshot
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
        // assembled and before the response is delivered — a
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
            val continuity = selfModel.continuitySnapshot()
            val id = continuity.identity
            val caps = selfModel.capabilities().take(8).joinToString(", ") { it.name }
            val lims = selfModel.limitations().take(4).joinToString(", ") { it.capabilityId }
            val goals = continuity.activeGoals.take(3).joinToString("; ").ifBlank { "none" }
            "identity=${id.name}${id.pronouns?.let { " (${it})" }.orEmpty()} v${id.version}; " +
                "continuity: experiences=${continuity.experience.experienceCount}, " +
                "learned_associations=${continuity.experience.associationCount}, " +
                "latest_experience_at_ms=${continuity.experience.latestExperienceAtMs ?: "unknown"}, active_goals=$goals; " +
                "capabilities: ${caps.ifBlank { "(none)" }}; limitations: ${lims.ifBlank { "(none)" }}"
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
            sb.appendLine(formatMentalStateHypothesis(ctx.mentalState))
        }
        if (ctx.dialectSignal != null && ctx.dialectSignal != DialectSignal.neutral()) {
            sb.appendLine("- user dialect: ${ctx.dialectSignal.detectedLanguageMix} (register=${ctx.dialectSignal.register}, confidence=${ctx.dialectSignal.dialectConfidence})")
        }
        return sb.toString().trimEnd()
    }

    companion object {
        /**
         * Preserve the complete uncertainty-aware affect signal for the
         * reasoning model. The lexical reader is a hint, never a diagnosis:
         * the model must interpret the original message and wider context.
         */
        internal fun formatMentalStateHypothesis(mental: MentalStateHypothesis): String {
            val affect = mental.emotion
            return buildString {
                appendLine("- tentative user-state hypothesis: goal=${mental.goal}; mood=${mental.mood}; possible unstated need=${mental.unstatedNeed.ifBlank { "unknown" }}")
                appendLine(
                    "- text-only affect estimate (not a diagnosis): state=${affect.likelyState}; " +
                        "confidence=${"%.2f".format(java.util.Locale.ROOT, affect.confidence)}; " +
                        "valence=${"%.2f".format(java.util.Locale.ROOT, affect.valence)}; " +
                        "arousal=${"%.2f".format(java.util.Locale.ROOT, affect.arousal)}; " +
                        "agency=${"%.2f".format(java.util.Locale.ROOT, affect.dominance)}; " +
                        "tension=${"%.2f".format(java.util.Locale.ROOT, affect.tension)}; " +
                        "trend=${affect.temporalTrend}"
                )
                if (affect.alternatives.isNotEmpty()) {
                    appendLine("- alternative affect interpretations: ${affect.alternatives.joinToString()}")
                }
                if (affect.evidence.isNotEmpty()) {
                    appendLine("- observed text cues: ${affect.evidence.take(5).joinToString("; ")}")
                }
                append("- Treat this as a fallible hypothesis, not a fact about the person. Re-read their actual words and history, avoid overconfident emotion labels, and adapt tone/pacing with empathy without becoming patronising.")
            }
        }
    }
}
