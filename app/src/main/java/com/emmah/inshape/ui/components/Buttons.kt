package com.emmah.inshape.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme

@Composable
private fun RowScope.ButtonLabel(text: String, icon: ImageVector?, textAlign: TextAlign = TextAlign.Center) {
    if (icon !=
        null
    ) {
        Icon(icon, null, Modifier.size(Dimens.buttonIcon))
        Spacer(Modifier.width(Dimens.innerSmall))
    }
    Text(text, textAlign = textAlign)
}

@Composable
fun HeaderIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryAdd: Boolean = false,
    enabled: Boolean = true
) {
    if (primaryAdd) {
        IconCircleButton(icon, label, onClick, modifier, enabled = enabled)
    } else {
        IconButton(onClick, modifier.size(Dimens.touch), enabled = enabled) {
            Icon(icon, label, Modifier.size(Dimens.icon))
        }
    }
}

enum class ButtonSize { Medium, Large }

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
    inverted: Boolean = false,
    size: ButtonSize = ButtonSize.Medium
) {
    Button(
        onClick,
        modifier.heightIn(min = if (size == ButtonSize.Large) Dimens.button else Dimens.touch),
        enabled,
        shape = InShapeShapes.button,
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (inverted) InShapeTheme.colors.onInk else InShapeTheme.colors.ink,
            contentColor = if (inverted) InShapeTheme.colors.ink else InShapeTheme.colors.onInk,
            disabledContainerColor = InShapeTheme.colors.surface,
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
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    size: ButtonSize = ButtonSize.Medium
) {
    OutlinedButton(
        onClick,
        modifier.heightIn(min = if (size == ButtonSize.Large) Dimens.button else Dimens.touch),
        enabled,
        shape = InShapeShapes.button,
        border = BorderStroke(Dimens.line, if (enabled) InShapeTheme.colors.outline else InShapeTheme.colors.divider),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = InShapeTheme.colors.text,
            disabledContentColor = InShapeTheme.colors.textTertiary
        )
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
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = Dimens.zero, vertical = Dimens.innerSmall)
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)) {
            ButtonLabel(text, icon, TextAlign.Start)
        }
    }
}

@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true
) {
    Box(modifier.size(Dimens.touch), contentAlignment = Alignment.Center) {
        Surface(
            onClick,
            Modifier.size(
                Dimens.check
            ),
            shape = InShapeShapes.pill,
            enabled = enabled,
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
