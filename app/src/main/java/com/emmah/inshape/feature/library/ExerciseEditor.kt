package com.emmah.inshape.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.*
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import kotlinx.serialization.json.Json

@Composable
fun ExerciseEditor(id: String, data: JournalSnapshot, vm: JournalViewModel, onSaved: (String) -> Unit) {
    var exercise by remember(id) { mutableStateOf(
        data.drafts.find { it.id == "exercise:$id" }?.let { runCatching { Json.decodeFromString<Exercise>(it.payload) }.getOrNull() }
            ?: data.exercises.find { it.id == id } ?: Exercise(id = if (id == "new") newId() else id)
    ) }
    fun edit(value: Exercise) { exercise = value; vm.saveDraft("exercise:$id", "exercise", Json.encodeToString(value)) }
    var archive by remember { mutableStateOf(false) }
    Page(bottomBar = { PrimaryButton("Save exercise", { vm.write { saveExercise(exercise); deleteEditorDraft("exercise:$id"); onSaved(exercise.id) } }, Modifier.fillMaxWidth().heightIn(min = Dimens.button)) }) {
        GroupLabel("Basics")
        InShapeCard {
        TextField(exercise.name, "Exercise name", { edit(exercise.copy(name = it)) }, true)
        TextField(exercise.nickname, "Nickname (optional)", { edit(exercise.copy(nickname = it)) }, true)
        SelectField("Type", exercise.category, exerciseCategories, { edit(exercise.copy(category = it)) })
        TextField(exercise.muscles, "Muscles or labels", { edit(exercise.copy(muscles = it)) })
        }
        GroupLabel("Where")
        InShapeCard {
        TextField(exercise.location, "Location", { edit(exercise.copy(location = it)) }, true)
        TextField(exercise.equipmentName, "Recognizable machine name", { edit(exercise.copy(equipmentName = it)) }, true)
        SelectField("Measurement", exercise.basis, measurementBases, { edit(exercise.copy(basis = it)) })
        }
        Text("Use a separate exercise for a different machine or measurement basis.", style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.textSecondary)
        GroupLabel("Machine settings")
        InShapeCard {
            exercise.settings.forEachIndexed { index, setting ->
                TextField(setting.label, "Setting label", { text -> edit(exercise.copy(settings = exercise.settings.toMutableList().also { it[index] = setting.copy(label = text) })) }, true)
                TextField(setting.value, "Setting value", { text -> edit(exercise.copy(settings = exercise.settings.toMutableList().also { it[index] = setting.copy(value = text) })) }, true)
                TextAction("Remove setting", { edit(exercise.copy(settings = exercise.settings.filterIndexed { i, _ -> i != index })) })
            }
            TextAction("Add setting", { edit(exercise.copy(settings = exercise.settings + Setting("", ""))) }, icon = Icons.Outlined.Add)
        }
        GroupLabel("Notes")
        InShapeCard {
        TextField(exercise.instructions, "My instructions", { edit(exercise.copy(instructions = it)) })
        TextField(exercise.notes, "Personal notes", { edit(exercise.copy(notes = it)) })
        }
        GroupLabel("Links")
        InShapeCard {
            exercise.links.forEachIndexed { index, link ->
                TextField(link.label, "Link label", { text -> edit(exercise.copy(links = exercise.links.toMutableList().also { it[index] = link.copy(label = text) })) })
                TextField(link.url, "HTTP or HTTPS address", { text -> edit(exercise.copy(links = exercise.links.toMutableList().also { it[index] = link.copy(url = text) })) })
                TextAction("Remove link", { edit(exercise.copy(links = exercise.links.filterIndexed { i, _ -> i != index })) })
            }
            TextAction("Add link", { edit(exercise.copy(links = exercise.links + SavedLink("", ""))) }, icon = Icons.Outlined.Add)
        }
        GroupLabel("Alternatives")
        InShapeCard {
            data.exercises.filter { it.id != exercise.id && !it.archived }.forEach { option ->
                Row { Checkbox(option.id in exercise.alternatives, { checked -> edit(exercise.copy(alternatives = if (checked) exercise.alternatives + option.id else exercise.alternatives - option.id)) }); Text(option.name) }
            }
            if (data.exercises.none { it.id != exercise.id && !it.archived }) Text("Create another exercise to choose it as an alternative.")
        }
        Text("Save this exercise, then add photos on its details page.", style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.textSecondary)
        if (id != "new") TextAction("Archive exercise", { archive = true }, color = InShapeTheme.colors.error)
    }
    if (archive) ConfirmDialog("Archive exercise?", "Historical records and photos are kept. This exercise will no longer appear in new selections.", "Archive", {
        vm.write { archiveExercise(id); deleteEditorDraft("exercise:$id"); onSaved(id) }; archive = false
    }, { archive = false })
}
