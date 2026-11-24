package com.emmah.inshape.feature.assistant

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.emmah.inshape.AppContainer
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.AssistantDraft
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.feature.library.groupTypeLabel
import com.emmah.inshape.feature.library.locationLabel
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.uiMessage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun AssistantImportScreen(
    equipment: Boolean,
    data: JournalSnapshot,
    vm: JournalViewModel,
    onSettings: () -> Unit,
    onSave: (String?, Boolean) -> Unit
) {
    val app = vm.container
    val scope = rememberCoroutineScope()
    val mode = if (equipment) "equipment" else "program"
    val owned = remember { mutableListOf<File>() }
    var capture by remember { mutableStateOf<Pair<Uri, File>?>(null) }
    var sources by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cropped by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    val selectedSources = sources
    val selectedCrops = cropped
    var prepared by remember(selectedSources, selectedCrops) { mutableStateOf<Map<Uri, AssistantImage>>(emptyMap()) }
    var preparing by remember(selectedSources, selectedCrops) { mutableStateOf(selectedSources.isNotEmpty()) }
    var input by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf<AssistantDraft?>(null) }
    var card by remember { mutableStateOf<ImportedCard?>(null) }
    var days by remember { mutableStateOf<List<ImportedDay>>(emptyList()) }
    var uncertainty by remember { mutableStateOf("") }
    var work by remember { mutableStateOf<Job?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var keyRequired by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var chosenCard by remember { mutableStateOf<Exercise?>(null) }
    var reviewExercises by remember { mutableStateOf(emptyList<Exercise>()) }
    val selection = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        if (uris.isNotEmpty()) sources = (sources + uris).distinct().take(4)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        capture?.let { (uri, file) ->
            if (success) {
                sources = (sources + uri).take(4)
            } else {
                file.deleteAssistantTemporaryFile()
            }
        }
        capture = null
    }
    DisposableEffect(owned) { onDispose { owned.forEach { it.deleteAssistantTemporaryFile() } } }
    LaunchedEffect(selectedSources, selectedCrops) {
        try {
            prepared = selectedSources.associateWith { uri -> AssistantImage(app.photos.uploadDerivative(uri, uri in selectedCrops)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            problem = app.context.getString(R.string.prepare_image_failed)
        } finally {
            preparing = false
        }
    }

    fun canExtract(): Boolean = work == null && !preparing && sources == selectedSources && cropped == selectedCrops &&
        prepared.keys.containsAll(selectedSources) && (input.isNotBlank() || selectedSources.isNotEmpty())

    fun extract() {
        if (!canExtract()) return
        val outgoing = input.trim()
        val images = selectedSources.map(prepared::getValue)
        val current = AssistantDraft("import:$mode", newId(), outgoing)
        problem = null
        keyRequired = false
        work = scope.launch {
            try {
                val result = app.extractImport(current, equipment, images)
                if (result == null) {
                    problem = app.context.getString(R.string.journal_changed_restart)
                    return@launch
                }
                draft = current
                reviewExercises = data.exercises.filterNot(Exercise::archived)
                card = result.card
                days = result.days
                uncertainty = result.uncertainty
            } catch (failure: AssistantFailure) {
                problem = app.context.getString(failure.kind.message)
                keyRequired = failure.kind.requiresCredentialSettings
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                problem = app.context.getString(R.string.extract_failed)
            } finally {
                work = null
            }
        }
    }

    fun takePhoto() {
        try {
            val directory = File(app.context.cacheDir, "assistant-camera").apply { mkdirs() }
            val file = File.createTempFile("capture-", ".jpg", directory).also { owned.add(it) }
            val uri = FileProvider.getUriForFile(app.context, "${app.context.packageName}.exports", file)
            capture = uri to file
            camera.launch(uri)
        } catch (_: Exception) {
            problem = app.context.getString(R.string.camera_failed)
        }
    }

    val reviewedCard = card
    val reviewedDays = days
    val reviewing = reviewedCard != null || reviewedDays.isNotEmpty()

    fun save() {
        val command = draft ?: return
        applying = true
        problem = null
        scope.launch {
            try {
                problem = app.saveImport(
                    command, reviewedCard, chosenCard, if (attachments) sources else emptyList(), reviewedDays, reviewExercises, onSave
                )
            } finally {
                applying = false
            }
        }
    }

    Page(Modifier.imePadding(), bottomBar = {
        PrimaryButton(
            stringResource(if (reviewing) R.string.confirm_save else R.string.review_extraction),
            modifier = Modifier.fillMaxWidth(),
            enabled = if (reviewing) !applying else canExtract(),
            onClick = if (reviewing) ::save else ::extract
        )
    }) {
        when {
            reviewedCard != null -> {
                EquipmentReviewForm(
                    reviewedCard,
                    reviewExercises,
                    chosenCard,
                    attachments.takeIf { sources.isNotEmpty() },
                    onEdit = { card = it },
                    onSelect = { chosenCard = it },
                    onToggleAttachments = { attachments = !attachments }
                )
            }

            reviewedDays.isNotEmpty() -> ProgramReviewForm(reviewedDays, reviewExercises, uncertainty) { index, change ->
                days = days.toMutableList().also { it[index] = change }
            }

            else -> ImportSourceForm(
                equipment,
                input,
                sources,
                prepared,
                cropped,
                working = work != null,
                preparing = preparing,
                onInputChange = { input = it.take(20_000) },
                onChooseImages = { selection.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onTakePhoto = ::takePhoto,
                onToggleCrop = { uri -> cropped = cropped.toggleCrop(uri) },
                onRemoveImage = { uri -> sources = sources - uri },
                onCancel = { work?.cancel() }
            )
        }
        if (reviewing) {
            OutlineButton(stringResource(R.string.back_to_source), modifier = Modifier.fillMaxWidth(), enabled = !applying, onClick = {
                card = null
                days = emptyList()
            })
        }
        problem?.let { Text(it) }
        if (keyRequired) {
            OutlineButton(stringResource(R.string.assistant_settings), modifier = Modifier.fillMaxWidth(), onClick = onSettings)
        }
    }
}

private fun Set<Uri>.toggleCrop(uri: Uri): Set<Uri> = if (uri in this) this - uri else this + uri

private data class ImportReview(val card: ImportedCard?, val days: List<ImportedDay>, val uncertainty: String)

private suspend fun AppContainer.extractImport(draft: AssistantDraft, equipment: Boolean, images: List<AssistantImage>): ImportReview? {
    val dao = database.journal()
    dao.put(draft)
    val response = assistant.extract(
        if (equipment) EQUIPMENT_CARD_INSTRUCTIONS else PROGRAM_IMPORT_INSTRUCTIONS,
        draft.input,
        images,
        if (equipment) equipmentCardSchema else programImportSchema
    ).proposal
    if (dao.assistantDraft(draft.key)?.id != draft.id) return null
    return if (equipment) {
        ImportReview(equipmentCard(response), emptyList(), "")
    } else {
        ImportReview(null, parseProgramImport(response), response.text("uncertainties").orEmpty().take(1000))
    }
}

private suspend fun AppContainer.saveImport(
    command: AssistantDraft,
    card: ImportedCard?,
    chosenCard: Exercise?,
    attachments: List<Uri>,
    days: List<ImportedDay>,
    exercises: List<Exercise>,
    onSave: (String?, Boolean) -> Unit
): String? = try {
    if (card != null) {
        onSave(confirmEquipmentCard(command, card, chosenCard, attachments), true)
    } else {
        onSave(repository.confirmProgramImport(command, days, exercises).firstOrNull(), false)
    }
    null
} catch (duplicate: DuplicateCard) {
    onSave(duplicate.exerciseId, true)
    null
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: IllegalArgumentException) {
    failure.uiMessage(context, R.string.review_imported_fields)
} catch (_: Exception) {
    context.getString(R.string.import_save_failed)
}

private fun File.deleteAssistantTemporaryFile() {
    if (!delete() && exists()) Log.w("AssistantImport", "Could not delete temporary camera capture")
}

@Composable
private fun ProgramReviewForm(
    days: List<ImportedDay>,
    exercises: List<Exercise>,
    uncertainty: String,
    onEdit: (Int, ImportedDay) -> Unit
) {
    if (uncertainty.isNotBlank()) Text(uncertainty)
    days.forEachIndexed { dayIndex, day ->
        TextField(day.title, stringResource(R.string.program_day, dayIndex + 1), { text ->
            onEdit(dayIndex, day.copy(title = text.take(120)))
        })
        day.rows.forEachIndexed { rowIndex, row ->
            ProgramRowForm(row, exercises) { change ->
                val rows = day.rows.toMutableList().also { it[rowIndex] = change }
                onEdit(dayIndex, day.copy(rows = rows))
            }
        }
    }
}

@Composable
private fun ImportSourceForm(
    equipment: Boolean,
    input: String,
    sources: List<Uri>,
    prepared: Map<Uri, AssistantImage>,
    cropped: Set<Uri>,
    working: Boolean,
    preparing: Boolean,
    onInputChange: (String) -> Unit,
    onChooseImages: () -> Unit,
    onTakePhoto: () -> Unit,
    onToggleCrop: (Uri) -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    if (!equipment) TextField(input, stringResource(R.string.paste_program_text), onInputChange)
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
        val canAdd = !working && sources.size < 4
        OutlineButton(
            stringResource(R.string.choose_images), modifier = Modifier.weight(1f), enabled = canAdd, onClick = onChooseImages
        )
        OutlineButton(
            stringResource(R.string.take_photo), modifier = Modifier.weight(1f), enabled = canAdd, onClick = onTakePhoto
        )
    }
    sources.forEachIndexed { index, uri ->
        prepared[uri]?.let { ImportPreview(it, index + 1) }
        OutlineButton(
            stringResource(if (uri in cropped) R.string.whole_image else R.string.crop_image, index + 1),
            modifier = Modifier.fillMaxWidth(),
            enabled = !working && !preparing,
            onClick = { onToggleCrop(uri) }
        )
        OutlineButton(
            stringResource(R.string.remove_image, index + 1),
            modifier = Modifier.fillMaxWidth(),
            enabled = !working,
            onClick = { onRemoveImage(uri) }
        )
    }
    if (working) OutlineButton(stringResource(R.string.cancel_request), modifier = Modifier.fillMaxWidth(), onClick = onCancel)
}

@Composable
private fun ImportPreview(derivative: AssistantImage, number: Int) {
    val bitmap = remember(derivative) { BitmapFactory.decodeByteArray(derivative.bytes, 0, derivative.bytes.size) } ?: return
    DisposableEffect(bitmap) { onDispose { bitmap.recycle() } }
    Image(bitmap.asImageBitmap(), stringResource(R.string.selected_image, number), Modifier.height(Dimens.exercisePhotoArtworkWidth))
}

@Composable
private fun EquipmentReviewForm(
    card: ImportedCard,
    exercises: List<Exercise>,
    existing: Exercise?,
    attachments: Boolean?,
    onEdit: (ImportedCard) -> Unit,
    onSelect: (Exercise?) -> Unit,
    onToggleAttachments: () -> Unit
) {
    TextField(card.name, stringResource(R.string.exercise_name), { onEdit(card.copy(name = it.take(120))) })
    TextField(card.source, stringResource(R.string.source_text), { onEdit(card.copy(source = it.take(6000))) })
    TextField(card.translation, stringResource(R.string.finnish_translation), { onEdit(card.copy(translation = it.take(6000))) })
    TextField(card.uncertainty, stringResource(R.string.unresolved_text), { onEdit(card.copy(uncertainty = it.take(1000))) })
    if (card.imageIndices.isNotBlank()) Text(stringResource(R.string.source_images, card.imageIndices))
    ImportExercisePicker(
        stringResource(R.string.save_to_exercise, existing?.name ?: stringResource(R.string.new_exercise)),
        exercises,
        false
    ) { id ->
        onSelect(exercises.find { it.id == id })
    }
    if (attachments != null) {
        OutlineButton(
            stringResource(if (attachments) R.string.attach_originals else R.string.keep_text_only),
            modifier = Modifier.fillMaxWidth(),
            onClick = onToggleAttachments
        )
    }
}

@Composable
private fun ProgramRowForm(row: ImportedRow, exercises: List<Exercise>, onEdit: (ImportedRow) -> Unit) {
    val selected = exercises.find { it.id == row.selection }
    val label = when (row.selection) {
        CREATE_IMPORT_EXERCISE -> stringResource(R.string.create_new_exercise)
        EXCLUDE_IMPORT_EXERCISE -> stringResource(R.string.exclude_row)
        else -> selected?.name ?: stringResource(R.string.choose_library_exercise)
    }
    if (row.selection == EXCLUDE_IMPORT_EXERCISE) {
        Text(row.name, style = MaterialTheme.typography.titleMedium)
    } else {
        TextField(row.name, stringResource(R.string.exercise_name), { onEdit(row.copy(name = it.take(120))) })
        if (row.sourceExcerpt.isNotBlank()) Text(row.sourceExcerpt)
        TextField(row.equipment, stringResource(R.string.equipment), { onEdit(row.copy(equipment = it.take(120))) })
        val resources = LocalResources.current
        val units = MeasurementBasis.entries.associate { it.key to resources.getString(it.optionLabel) }
        TextField(units[row.basis] ?: row.basis, stringResource(R.string.measurement_unit), { value ->
            val key = units.entries.firstOrNull { it.value.equals(value.trim(), ignoreCase = true) }?.key
            onEdit(row.copy(basis = (key ?: value).take(40)))
        })
        NumberField(row.sets, { onEdit(row.copy(sets = it)) }, stringResource(R.string.sets))
        NumberField(row.min, { onEdit(row.copy(min = it)) }, stringResource(R.string.minimum_reps))
        NumberField(row.max, { onEdit(row.copy(max = it)) }, stringResource(R.string.maximum_reps))
        OutlineButton(if (row.perSide) stringResource(R.string.per_side) else stringResource(R.string.total_reps), {
            onEdit(row.copy(perSide = !row.perSide))
        })
        NumberField(row.load, { onEdit(row.copy(load = it)) }, stringResource(R.string.load), decimal = true)
        row.roundTargets.forEachIndexed { index, round ->
            fun update(value: ImportedRound) {
                onEdit(row.copy(roundTargets = row.roundTargets.toMutableList().also { it[index] = value }))
            }
            Text(stringResource(R.string.set_or_round, index + 1), style = MaterialTheme.typography.titleMedium)
            NumberField(round.min, { update(round.copy(min = it)) }, stringResource(R.string.minimum_reps))
            NumberField(round.max, { update(round.copy(max = it)) }, stringResource(R.string.maximum_reps))
            NumberField(round.load, { update(round.copy(load = it)) }, stringResource(R.string.load), decimal = true)
            OutlineButton(if (round.perSide) stringResource(R.string.per_side) else stringResource(R.string.total_reps), {
                update(round.copy(perSide = !round.perSide))
            })
        }
        NumberField(row.duration, { onEdit(row.copy(duration = it)) }, stringResource(R.string.duration_seconds))
        NumberField(row.rest, { onEdit(row.copy(rest = it)) }, stringResource(R.string.rest_seconds))
        TextField(row.group, stringResource(R.string.group_identifier), { onEdit(row.copy(group = it.take(120))) })
        if (row.group.isNotBlank()) {
            TextField(row.groupType.groupTypeLabel(LocalContext.current), stringResource(R.string.group_type), {
                onEdit(row.copy(groupType = it.take(120)))
            })
            NumberField(row.rounds, { onEdit(row.copy(rounds = it)) }, stringResource(R.string.rounds))
        }
        TextField(row.note, stringResource(R.string.notes), { onEdit(row.copy(note = it.take(1000))) })
    }
    ImportExercisePicker(label, exercises, true) { onEdit(row.copy(selection = it)) }
}

@Composable
private fun ImportExercisePicker(label: String, exercises: List<Exercise>, exclusion: Boolean, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlineButton(label, { open = !open })
    if (open) {
        ListCard {
            item {
                ListRow(stringResource(R.string.create_new_exercise), onClick = {
                    onSelect(CREATE_IMPORT_EXERCISE)
                    open = false
                })
            }
            if (exclusion) {
                item {
                    ListRow(stringResource(R.string.exclude_row), onClick = {
                        onSelect(EXCLUDE_IMPORT_EXERCISE)
                        open = false
                    })
                }
            }
            exercises.forEach { exercise ->
                item {
                    ListRow(
                        exercise.name.exerciseDisplayName(LocalContext.current),
                        subtitle = listOf(
                            exercise.equipmentName.exerciseDisplayName(LocalContext.current),
                            exercise.location.locationLabel(LocalContext.current)
                        ).filter(String::isNotBlank).joinToString(", "),
                        onClick = {
                            onSelect(exercise.id)
                            open = false
                        }
                    )
                }
            }
        }
    }
}
