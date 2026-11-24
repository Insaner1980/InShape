package com.emmah.inshape.feature.program

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.ProgramDayOverride
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.PushedHeader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RemovedSlot(val dayId: String, val index: Int, val slot: ProgramSlot)

@Serializable
private data class DayEditorDraft(
    val expected: List<ProgramDayOverride>,
    val changed: Map<String, ProgramDay> = emptyMap(),
    val created: List<Exercise> = emptyList(),
    val removed: List<RemovedSlot> = emptyList()
) {
    fun update(original: ProgramDay, slots: List<ProgramSlot>): DayEditorDraft {
        val next = original.withExercises(slots)
        return copy(changed = if (slots == original.exercises) changed - original.id else changed + (original.id to next))
    }
}

private val draftSaver = Saver<DayEditorDraft, String>(
    save = { Json.encodeToString(it) },
    restore = { Json.decodeFromString<DayEditorDraft>(it) }
)

@Composable
internal fun ProgramDayEditor(
    catalog: ProgramCatalog,
    programId: String,
    initialDayId: String,
    state: JournalSnapshot,
    vm: JournalViewModel,
    onBack: () -> Unit
) {
    var draft by rememberSaveable(programId, stateSaver = draftSaver) {
        val expected = state.programDayOverrides.filter { it.programId == programId && it.catalogVersion == catalog.schemaVersion }
        mutableStateOf(DayEditorDraft(expected))
    }
    val program = remember(catalog, programId, draft.expected) { catalog.personalProgram(programId, draft.expected) }
    var dayId by rememberSaveable(programId) { mutableStateOf(initialDayId) }
    val original = program?.sessions?.find { it.id == dayId }
    if (program == null || original == null) {
        Page(topBar = { PushedHeader(onBack, title = stringResource(R.string.edit_program_days)) }) {
            Text(stringResource(R.string.program_day_unavailable))
        }
        return
    }
    val day = draft.changed[dayId] ?: original
    var picker by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val submissionKey = rememberSaveable { newId() }
    val submissions by vm.submissions.collectAsState()
    val submission = submissions[submissionKey]
    var leaving by remember { mutableStateOf(false) }
    val exitOnce = {
        if (!leaving) {
            leaving = true
            onBack()
        }
    }
    LaunchedEffect(submission) {
        if (submission != null && vm.consumeSubmission(submissionKey, submission)) exitOnce()
    }
    val leave = {
        if (!leaving && vm.submissions.value[submissionKey] == null) {
            if (draft.changed.isEmpty()) exitOnce() else confirmDiscard = true
        }
    }
    val discard = { if (vm.submissions.value[submissionKey] == null) exitOnce() }
    BackHandler(onBack = leave)
    val library = remember(state.exercises, draft.created) { (state.exercises + draft.created).filterNot(Exercise::archived) }
    if (picker) {
        ProgramMovementPicker(catalog, state.programLinks, library, day, { picker = false }) { slot, exercise ->
            val addedIds = occupiedMovementIds(setOf(slot.exerciseId), state.programLinks)
            val removed = draft.removed.filterNot { it.dayId == dayId && it.slot.exerciseId in addedIds }
            draft = draft.update(original, day.exercises + slot).copy(
                created = if (exercise == null) draft.created else draft.created + exercise,
                removed = removed
            )
            picker = false
        }
        return
    }
    val editing = day.exercises.find { it.exerciseId == editingId }
    if (editing != null) {
        val finish = { editingId = null }
        BackHandler(onBack = finish)
        PersonalTargets(editing.name, editing.sets, editing.target, R.string.save, finish) { sets, reps ->
            draft = draft.update(original, day.exercises.map { if (it == editing) it.copy(sets = sets, target = reps) else it })
            finish()
        }
    } else {
        key(dayId) {
            Page(topBar = { PushedHeader(leave, title = stringResource(R.string.edit_program_days)) }) {
                Text(program.name, style = MaterialTheme.typography.titleLarge)
                EditorChoice(stringResource(R.string.editor_day), day.name, program.sessions.map { it.id to it.name }) { dayId = it }
                ProgramDayMovements(
                    day,
                    vm.container.context,
                    enabled = submission == null,
                    onEdit = { editingId = it.exerciseId },
                    onRemove = { index, slot ->
                        draft = draft.update(original, day.exercises - slot).copy(
                            removed = draft.removed + RemovedSlot(dayId, index, slot)
                        )
                    }
                )
                val occupied = occupiedMovementIds(day.exercises.map { it.exerciseId }.toSet(), state.programLinks)
                val lastRemoved = draft.removed.lastOrNull { it.dayId == dayId && it.slot.exerciseId !in occupied }
                if (lastRemoved != null) {
                    Text(stringResource(R.string.editor_removed, lastRemoved.slot.name.exerciseDisplayName(vm.container.context)))
                    OutlineButton(stringResource(R.string.editor_undo), {
                        val slots = day.exercises.toMutableList()
                        slots.add(lastRemoved.index.coerceAtMost(slots.size), lastRemoved.slot)
                        draft = draft.update(original, slots).copy(removed = draft.removed - lastRemoved)
                    }, enabled = submission == null)
                }
                OutlineButton(
                    stringResource(R.string.add_exercise),
                    { picker = true },
                    Modifier.fillMaxWidth(),
                    enabled = submission == null
                )
                val emptyDay = draft.changed.values.firstOrNull { it.exercises.isEmpty() }
                if (emptyDay != null) {
                    Text(emptyDay.name + ": " + stringResource(R.string.editor_day_required), color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.edited_days_future))
                PrimaryButton(
                    stringResource(R.string.save),
                    save@{
                        if (leaving || vm.submissions.value[submissionKey] != null) return@save
                        val changed = draft.changed.values.toList()
                        val ids = changed.flatMap { it.exercises }.mapNotNull { it.localExerciseId }.toSet()
                        val created = draft.created.filter { it.id in ids }
                        val expected = draft.expected
                        vm.submit(submissionKey) {
                            saveProgramDays(catalog, programId, changed, expected, created)
                            programId
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = draft.changed.isNotEmpty() && emptyDay == null && submission == null
                )
                TextButton(onClick = leave, enabled = submission == null) { Text(stringResource(R.string.cancel)) }
            }
        }
        if (confirmDiscard) {
            DiscardProgramChanges(
                onDiscard = discard,
                onContinue = { confirmDiscard = false }
            )
        }
    }
}

@Composable
private fun ProgramDayMovements(
    day: ProgramDay,
    context: Context,
    enabled: Boolean,
    onEdit: (ProgramSlot) -> Unit,
    onRemove: (Int, ProgramSlot) -> Unit
) {
    day.exercises.forEachIndexed { index, slot ->
        Column {
            val name = slot.name.exerciseDisplayName(context)
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(slotPrescription(slot))
            if (slot.localExerciseId != null) {
                TextButton(onClick = { onEdit(slot) }, enabled = enabled) {
                    Text(stringResource(R.string.edit_sets))
                }
            }
            TextButton(onClick = { onRemove(index, slot) }, enabled = enabled) {
                Text(stringResource(R.string.remove_named, name))
            }
        }
    }
}

@Composable
private fun DiscardProgramChanges(onDiscard: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text(stringResource(R.string.editor_discard_question)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.editor_continue)) } }
    )
}

@Composable
internal fun slotPrescription(slot: ProgramSlot): String = stringResource(
    if (slot.target.perSide) R.string.program_slot_per_side else R.string.program_slot,
    slot.sets,
    slot.target.range
)
