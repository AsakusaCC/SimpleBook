package com.ebookreader.simplebook.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ebookreader.simplebook.domain.model.ReaderTheme

val LocalRippleColor = compositionLocalOf { Color(0x206750A4) }
val LocalReaderTheme = compositionLocalOf { ReaderTheme.DEFAULT_WHITE }

private fun light(
    c: ThemeColors,
): ColorScheme = lightColorScheme(
    primary = c.primary, onPrimary = c.onPrimary,
    primaryContainer = c.primaryContainer, onPrimaryContainer = c.onPrimaryContainer,
    secondary = c.secondary, onSecondary = c.onSecondary,
    secondaryContainer = c.secondaryContainer, onSecondaryContainer = c.onSecondaryContainer,
    tertiary = c.tertiary, onTertiary = c.onTertiary,
    tertiaryContainer = c.tertiaryContainer, onTertiaryContainer = c.onTertiaryContainer,
    error = c.error, onError = c.onError,
    errorContainer = c.errorContainer, onErrorContainer = c.onErrorContainer,
    background = c.background, onBackground = c.onBackground,
    surface = c.surface, onSurface = c.onSurface,
    surfaceVariant = c.surfaceVariant, onSurfaceVariant = c.onSurfaceVariant,
    outline = c.outline, outlineVariant = c.outlineVariant,
)

private fun dark(
    c: ThemeColors,
): ColorScheme = darkColorScheme(
    primary = c.primary, onPrimary = c.onPrimary,
    primaryContainer = c.primaryContainer, onPrimaryContainer = c.onPrimaryContainer,
    secondary = c.secondary, onSecondary = c.onSecondary,
    secondaryContainer = c.secondaryContainer, onSecondaryContainer = c.onSecondaryContainer,
    tertiary = c.tertiary, onTertiary = c.onTertiary,
    tertiaryContainer = c.tertiaryContainer, onTertiaryContainer = c.onTertiaryContainer,
    error = c.error, onError = c.onError,
    errorContainer = c.errorContainer, onErrorContainer = c.onErrorContainer,
    background = c.background, onBackground = c.onBackground,
    surface = c.surface, onSurface = c.onSurface,
    surfaceVariant = c.surfaceVariant, onSurfaceVariant = c.onSurfaceVariant,
    outline = c.outline, outlineVariant = c.outlineVariant,
)

private data class ThemeColors(
    val primary: Color, val onPrimary: Color,
    val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val onSecondary: Color,
    val secondaryContainer: Color, val onSecondaryContainer: Color,
    val tertiary: Color, val onTertiary: Color,
    val tertiaryContainer: Color, val onTertiaryContainer: Color,
    val error: Color = Color(0xFFB3261E), val onError: Color = Color.White,
    val errorContainer: Color = Color(0xFFF9DEDC), val onErrorContainer: Color = Color(0xFF410E0B),
    val background: Color, val onBackground: Color,
    val surface: Color, val onSurface: Color,
    val surfaceVariant: Color, val onSurfaceVariant: Color,
    val outline: Color, val outlineVariant: Color,
)

private fun colorSchemeFor(theme: ReaderTheme): ColorScheme {
    val c = when (theme) {
        ReaderTheme.DEFAULT_WHITE -> ThemeColors(
            primary = DefaultWhiteColors.primary, onPrimary = DefaultWhiteColors.onPrimary,
            primaryContainer = DefaultWhiteColors.primaryContainer, onPrimaryContainer = DefaultWhiteColors.onPrimaryContainer,
            secondary = DefaultWhiteColors.secondary, onSecondary = DefaultWhiteColors.onSecondary,
            secondaryContainer = DefaultWhiteColors.secondaryContainer, onSecondaryContainer = DefaultWhiteColors.onSecondaryContainer,
            tertiary = DefaultWhiteColors.tertiary, onTertiary = DefaultWhiteColors.onTertiary,
            tertiaryContainer = DefaultWhiteColors.tertiaryContainer, onTertiaryContainer = DefaultWhiteColors.onTertiaryContainer,
            background = DefaultWhiteColors.background, onBackground = DefaultWhiteColors.onBackground,
            surface = DefaultWhiteColors.surface, onSurface = DefaultWhiteColors.onSurface,
            surfaceVariant = DefaultWhiteColors.surfaceVariant, onSurfaceVariant = DefaultWhiteColors.onSurfaceVariant,
            outline = DefaultWhiteColors.outline, outlineVariant = DefaultWhiteColors.outlineVariant,
        )
        ReaderTheme.SEPIA -> ThemeColors(
            primary = SepiaColors.primary, onPrimary = SepiaColors.onPrimary,
            primaryContainer = SepiaColors.primaryContainer, onPrimaryContainer = SepiaColors.onPrimaryContainer,
            secondary = SepiaColors.secondary, onSecondary = SepiaColors.onSecondary,
            secondaryContainer = SepiaColors.secondaryContainer, onSecondaryContainer = SepiaColors.onSecondaryContainer,
            tertiary = SepiaColors.tertiary, onTertiary = SepiaColors.onTertiary,
            tertiaryContainer = SepiaColors.tertiaryContainer, onTertiaryContainer = SepiaColors.onTertiaryContainer,
            background = SepiaColors.background, onBackground = SepiaColors.onBackground,
            surface = SepiaColors.surface, onSurface = SepiaColors.onSurface,
            surfaceVariant = SepiaColors.surfaceVariant, onSurfaceVariant = SepiaColors.onSurfaceVariant,
            outline = SepiaColors.outline, outlineVariant = SepiaColors.outlineVariant,
        )
        ReaderTheme.CHERRY_PINK -> ThemeColors(
            primary = CherryPinkColors.primary, onPrimary = CherryPinkColors.onPrimary,
            primaryContainer = CherryPinkColors.primaryContainer, onPrimaryContainer = CherryPinkColors.onPrimaryContainer,
            secondary = CherryPinkColors.secondary, onSecondary = CherryPinkColors.onSecondary,
            secondaryContainer = CherryPinkColors.secondaryContainer, onSecondaryContainer = CherryPinkColors.onSecondaryContainer,
            tertiary = CherryPinkColors.tertiary, onTertiary = CherryPinkColors.onTertiary,
            tertiaryContainer = CherryPinkColors.tertiaryContainer, onTertiaryContainer = CherryPinkColors.onTertiaryContainer,
            background = CherryPinkColors.background, onBackground = CherryPinkColors.onBackground,
            surface = CherryPinkColors.surface, onSurface = CherryPinkColors.onSurface,
            surfaceVariant = CherryPinkColors.surfaceVariant, onSurfaceVariant = CherryPinkColors.onSurfaceVariant,
            outline = CherryPinkColors.outline, outlineVariant = CherryPinkColors.outlineVariant,
        )
        ReaderTheme.MINT_GREEN -> ThemeColors(
            primary = MintGreenColors.primary, onPrimary = MintGreenColors.onPrimary,
            primaryContainer = MintGreenColors.primaryContainer, onPrimaryContainer = MintGreenColors.onPrimaryContainer,
            secondary = MintGreenColors.secondary, onSecondary = MintGreenColors.onSecondary,
            secondaryContainer = MintGreenColors.secondaryContainer, onSecondaryContainer = MintGreenColors.onSecondaryContainer,
            tertiary = MintGreenColors.tertiary, onTertiary = MintGreenColors.onTertiary,
            tertiaryContainer = MintGreenColors.tertiaryContainer, onTertiaryContainer = MintGreenColors.onTertiaryContainer,
            background = MintGreenColors.background, onBackground = MintGreenColors.onBackground,
            surface = MintGreenColors.surface, onSurface = MintGreenColors.onSurface,
            surfaceVariant = MintGreenColors.surfaceVariant, onSurfaceVariant = MintGreenColors.onSurfaceVariant,
            outline = MintGreenColors.outline, outlineVariant = MintGreenColors.outlineVariant,
        )
        ReaderTheme.SAPPHIRE_BLUE -> ThemeColors(
            primary = SapphireBlueColors.primary, onPrimary = SapphireBlueColors.onPrimary,
            primaryContainer = SapphireBlueColors.primaryContainer, onPrimaryContainer = SapphireBlueColors.onPrimaryContainer,
            secondary = SapphireBlueColors.secondary, onSecondary = SapphireBlueColors.onSecondary,
            secondaryContainer = SapphireBlueColors.secondaryContainer, onSecondaryContainer = SapphireBlueColors.onSecondaryContainer,
            tertiary = SapphireBlueColors.tertiary, onTertiary = SapphireBlueColors.onTertiary,
            tertiaryContainer = SapphireBlueColors.tertiaryContainer, onTertiaryContainer = SapphireBlueColors.onTertiaryContainer,
            background = SapphireBlueColors.background, onBackground = SapphireBlueColors.onBackground,
            surface = SapphireBlueColors.surface, onSurface = SapphireBlueColors.onSurface,
            surfaceVariant = SapphireBlueColors.surfaceVariant, onSurfaceVariant = SapphireBlueColors.onSurfaceVariant,
            outline = SapphireBlueColors.outline, outlineVariant = SapphireBlueColors.outlineVariant,
        )
        ReaderTheme.NIGHT_SAKURA -> ThemeColors(
            primary = NightSakuraColors.primary, onPrimary = NightSakuraColors.onPrimary,
            primaryContainer = NightSakuraColors.primaryContainer, onPrimaryContainer = NightSakuraColors.onPrimaryContainer,
            secondary = NightSakuraColors.secondary, onSecondary = NightSakuraColors.onSecondary,
            secondaryContainer = NightSakuraColors.secondaryContainer, onSecondaryContainer = NightSakuraColors.onSecondaryContainer,
            tertiary = NightSakuraColors.tertiary, onTertiary = NightSakuraColors.onTertiary,
            tertiaryContainer = NightSakuraColors.tertiaryContainer, onTertiaryContainer = NightSakuraColors.onTertiaryContainer,
            error = NightSakuraColors.error, onError = NightSakuraColors.onError,
            errorContainer = NightSakuraColors.errorContainer, onErrorContainer = NightSakuraColors.onErrorContainer,
            background = NightSakuraColors.background, onBackground = NightSakuraColors.onBackground,
            surface = NightSakuraColors.surface, onSurface = NightSakuraColors.onSurface,
            surfaceVariant = NightSakuraColors.surfaceVariant, onSurfaceVariant = NightSakuraColors.onSurfaceVariant,
            outline = NightSakuraColors.outline, outlineVariant = NightSakuraColors.outlineVariant,
        )
        ReaderTheme.DARK_GREEN -> ThemeColors(
            primary = DarkGreenColors.primary, onPrimary = DarkGreenColors.onPrimary,
            primaryContainer = DarkGreenColors.primaryContainer, onPrimaryContainer = DarkGreenColors.onPrimaryContainer,
            secondary = DarkGreenColors.secondary, onSecondary = DarkGreenColors.onSecondary,
            secondaryContainer = DarkGreenColors.secondaryContainer, onSecondaryContainer = DarkGreenColors.onSecondaryContainer,
            tertiary = DarkGreenColors.tertiary, onTertiary = DarkGreenColors.onTertiary,
            tertiaryContainer = DarkGreenColors.tertiaryContainer, onTertiaryContainer = DarkGreenColors.onTertiaryContainer,
            error = DarkGreenColors.error, onError = DarkGreenColors.onError,
            errorContainer = DarkGreenColors.errorContainer, onErrorContainer = DarkGreenColors.onErrorContainer,
            background = DarkGreenColors.background, onBackground = DarkGreenColors.onBackground,
            surface = DarkGreenColors.surface, onSurface = DarkGreenColors.onSurface,
            surfaceVariant = DarkGreenColors.surfaceVariant, onSurfaceVariant = DarkGreenColors.onSurfaceVariant,
            outline = DarkGreenColors.outline, outlineVariant = DarkGreenColors.outlineVariant,
        )
        ReaderTheme.DEEP_SEA -> ThemeColors(
            primary = DeepSeaColors.primary, onPrimary = DeepSeaColors.onPrimary,
            primaryContainer = DeepSeaColors.primaryContainer, onPrimaryContainer = DeepSeaColors.onPrimaryContainer,
            secondary = DeepSeaColors.secondary, onSecondary = DeepSeaColors.onSecondary,
            secondaryContainer = DeepSeaColors.secondaryContainer, onSecondaryContainer = DeepSeaColors.onSecondaryContainer,
            tertiary = DeepSeaColors.tertiary, onTertiary = DeepSeaColors.onTertiary,
            tertiaryContainer = DeepSeaColors.tertiaryContainer, onTertiaryContainer = DeepSeaColors.onTertiaryContainer,
            error = DeepSeaColors.error, onError = DeepSeaColors.onError,
            errorContainer = DeepSeaColors.errorContainer, onErrorContainer = DeepSeaColors.onErrorContainer,
            background = DeepSeaColors.background, onBackground = DeepSeaColors.onBackground,
            surface = DeepSeaColors.surface, onSurface = DeepSeaColors.onSurface,
            surfaceVariant = DeepSeaColors.surfaceVariant, onSurfaceVariant = DeepSeaColors.onSurfaceVariant,
            outline = DeepSeaColors.outline, outlineVariant = DeepSeaColors.outlineVariant,
        )
    }
    return if (theme.isDark) dark(c) else light(c)
}

@Composable
fun SimpleBookTheme(
    readerTheme: ReaderTheme = ReaderTheme.DEFAULT_WHITE,
    content: @Composable () -> Unit
) {
    val colorScheme = colorSchemeFor(readerTheme)
    val rippleColor = Color(readerTheme.accentColor).copy(alpha = 0.12f)

    CompositionLocalProvider(
        LocalRippleColor provides rippleColor,
        LocalReaderTheme provides readerTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
