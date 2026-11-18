package com.emmah.inshape.feature.workout

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.feature.library.ExercisePicker
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.ButtonSize
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.Pill
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.ProgressSegments
import com.emmah.inshape.ui.components.ProgressState
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.formatElapsed
import com.emmah.inshape.ui.theme.journalDateLabel
import com.emmah.inshape.ui.theme.rememberElapsedSeconds
import com.emmah.inshape.ui.theme.workoutTime
import java.time.Instant
import java.time.ZoneId

fun itemSummary(item: WorkoutItem): String = if (item.completedSets.isNotEmpty()) {
    item.completedSets.groupBy {
        it.load to
            it.reps
    }.entries.joinToString("; ") { (values, sets) ->
        listOfNotNull(
            "${sets.size} ${if (sets.size == 1) "set" else "sets"}",
            values.second?.let {
                "$it reps"
            },
            values.first?.let {
                "$it ${if (item.row.basis == "assistance_kg") "kg assistance" else item.row.basis}"
            }
        ).joinToString(", ")
    }
} else if (item.row.quickCompletedAt !=
    null
) {
    item.row.durationSeconds?.let { "Marked done, ${com.emmah.inshape.ui.theme.formatDuration(it)}" } ?: "Marked done"
} else {
    "Not completed"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutScreen(
    id: String,
    data: JournalSnapshot,
    vm: JournalViewModel,
    onExercise: (String, String) -> Unit,
    onCheckIn: () -> Unit,
    onFinish: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val workout = data.workout(id) ?: return
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    val colors = InShapeTheme.colors
    var picker by remember { mutableStateOf(false) }
    var alternatives by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<String?>(null) }
    var routineSave by remember { mutableStateOf(false) }
    val pending = workout.items.filter { it.row.disposition in listOf("PLANNED", "IN_PROGRESS") }
    val current = pending.find { it.row.id == workout.row.currentItemId } ?: pending.firstOrNull()
    var workoutMenu by remember { mutableStateOf(false) }
    val elapsed by rememberElapsedSeconds(
        workout.row.start?.takeUnless {
            workout.row.timingNeedsReview
        }?.let(Instant::parse),
        vm.container.clock
    )
    var duration by rememberSaveable(current?.row?.id) {
        mutableStateOf(data.drafts.find { it.id == "duration:${current?.row?.id}" }?.payload ?: "")
    }
    val scrollState = rememberScrollState()
    var clockHeight by remember { mutableIntStateOf(0) }
    val screenTopPx = with(LocalDensity.current) { Dimens.screenTop.roundToPx() }
    val showTopClock by remember(scrollState, screenTopPx) {
        derivedStateOf { clockHeight > 0 && scrollState.value >= clockHeight + screenTopPx }
    }
    val bottomAction: (@Composable () -> Unit)? = if (WindowInsets.isImeVisible) {
        null
    } else {
        {
            PrimaryButton(
                if (current == null) "Finish workout" else "Exercise done",
                {
                    if (current == null) {
                        onFinish()
                    } else {
                        vm.write {
                            val seconds = if (duration.isBlank()) {
                                null
                            } else {
                                duration.toLongOrNull()?.takeIf {
                                    it >
                                        0 &&
                                        it <= Long.MAX_VALUE / 60
                                }?.times(60)
                                    ?: throw IllegalArgumentException("Enter positive whole minutes")
                            }
                            markItemDone(current.row.id, seconds)
                            deleteEditorDraft("duration:${current.row.id}")
                        }
                    }
                },
                Modifier.fillMaxWidth(),
                size = ButtonSize.Large
            )
        }
    }
    Page(
        modifier,
        bottomBar = bottomAction,
        topBar = {
            PushedHeader(onBack) {
                elapsed?.takeIf { showTopClock }?.let {
                    Text(formatElapsed(it), style = InShapeType.numeric, modifier = Modifier.padding(end = Dimens.innerSmall))
                }
                OutlineButton("Finish", onFinish)
                Box {
                    IconButton({ workoutMenu = true }) { Icon(Icons.Outlined.MoreVert, "Workout options") }
                    AppMenu(workoutMenu, { workoutMenu = false }) {
                        MenuAction("Add exercise", Icons.Outlined.Add, {
                            workoutMenu = false
                            picker = true
                        })
                        MenuAction("Check in before workout", Icons.Outlined.WbTwilight, {
                            workoutMenu = false
                            onCheckIn()
                        })
                        if (data.routines.any { it.id == workout.row.sourceRoutineId }) {
                            MenuAction("Save changes to routine", Icons.Outlined.Save, {
                                workoutMenu = false
                                routineSave = true
                            })
                        }
                        HorizontalDivider(color = colors.divider)
                        MenuAction("Discard workout", Icons.Outlined.Delete, {
                            workoutMenu = false
                            discard = true
                        }, destructive = true)
                    }
                }
            }
        },
        scrollState = scrollState
    ) {
        Column(
            Modifier.fillMaxWidth().onSizeChanged { clockHeight = it.height }.padding(bottom = Dimens.itemGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)
        ) {
            Text(workout.row.title, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            if (elapsed != null) {
                Text(formatElapsed(elapsed!!), style = MaterialTheme.typography.displayLarge)
                workout.row.start?.let {
                    Text(
                        "Started ${Instant.parse(it).workoutTime(ZoneId.of(workout.row.zoneId), uses24Hours)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
            } else {
                Text(
                    "Started ${workout.row.journalDate.journalDateLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                Text("Review the timing before saving.", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
        if (workout.items.isNotEmpty()) {
            ProgressSegments(
                workout.items.map {
                    when {
                        it.row.disposition == "DONE" -> ProgressState.Done

                        it.row.id ==
                            current?.row?.id -> ProgressState.Current

                        else -> ProgressState.Pending
                    }
                },
                "${workout.doneCount} of ${workout.items.size} done"
            )
            Text(
                if (current == null) "All exercises done" else "Exercise ${workout.items.indexOf(current) + 1} of ${workout.items.size}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        if (current == null) {
            val empty = workout.items.isEmpty()
            EmptyState(
                if (empty) "No exercises yet" else "Your plan is complete",
                if (empty) "Add exercises as you go." else "Add another exercise or finish with the work you recorded.",
                action = "Add exercise",
                onAction = { picker = true },
                icon = Icons.Outlined.Add
            )
        }
        current?.let { item ->
            var menu by remember(item.row.id) { mutableStateOf(false) }
            var exerciseNote by rememberSaveable(item.row.id) { mutableStateOf(item.row.note) }
            var expanded by rememberSaveable(item.row.id) {
                mutableStateOf(false)
            }
            var focusNote by remember { mutableStateOf(false) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(expanded, focusNote) {
                if (expanded &&
                    focusNote
                ) {
                    focus.requestFocus()
                    focusNote = false
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.row.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Box {
                        IconButton({
                            menu = true
                        }) { Icon(Icons.Outlined.MoreVert, "More options for ${item.row.name}") }
                        AppMenu(menu, { menu = false }) {
                            MenuAction("Exercise details", Icons.Outlined.Info, {
                                menu = false
                                onExercise(item.row.exerciseId, item.row.id)
                            })
                            MenuAction(if (exerciseNote.isBlank()) "Add note" else "Edit note", Icons.Outlined.EditNote, {
                                menu = false
                                expanded = true
                                focusNote = true
                            })
                            MenuAction("Swap exercise", Icons.Outlined.SwapHoriz, {
                                menu = false
                                alternatives = true
                            })
                            MenuAction("Do later", Icons.Outlined.Schedule, {
                                menu = false
                                vm.write { deferItem(item.row.id) }
                            })
                            MenuAction("Skip today", Icons.Outlined.SkipNext, {
                                menu = false
                                vm.write { skipItem(item.row.id) }
                            })
                            HorizontalDivider(color = colors.divider)
                            MenuAction("Remove exercise", Icons.Outlined.Delete, {
                                menu = false
                                if (item.hasPerformedWork) remove = item.row.id else vm.write { removeItem(item.row.id) }
                            }, destructive = true)
                        }
                    }
                }
                val settings = item.row.setup.split(", ").mapNotNull { raw ->
                    val entry = raw.trim()
                    val label = entry.substringBefore(": ")
                    val value = entry.substringAfter(": ")
                    when {
                        value.isBlank() || value.equals("None", ignoreCase = true) -> null

                        label != value && label.equals("Handle", ignoreCase = true) -> value

                        label != value && label.equals("Seat height", ignoreCase = true) -> "Seat $value"

                        label != value -> "$label $value"

                        // Older journal entries stored the label and value without a colon.
                        entry.endsWith(" None", ignoreCase = true) -> null

                        entry.startsWith("Handle ", ignoreCase = true) -> entry.drop("Handle ".length)

                        entry.startsWith("Seat height ", ignoreCase = true) -> "Seat " + entry.drop("Seat height ".length)

                        else -> entry
                    }
                }
                if (settings.isNotEmpty()) ActionRow { settings.forEach { Pill(it, outlined = true) } }
                val previous = data.history.filter {
                    it.row.id != id
                }.sortedWith(
                    compareByDescending<Workout> {
                        it.row.journalDate
                    }.thenByDescending { it.row.createdAt }
                ).firstNotNullOfOrNull { old ->
                    old.items.find {
                        it.row.exerciseId == item.row.exerciseId &&
                            it.row.equipmentId == item.row.equipmentId &&
                            it.row.basis == item.row.basis &&
                            it.hasPerformedWork
                    }?.let { old.row.journalDate to it }
                }
                if (previous != null) {
                    InShapeCard(inset = true, gap = Dimens.innerTiny) {
                        Text(
                            "Last time, ${previous.first.journalDateLabel()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Text(
                            itemSummary(previous.second),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        )
                        previous.second.row.note.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        }
                    }
                }
                if (item.sets.isNotEmpty()) SetTableHeader(item.row.basis)
                val firstIncomplete = item.sets.firstOrNull { it.completedAt == null }?.id
                item.sets.forEachIndexed { index, set ->
                    key(set.id) {
                        SetRow(
                            set,
                            index + 1,
                            item.row.basis,
                            vm,
                            highlighted =
                                set.id == firstIncomplete
                        )
                    }
                }
                TextAction("Add set", {
                    vm.write { addSet(item.row.id) }
                }, icon = Icons.Outlined.Add)
                if (item.row.mode == "DURATION" || item.sets.isEmpty() ||
                    duration.isNotEmpty()
                ) {
                    NumberField(duration, {
                        duration = it
                        vm.saveDraft("duration:${item.row.id}", "duration", it)
                    }, "Performed minutes (optional)")
                }
                if (expanded) {
                    TextField(exerciseNote, "Note for this exercise", {
                        exerciseNote = it
                        vm.write { saveItemNote(item.row.id, it, item.row.discomfort) }
                    }, modifier = Modifier.focusRequester(focus))
                    ListRow("This movement felt uncomfortable", trailing = {
                        Checkbox(item.row.discomfort, { value ->
                            vm.write { saveItemNote(item.row.id, exerciseNote, value) }
                        })
                    })
                }
                if (!expanded && exerciseNote.isNotBlank()) {
                    Text(
                        exerciseNote,
                        Modifier.fillMaxWidth().heightIn(min = Dimens.touch).clickable {
                            expanded = true
                            focusNote = true
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }
        val next = pending.filter { it.row.id != current?.row?.id }
        if (next.isNotEmpty()) {
            SectionHeader("Up next")
            ListCard {
                next.forEach { nextItem ->
                    item {
                        ListRow(nextItem.row.name, trailing = {
                            Text(
                                nextItem.row.targetDurationSeconds?.let { com.emmah.inshape.ui.theme.formatDuration(it) }
                                    ?: if (nextItem.row.mode ==
                                        "QUICK"
                                    ) {
                                        "Completion only"
                                    } else {
                                        "${nextItem.sets.size} sets"
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                        })
                    }
                }
            }
        }
        val done = workout.items.filter { it.row.disposition == "DONE" }
        if (done.isNotEmpty()) {
            SectionHeader("Done")
            ListCard {
                done.forEach { doneItem ->
                    item {
                        ListRow(doneItem.row.name, subtitle = itemSummary(doneItem), trailing = {
                            IconButton({
                                vm.write { reopenItem(doneItem.row.id) }
                            }) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    "Reopen ${doneItem.row.name}",
                                    tint = colors.ink
                                )
                            }
                        })
                    }
                }
            }
        }
        val skipped = workout.items.filter { it.row.disposition in listOf("SKIPPED", "INCOMPLETE") }
        if (skipped.isNotEmpty()) {
            SectionHeader("Skipped")
            ListCard {
                skipped.forEach { skippedItem ->
                    item {
                        ListRow(
                            skippedItem.row.name,
                            subtitle = if (skippedItem.hasPerformedWork) "Remainder skipped, recorded work kept" else "Skipped today",
                            trailing = {
                                TextAction("Reopen", { vm.write { reopenItem(skippedItem.row.id) } })
                            }
                        )
                    }
                }
            }
        }
    }
    if (picker) {
        ModalBottomSheet(onDismissRequest = { picker = false }, containerColor = colors.background) {
            ExercisePicker(data, {
                vm.write { addExercise(id, it) }
                picker =
                    false
            }, {
                picker = false
                onExercise("new", "")
            })
        }
    }
    if (alternatives &&
        current != null
    ) {
        ModalBottomSheet(onDismissRequest = {
            alternatives = false
        }, containerColor = colors.background) {
            val ids =
                data.exercises.find { it.id == current.row.exerciseId }?.alternatives ?: emptyList()
            Page {
                SectionHeader("Your alternatives")
                if (ids.isEmpty()) {
                    Text(
                        "Add your alternatives by editing this exercise in Library."
                    )
                }
                ListCard {
                    data.exercises.filter { it.id in ids && !it.archived }.forEach { exercise ->
                        item {
                            ListRow(exercise.name, subtitle = exercise.equipmentName, showChevron = false, onClick = {
                                vm.write { useAlternative(current.row.id, exercise.id) }
                                alternatives =
                                    false
                            })
                        }
                    }
                }
            }
        }
    }
    if (discard) {
        ConfirmDialog("Discard this workout?", "The session and all work recorded in it will be deleted.", "Discard", {
            vm.write {
                deleteWorkout(id)
                onDelete()
            }
            discard =
                false
        }, { discard = false })
    }
    remove?.let { item ->
        ConfirmDialog(
            "Delete recorded work?",
            "All sets, notes and completion for this exercise will be removed from this workout.",
            "Delete",
            {
                vm.write { removeItem(item, true) }
                remove =
                    null
            },
            { remove = null }
        )
    }
    if (routineSave) {
        ConfirmDialog(
            "Update the saved routine?",
            "Replace its exercise order and planned targets with:\n" +
                workout.items.joinToString("\n") {
                    "${it.row.name}, ${it.sets.size} planned sets"
                },
            "Update routine",
            {
                vm.write { saveSessionPlanToRoutine(id, workout.row.sourceRoutineId!!) }
                routineSave =
                    false
            },
            { routineSave = false }
        )
    }
}
