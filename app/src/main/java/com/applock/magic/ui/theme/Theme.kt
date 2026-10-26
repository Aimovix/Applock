package com.applock.magic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SecurityDarkBackground = Color(0xFF121417)
val SecuritySurface = Color(0xFF1B1E23)
val SecuritySurfaceVariant = Color(0xFF262A32)
val SecurityPrimary = Color(0xFFE2E8F0) // Clean Titanium
val SecurityAccent = Color(0xFF2563EB) // Precision Royal Blue
val SecuritySuccess = Color(0xFF10B981) // Protected Green
val SecurityWarning = Color(0xFFF59E0B) // Permission Required Amber
val SecurityTextPrimary = Color(0xFFF8FAFC)
val SecurityTextSecondary = Color(0xFF94A3B8)

private val DarkColorScheme = darkColorScheme(
    primary = SecurityAccent,
    background = SecurityDarkBackground,
    surface = SecuritySurface,
    surfaceVariant = SecuritySurfaceVariant,
    onPrimary = Color.White,
    onBackground = SecurityTextPrimary,
    onSurface = SecurityTextPrimary
)

@Composable
fun HonorAppLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
