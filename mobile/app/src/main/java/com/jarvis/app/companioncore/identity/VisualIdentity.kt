package com.jarvis.app.companioncore.identity

import androidx.compose.ui.graphics.Color
import com.jarvis.app.vf.tokens.JvTokens

/**
 * §2.5 — Visual Identity.
 *
 * The static, canonical definition of what JARVIS looks like: the single
 * source of truth for color (and, later, form/iconography) that every visual
 * subsystem draws from. Loaded once at app start, never mutated at runtime —
 * a new identity version requires an explicit build (§2.5 lifecycle).
 *
 * Colors are semantic roles, not fixed per-screen values, so the Visual Theme
 * System (§2.27, Phase 5) can restyle them without touching consumers.
 * Values trace to the frozen Visual Bible §5 / `JvTokens` and the orb's
 * historical brand violet (`0xFF7C5CFF`).
 *
 * Pure value module — no runtime state, no dependencies on other CC modules.
 */
object VisualIdentity {

    const val IDENTITY_VERSION: Int = 1

    // ------------------------------------------------------------- brand root
    /** The orb's historical brand accent — used only during the Wake bloom. */
    val BrandViolet: Color = Color(0xFF7C5CFF)

    /** The brand core (idle accent) — Jarvis Blue. */
    val BrandCore: Color = JvTokens.JarvisBlue

    // ------------------------------------------------------------- color roles
    /** Semantic color roles (§2.5). Consumers take a role, never a hex. */
    enum class SemanticRole {
        IDLE, LISTENING, THINKING, SPEAKING, WAKE, WARNING, CRITICAL, SLEEP, OFFLINE
    }

    private val semanticColors: Map<SemanticRole, Color> = mapOf(
        SemanticRole.IDLE to JvTokens.StateIdle,          // §9.1 #2E9BFF
        SemanticRole.LISTENING to JvTokens.StateListening, // §9.2 #00D9C0
        SemanticRole.THINKING to JvTokens.StateThinking,   // §9.3 #F5A623
        SemanticRole.SPEAKING to JvTokens.StateBuilding,   // §9.7 #E8ECF2 (output being produced)
        SemanticRole.WAKE to BrandViolet,                  // boot / wake bloom
        SemanticRole.WARNING to JvTokens.StateWarning,     // §9.10 #FF3B30
        SemanticRole.CRITICAL to JvTokens.StateCritical,   // §9.11 #FF1744
        SemanticRole.SLEEP to JvTokens.StateSleep,         // §9.12 #7C4DFF
        SemanticRole.OFFLINE to JvTokens.StateOffline      // §9.13 Steel @ ~15%
    )

    /** Resolve a semantic color role to its concrete [Color]. */
    fun color(role: SemanticRole): Color = semanticColors.getValue(role)

    // ------------------------------------------------------------- motion profile
    /**
     * Brand motion language (§2.5): "JARVIS never moves sharply/jerkily —
     * all motion eases." A static easing profile consumed by the Animation
     * Controller for transitions.
     */
    data class MotionEasingProfile(val curveName: String, val durationMsRange: LongRange)

    /** Canonical brand motion profile (dur = the §12.1 state-change window). */
    val BrandMotion: MotionEasingProfile =
        MotionEasingProfile(curveName = "cubic-bezier(0.45,0,0.55,1)", durationMsRange = 400L..600L)
}
