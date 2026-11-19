package com.emmah.inshape.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class InShapeColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceStrong: Color,
    val divider: Color,
    val outline: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val ink: Color,
    val onInk: Color,
    val error: Color,
    val scrim: Color
) {
    val inactiveMark: Color get() = if (isDark) surfaceStrong else Color(0xFFE0E0E0)
}

val LightColors = InShapeColors(
    false, Color(0xFFFFFFFF), Color(0xFFF4F4F4), Color(0xFFEBEBEB),
    Color(0xFFE3E3E3), Color(0xFF0B0B0B), Color(0xFF0B0B0B),
    Color(0xFF5E5E5E), Color(0xFF707070), Color(0xFF0B0B0B),
    Color(0xFFFFFFFF), Color(0xFFB3261E), Color(0x520B0B0B)
)
val DarkColors = InShapeColors(
    true, Color(0xFF0B0B0B), Color(0xFF1A1A1A), Color(0xFF242424),
    Color(0xFF2A2A2A), Color(0xFFF5F5F5), Color(0xFFF5F5F5),
    Color(0xFFA3A3A3), Color(0xFF858585), Color(0xFFF5F5F5),
    Color(0xFF0B0B0B), Color(0xFFF2B8B5), Color(0x99000000)
)
