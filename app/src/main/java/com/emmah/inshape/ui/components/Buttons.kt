package com.emmah.inshape.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme

@Composable
private fun RowScope.ButtonLabel(text: String, icon: ImageVector?) {
    if (icon !=
        null
    ) {
        Icon(icon, null, Modifier.size(Dimens.buttonIcon))
        Spacer(Modifier.width(Dimens.innerSmall))
    }
    Text(text)
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
    inverted: Boolean = false
) {
    Button(
        onClick,
        modifier.heightIn(min = Dimens.button),
        enabled,
        shape = InShapeShapes.button,
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (inverted) InShapeTheme.colors.onInk else InShapeTheme.colors.ink,
            contentColor = if (inverted) InShapeTheme.colors.ink else InShapeTheme.colors.onInk,
            disabledContainerColor = InShapeTheme.colors.surfaceStrong,
            disabledContentColor = InShapeTheme.colors.textTertiary
        )
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) {
            ButtonLabel(text, icon)
        }
        if (trailingIcon !=
            null
        ) {
            Spacer(Modifier.width(Dimens.innerSmall))
            Icon(trailingIcon, null, Modifier.size(Dimens.buttonIcon))
        }
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick,
        modifier.heightIn(min = Dimens.touch),
        enabled,
        shape = InShapeShapes.button,
        border = BorderStroke(Dimens.line, InShapeTheme.colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = InShapeTheme.colors.text)
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) { ButtonLabel(text, icon) }
    }
}

@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = InShapeTheme.colors.text,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    TextButton(
        onClick,
        modifier.heightIn(min = Dimens.touch),
        enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = color)
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) { ButtonLabel(text, icon) }
    }
}

@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Box(modifier.size(Dimens.touch), contentAlignment = Alignment.Center) {
        Surface(
            onClick,
            Modifier.size(
                Dimens.check
            ),
            shape = InShapeShapes.pill,
            color = if (accent) InShapeTheme.colors.ink else InShapeTheme.colors.surface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription,
                    Modifier.size(Dimens.icon),
                    tint = if (accent) InShapeTheme.colors.onInk else InShapeTheme.colors.text
                )
            }
        }
    }
}
