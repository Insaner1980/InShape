package com.emmah.inshape.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.emmah.inshape.R

val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600)))
)

private fun style(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    lineHeight = height.sp,
    fontWeight = weight,
    letterSpacing = spacing.em,
    fontFeatureSettings = "tnum"
)

val AppTypography = Typography(
    displayLarge = style(56, 60, FontWeight.Medium, -.03f),
    displayMedium = style(40, 44, FontWeight.Medium, -.03f),
    displaySmall = style(40, 44, FontWeight.Medium, -.03f),
    headlineLarge = style(28, 34, FontWeight.SemiBold, -.02f),
    headlineMedium = style(28, 34, FontWeight.SemiBold, -.02f),
    headlineSmall = style(20, 26, FontWeight.SemiBold, -.01f),
    titleLarge = style(20, 26, FontWeight.SemiBold, -.01f),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    titleSmall = style(16, 22, FontWeight.SemiBold),
    bodyLarge = style(15, 22), bodyMedium = style(13, 18), bodySmall = style(13, 18),
    labelLarge = style(15, 20, FontWeight.SemiBold),
    labelMedium = style(14, 20, FontWeight.Medium),
    labelSmall = style(12, 16, FontWeight.Medium, .01f)
)

object InShapeType {
    val setValue = style(18, 24, FontWeight.SemiBold)
    val nav = style(12, 16)
    val navSelected = style(12, 16, FontWeight.SemiBold)
}
