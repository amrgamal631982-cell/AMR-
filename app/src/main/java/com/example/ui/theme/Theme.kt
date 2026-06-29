package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = VibrantPrimary,
    secondary = VibrantSecondary,
    tertiary = AccentGreen,
    background = Color(0xFF0F172A), // Keep slate dark for dark mode fans
    surface = Color(0xFF1E293B),
    error = VibrantSOSBorder,
    onPrimary = Color.White,
    onSecondary = VibrantTextDark,
    onTertiary = Color.White,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = VibrantPrimary,
    secondary = VibrantSecondary,
    tertiary = AccentGreen,
    background = VibrantBackground,
    surface = VibrantSurface,
    error = VibrantSOSBorder,
    onPrimary = Color.White,
    onSecondary = VibrantTextDark,
    onTertiary = Color.White,
    onBackground = VibrantTextDark,
    onSurface = VibrantTextDark,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Set default to false to showcase the beautiful Vibrant Palette light mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

