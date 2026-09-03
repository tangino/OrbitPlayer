package com.antigravity.equalizer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Orbit Player 专属语义化动态调色盘
 */
data class OrbitColors(
    val background: Color,
    val surface: Color,
    val surfaceCard: Color,
    val surfaceBorder: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val danger: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val gridLine: Color,
    val curveGlow: Color,
    val isDark: Boolean
)

val LocalOrbitColors = staticCompositionLocalOf {
    OrbitColors(
        background = DarkBackground,
        surface = SurfaceDark,
        surfaceCard = SurfaceCard,
        surfaceBorder = SurfaceCardBorder,
        primary = PrimaryNeonCyan,
        secondary = AccentPurple,
        tertiary = AccentOrange,
        danger = DangerRed,
        textPrimary = TextPrimary,
        textSecondary = TextSecondary,
        gridLine = GridLineColor,
        curveGlow = CurveGlow,
        isDark = true
    )
}

object OrbitTheme {
    val colors: OrbitColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOrbitColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryNeonCyan,
    secondary = AccentPurple,
    tertiary = AccentOrange,
    error = DangerRed,
    background = DarkBackground,
    surface = SurfaceDark,
    surfaceVariant = SurfaceCard,
    onPrimary = DarkBackground,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryCyanLight,
    secondary = AccentPurpleLight,
    tertiary = AccentOrangeLight,
    error = DangerRedLight,
    background = LightBackground,
    surface = SurfaceLight,
    surfaceVariant = SurfaceCardLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight
)

@Composable
fun MusicEqualizerTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val orbitColors = if (isDark) {
        OrbitColors(
            background = DarkBackground,
            surface = SurfaceDark,
            surfaceCard = SurfaceCard,
            surfaceBorder = SurfaceCardBorder,
            primary = PrimaryNeonCyan,
            secondary = AccentPurple,
            tertiary = AccentOrange,
            danger = DangerRed,
            textPrimary = TextPrimary,
            textSecondary = TextSecondary,
            gridLine = GridLineColor,
            curveGlow = CurveGlow,
            isDark = true
        )
    } else {
        OrbitColors(
            background = LightBackground,
            surface = SurfaceLight,
            surfaceCard = SurfaceCardLight,
            surfaceBorder = SurfaceCardBorderLight,
            primary = PrimaryCyanLight,
            secondary = AccentPurpleLight,
            tertiary = AccentOrangeLight,
            danger = DangerRedLight,
            textPrimary = TextPrimaryLight,
            textSecondary = TextSecondaryLight,
            gridLine = GridLineColorLight,
            curveGlow = CurveGlowLight,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalOrbitColors provides orbitColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
