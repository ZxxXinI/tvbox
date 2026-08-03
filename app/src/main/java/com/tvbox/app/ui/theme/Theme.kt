package com.tvbox.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvbox.app.domain.TvTheme

data class TvColorTokens(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceOverlay: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val focusRing: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
)

private val DefaultTokens = TvColorTokens(
    background = Color(0xFF121212),
    surface = Color(0xFF181818),
    surfaceRaised = Color(0xFF252525),
    surfaceOverlay = Color(0xE6181818),
    border = Color(0x664D4D4D),
    textPrimary = Color.White,
    textSecondary = Color(0xFFB3B3B3),
    textTertiary = Color(0xFF888888),
    accent = Color(0xFF1ED760),
    accentStrong = Color(0xFF1DB954),
    accentSoft = Color(0x331ED760),
    focusRing = Color.White,
    success = Color(0xFF1ED760),
    warning = Color(0xFFFFA42B),
    danger = Color(0xFFF3727F),
)

private val CinemaTokens = TvColorTokens(
    background = Color(0xFF080B0B),
    surface = Color(0xFF121716),
    surfaceRaised = Color(0xFF1A211E),
    surfaceOverlay = Color(0xCC0C1110),
    border = Color(0x3357635C),
    textPrimary = Color(0xFFF5F8F6),
    textSecondary = Color(0xFFA6B0AA),
    textTertiary = Color(0xFF7D8982),
    accent = Color(0xFF29E68C),
    accentStrong = Color(0xFF55F0A1),
    accentSoft = Color(0x2229E68C),
    focusRing = Color(0xFFF7FAF8),
    success = Color(0xFF55F0A1),
    warning = Color(0xFFFFC857),
    danger = Color(0xFFFF7171),
)

val LocalTvColors = staticCompositionLocalOf { DefaultTokens }

object TvColors {
    val Background: Color @Composable get() = LocalTvColors.current.background
    val Surface: Color @Composable get() = LocalTvColors.current.surface
    val SurfaceRaised: Color @Composable get() = LocalTvColors.current.surfaceRaised
    val SurfaceOverlay: Color @Composable get() = LocalTvColors.current.surfaceOverlay
    val Border: Color @Composable get() = LocalTvColors.current.border
    val TextPrimary: Color @Composable get() = LocalTvColors.current.textPrimary
    val TextSecondary: Color @Composable get() = LocalTvColors.current.textSecondary
    val TextTertiary: Color @Composable get() = LocalTvColors.current.textTertiary
    val Accent: Color @Composable get() = LocalTvColors.current.accent
    val AccentStrong: Color @Composable get() = LocalTvColors.current.accentStrong
    val AccentSoft: Color @Composable get() = LocalTvColors.current.accentSoft
    val FocusRing: Color @Composable get() = LocalTvColors.current.focusRing
    val Success: Color @Composable get() = LocalTvColors.current.success
    val Warning: Color @Composable get() = LocalTvColors.current.warning
    val Danger: Color @Composable get() = LocalTvColors.current.danger
}

object TvDimens {
    val RailWidth = 76.dp
    val PageHorizontalPadding = 28.dp
    val PageVerticalPadding = 24.dp
    val SectionGap = 16.dp
    val CardRadius = 12.dp
    val FocusScale = 1.04f
}

private fun colorScheme(tokens: TvColorTokens): ColorScheme = darkColorScheme(
    primary = tokens.accent,
    primaryContainer = tokens.accentStrong,
    secondary = tokens.accentStrong,
    tertiary = tokens.warning,
    background = tokens.background,
    surface = tokens.surface,
    surfaceVariant = tokens.surfaceRaised,
    onPrimary = Color(0xFF06130A),
    onPrimaryContainer = Color(0xFF06130A),
    onSecondary = Color(0xFF07182E),
    onTertiary = Color(0xFF241300),
    onBackground = tokens.textPrimary,
    onSurface = tokens.textPrimary,
    onSurfaceVariant = tokens.textSecondary,
    outline = tokens.border,
    error = tokens.danger,
)

private val TvTypography = Typography(
    displaySmall = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Medium),
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    bodySmall = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 12.sp),
)

@Composable
fun TVBoxTheme(
    theme: TvTheme = TvTheme.Default,
    content: @Composable () -> Unit,
) {
    val tokens = if (theme == TvTheme.Cinema) CinemaTokens else DefaultTokens
    androidx.compose.runtime.CompositionLocalProvider(LocalTvColors provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme(tokens),
            typography = TvTypography,
            content = content,
        )
    }
}
