package com.emmah.inshape.ui.components

import com.emmah.inshape.ui.theme.decimalDisplay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField as MaterialTextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme

@Composable
private fun fieldColors(container: Color): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = container,
    unfocusedContainerColor = container,
    disabledContainerColor = container,
    errorContainerColor = container,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    focusedLabelColor = InShapeTheme.colors.textSecondary,
    unfocusedLabelColor = InShapeTheme.colors.textSecondary,
    cursorColor = InShapeTheme.colors.ink
)

@Composable
internal fun fieldBorder(focused: Boolean, isError: Boolean = false): BorderStroke {
    val colors = InShapeTheme.colors
    return BorderStroke(
        if (focused) Dimens.ring else Dimens.line,
        when {
            isError -> colors.error
            focused -> colors.ink
            else -> colors.fieldBorder
        }
    )
}

@Composable
fun TextField(
    value: String,
    label: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    error: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    accessibilityLabel: String? = null,
    focusRequester: FocusRequester? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val colors = InShapeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
        MaterialTextField(
            value,
            onChange,
            Modifier.fillMaxWidth()
                .then(focusModifier)
                .border(fieldBorder(focused, isError = error != null), InShapeShapes.field)
                .semantics {
                    if (accessibilityLabel != null) contentDescription = accessibilityLabel
                    if (error != null) error(error)
                },
            label = label.takeUnless(String::isBlank)?.let { title -> { Text(title, style = MaterialTheme.typography.labelSmall) } },
            enabled = enabled,
            placeholder = placeholder?.let { hint -> { Text(hint) } },
            shape = InShapeShapes.field,
            singleLine = singleLine,
            isError = error != null,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            colors = fieldColors(colors.background),
            interactionSource = interaction
        )
        if (error != null) {
            Text(error, Modifier.padding(horizontal = Dimens.inner), style = MaterialTheme.typography.labelSmall, color = colors.error)
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    readOnly: Boolean = false,
    compact: Boolean = false,
    highlighted: Boolean = false,
    setTable: Boolean = false
) {
    val colors = InShapeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused && !readOnly) bringIntoView.bringIntoView()
    }
    val fieldModifier = modifier.bringIntoViewRequester(bringIntoView)
    val active = focused || highlighted
    val border = if (compact || setTable) {
        if (active) Modifier.border(Dimens.chartOutline, colors.ink, InShapeShapes.field) else Modifier
    } else {
        Modifier.border(fieldBorder(active), InShapeShapes.field)
    }
    val displayedValue = if (decimal) value.decimalDisplay() else value
    val keyboard =
        KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number)
    if (compact) {
        Surface(
            fieldModifier.heightIn(min = Dimens.setField).then(border),
            color = if (highlighted) colors.background else colors.surface,
            shape = InShapeShapes.field
        ) {
            Box(Modifier.padding(Dimens.innerSmall), contentAlignment = Alignment.Center) {
                BasicTextField(
                    displayedValue, onValueChange,
                    Modifier.fillMaxWidth().semantics {
                        contentDescription =
                            label
                    },
                    readOnly = readOnly, singleLine = true,
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
        MaterialTextField(
            displayedValue, onValueChange, fieldModifier.heightIn(min = Dimens.button).then(border),
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            singleLine = true, readOnly = readOnly,
            textStyle = MaterialTheme.typography.titleLarge,
            colors = fieldColors(if (setTable) colors.surface else colors.background),
            shape = InShapeShapes.field,
            keyboardOptions = keyboard,
            interactionSource = interaction
        )
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        modifier.heightIn(min = Dimens.touch),
        color = InShapeTheme.colors.background,
        border = fieldBorder(focused),
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
                enabled = enabled,
                singleLine = true,
                interactionSource = interaction,
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
