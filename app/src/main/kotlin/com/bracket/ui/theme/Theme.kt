package com.bracket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BracketBlue   = Color(0xFF1565C0)
private val BracketRed    = Color(0xFFB71C1C)
private val BracketGold   = Color(0xFFF9A825)

private val LightColors = lightColorScheme(
    primary          = BracketBlue,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFD0E4FF),
    secondary        = BracketRed,
    onSecondary      = Color.White,
    tertiary         = BracketGold,
    background       = Color(0xFFF5F5F5),
    surface          = Color.White,
    onBackground     = Color(0xFF1C1B1F),
    onSurface        = Color(0xFF1C1B1F),
)

private val DarkColors = darkColorScheme(
    primary          = Color(0xFF90CAF9),
    onPrimary        = Color(0xFF003065),
    primaryContainer = Color(0xFF1565C0),
    secondary        = Color(0xFFEF9A9A),
    onSecondary      = Color(0xFF7F0000),
    tertiary         = BracketGold,
    background       = Color(0xFF1C1B1F),
    surface          = Color(0xFF2C2C2E),
    onBackground     = Color(0xFFE6E1E5),
    onSurface        = Color(0xFFE6E1E5),
)

@Composable
fun BracketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content     = content
    )
}
