package com.emmah.inshape.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.DialogAction
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType

@Composable
fun SetTableHeader(basis: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= Dimens.setFallback &&
            LocalDensity.current.fontScale < 1.3f
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                Text(
                    "Set",
                    Modifier.width(Dimens.setNumber),
                    style = MaterialTheme.typography.labelSmall,
                    color = InShapeTheme.colors.textSecondary
                )
                Text(
                    MeasurementBasis.fromKey(basis).columnLabel,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = InShapeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Reps",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = InShapeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Done",
                    Modifier.width(Dimens.setField),
                    style = MaterialTheme.typography.labelSmall,
                    color = InShapeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SetRow(
    set: SessionSet,
    number: Int,
    basis: String,
    vm: JournalViewModel,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onRemove: () -> Unit = {}
) {
    var load by rememberSaveable(set.id) { mutableStateOf(set.rawLoad) }
    var reps by rememberSaveable(set.id) { mutableStateOf(set.rawReps) }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val complete = set.completedAt != null
    val unit = MeasurementBasis.fromKey(basis).columnLabel
    val colors = InShapeTheme.colors

    @Composable fun Options() {
        Box {
            TextButton(
                { menu = true },
                Modifier.width(Dimens.setNumber).heightIn(min = Dimens.touch).semantics {
                    contentDescription =
                        "Options for set $number"
                },
                contentPadding = PaddingValues(Dimens.innerTiny)
            ) {
                Text(
                    number.toString(),
                    style = InShapeType.setValue,
                    color = if (complete) colors.textSecondary else colors.text
                )
            }
            AppMenu(menu, { menu = false }) {
                MenuAction("Remove set $number", Icons.Outlined.Delete, {
                    menu = false
                    vm.write {
                        removeSet(set.id)
                        vm.container.markSetRemoved()
                        onRemove()
                    }
                }, destructive = true)
            }
        }
    }

    @Composable fun SetValue(
        label: String,
        completedValue: String,
        draftValue: String,
        onChange: (String) -> Unit,
        modifier: Modifier = Modifier,
        decimal: Boolean = false,
        compact: Boolean = false
    ) {
        if (complete) {
            Surface(
                onClick = { editing = true },
                modifier = modifier.heightIn(min = Dimens.setField).semantics {
                    contentDescription = "Correct $label for set $number"
                },
                color = colors.surface,
                shape = InShapeShapes.field
            ) {
                Column(
                    Modifier.padding(Dimens.innerSmall),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!compact) Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    Text(completedValue, style = InShapeType.setValue)
                }
            }
        } else {
            NumberField(draftValue, onChange, label, modifier, decimal = decimal, compact = compact, highlighted = highlighted)
        }
    }

    val changeLoad: (String) -> Unit = {
        load = it
        vm.write { saveSetDraft(set.id, it, reps) }
    }
    val changeReps: (String) -> Unit = {
        reps = it
        vm.write { saveSetDraft(set.id, load, it) }
    }

    @Composable fun Done() {
        Box(Modifier.size(Dimens.setField), contentAlignment = Alignment.Center) {
            IconToggleButton(
                complete,
                {
                    vm.write { setSetCompleted(set.id, it) }
                },
                Modifier.size(
                    Dimens.check
                ).background(if (complete) colors.ink else Color.Transparent, InShapeShapes.pill)
                    .then(
                        if (complete) {
                            Modifier
                        } else {
                            Modifier.border(
                                Dimens.chartOutline,
                                colors.textSecondary,
                                InShapeShapes.pill
                            )
                        }
                    )
            ) {
                Icon(
                    Icons.Outlined.Check,
                    if (complete) "Undo set $number" else "Complete set $number",
                    tint = if (complete) colors.onInk else colors.textSecondary
                )
            }
        }
    }
    BoxWithConstraints(modifier) {
        if (maxWidth < Dimens.setFallback ||
            LocalDensity.current.fontScale >= 1.3f
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Options()
                    Text(
                        "Set $number, ${if (complete) "completed" else "not completed"}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Done()
                }
                SetValue(unit, set.load.orEmpty(), load, changeLoad, Modifier.fillMaxWidth(), decimal = true)
                SetValue("Reps", set.reps?.toString().orEmpty(), reps, changeReps, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Options()
                SetValue(unit, set.load.orEmpty(), load, changeLoad, Modifier.weight(1f), decimal = true, compact = true)
                SetValue("Reps", set.reps?.toString().orEmpty(), reps, changeReps, Modifier.weight(1f), compact = true)
                Done()
            }
        }
    }
    if (editing) {
        var correctionLoad by remember { mutableStateOf(set.load ?: "") }
        var correctionReps by remember { mutableStateOf(set.reps?.toString() ?: "") }
        AlertDialog(onDismissRequest = {
            editing = false
        }, title = { Text("Correct set $number") }, text = {
            Column {
                NumberField(correctionLoad, { correctionLoad = it }, unit, decimal = true)
                NumberField(correctionReps, { correctionReps = it }, "Reps")
            }
        }, confirmButton = {
            DialogAction("Update", {
                vm.write {
                    correctCompletedSet(set.id, correctionLoad, correctionReps)
                    load =
                        correctionLoad
                    reps = correctionReps
                    editing = false
                }
            })
        }, dismissButton = {
            DialogAction("Cancel", {
                editing =
                    false
            })
        })
    }
}
