package com.emmah.inshape.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

object InShapeShapes {
    val card = RoundedCornerShape(24.dp)
    val inset = RoundedCornerShape(14.dp)
    val tile = RoundedCornerShape(12.dp)
    val pill = CircleShape
}
val AppShapes = Shapes(small = InShapeShapes.tile, medium = InShapeShapes.inset, large = InShapeShapes.card, extraLarge = InShapeShapes.card)

@Composable
fun Modifier.cardElevation(shape: Shape = InShapeShapes.card): Modifier =
    if (InShapeTheme.colors.isDark) this else
        dropShadow(shape, Shadow(2.dp, Color(0xFF12161B).copy(alpha = .05f), offset = DpOffset(0.dp, 1.dp)))
            .dropShadow(shape, Shadow(24.dp, Color(0xFF12161B).copy(alpha = .07f), offset = DpOffset(0.dp, 8.dp)))
