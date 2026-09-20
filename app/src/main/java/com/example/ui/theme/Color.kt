package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Global Theme state for the application.
 * Default is dark mode (`true`) to reduce eye strain during nighttime security monitoring
 * and maximize battery longevity on OLED/AMOLED displays.
 */
object NightSecurityThemeState {
    var isDarkMode: Boolean by mutableStateOf(true)
}

// -------------------------------------------------------------
// Base Day Mode Colors (Light Blue & WhiteSmoke)
// -------------------------------------------------------------
val DayWhiteSmoke = Color(0xFFF5F5F7)
val DayWhiteSmokeAlt = Color(0xFFF0F4F8)
val DayPureWhite = Color(0xFFFFFFFF)
val DayCardBorderLight = Color(0xFFE2E8F0)
val DayCardBorderHover = Color(0xFFCBD5E1)

val DayLightBluePrimary = Color(0xFF0284C7)      // Refined Sky 600
val DayLightBlueAccent = Color(0xFF0EA5E9)       // Sky 500
val DayLightBlueSubtle = Color(0xFF38BDF8)       // Sky 400
val DayLightBlueContainer = Color(0xFFE0F2FE)    // Soft Sky 100
val DayLightBlueSoft = Color(0xFFF0F9FF)         // Ice Sky 50
val DayLightBlueIce = Color(0xFFBAE6FD)          // Sky 200

val DaySlate900 = Color(0xFF0F172A)
val DaySlate800 = Color(0xFF1E293B)
val DaySlate600 = Color(0xFF475569)
val DaySlate500 = Color(0xFF64748B)
val DaySlate400 = Color(0xFF94A3B8)
val DaySlate200 = Color(0xFFE2E8F0)
val DaySlate100 = Color(0xFFF1F5F9)

// -------------------------------------------------------------
// High-Efficiency Dark Security Palette (Nighttime Surveillance & OLED Power Saving)
// -------------------------------------------------------------
val NightObsidian = Color(0xFF070B14)          // Pure deep midnight obsidian canvas (OLED 0% power)
val NightSurface = Color(0xFF0F172A)           // Elevated night tactical surface (Slate 900)
val NightSurfaceAlt = Color(0xFF162036)        // Secondary night surface (Slate 850)
val NightBorder = Color(0xFF1E2B3E)            // Low-eye-strain subtle border (Slate 800)
val NightBorderHover = Color(0xFF2E3E59)       // Active border highlight

val NightTextPrimary = Color(0xFFF8FAFC)       // Crisp high-contrast ice white (Slate 50)
val NightTextSecondary = Color(0xFF94A3B8)     // Soft anti-glare silver-slate (Slate 400)
val NightTextMuted = Color(0xFF64748B)         // Deep muted slate (Slate 500)

val NightElectricSky = Color(0xFF38BDF8)       // Radiant cyber sky blue (Sky 400)
val NightSkyAccent = Color(0xFF0EA5E9)         // Glowing marine cyan (Sky 500)
val NightSkyContainer = Color(0xFF0F253E)      // Deep night sky container
val NightSkySoft = Color(0xFF142032)           // Elevated dark pill/chip surface
val NightSkyGlow = Color(0xFF7DD3FC)           // Electric cyan text glow (Sky 300)

val CrimsonAlertDark = Color(0xFFF87171)       // Vivid alert coral/red
val CrimsonContainerDark = Color(0xFF3B1219)   // Low-glare deep maroon container
val CrimsonBorderDark = Color(0xFF7F1D1D)

val EmeraldSafeDark = Color(0xFF34D399)        // High-contrast radar green
val EmeraldContainerDark = Color(0xFF063323)   // Low-glare deep green container
val EmeraldBorderDark = Color(0xFF065F46)

val AmberWarningDark = Color(0xFFFBBF24)       // Crisp night amber
val AmberContainerDark = Color(0xFF362005)     // Low-glare deep amber container

// -------------------------------------------------------------
// Semantic Adaptive Color Accessors (Respond instantly to Dark Mode)
// -------------------------------------------------------------
val WhiteSmoke: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightObsidian else DayWhiteSmoke

val WhiteSmokeAlt: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightSurfaceAlt else DayWhiteSmokeAlt

val PureWhite: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightSurface else DayPureWhite

val WhitePure: Color
    @Composable
    get() = PureWhite

val CardBorderLight: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightBorder else DayCardBorderLight

val CardBorderHover: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightBorderHover else DayCardBorderHover

val LightBluePrimary: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightElectricSky else DayLightBluePrimary

val LightBlueAccent: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightSkyAccent else DayLightBlueAccent

val LightBlueSubtle: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightElectricSky else DayLightBlueSubtle

val LightBlueContainer: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightSkyContainer else DayLightBlueContainer

val LightBlueSoft: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightSkySoft else DayLightBlueSoft

val LightBlueIce: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightSkyContainer else DayLightBlueIce

val Slate900: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) Color(0xFFFFFFFF) else DaySlate900

val Slate800: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightTextPrimary else DaySlate800

val Slate600: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) Color(0xFFCBD5E1) else DaySlate600

val Slate500: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightTextSecondary else DaySlate500

val Slate400: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) Color(0xFF64748B) else DaySlate400

val Slate200: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightBorder else DaySlate200

val Slate100: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightSurfaceAlt else DaySlate100

// Status & Defense Indicators
val CrimsonAlert = Color(0xFFEF4444)
val CrimsonLight: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) CrimsonContainerDark else Color(0xFFFEE2E2)
val CrimsonBorder: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) CrimsonBorderDark else Color(0xFFFCA5A5)

val EmeraldSafe = Color(0xFF10B981)
val EmeraldLight: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) EmeraldContainerDark else Color(0xFFD1FAE5)
val EmeraldBorder: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) EmeraldBorderDark else Color(0xFF6EE7B7)

val AmberWarning = Color(0xFFF59E0B)
val AmberLight: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) AmberContainerDark else Color(0xFFFEF3C7)

val PurpleCloud = Color(0xFF8B5CF6)
val PurpleLight: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) Color(0xFF2E1A47) else Color(0xFFEDE9FE)

// Semantic Aliases
val DeepSlate800: Color
    @Composable
    get() = Slate800

val MutedSlate500: Color
    @Composable
    get() = Slate500

val LightBlue100: Color
    @Composable
    get() = CardBorderLight

val LightBlue50: Color
    @Composable
    get() = LightBlueSoft

val SkyBluePrimary: Color
    @Composable
    get() = LightBluePrimary

val SkyBlueDark: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightSkyGlow else Color(0xFF0369A1)

val SkyBlueSoft: Color
    @Composable
    get() = LightBlueContainer

val Navy900: Color
    @Composable
    get() = WhiteSmoke

val Navy800: Color
    @Composable
    get() = PureWhite

val Navy700: Color
    @Composable
    get() = LightBlueContainer

val CyanAccent: Color
    @Composable
    get() = LightBluePrimary

val CyanGlow: Color
    @Composable
    get() = LightBlueAccent

// Record / Video Button Theme-Aware Colors
val RecordButtonBg: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightSurfaceAlt else DaySlate800

val RecordButtonBorder: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) NightBorder else DaySlate800

// Threat Level Banner Theme-Aware Container Backgrounds
val ThreatBannerAlertBg: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) CrimsonContainerDark else Color(0xFFFFF1F2)

val ThreatBannerElevatedBg: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) AmberContainerDark else Color(0xFFFFFBEB)

val ThreatBannerSafeBg: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) EmeraldContainerDark else Color(0xFFF0FDF4)

val ThreatBannerNormalBg: Color
    @Composable
    get() = PureWhite

val AwayBadgeText: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) AmberWarningDark else Color(0xFFB45309)

val AlertDismissBg: Color
    @Composable
    get() = if (NightSecurityThemeState.isDarkMode) CrimsonBorderDark else Color(0xFFFECDD3)


