package com.emmah.inshape.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class InShapeColors(
    val isDark: Boolean, val background: Color, val surface: Color, val surfaceInset: Color,
    val segmentTrack: Color, val selected: Color, val muted: Color, val divider: Color,
    val text: Color, val textSecondary: Color, val accentContainer: Color,
    val onAccentContainer: Color, val mark: Color, val pain: Color, val stiffness: Color,
    val error: Color,
    val accent: Color = Color(0xFFC5F25A), val onAccent: Color = Color(0xFF141A05),
    val onAccentMuted: Color = Color(0xFF35440F), val onAccentInverse: Color = Color(0xFFD4F77F),
)

val DarkColors = InShapeColors(
    true, Color(0xFF0E1114), Color(0xFF171B20), Color(0xFF20262D), Color(0xFF20262D),
    Color(0xFF39424D), Color(0xFF39424D), Color(0xFF2A313A), Color(0xFFF2F4F5),
    Color(0xFFA3ADB8), Color(0xFF26330F), Color(0xFFD4F77F), Color(0xFFC5F25A),
    Color(0xFFFF9A62), Color(0xFF7FB2FF), Color(0xFFFF8A80),
)
val LightColors = InShapeColors(
    false, Color(0xFFF3F4F6), Color.White, Color(0xFFECEEF1), Color(0xFFE4E7EB),
    Color.White, Color(0xFFD5DAE0), Color(0xFFE4E7EB), Color(0xFF12161B),
    Color(0xFF5A6470), Color(0xFFE9F8C2), Color(0xFF3F6200), Color(0xFF5E8C00),
    Color(0xFFB8490B), Color(0xFF2459B0), Color(0xFFB3261E),
)
