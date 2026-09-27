package de.handyzeitvertreib.app.ui.designsystem

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.handyzeitvertreib.app.core.model.ThemeMode

/** Semantic colour tokens. Components use these names instead of raw colours. */
@Immutable
data class HzvColors(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val ambientA: Color,
    val ambientB: Color,
    val ambientC: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassEdgeTop: Color,
    val glassEdgeBottom: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val track: Color,
    val warning: Color,
    val limitReached: Color,
    val success: Color,
    val isDark: Boolean,
)

private val LightTokens =
    HzvColors(
        backgroundTop = Color(0xFFF1F5F9),
        backgroundBottom = Color(0xFFE5EBF3),
        ambientA = Color(0x8C9ED8D2),
        ambientB = Color(0x80B9C3FF),
        ambientC = Color(0x66FFD7C2),
        glassFill = Color(0x9EFFFFFF),
        glassFillStrong = Color(0xD9FFFFFF),
        glassEdgeTop = Color(0xE6FFFFFF),
        glassEdgeBottom = Color(0x40FFFFFF),
        textPrimary = Color(0xFF14202B),
        textSecondary = Color(0xFF475563),
        accent = Color(0xFF1B6A73),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0x331B6A73),
        track = Color(0x1F14202B),
        warning = Color(0xFF8A5A00),
        limitReached = Color(0xFFA63A26),
        success = Color(0xFF2E7045),
        isDark = false,
    )

private val DarkTokens =
    HzvColors(
        backgroundTop = Color(0xFF0D141E),
        backgroundBottom = Color(0xFF111A28),
        ambientA = Color(0x731E5A5A),
        ambientB = Color(0x732E3A7A),
        ambientC = Color(0x594A2A4F),
        glassFill = Color(0x17FFFFFF),
        glassFillStrong = Color(0x2BFFFFFF),
        glassEdgeTop = Color(0x3DFFFFFF),
        glassEdgeBottom = Color(0x0DFFFFFF),
        textPrimary = Color(0xFFEAF0F6),
        textSecondary = Color(0xFFAEBBC8),
        accent = Color(0xFF82D3CC),
        onAccent = Color(0xFF00201F),
        accentSoft = Color(0x3382D3CC),
        track = Color(0x26FFFFFF),
        warning = Color(0xFFF2C14E),
        limitReached = Color(0xFFFF9C87),
        success = Color(0xFF86DBA2),
        isDark = true,
    )

val LocalHzvColors = staticCompositionLocalOf { LightTokens }

/** True when animations should be skipped (app setting or system "remove animations"). */
val LocalReduceMotion = staticCompositionLocalOf { false }

object HzvTheme {
    val colors: HzvColors
        @Composable get() = LocalHzvColors.current
}

private fun schemeFrom(tokens: HzvColors): ColorScheme {
    val base = if (tokens.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = tokens.accent,
        onPrimary = tokens.onAccent,
        primaryContainer = tokens.accentSoft,
        onPrimaryContainer = tokens.textPrimary,
        secondary = tokens.accent,
        onSecondary = tokens.onAccent,
        background = tokens.backgroundTop,
        onBackground = tokens.textPrimary,
        surface = tokens.backgroundTop,
        onSurface = tokens.textPrimary,
        onSurfaceVariant = tokens.textSecondary,
        surfaceVariant = tokens.backgroundBottom,
        surfaceContainerHigh = tokens.backgroundBottom,
        surfaceContainerHighest = tokens.backgroundBottom,
        error = tokens.limitReached,
    )
}

private val HzvTypography =
    Typography().run {
        copy(
            displayMedium = displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
            headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
        )
    }

val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun HzvTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val context = LocalContext.current
    var tokens = if (dark) DarkTokens else LightTokens
    val scheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            tokens = tokens.copy(accent = dynamic.primary, onAccent = dynamic.onPrimary, accentSoft = dynamic.primary.copy(alpha = 0.2f))
            schemeFrom(tokens)
        } else {
            schemeFrom(tokens)
        }
    val systemAnimationsOff =
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    CompositionLocalProvider(
        LocalHzvColors provides tokens,
        LocalReduceMotion provides (reduceMotion || systemAnimationsOff),
    ) {
        MaterialTheme(colorScheme = scheme, typography = HzvTypography, content = content)
    }
}
