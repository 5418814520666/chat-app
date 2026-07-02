package com.chatapp.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WxGreen = Color(0xFF07C160)
val WxDarkBg = Color(0xFF191919)
val WxDarkSurface = Color(0xFF2C2C2C)
val WxGray = Color(0xFF999999)
val WxBubbleSelf = Color(0xFF95EC69)
val WxBubbleOther = Color(0xFFFFFFFF)

private val DarkScheme = darkColorScheme(
    primary = WxGreen,
    onPrimary = Color.White,
    secondary = WxGreen,
    background = WxDarkBg,
    surface = WxDarkSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = WxGray,
    outline = Color(0xFF444444),
    error = Color(0xFFFF5252),
    onError = Color.White
)

@Composable
fun ChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
