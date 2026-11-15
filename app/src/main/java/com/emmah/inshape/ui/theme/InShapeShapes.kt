package com.emmah.inshape.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object InShapeShapes {
    val chip = RoundedCornerShape(8.dp)
    val field = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(14.dp)
    val card = RoundedCornerShape(20.dp)
    val segment = RoundedCornerShape(9.dp)
    val pill = CircleShape
}
val AppShapes = Shapes(
    small = InShapeShapes.chip,
    medium = InShapeShapes.field,
    large = InShapeShapes.card,
    extraLarge = InShapeShapes.card
)
