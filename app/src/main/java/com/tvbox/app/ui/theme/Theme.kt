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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvbox.app.domain.TvFontScale
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

data class TvLayoutTokens(
    val posterGridMinWidth: Dp,
    val historyGridMinWidth: Dp,
    val episodeGridMinWidth: Dp,
    val settingsGridMinWidth: Dp,
    val platformLiveColumns: Int,
)

private val NormalLayoutTokens = TvLayoutTokens(
    posterGridMinWidth = 148.dp,
    historyGridMinWidth = 230.dp,
    episodeGridMinWidth = 132.dp,
    settingsGridMinWidth = 176.dp,
    platformLiveColumns = 5,
)

private val LargeLayoutTokens = TvLayoutTokens(
    posterGridMinWidth = 178.dp,
    historyGridMinWidth = 276.dp,
    episodeGridMinWidth = 156.dp,
    settingsGridMinWidth = 210.dp,
    platformLiveColumns = 4,
)

private val ExtraLargeLayoutTokens = TvLayoutTokens(
    posterGridMinWidth = 216.dp,
    historyGridMinWidth = 322.dp,
    episodeGridMinWidth = 184.dp,
    settingsGridMinWidth = 250.dp,
    platformLiveColumns = 3,
)

val LocalTvLayout = staticCompositionLocalOf { NormalLayoutTokens }

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

object TvLayout {
    val PosterGridMinWidth: Dp @Composable get() = LocalTvLayout.current.posterGridMinWidth
    val HistoryGridMinWidth: Dp @Composable get() = LocalTvLayout.current.historyGridMinWidth
    val EpisodeGridMinWidth: Dp @Composable get() = LocalTvLayout.current.episodeGridMinWidth
    val SettingsGridMinWidth: Dp @Composable get() = LocalTvLayout.current.settingsGridMinWidth
    val PlatformLiveColumns: Int @Composable get() = LocalTvLayout.current.platformLiveColumns
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

private fun Typography.scaled(multiplier: Float): Typography = copy(
    displayLarge = displayLarge.scaled(multiplier),
    displayMedium = displayMedium.scaled(multiplier),
    displaySmall = displaySmall.scaled(multiplier),
    headlineLarge = headlineLarge.scaled(multiplier),
    headlineMedium = headlineMedium.scaled(multiplier),
    headlineSmall = headlineSmall.scaled(multiplier),
    titleLarge = titleLarge.scaled(multiplier),
    titleMedium = titleMedium.scaled(multiplier),
    titleSmall = titleSmall.scaled(multiplier),
    bodyLarge = bodyLarge.scaled(multiplier),
    bodyMedium = bodyMedium.scaled(multiplier),
    bodySmall = bodySmall.scaled(multiplier),
    labelLarge = labelLarge.scaled(multiplier),
    labelMedium = labelMedium.scaled(multiplier),
    labelSmall = labelSmall.scaled(multiplier),
)

private fun TextStyle.scaled(multiplier: Float): TextStyle = copy(
    fontSize = fontSize * multiplier,
    letterSpacing = letterSpacing * multiplier,
)

private fun layoutTokens(fontScale: TvFontScale): TvLayoutTokens = when (fontScale) {
    TvFontScale.Normal -> NormalLayoutTokens
    TvFontScale.Large -> LargeLayoutTokens
    TvFontScale.ExtraLarge -> ExtraLargeLayoutTokens
}

@Composable
fun TVBoxTheme(
    theme: TvTheme = TvTheme.Default,
    fontScale: TvFontScale = TvFontScale.Normal,
    content: @Composable () -> Unit,
) {
    val tokens = if (theme == TvTheme.Cinema) CinemaTokens else DefaultTokens
    val typography = TvTypography.scaled(fontScale.typographyScale)
    androidx.compose.runtime.CompositionLocalProvider(
        LocalTvColors provides tokens,
        LocalTvLayout provides layoutTokens(fontScale),
    ) {
        MaterialTheme(
            colorScheme = colorScheme(tokens),
            typography = typography,
            content = content,
        )
    }
}
