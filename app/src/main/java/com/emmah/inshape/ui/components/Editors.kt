package com.emmah.inshape.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.emmah.inshape.SaveState
import com.emmah.inshape.ui.theme.*

@Composable
fun SelectField(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier, inline: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (!inline) GroupLabel(label, Modifier.padding(bottom = Dimens.innerSmall))
        Surface(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = "$label, $selected" }, shape = InShapeShapes.inset,
            color = if (inline) androidx.compose.ui.graphics.Color.Transparent else InShapeTheme.colors.surfaceInset) {
            Row(Modifier.padding(horizontal = if (inline) 0.dp else Dimens.inner, vertical = Dimens.innerSmall), verticalAlignment = Alignment.CenterVertically) {
                Text(selected, Modifier.weight(1f), style = if (inline) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                Icon(Icons.Outlined.KeyboardArrowDown, null)
            }
        }
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
        LazyColumn { items(options) { option -> Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touch).clickable { onSelect(option); open = false }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(option == selected, null); Text(option, Modifier.padding(Dimens.itemGap))
        } } }
    }, confirmButton = { TextAction("Close", { open = false }) })
}

@Composable
fun ActionRow(content: @Composable FlowRowScope.() -> Unit) { FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall), verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny), content = content) }

@Composable
fun SaveStatus(state: SaveState, retry: () -> Unit) {
    when (state) {
        SaveState.Idle -> Unit
        SaveState.Saving -> Text("Saving…", style = MaterialTheme.typography.bodyMedium)
        SaveState.Saved -> Text("Saved automatically", style = MaterialTheme.typography.bodyMedium)
        is SaveState.Failed -> InShapeCard { Text("Could not save", color = InShapeTheme.colors.error); Text(state.message); if (state.retryable) SecondaryButton("Retry", retry) else Text("Review your values and save again.") }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val destructive = listOf("delete", "discard", "remove", "replace", "archive").any { confirm.startsWith(it, true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextAction(confirm, onConfirm, color = if (destructive) InShapeTheme.colors.error else InShapeTheme.colors.onAccentContainer) }, dismissButton = { TextAction("Cancel", onDismiss) })
}
