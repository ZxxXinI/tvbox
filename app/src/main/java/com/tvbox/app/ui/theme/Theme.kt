package com.tvbox.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TvColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF23D1A8),
    secondary = Color(0xFF8FB7FF),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFF10131A),
    surface = Color(0xFF181D27),
    surfaceVariant = Color(0xFF252B38),
    onPrimary = Color(0xFF06251F),
    onSecondary = Color(0xFF081A33),
    onTertiary = Color(0xFF2B1B00),
    onBackground = Color(0xFFE9EEF7),
    onSurface = Color(0xFFE9EEF7),
    onSurfaceVariant = Color(0xFFB8C2D1),
)

@Composable
fun TVBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvColorScheme,
        content = content,
    )
}

