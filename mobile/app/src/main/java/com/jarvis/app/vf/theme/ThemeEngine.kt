package com.jarvis.app.vf.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jarvis.app.R
import com.jarvis.app.vf.tokens.JvTokens
import com.jarvis.app.vf.typography.TypographyEngine

/**
 * JARVIS Visual Foundation — Theme Engine.
 *
 * Implements `ThemeEngine.kt` + `01_COLORS.md` + `03_TYPOGRAPHY.md`:
 * provides `LocalJvTokens` (design tokens) and `LocalJvTypography`
 * (three-voice type engine) through the Compose composition tree,
 * and wraps MaterialTheme with the frozen Design System dark palette.
 *
 * The Design System palette (blue idle, amber thinking, etc.) is
 * independent of the existing screens' legacy JarvisColors and is
 * frozen in place by the Visual Bible.
 */

/** Composition-local provider for `JvTokens` — available to every Jv component. */
val LocalJvTokens = staticCompositionLocalOf { JvTokens }

/** Composition-local provider for the type engine. */
val LocalJvTypography = staticCompositionLocalOf { TypographyEngine }

// ------------------------------------------------------------------ Fonts (cached at composition start)
private val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)
private val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal)
)

/**
 * MaterialTheme typography aligned to the Design System three-voice contract:
 * display, heading, body, label, caption, data-mono. Inter for display/UI,
 * JetBrains Mono for data/terminal/HUD.
 */
private val JvTypographyStyle = Typography(
    displayLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, lineHeight = 56.sp, color = JvTokens.TextPrimary),
    headlineLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, color = JvTokens.TextPrimary),
    headlineMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp, color = JvTokens.TextPrimary),
    titleLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = JvTokens.TextPrimary),
    bodyLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = JvTokens.TextPrimary),
    labelSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.12.em, color = JvTokens.TextSecondary),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = JvTokens.TextSecondary)
)

/** The frozen Design System dark color scheme. */
private val JvDarkScheme = darkColorScheme(
    background = JvTokens.VoidBlack,
    surface = JvTokens.VoidBlack,
    surfaceVariant = JvTokens.DeepSpace,
    onBackground = JvTokens.TextPrimary,
    onSurface = JvTokens.TextPrimary,
    primary = JvTokens.StateIdle,
    error = JvTokens.StateWarning,
    onPrimary = JvTokens.IgnitionWhite
)

/**
 * JvTheme: the single root composable for the Visual Foundation.
 *
 * Provides `LocalJvTokens` and `LocalJvTypography` to all descendant
 * Composables, and wraps `MaterialTheme` with the frozen Design System
 * palette and typography.
 */
@Composable
fun JvTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalJvTokens provides JvTokens,
        LocalJvTypography provides TypographyEngine
    ) {
        MaterialTheme(
            colorScheme = JvDarkScheme,
            typography = JvTypographyStyle,
            content = content
        )
    }
}

