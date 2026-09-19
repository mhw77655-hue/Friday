package com.jarvis.app.vf.typography

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jarvis.app.R
import com.jarvis.app.vf.tokens.JvTokens

/**
 * JARVIS Visual Foundation — Typography Engine.
 *
 * Implements `03_TYPOGRAPHY.md`: exactly three voices, each with a fixed job.
 *
 *  - **Display** — wide-tracked, geometric, thin-to-regular weight sans.
 *    Reserved for the product wordmark and top-level screen titles ONLY.
 *  - **UI / Body** — clean geometric-grotesque sans at regular/medium.
 *    Labels, descriptions, buttons. Legible on glass panels.
 *  - **Technical / Data** — monospace. The system's visual signal for
 *    "this is real machine data"; never used for conversational/marketing text.
 *
 * Type scale is strictly 4px-based (§7.2) and the three-tier panel hierarchy
 * (§7.3) is provided as a first-class builder: a panel's text never exceeds
 * label → title → description.
 */
object TypographyEngine {

    // ------------------------------------------------------------------ voices
    private val DisplayFamily = FontFamily(
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_regular, FontWeight.Normal)
    )

    private val UiFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium)
    )

    private val MonoFamily = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal)
    )

    /** Display / wordmark voice. Wide-tracked, geometric, technical. */
    val Display = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = JvTokens.FontDisplay,
        lineHeight = 40.sp,
        color = JvTokens.TextPrimary
    )

    val DisplayXl = Display.copy(
        fontSize = JvTokens.FontDisplayXl,
        lineHeight = 56.sp
    )

    /** UI / body voice. */
    val Body = TextStyle(
        fontFamily = UiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = JvTokens.FontBody,
        lineHeight = 20.sp,
        color = JvTokens.TextPrimary
    )

    val Heading = TextStyle(
        fontFamily = UiFamily,
        fontWeight = FontWeight.Medium,
        fontSize = JvTokens.FontHeading,
        lineHeight = 28.sp,
        color = JvTokens.TextPrimary
    )

    /** Small-caps section label — uppercase, tracked +0.12em (§7.2). */
    val Label = TextStyle(
        fontFamily = UiFamily,
        fontWeight = FontWeight.Medium,
        fontSize = JvTokens.FontLabel,
        lineHeight = 16.sp,
        letterSpacing = JvTokens.LabelTrackingEm.em,
        color = JvTokens.TextSecondary
    )

    val Caption = TextStyle(
        fontFamily = UiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = JvTokens.FontCaption,
        lineHeight = 16.sp,
        color = JvTokens.TextSecondary
    )

    /** Technical / data voice — monospace signal for real machine data. */
    val DataMono = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = JvTokens.FontDataMono,
        lineHeight = 16.sp,
        color = JvTokens.TextSecondary
    )

    /**
     * The three-tier panel hierarchy (§7.3), always in this order:
     *  1. small-caps label (what this is)   — uppercase, tracked wide
     *  2. short title/value (headline fact) — sentence case, brightest
     *  3. supporting description (1–2 lines) — dimmer, regular weight
     *
     * No panel may exceed these three tiers.
     */
    data class Tiered(
        val label: String,
        val title: String,
        val description: String,
        val labelStyle: TextStyle = Label,
        val titleStyle: TextStyle = Heading.copy(color = JvTokens.TextPrimary),
        val descriptionStyle: TextStyle = Body.copy(color = JvTokens.TextSecondary)
    )

    /** Convenience builder for a tiered panel text group. */
    fun tiered(
        label: String,
        title: String,
        description: String = ""
    ): Tiered = Tiered(label = label, title = title, description = description)

    // ------------------------------------------------------------------ terminal (§7.4)
    /**
     * Terminal style — monospace on near-black, max TWO colors:
     * base Steel text + ONE state accent for flagged lines. No rainbow.
     */
    val TerminalBase = DataMono.copy(color = JvTokens.TerminalText)
    fun TerminalAccent(stateColor: androidx.compose.ui.graphics.Color) =
        TerminalBase.copy(color = stateColor)

    // ------------------------------------------------------------------ HUD (§7.5)
    /** HUD readout style — monospace, tabular, right-aligned to its data point. */
    val Hud = DataMono.copy(color = JvTokens.TextSecondary)
    fun HudAccent(stateColor: androidx.compose.ui.graphics.Color) =
        Hud.copy(color = stateColor)
}
