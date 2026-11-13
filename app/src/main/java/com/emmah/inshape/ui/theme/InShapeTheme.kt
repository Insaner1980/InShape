package com.emmah.inshape.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LocalInShapeColors = androidx.compose.runtime.staticCompositionLocalOf<InShapeColors> { error("No InShapeColors") }

object InShapeTheme {
    val colors: InShapeColors
        @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalInShapeColors.current
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
        primary = colors.accent, onPrimary = colors.onAccent,
        primaryContainer = colors.accentContainer, onPrimaryContainer = colors.onAccentContainer,
        secondary = colors.textSecondary, onSecondary = colors.background,
        secondaryContainer = colors.accentContainer, onSecondaryContainer = colors.onAccentContainer,
        tertiary = colors.onAccentContainer, background = colors.background, onBackground = colors.text,
        surface = colors.surface, onSurface = colors.text, surfaceVariant = colors.surfaceInset,
        onSurfaceVariant = colors.textSecondary, surfaceContainerLowest = colors.surface,
        surfaceContainerLow = colors.surface, surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surface, surfaceContainerHighest = colors.surfaceInset,
        outline = colors.muted, outlineVariant = colors.divider, error = colors.error,
        surfaceTint = Color.Transparent,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalInShapeColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
