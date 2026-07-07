package com.tvbox.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TvColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF1ED760),
    primaryContainer = Color(0xFF1DB954),
    secondary = Color(0xFF539DF5),
    tertiary = Color(0xFFFFA42B),
    background = Color(0xFF121212),
    surface = Color(0xFF181818),
    surfaceVariant = Color(0xFF252525),
    onPrimary = Color(0xFF06130A),
    onPrimaryContainer = Color(0xFF06130A),
    onSecondary = Color(0xFF07182E),
    onTertiary = Color(0xFF241300),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFB3B3B3),
    outline = Color(0xFF4D4D4D),
    error = Color(0xFFF3727F),
)

@Composable
fun TVBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvColorScheme,
        content = content,
    )
}
