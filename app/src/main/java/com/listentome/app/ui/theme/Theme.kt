package com.listentome.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFB0C6FF),
    onSecondary = Color(0xFF002B6B),
    secondaryContainer = Color(0xFF15398E),
    onSecondaryContainer = Color(0xFFDBE1FF)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF0F52BA),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3FF),
    onSecondaryContainer = Color(0xFF001947)
)

@Composable
fun ListenToMeTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
