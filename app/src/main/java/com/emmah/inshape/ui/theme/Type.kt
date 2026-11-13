package com.emmah.inshape.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.emmah.inshape.R

val Display = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.space_grotesk, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.space_grotesk, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
private fun style(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, display: Boolean = false, spacing: Float = 0f) =
    TextStyle(fontFamily = if (display) Display else FontFamily.Default, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight, letterSpacing = spacing.em)

val AppTypography = Typography(
    displaySmall = style(36, 40, FontWeight.Bold, true, -.02f),
    headlineLarge = style(32, 38, FontWeight.Bold, true, -.02f),
    headlineMedium = style(28, 32, FontWeight.Bold, true, -.02f),
    headlineSmall = style(24, 28, FontWeight.Bold, true),
    titleLarge = style(20, 24, FontWeight.SemiBold, true),
    titleMedium = style(17, 22, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 23), bodyMedium = style(14, 20), bodySmall = style(13, 18),
    labelLarge = style(16, 22, FontWeight.SemiBold),
    labelMedium = style(14, 20, FontWeight.SemiBold),
    labelSmall = style(12, 16, FontWeight.Bold, spacing = .06f),
)
object InShapeType {
    val rowTitle: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val settingsRow: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
    val date = style(13, 18, FontWeight.SemiBold, spacing = .04f)
    val nav = style(12, 16)
    val navSelected = style(12, 16, FontWeight.Bold)
    val compactDisplay = style(16, 22, FontWeight.Bold, true)
    val setNumber = style(18, 24, FontWeight.Bold, true)
    val pill = style(13, 18, FontWeight.SemiBold)
    val tileCaption = style(11, 14)
}
