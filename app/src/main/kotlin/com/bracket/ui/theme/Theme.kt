package com.bracket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Light palette ──────────────────────────────────────────────────────────────
// Primary: Deep Orange 900 — vivid, warm, passes AA contrast on white
// Secondary: Brown 700 — warm neutral complement
// Tertiary: Amber 800 — gold, used for rank #1 badge and correct-pick accents

private val LightColors = lightColorScheme(
    primary              = Color(0xFFBF360C),
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFFFDBCC),
    onPrimaryContainer   = Color(0xFF3E0700),
    secondary            = Color(0xFF6D4C41),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFEFEBE9),
    onSecondaryContainer = Color(0xFF1A0000),
    tertiary             = Color(0xFFF57F17),
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFFFF8E1),
    onTertiaryContainer  = Color(0xFF1A1200),
    background           = Color(0xFFFFFBFF),
    onBackground         = Color(0xFF201A19),
    surface              = Color(0xFFFFFFFF),
    onSurface            = Color(0xFF201A19),
    surfaceVariant       = Color(0xFFF5DED8),
    onSurfaceVariant     = Color(0xFF534340),
    outline              = Color(0xFF85736F),
    error                = Color(0xFFBA1A1A),
    onError              = Color.White,
    errorContainer       = Color(0xFFFFDAD6),
    onErrorContainer     = Color(0xFF410002),
)

// ── Dark palette ───────────────────────────────────────────────────────────────

private val DarkColors = darkColorScheme(
    primary              = Color(0xFFFF8A65),
    onPrimary            = Color(0xFF5C1700),
    primaryContainer     = Color(0xFF8C2F00),
    onPrimaryContainer   = Color(0xFFFFDBCC),
    secondary            = Color(0xFFBCAAA4),
    onSecondary          = Color(0xFF3A0000),
    secondaryContainer   = Color(0xFF4A2D28),
    onSecondaryContainer = Color(0xFFD7CCC8),
    tertiary             = Color(0xFFFFCA28),
    onTertiary           = Color(0xFF3A2F00),
    tertiaryContainer    = Color(0xFF554500),
    onTertiaryContainer  = Color(0xFFFFF9C4),
    background           = Color(0xFF201A19),
    onBackground         = Color(0xFFEDE0DD),
    surface              = Color(0xFF2C2321),
    onSurface            = Color(0xFFEDE0DD),
    surfaceVariant       = Color(0xFF534340),
    onSurfaceVariant     = Color(0xFFD8C2BD),
    outline              = Color(0xFFA08C88),
    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),
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
