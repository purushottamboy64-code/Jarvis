package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ArushiDarkColorScheme = darkColorScheme(
    primary = CoralPulse,
    onPrimary = TextPrimary,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = CoralGlow,
    secondary = ElectricCyan,
    onSecondary = ObsidianNight,
    secondaryContainer = SurfaceGlass,
    onSecondaryContainer = ElectricCyan,
    tertiary = AmberWarm,
    onTertiary = ObsidianNight,
    background = ObsidianNight,
    onBackground = TextPrimary,
    surface = DeepIndigo,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceGlass,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSubtle,
    error = ErrorCrimson,
    onError = TextPrimary,
    errorContainer = ErrorSurface,
    onErrorContainer = CoralGlow
)

@Composable
fun ArushiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ArushiDarkColorScheme,
        typography = ArushiTypography,
        content = content
    )
}
