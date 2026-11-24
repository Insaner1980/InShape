package com.emmah.inshape.feature.workout

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Setting
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.data.parseWholeMinutesToSeconds
import com.emmah.inshape.data.resolveRecordedTime
import com.emmah.inshape.feature.assistant.WorkoutSpeech
import com.emmah.inshape.feature.library.ExercisePicker
import com.emmah.inshape.feature.library.ExercisePickerList
import com.emmah.inshape.feature.library.categoryLabel
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.feature.library.locationLabel
import com.emmah.inshape.feature.music.MusicPlayback
import com.emmah.inshape.feature.music.PlaybackBars
import com.emmah.inshape.feature.program.ProgramProgressionReview
import com.emmah.inshape.ui.UiException
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.ButtonSize
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.DialogAction
import com.emmah.inshape.ui.components.EditorSheet
import com.emmah.inshape.ui.components.HeaderIconButton
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.ArtworkWash
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.ui.theme.formatElapsed
import com.emmah.inshape.ui.theme.hasArtworkColumns
import com.emmah.inshape.ui.theme.rememberElapsedSeconds
import com.emmah.inshape.ui.theme.shortDate
import com.emmah.inshape.ui.theme.workoutTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

@Suppress("LongParameterList")
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
    modifier: Modifier = Modifier,
    musicPlayback: MusicPlayback? = null,
    onMusic: () -> Unit = {},
    onAssistantSettings: (() -> Unit)? = null,
    onAdaptWorkout: (() -> Unit)? = null
) {
    val workout = data.workout(id) ?: return
    val colors = InShapeTheme.colors
    var picker by remember { mutableStateOf(false) }
    var alternativeItemId by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<String?>(null) }
    var routineSave by remember { mutableStateOf(false) }
    var routineSaveRevision by remember { mutableStateOf<Long?>(null) }
    val pending = workout.pendingItems
    val current = workout.currentItem(pending)
    var assistantItem by rememberSaveable(id) { mutableStateOf<String?>(null) }
    if (current != null && assistantItem == current.row.id && onAssistantSettings != null) {
        com.emmah.inshape.feature.assistant.AssistantReview(
            vm.container,
            data,
            workout,
            current,
            musicPlayback,
            onSettings = {
                assistantItem = null
                onAssistantSettings()
            },
            onDismiss = { assistantItem = null }
        )
    }
    val elapsed by rememberElapsedSeconds(
        workout.row.start?.takeUnless {
            workout.row.timingNeedsReview
        }?.let(Instant::parse),
        vm.container.clock
    )
    val durationKey = "duration:${current?.row?.id}"
    val submissions by vm.submissions.collectAsStateWithLifecycle()
    val pastKey = "timing:$id"
    val savePastKey = "save-past:$id"
    val savedPast = submissions[savePastKey]
    val savingPast = savedPast != null
    val showSavedWorkout by rememberUpdatedState(onFinish)
    LaunchedEffect(savedPast) {
        if (savedPast != null && vm.consumeSubmission(savePastKey, savedPast)) showSavedWorkout()
    }
    var pastForm by remember(id) {
        mutableStateOf(pastWorkoutTiming(workout, data, pastKey))
    }
    var editMinutes by rememberSaveable(id) { mutableStateOf(false) }
    fun updatePast(form: TimingDraft) {
        if (savingPast) return
        pastForm = form
        vm.saveDraft(pastKey, "timing", Json.encodeToString(form))
    }
    val completingItem = submissions[durationKey]?.let { it.result == null } == true
    var duration by rememberSaveable(current?.row?.id) {
        mutableStateOf(data.drafts.find { it.id == durationKey }?.payload ?: "")
    }
    fun updateDuration(value: String) {
        if (vm.isSubmitting(durationKey)) return
        duration = value
        vm.saveDraft(durationKey, "duration", value)
    }
    val scrollState = rememberScrollState()
    val setRequester = remember { BringIntoViewRequester() }
    val firstIncomplete = current?.sets?.firstOrNull { it.completedAt == null }?.id
    var titleTop by remember(current?.row?.id) { mutableFloatStateOf(Float.NaN) }
    var viewportTop by remember { mutableFloatStateOf(Float.NaN) }
    val keyboardVisible = WindowInsets.isImeVisible
    ScrollToWorkoutProgress(id, current, firstIncomplete, keyboardVisible, workout.doneCount, scrollState, setRequester) {
        if (titleTop.isFinite() && viewportTop.isFinite()) {
            (scrollState.value + titleTop - viewportTop).roundToInt().coerceIn(0, scrollState.maxValue)
        } else {
            null
        }
    }
    val addFirstExercise = workout.items.isEmpty() && !workout.isPastEntry
    val next = pending.filter { it.row.id != current?.row?.id }
    val savedRoutine = data.routines.find { it.id == workout.row.sourceRoutineId }
    val canUpdateRoutine = savedRoutine?.let { routine ->
        val savedTargets = data.routineItems.filter { it.routineId == routine.id }.sortedBy { it.position }
            .mapIndexed { index, item -> item.copy(id = "", position = index) }
        workout.routinePlanItems.mapIndexed { index, item -> item.routineTarget(routine.id, index) } != savedTargets
    } == true
    fun onPrimaryAction() {
        val form = pastForm
        when {
            form != null -> savePastWorkout(vm, workout, form, current, duration, savePastKey, pastKey)
            addFirstExercise -> picker = true
            current == null -> onFinish()
            else -> completeCurrentExercise(vm, current, durationKey, duration) {
                if (next.isEmpty()) onFinish()
            }
        }
    }
    Page(
        modifier,
        bottomBar = if (keyboardVisible) null else {
            { WorkoutBottomBar(workout, current != null && next.isNotEmpty(), addFirstExercise, completingItem || savingPast, ::onPrimaryAction) }
        },
        topBar = {
            WorkoutHeader(workout, elapsed, musicPlayback, onBack, onFinish, onMusic)
        },
        scrollState = scrollState,
        scrollViewportModifier = Modifier.onGloballyPositioned { viewportTop = it.positionInRoot().y }
    ) {
        val form = pastForm
        WorkoutContentHeader(form, workout, elapsed)
        current?.let { item ->
            val canUseAssistant = onAssistantSettings != null && workout.row.activeKey == 1 && !workout.isPastEntry
            CurrentWorkoutExercise(
                item,
                data,
                vm,
                onExercise,
                { titleTop = it },
                { alternativeItemId = item.row.id },
                { remove = item.row.id },
                onAssistant = if (canUseAssistant) ({ assistantItem = item.row.id }) else null
            ) {
                WorkoutExerciseGuidance(data, workout, item, vm, canUseAssistant, musicPlayback)
                WorkoutSetEditor(
                    item,
                    vm,
                    firstIncomplete,
                    setRequester,
                    duration,
                    completingItem,
                    ::updateDuration
                )
                PastWorkoutExerciseAction(form != null, !completingItem && !savingPast) {
                    completeCurrentExercise(vm, item, durationKey, duration) {}
                }
            }
        }
        RemainingWorkoutItems(workout, next, vm)
        if (!addFirstExercise) {
            TextAction(stringResource(R.string.add_exercise), {
                picker = true
            }, Modifier.padding(top = Dimens.sectionGap), icon = Icons.Outlined.Add)
        }
        if (form != null) {
            KeyValueRow(
                stringResource(R.string.duration),
                runCatching { form.toTiming(ZoneId.of(workout.row.zoneId)).durationSeconds }.getOrNull()?.let(::formatDuration)
                    ?: stringResource(R.string.unknown),
                enabled = !savingPast,
                onClick = { editMinutes = true }
            )
            TextField(form.note, stringResource(R.string.note), { updatePast(form.copy(note = it)) }, enabled = !savingPast)
        }
        if (!addFirstExercise) {
            WorkoutEntryActions(
                isPastEntry = form != null,
                onCheckIn = onCheckIn,
                onAdaptWorkout = onAdaptWorkout,
                onUpdateRoutine = savedRoutine?.takeIf { canUpdateRoutine }?.let { routine ->
                    {
                        routineSaveRevision = routine.revision
                        routineSave = true
                    }
                },
                onDiscard = { discard = true }
            )
        }
    }
    val form = pastForm
    if (editMinutes && form != null) {
        PastWorkoutDurationEditor(form.duration, onDismiss = { editMinutes = false }) {
            updatePast(form.copy(duration = it))
            editMinutes = false
        }
    }
    if (picker) {
        ModalBottomSheet(onDismissRequest = { picker = false }, containerColor = colors.background) {
            ExercisePicker(data, vm.container.photos, {
                vm.write { addExercise(id, it) }
                picker =
                    false
            }, {
                picker = false
                onExercise("new", "")
            })
        }
    }
    LaunchedEffect(current?.row?.id) {
        if (alternativeItemId != current?.row?.id) alternativeItemId = null
    }
    if (current != null && alternativeItemId == current.row.id) {
        WorkoutAlternativeSheet(data, current, vm) { alternativeItemId = null }
    }
    if (discard) {
        ConfirmDialog(stringResource(R.string.discard_workout_question), null, stringResource(R.string.discard), destructive = true, {
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
            stringResource(R.string.delete_recorded_work_question),
            null,
            stringResource(R.string.delete),
            destructive = true,
            {
                vm.write { removeItem(item, true) }
                remove =
                    null
            },
            { remove = null }
        )
    }
    if (routineSave && savedRoutine != null) {
        ConfirmDialog(
            stringResource(R.string.update_named_routine, savedRoutine.title),
            stringResource(R.string.update_routine_message),
            stringResource(R.string.update_routine),
            destructive = false,
            {
                val revision = routineSaveRevision
                vm.write { saveSessionPlanToRoutine(id, savedRoutine.id, expectedRevision = revision) }
                routineSave = false
            },
            { routineSave = false }
        )
    }
}

@Composable
private fun WorkoutContentHeader(form: TimingDraft?, workout: Workout, elapsed: Long?) {
    if (form != null) {
        Text(form.title, style = MaterialTheme.typography.headlineLarge)
        Text(LocalDate.parse(form.date).shortDate(), color = InShapeTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    } else if (workout.items.isNotEmpty()) {
        WorkoutBanner(workout, elapsed)
    }
    if (workout.items.isEmpty()) Text(stringResource(R.string.no_exercises), style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun PastWorkoutExerciseAction(isPastEntry: Boolean, enabled: Boolean, onDone: () -> Unit) {
    if (isPastEntry) TextAction(stringResource(R.string.exercise_done), onDone, icon = Icons.Outlined.Check, enabled = enabled)
}

@Composable
private fun ColumnScope.WorkoutExerciseGuidance(
    data: JournalSnapshot,
    workout: Workout,
    item: WorkoutItem,
    vm: JournalViewModel,
    canUseAssistant: Boolean,
    musicPlayback: MusicPlayback?
) {
    val id = workout.row.id
    if (canUseAssistant) WorkoutSpeech(id, item, vm.container.assistant, musicPlayback)
    com.emmah.inshape.feature.assistant.AssistantMemories(
        data.assistantMemories.filter {
            it.exerciseId == item.row.exerciseId && it.acknowledgedAt == null && it.creationWorkoutId != id
        },
        vm,
        acknowledge = true
    )
    ProgramProgressionReview(data, workout, item, vm)
}

private fun pastWorkoutTiming(workout: Workout, data: JournalSnapshot, draftKey: String): TimingDraft? {
    if (!workout.isPastEntry) return null
    val draft = initialTimingDraft(workout.row, data, draftKey)
    val date = runCatching { LocalDate.parse(draft.date) }.getOrNull()?.toString() ?: workout.row.journalDate
    val zone = ZoneId.of(workout.row.zoneId)
    val hasValidTimes = !draft.unknownTimes && runCatching {
        val start = resolveRecordedTime(draft.startDate, draft.startTime, zone, draft.originalStart)
        val end = resolveRecordedTime(draft.endDate, draft.endTime, zone, draft.originalEnd)
        end.isAfter(start)
    }.getOrDefault(false)
    return draft.copy(date = date, unknownTimes = !hasValidTimes)
}

private fun savePastWorkout(
    vm: JournalViewModel,
    workout: Workout,
    form: TimingDraft,
    current: WorkoutItem?,
    duration: String,
    savePastKey: String,
    pastKey: String
) {
    val id = workout.row.id
    vm.submit(savePastKey) {
        val timing = form.toTiming(ZoneId.of(workout.row.zoneId))
        db.withTransaction {
            if (current != null && duration.isNotBlank()) {
                markItemDone(current.row.id, performedSeconds(duration), draftId = "duration:${current.row.id}")
            }
            val row = snapshot().workout(id)?.row ?: throw UiException(R.string.workout_missing)
            if (row.state != "COMPLETED") {
                correctWorkout(id, row.revision, form.title, form.date, form.note, timing)
                finishWorkout(id, timing)
            }
            deleteEditorDraft(pastKey)
        }
        id
    }
}

@Composable
private fun WorkoutEntryActions(
    isPastEntry: Boolean,
    onCheckIn: () -> Unit,
    onAdaptWorkout: (() -> Unit)?,
    onUpdateRoutine: (() -> Unit)?,
    onDiscard: () -> Unit
) {
    val colors = InShapeTheme.colors
    ListCard {
        if (!isPastEntry) {
            item {
                ListRow(
                    stringResource(R.string.add_check_in),
                    onClick = onCheckIn,
                    leading = { Icon(Icons.Outlined.FavoriteBorder, null, tint = colors.textSecondary) }
                )
            }
            if (onAdaptWorkout != null) {
                item {
                    ListRow(
                        stringResource(R.string.adapt_workout),
                        onClick = onAdaptWorkout,
                        leading = { Icon(Icons.Outlined.Edit, null, tint = colors.textSecondary) }
                    )
                }
            }
            if (onUpdateRoutine != null) {
                item {
                    ListRow(
                        stringResource(R.string.save_changes_routine),
                        onClick = onUpdateRoutine,
                        leading = { Icon(Icons.Outlined.Save, null, tint = colors.textSecondary) },
                        showChevron = false
                    )
                }
            }
        }
        item {
            ListRow(
                stringResource(R.string.discard_workout),
                onClick = onDiscard,
                leading = { Icon(Icons.Outlined.Delete, null, tint = colors.error) },
                titleColor = colors.error,
                showChevron = false
            )
        }
    }
}

@Composable
private fun PastWorkoutDurationEditor(initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var entered by rememberSaveable { mutableStateOf(initial) }
    val valid = entered == initial || runCatching { performedSeconds(entered) }.isSuccess
    EditorSheet(stringResource(R.string.duration), onDismiss) {
        NumberField(entered, { entered = it }, stringResource(R.string.minutes))
        ActionRow(Modifier.align(Alignment.End)) {
            DialogAction(stringResource(R.string.cancel), onDismiss)
            DialogAction(stringResource(R.string.done), { onDone(entered) }, enabled = valid)
        }
    }
}

private fun completeCurrentExercise(
    vm: JournalViewModel,
    current: WorkoutItem,
    durationKey: String,
    submittedDuration: String,
    onCompleted: () -> Unit
) {
    vm.submit(durationKey) {
        markItemDone(current.row.id, performedSeconds(submittedDuration), draftId = durationKey)
        onCompleted()
        null
    }
}

private fun performedSeconds(minutes: String): Long? {
    if (minutes.isBlank()) return null
    return parseWholeMinutesToSeconds(minutes)
        ?: throw UiException(R.string.positive_whole_minutes)
}

@Composable
private fun WorkoutBottomBar(
    workout: Workout,
    hasMoreExercises: Boolean,
    addFirstExercise: Boolean,
    completingItem: Boolean,
    onPrimary: () -> Unit
) {
    PrimaryButton(
        when {
            addFirstExercise -> stringResource(R.string.add_exercise)
            workout.isPastEntry -> stringResource(R.string.save_workout)
            hasMoreExercises -> stringResource(R.string.exercise_done)
            else -> stringResource(R.string.finish)
        },
        onPrimary,
        Modifier.fillMaxWidth(),
        enabled = !completingItem,
        icon = Icons.Outlined.Add.takeIf { addFirstExercise },
        size = ButtonSize.Large
    )
}

@Composable
private fun WorkoutHeader(
    workout: Workout,
    elapsed: Long?,
    musicPlayback: MusicPlayback?,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onMusic: () -> Unit
) {
    val colors = InShapeTheme.colors
    val compactActions = LocalWindowInfo.current.containerDpSize.width < Dimens.pickerWidth && LocalDensity.current.fontScale >= 1.3f
    PushedHeader(
        onBack,
        trailingText = !workout.isPastEntry,
        titleContent = if (workout.isPastEntry) {
            null
        } else {
            {
                BoxWithConstraints {
                    val clock = elapsed?.let(::formatElapsed) ?: "–"
                    val textMeasurer = rememberTextMeasurer()
                    val clockStyle = if (textMeasurer.measure(clock, InShapeType.workoutClock, softWrap = false).size.width <=
                        constraints.maxWidth
                    ) {
                        InShapeType.workoutClock
                    } else {
                        InShapeType.workoutClockCompact
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            clock,
                            color = colors.text,
                            style = clockStyle,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (workout.items.isNotEmpty()) {
                            Text(
                                "${workout.doneCount} / ${workout.items.size}",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    ) {
        if (musicPlayback != null) {
            val playback by musicPlayback.state.collectAsStateWithLifecycle()
            val label = stringResource(if (playback.song != null) R.string.now_playing else R.string.music)
            if (playback.playing) {
                IconButton(onMusic, Modifier.size(Dimens.touch).semantics { contentDescription = label }) {
                    PlaybackBars(playing = true, inHeader = true)
                }
            } else {
                HeaderIconButton(Icons.Outlined.MusicNote, label, onMusic)
            }
        }
        if (!workout.isPastEntry) {
            TextAction(stringResource(R.string.finish), onFinish, icon = if (compactActions) null else Icons.Outlined.Flag)
        }
    }
}

@Composable
private fun WorkoutBanner(workout: Workout, elapsed: Long?) {
    val colors = InShapeTheme.colors
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    BoxWithConstraints(
        Modifier.fillMaxWidth().heightIn(min = Dimens.rowMinTwoLine)
            .clip(InShapeShapes.card).background(ArtworkWash.Active.brush()),
        contentAlignment = Alignment.Center
    ) {
        if (elapsed != null && hasArtworkColumns()) {
            val flip = LocalLayoutDirection.current == LayoutDirection.Rtl
            val motion = if (colors.isDark) R.drawable.art_training_motion_dark else R.drawable.art_training_motion_light
            Image(
                painterResource(motion),
                null,
                Modifier.align(Alignment.CenterEnd).width(minOf(maxWidth * .4f, (maxWidth - Dimens.workoutArtworkTextWidth) / 2))
                    .height(Dimens.rowMinTwoLine).scale(if (flip) -1f else 1f, 1f)
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(vertical = Dimens.innerSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)
        ) {
            Text(workout.row.title, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            if (elapsed != null) {
                workout.row.start?.let {
                    Text(
                        stringResource(R.string.started_at, Instant.parse(it).workoutTime(ZoneId.of(workout.row.zoneId), uses24Hours)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
            } else {
                Text(
                    stringResource(R.string.started_at, LocalDate.parse(workout.row.journalDate).shortDate()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
        }
    }
}

internal fun workoutSetupLabels(context: Context, setup: String, settings: List<Setting> = emptyList()): List<Pair<String?, String>> {
    val knownLabels = mapOf(
        "Handle" to context.getString(R.string.handle),
        "Seat height" to context.getString(R.string.seat_height),
        "Backrest" to context.getString(R.string.backrest)
    )
    val emptyValues = listOf(context.getString(R.string.none), context.getString(R.string.no), "None", "-")
    fun setting(label: String?, value: String): Pair<String?, String>? {
        if (value.isBlank() || emptyValues.any { it.equals(value, ignoreCase = true) }) return null
        val localizedLabel = knownLabels.entries.firstOrNull { it.key.equals(label, ignoreCase = true) }?.value ?: label
        return localizedLabel to value
    }
    if (settings.isNotEmpty()) return settings.mapNotNull { setting(it.label.trim(), it.value.trim()) }
    return setup.split(", ").mapNotNull { raw ->
        val entry = raw.trim()
        val separator = entry.indexOf(':')
        val legacyLabel = knownLabels.asSequence().flatMap { (key, label) -> sequenceOf(key, label) }
            .firstOrNull { entry.startsWith("$it ", ignoreCase = true) }
        val lastToken = entry.substringAfterLast(' ')
        when {
            separator >= 0 -> setting(entry.take(separator).trim(), entry.drop(separator + 1).trim())

            legacyLabel != null -> setting(legacyLabel, entry.drop(legacyLabel.length + 1).trim())

            lastToken != entry && (lastToken.replace(',', '.').toBigDecimalOrNull() != null || lastToken.length <= 8) ->
                setting(entry.substringBeforeLast(' ').trimEnd(), lastToken)

            else -> setting(null, entry)
        }
    }
}

@Composable
private fun WorkoutSetup(setup: String, storedSettings: List<Setting>) {
    val settings = workoutSetupLabels(LocalContext.current, setup, storedSettings)
    val colors = InShapeTheme.colors
    if (settings.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.innerTiny),
            verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)
        ) {
            settings.forEach { (label, value) ->
                Surface(shape = InShapeShapes.chip, color = colors.surface, contentColor = colors.text) {
                    Text(
                        buildAnnotatedString {
                            if (!label.isNullOrBlank()) withStyle(SpanStyle(color = colors.textSecondary)) { append("$label ") }
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(value) }
                        },
                        Modifier.padding(horizontal = Dimens.pillPadding, vertical = Dimens.innerTiny),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviousWorkoutResult(data: JournalSnapshot, id: String, item: WorkoutItem) {
    val colors = InShapeTheme.colors
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
                stringResource(R.string.last_time_date, LocalDate.parse(previous.first).shortDate()),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
            Text(
                itemSummary(LocalContext.current, previous.second),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            previous.second.row.note.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ColumnScope.WorkoutSetEditor(
    item: WorkoutItem,
    vm: JournalViewModel,
    firstIncomplete: String?,
    setRequester: BringIntoViewRequester,
    duration: String,
    completingItem: Boolean,
    onDurationChange: (String) -> Unit
) {
    item.sets.mapNotNull { it.plannedReps }.distinct().forEach { reps ->
        Text(stringResource(R.string.rep_target, reps.localizedDisplay(LocalContext.current)), style = MaterialTheme.typography.bodySmall)
    }
    if (item.sets.isNotEmpty()) SetTableHeader(item.row.basis)
    item.sets.forEachIndexed { index, set ->
        key(set.id) {
            SetRow(
                set,
                index + 1,
                item.row.basis,
                vm,
                modifier = Modifier.testTag("workout-set-${index + 1}").then(
                    if (set.id == firstIncomplete) Modifier.bringIntoViewRequester(setRequester) else Modifier
                ),
                highlighted = set.id == firstIncomplete
            )
        }
    }
    TextAction(stringResource(R.string.add_set), {
        vm.write { addSet(item.row.id) }
    }, icon = Icons.Outlined.Add)
    if (item.row.mode == "DURATION" || item.sets.isEmpty() ||
        duration.isNotEmpty()
    ) {
        NumberField(duration, onDurationChange, stringResource(R.string.performed_minutes_optional), readOnly = completingItem)
    }
}

@Composable
private fun CurrentWorkoutExercise(
    item: WorkoutItem,
    data: JournalSnapshot,
    vm: JournalViewModel,
    onExercise: (String, String) -> Unit,
    onTitlePositionChange: (Float) -> Unit,
    onSwap: () -> Unit,
    onRemoveWithResults: () -> Unit,
    onAssistant: (() -> Unit)?,
    setEditor: @Composable ColumnScope.() -> Unit
) {
    val colors = InShapeTheme.colors
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
        Row(
            Modifier.fillMaxWidth().onGloballyPositioned { onTitlePositionChange(it.positionInRoot().y) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.row.name.exerciseDisplayName(LocalContext.current),
                Modifier.weight(1f).heightIn(min = Dimens.touch)
                    .clickable(onClickLabel = stringResource(R.string.exercise_details)) { onExercise(item.row.exerciseId, item.row.id) }
                    .padding(vertical = Dimens.innerSmall),
                style = MaterialTheme.typography.titleLarge
            )
            Box {
                IconButton({
                    menu = true
                }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        stringResource(R.string.named_options, item.row.name.exerciseDisplayName(LocalContext.current))
                    )
                }
                AppMenu(menu, { menu = false }) {
                    if (onAssistant != null) {
                        MenuAction(stringResource(R.string.workout_assistant), Icons.Outlined.Mic, {
                            menu = false
                            onAssistant()
                        })
                    }
                    MenuAction(
                        if (exerciseNote.isBlank()) {
                            stringResource(
                                R.string.add_note
                            )
                        } else {
                            stringResource(R.string.edit_note)
                        },
                        Icons.Outlined.EditNote,
                        {
                            menu = false
                            expanded = true
                            focusNote = true
                        }
                    )
                    MenuAction(stringResource(R.string.swap_exercise), Icons.Outlined.SwapHoriz, {
                        menu = false
                        onSwap()
                    })
                    MenuAction(stringResource(R.string.skip_for_now), Icons.Outlined.SkipNext, {
                        menu = false
                        vm.write { skipItemForNow(item.row.id) }
                    })
                    HorizontalDivider(color = colors.divider)
                    MenuAction(stringResource(R.string.remove_exercise), Icons.Outlined.Delete, {
                        menu = false
                        if (item.hasPerformedWork) onRemoveWithResults() else vm.write { removeItem(item.row.id) }
                    }, destructive = true)
                }
            }
        }
        val settings = data.exercises.firstOrNull { it.id == item.row.exerciseId && it.equipmentId == item.row.equipmentId }?.settings
        WorkoutSetup(item.row.setup, settings.orEmpty())
        PreviousWorkoutResult(data, item.row.workoutId, item)
        setEditor()
        if (expanded) {
            TextField(exerciseNote, stringResource(R.string.note), {
                exerciseNote = it
                vm.write { saveItemNote(item.row.id, note = it) }
            }, modifier = Modifier.focusRequester(focus))
            ListRow(stringResource(R.string.discomfort_example), trailing = {
                Checkbox(item.row.discomfort, { value ->
                    vm.write { saveItemNote(item.row.id, discomfort = value) }
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

@Composable
private fun ColumnScope.RemainingWorkoutItems(workout: Workout, next: List<WorkoutItem>, vm: JournalViewModel) {
    val colors = InShapeTheme.colors
    if (next.isNotEmpty()) {
        SectionHeader(stringResource(R.string.next_up))
        ListCard {
            next.forEach { nextItem ->
                item {
                    val summary = plannedItemSummary(LocalContext.current, nextItem)
                    ListRow(nextItem.row.name.exerciseDisplayName(LocalContext.current), subtitle = summary.takeIf(String::isNotEmpty))
                }
            }
        }
    }
    val done = workout.items.filter { it.row.disposition == "DONE" }
    if (done.isNotEmpty()) {
        SectionHeader(stringResource(R.string.workout_done_section))
        ListCard {
            done.forEach { doneItem ->
                item {
                    ListRow(
                        doneItem.row.name.exerciseDisplayName(
                            LocalContext.current
                        ),
                        subtitle = itemSummary(LocalContext.current, doneItem),
                        trailing = {
                            IconButton({
                                vm.write { reopenItem(doneItem.row.id) }
                            }) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    stringResource(R.string.reopen_named, doneItem.row.name.exerciseDisplayName(LocalContext.current)),
                                    tint = colors.ink
                                )
                            }
                        }
                    )
                }
            }
        }
    }
    val skipped = workout.items.filter { it.row.disposition == "SKIPPED" }
    if (skipped.isNotEmpty()) {
        SectionHeader(stringResource(R.string.skipped))
        ListCard {
            skipped.forEach { skippedItem ->
                item {
                    ListRow(
                        skippedItem.row.name.exerciseDisplayName(LocalContext.current),
                        subtitle = if (skippedItem.hasPerformedWork) {
                            stringResource(
                                R.string.remainder_skipped
                            )
                        } else {
                            stringResource(R.string.skipped_today)
                        },
                        trailing = {
                            TextAction(stringResource(R.string.reopen), {
                                vm.write { reopenItem(skippedItem.row.id) }
                            }, icon = Icons.AutoMirrored.Outlined.Undo)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutAlternativeSheet(data: JournalSnapshot, current: WorkoutItem, vm: JournalViewModel, onDismiss: () -> Unit) {
    val colors = InShapeTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background) {
        val currentId = current.row.exerciseId
        val ids = data.exercises.find { it.id == currentId }?.alternatives.orEmpty()
        val saved = data.exercises.filter { it.id != currentId && it.id in ids && !it.archived }
        val swap: (String) -> Unit = { exerciseId ->
            vm.write { useAlternative(current.row.id, exerciseId, requireCurrent = true) }
            onDismiss()
        }
        Page {
            val source = data.workouts.find { it.id == current.row.workoutId }?.program
            val planned = current.row.plan?.sourceSlot ?: source?.let { vm.container.programs.session(it.programId, it.day) }?.exercises
                ?.find { it.exerciseId == current.row.plan?.originalCatalogExerciseId }
            if (planned != null && planned.alternatives.isNotEmpty()) {
                SectionHeader(stringResource(R.string.program_alternatives))
                ListCard {
                    planned.alternatives.filter { it.exerciseId != current.row.plan?.catalogExerciseId }.forEach { alternative ->
                        val linked = data.programLinks.filter {
                            it.catalogExerciseId == alternative.exerciseId
                        }.map { it.exerciseId }.toSet()
                        data.exercises.filter { it.id in linked && it.id != currentId && !it.archived }.forEach { exercise ->
                            item {
                                ListRow(
                                    exercise.name.exerciseDisplayName(
                                        LocalContext.current
                                    ),
                                    subtitle = listOf(
                                        exercise.location.locationLabel(LocalContext.current),
                                        exercise.equipmentName.exerciseDisplayName(LocalContext.current)
                                    ).joinToString(", "),
                                    onClick = {
                                        vm.write { useProgramAlternative(current.row.id, planned, alternative, exercise.id) }
                                        onDismiss()
                                    }
                                )
                            }
                        }
                        item {
                            ListRow(alternative.label, subtitle = stringResource(R.string.create_from_template), onClick = {
                                vm.write { useProgramAlternative(current.row.id, planned, alternative, null) }
                                onDismiss()
                            })
                        }
                    }
                }
            }
            if (saved.isNotEmpty()) {
                SectionHeader(stringResource(R.string.your_alternatives))
                ListCard {
                    saved.forEach { exercise ->
                        item {
                            ListRow(
                                exercise.name.exerciseDisplayName(
                                    LocalContext.current
                                ),
                                subtitle = exercise.category.categoryLabel(LocalContext.current),
                                onClick = {
                                    swap(exercise.id)
                                }
                            )
                        }
                    }
                }
            }
            SectionHeader(stringResource(R.string.all_exercises))
            ExercisePickerList(data, vm.container.photos, swap, excluded = saved.mapTo(mutableSetOf(currentId)) { it.id })
        }
    }
}

@Composable
private fun ScrollToWorkoutProgress(
    id: String,
    current: WorkoutItem?,
    firstIncomplete: String?,
    keyboardVisible: Boolean,
    doneCount: Int,
    scrollState: ScrollState,
    setRequester: BringIntoViewRequester,
    scrollTarget: () -> Int?
) {
    val dragging by scrollState.interactionSource.collectIsDraggedAsState()
    val latestScrollTarget by rememberUpdatedState(scrollTarget)
    var previousItemId by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var previousSetId by rememberSaveable(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(current?.row?.id, firstIncomplete) {
        val itemChanged = previousItemId != current?.row?.id
        val setChanged = previousSetId != firstIncomplete
        previousItemId = current?.row?.id
        previousSetId = firstIncomplete
        if (current != null && !keyboardVisible && !dragging) {
            withFrameNanos { }
            if (itemChanged && doneCount > 0) {
                val target = snapshotFlow { latestScrollTarget() }.filterNotNull().first()
                if (!dragging) scrollState.animateScrollTo(target)
            }
            val hasRecordedProgress = doneCount > 0 || current.completedSets.isNotEmpty()
            val hasNewIncompleteSet = setChanged && firstIncomplete != null
            if (!dragging && hasNewIncompleteSet && hasRecordedProgress) {
                setRequester.bringIntoView()
            }
        }
    }
}
