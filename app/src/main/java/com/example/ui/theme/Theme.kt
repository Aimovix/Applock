package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = CyanSecondary,
    onSecondary = Color(0xFF082F49),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = AmberWarning,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralContainer,
    onErrorContainer = OnCoralContainer,
    background = ObsidianBackground,
    onBackground = SlateTextPrimary,
    surface = ObsidianSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = SlateTextSecondary,
    outline = SlateOutline
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldLightPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldLightContainer,
    onPrimaryContainer = OnEmeraldLightContainer,
    secondary = CyanLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = CyanLightContainer,
    onSecondaryContainer = OnCyanLightContainer,
    tertiary = AmberWarning,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = CoralError,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = AlabasterBackground,
    onBackground = SlateLightTextPrimary,
    surface = AlabasterSurface,
    onSurface = SlateLightTextPrimary,
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = SlateLightTextSecondary,
    outline = SlateLightOutline
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
