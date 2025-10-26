package com.galggg.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFCFCFC),
    onPrimary = Color(0xFF353535),
    secondary = Color(0xFF444444),
    onSecondary = Color(0xFFFCFCFC),
    background = Color(0xFF242424),
    onBackground = Color(0xFFFCFCFC),
    surface = Color(0xFF242424),
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