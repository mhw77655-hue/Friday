package com.jarvis.app.model

/**
 * The organ roles that can wake a model (frozen Brain-Inspired vocabulary).
 * A role pins the [ModelTier] its organ wakes in: reasoning is the on-demand
 * conversational brain, voice/vision are their multimodal organs, and the
 * resident tier is always-available minimal cognition.
 */
enum class OrganRole(val tier: ModelTier) {
    RESIDENT(ModelTier.RESIDENT),
    REASONING(ModelTier.ON_DEMAND_REASONING),
    VOICE(ModelTier.VOICE),
    VISION(ModelTier.VISION)
}

/**
 * The outcome of an organ wake: the loaded handle plus which tier actually
 * served it. [requestedTier] is what the caller asked for; [servedTier] lands
 * below it — and [degraded] is true — when the [ResourceGovernor] denied or
 * degraded the wake. Serve the turn from [servedTier], never throw/hang.
 */
data class WakeResult(
    val handle: ModelHandle,
    val requestedTier: ModelTier,
    val servedTier: ModelTier,
    val degraded: Boolean = servedTier != requestedTier
)