package com.emmah.inshape.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*

private fun loadLabel(basis: String) = when (basis) { "kg" -> "kg"; "assistance_kg" -> "Assistance kg"; "level" -> "Level"; else -> "Load" }

@Composable
fun SetTableHeader(basis: String) {
    BoxWithConstraints {
        if (maxWidth >= Dimens.setFallback && LocalDensity.current.fontScale < 1.3f) Row(horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
            Text("SET", Modifier.width(Dimens.setNumber), style = MaterialTheme.typography.labelSmall, color = InShapeTheme.colors.textSecondary)
            Text(loadLabel(basis).uppercase(java.util.Locale.ENGLISH), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = InShapeTheme.colors.textSecondary)
            Text("REPS", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = InShapeTheme.colors.textSecondary)
            Text("DONE", Modifier.width(Dimens.setField), style = MaterialTheme.typography.labelSmall, color = InShapeTheme.colors.textSecondary)
        }
    }
}

@Composable
fun SetRow(set: SessionSet, number: Int, basis: String, vm: JournalViewModel, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    var load by rememberSaveable(set.id) { mutableStateOf(set.rawLoad) }
    var reps by rememberSaveable(set.id) { mutableStateOf(set.rawReps) }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val complete = set.completedAt != null
    val unit = loadLabel(basis)
    val colors = InShapeTheme.colors
    @Composable fun Options() {
        Box {
            TextButton({ menu = true }, Modifier.size(Dimens.setNumber).semantics { contentDescription = "Options for set $number" }, contentPadding = PaddingValues(Dimens.innerTiny)) {
                Text(number.toString(), style = InShapeType.setNumber, color = if (complete) colors.textSecondary else colors.text)
            }
            DropdownMenu(menu, { menu = false }) {
                if (complete) DropdownMenuItem({ Text("Edit set $number") }, { menu = false; editing = true })
                DropdownMenuItem({ Text("Remove set $number") }, { menu = false; vm.write { removeSet(set.id) } })
            }
        }
    }
    @Composable fun Load(modifier: Modifier = Modifier, compact: Boolean = false) = NumberField(if (complete) set.load ?: "" else load, { load = it; vm.write { saveSetDraft(set.id, it, reps) } }, unit, modifier, decimal = true, readOnly = complete, compact = compact, highlighted = highlighted)
    @Composable fun Reps(modifier: Modifier = Modifier, compact: Boolean = false) = NumberField(if (complete) set.reps?.toString() ?: "" else reps, { reps = it; vm.write { saveSetDraft(set.id, load, it) } }, "Reps", modifier, readOnly = complete, compact = compact, highlighted = highlighted)
    @Composable fun Done() {
        IconToggleButton(complete, { vm.write { setSetCompleted(set.id, it) } }, Modifier.size(Dimens.setField).background(if (complete) colors.accent else Color.Transparent, InShapeShapes.pill)
            .then(if (complete) Modifier else Modifier.border(Dimens.ring, colors.muted, InShapeShapes.pill))) {
            Icon(Icons.Outlined.Check, if (complete) "Undo set $number" else "Complete set $number", tint = if (complete) colors.onAccent else colors.textSecondary)
        }
    }
    BoxWithConstraints(modifier) {
        if (maxWidth < Dimens.setFallback || LocalDensity.current.fontScale >= 1.3f) Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Options(); Text("Set $number, ${if (complete) "completed" else "not completed"}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Done() }
            Load(Modifier.fillMaxWidth()); Reps(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall), verticalAlignment = Alignment.CenterVertically) {
            Options(); Load(Modifier.weight(1f), true); Reps(Modifier.weight(1f), true); Done()
        }
    }
    if (editing) {
        var correctionLoad by remember { mutableStateOf(set.load ?: "") }
        var correctionReps by remember { mutableStateOf(set.reps?.toString() ?: "") }
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Correct set $number") }, text = { Column {
            NumberField(correctionLoad, { correctionLoad = it }, unit, decimal = true)
            NumberField(correctionReps, { correctionReps = it }, "Reps")
        } }, confirmButton = { TextAction("Update", { vm.write { correctCompletedSet(set.id, correctionLoad, correctionReps); load = correctionLoad; reps = correctionReps; editing = false } }) }, dismissButton = { TextAction("Cancel", { editing = false }) })
    }
}
