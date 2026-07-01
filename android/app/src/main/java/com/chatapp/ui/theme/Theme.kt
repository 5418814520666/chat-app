package com.chatapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF1A1A2E)
val DarkSurface = Color(0xFF16213E)
val DarkSurfaceVariant = Color(0xFF0F3460)
val Accent = Color(0xFFE94560)
val AccentLight = Color(0xFFFF6B6B)
val Success = Color(0xFF4CAF50)
val Warning = Color(0xFFFFC107)
val TextPrimary = Color(0xFFEEEEEE)
val TextSecondary = Color(0xFFAAAAAA)
val BorderColor = Color(0xFF2A2A4A)
val InputBg = Color(0xFF242450)

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = AccentLight,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
    error = Color(0xFFFF5252),
    onError = Color.White
)

@Composable
fun ChatAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
