package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkSecurityColorScheme = darkColorScheme(
    primary = NightElectricSky,
    onPrimary = Color(0xFF082F49),
    primaryContainer = NightSkyContainer,
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = NightSkyAccent,
    onSecondary = Color(0xFF082F49),
    secondaryContainer = NightSkySoft,
    onSecondaryContainer = NightTextPrimary,
    tertiary = AmberWarningDark,
    onTertiary = Color.Black,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = NightObsidian,
    onBackground = NightTextPrimary,
    surface = NightSurface,
    onSurface = NightTextPrimary,
    surfaceVariant = NightSurfaceAlt,
    onSurfaceVariant = NightTextSecondary,
    error = CrimsonAlertDark,
    onError = Color.White,
    errorContainer = CrimsonContainerDark,
    onErrorContainer = Color(0xFFFCA5A5),
    outline = NightBorder,
    outlineVariant = NightBorderHover
)

private val LightBlueWhiteSmokeColorScheme = lightColorScheme(
    primary = DayLightBluePrimary,
    onPrimary = Color.White,
    primaryContainer = DayLightBlueContainer,
    onPrimaryContainer = DayLightBluePrimary,
    secondary = DayLightBlueAccent,
    onSecondary = Color.White,
    secondaryContainer = DayLightBlueSoft,
    onSecondaryContainer = DaySlate800,
    tertiary = AmberWarning,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = DaySlate900,
    background = DayWhiteSmoke,
    onBackground = DaySlate900,
    surface = DayPureWhite,
    onSurface = DaySlate900,
    surfaceVariant = DayWhiteSmokeAlt,
    onSurfaceVariant = DaySlate600,
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = CrimsonAlert,
    outline = DayCardBorderLight,
    outlineVariant = DaySlate200
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = NightSecurityThemeState.isDarkMode,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkSecurityColorScheme else LightBlueWhiteSmokeColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}


