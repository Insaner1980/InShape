package com.emmah.inshape.feature.library

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.RoutineItem
import com.emmah.inshape.data.SetTarget
import com.emmah.inshape.data.newId
import com.emmah.inshape.data.validatedSetValues
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.ButtonSize
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.PushedHeader
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
    modifier: Modifier = Modifier
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
    var showNameError by rememberSaveable { mutableStateOf(false) }
    var routineMenu by remember { mutableStateOf(false) }
    fun save(start: Boolean) {
        if (draft.title.isBlank()) {
            showNameError = true
            details = true
            return
        }
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
    Page(
        modifier,
        topBar = {
            PushedHeader(onBack = onSave, title = draft.title.ifBlank { "New routine" }) {
                Box {
                    IconButton({ routineMenu = true }) { Icon(Icons.Outlined.MoreVert, "Routine options") }
                    AppMenu(routineMenu, { routineMenu = false }) {
                        MenuAction("Edit details", Icons.Outlined.Edit, {
                            routineMenu = false
                            details = true
                        })
                        if (id != "new") {
                            MenuAction("Duplicate", Icons.Outlined.ContentCopy, {
                                routineMenu = false
                                vm.write {
                                    duplicateRoutine(id)
                                    onSave()
                                }
                            })
                            MenuAction("Remove", Icons.Outlined.Delete, {
                                routineMenu = false
                                removing = true
                            }, destructive = true)
                        }
                    }
                }
            }
        },
        bottomBar = {
            PrimaryButton("Start routine", {
                save(true)
            }, Modifier.fillMaxWidth(), size = ButtonSize.Large, enabled = draft.items.isNotEmpty())
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            Text(
                pluralStringResource(R.plurals.exercise_count, draft.items.size, draft.items.size) +
                    draft.duration.takeIf(String::isNotBlank)?.let { ", about $it min" }.orEmpty()
            )
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
                                modifier = Modifier.semantics {
                                    customActions = buildList {
                                        if (index > 0) {
                                            add(
                                                CustomAccessibilityAction("Move up") {
                                                    move(entry.id, -1)
                                                    true
                                                }
                                            )
                                        }
                                        if (index < draft.items.lastIndex) {
                                            add(
                                                CustomAccessibilityAction("Move down") {
                                                    move(entry.id, 1)
                                                    true
                                                }
                                            )
                                        }
                                    }
                                },
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
            TextAction("Add exercise", { picker = true }, icon = Icons.Outlined.Add)
            // The editor keeps a draft until Save routine or Start routine is pressed.
            OutlineButton("Save routine", { save(false) }, Modifier.fillMaxWidth())
        }
    }
    if (details) {
        AlertDialog(onDismissRequest = {
            details = false
        }, title = { Text("Routine details") }, text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
            ) {
                TextField(
                    draft.title,
                    "Routine name",
                    { edit(draft.copy(title = it)) },
                    singleLine = true,
                    error = "Enter a routine name".takeIf { showNameError && draft.title.isBlank() }
                )
                TextField(draft.description, "Description (optional)", {
                    edit(draft.copy(description = it))
                })
                NumberField(draft.duration, {
                    edit(draft.copy(duration = it))
                }, "Estimated minutes (optional)")
            }
        }, confirmButton = {
            TextAction("Done", {
                if (draft.title.isBlank()) showNameError = true else details = false
            })
        })
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
