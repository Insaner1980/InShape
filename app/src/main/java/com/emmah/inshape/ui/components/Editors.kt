package com.emmah.inshape.ui.components

import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme

@Composable
fun SelectField(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    inline: Boolean = false,
    optionLabel: (String) -> String = { it }
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier) {
        if (!inline) GroupLabel(label, Modifier.padding(bottom = Dimens.innerSmall))
        Surface(
            onClick = {
                open = true
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.setField).semantics {
                contentDescription =
                    "$label, ${optionLabel(selected)}"
            },
            shape = InShapeShapes.field,
            color = InShapeTheme.colors.background,
            border = fieldBorder(focused),
            interactionSource = interaction
        ) {
            Row(
                Modifier.padding(
                    horizontal = Dimens.inner,
                    vertical = Dimens.innerSmall
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    optionLabel(selected),
                    Modifier.weight(1f),
                    style = if (inline) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
                )
                Icon(Icons.Outlined.KeyboardArrowDown, null)
            }
        }
    }
    if (open) {
        SelectionSheet(label, options, selected, optionLabel, onSelect) { open = false }
    }
}

@Composable
internal fun SelectionSheet(
    label: String,
    options: List<String>,
    selected: String? = null,
    optionLabel: (String) -> String = { it },
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    EditorSheet(label, onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().selectableGroup()) {
            items(options) { option ->
                ListRow(
                    optionLabel(option),
                    Modifier.selectable(option == selected) {
                        onSelect(option)
                        onDismiss()
                    },
                    trailing = if (option == selected) ({ Icon(Icons.Rounded.Check, null) }) else null,
                    showChevron = false
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorSheet(
    title: String,
    onDismiss: () -> Unit,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = InShapeTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = colors.background,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.divider) }
    ) {
        Column(
            Modifier.fillMaxWidth().then(contentModifier).imePadding()
                .padding(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = Dimens.inner),
            verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
fun ActionRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny),
        content = content
    )
}

@Composable
fun DialogAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false, enabled: Boolean = true) {
    val colors = InShapeTheme.colors
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (destructive) colors.error else colors.text,
            disabledContentColor = colors.textTertiary
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, destructive: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (!destructive) {
        EditorSheet(title, onDismiss) {
            Text(message)
            ActionRow(Modifier.align(Alignment.End)) {
                DialogAction(stringResource(R.string.cancel), onDismiss)
                DialogAction(confirm, onConfirm)
            }
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { DialogAction(confirm, onConfirm, destructive = destructive) },
        dismissButton = { DialogAction(stringResource(R.string.cancel), onDismiss) }
    )
}
