package com.emmah.inshape.feature.library

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.RoutineItem
import com.emmah.inshape.data.SetTarget
import com.emmah.inshape.data.newId
import com.emmah.inshape.data.validatedSetValues
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.SectionLabel
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class RoutineSetDraft(val load: String = "", val reps: String = "")

@Serializable data class RoutineItemDraft(
    val id: String = newId(),
    val exerciseId: String,
    val mode: String = "SETS",
    val duration: String = "",
    val note: String = "",
    val sets: List<RoutineSetDraft> = emptyList(),
    val originalDurationSeconds: Long? = null,
    val durationReviewed: Boolean = false
) {
    val requiresDurationReview: Boolean get() = mode == "DURATION" && originalDurationSeconds != null && !durationReviewed

    fun restoreDuration(seconds: Long?): RoutineItemDraft {
        if (originalDurationSeconds != null || durationReviewed || seconds == null) return this
        return if (seconds % 60 != 0L && duration == (seconds / 60).toString()) {
            copy(originalDurationSeconds = seconds)
        } else {
            this
        }
    }

    fun plannedDurationSeconds(): Long? {
        if (mode != "DURATION") return null
        require(!requiresDurationReview) { "Review the saved planned duration before saving this routine." }
        return when {
            duration.isBlank() -> null

            originalDurationSeconds != null && duration == (originalDurationSeconds / 60).toString() -> originalDurationSeconds

            else -> duration.toLongOrNull()?.takeIf { it > 0 && it <= Long.MAX_VALUE / 60 }?.times(60)
                ?: throw IllegalArgumentException("Enter positive whole minutes")
        }
    }
}

@Serializable data class RoutineDraft(
    val id: String = newId(),
    val title: String = "",
    val description: String = "",
    val duration: String = "",
    val items: List<RoutineItemDraft> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditor(
    id: String,
    data: JournalSnapshot,
    vm: JournalViewModel,
    onSave: () -> Unit,
    onStart: (String) -> Unit,
    onCreateExercise: () -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false
) {
    var draft by remember(id) {
        mutableStateOf(
            data.drafts.find {
                it.id == "routine:$id"
            }?.let { runCatching { Json.decodeFromString<RoutineDraft>(it.payload) }.getOrNull() }
                ?.let { restored ->
                    restored.copy(
                        items = restored.items.map { item ->
                            item.restoreDuration(data.routineItems.find { it.routineId == id && it.id == item.id }?.durationSeconds)
                        }
                    )
                }
                ?: data.routines.find { it.id == id }?.let { routine ->
                    RoutineDraft(
                        routine.id,
                        routine.title,
                        routine.description,
                        routine.durationMinutes?.toString() ?: "",
                        data.routineItems.filter {
                            it.routineId == id
                        }.map { item ->
                            RoutineItemDraft(
                                item.id,
                                item.exerciseId,
                                item.mode,
                                item.durationSeconds?.div(60)?.toString() ?: "",
                                item.note,
                                item.sets.map {
                                    RoutineSetDraft(it.load ?: "", it.reps?.toString() ?: "")
                                },
                                originalDurationSeconds = item.durationSeconds,
                                durationReviewed = true
                            )
                        }
                    )
                } ?: RoutineDraft()
        )
    }
    var picker by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(id == "new") }
    var editingItem by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<String?>(null) }
    var movement by remember { mutableStateOf("") }
    fun edit(value: RoutineDraft) {
        draft = value
        vm.saveDraft("routine:$id", "routine", Json.encodeToString(value))
    }
    fun updateItem(index: Int, item: RoutineItemDraft) = edit(
        draft.copy(
            items = draft.items.toMutableList().also {
                it[index] =
                    item
            }
        )
    )
    fun move(itemId: String, offset: Int) {
        val items = draft.items.toMutableList()
        val from = items.indexOfFirst { it.id == itemId }
        val target = (
            from +
                offset
            ).coerceIn(items.indices)
        val item = items.removeAt(from)
        items.add(target, item)
        edit(draft.copy(items = items))
        movement =
            "${data.exercises.find {
                it.id == item.exerciseId
            }?.name ?: "Exercise"} moved to position ${target + 1}"
    }
    fun save(start: Boolean) {
        val toSave = draft
        vm.write {
            fun minutes(value: String): Int? = if (value.isBlank()) {
                null
            } else {
                value.toIntOrNull()?.takeIf {
                    it >
                        0
                }
                    ?: throw IllegalArgumentException("Enter a positive duration in minutes")
            }
            val routine =
                Routine(toSave.id, toSave.title, toSave.description, minutes(toSave.duration))
            val items = toSave.items.map { item ->
                RoutineItem(
                    item.id,
                    routine.id,
                    item.exerciseId,
                    mode = item.mode,
                    durationSeconds = item.plannedDurationSeconds(),
                    note = item.note,
                    sets = item.sets.map { target ->
                        if (target.load.isBlank() &&
                            target.reps.isBlank()
                        ) {
                            SetTarget()
                        } else {
                            validatedSetValues(target.load, target.reps).let {
                                SetTarget(it.first, it.second)
                            }
                        }
                    }
                )
            }
            saveRoutine(routine, items)
            deleteEditorDraft("routine:$id")
            if (start) onStart(routine.id) else onSave()
        }
    }
    val dragStep = with(LocalDensity.current) { Dimens.rowMinTwoLine.toPx() }
    val content: @Composable ColumnScope.() -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    SectionLabel(draft.title.ifBlank { "New routine" })
                    Text(
                        "${draft.items.size} exercises" +
                            draft.duration.takeIf {
                                it.isNotBlank()
                            }?.let { ", about $it min" }.orEmpty()
                    )
                }
            }
            if (draft.description.isNotBlank()) Text(draft.description)
            if (movement.isNotEmpty()) {
                Text(
                    movement,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics {
                        liveRegion =
                            LiveRegionMode.Polite
                    }
                )
            }
            draft.items.firstOrNull { it.requiresDurationReview }?.let { item ->
                OutlineButton("Review saved duration", { editingItem = item.id })
            }
            ListCard {
                draft.items.forEachIndexed { index, entry ->
                    item {
                        key(entry.id) {
                            var distance by remember { mutableFloatStateOf(0f) }
                            val exercise = data.exercises.find { it.id == entry.exerciseId }
                            ListRow(
                                exercise?.name ?: "Archived exercise",
                                subtitle = if (entry.mode ==
                                    "DURATION"
                                ) {
                                    "${entry.duration.ifBlank { "No" }} min planned"
                                } else if (entry.mode ==
                                    "QUICK"
                                ) {
                                    "Completion only"
                                } else {
                                    exercise?.category ?: "Exercise"
                                },
                                leading = {
                                    Box(
                                        Modifier.size(
                                            Dimens.touch
                                        ).pointerInput(entry.id, draft.items.size) {
                                            detectDragGesturesAfterLongPress(onDragStart = {
                                                distance = 0f
                                            }, onDrag = { change, amount ->
                                                change.consume()
                                                distance +=
                                                    amount.y
                                            }, onDragEnd = {
                                                move(entry.id, (distance / dragStep).toInt())
                                                distance =
                                                    0f
                                            })
                                        },
                                        contentAlignment = androidx.compose.ui.Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.DragIndicator,
                                            "Drag to reorder ${exercise?.name ?: "exercise"}",
                                            Modifier.size(Dimens.screenPadding),
                                            tint = InShapeTheme.colors.textSecondary
                                        )
                                    }
                                },
                                trailing = {
                                    Box {
                                        IconButton({
                                            menu = entry.id
                                        }) {
                                            Icon(
                                                Icons.Outlined.MoreVert,
                                                "Options for ${exercise?.name ?: "exercise"}"
                                            )
                                        }
                                        AppMenu(menu == entry.id, { menu = null }) {
                                            DropdownMenuItem({ Text("Edit targets and note") }, {
                                                menu =
                                                    null
                                                editingItem = entry.id
                                            })
                                            DropdownMenuItem(
                                                { Text("Move up") },
                                                {
                                                    menu = null
                                                    move(entry.id, -1)
                                                },
                                                enabled =
                                                    index > 0
                                            )
                                            DropdownMenuItem(
                                                { Text("Move down") },
                                                {
                                                    menu = null
                                                    move(entry.id, 1)
                                                },
                                                enabled =
                                                    index < draft.items.lastIndex
                                            )
                                            DropdownMenuItem({ Text("Remove exercise") }, {
                                                menu = null
                                                edit(
                                                    draft.copy(
                                                        items = draft.items.filter {
                                                            it.id !=
                                                                entry.id
                                                        }
                                                    )
                                                )
                                            })
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            ActionRow {
                OutlineButton("Add exercise", { picker = true })
                OutlineButton("Start routine", { save(true) })
            }
            TextAction("Edit routine details", { details = true })
            if (embedded) PrimaryButton("Save routine", { save(false) }, Modifier.fillMaxWidth())
        }
    }
    if (embedded) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Dimens.inner),
            content = content
        )
    } else {
        Page(modifier, bottomBar = { PrimaryButton("Save routine", { save(false) }, Modifier.fillMaxWidth()) }, content = content)
    }
    if (details) {
        AlertDialog(onDismissRequest = {
            details = false
        }, title = { Text("Routine details") }, text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
            ) {
                TextField(draft.title, "Routine name", { edit(draft.copy(title = it)) }, singleLine = true)
                TextField(draft.description, "Description (optional)", {
                    edit(draft.copy(description = it))
                })
                NumberField(draft.duration, {
                    edit(draft.copy(duration = it))
                }, "Estimated minutes (optional)")
                if (id != "new") {
                    TextAction("Duplicate routine", {
                        vm.write {
                            duplicateRoutine(id)
                            details = false
                            onSave()
                        }
                    })
                    TextAction("Remove routine", {
                        details = false
                        removing = true
                    }, color = InShapeTheme.colors.error)
                }
            }
        }, confirmButton = { TextAction("Done", { details = false }) })
    }
    editingItem?.let { editing ->
        val index = draft.items.indexOfFirst { it.id == editing }
        if (index >= 0) {
            val item = draft.items[index]
            val exercise = data.exercises.find {
                it.id ==
                    item.exerciseId
            }
            AlertDialog(onDismissRequest = {
                editingItem = null
            }, title = { Text(exercise?.name ?: "Exercise") }, text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
                ) {
                    SelectField(
                        "Record",
                        when (item.mode) {
                            "DURATION" -> "Duration"
                            "QUICK" -> "Completion only"
                            else -> "Sets"
                        },
                        listOf("Sets", "Duration", "Completion only"),
                        { label ->
                            updateItem(
                                index,
                                item.copy(
                                    mode = when (label) {
                                        "Duration" -> "DURATION"
                                        "Completion only" -> "QUICK"
                                        else -> "SETS"
                                    },
                                    sets = if (label ==
                                        "Sets"
                                    ) {
                                        item.sets
                                    } else {
                                        emptyList()
                                    }
                                )
                            )
                        }
                    )
                    if (item.requiresDurationReview) {
                        Text("Saved duration: ${item.originalDurationSeconds} seconds. Keep it, or edit planned minutes below.")
                        OutlineButton("Keep saved duration", { updateItem(index, item.copy(durationReviewed = true)) })
                    }
                    if (item.mode ==
                        "DURATION"
                    ) {
                        NumberField(item.duration, {
                            updateItem(index, item.copy(duration = it, originalDurationSeconds = null, durationReviewed = true))
                        }, "Planned minutes")
                    }
                    if (item.mode == "SETS") {
                        item.sets.forEachIndexed { setIndex, set ->
                            Text("Set ${setIndex + 1}")
                            NumberField(set.load, { text ->
                                updateItem(
                                    index,
                                    item.copy(
                                        sets = item.sets.toMutableList().also {
                                            it[setIndex] =
                                                set.copy(load = text)
                                        }
                                    )
                                )
                            }, exercise?.basis ?: "Load", decimal = true)
                            NumberField(set.reps, { text ->
                                updateItem(
                                    index,
                                    item.copy(
                                        sets = item.sets.toMutableList().also {
                                            it[setIndex] =
                                                set.copy(reps = text)
                                        }
                                    )
                                )
                            }, "Reps")
                            TextAction("Remove planned set", {
                                updateItem(
                                    index,
                                    item.copy(
                                        sets = item.sets.filterIndexed { i, _ ->
                                            i !=
                                                setIndex
                                        }
                                    )
                                )
                            })
                        }
                        OutlineButton("Add planned set", {
                            updateItem(
                                index,
                                item.copy(
                                    sets =
                                        item.sets + RoutineSetDraft()
                                )
                            )
                        })
                    }
                    TextField(item.note, "Plan note", { updateItem(index, item.copy(note = it)) })
                }
            }, confirmButton = { TextAction("Done", { editingItem = null }) })
        }
    }
    if (picker) {
        ModalBottomSheet(onDismissRequest = { picker = false }) {
            ExercisePicker(data, { exerciseId ->
                edit(
                    draft.copy(
                        items =
                            draft.items + RoutineItemDraft(exerciseId = exerciseId)
                    )
                )
                picker = false
            }, {
                picker =
                    false
                onCreateExercise()
            })
        }
    }
    if (removing) {
        ConfirmDialog("Remove routine?", "Saved workouts and any active session are kept.", "Remove", {
            vm.write {
                removeRoutine(id)
                deleteEditorDraft("routine:$id")
                onSave()
            }
            removing =
                false
        }, { removing = false })
    }
}
