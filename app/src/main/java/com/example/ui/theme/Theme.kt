package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
    SYSTEM("Auto"),
    LIGHT("Light"),
    DARK("Dark"),
}

@Immutable
data class PrismExtendedColors(
    val isDark: Boolean,
    val canvas: Color,
    val groupedCanvas: Color,
    val groupedCard: Color,
    val secondaryFill: Color,
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val systemBlue: Color,
    val notesGold: Color,
    val musicRose: Color,
    val youtubeRed: Color,
)

val LocalPrismColors = staticCompositionLocalOf {
    PrismExtendedColors(
        isDark = false,
        canvas = IosLightBackground,
        groupedCanvas = IosLightGroupedBackground,
        groupedCard = IosLightCard,
        secondaryFill = IosLightSecondaryFill,
        separator = IosLightSeparator,
        label = IosLightLabel,
        secondaryLabel = IosLightSecondaryLabel,
        tertiaryLabel = IosLightTertiaryLabel,
        systemBlue = IosSystemBlue,
        notesGold = NotesGoldLight,
        musicRose = MusicRose,
        youtubeRed = YouTubeRed,
    )
}

private val PrismLightColorScheme = lightColorScheme(
    primary = MusicRose,
    onPrimary = Color.White,
    secondary = NotesGoldLight,
    onSecondary = Color.White,
    tertiary = IosSystemBlue,
    onTertiary = Color.White,
    background = IosLightBackground,
    onBackground = IosLightLabel,
    surface = IosLightCard,
    onSurface = IosLightLabel,
    surfaceVariant = IosLightGroupedBackground,
    onSurfaceVariant = IosLightSecondaryLabel,
    outline = IosLightSeparator,
)

private val PrismDarkColorScheme = darkColorScheme(
    primary = MusicRose,
    onPrimary = Color.White,
    secondary = NotesGoldDark,
    onSecondary = Color.Black,
    tertiary = IosSystemBlueDark,
    onTertiary = Color.White,
    background = IosDarkBackground,
    onBackground = IosDarkLabel,
    surface = IosDarkCard,
    onSurface = IosDarkLabel,
    surfaceVariant = IosDarkSecondaryFill,
    onSurfaceVariant = IosDarkSecondaryLabel,
    outline = IosDarkSeparator,
)

@Composable
fun PrismTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val extendedColors = if (isDark) {
        PrismExtendedColors(
            isDark = true,
            canvas = IosDarkBackground,
            groupedCanvas = IosDarkGroupedBackground,
            groupedCard = IosDarkCard,
            secondaryFill = IosDarkSecondaryFill,
            separator = IosDarkSeparator,
            label = IosDarkLabel,
            secondaryLabel = IosDarkSecondaryLabel,
            tertiaryLabel = IosDarkTertiaryLabel,
            systemBlue = IosSystemBlueDark,
            notesGold = NotesGoldDark,
            musicRose = MusicRose,
            youtubeRed = YouTubeRed,
        )
    } else {
        PrismExtendedColors(
            isDark = false,
            canvas = IosLightBackground,
            groupedCanvas = IosLightGroupedBackground,
            groupedCard = IosLightCard,
            secondaryFill = IosLightSecondaryFill,
            separator = IosLightSeparator,
            label = IosLightLabel,
            secondaryLabel = IosLightSecondaryLabel,
            tertiaryLabel = IosLightTertiaryLabel,
            systemBlue = IosSystemBlue,
            notesGold = NotesGoldLight,
            musicRose = MusicRose,
            youtubeRed = YouTubeRed,
        )
    }

    CompositionLocalProvider(LocalPrismColors provides extendedColors) {
        MaterialTheme(
            colorScheme = if (isDark) PrismDarkColorScheme else PrismLightColorScheme,
            typography = PrismTypography,
            content = content,
        )
    }
}
