package com.emmah.inshape.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emmah.inshape.ui.theme.*

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?) {
    if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(Dimens.innerSmall)) }
    Text(text)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, trailingIcon: ImageVector? = null) {
    Button(onClick, modifier.heightIn(min = 52.dp), enabled, shape = InShapeShapes.pill, elevation = null,
        colors = ButtonDefaults.buttonColors(containerColor = InShapeTheme.colors.accent, contentColor = InShapeTheme.colors.onAccent, disabledContainerColor = InShapeTheme.colors.muted, disabledContentColor = InShapeTheme.colors.textSecondary)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) { ButtonLabel(text, icon) }
        if (trailingIcon != null) { Spacer(Modifier.width(Dimens.innerSmall)); Icon(trailingIcon, null, Modifier.size(18.dp)) }
    }
}

@Composable
fun HeroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 52.dp), shape = InShapeShapes.pill, elevation = null,
        colors = ButtonDefaults.buttonColors(containerColor = InShapeTheme.colors.onAccent, contentColor = InShapeTheme.colors.onAccentInverse)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) { ButtonLabel(text, icon) }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick, modifier.heightIn(min = Dimens.touch), enabled, shape = InShapeShapes.pill, elevation = null,
        colors = ButtonDefaults.buttonColors(containerColor = InShapeTheme.colors.surfaceInset, contentColor = InShapeTheme.colors.text)) {
        ProvideTextStyle(MaterialTheme.typography.labelMedium) { ButtonLabel(text, icon) }
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick, modifier.heightIn(min = Dimens.touch), enabled, shape = InShapeShapes.pill, border = BorderStroke(1.5.dp, InShapeTheme.colors.muted), colors = ButtonDefaults.outlinedButtonColors(contentColor = InShapeTheme.colors.text)) { Text(text, style = MaterialTheme.typography.labelMedium) }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = InShapeTheme.colors.onAccentContainer, icon: ImageVector? = null, enabled: Boolean = true) {
    TextButton(onClick, modifier.heightIn(min = Dimens.touch), enabled, colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        ProvideTextStyle(MaterialTheme.typography.labelMedium) { ButtonLabel(text, icon) }
    }
}

@Composable
fun IconCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false) {
    Box(modifier.size(Dimens.touch), contentAlignment = Alignment.Center) {
        Surface(onClick, Modifier.size(44.dp).then(if (accent) Modifier else Modifier.cardElevation(InShapeShapes.pill)), shape = InShapeShapes.pill, color = if (accent) InShapeTheme.colors.accent else InShapeTheme.colors.surface) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription, Modifier.size(22.dp), tint = if (accent) InShapeTheme.colors.onAccent else InShapeTheme.colors.text) }
        }
    }
}
