package com.jarvis.app.model

/**
 * Model federation tiers (frozen Brain-Inspired architecture): a model is an
 * organ, not an identity, and every organ wakes in one of these tiers.
 *
 * [RESIDENT] is the always-allowed, never-auto-unloaded minimal cognition
 * tier. The on-demand tiers ([VOICE], [VISION], [ON_DEMAND_REASONING]) require
 * resource headroom and are subject to the degraded-cognition ladder.
 *
 * The degradation ladder is intentionally single-hop: any on-demand tier that
 * cannot get headroom falls back to [RESIDENT] — the one tier that is always
 * allowed. This is what STAGE-04-INTEGRATION asserts ("gets DEGRADE_TO'd to
 * the resident tier").
 */
enum class ModelTier {
    RESIDENT,
    VOICE,
    VISION,
    ON_DEMAND_REASONING;

    /** The concrete next-lower tier; null only for [RESIDENT] (never degrades). */
    val lowerTier: ModelTier?
        get() = when (this) {
            RESIDENT -> null
            VOICE, VISION, ON_DEMAND_REASONING -> RESIDENT
        }
}