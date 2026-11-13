package com.emmah.inshape.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emmah.inshape.ui.theme.*

@Composable
private fun fieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = InShapeTheme.colors.surfaceInset, unfocusedContainerColor = InShapeTheme.colors.surfaceInset,
    disabledContainerColor = InShapeTheme.colors.surfaceInset, focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
    focusedLabelColor = InShapeTheme.colors.textSecondary, unfocusedLabelColor = InShapeTheme.colors.textSecondary,
    cursorColor = InShapeTheme.colors.mark,
)

@Composable
fun TextField(value: String, label: String, onChange: (String) -> Unit, singleLine: Boolean = false, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    androidx.compose.material3.TextField(value, onChange, modifier.fillMaxWidth().then(if (focused) Modifier.border(2.dp, InShapeTheme.colors.mark, InShapeShapes.inset) else Modifier),
        label = { Text(label) }, shape = InShapeShapes.inset, singleLine = singleLine, colors = fieldColors(), interactionSource = interaction)
}

@Composable
fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, decimal: Boolean = false, enabled: Boolean = true, readOnly: Boolean = false, compact: Boolean = false, highlighted: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border = if (focused || highlighted) Modifier.border(2.dp, InShapeTheme.colors.mark, InShapeShapes.inset) else Modifier
    val keyboard = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number)
    if (compact) Surface(modifier.heightIn(min = 52.dp).then(border), color = InShapeTheme.colors.surfaceInset, shape = InShapeShapes.inset) {
        Box(Modifier.padding(Dimens.innerSmall), contentAlignment = Alignment.Center) {
            BasicTextField(value, onValueChange, Modifier.fillMaxWidth().semantics { contentDescription = label }, enabled = enabled, readOnly = readOnly, singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = InShapeTheme.colors.text, textAlign = TextAlign.Center), keyboardOptions = keyboard, interactionSource = interaction, cursorBrush = SolidColor(InShapeTheme.colors.mark))
        }
    } else androidx.compose.material3.TextField(value, onValueChange, modifier.heightIn(min = 56.dp).then(border), label = { Text(label) }, singleLine = true, enabled = enabled, readOnly = readOnly,
        textStyle = MaterialTheme.typography.titleLarge, colors = fieldColors(), shape = InShapeShapes.inset, keyboardOptions = keyboard, interactionSource = interaction)
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Surface(modifier.heightIn(min = Dimens.touch).cardElevation(InShapeShapes.inset), color = InShapeTheme.colors.surface, shape = InShapeShapes.inset) {
        Row(Modifier.padding(Dimens.itemGap), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
            Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = InShapeTheme.colors.textSecondary)
            BasicTextField(value, onChange, Modifier.weight(1f).semantics { contentDescription = placeholder }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = InShapeTheme.colors.text), cursorBrush = SolidColor(InShapeTheme.colors.mark), decorationBox = { field ->
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = InShapeTheme.colors.textSecondary)
                    field()
                })
        }
    }
}
