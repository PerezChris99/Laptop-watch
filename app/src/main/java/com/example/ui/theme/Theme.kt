package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightBlueWhiteSmokeColorScheme = lightColorScheme(
    primary = LightBluePrimary,
    onPrimary = Color.White,
    primaryContainer = LightBlueContainer,
    onPrimaryContainer = LightBluePrimary,
    secondary = LightBlueAccent,
    onSecondary = Color.White,
    secondaryContainer = LightBlueSoft,
    onSecondaryContainer = Slate800,
    tertiary = AmberWarning,
    onTertiary = Color.White,
    tertiaryContainer = AmberLight,
    onTertiaryContainer = Slate900,
    background = WhiteSmoke,
    onBackground = Slate900,
    surface = PureWhite,
    onSurface = Slate900,
    surfaceVariant = WhiteSmokeAlt,
    onSurfaceVariant = Slate600,
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonLight,
    onErrorContainer = CrimsonAlert,
    outline = CardBorderLight,
    outlineVariant = Slate200
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Neat, beautiful Light Blue & WhiteSmoke theme by default
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightBlueWhiteSmokeColorScheme,
        typography = Typography,
        content = content
    )
}

