package com.smartlease.edge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PurpleDeep,
    secondary = OrangeAccent,
    background = Color.White,
    surface = Lavender,
    error = RedDanger
)

private val DarkColors = darkColorScheme(
    primary = OrangeAccent,
    secondary = PurpleMid,
    background = NavyDark,
    surface = PurpleDeep,
    error = RedDanger
)

@Composable
fun SmartLeaseEdgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
