package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// WhiteSmoke & Light Canvas Colors
val WhiteSmoke = Color(0xFFF5F5F7)
val WhiteSmokeAlt = Color(0xFFF0F4F8)
val PureWhite = Color(0xFFFFFFFF)
val CardBorderLight = Color(0xFFE2E8F0)
val CardBorderHover = Color(0xFFCBD5E1)

// Light Blue Palette (Sky / Marine / Ice)
val LightBluePrimary = Color(0xFF0284C7)      // Refined Sky 600
val LightBlueAccent = Color(0xFF0EA5E9)       // Sky 500
val LightBlueSubtle = Color(0xFF38BDF8)       // Sky 400
val LightBlueContainer = Color(0xFFE0F2FE)    // Soft Sky 100
val LightBlueSoft = Color(0xFFF0F9FF)         // Ice Sky 50
val LightBlueIce = Color(0xFFBAE6FD)          // Sky 200

// Neutral Slate Typography (Neat, clean contrast on whitesmoke)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)

// Status & Defense Indicators
val CrimsonAlert = Color(0xFFEF4444)
val CrimsonLight = Color(0xFFFEE2E2)
val CrimsonBorder = Color(0xFFFCA5A5)

val EmeraldSafe = Color(0xFF10B981)
val EmeraldLight = Color(0xFFD1FAE5)
val EmeraldBorder = Color(0xFF6EE7B7)

val AmberWarning = Color(0xFFF59E0B)
val AmberLight = Color(0xFFFEF3C7)

val PurpleCloud = Color(0xFF8B5CF6)
val PurpleLight = Color(0xFFEDE9FE)

// Aliases for clean semantic usage
val WhitePure = PureWhite
val DeepSlate800 = Slate800
val MutedSlate500 = Slate500
val LightBlue100 = CardBorderLight
val LightBlue50 = LightBlueSoft
val SkyBluePrimary = LightBluePrimary
val SkyBlueDark = Color(0xFF0369A1)
val SkyBlueSoft = LightBlueContainer

// Backward-compatibility aliases so existing references resolve seamlessly
val Navy900 = WhiteSmoke
val Navy800 = PureWhite
val Navy700 = LightBlueContainer
val CyanAccent = LightBluePrimary
val CyanGlow = LightBlueAccent

