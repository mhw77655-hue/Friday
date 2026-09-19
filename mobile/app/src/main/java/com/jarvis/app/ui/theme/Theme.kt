package com.jarvis.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jarvis.app.R

/**
 * Palette refreshed to match the approved living-UI prototype. Token
 * NAMES are unchanged on purpose — DiagnosticsViewModel.kt, SystemScreen.kt,
 * and everything else that references JarvisColors.CoreIdle etc. keeps
 * compiling untouched; only the hex values move to the richer palette.
 * One genuinely new addition: CoreAttention (amber) — the prototype's
 * "uncertain / needs input" meaning didn't have a token before (the old
 * palette only had Warning-red and Unknown-gray, nothing in between).
 * Nothing references it yet; it's here for whatever wires an
 * uncertain/needs-attention state to the UI next.
 */
object JarvisColors {
    val SurfaceBase = Color(0xFF08090C)
    val SurfacePanel = Color(0xFF12151B)
    val SurfaceHairline = Color(0x12FFFFFF)
    val CoreIdle = Color(0xFF7C5CFF)       // was blue 0x3B82F6 -> now violet, matches prototype's "presence/continuity"
    val CoreThinking = Color(0xFF4C8DFF)   // was purple 0x7C3AED -> now blue, kept deliberately distinct from CoreIdle's violet
    val CoreSuccess = Color(0xFF34D399)    // refined mint-green
    val CoreWarning = Color(0xFFFB4B4B)    // refined red
    val CoreOffline = Color(0xFF5B6270)    // refined dormant gray
    val CoreUnknown = Color(0xFF7B8394)    // lighter neutral, distinct from CoreOffline
    val CoreAttention = Color(0xFFF5A623)  // NEW — amber, "uncertain / needs input", nothing wired to it yet
    val TextPrimary = Color(0xFFECEDF1)
    val TextSecondary = Color(0xFF8B93A3)
}

/**
 * Design spec §4: "Display/Identity: Inter... weight 600", "Body: Inter,
 * weight 400", "Label: ...uppercase" (also Inter, weight per spec is
 * unspecified for Label beyond caps — kept at Medium/500 as a readable
 * mid-weight for small uppercase text), "Technical/mono: JetBrains Mono".
 * Files expected at res/font/: inter_regular.ttf, inter_medium.ttf,
 * inter_semibold.ttf, jetbrains_mono_regular.ttf — see the Termux
 * download script that fetches these before this file will compile
 * (R.font.* references fail to resolve if the files aren't present).
 */
private val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

private val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal)
)

object JarvisType {
    val Display = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        color = JarvisColors.TextPrimary
    )
    val Body = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = JarvisColors.TextPrimary
    )
    val Technical = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = JarvisColors.TextSecondary
    )
    val Label = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.08.em,
        color = JarvisColors.TextSecondary
    )
}

private val JarvisDarkScheme = darkColorScheme(
    background = JarvisColors.SurfaceBase,
    surface = JarvisColors.SurfaceBase,
    surfaceVariant = JarvisColors.SurfacePanel,
    primary = JarvisColors.CoreIdle,
    onBackground = JarvisColors.TextPrimary,
    onSurface = JarvisColors.TextPrimary,
    error = JarvisColors.CoreWarning
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisDarkScheme,
        typography = Typography(),
        content = content
    )
}
