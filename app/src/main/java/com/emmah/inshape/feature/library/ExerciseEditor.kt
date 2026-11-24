package com.emmah.inshape.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.JournalSubmission
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.SavedLink
import com.emmah.inshape.data.Setting
import com.emmah.inshape.data.newId
import com.emmah.inshape.data.validExerciseLink
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.GroupLabel
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditor(
    id: String,
    data: JournalSnapshot,
    vm: JournalViewModel,
    onCreate: (String) -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val creating = id == "new"
    val key = "exercise:$id"
    val origin = rememberSaveable(id) { newId() }
    val submissions by vm.submissions.collectAsStateWithLifecycle()
    val submission = submissions[key]
    val saving = submission?.result == null && submission != null
    NotifyEditorCreation(vm, key, origin, submission, onCreate) { result ->
        data.exercises.any { it.id == result }
    }
    var exercise by remember(id) {
        val stored = data.drafts.find {
            it.id == "exercise:$id"
        }?.let { runCatching { Json.decodeFromString<Exercise>(it.payload) }.getOrNull() }
            ?: data.exercises.find { it.id == id }
            ?: Exercise(id = if (id == "new") newId() else id)
        mutableStateOf(
            if (stored.notes.isBlank()) {
                stored
            } else {
                stored.copy(
                    instructions = listOf(stored.instructions, stored.notes).filter(String::isNotBlank).joinToString("\n\n"),
                    notes = ""
                )
            }
        )
    }
    fun edit(value: Exercise) {
        if (saving) return
        exercise = value
        vm.saveDraft(key, "exercise", Json.encodeToString(value))
        if (!creating && value.name.isNotBlank() && value.links.all { validExerciseLink(it.url) }) {
            vm.write { saveExercise(value, draftId = key, preserveFavorite = true) }
        }
    }
    var archive by remember { mutableStateOf(false) }
    var picker by rememberSaveable(id) { mutableStateOf(false) }
    Page(
        modifier,
        bottomBar = if (creating) {
            {
                PrimaryButton(stringResource(R.string.create_exercise), {
                    val submitted = exercise
                    vm.submit(key, origin) { saveExercise(submitted, draftId = key, preserveFavorite = true) }
                }, Modifier.fillMaxWidth().heightIn(min = Dimens.button), enabled = exercise.name.isNotBlank() && !saving)
            }
        } else {
            null
        }
    ) {
        TextField(
            exercise.name,
            stringResource(R.string.exercise_name),
            { edit(exercise.copy(name = it)) },
            singleLine = true,
            error = stringResource(R.string.exercise_name_required).takeIf { !creating && exercise.name.isBlank() }
        )
        val context = LocalContext.current
        val resources = LocalResources.current
        SelectField(
            stringResource(R.string.type),
            exercise.category,
            exerciseCategories,
            { edit(exercise.copy(category = it)) },
            optionLabel = { it.categoryLabel(context) }
        )
        SelectField(
            stringResource(R.string.measurement),
            exercise.basis,
            MeasurementBasis.entries.map { it.key },
            { edit(exercise.copy(basis = it)) },
            optionLabel = { resources.getString(MeasurementBasis.fromKey(it).optionLabel) }
        )
        GroupLabel(stringResource(R.string.machine_settings))
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.itemGap)
        ) {
            exercise.settings.forEachIndexed { index, setting ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
                ) {
                    TextField(
                        setting.label,
                        stringResource(R.string.setting_number_name, index + 1),
                        { text ->
                            edit(
                                exercise.copy(
                                    settings = exercise.settings.toMutableList().also {
                                        it[index] = setting.copy(label = text)
                                    }
                                )
                            )
                        },
                        Modifier.weight(1f),
                        singleLine = true
                    )
                    TextField(
                        setting.value,
                        stringResource(R.string.setting_number_value, index + 1),
                        { text ->
                            edit(
                                exercise.copy(
                                    settings = exercise.settings.toMutableList().also {
                                        it[index] = setting.copy(value = text)
                                    }
                                )
                            )
                        },
                        Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton({
                        edit(exercise.copy(settings = exercise.settings.filterIndexed { i, _ -> i != index }))
                    }) { Icon(Icons.Outlined.Close, stringResource(R.string.remove_setting_number, index + 1)) }
                }
            }
            TextAction(stringResource(R.string.add_setting), {
                edit(
                    exercise.copy(
                        settings =
                            exercise.settings + Setting("", "")
                    )
                )
            }, icon = Icons.Outlined.Add)
        }
        TextField(exercise.instructions, stringResource(R.string.notes), { edit(exercise.copy(instructions = it)) })
        TextField(exercise.muscles, stringResource(R.string.muscles_or_labels), { edit(exercise.copy(muscles = it)) })
        TextField(exercise.location.locationLabel(context), stringResource(R.string.location), {
            edit(exercise.copy(location = it))
        }, singleLine = true)
        GroupLabel(stringResource(R.string.links))
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.itemGap)
        ) {
            exercise.links.forEachIndexed { index, link ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(link.label, stringResource(R.string.link_label), { text ->
                        edit(exercise.copy(links = exercise.links.toMutableList().also { it[index] = link.copy(label = text) }))
                    }, modifier = Modifier.weight(1f), singleLine = true)
                    IconButton({
                        edit(exercise.copy(links = exercise.links.filterIndexed { i, _ -> i != index }))
                    }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.remove_link))
                    }
                }
                TextField(link.url, stringResource(R.string.web_address), { text ->
                    edit(
                        exercise.copy(
                            links = exercise.links.toMutableList().also {
                                it[index] =
                                    link.copy(url = text)
                            }
                        )
                    )
                }, error = stringResource(R.string.invalid_web_address).takeIf { link.url.isNotBlank() && !validExerciseLink(link.url) })
            }
            TextAction(stringResource(R.string.add_link), {
                edit(exercise.copy(links = exercise.links + SavedLink("", "")))
            }, icon = Icons.Outlined.Add)
        }
        GroupLabel(stringResource(R.string.alternatives))
        ListCard {
            exercise.alternatives.forEach { optionId ->
                val option = data.exercises.find { it.id == optionId } ?: return@forEach
                item {
                    ListRow(option.name, trailing = {
                        IconButton({ edit(exercise.copy(alternatives = exercise.alternatives - optionId)) }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.remove_named_alternative, option.name))
                        }
                    })
                }
            }
        }
        TextAction(stringResource(R.string.add_alternative), { picker = true }, icon = Icons.Outlined.Add)
        if (id !=
            "new"
        ) {
            TextAction(stringResource(R.string.archive_exercise), {
                archive = true
            }, color = InShapeTheme.colors.error, icon = Icons.Outlined.Delete)
        }
    }
    if (picker) {
        ModalBottomSheet(onDismissRequest = { picker = false }, containerColor = InShapeTheme.colors.background) {
            Page {
                SectionHeader(stringResource(R.string.add_alternative))
                ExercisePickerList(data, vm.container.photos, { alternative ->
                    edit(exercise.copy(alternatives = exercise.alternatives + alternative))
                    picker = false
                }, excluded = exercise.alternatives.toSet() + exercise.id)
            }
        }
    }
    if (archive) {
        ConfirmDialog(
            stringResource(R.string.archive_exercise_question),
            stringResource(R.string.archive_exercise_message),
            stringResource(R.string.archive),
            destructive = true,
            {
                vm.write {
                    archiveExercise(id)
                    deleteEditorDraft("exercise:$id")
                    onArchive()
                }
                archive =
                    false
            },
            { archive = false }
        )
    }
}

@Composable
internal fun NotifyEditorCreation(
    vm: JournalViewModel,
    key: String,
    origin: String,
    submission: JournalSubmission?,
    onCreate: (String) -> Unit,
    isVisible: (String) -> Boolean
) {
    val notifyCreated by rememberUpdatedState(onCreate)
    val createdId = submission?.result?.takeIf(isVisible)
    LaunchedEffect(submission, createdId) {
        if (submission == null || createdId == null) return@LaunchedEffect
        if (vm.consumeSubmission(key, submission) && submission.origin == origin) {
            notifyCreated(createdId)
        }
    }
}
