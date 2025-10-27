package com.galggg.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Custom VPN button colors
val VpnButtonDisconnectedInner = Color(0xFF7a7a7a)
val VpnButtonDisconnectedOuter = Color(0xFF4a4a4a)
val VpnButtonConnectedInner = Color(0xFF5dd3b3)
val VpnButtonConnectedOuter = Color(0xFF3a8a72)
val ErrorTint = Color(0xFFfb9e9e)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFCFCFC),
    onPrimary = Color(0xFF353535),
    secondary = Color(0xFF444444),
    onSecondary = Color(0xFFFCFCFC),
    background = Color(0xFF181a1e),
    onBackground = Color(0xFFFCFCFC),
    surface = Color(0xFF202128),
    onSurface = Color(0xFFFCFCFC),
    error = Color(0xFFC0443D)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF353535),
    onPrimary = Color(0xFFFCFCFC),
    secondary = Color(0xFFECECEC),
    onSecondary = Color(0xFF353535),
    background = Color(0xFFF8F8FA),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    error = Color(0xFFC0443D)
)

@Composable
fun VpnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) { 
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}