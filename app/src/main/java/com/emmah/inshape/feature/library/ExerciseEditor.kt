package com.emmah.inshape.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.SavedLink
import com.emmah.inshape.data.Setting
import com.emmah.inshape.data.newId
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.GroupLabel
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.serialization.json.Json

@Composable
fun ExerciseEditor(id: String, data: JournalSnapshot, vm: JournalViewModel, onSave: (String) -> Unit, modifier: Modifier = Modifier) {
    var exercise by remember(id) {
        mutableStateOf(
            data.drafts.find {
                it.id == "exercise:$id"
            }?.let { runCatching { Json.decodeFromString<Exercise>(it.payload) }.getOrNull() }
                ?: data.exercises.find { it.id == id }
                ?: Exercise(id = if (id == "new") newId() else id)
        )
    }
    fun edit(value: Exercise) {
        exercise = value
        vm.saveDraft("exercise:$id", "exercise", Json.encodeToString(value))
    }
    var more by remember(id) { mutableStateOf(false) }
    var archive by remember { mutableStateOf(false) }
    var showNameError by remember(id) { mutableStateOf(false) }
    Page(modifier, bottomBar = {
        PrimaryButton("Save exercise", {
            if (exercise.name.isBlank()) {
                showNameError = true
            } else {
                vm.write {
                    saveExercise(exercise)
                    deleteEditorDraft("exercise:$id")
                    onSave(exercise.id)
                }
            }
        }, Modifier.fillMaxWidth().heightIn(min = Dimens.button))
    }) {
        TextField(
            exercise.name,
            "Exercise name",
            { edit(exercise.copy(name = it)) },
            singleLine = true,
            error = "Enter an exercise name".takeIf { showNameError && exercise.name.isBlank() }
        )
        GroupLabel("Machine settings")
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.itemGap)
        ) {
            exercise.settings.forEachIndexed { index, setting ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
                ) {
                    TextField(setting.label, "Label", { text ->
                        edit(exercise.copy(settings = exercise.settings.toMutableList().also { it[index] = setting.copy(label = text) }))
                    }, Modifier.weight(1f), singleLine = true)
                    TextField(setting.value, "Value", { text ->
                        edit(exercise.copy(settings = exercise.settings.toMutableList().also { it[index] = setting.copy(value = text) }))
                    }, Modifier.weight(1f), singleLine = true)
                    IconButton({
                        edit(exercise.copy(settings = exercise.settings.filterIndexed { i, _ -> i != index }))
                    }) { Icon(Icons.Outlined.Close, "Remove setting ${index + 1}") }
                }
            }
            TextAction("Add setting", {
                edit(
                    exercise.copy(
                        settings =
                            exercise.settings + Setting("", "")
                    )
                )
            }, icon = Icons.Outlined.Add)
        }
        SelectField("Measurement", exercise.basis, measurementBases, { edit(exercise.copy(basis = it)) })
        Text(
            "Use a separate exercise for a different machine or measurement basis.",
            style = MaterialTheme.typography.bodyMedium,
            color = InShapeTheme.colors.textSecondary
        )
        com.emmah.inshape.ui.components.ListRow(
            "More details",
            subtitle = listOfNotNull(
                exercise.category.takeIf { it.isNotBlank() },
                exercise.location.takeIf { it.isNotBlank() },
                exercise.links.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.link_count, it, it) }
            ).joinToString(", "),
            onClick = { more = !more }
        )
        if (more) {
            TextField(exercise.nickname, "Nickname (optional)", { edit(exercise.copy(nickname = it)) }, singleLine = true)
            SelectField("Type", exercise.category, exerciseCategories, { edit(exercise.copy(category = it)) })
            TextField(exercise.muscles, "Muscles or labels", { edit(exercise.copy(muscles = it)) })
            TextField(exercise.location, "Location", { edit(exercise.copy(location = it)) }, singleLine = true)
            TextField(exercise.equipmentName, "Recognizable machine name", { edit(exercise.copy(equipmentName = it)) }, singleLine = true)
            GroupLabel("Notes")
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.itemGap)
            ) {
                TextField(exercise.instructions, "My instructions", {
                    edit(exercise.copy(instructions = it))
                })
                TextField(exercise.notes, "Personal notes", { edit(exercise.copy(notes = it)) })
            }
            GroupLabel("Links")
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.itemGap)
            ) {
                exercise.links.forEachIndexed { index, link ->
                    TextField(link.label, "Link label", { text ->
                        edit(
                            exercise.copy(
                                links = exercise.links.toMutableList().also {
                                    it[index] =
                                        link.copy(label = text)
                                }
                            )
                        )
                    })
                    TextField(link.url, "Web address", { text ->
                        edit(
                            exercise.copy(
                                links = exercise.links.toMutableList().also {
                                    it[index] =
                                        link.copy(url = text)
                                }
                            )
                        )
                    })
                    TextAction("Remove link", {
                        edit(
                            exercise.copy(
                                links = exercise.links.filterIndexed { i, _ ->
                                    i !=
                                        index
                                }
                            )
                        )
                    })
                }
                TextAction("Add link", {
                    edit(exercise.copy(links = exercise.links + SavedLink("", "")))
                }, icon = Icons.Outlined.Add)
            }
            GroupLabel("Alternatives")
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.itemGap)
            ) {
                data.exercises.filter { it.id != exercise.id && !it.archived }.forEach { option ->
                    Row(Modifier.heightIn(min = Dimens.rowMin), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(option.id in exercise.alternatives, { checked ->
                            edit(
                                exercise.copy(
                                    alternatives = if (checked) {
                                        exercise.alternatives +
                                            option.id
                                    } else {
                                        exercise.alternatives - option.id
                                    }
                                )
                            )
                        })
                        Text(option.name)
                    }
                }
                if (data.exercises.none {
                        it.id != exercise.id && !it.archived
                    }
                ) {
                    Text("Create another exercise to choose it as an alternative.")
                }
            }
        }
        Text(
            "Save this exercise, then add photos on its details page.",
            style = MaterialTheme.typography.bodySmall,
            color = InShapeTheme.colors.textSecondary
        )
        if (id !=
            "new"
        ) {
            TextAction("Archive exercise", {
                archive = true
            }, color = InShapeTheme.colors.error)
        }
    }
    if (archive) {
        ConfirmDialog(
            "Archive exercise?",
            "Historical records and photos are kept. This exercise will no longer appear in new selections.",
            "Archive",
            {
                vm.write {
                    archiveExercise(id)
                    deleteEditorDraft("exercise:$id")
                    onSave(id)
                }
                archive =
                    false
            },
            { archive = false }
        )
    }
}
