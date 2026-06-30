package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = SecondaryTeal,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = TertiaryGreen,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = ErrorRed,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    surface = Surface,
    onSurface = OnSurface,
    background = Background,
    onBackground = OnBackground
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryContainer,
    onPrimary = OnPrimaryContainer,
    primaryContainer = PrimaryBlue,
    onPrimaryContainer = OnPrimary,
    secondary = SecondaryContainer,
    onSecondary = OnSecondaryContainer,
    secondaryContainer = SecondaryTeal,
    onSecondaryContainer = OnSecondary,
    tertiary = TertiaryContainer,
    onTertiary = OnTertiaryContainer,
    tertiaryContainer = TertiaryGreen,
    onTertiaryContainer = OnTertiary,
    error = ErrorContainer,
    onError = OnErrorContainer,
    errorContainer = ErrorRed,
    onErrorContainer = OnError,
    surface = OnSurface,
    onSurface = Surface,
    background = OnBackground,
    onBackground = Background
)

@Composable
fun MyApplicationTheme(
    themeIndex: Int = 0,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val selectedPalette = AppThemes.getOrNull(themeIndex) ?: AppThemes[0]
    
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = selectedPalette.primaryContainer, // Light/vibrant pastel primary
            onPrimary = selectedPalette.primary, // Contrast dark text
            primaryContainer = selectedPalette.primary, // Deeper brand color
            onPrimaryContainer = selectedPalette.onPrimary, // White or light
            secondary = Color(0xFF4DB6AC), // Teal-accent
            onSecondary = Color(0xFF003730),
            secondaryContainer = Color(0xFF004D40),
            onSecondaryContainer = Color(0xFFB2DFDB),
            tertiary = Color(0xFF81C784), // Green-accent
            onTertiary = Color(0xFF0C3811),
            tertiaryContainer = Color(0xFF1B5E20),
            onTertiaryContainer = Color(0xFFC8E6C9),
            error = Color(0xFFE57373),
            onError = Color(0xFF3B0505),
            errorContainer = Color(0xFFB71C1C),
            onErrorContainer = Color(0xFFFFCDD2),
            background = Color(0xFF0F172A), // Slate 900
            onBackground = Color(0xFFF1F5F9), // Slate 100
            surface = Color(0xFF1E293B), // Slate 800
            onSurface = Color(0xFFF1F5F9)
        )
    } else {
        lightColorScheme(
            primary = selectedPalette.primary,
            onPrimary = selectedPalette.onPrimary,
            primaryContainer = selectedPalette.primaryContainer,
            onPrimaryContainer = selectedPalette.onPrimaryContainer,
            secondary = SecondaryTeal,
            onSecondary = OnSecondary,
            secondaryContainer = SecondaryContainer,
            onSecondaryContainer = OnSecondaryContainer,
            tertiary = TertiaryGreen,
            onTertiary = OnTertiary,
            tertiaryContainer = TertiaryContainer,
            onTertiaryContainer = OnTertiaryContainer,
            error = ErrorRed,
            onError = OnError,
            errorContainer = ErrorContainer,
            onErrorContainer = OnErrorContainer,
            surface = selectedPalette.surface,
            onSurface = selectedPalette.onSurface,
            background = selectedPalette.background,
            onBackground = selectedPalette.onBackground
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
