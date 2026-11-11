package com.bettercaper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimary,
    onPrimary = BackgroundDark,
    primaryContainer = GreenVariant,
    onPrimaryContainer = BackgroundDark,
    secondary = CyanAccent,
    onSecondary = BackgroundDark,
    background = BackgroundDark,
    onBackground = OnSurface,
    surface = SurfaceDark,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,
    error = ErrorRed,
    outline = OutlineMuted
)

@Composable
fun BetterCaperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = BetterCaperTypography,
        content = content
    )
}
