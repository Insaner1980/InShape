package com.emmah.inshape.feature.workout

import androidx.compose.ui.res.pluralStringResource
import com.emmah.inshape.ui.uiMessage
import com.emmah.inshape.ui.requireUi
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.outlined.EditNote
import com.emmah.inshape.feature.checkin.checkInDraft
import com.emmah.inshape.feature.checkin.CheckInDraft
import com.emmah.inshape.ui.theme.durationParts
import com.emmah.inshape.ui.components.DisclosureRow
import com.emmah.inshape.feature.checkin.PainInput
import com.emmah.inshape.feature.checkin.EnergyChoices
import com.emmah.inshape.feature.checkin.EffortChoices
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.EditorDraft
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutTiming
import com.emmah.inshape.data.newId
import com.emmah.inshape.data.resolveRecordedTime
import com.emmah.inshape.ui.components.ArtworkHeader
import com.emmah.inshape.feature.checkin.EffortLevel
import com.emmah.inshape.ui.components.ButtonSize
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.RecordedTimePicker
import com.emmah.inshape.ui.components.ResponsiveRow
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.ArtworkWash
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.dateTime
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.ui.theme.shortDate
import com.emmah.inshape.ui.theme.workoutTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.json.Json


@Composable
fun WorkoutMetrics(workout: Workout, modifier: Modifier = Modifier) {
    ResponsiveRow(modifier) {
        listOf(
            workout.performedCount to R.plurals.exercise_count_label,
            workout.items.sumOf { it.completedSets.size } to R.plurals.set_count_label
        ).forEach { (value, label) ->
            Surface(Modifier.weight(1f), shape = InShapeShapes.field, color = InShapeTheme.colors.surface) {
                Column(Modifier.padding(Dimens.rowPadding), verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
                    Text(value.toString(), style = MaterialTheme.typography.titleLarge)
                    Text(pluralStringResource(label, value), style = MaterialTheme.typography.labelSmall, color = InShapeTheme.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun DurationSummary(seconds: Long?) {
    if (seconds == null) {
        Text(stringResource(R.string.duration_not_recorded), style = MaterialTheme.typography.titleLarge)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
            durationParts(seconds).forEach { (number, unit) ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
                    Text(number.toString(), Modifier.alignByBaseline(), style = MaterialTheme.typography.displayLarge)
                    Text(unit, Modifier.alignByBaseline(), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishScreen(
    id: String,
    data: JournalSnapshot,
    vm: JournalViewModel,
    onBack: () -> Unit,
    onSaveComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val key = "finish:$id"
    val origin = rememberSaveable(id) { newId() }
    val submissions by vm.submissions.collectAsStateWithLifecycle()
    val submission = submissions[key]
    val submitting = submission != null
    val latestOnSaveComplete by rememberUpdatedState(onSaveComplete)
    val latestOnDelete by rememberUpdatedState(onDelete)
    val latestOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(submission) {
        if (submission != null && vm.consumeSubmission(key, submission) && submission.origin == origin) {
            when (submission.result) {
                "save" -> latestOnSaveComplete()
                "discard" -> latestOnDelete()
                "back" -> latestOnBack()
            }
        }
    }
    val workout = data.workout(id) ?: return
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    var after by remember(id) {
        val draft = data.checkInDraft(id, "AFTER", vm.container.clock, uses24Hours)
        mutableStateOf(if (draft.checkIn.effort == null) draft.copy(checkIn = draft.checkIn.copy(effort = EffortLevel.JustRight.stored)) else draft)
    }
    var savedForm by remember(id) {
        val payload = data.drafts.find { it.id == key && it.kind == "finish" }?.payload
        mutableStateOf(finishDraft(payload, vm.container.clock.instant(), workout.row.note))
    }
    val currentNote = workout.row.note
    val form = savedForm.withCurrentWorkoutNote(currentNote)
    val noteConflict = form.note != form.originalNote && currentNote != form.originalNote && currentNote != form.note
    var fixTime by remember { mutableStateOf(false) }
    val pageScroll = rememberScrollState()
    var preSheetScroll by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(fixTime) {
        if (fixTime) {
            preSheetScroll = pageScroll.value
            pageScroll.scrollTo(0)
        } else {
            preSheetScroll?.let { pageScroll.scrollTo(it) }
            preSheetScroll = null
        }
    }
    var more by rememberSaveable(id) { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val colors = InShapeTheme.colors
    val zone = ZoneId.of(workout.row.zoneId)
    val timingReviewed = form.endMode == FinishDraft.EndMode.Manual && (form.unknownTimes || form.fixedStart != null)
    val empty = nothingRecorded(workout, form.note)
    LaunchedEffect(id) {
        if (vm.submissions.value[key] == null) vm.saveDraft(key, "finish", Json.encodeToString(form))
    }
    fun edit(value: FinishDraft) {
        if (vm.isSubmitting(key)) return
        savedForm = value
        vm.saveDraft(key, "finish", Json.encodeToString(value))
    }
    fun editAfter(value: CheckInDraft) {
        if (vm.isSubmitting(key)) return
        after = value
        vm.saveDraft("checkin:$id:AFTER", "checkin", Json.encodeToString(value))
    }
    fun back() {
        val draft = form.afterReturningToWorkout()
        vm.submit(key, origin) {
            if (draft == null) {
                deleteEditorDraft(key)
            } else {
                saveEditorDraft(
                    EditorDraft(key, "finish", Json.encodeToString(draft), vm.container.clock.instant().toString())
                )
            }
            "back"
        }
    }
    BackHandler { back() }
    Page(modifier, scrollState = pageScroll, bottomBar = {
        val reviewRequired = workout.row.timingNeedsReview && !timingReviewed
        FinishActions(empty, submitting, noteConflict || reviewRequired, ::back, {
            saveWorkout(vm, id, key, origin, workout, form, after, timingReviewed)
        }) {
            discard = true
        }
    }, topBar = { PushedHeader(onBack = ::back) }) {
        FinishHeader(workout, form, empty, zone, uses24Hours)
        if (!empty) {
            if (data.preferences.shareWorkouts && !form.unknownTimes &&
                vm.container.healthGateway.availability() == HealthConnectClient.SDK_AVAILABLE
            ) {
                Text(stringResource(R.string.shared_after_save), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            Column(
                Modifier.padding(top = Dimens.sectionGap - Dimens.itemGap),
                verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
            ) {
                Text(stringResource(R.string.check_in_after_question), style = MaterialTheme.typography.titleMedium)
                EffortChoices(after.checkIn.effort) { editAfter(after.copy(checkIn = after.checkIn.copy(effort = it))) }
            }
            Column(
                Modifier.padding(top = Dimens.sectionGap - Dimens.itemGap),
                verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
            ) {
                PainInput(after.checkIn.id, after.observations, submitting, onChange = { editAfter(after.copy(observations = it)) })
            }
        }
        DisclosureRow(stringResource(R.string.energy_and_notes), more, { more = !more })
        if (more) {
            if (!empty) EnergyChoices(after.checkIn.energy) { editAfter(after.copy(checkIn = after.checkIn.copy(energy = it))) }
            TextField(form.note, stringResource(R.string.workout_note), { edit(form.copy(note = it)) }, enabled = !submitting)
        }
        ListRow(
            stringResource(R.string.fix_time),
            subtitle = stringResource(R.string.review_recorded_times).takeIf { workout.row.timingNeedsReview && !timingReviewed },
            onClick = { fixTime = true },
            enabled = !submitting
        )
        if (noteConflict) {
            Text(stringResource(R.string.workout_note_conflict), color = colors.error)
            Text(stringResource(R.string.current_note_text, currentNote.ifBlank { stringResource(R.string.no_note) }))
            TextAction(
                stringResource(R.string.keep_current_note), { edit(form.copy(note = currentNote, originalNote = currentNote)) },
                icon = Icons.Outlined.Check, enabled = !submitting
            )
            TextAction(stringResource(R.string.use_draft_note), { edit(form.copy(originalNote = currentNote)) }, icon = Icons.Outlined.EditNote, enabled = !submitting)
        }
    }
    if (fixTime && !submitting) {
        FixTimeSheet(form, workout.row.start, zone, {
            edit(
                if (workout.row.timingNeedsReview) {
                    it.copy(fixedStart = it.fixedStart ?: workout.row.start, endMode = FinishDraft.EndMode.Manual)
                } else it
            )
            fixTime = false
        }, { fixTime = false })
    }
    if (discard && !submitting) {
        ConfirmDialog(stringResource(R.string.discard_workout_question), stringResource(R.string.discard_workout_message), stringResource(R.string.discard), destructive = true, {
            discard = false
            vm.submit(key, origin) {
                deleteWorkout(id)
                "discard"
            }
        }, { discard = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixTimeSheet(initial: FinishDraft, recordedStart: String?, zone: ZoneId, onDone: (FinishDraft) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var picker by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val startLabel = stringResource(R.string.started)
    val finishLabel = stringResource(R.string.finished)
    var error by remember { mutableStateOf<String?>(null) }
    val start = draft.fixedStart ?: recordedStart
    val crossesMidnight =
        start != null && Instant.parse(start).atZone(zone).toLocalDate() != Instant.parse(draft.end).atZone(zone).toLocalDate()
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    fun timeLabel(value: String): String {
        val instant = Instant.parse(value)
        return if (crossesMidnight) dateTime(instant, zone, uses24Hours) else instant.workoutTime(zone, uses24Hours)
    }
    val colors = InShapeTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        scrimColor = colors.scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.divider) }
    ) {
        Text(
            stringResource(R.string.fix_time),
            Modifier.fillMaxWidth().background(colors.background).padding(horizontal = Dimens.screenPadding, vertical = Dimens.innerSmall),
            style = MaterialTheme.typography.titleLarge
        )
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Dimens.screenPadding).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
        ) {
            ListCard {
                item {
                    ListRow(
                        stringResource(R.string.started),
                        trailingValue = {
                            Text(
                                if (draft.unknownTimes ||
                                    start == null
                                ) {
                                    stringResource(R.string.not_recorded)
                                } else {
                                    timeLabel(start)
                                }
                            )
                        },
                        onClick = if (draft.unknownTimes) {
                            null
                        } else {
                            {
                                picker = startLabel
                            }
                        }
                    )
                }
                item {
                    ListRow(
                        stringResource(R.string.finished),
                        trailingValue = {
                            Text(if (draft.unknownTimes) stringResource(R.string.not_recorded) else timeLabel(draft.end))
                        },
                        onClick = if (draft.unknownTimes) {
                            null
                        } else {
                            {
                                picker = finishLabel
                            }
                        }
                    )
                }
                item {
                    ListRow(stringResource(R.string.duration), trailingValue = {
                        Text(finishDuration(draft, recordedStart)?.let(::formatDuration) ?: stringResource(R.string.not_recorded), color = colors.textSecondary)
                    })
                }
            }
            error?.let { Text(it, color = colors.error) }
            TextAction(if (draft.unknownTimes) stringResource(R.string.use_recorded_times) else stringResource(R.string.times_unknown), {
                draft =
                    draft.copy(unknownTimes = !draft.unknownTimes)
                error = null
            }, icon = Icons.Outlined.Schedule)
            if (draft.unknownTimes) {
                NumberField(draft.minutes, { draft = draft.copy(minutes = it) }, stringResource(R.string.duration_minutes_optional), Modifier.fillMaxWidth())

            }
            PrimaryButton(stringResource(R.string.done), {
                error = finishTimeError(draft, start, recordedStart)?.let(context::getString)
                if (error == null) onDone(draft)
            }, Modifier.fillMaxWidth())
        }
    }
    picker?.let { field ->
        FinishTimePicker(field, draft, start, zone, onChange = {
            draft = it
            error = null
        }, onFailure = { error = it }, onDismiss = { picker = null })
    }
}

private fun finishDateForTime(start: ZonedDateTime, time: LocalTime): LocalDate {
    val date = start.toLocalDate()
    if (!time.isBefore(start.toLocalTime().withSecond(0).withNano(0))) return date
    val localEnd = date.atTime(time)
    val startInstant = start.toInstant()
    val endsSameDay = start.zone.rules.getValidOffsets(localEnd).any { localEnd.toInstant(it).isAfter(startInstant) }
    return if (endsSameDay) date else date.plusDays(1)
}

@Composable
private fun ColumnScope.FinishHeader(workout: Workout, form: FinishDraft, empty: Boolean, zone: ZoneId, uses24Hours: Boolean) {
    val colors = InShapeTheme.colors
    if (!empty) {
        Surface(Modifier.size(Dimens.touch), shape = InShapeShapes.pill, color = colors.ink) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, null, tint = colors.onInk) }
        }
    }
    Text(if (empty) stringResource(R.string.nothing_recorded) else stringResource(R.string.workout_done), style = MaterialTheme.typography.headlineLarge)
    val dateLabel = LocalDate.parse(workout.row.journalDate).shortDate()
    if (empty) {
        Text("${workout.row.title}, $dateLabel", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Text(
            stringResource(R.string.record_before_save),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
    } else {
        ArtworkHeader(
            R.drawable.art_training_workout_finished,
            Dimens.finishArtworkWidth,
            Dimens.finishArtworkHeight,
            Modifier.clip(InShapeShapes.card).background(ArtworkWash.Completed.brush()).padding(Dimens.inner),
            cropEnd = true
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                Text(workout.row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                DurationSummary(finishDuration(form, workout.row.start))
                if (!form.unknownTimes) {
                    (form.fixedStart ?: workout.row.start)?.let {
                        Text(
                            workoutTimeRange(Instant.parse(it), Instant.parse(form.end), zone, uses24Hours),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
        WorkoutMetrics(workout)
    }
}

private fun saveWorkout(
    vm: JournalViewModel,
    id: String,
    key: String,
    origin: String,
    workout: Workout,
    value: FinishDraft,
    after: CheckInDraft,
    timingReviewed: Boolean
) {
    vm.submit(key, origin) {
        val duration = finishDuration(value, workout.row.start)
        requireUi(!value.unknownTimes || value.minutes.isBlank() || duration != null, R.string.positive_whole_minutes)
        db.withTransaction {
            finishWorkout(
                id,
                WorkoutTiming(
                    start = value.fixedStart,
                    end = value.end,
                    clearTimes = value.unknownTimes,
                    acknowledged = timingReviewed,
                    durationSeconds = if (value.unknownTimes) duration else null,
                    inferDuration = !value.unknownTimes
                ),
                note = value.note.takeIf { it != value.originalNote },
                expectedNote = value.originalNote,
                confirmCurrentTiming = true
            )
            val check = after.checkIn
            if (workout.checkIns.any { it.phase == "AFTER" } || check.effort != null || check.energy != null ||
                check.feelings.isNotBlank() || check.note.isNotBlank() || after.observations.isNotEmpty() || value.note.isNotBlank()
            ) {
                saveCheckIn(check, after.observations)
            } else {
                deleteEditorDraft("checkin:$id:AFTER")
            }
            deleteEditorDraft(key)
        }
        "save"
    }
}

@Composable
private fun FinishTimePicker(
    field: String,
    draft: FinishDraft,
    start: String?,
    zone: ZoneId,
    onChange: (FinishDraft) -> Unit,
    onFailure: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val editingStart = field == stringResource(R.string.started)
    val zonedStart = Instant.parse(start ?: draft.end).atZone(zone)
    val original = if (editingStart) start else draft.end
    val zonedOriginal = original?.let { Instant.parse(it).atZone(zone) }
    val initialTime = zonedOriginal?.toLocalTime()?.withSecond(0)?.withNano(0)?.toString() ?: "12:00"
    fun dateFor(time: LocalTime): LocalDate = when {
        zonedOriginal != null && time.toString() == initialTime -> zonedOriginal.toLocalDate()
        editingStart -> zonedStart.toLocalDate()
        else -> finishDateForTime(zonedStart, time)
    }
    RecordedTimePicker(
        field,
        initialTime,
        { time ->
            try {
                val date = dateFor(LocalTime.parse(time.take(5)))
                val resolved = resolveRecordedTime(date.toString(), time, zone, original).toString()
                val updated = if (editingStart) {
                    draft.copy(fixedStart = resolved)
                } else {
                    draft.copy(
                        fixedStart = zonedStart.toInstant().toString(),
                        end = resolved,
                        endMode = FinishDraft.EndMode.Manual
                    )
                }
                onChange(updated)
            } catch (failure: IllegalArgumentException) {
                onFailure(failure.uiMessage(context, R.string.invalid_date))
            }
            onDismiss()
        },
        onDismiss,
        validOffsetsAt = { time -> zone.rules.getValidOffsets(dateFor(time).atTime(time)) },
        originalOffset = zonedOriginal?.offset
    )
}

@Composable
private fun FinishActions(
    empty: Boolean,
    submitting: Boolean,
    noteConflict: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    Column {
        PrimaryButton(
            if (empty) stringResource(R.string.back_to_workout) else stringResource(R.string.save_workout),
            if (empty) onBack else onSave,
            Modifier.fillMaxWidth(),
            enabled = !submitting && (empty || !noteConflict),
            size = ButtonSize.Large
        )
        if (empty) {
            TextAction(stringResource(R.string.discard_workout), onDiscard, color = InShapeTheme.colors.error, icon = Icons.Outlined.Delete, enabled = !submitting)
        }
    }
}

private fun finishTimeError(draft: FinishDraft, start: String?, recordedStart: String?): Int? {
    return when {
        !draft.unknownTimes && (
            start == null || !Instant.parse(
                draft.end
            ).isAfter(Instant.parse(start))
            ) -> R.string.finish_after_start

        draft.unknownTimes && draft.minutes.isNotBlank() && finishDuration(
            draft,
            recordedStart
        ) == null -> R.string.positive_whole_minutes

        else -> null
    }
}
