package com.jarvis.app.continuity

import com.jarvis.app.identity.MentalStateHypothesis
import com.jarvis.app.identity.UserMentalStateEstimator
import com.jarvis.app.identity.WorldModelService
import com.jarvis.app.language.DialectSignal
import com.jarvis.app.language.EgyptianArabicDialectDetector
import com.jarvis.app.memory.BlendedMemoryRetriever
import com.jarvis.app.memory.RankedMemory
import com.jarvis.app.model.ModelTier
import com.jarvis.app.social.ConfidentialityFirewall
import com.jarvis.app.social.PersonRelationshipModel

/**
 * CONTINUITY-GATE-ENFORCED-SEAM — the single typed registry every organ must
 * contribute a per-turn signal into at construction time.
 *
 * Every organ that feeds the generation payload (dialect, identity/emotion
 * mental state, person/relationship + trust tier, confidentiality firewall,
 * galaxy memory, model tier) registers a real [Contribution] into this gate at
 * the composition point (JarvisEngine.init / TermuxJarvisServer — AC2). Per
 * turn the gate produces ONE [Snapshot]; the generation seam
 * ([com.jarvis.app.identity.IdentityContext.gatherForTurn] and
 * [com.jarvis.app.cognitive.ContextWindowAssembler]) can ONLY assemble the
 * outgoing generation payload from that snapshot — there is no other parameter
 * surface a new organ can attach to, so a second parallel path cannot compile.
 *
 * This is a structural hardening of the existing real seam, not a new
 * cognitive capability: every contribution below is the REAL signal that was
 * already flowing through IdentityContext.gatherForTurn / the assembler, not a
 * reinvented parallel implementation.
 */
class ContinuityGate(
    /** EGYPTIAN-ARABIC-TEXT-HALF: per-turn dialect/code-switch signal. */
    private val dialectDetector: EgyptianArabicDialectDetector? = null,
    /** PERSON-RELATIONSHIP-MODEL: per-person profile + trust-tier surface lines. */
    private val personRelationshipModel: PersonRelationshipModel? = null,
    /** Confidentiality firewall: the SAME closed-world gate applied to BOTH the
     *  galaxy-retrieval seam (inside [snapshotForTurn]) and the identity-suffix
     *  seam (via [Snapshot.confidentialityFirewall]). */
    private val confidentialityFirewall: ConfidentialityFirewall? = null,
    /** GALAXY MEMORY: cross-session retrieval seam feeding the generation payload. */
    private val blendedRetriever: BlendedMemoryRetriever? = null,
    /** IDENTITY/EMOTION: per-turn mental-state hypothesis (ephemeral, never persisted). */
    private val mentalStateEstimator: UserMentalStateEstimator? = null,
    /** Maximum number of cross-session memories admitted into one snapshot. */
    val maxCrossSessionMemories: Int = 5
) {

    enum class SignalKind {
        DIALECT,
        MENTAL_STATE,
        SOCIAL_RELATIONSHIP,
        CONFIDENTIALITY,
        GALAXY_MEMORY,
        MODEL_TIER
    }

    /** One typed contribution an organ registers at construction time. */
    data class Contribution(
        val organId: String,
        val kind: SignalKind,
        val description: String
    )

    /**
     * The per-turn bundle ONLY [ContinuityGate.snapshotForTurn] can produce —
     * the single input the generation seam is allowed to consume. Every
     * per-turn signal travelling into the generation payload is carried here;
     * no organ writes directly into the payload outside this type.
     */
    data class Snapshot(
        val turnIndex: Long,
        val userText: String,
        val interlocutor: String,
        val mentionedEntities: List<String>,
        val selfReferential: Boolean,
        /** DIALECT contribution — detected per turn from [userText]. */
        val dialectSignal: DialectSignal?,
        /** MENTAL_STATE contribution — ephemeral hypothesis, never persisted. */
        val mentalState: MentalStateHypothesis?,
        /** SOCIAL_RELATIONSHIP contribution — per-person profile + trust-tier lines. */
        val socialLines: List<String>,
        /** GALAXY_MEMORY + CONFIDENTIALITY contribution — firewalled cross-session
         *  memories the assembler formats into `[Cross-session memory]` lines. */
        val crossSessionMemories: List<RankedMemory>,
        /** CONFIDENTIALITY contribution — the registered closed-world gate the
         *  identity suffix applies to its gathered world facts. The snapshot is
         *  the only way this firewall reaches a generation-assembly method. */
        val confidentialityFirewall: ConfidentialityFirewall?,
        /** MODEL_TIER contribution — the per-turn served tier (resident vs
         *  on-demand reasoning) from the admission gate. Null before the
         *  cognitive runtime gateway has admitted the turn. */
        val servedTier: ModelTier?
    )

    private val registry = mutableListOf<Contribution>()

    init {
        if (dialectDetector != null) {
            register(Contribution(
                organId = "language.egyptianArabicTextHalf",
                kind = SignalKind.DIALECT,
                description = "EGYPTIAN-ARABIC-TEXT-HALF: the real EgyptianArabicDialectDetector seams " +
                    "its per-turn DialectSignal into the gate; the identity suffix renders it, never " +
                    "the organ directly."
            ))
        }
        if (mentalStateEstimator != null) {
            register(Contribution(
                organId = "identity.mentalStateEstimator",
                kind = SignalKind.MENTAL_STATE,
                description = "EMOTIONAL-INTELLIGENCE-FUSION-LAYER-TIER-1: the real UserMentalStateEstimator " +
                    "(whose hypothesis rides the Tier-1 emotion fold) contributes the ephemeral per-turn " +
                    "mental-state hypothesis to the gate."
            ))
        }
        if (personRelationshipModel != null) {
            register(Contribution(
                organId = "social.personRelationshipModel",
                kind = SignalKind.SOCIAL_RELATIONSHIP,
                description = "PERSON-RELATIONSHIP-MODEL: the real PersonRelationshipModel contributes the " +
                    "per-person profile + relationship/trust-tier surface lines to the gate."
            ))
        }
        if (confidentialityFirewall != null) {
            register(Contribution(
                organId = "social.confidentialityFirewall",
                kind = SignalKind.CONFIDENTIALITY,
                description = "Confidentiality firewall: the SAME closed-world gate the identity suffix and " +
                    "the galaxy seam both consult is contributed to the gate; no generation-assembly method " +
                    "receives a firewall from anywhere else."
            ))
        }
        if (blendedRetriever != null) {
            register(Contribution(
                organId = "memory.blendedRetriever",
                kind = SignalKind.GALAXY_MEMORY,
                description = "GALAXY MEMORY: the real BlendedMemoryRetriever contributes firewalled " +
                    "cross-session memories to the gate's snapshot."
            ))
        }
        register(Contribution(
            organId = "model.reasoningTier",
            kind = SignalKind.MODEL_TIER,
            description = "MODEL-TIER: the cognitive runtime gateway's per-turn served tier (resident vs " +
                "ON_DEMAND_REASONING) is carried in the gate's snapshot as the model-tier contribution."
        ))
    }

    /** Register an organ's typed [Contribution]; returns the gate for chaining. */
    fun register(contribution: Contribution): ContinuityGate {
        registry.add(contribution)
        return this
    }

    /** The immutable registry of every contribution wired at construction. */
    fun contributions(): List<Contribution> = registry.toList()

    /**
     * Build the single per-turn [Snapshot] every generation-seam contribution
     * flows through. Each registered organ's real provider is invoked HERE, in
     * the gate — afterwards neither IdentityContext.gatherForTurn nor the
     * ContextWindowAssembler can be handed a per-turn signal from outside the
     * snapshot (their generation-assembly signatures accept only the snapshot).
     */
    fun snapshotForTurn(
        turnIndex: Long,
        userText: String,
        interlocutor: String = WorldModelService.USER_NODE_NAME,
        mentionedEntities: List<String> = emptyList(),
        selfReferential: Boolean = false,
        servedTier: ModelTier? = null
    ): Snapshot {
        val dialect: DialectSignal? = dialectDetector?.detect(userText)

        val mental: MentalStateHypothesis? = mentalStateEstimator?.estimateForTurn(userText)

        val social: List<String> = personRelationshipModel?.let { prm ->
            buildList {
                for (entity in mentionedEntities.filter { it.isNotBlank() }) {
                    addAll(prm.personSurface(entity))
                }
            }
        } ?: emptyList()

        // GALAXY MEMORY + CONFIDENTIALITY: the SAME closed-world gate the
        // identity suffix consults is applied to the RAW RankedMemory list HERE,
        // BEFORE any node becomes a formatted `[Cross-session memory]` line.
        val crossSession: List<RankedMemory> =
            if (blendedRetriever != null && userText.isNotBlank()) {
                val retrieved = blendedRetriever.retrieve(userText, now = System.currentTimeMillis())
                val gated = confidentialityFirewall
                    ?.filterRankedMemoriesForInterlocutor(retrieved, interlocutor)
                    ?: retrieved
                gated.take(maxCrossSessionMemories)
            } else {
                emptyList()
            }

        return Snapshot(
            turnIndex = turnIndex,
            userText = userText,
            interlocutor = interlocutor,
            mentionedEntities = mentionedEntities,
            selfReferential = selfReferential,
            dialectSignal = dialect,
            mentalState = mental,
            socialLines = social,
            crossSessionMemories = crossSession,
            confidentialityFirewall = confidentialityFirewall,
            servedTier = servedTier
        )
    }
}