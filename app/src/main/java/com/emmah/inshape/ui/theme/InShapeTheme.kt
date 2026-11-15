package com.emmah.inshape.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LocalInShapeColors = androidx.compose.runtime.staticCompositionLocalOf<InShapeColors> {
    error("No InShapeColors")
}

object InShapeTheme {
    val colors: InShapeColors
        @Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalInShapeColors.current
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun InShapeTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val colors = if (mode.isDark()) DarkColors else LightColors
    val base = if (colors.isDark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = colors.ink, onPrimary = colors.onInk,
        primaryContainer = colors.surface, onPrimaryContainer = colors.text,
        secondary = colors.textSecondary, onSecondary = colors.background,
        secondaryContainer = colors.surface, onSecondaryContainer = colors.text,
        tertiary = colors.text, onTertiary = colors.background,
        tertiaryContainer = colors.surface, onTertiaryContainer = colors.text,
        background = colors.background, onBackground = colors.text,
        surface = colors.background, onSurface = colors.text, surfaceVariant = colors.surface,
        onSurfaceVariant = colors.textSecondary, surfaceContainerLowest = colors.background,
        surfaceContainerLow = colors.background, surfaceContainer = colors.background,
        surfaceContainerHigh = colors.background, surfaceContainerHighest = colors.surface,
        outline = colors.outline, outlineVariant = colors.divider, error = colors.error,
        onError = colors.background, errorContainer = colors.surface, onErrorContainer = colors.error,
        inverseSurface = colors.ink, inverseOnSurface = colors.onInk, inversePrimary = colors.onInk,
        surfaceTint = Color.Transparent, scrim = colors.scrim
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalInShapeColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
