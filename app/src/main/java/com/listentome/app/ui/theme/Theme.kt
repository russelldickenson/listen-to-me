package com.listentome.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF7C5CFF)
private val PurpleDark = Color(0xFF5B3FD6)

private val DarkColors = darkColorScheme(
    primary = Purple,
    secondary = PurpleDark
)

private val LightColors = lightColorScheme(
    primary = Purple,
    secondary = PurpleDark
)

@Composable
fun ListenToMeTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
