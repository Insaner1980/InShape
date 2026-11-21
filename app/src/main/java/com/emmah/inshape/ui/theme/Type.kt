package com.emmah.inshape.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontWeight as WidgetFontWeight
import androidx.glance.text.TextStyle as WidgetTextStyle
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
    letterSpacing = spacing.em
)

private val compactHero = style(40, 44, FontWeight.Medium, -.03f).copy(fontFeatureSettings = "tnum")
private val pageTitle = style(28, 34, FontWeight.SemiBold, -.02f)
private val sectionTitle = style(20, 26, FontWeight.SemiBold, -.01f)
private val numericTitle = sectionTitle.copy(fontFeatureSettings = "tnum")
private val body = style(16, 22)
private val emphasizedBody = body.copy(fontWeight = FontWeight.SemiBold)
private val secondaryBody = style(13, 18)
private val caption = secondaryBody.copy(fontWeight = FontWeight.Medium)

val AppTypography = Typography(
    displayLarge = style(56, 60, FontWeight.Medium, -.03f).copy(fontFeatureSettings = "tnum"),
    displayMedium = compactHero,
    displaySmall = compactHero,
    headlineLarge = pageTitle,
    headlineMedium = pageTitle,
    headlineSmall = sectionTitle,
    titleLarge = sectionTitle,
    titleMedium = emphasizedBody,
    titleSmall = emphasizedBody,
    bodyLarge = body,
    bodyMedium = secondaryBody,
    bodySmall = secondaryBody,
    labelLarge = emphasizedBody,
    labelMedium = caption,
    labelSmall = caption
)

object InShapeType {
    val todayTitle = style(27, 34, FontWeight.SemiBold, -.02f)
    val workoutClock = numericTitle
    val workoutClockCompact = caption.copy(fontFeatureSettings = "tnum")
    val setValue = numericTitle
    val numeric = body.copy(fontFeatureSettings = "tnum")
    val nav = style(12, 16)
    val navSelected = nav.copy(fontWeight = FontWeight.SemiBold)
}

internal object WidgetTypography {
    val title = WidgetTextStyle(
        color = ColorProvider(LightColors.text, DarkColors.text),
        fontSize = AppTypography.titleMedium.fontSize,
        fontWeight = WidgetFontWeight.Medium
    )
    val label = WidgetTextStyle(
        color = ColorProvider(LightColors.textSecondary, DarkColors.textSecondary),
        fontSize = AppTypography.labelSmall.fontSize
    )
}
