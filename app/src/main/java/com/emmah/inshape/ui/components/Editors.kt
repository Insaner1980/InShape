package com.emmah.inshape.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.inshape.SaveState
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
            color = if (inline) androidx.compose.ui.graphics.Color.Transparent else InShapeTheme.colors.surface
        ) {
            Row(
                Modifier.padding(
                    horizontal = if (inline) Dimens.zero else Dimens.inner,
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
        AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
            LazyColumn {
                items(options) { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Dimens.touch).clickable {
                            onSelect(option)
                            open =
                                false
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(option == selected, null)
                        Text(optionLabel(option), Modifier.padding(Dimens.itemGap))
                    }
                }
            }
        }, confirmButton = { TextAction("Close", { open = false }) })
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
fun SaveStatus(state: SaveState, retry: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        SaveState.Idle -> Unit

        SaveState.Saving -> Text("Saving...", modifier, style = MaterialTheme.typography.bodyMedium)

        SaveState.Saved -> Text("Saved automatically", modifier, style = MaterialTheme.typography.bodyMedium)

        is SaveState.Failed -> InShapeCard(modifier, inset = true) {
            Text("Could not save", color = InShapeTheme.colors.error)
            Text(state.message)
            if (state.retryable) {
                OutlineButton(
                    "Retry",
                    retry
                )
            } else {
                Text("Review your values and save again.")
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val destructive = listOf("delete", "discard", "remove", "replace", "archive").any {
        confirm.startsWith(it, true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextAction(
                confirm,
                onConfirm,
                color = if (destructive) InShapeTheme.colors.error else InShapeTheme.colors.text
            )
        },
        dismissButton = { TextAction("Cancel", onDismiss) }
    )
}
