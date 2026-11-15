package com.emmah.inshape.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme

@Composable
private fun fieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = InShapeTheme.colors.surface, unfocusedContainerColor = InShapeTheme.colors.surface,
    disabledContainerColor = InShapeTheme.colors.surface, focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
    focusedLabelColor = InShapeTheme.colors.textSecondary, unfocusedLabelColor = InShapeTheme.colors.textSecondary,
    cursorColor = InShapeTheme.colors.ink
)

@Composable
fun TextField(value: String, label: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    androidx.compose.material3.TextField(
        value,
        onChange,
        modifier.fillMaxWidth().then(
            if (focused) {
                Modifier.border(
                    Dimens.chartOutline,
                    InShapeTheme.colors.ink,
                    InShapeShapes.field
                )
            } else {
                Modifier
            }
        ),
        label = {
            Text(label)
        },
        shape = InShapeShapes.field,
        singleLine = singleLine,
        colors = fieldColors(),
        interactionSource = interaction
    )
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    compact: Boolean = false,
    highlighted: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border = if (focused ||
        highlighted
    ) {
        Modifier.border(Dimens.chartOutline, InShapeTheme.colors.ink, InShapeShapes.field)
    } else {
        Modifier
    }
    val keyboard =
        KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number)
    if (compact) {
        Surface(
            modifier.heightIn(min = Dimens.setField).then(border),
            color = if (highlighted) InShapeTheme.colors.background else InShapeTheme.colors.surface,
            shape = InShapeShapes.field
        ) {
            Box(Modifier.padding(Dimens.innerSmall), contentAlignment = Alignment.Center) {
                BasicTextField(
                    value, onValueChange,
                    Modifier.fillMaxWidth().semantics {
                        contentDescription =
                            label
                    },
                    enabled = enabled, readOnly = readOnly, singleLine = true,
                    textStyle = com.emmah.inshape.ui.theme.InShapeType.setValue.copy(
                        color = InShapeTheme.colors.text,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = keyboard, interactionSource = interaction,
                    cursorBrush = SolidColor(
                        InShapeTheme.colors.ink
                    )
                )
            }
        }
    } else {
        androidx.compose.material3.TextField(
            value, onValueChange, modifier.heightIn(min = Dimens.button).then(border), label = {
                Text(label)
            }, singleLine = true, enabled = enabled, readOnly = readOnly,
            textStyle = MaterialTheme.typography.titleLarge,
            colors = fieldColors(),
            shape = InShapeShapes.field,
            keyboardOptions = keyboard,
            interactionSource = interaction
        )
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Surface(
        modifier.heightIn(min = Dimens.touch),
        color = InShapeTheme.colors.surface,
        shape = InShapeShapes.field
    ) {
        Row(
            Modifier.padding(Dimens.itemGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
        ) {
            Icon(
                Icons.Outlined.Search,
                null,
                Modifier.size(Dimens.buttonIcon),
                tint = InShapeTheme.colors.textSecondary
            )
            BasicTextField(
                value,
                onChange,
                Modifier.weight(1f).semantics {
                    contentDescription =
                        placeholder
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = InShapeTheme.colors.text
                ),
                cursorBrush = SolidColor(InShapeTheme.colors.ink),
                decorationBox = { field ->
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = InShapeTheme.colors.textSecondary
                        )
                    }
                    field()
                }
            )
        }
    }
}
