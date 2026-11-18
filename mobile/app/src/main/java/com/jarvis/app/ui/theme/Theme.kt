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
 * Friday's visual language: deep ink, atmospheric violet and living cyan.
 *
 * The legacy JarvisColors/JarvisType names remain as source-compatible aliases
 * for the existing screens while the visual system is migrated. UI tokens do
 * not define Friday's cognitive architecture.
 */
object JarvisColors {
    val SurfaceBase = Color(0xFF080B14)
    val SurfacePanel = Color(0xFF111827)
    val SurfaceRaised = Color(0xFF192337)
    val SurfaceInset = Color(0xFF0D1421)
    val SurfaceHairline = Color(0x22A9B8D2)
    val CoreIdle = Color(0xFFB49AFF)
    val CoreThinking = Color(0xFF6CDDF2)
    val CoreSuccess = Color(0xFF65DDB5)
    val CoreWarning = Color(0xFFFF6F86)
    val CoreOffline = Color(0xFF727D92)
    val CoreUnknown = Color(0xFFA5B0C6)
    val CoreAttention = Color(0xFFFFC27C)
    val TextPrimary = Color(0xFFF3F5FC)
    val TextSecondary = Color(0xFFA3AEC4)
    val TextMuted = Color(0xFF6F7B92)
    val UserBubble = Color(0xFF272442)
}

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
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.035).em,
        color = JarvisColors.TextPrimary
    )
    val Headline = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.02).em,
        color = JarvisColors.TextPrimary
    )
    val Body = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        color = JarvisColors.TextPrimary
    )
    val Technical = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.015.em,
        color = JarvisColors.TextSecondary
    )
    val Label = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.12.em,
        color = JarvisColors.TextSecondary
    )
}

private val FridayDarkScheme = darkColorScheme(
    primary = JarvisColors.CoreIdle,
    onPrimary = Color(0xFF17112B),
    primaryContainer = Color(0xFF30284B),
    onPrimaryContainer = JarvisColors.TextPrimary,
    secondary = JarvisColors.CoreThinking,
    onSecondary = Color(0xFF071C23),
    tertiary = JarvisColors.CoreSuccess,
    background = JarvisColors.SurfaceBase,
    onBackground = JarvisColors.TextPrimary,
    surface = JarvisColors.SurfacePanel,
    onSurface = JarvisColors.TextPrimary,
    surfaceVariant = JarvisColors.SurfaceRaised,
    onSurfaceVariant = JarvisColors.TextSecondary,
    outline = JarvisColors.SurfaceHairline,
    error = JarvisColors.CoreWarning
)

private val FridayTypography = Typography(
    displayLarge = JarvisType.Display,
    headlineLarge = JarvisType.Headline,
    titleLarge = JarvisType.Headline.copy(fontSize = 19.sp, lineHeight = 25.sp),
    bodyLarge = JarvisType.Body,
    bodyMedium = JarvisType.Body.copy(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = JarvisType.Label.copy(fontSize = 12.sp, letterSpacing = 0.04.em)
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FridayDarkScheme,
        typography = FridayTypography,
        content = content
    )
}
