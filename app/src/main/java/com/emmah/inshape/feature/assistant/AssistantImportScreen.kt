package com.emmah.inshape.feature.assistant

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.feature.library.locationLabel
import com.emmah.inshape.feature.library.groupTypeLabel
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.ui.uiMessage
import com.emmah.inshape.feature.library.exerciseDisplayName
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.core.content.FileProvider
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.AssistantDraft
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.newId
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.TextField
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
    onSaved: (String?, Boolean) -> Unit
) {
    val app = vm.container
    val scope = rememberCoroutineScope()
    val mode = if (equipment) "equipment" else "program"
    val owned = remember { mutableListOf<File>() }
    var capture by remember { mutableStateOf<Pair<Uri, File>?>(null) }
    var sources by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cropped by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var prepared by remember { mutableStateOf<Map<Uri, AssistantImage>>(emptyMap()) }
    var preparing by remember { mutableStateOf(false) }
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
            if (success) sources = (sources + uri).take(4)
            else file.delete()
        }
        capture = null
    }
    DisposableEffect(owned) { onDispose { owned.forEach { it.delete() } } }
    LaunchedEffect(sources, cropped) {
        preparing = true
        try {
            prepared = sources.associateWith { uri -> AssistantImage(app.photos.uploadDerivative(uri, uri in cropped)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            problem = app.context.getString(R.string.prepare_image_failed)
        } finally {
            preparing = false
        }
    }

    fun extract() {
        val outgoing = input.trim()
        val images = sources.mapNotNull(prepared::get)
        val current = AssistantDraft("import:$mode", newId(), outgoing)
        problem = null
        keyRequired = false
        work = scope.launch {
            try {
                app.database.journal().put(current)
                draft = current
                val response = app.assistant.extract(
                    if (equipment) EQUIPMENT_CARD_INSTRUCTIONS else PROGRAM_IMPORT_INSTRUCTIONS,
                    outgoing, images,
                    if (equipment) equipmentCardSchema else programImportSchema
                ).proposal
                if (app.database.journal().assistantDraft(current.key)?.id != current.id) {
                    problem = app.context.getString(R.string.journal_changed_restart)
                } else {
                    reviewExercises = data.exercises.filterNot(Exercise::archived)
                    if (equipment) card = equipmentCard(response)
                    else {
                        days = parseProgramImport(response)
                        uncertainty = response.text("uncertainties").orEmpty().take(1000)
                    }
                }
            } catch (failure: AssistantFailure) {
                problem = app.context.getString(failure.kind.message)
                keyRequired = failure.kind in setOf(AssistantFailureKind.NO_KEY, AssistantFailureKind.UNREADABLE_KEY)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                problem = app.context.getString(R.string.extract_failed)
            } finally {
                work = null
            }
        }
    }

    val reviewedCard = card
    Page(Modifier.imePadding(), bottomBar = {
        if (reviewedCard != null || days.isNotEmpty()) {
            val reviewedDays = days
            PrimaryButton(stringResource(R.string.confirm_save), modifier = Modifier.fillMaxWidth(), enabled = !applying, onClick = {
                val command = draft ?: return@PrimaryButton
                applying = true
                problem = null
                scope.launch {
                    try {
                        if (reviewedCard != null) {
                            val id = app.confirmEquipmentCard(command, reviewedCard, chosenCard, if (attachments) sources else emptyList())
                            onSaved(id, true)
                        } else {
                            val ids = app.repository.confirmProgramImport(command, reviewedDays, reviewExercises)
                            onSaved(ids.firstOrNull(), false)
                        }
                    } catch (duplicate: DuplicateCard) {
                        onSaved(duplicate.exerciseId, true)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: IllegalArgumentException) {
                        problem = failure.uiMessage(app.context, R.string.review_imported_fields)
                    } catch (_: Exception) {
                        problem = app.context.getString(R.string.import_save_failed)
                    } finally {
                        applying = false
                    }
                }
            })
        } else {
            PrimaryButton(
                stringResource(R.string.review_extraction), modifier = Modifier.fillMaxWidth(),
                enabled = work == null && !preparing && prepared.keys.containsAll(sources) && (input.isNotBlank() || sources.isNotEmpty()),
                onClick = ::extract
            )
        }
    }) {
        if (reviewedCard != null) {
            EquipmentReviewForm(reviewedCard, reviewExercises, chosenCard, { card = it }, { chosenCard = it })
            if (sources.isNotEmpty()) OutlineButton(
                if (attachments) stringResource(R.string.attach_originals) else stringResource(R.string.keep_text_only), modifier = Modifier.fillMaxWidth(), onClick = { attachments = !attachments }
            )
        } else if (days.isNotEmpty()) {
            if (uncertainty.isNotBlank()) Text(uncertainty)
            days.forEachIndexed { dayIndex, day ->
                TextField(day.title, stringResource(R.string.program_day, dayIndex + 1), { text ->
                    days = days.toMutableList().also { it[dayIndex] = day.copy(title = text.take(120)) }
                })
                day.rows.forEachIndexed { rowIndex, row ->
                    ProgramRowForm(row, reviewExercises) { change ->
                        val rows = day.rows.toMutableList().also { it[rowIndex] = change }
                        days = days.toMutableList().also { it[dayIndex] = day.copy(rows = rows) }
                    }
                }
            }
        } else {
            if (!equipment) TextField(input, stringResource(R.string.paste_program_text), { input = it.take(20_000) })
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                OutlineButton(stringResource(R.string.choose_images), modifier = Modifier.weight(1f), enabled = work == null && sources.size < 4, onClick = {
                    selection.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                OutlineButton(stringResource(R.string.take_photo), modifier = Modifier.weight(1f), enabled = work == null && sources.size < 4, onClick = {
                    try {
                        val directory = File(app.context.cacheDir, "assistant-camera").apply { mkdirs() }
                        val file = File.createTempFile("capture-", ".jpg", directory).also { owned.add(it) }
                        val uri = FileProvider.getUriForFile(app.context, "${app.context.packageName}.exports", file)
                        capture = uri to file
                        camera.launch(uri)
                    } catch (_: Exception) {
                        problem = app.context.getString(R.string.camera_failed)
                    }
                })
            }
            sources.forEachIndexed { index, uri ->
                prepared[uri]?.let { derivative ->
                    val bitmap = remember(derivative) {
                        BitmapFactory.decodeByteArray(derivative.bytes, 0, derivative.bytes.size)
                    }
                    if (bitmap != null) {
                        DisposableEffect(bitmap) { onDispose { bitmap.recycle() } }
                        Image(bitmap.asImageBitmap(), stringResource(R.string.selected_image, index + 1), Modifier.height(Dimens.exercisePhotoArtworkWidth))
                    }
                }
                OutlineButton(if (uri in cropped) stringResource(R.string.whole_image, index + 1) else stringResource(R.string.crop_image, index + 1), modifier = Modifier.fillMaxWidth(),
                    enabled = work == null && !preparing,
                    onClick = { cropped = if (uri in cropped) cropped - uri else cropped + uri }
                )
                OutlineButton(stringResource(R.string.remove_image, index + 1), modifier = Modifier.fillMaxWidth(), enabled = work == null, onClick = { sources = sources - uri })
            }
            if (work != null) OutlineButton(stringResource(R.string.cancel_request), modifier = Modifier.fillMaxWidth(), onClick = { work?.cancel() })
        }
        if (reviewedCard != null || days.isNotEmpty()) {
            OutlineButton(stringResource(R.string.back_to_source), modifier = Modifier.fillMaxWidth(), enabled = !applying, onClick = {
                card = null
                days = emptyList()
            })
        }
        problem?.let { Text(it) }
        if (keyRequired) OutlineButton(stringResource(R.string.assistant_settings), modifier = Modifier.fillMaxWidth(), onClick = onSettings)
    }
}

@Composable
private fun EquipmentReviewForm(
    card: ImportedCard,
    exercises: List<Exercise>,
    existing: Exercise?,
    onEdit: (ImportedCard) -> Unit,
    onSelect: (Exercise?) -> Unit
) {
    TextField(card.name, stringResource(R.string.exercise_name), { onEdit(card.copy(name = it.take(120))) })
    TextField(card.source, stringResource(R.string.source_text), { onEdit(card.copy(source = it.take(6000))) })
    TextField(card.translation, stringResource(R.string.finnish_translation), { onEdit(card.copy(translation = it.take(6000))) })
    TextField(card.uncertainty, stringResource(R.string.unresolved_text), { onEdit(card.copy(uncertainty = it.take(1000))) })
    if (card.imageIndices.isNotBlank()) Text(stringResource(R.string.source_images, card.imageIndices))
    ImportExercisePicker(stringResource(R.string.save_to_exercise, existing?.name ?: stringResource(R.string.new_exercise)), exercises, false) { id ->
        onSelect(exercises.find { it.id == id })
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
        val context = LocalContext.current
        val units = MeasurementBasis.entries.associate { it.key to context.getString(it.optionLabel) }
        TextField(units[row.basis] ?: row.basis, stringResource(R.string.measurement_unit), { value ->
            val key = units.entries.firstOrNull { it.value.equals(value.trim(), ignoreCase = true) }?.key
            onEdit(row.copy(basis = (key ?: value).take(40)))
        })
        NumberField(row.sets, { onEdit(row.copy(sets = it)) }, stringResource(R.string.sets))
        NumberField(row.min, { onEdit(row.copy(min = it)) }, stringResource(R.string.minimum_reps))
        NumberField(row.max, { onEdit(row.copy(max = it)) }, stringResource(R.string.maximum_reps))
        OutlineButton(if (row.perSide) stringResource(R.string.per_side) else stringResource(R.string.total_reps), { onEdit(row.copy(perSide = !row.perSide)) })
        NumberField(row.load, { onEdit(row.copy(load = it)) }, stringResource(R.string.load), decimal = true)
        row.roundTargets.forEachIndexed { index, round ->
            fun update(value: ImportedRound) {
                onEdit(row.copy(roundTargets = row.roundTargets.toMutableList().also { it[index] = value }))
            }
            Text(stringResource(R.string.set_or_round, index + 1), style = MaterialTheme.typography.titleMedium)
            NumberField(round.min, { update(round.copy(min = it)) }, stringResource(R.string.minimum_reps))
            NumberField(round.max, { update(round.copy(max = it)) }, stringResource(R.string.maximum_reps))
            NumberField(round.load, { update(round.copy(load = it)) }, stringResource(R.string.load), decimal = true)
            OutlineButton(if (round.perSide) stringResource(R.string.per_side) else stringResource(R.string.total_reps), { update(round.copy(perSide = !round.perSide)) })
        }
        NumberField(row.duration, { onEdit(row.copy(duration = it)) }, stringResource(R.string.duration_seconds))
        NumberField(row.rest, { onEdit(row.copy(rest = it)) }, stringResource(R.string.rest_seconds))
        TextField(row.group, stringResource(R.string.group_identifier), { onEdit(row.copy(group = it.take(120))) })
        if (row.group.isNotBlank()) {
            TextField(row.groupType.groupTypeLabel(LocalContext.current), stringResource(R.string.group_type), { onEdit(row.copy(groupType = it.take(120))) })
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
    if (open) ListCard {
        item {
            ListRow(stringResource(R.string.create_new_exercise), onClick = { onSelect(CREATE_IMPORT_EXERCISE); open = false })
        }
        if (exclusion) item {
            ListRow(stringResource(R.string.exclude_row), onClick = { onSelect(EXCLUDE_IMPORT_EXERCISE); open = false })
        }
        exercises.forEach { exercise ->
            item {
                ListRow(exercise.name.exerciseDisplayName(LocalContext.current), subtitle = listOf(exercise.equipmentName.exerciseDisplayName(LocalContext.current), exercise.location.locationLabel(LocalContext.current)).filter(String::isNotBlank).joinToString(", "),
                    onClick = { onSelect(exercise.id); open = false })
            }
        }
    }
}
