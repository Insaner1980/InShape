package com.emmah.inshape.feature.workout

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.*
import com.emmah.inshape.feature.library.ExercisePicker
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*

fun itemSummary(item: WorkoutItem): String = if (item.completedSets.isNotEmpty()) item.completedSets.groupBy { it.load to it.reps }.entries.joinToString("; ") { (values, sets) ->
    listOfNotNull("${sets.size} ${if (sets.size == 1) "set" else "sets"}", values.second?.let { "$it reps" }, values.first?.let { "$it ${if (item.row.basis == "assistance_kg") "kg assistance" else item.row.basis}" }).joinToString(", ")
} else if (item.row.quickCompletedAt != null) item.row.durationSeconds?.let { "Marked done, ${it / 60} min" } ?: "Marked done" else "Not completed"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(id: String, data: JournalSnapshot, vm: JournalViewModel, onDetails: (String, String) -> Unit, onCreateExercise: () -> Unit, onCheckIn: () -> Unit, onFinish: () -> Unit, onDeleted: () -> Unit) {
    val workout = data.workout(id) ?: return
    val colors = InShapeTheme.colors
    var picker by remember { mutableStateOf(false) }
    var alternatives by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<String?>(null) }
    var routineSave by remember { mutableStateOf(false) }
    val pending = workout.items.filter { it.row.disposition in listOf("PLANNED", "IN_PROGRESS") }
    val current = pending.find { it.row.id == workout.row.currentItemId } ?: pending.firstOrNull()
    var note by rememberSaveable(id) { mutableStateOf(workout.row.note) }
    var duration by rememberSaveable(current?.row?.id) { mutableStateOf(data.drafts.find { it.id == "duration:${current?.row?.id}" }?.payload ?: "") }
    Page(bottomBar = {
        PrimaryButton(if (current == null) "Review and finish" else "Mark done", {
            if (current == null) onFinish() else vm.write {
                val seconds = if (duration.isBlank()) null else duration.toLongOrNull()?.takeIf { it > 0 && it <= Long.MAX_VALUE / 60 }?.times(60) ?: throw IllegalArgumentException("Enter positive whole minutes")
                markItemDone(current.row.id, seconds); deleteEditorDraft("duration:${current.row.id}")
            }
        }, Modifier.fillMaxWidth().heightIn(min = Dimens.button), trailingIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight)
    }) {
        if (workout.row.timingNeedsReview) InShapeCard { Text("This session began on another date. Review its timing before saving.", style = MaterialTheme.typography.bodyMedium) }
        ProgressSegments(workout.items.map { when { it.row.disposition == "DONE" -> ProgressState.Done; it.row.id == current?.row?.id -> ProgressState.Current; else -> ProgressState.Pending } }, "${workout.doneCount} of ${workout.items.size} done")
        Text("${workout.doneCount} of ${workout.items.size} done", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        if (current == null) EmptyState("Your plan is complete", "Add another exercise or finish with the work you recorded.")
        current?.let { item ->
            var menu by remember(item.row.id) { mutableStateOf(false) }
            var exerciseNote by rememberSaveable(item.row.id) { mutableStateOf(item.row.note) }
            var expanded by rememberSaveable(item.row.id) { mutableStateOf(item.row.note.isNotBlank() || item.row.discomfort) }
            var focusNote by remember { mutableStateOf(false) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(expanded, focusNote) { if (expanded && focusNote) { focus.requestFocus(); focusNote = false } }
            InShapeCard {
                ResponsiveRow {
                    Text(item.row.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                    TextAction("Details", { onDetails(item.row.exerciseId, item.row.id) })
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "More options for ${item.row.name}") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Move up") }, { menu = false; vm.write { moveItem(item.row.id, -1) } }, enabled = item.row.position > 0)
                            DropdownMenuItem({ Text("Move down") }, { menu = false; vm.write { moveItem(item.row.id, 1) } }, enabled = item.row.position < workout.items.lastIndex)
                            DropdownMenuItem({ Text("Remove exercise") }, { menu = false; if (item.hasPerformedWork) remove = item.row.id else vm.write { removeItem(item.row.id) } })
                        }
                    }
                }
                if (item.row.setup.isNotBlank()) ActionRow { item.row.setup.split(',').map(String::trim).filter(String::isNotEmpty).forEach { Pill(it) } }
                val previous = data.history.filter { it.row.id != id }.sortedWith(compareByDescending<Workout> { it.row.journalDate }.thenByDescending { it.row.createdAt }).firstNotNullOfOrNull { old ->
                    old.items.find { it.row.exerciseId == item.row.exerciseId && it.row.equipmentId == item.row.equipmentId && it.row.basis == item.row.basis && it.hasPerformedWork }?.let { old.row.journalDate to it }
                }
                Surface(shape = InShapeShapes.inset, color = if (previous == null) colors.surfaceInset else colors.accentContainer) {
                    Column(Modifier.fillMaxWidth().padding(Dimens.itemGap), verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                        ResponsiveRow {
                            Text(previous?.let { "Last time, ${it.first.journalDateLabel()}" } ?: "No previous record", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = if (previous == null) colors.textSecondary else colors.onAccentContainer)
                            previous?.let { Text(itemSummary(it.second), style = InShapeType.compactDisplay, color = colors.onAccentContainer) }
                        }
                        previous?.second?.row?.note?.takeIf(String::isNotBlank)?.let { Text("Your note: $it", style = MaterialTheme.typography.bodySmall, color = colors.onAccentContainer) }
                    }
                }
                if (item.sets.isNotEmpty()) SetTableHeader(item.row.basis)
                val firstIncomplete = item.sets.firstOrNull { it.completedAt == null }?.id
                item.sets.forEachIndexed { index, set -> key(set.id) { SetRow(set, index + 1, item.row.basis, vm, highlighted = set.id == firstIncomplete) } }
                TextAction("Add set", { vm.write { addSet(item.row.id) } }, icon = Icons.Outlined.Add)
                if (item.row.mode == "DURATION" || item.sets.isEmpty() || duration.isNotEmpty()) NumberField(duration, { duration = it; vm.saveDraft("duration:${item.row.id}", "duration", it) }, "Performed minutes (optional)")
                if (expanded) {
                    TextField(exerciseNote, "Note for this exercise", { exerciseNote = it; vm.write { saveItemNote(item.row.id, it, item.row.discomfort) } }, modifier = Modifier.focusRequester(focus))
                    ListRow("This movement felt uncomfortable", trailing = { Checkbox(item.row.discomfort, { value -> vm.write { saveItemNote(item.row.id, exerciseNote, value) } }) })
                }
                HorizontalDivider(color = colors.divider)
                ResponsiveRow {
                    SecondaryButton(if (expanded && exerciseNote.isBlank() && !item.row.discomfort) "Hide note" else "Add note", {
                        if (expanded && exerciseNote.isBlank() && !item.row.discomfort) expanded = false else { expanded = true; focusNote = true }
                    }, Modifier.weight(1f))
                    SecondaryButton("Swap", { alternatives = true }, Modifier.weight(1f))
                    SecondaryButton("Do later", { vm.write { deferItem(item.row.id) } }, Modifier.weight(1f))
                }
                TextAction("Skip today", { vm.write { skipItem(item.row.id) } }, Modifier.align(Alignment.End), color = colors.textSecondary)
            }
        }
        val next = pending.filter { it.row.id != current?.row?.id }
        if (next.isNotEmpty()) {
            SectionLabel("Up next")
            ListCard { next.forEach { nextItem -> item { ListRow(nextItem.row.name, trailing = {
                Text(nextItem.row.targetDurationSeconds?.let { "${it / 60} min" } ?: if (nextItem.row.mode == "QUICK") "Completion only" else "${nextItem.sets.size} sets", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }) } } }
        }
        val done = workout.items.filter { it.row.disposition == "DONE" }
        if (done.isNotEmpty()) {
            SectionLabel("Done")
            ListCard { done.forEach { doneItem -> item { ListRow(doneItem.row.name, subtitle = itemSummary(doneItem), trailing = {
                IconButton({ vm.write { reopenItem(doneItem.row.id) } }) { Icon(Icons.Outlined.CheckCircle, "Reopen ${doneItem.row.name}", tint = colors.mark) }
            }) } } }
        }
        val skipped = workout.items.filter { it.row.disposition in listOf("SKIPPED", "INCOMPLETE") }
        if (skipped.isNotEmpty()) {
            SectionLabel("Skipped")
            ListCard { skipped.forEach { skippedItem -> item { ListRow(skippedItem.row.name, subtitle = if (skippedItem.hasPerformedWork) "Remainder skipped, recorded work kept" else "Skipped today", trailing = {
                TextAction("Reopen", { vm.write { reopenItem(skippedItem.row.id) } })
            }) } } }
        }
        SecondaryButton("Add exercise", { picker = true }, Modifier.fillMaxWidth())
        TextField(note, "Workout note", { note = it; vm.write { saveWorkoutNote(id, it) } })
        ListCard { item { ListRow("Workout check-in", subtitle = "Effort, energy, pain and stiffness. Optional.", onClick = onCheckIn) } }
        workout.row.sourceRoutineId?.takeIf { source -> data.routines.any { it.id == source } }?.let { SecondaryButton("Save changes to routine", { routineSave = true }, Modifier.fillMaxWidth()) }
        TextAction("Discard workout", { discard = true }, Modifier.align(Alignment.CenterHorizontally), color = colors.error)
    }
    if (picker) ModalBottomSheet(onDismissRequest = { picker = false }, containerColor = colors.surface) { ExercisePicker(data, { vm.write { addExercise(id, it) }; picker = false }, { picker = false; onCreateExercise() }) }
    if (alternatives && current != null) ModalBottomSheet(onDismissRequest = { alternatives = false }, containerColor = colors.surface) {
        val ids = data.exercises.find { it.id == current.row.exerciseId }?.alternatives ?: emptyList()
        Page {
            SectionLabel("Your alternatives")
            if (ids.isEmpty()) Text("Add your alternatives by editing this exercise in Library.")
            ListCard { data.exercises.filter { it.id in ids && !it.archived }.forEach { exercise -> item { ListRow(exercise.name, subtitle = exercise.equipmentName, onClick = { vm.write { useAlternative(current.row.id, exercise.id) }; alternatives = false }) } } }
        }
    }
    if (discard) ConfirmDialog("Discard this workout?", "The session and all work recorded in it will be deleted.", "Discard", { vm.write { deleteWorkout(id); onDeleted() }; discard = false }, { discard = false })
    remove?.let { item -> ConfirmDialog("Delete recorded work?", "All sets, notes and completion for this exercise will be removed from this workout.", "Delete", { vm.write { removeItem(item, true) }; remove = null }, { remove = null }) }
    if (routineSave) ConfirmDialog("Update the saved routine?", "Replace its exercise order and planned targets with:\n" + workout.items.joinToString("\n") { "${it.row.name}, ${it.sets.size} planned sets" }, "Update routine", {
        vm.write { saveSessionPlanToRoutine(id, workout.row.sourceRoutineId!!) }; routineSave = false
    }, { routineSave = false })
}
