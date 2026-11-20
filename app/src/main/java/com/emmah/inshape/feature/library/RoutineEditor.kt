package com.emmah.inshape.feature.library

import com.emmah.inshape.feature.library.groupTypeLabel
import androidx.compose.ui.platform.LocalContext
import com.emmah.inshape.ui.requireUi
import com.emmah.inshape.ui.UiException
import com.emmah.inshape.ui.theme.finnishLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.feature.workout.setSummary
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.ExercisePlan
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.PlannedReps
import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.RoutineItem
import com.emmah.inshape.data.SetTarget
import com.emmah.inshape.data.newId
import com.emmah.inshape.data.parseWholeMinutesToSeconds
import com.emmah.inshape.data.validatedSetValues
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.ArtworkHeader
import com.emmah.inshape.ui.components.ButtonSize
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.HeaderIconButton
import com.emmah.inshape.ui.components.EditorSheet
import com.emmah.inshape.ui.components.DialogAction
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.ReorderHandle
import com.emmah.inshape.ui.components.ResponsiveRow
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.components.reorderActions
import com.emmah.inshape.ui.theme.ArtworkWash
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val MAX_UNIFORM_ROUTINE_SETS = 999

@Serializable data class RoutineSetDraft(val load: String = "", val reps: String = "", val plannedReps: PlannedReps? = null)

@Serializable data class RoutineItemDraft(
    val id: String = newId(),
    val exerciseId: String,
    val mode: String = "SETS",
    val duration: String = "",
    val note: String = "",
    val sets: List<RoutineSetDraft> = emptyList(),
    val originalDurationSeconds: Long? = null,
    val durationReviewed: Boolean = false,
    val plan: ExercisePlan? = null
) {
    val requiresDurationReview: Boolean get() = mode == "DURATION" && originalDurationSeconds != null && !durationReviewed
    val preservedFractionalDuration: Long?
        get() = originalDurationSeconds?.takeIf { mode == "DURATION" && it % 60 != 0L && duration == (it / 60).toString() }

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
        requireUi(!requiresDurationReview, R.string.review_planned_duration)
        return when {
            duration.isBlank() -> null

            originalDurationSeconds != null && duration == (originalDurationSeconds / 60).toString() -> originalDurationSeconds

            else -> parseWholeMinutesToSeconds(duration)
                ?: throw UiException(R.string.positive_whole_minutes)
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
    onClose: () -> Unit,
    onCreated: (String) -> Unit,
    onStart: (String) -> Unit,
    onCreateExercise: () -> Unit,
    modifier: Modifier = Modifier
) {
    val creating = id == "new"
    val key = "routine:$id"
    val origin = rememberSaveable(id) { newId() }
    val submissions by vm.submissions.collectAsStateWithLifecycle()
    val submission = submissions[key]
    val creatingRoutine = submission?.result == null && submission != null
    val notifyCreated by rememberUpdatedState(onCreated)
    val createdId = submission?.result?.takeIf { result -> data.routines.any { it.id == result } }
    LaunchedEffect(submission, createdId) {
        if (submission != null && createdId != null && vm.consumeSubmission(key, submission) && submission.origin == origin) {
            notifyCreated(createdId)
        }
    }
    val saved = remember(id, data.routines, data.routineItems) {
        data.routines.find { it.id == id }?.let { routine ->
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
                            RoutineSetDraft(it.load ?: "", it.reps?.toString() ?: "", it.plannedReps)
                        },
                        originalDurationSeconds = item.durationSeconds,
                        durationReviewed = true,
                        plan = item.plan
                    )
                }
            )
        }
    }
    var draft by remember(id) {
        mutableStateOf(
            data.drafts.find { it.id == "routine:$id" }
                ?.let { runCatching { Json.decodeFromString<RoutineDraft>(it.payload) }.getOrNull() }
                ?.let { restored ->
                    restored.copy(
                        items = restored.items.map { item ->
                            item.restoreDuration(data.routineItems.find { it.routineId == id && it.id == item.id }?.durationSeconds)
                        }
                    )
                }
                ?: saved
                ?: RoutineDraft()
        )
    }
    var picker by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var details by rememberSaveable(id) { mutableStateOf(id == "new") }
    var editingItem by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<String?>(null) }
    var movement by remember { mutableStateOf("") }
    fun edit(value: RoutineDraft) {
        if (creatingRoutine) return
        draft = value
        vm.saveDraft(key, "routine", Json.encodeToString(value))
        if (!creating) {
            val models = runCatching { value.toRoutine() }.getOrNull()
            if (models != null) vm.write { saveRoutine(models.first, models.second, draftId = key) }
        }
    }
    fun updateItem(index: Int, item: RoutineItemDraft) = edit(
        draft.copy(
            items = draft.items.toMutableList().also {
                it[index] =
                    item
            }
        )
    )
    val context = androidx.compose.ui.platform.LocalContext.current
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
            context.getString(
                R.string.moved_to_position,
                data.exercises.find { it.id == item.exerciseId }?.name ?: context.getString(R.string.exercise),
                target + 1
            )
    }
    var showNameError by rememberSaveable { mutableStateOf(false) }
    var routineMenu by remember { mutableStateOf(false) }
    fun confirmRoutine() {
        if (draft.title.isBlank()) {
            showNameError = true
            details = true
            return
        }
        val toSave = draft
        if (creating) {
            vm.submit(key, origin) {
                val (routine, items) = toSave.toRoutine()
                saveRoutine(routine, items, draftId = key)
            }
        } else {
            vm.write {
                val (routine, items) = toSave.toRoutine()
                saveRoutine(routine, items, draftId = key)
                onStart(routine.id)
            }
        }
    }
    Page(
        modifier,
        topBar = {
            PushedHeader(onBack = onClose, title = if (creating) draft.title.ifBlank { stringResource(R.string.new_routine) } else "") {
                if (creating) {
                    HeaderIconButton(Icons.Outlined.Edit, stringResource(R.string.edit_details), { details = true })
                } else {
                    Box {
                        HeaderIconButton(Icons.Outlined.MoreVert, stringResource(R.string.routine_options), { routineMenu = true })
                        AppMenu(routineMenu, { routineMenu = false }, inHeader = true) {
                            MenuAction(stringResource(R.string.edit_details), Icons.Outlined.Edit, {
                                routineMenu = false
                                details = true
                            })
                            MenuAction(stringResource(R.string.duplicate), Icons.Outlined.ContentCopy, {
                                routineMenu = false
                                vm.write {
                                    duplicateRoutine(id)
                                    onClose()
                                }
                            })
                            MenuAction(stringResource(R.string.remove), Icons.Outlined.Delete, {
                                routineMenu = false
                                removing = true
                            }, destructive = true)
                        }
                    }
                }
            }
        },
        bottomBar = {
            PrimaryButton(
                if (creating) stringResource(R.string.create_routine) else stringResource(R.string.start_workout),
                ::confirmRoutine,
                Modifier.fillMaxWidth(),
                size = ButtonSize.Large,
                enabled = draft.items.isNotEmpty() && (!creating || draft.title.isNotBlank()) && !creatingRoutine
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            if (!creating) Text(draft.title, style = MaterialTheme.typography.headlineLarge)
            val artwork = remember(draft.items, draft.duration, data.exercises) {
                val exercises = data.exercises.associateBy { it.id }
                routineArtwork(draft.items.map { exercises[it.exerciseId] }, draft.duration.toIntOrNull())
            }
            if (draft.items.isNotEmpty()) {
                ArtworkHeader(
                    artwork,
                    Dimens.routineArtworkWidth,
                    Dimens.routineArtworkHeight,
                    Modifier.clip(InShapeShapes.card).background(ArtworkWash.Routine.brush())
                        .padding(Dimens.inner),
                    cropEnd = artwork != R.drawable.art_training_mobility_mat
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
                        Text(
                            exerciseCountLabel(draft.items.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = InShapeTheme.colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        estimatedDurationLabel(draft.duration.toIntOrNull())?.let { duration ->
                            Text(
                                duration.replaceFirstChar { it.titlecase(finnishLocale) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = InShapeTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (draft.description.isNotBlank()) {
                            Text(
                                draft.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InShapeTheme.colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
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
                OutlineButton(stringResource(R.string.review_saved_duration), { editingItem = item.id })
            }
            ListCard {
                draft.items.forEachIndexed { index, entry ->
                    item {
                        key(entry.id) {
                            RoutineExerciseRow(
                                entry,
                                data.exercises.find { it.id == entry.exerciseId },
                                canMoveUp = index > 0,
                                canMoveDown = index < draft.items.lastIndex,
                                menuOpen = menu == entry.id,
                                onMenuChange = { open -> menu = entry.id.takeIf { open } },
                                onMove = { move(entry.id, it) },
                                onEdit = { editingItem = entry.id },
                                onRemove = { edit(draft.copy(items = draft.items.filter { it.id != entry.id })) }
                            )
                        }
                    }
                }
            }
            TextAction(stringResource(R.string.add_exercise), { picker = true }, icon = Icons.Outlined.Add)

        }
    }
    if (details) {
        RoutineDetailsSheet(
            draft,
            showNameError,
            onNameError = { showNameError = true },
            onEdit = ::edit,
            onClose = {
                details = false
                showNameError = false
            }
        )
    }
    editingItem?.let { editing ->
        val index = draft.items.indexOfFirst { it.id == editing }
        if (index >= 0) {
            val item = draft.items[index]
            RoutineItemTargetsSheet(
                item,
                data.exercises.find { it.id == item.exerciseId },
                onChange = { updateItem(index, it) },
                onClose = { editingItem = null }
            )
        }
    }
    if (picker) {
        ModalBottomSheet(onDismissRequest = { picker = false }) {
            ExercisePicker(data, vm.container.photos, { exerciseId ->
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
        ConfirmDialog(stringResource(R.string.remove_routine_question), stringResource(R.string.remove_routine_message), stringResource(R.string.remove), destructive = true, {
            vm.write {
                removeRoutine(id)
                deleteEditorDraft("routine:$id")
                onClose()
            }
            removing =
                false
        }, { removing = false })
    }
}

private fun RoutineDraft.toRoutine(): Pair<Routine, List<RoutineItem>> {
    requireUi(title.isNotBlank(), R.string.routine_name_required)
    val minutes = if (duration.isBlank()) null else duration.toIntOrNull()?.takeIf { it > 0 }
        ?: throw UiException(R.string.positive_duration)
    val routine = Routine(id, title, description, minutes)
    val targets = items.map { item ->
        RoutineItem(
            item.id,
            id,
            item.exerciseId,
            mode = item.mode,
            durationSeconds = item.plannedDurationSeconds(),
            note = item.note,
            sets = item.sets.map { target ->
                if (target.load.isBlank() && target.reps.isBlank()) SetTarget(plannedReps = target.plannedReps)
                else validatedSetValues(target.load, target.reps).let { SetTarget(it.first, it.second, target.plannedReps) }
            },
            plan = item.plan
        )
    }
    return routine to targets
}

@Composable
private fun RoutineExerciseRow(
    entry: RoutineItemDraft,
    exercise: Exercise?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    menuOpen: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    ListRow(
        exercise?.name?.exerciseDisplayName(LocalContext.current) ?: stringResource(R.string.archived_exercise),
        modifier = Modifier.semantics(mergeDescendants = true) {}.reorderActions(canMoveUp, canMoveDown, onMove),
        subtitle = when (entry.mode) {
            "DURATION" -> runCatching { entry.plannedDurationSeconds() }.getOrNull()?.let(::formatDuration) ?: stringResource(R.string.no_targets)
            "QUICK" -> null

            else -> setSummary(
                LocalContext.current,
                MeasurementBasis.fromKey(exercise?.basis.orEmpty()),
                entry.sets.map { SetTarget(it.load.takeIf(String::isNotBlank), it.reps.toIntOrNull()) }
            ).takeIf { it.isNotEmpty() } ?: entry.sets.mapNotNull { it.plannedReps?.localizedDisplay(LocalContext.current) }.distinct()
                .joinToString(", ").takeIf(String::isNotBlank)
        },
        leading = {
            Box(Modifier.clearAndSetSemantics {}) {
                ReorderHandle(exercise?.name?.exerciseDisplayName(LocalContext.current) ?: stringResource(R.string.exercise)) { onMove(it) }
            }
        },
        trailing = {
            Box {
                IconButton({
                    onMenuChange(true)
                }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        stringResource(R.string.named_options, exercise?.name?.exerciseDisplayName(LocalContext.current) ?: stringResource(R.string.exercise).lowercase())
                    )
                }
                AppMenu(menuOpen, { onMenuChange(false) }) {
                    MenuAction(stringResource(R.string.edit_targets_note), Icons.Outlined.Edit, {
                        onMenuChange(false)
                        onEdit()
                    })
                    MenuAction(stringResource(R.string.remove_exercise), Icons.Outlined.Delete, {
                        onMenuChange(false)
                        onRemove()
                    }, destructive = true)
                }
            }
        }
    )
}

@Composable
private fun RoutineDetailsSheet(
    draft: RoutineDraft,
    showNameError: Boolean,
    onNameError: () -> Unit,
    onEdit: (RoutineDraft) -> Unit,
    onClose: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(draft.title) }
    var description by rememberSaveable { mutableStateOf(draft.description) }
    var duration by rememberSaveable { mutableStateOf(draft.duration) }
    val nameFocus = remember { FocusRequester() }
    EditorSheet(stringResource(R.string.routine_details), onClose) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
        ) {
            TextField(
                title,
                stringResource(R.string.routine_name),
                { title = it },
                singleLine = true,
                error = stringResource(R.string.routine_name_required).takeIf { showNameError && title.isBlank() },
                focusRequester = nameFocus
            )
            TextField(description, stringResource(R.string.description_optional), { description = it })
            NumberField(duration, { duration = it }, stringResource(R.string.estimated_minutes_optional))
        }
        val keyboard = LocalSoftwareKeyboardController.current
        ActionRow(Modifier.align(Alignment.End)) {
            DialogAction(stringResource(R.string.cancel), onClose)
            DialogAction(stringResource(R.string.done), {
                if (title.isBlank()) {
                    onNameError()
                    nameFocus.requestFocus()
                    keyboard?.show()
                } else {
                    onEdit(draft.copy(title = title, description = description, duration = duration))
                    onClose()
                }
            })
        }
    }
}

@Composable
private fun RoutineItemTargetsSheet(
    item: RoutineItemDraft,
    exercise: Exercise?,
    onChange: (RoutineItemDraft) -> Unit,
    onClose: () -> Unit
) {
    EditorSheet(exercise?.name?.exerciseDisplayName(LocalContext.current) ?: stringResource(R.string.exercise), onClose) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
        ) {
            val time = stringResource(R.string.time)
            val sets = stringResource(R.string.sets)
            val justDone = stringResource(R.string.just_done)
            SelectField(
                stringResource(R.string.log_as),
                when (item.mode) {
                    "DURATION" -> stringResource(R.string.time)
                    "QUICK" -> justDone
                    else -> stringResource(R.string.sets)
                },
                listOf(stringResource(R.string.sets), stringResource(R.string.time), justDone),
                { label ->
                    onChange(
                        item.copy(
                            mode = when (label) {
                                time -> "DURATION"
                                justDone -> "QUICK"
                                else -> "SETS"
                            },
                            sets = if (label ==
                                sets
                            ) {
                                item.sets
                            } else {
                                emptyList()
                            }
                        )
                    )
                }
            )
            val preservedDuration = item.preservedFractionalDuration
            if (item.requiresDurationReview) {
                Text(stringResource(R.string.saved_duration_explanation, checkNotNull(item.originalDurationSeconds)))
                OutlineButton(stringResource(R.string.keep_saved_duration), { onChange(item.copy(durationReviewed = true)) })
            } else if (preservedDuration != null) {
                Text(stringResource(R.string.saved_duration, preservedDuration))
            }
            if (item.mode ==
                "DURATION"
            ) {
                NumberField(item.duration, {
                    onChange(item.copy(duration = it, originalDurationSeconds = null, durationReviewed = true))
                }, if (preservedDuration == null) stringResource(R.string.planned_minutes) else stringResource(R.string.whole_minutes))
            }
            if (item.mode == "SETS") {
                RoutineSetTargets(item, MeasurementBasis.fromKey(exercise?.basis.orEmpty()), onChange)
            }
            TextField(item.note, stringResource(R.string.plan_note), { onChange(item.copy(note = it)) })
        }
        DialogAction(stringResource(R.string.done), onClose, Modifier.align(Alignment.End))
    }
}

@Composable
internal fun RoutineSetTargets(item: RoutineItemDraft, basis: MeasurementBasis, onChange: (RoutineItemDraft) -> Unit) {
    var varies by rememberSaveable(item.id) { mutableStateOf(item.sets.distinct().size > 1) }
    val loadLabel = if (basis != MeasurementBasis.Bodyweight) {
        stringResource(basis.columnLabel)
    } else {
        stringResource(R.string.load).takeIf { item.sets.any { set -> set.load.isNotBlank() } }
    }
    if (item.plan?.restSeconds != null) Text(stringResource(R.string.rest_duration, item.plan.restSeconds))
    item.plan?.group?.let { Text(stringResource(R.string.rounds_group, it.type.groupTypeLabel(LocalContext.current), it.rounds)) }
    if (varies) {
        item.sets.forEachIndexed { index, set ->
            fun update(value: RoutineSetDraft) {
                onChange(item.copy(sets = item.sets.toMutableList().also { it[index] = value }))
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                    Text(stringResource(R.string.set_number, index + 1), style = MaterialTheme.typography.bodySmall)
                    ResponsiveRow {
                        NumberField(
                            set.reps, { update(set.copy(reps = it, plannedReps = null)) },
                            set.plannedReps?.localizedDisplay(LocalContext.current) ?: stringResource(R.string.reps), Modifier.weight(1f)
                        )
                        loadLabel?.let { label ->
                            NumberField(set.load, { update(set.copy(load = it)) }, label, Modifier.weight(1f), decimal = true)
                        }
                    }
                }
                IconButton({ onChange(item.copy(sets = item.sets.filterIndexed { position, _ -> position != index })) }) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.remove_planned_set, index + 1), tint = InShapeTheme.colors.text)
                }
            }
        }
        OutlineButton(stringResource(R.string.add_planned_set), { onChange(item.copy(sets = item.sets + RoutineSetDraft())) })
    } else {
        UniformRoutineTargets(item, loadLabel, onChange)
        TextAction(stringResource(R.string.vary_by_set), { varies = true }, icon = Icons.Outlined.Tune)
    }
}

@Composable
private fun UniformRoutineTargets(item: RoutineItemDraft, loadLabel: String?, onChange: (RoutineItemDraft) -> Unit) {
    var countText by rememberSaveable(item.id) { mutableStateOf(item.sets.size.toString()) }
    val maxCount = remember(item.id) { maxOf(MAX_UNIFORM_ROUTINE_SETS, item.sets.size) }
    val target = item.sets.firstOrNull() ?: RoutineSetDraft()

    fun updateTarget(value: RoutineSetDraft) {
        val count = item.sets.size.coerceAtLeast(1)
        countText = count.toString()
        onChange(item.copy(sets = List(count) { value }))
    }

    val fields: @Composable RowScope.() -> Unit = {
        NumberField(countText, { text ->
            if (text.isEmpty()) {
                // Keep the current targets while replacing the count. Entering 0 removes them.
                countText = text
            } else {
                text.toIntOrNull()?.takeIf { it in 0..maxCount }?.let { count ->
                    countText = count.toString()
                    onChange(item.copy(sets = List(count) { target }))
                }
            }
        }, stringResource(R.string.sets), Modifier.weight(1f))
        NumberField(
            target.reps, { updateTarget(target.copy(reps = it, plannedReps = null)) },
            target.plannedReps?.localizedDisplay(LocalContext.current) ?: stringResource(R.string.reps), Modifier.weight(1f)
        )
        loadLabel?.let { label ->
            NumberField(target.load, { updateTarget(target.copy(load = it)) }, label, Modifier.weight(1f), decimal = true)
        }
    }
    if (LocalDensity.current.fontScale >= 1.3f) {
        ResponsiveRow { fields() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall), content = fields)
    }
}
