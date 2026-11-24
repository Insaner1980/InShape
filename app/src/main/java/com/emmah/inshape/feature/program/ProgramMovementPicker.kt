package com.emmah.inshape.feature.program

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.ProgramExerciseLink
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.components.TextFieldKeyboard

internal fun occupiedMovementIds(slotIds: Set<String>, links: List<ProgramExerciseLink>): Set<String> = buildSet {
    addAll(slotIds)
    for (link in links) {
        val localId = "local_${link.exerciseId}"
        if (localId in slotIds) add(link.catalogExerciseId)
        if (link.catalogExerciseId in slotIds) add(localId)
    }
}

@Composable
internal fun EditorChoice(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlineButton(selected, { expanded = true }, Modifier.fillMaxWidth())
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { (id, title) ->
                    DropdownMenuItem(text = { Text(title) }, onClick = {
                        expanded = false
                        onSelect(id)
                    })
                }
            }
        }
    }
}

@Composable
internal fun ProgramMovementPicker(
    catalog: ProgramCatalog,
    links: List<ProgramExerciseLink>,
    library: List<Exercise>,
    day: ProgramDay,
    onBack: () -> Unit,
    onAdd: (ProgramSlot, Exercise?) -> Unit
) {
    val context = LocalContext.current
    val all = stringResource(R.string.all)
    val libraryTitle = stringResource(R.string.library)
    var query by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var newName by rememberSaveable { mutableStateOf<String?>(null) }
    var existingId by rememberSaveable { mutableStateOf<String?>(null) }
    val existing = library.find { it.id == existingId }
    val name = newName ?: existing?.name?.exerciseDisplayName(context)
    if (name != null) {
        val leaveTargets = {
            existingId = null
            newName = null
        }
        BackHandler(onBack = leaveTargets)
        PersonalTargets(name, 2, ProgramReps(8, 12, false), R.string.add_exercise, leaveTargets) { sets, reps ->
            val exercise = existing ?: Exercise(name = name)
            val slot = personalProgramSlot(exercise, sets, reps).copy(name = name)
            onAdd(slot, exercise.takeIf { existing == null })
        }
        return
    }
    BackHandler(onBack = onBack)
    val options = listOf("" to all, "library" to libraryTitle) + catalog.programs.map { it.id to it.name }
    val text = query.trim()
    val occupied = remember(day, links) { occupiedMovementIds(day.exercises.map { it.exerciseId }.toSet(), links) }
    val allowCreation = text.isNotEmpty() &&
        library.none { it.name.exerciseDisplayName(context).equals(text, true) } &&
        catalog.programs.none { program -> program.sessions.any { session -> session.exercises.any { it.name.equals(text, true) } } }
    Page(topBar = { PushedHeader(onBack, title = stringResource(R.string.add_exercise)) }) {
        TextField(query, stringResource(R.string.search_exercises), { query = it }, singleLine = true)
        EditorChoice(stringResource(R.string.editor_source), options.first { it.first == source }.second, options) { source = it }
        if (allowCreation) {
            OutlineButton(stringResource(R.string.editor_create_named, text), { newName = text }, Modifier.fillMaxWidth())
        }
        var hasMatches = false
        if (source.isEmpty() || source == "library") {
            for (exercise in library) {
                val displayName = exercise.name.exerciseDisplayName(context)
                if (displayName.contains(text, true)) {
                    hasMatches = true
                    PickerResult(displayName, libraryTitle, "local_${exercise.id}" !in occupied) {
                        existingId = exercise.id
                    }
                }
            }
        }
        for (program in catalog.programs.filter { source.isEmpty() || it.id == source }) {
            for (session in program.sessions) {
                for (slot in session.exercises.filter { it.name.contains(text, true) }) {
                    hasMatches = true
                    PickerResult(
                        slot.name,
                        program.name + "\n" + session.name + "\n" + slotPrescription(slot),
                        slot.exerciseId !in occupied
                    ) { onAdd(slot, null) }
                }
            }
        }
        if (!hasMatches && !allowCreation) Text(stringResource(R.string.no_matching_exercises))
    }
}

@Composable
private fun PickerResult(name: String, detail: String, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 8.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(detail)
        if (!enabled) Text(stringResource(R.string.already_added))
    }
}

@Composable
internal fun PersonalTargets(
    name: String,
    initialSets: Int,
    initialReps: ProgramReps,
    action: Int,
    onBack: () -> Unit,
    onConfirm: (Int, ProgramReps) -> Unit
) {
    var sets by rememberSaveable { mutableStateOf(initialSets.toString()) }
    var min by rememberSaveable { mutableStateOf(initialReps.min.toString()) }
    var max by rememberSaveable { mutableStateOf(initialReps.max.toString()) }
    val validSets = sets.toIntOrNull()?.takeIf { it in 1..MAX_PROGRAM_SETS }
    val validMin = min.toIntOrNull()?.takeIf { it > 0 }
    val validMax = max.toIntOrNull()?.takeIf { validMin != null && it >= validMin }
    val numeric = TextFieldKeyboard(KeyboardOptions(keyboardType = KeyboardType.Number))
    Page(topBar = { PushedHeader(onBack, title = stringResource(R.string.edit_sets)) }) {
        Text(name, style = MaterialTheme.typography.titleLarge)
        TextField(sets, stringResource(R.string.sets), { sets = it }, singleLine = true, keyboard = numeric)
        TextField(min, stringResource(R.string.minimum_reps), { min = it }, singleLine = true, keyboard = numeric)
        TextField(max, stringResource(R.string.maximum_reps), { max = it }, singleLine = true, keyboard = numeric)
        if (validSets == null || validMin == null || validMax == null) {
            Text(stringResource(R.string.editor_bad_targets), color = MaterialTheme.colorScheme.error)
        }
        PrimaryButton(stringResource(action), {
            if (validSets != null && validMin != null && validMax != null) {
                onConfirm(validSets, ProgramReps(validMin, validMax, initialReps.perSide))
            }
        }, Modifier.fillMaxWidth(), enabled = validSets != null && validMin != null && validMax != null)
    }
}
