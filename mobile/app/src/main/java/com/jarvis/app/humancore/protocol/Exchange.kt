package com.jarvis.app.humancore.protocol

/**
 * One full conversational exchange as seen by the Human Core Integration
 * Pass (§0.5): what the user said, what the Reasoning subsystem produced
 * (unstyled), and what finally reached the user after the Expression Pass.
 */
data class Exchange(
    /** The user's inbound text. */
    val userText: String,
    /** The Reasoning subsystem's raw reply, before styling (§13). */
    val reasoningReply: String,
    /** The final styled/vetoed outcome that reached (or would reach) the user. */
    val styled: StyledResponse,
    /** Epoch millis of the exchange. */
    val ts: Long
)

/**
 * Everything the Integration Pass computed about one exchange and needs to
 * distribute to the integration modules — computed once, shared everywhere,
 * so no module re-derives the same reading (§0.5, §8, §9a).
 */
data class IntegrationContext(
    val affect: AffectRead?,
    val deviation: DeviationResult?,
    val vulnerabilityShared: Boolean,
    val mentionsJarvis: Boolean
)
