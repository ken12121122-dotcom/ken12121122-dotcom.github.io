package com.fox.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FoxOrange = Color(0xFFF96A2B)
private val FoxOrangeDark = Color(0xFFC94E19)

private val LightColors = lightColorScheme(
    primary = FoxOrange,
    secondary = FoxOrangeDark,
)

private val DarkColors = darkColorScheme(
    primary = FoxOrange,
    secondary = FoxOrangeDark,
)

@Composable
fun FoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
