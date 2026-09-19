package com.jarvis.app.vf.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.CubicBezierEasing

/**
 * JARVIS Visual Foundation — Design Tokens.
 *
 * Machine-consumable form of `vault/design-system/system/20_DESIGN_TOKENS.md`,
 * itself derived from the frozen `JARVIS_VISUAL_BIBLE.md` §16.
 *
 * Every value here is canonical. No subsystem may redefine or drift from
 * these. The palette is the Design System's state language (§01_COLORS):
 * each color maps to exactly one meaning system-wide; color is never a
 * decorative palette.
 */
object JvTokens {

    // ---------------------------------------------------------------- color core
    val VoidBlack = Color(0xFF04060B)         // all primary backgrounds (Layer 0)
    val DeepSpace = Color(0xFF0A0E1A)         // panel/card fills (Layer 2)
    val IgnitionWhite = Color(0xFFF4FBFF)     // reactor ignition point ONLY
    val JarvisBlue = Color(0xFF2E9BFF)        // idle state, primary brand accent
    val Steel = Color(0xFF8A94A6)             // metal, chassis, terminal base text
    val Gunmetal = Color(0xFF1B2130)          // metal shadow/undertone

    // ---------------------------------------------------------------- color state
    val StateIdle = Color(0xFF2E9BFF)         // system online, nominal
    val StateListening = Color(0xFF00D9C0)    // acquiring input
    val StateThinking = Color(0xFFF5A623)     // processing / analysis / research / planning / learning
    val StateBuilding = Color(0xFFE8ECF2)     // producing output (near-white, min saturation)
    val StateWarning = Color(0xFFFF3B30)      // threat / attention required
    val StateCritical = Color(0xFFFF1744)     // emergency
    val StateSleep = Color(0xFF7C4DFF)        // deep rest, consciousness persisting
    val StateOffline = Color(0xFF8A94A6)      // off / inert (Steel at ~15% brightness)
    val StateSuccess = Color(0xFF34D399)      // confirmed task completion ONLY

    // ---------------------------------------------------------------- derived semantic
    /** State-colored hairline alpha — borders, dividers, window frames. */
    const val AccentHairlineAlpha = 0.20f

    // §5.6: #F4FBFF (ignition white) is reserved for the ignition point ONLY.
    // Text must use a distinct near-white — never the reserved ignition white.
    val TextPrimary = Color(0xFFECEDF1)       // tier-2 headline / brightest text
    val TextSecondary = Color(0xFF8A94A6)     // tier-3 body, captions, labels
    val TerminalText = Color(0xFF8A94A6)      // terminal base text

    // ---------------------------------------------------------------- typography (base 4px)
    val FontDisplayXl = 48.sp                 // wordmark, splash
    val FontDisplay = 32.sp                   // screen titles
    val FontHeading = 20.sp                   // panel/section titles
    val FontLabel = 12.sp                     // small-caps labels — uppercase, +0.12em tracking
    val FontBody = 14.sp                      // descriptions, primary reading
    val FontCaption = 11.sp                   // secondary/supporting
    val FontDataMono = 12.sp                  // telemetry, HUD, terminal
    val LabelTrackingEm = 0.12f               // small-caps tracking (+0.12em)

    // ---------------------------------------------------------------- spacing (4px multiples)
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space6 = 24.dp
    val Space8 = 32.dp
    val Space12 = 48.dp
    val Space16 = 64.dp
    val Hairline = 1f                       // the single non-4px value — a stroke, not a spacing unit

    // ---------------------------------------------------------------- motion durations
    const val DurMicro = 120L                 // button press, icon state (100–150ms)
    const val DurTransition = 300L            // panel open/close (250–350ms)
    const val DurStateChange = 500L           // reactor state crossfade (400–600ms)
    const val DurBreathingIdle = 3600L        // idle breathing (3.2–4.0s)
    const val DurBreathingSleep = 7000L       // sleep breathing (6–8s)
    const val DurEventPulseAttack = 150L      // event pulse attack (150–300ms)
    const val DurEventPulseDecay = 500L       // event pulse decay (400–600ms)
    const val DurStartupShutdown = 2200L      // startup/shutdown (1.5–3.0s)

    /** Voice-triggered state change: halved from DurStateChange (§14.4). */
    const val DurVoiceResponse = 250L

    // breathing amplitudes (§3.3)
    const val BreathingAmplitudeMin = 0.08f   // ±8% of baseline
    const val BreathingAmplitudeMax = 0.12f   // ±12% of baseline

    // event pulse envelope (§3.4)
    const val EventPulseAttackMs = 150L
    const val EventPulseDecayMs = 500L

    // ---------------------------------------------------------------- motion easing
    // §12.2 — four canonical curves. NO bounce/elastic anywhere.
    /** Orbital motion — rings, particles (sinusoidal in-out). */
    fun EaseOrbital() = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
    /** Panel entry (ease-out). */
    fun EasePanelIn() = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    /** Panel exit (ease-in). */
    fun EasePanelOut() = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)
    /** Warning/Critical pulses (sharp ease-out, no ease-in). */
    fun EaseWarningPulse() = CubicBezierEasing(0.9f, 0f, 1f, 1f)

    // ---------------------------------------------------------------- depth
    const val Layer0Void = 0
    const val Layer1Ambient = 1
    const val Layer2Structure = 2
    const val Layer3Content = 3
    const val Layer4Core = 4

    // ---------------------------------------------------------------- material opacity
    const val OpacityBloom = 0.05f            // ambient bloom/haze
    const val OpacityHologram = 0.15f         // hologram wireframes (base)
    const val OpacityGlass = 0.25f            // glass panels (75% transparent)
    const val OpacityCrystal = 0.50f          // crystal core shell
    const val OpacityMetal = 1.0f             // metal (opaque, always reflective)

    /** Glass self-reflection along its lower edge (§4.3). */
    const val GlassSelfReflectionMin = 0.04f
    const val GlassSelfReflectionMax = 0.08f

    // ---------------------------------------------------------------- sound
    const val AudioHumDb = -36.0               // baseline ambient reactor hum
    const val AudioUiHeadroomDb = 6.0          // max UI sound above hum

    // ---------------------------------------------------------------- reactor construction (§2.1/§2.2)
    const val RingCountPhone = 5               // full phone render 4–5 rings (§13.3)

    /**
     * Crossfade through a neutral Steel flash for family-disparate
     * transitions (§12.3) — never blend amber directly into red.
     */
    val NeutralFlash = Color(0xFF8A94A6)
}

/**
 * Color-family classification (§01 §2.3) — the only sanctioned basis for
 * direct crossfades and for emotional temperature shifts.
 */
enum class JvColorFamily(val members: Set<JvReactorStateColor>) {
    BLUE_NOMINAL(setOf(JvReactorStateColor.IDLE)),
    TEAL_INPUT(setOf(JvReactorStateColor.LISTENING)),
    AMBER_THINKING(setOf(JvReactorStateColor.THINKING, JvReactorStateColor.RESEARCH, JvReactorStateColor.PLANNING, JvReactorStateColor.LEARNING)),
    NEAR_WHITE_BUILDING(setOf(JvReactorStateColor.BUILDING, JvReactorStateColor.CODING)),
    RED_ALERT(setOf(JvReactorStateColor.WARNING, JvReactorStateColor.CRITICAL)),
    VIOLET_SLEEP(setOf(JvReactorStateColor.SLEEPING)),
    STEEL_OFFLINE(setOf(JvReactorStateColor.OFFLINE));

    companion object {
        fun of(state: JvReactorStateColor): JvColorFamily =
            entries.first { state in it.members }
    }
}

/**
 * The reactor's state color language (§5.2) — the meaning contract. These
 * are the ONLY colors a reactor/orb state may take.
 */
enum class JvReactorStateColor(val hex: Long) {
    IDLE(0xFF2E9BFF),
    LISTENING(0xFF00D9C0),
    THINKING(0xFFF5A623),
    RESEARCH(0xFFF5A623),
    PLANNING(0xFFF5A623),
    CODING(0xFFE8ECF2),
    BUILDING(0xFFE8ECF2),
    LEARNING(0xFFF5A623),
    WARNING(0xFFFF3B30),
    CRITICAL(0xFFFF1744),
    SLEEPING(0xFF7C4DFF),
    OFFLINE(0xFF8A94A6);

    /** The Compose color for this state. */
    val color: Color get() = Color(hex)

    fun family(): JvColorFamily = JvColorFamily.of(this)

    /** Linearly interpolate two state colors (used by crossfade paths). */
    fun lerpTo(other: JvReactorStateColor, t: Float): Color = lerp(color, other.color, t)
}
