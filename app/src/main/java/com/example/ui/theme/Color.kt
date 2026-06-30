package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val PrimaryBlue = Color(0xFF1565C0)
val OnPrimary = Color.White
val PrimaryContainer = Color(0xFFD0E4FF)
val OnPrimaryContainer = Color(0xFF001D36)

val SecondaryTeal = Color(0xFF00897B)
val OnSecondary = Color.White
val SecondaryContainer = Color(0xFFB2DFDB)
val OnSecondaryContainer = Color(0xFF00201C)

val TertiaryGreen = Color(0xFFC8E6C9)
val OnTertiary = Color(0xFF1B5E20)
val TertiaryContainer = Color(0xFFC8E6C9)
val OnTertiaryContainer = Color(0xFF1B5E20)

val ErrorRed = Color(0xFFFFCDD2)
val OnError = Color(0xFFB71C1C)
val ErrorContainer = Color(0xFFFFCDD2)
val OnErrorContainer = Color(0xFFB71C1C)

val Surface = Color.White
val OnSurface = Color(0xFF1E293B) // slate-800
val Background = Color(0xFFF0F4FF)
val OnBackground = Color(0xFF0F172A) // slate-900

val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)
val Slate100 = Color(0xFFF1F5F9)

data class AppThemePalette(
    val name: String,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val description: String
)

val AppThemes = listOf(
    // 0: Cool Slate (Professional)
    AppThemePalette(
        name = "Cool Slate",
        primary = Color(0xFF1E3A8A), // Slate Dark Blue
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDBEAFE), // Ice Blue
        onPrimaryContainer = Color(0xFF1E40AF),
        background = Color(0xFFF1F5F9), // Slate 100
        onBackground = Color(0xFF0F172A), // Slate 900
        surface = Color.White,
        onSurface = Color(0xFF1E293B), // Slate 800
        description = "Sleek slate with refreshing ice-blue details."
    ),
    // 1: Warm Editorial (Amber)
    AppThemePalette(
        name = "Warm Editorial",
        primary = Color(0xFFB45309), // Warm Amber
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFEF3C7), // Light Warm Cream
        onPrimaryContainer = Color(0xFF78350F),
        background = Color(0xFFFFFBEB), // Very Light warm background
        onBackground = Color(0xFF451A03), // Espresso dark
        surface = Color.White,
        onSurface = Color(0xFF78350F),
        description = "Ivory editorial colors for active reading."
    ),
    // 2: Cosmic Royal (Purple)
    AppThemePalette(
        name = "Cosmic Royal",
        primary = Color(0xFF6D28D9), // Purple 700
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE9FE), // Purple 100
        onPrimaryContainer = Color(0xFF4C1D95),
        background = Color(0xFFFAF5FF), // Light purple tinted background
        onBackground = Color(0xFF3B0764),
        surface = Color.White,
        onSurface = Color(0xFF5B21B6),
        description = "Deep mysterious violet with indigo tones."
    ),
    // 3: Vibrant Orchid (Pink)
    AppThemePalette(
        name = "Vibrant Orchid",
        primary = Color(0xFFBE185D), // Pink 700
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFCE7F3), // Pink 100
        onPrimaryContainer = Color(0xFF9D174D),
        background = Color(0xFFFFF1F2), // Light pink tinted background
        onBackground = Color(0xFF4C0519),
        surface = Color.White,
        onSurface = Color(0xFF831843),
        description = "Energetic fuchsia highlights and soft rose gradients."
    ),
    // 4: Forest Zen (Green)
    AppThemePalette(
        name = "Forest Zen",
        primary = Color(0xFF047857), // Emerald 700
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD1FAE5), // Emerald 100
        onPrimaryContainer = Color(0xFF065F46),
        background = Color(0xFFF0FDF4), // Light green tinted background
        onBackground = Color(0xFF14532D),
        surface = Color.White,
        onSurface = Color(0xFF065F46),
        description = "Calming deep forest pine and soothing mint."
    )
)
