package ai.friday.assistant.ui.theme

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
import ai.friday.assistant.R

/**
 * Friday's canonical semantic palette.
 *
 * These values intentionally match the Visual Foundation tokens so legacy
 * screens and newer components cannot assign different meanings to the same
 * system state. Color is semantic: blue=idle, teal=listening, amber=thinking,
 * violet=sleep, green=verified success, red=warning/critical.
 */
object FridayColors {
    val SurfaceBase = Color(0xFF000000)
    val SurfacePanel = Color(0xFF080D18)
    val SurfaceHairline = Color(0x33FFFFFF)
    val CoreIdle = Color(0xFF2E9BFF)
    val CoreListening = Color(0xFF00D9C0)
    val CoreThinking = Color(0xFFF5A623)
    val CoreBuilding = Color(0xFFE8ECF2)
    val CoreSuccess = Color(0xFF34D399)
    val CoreWarning = Color(0xFFFF3B30)
    val CoreCritical = Color(0xFFFF1744)
    val CoreSleep = Color(0xFF7C4DFF)
    val CoreOffline = Color(0xFF8A94A6)
    val CoreUnknown = Color(0xFF8A94A6)
    val CoreAttention = Color(0xFFF5A623)
    val TextPrimary = Color(0xFFECEDF1)
    val TextSecondary = Color(0xFFA8B2C4)
    val TextOnAccent = Color(0xFF04101D)
}

/** Inter for product/UI language; JetBrains Mono only for actual machine data. */
private val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

private val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal)
)

object FridayType {
    val Display = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        color = FridayColors.TextPrimary
    )
    val Body = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = FridayColors.TextPrimary
    )
    val Technical = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = FridayColors.TextSecondary
    )
    val Label = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.12.em,
        color = FridayColors.TextSecondary
    )
}

private val FridayMaterialTypography = Typography(
    displayLarge = FridayType.Display.copy(fontSize = 48.sp, lineHeight = 56.sp),
    displayMedium = FridayType.Display.copy(fontSize = 40.sp, lineHeight = 48.sp),
    displaySmall = FridayType.Display,
    headlineLarge = FridayType.Display,
    headlineMedium = FridayType.Display.copy(fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = FridayType.Display.copy(fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = FridayType.Display.copy(fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = FridayType.Body.copy(fontWeight = FontWeight.Medium),
    titleSmall = FridayType.Body.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = FridayType.Body,
    bodyMedium = FridayType.Body,
    bodySmall = FridayType.Body.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = FridayType.Label.copy(fontSize = 14.sp, color = Color.Unspecified),
    labelMedium = FridayType.Label.copy(color = Color.Unspecified),
    labelSmall = FridayType.Label.copy(fontSize = 11.sp, letterSpacing = 0.08.em, color = Color.Unspecified)
)

private val FridayDarkScheme = darkColorScheme(
    primary = FridayColors.CoreIdle,
    onPrimary = FridayColors.TextOnAccent,
    primaryContainer = Color(0xFF102A45),
    onPrimaryContainer = FridayColors.TextPrimary,
    secondary = FridayColors.CoreListening,
    onSecondary = FridayColors.TextOnAccent,
    secondaryContainer = Color(0xFF07312F),
    onSecondaryContainer = FridayColors.TextPrimary,
    tertiary = FridayColors.CoreThinking,
    onTertiary = FridayColors.TextOnAccent,
    background = FridayColors.SurfaceBase,
    onBackground = FridayColors.TextPrimary,
    surface = FridayColors.SurfaceBase,
    onSurface = FridayColors.TextPrimary,
    surfaceVariant = FridayColors.SurfacePanel,
    onSurfaceVariant = FridayColors.TextSecondary,
    outline = Color(0xFF465267),
    outlineVariant = Color(0xFF293448),
    error = FridayColors.CoreWarning,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3A1115),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun FridayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FridayDarkScheme,
        typography = FridayMaterialTypography,
        content = content
    )
}
