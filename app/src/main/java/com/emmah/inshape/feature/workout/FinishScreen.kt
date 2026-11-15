package com.emmah.inshape.feature.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.room.withTransaction
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutTiming
import com.emmah.inshape.data.resolveRecordedTime
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.RecordedTimePicker
import com.emmah.inshape.ui.components.ResponsiveRow
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.ui.theme.journalDateLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.Json

@Composable
fun WorkoutMetrics(workout: Workout, modifier: Modifier = Modifier) {
    ResponsiveRow(modifier) {
        listOf(
            workout.performedCount to if (workout.performedCount == 1) "exercise" else "exercises",
            workout.items.sumOf { it.completedSets.size } to "sets"
        ).forEach { (value, label) ->
            Surface(Modifier.weight(1f), shape = InShapeShapes.field, color = InShapeTheme.colors.surface) {
                Column(Modifier.padding(Dimens.rowPadding), verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
                    Text(value.toString(), style = MaterialTheme.typography.titleLarge)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = InShapeTheme.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun DurationSummary(seconds: Long?) {
    if (seconds == null || seconds < 60) {
        Text(if (seconds == null) "Duration not recorded" else "Under 1 min", style = MaterialTheme.typography.titleLarge)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
            val minutes = seconds / 60
            val values = if (minutes < 60) listOf(minutes to "min") else listOf(minutes / 60 to "h", minutes % 60 to "min")
            values.forEach { (number, unit) ->
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
    onSettings: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val workout = data.workout(id) ?: return
    val key = "finish:$id"
    var form by remember(id) {
        val payload = data.drafts.find { it.id == key && it.kind == "finish" }?.payload
        mutableStateOf(finishDraft(payload, vm.container.clock.instant(), workout.row.note))
    }
    var fixTime by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(form.note.isNotBlank()) }
    var discard by remember { mutableStateOf(false) }
    val colors = InShapeTheme.colors
    val zone = ZoneId.of(workout.row.zoneId)
    val empty = nothingRecorded(workout, form.note)
    LaunchedEffect(id) { vm.saveDraft(key, "finish", Json.encodeToString(form)) }
    fun edit(value: FinishDraft) {
        form = value
        vm.saveDraft(key, "finish", Json.encodeToString(value))
    }
    fun back() {
        vm.write {
            deleteEditorDraft(key)
            onBack()
        }
    }
    BackHandler { back() }
    Page(modifier, bottomBar = {
        Column {
            PrimaryButton(if (empty) "Back to workout" else "Save workout", {
                if (empty) {
                    back()
                } else {
                    val value = form
                    vm.write {
                        val duration = finishDuration(value, workout.row.start)
                        require(!value.unknownTimes || value.minutes.isBlank() || duration != null) { "Enter positive whole minutes" }
                        db.withTransaction {
                            if (value.note != workout.row.note) saveWorkoutNote(id, value.note)
                            finishWorkout(
                                id,
                                WorkoutTiming(
                                    start = value.fixedStart,
                                    end = value.end,
                                    acknowledged = true,
                                    clearTimes = value.unknownTimes,
                                    durationSeconds = if (value.unknownTimes) duration else null,
                                    inferDuration = !value.unknownTimes
                                )
                            )
                            deleteEditorDraft(key)
                        }
                        onSaveComplete()
                    }
                }
            }, Modifier.fillMaxWidth())
            if (empty) TextAction("Discard workout", { discard = true }, color = colors.error)
        }
    }) {
        IconButton({ back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Surface(Modifier.size(Dimens.touch), shape = InShapeShapes.pill, color = colors.ink) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, null, tint = colors.onInk) }
        }
        Text("Workout done", style = MaterialTheme.typography.headlineMedium)
        Text(
            "${workout.row.title}, ${workout.row.journalDate.journalDateLabel("EEEE d MMMM")}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        if (empty) {
            Text("Nothing recorded yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Complete at least one set, mark an exercise done or add a note.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        } else {
            Column(Modifier.padding(vertical = Dimens.itemGap), verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                DurationSummary(finishDuration(form, workout.row.start))
                if (!form.unknownTimes) {
                    (form.fixedStart ?: workout.row.start)?.let {
                        Text(
                            workoutTimeRange(Instant.parse(it), Instant.parse(form.end), zone),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            }
            WorkoutMetrics(workout)
        }
        ListCard {
            item { ListRow("Fix time", onClick = { fixTime = true }) }
            item { ListRow(if (form.note.isBlank()) "Add note" else "Note", onClick = { showNote = !showNote }) }
            item {
                ListRow("Share to Health Connect", trailing = {
                    Text(
                        if (data.preferences.shareWorkouts &&
                            form.unknownTimes
                        ) {
                            "Off for this workout"
                        } else if (data.preferences.shareWorkouts) {
                            "On"
                        } else {
                            "Off"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }, onClick = onSettings)
            }
        }
        if (showNote) TextField(form.note, "Workout note", { edit(form.copy(note = it)) })
    }
    if (fixTime) {
        FixTimeSheet(form, workout.row.start, zone, {
            edit(it)
            fixTime = false
        }, { fixTime = false })
    }
    if (discard) {
        ConfirmDialog("Discard this workout?", "The session and all work recorded in it will be deleted.", "Discard", {
            vm.write {
                deleteWorkout(id)
                deleteEditorDraft(key)
                onDelete()
            }
        }, { discard = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixTimeSheet(initial: FinishDraft, recordedStart: String?, zone: ZoneId, onDone: (FinishDraft) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var picker by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val start = draft.fixedStart ?: recordedStart
    val crossesMidnight =
        start != null && Instant.parse(start).atZone(zone).toLocalDate() != Instant.parse(draft.end).atZone(zone).toLocalDate()
    fun timeLabel(value: String) =
        Instant.parse(value).atZone(zone).format(DateTimeFormatter.ofPattern(if (crossesMidnight) "EEE HH:mm" else "HH:mm", Locale.ENGLISH))
    val colors = InShapeTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        scrimColor = colors.scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.surfaceStrong) }
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Dimens.screenPadding).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
        ) {
            Text("Fix time", style = MaterialTheme.typography.titleLarge)
            Text("Change when you started or finished.", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            ListCard {
                item {
                    ListRow(
                        "Started",
                        trailing = {
                            Text(
                                if (draft.unknownTimes ||
                                    start == null
                                ) {
                                    "Not recorded"
                                } else {
                                    timeLabel(start)
                                }
                            )
                        },
                        onClick = if (draft.unknownTimes) {
                            null
                        } else {
                            {
                                picker =
                                    "Started"
                            }
                        }
                    )
                }
                item {
                    ListRow(
                        "Finished",
                        trailing = {
                            Text(if (draft.unknownTimes) "Not recorded" else timeLabel(draft.end))
                        },
                        onClick = if (draft.unknownTimes) {
                            null
                        } else {
                            {
                                picker =
                                    "Finished"
                            }
                        }
                    )
                }
                item {
                    ListRow("Duration", trailing = {
                        Text(finishDuration(draft, recordedStart)?.let(::formatDuration) ?: "Not recorded", color = colors.textSecondary)
                    })
                }
            }
            error?.let { Text(it, color = colors.error) }
            TextAction(if (draft.unknownTimes) "Use recorded times" else "I don't know the times", {
                draft =
                    draft.copy(unknownTimes = !draft.unknownTimes)
                error = null
            })
            if (draft.unknownTimes) {
                NumberField(draft.minutes, { draft = draft.copy(minutes = it) }, "Duration in minutes (optional)")
                Text(
                    "Workouts without times aren't shared to Health Connect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
            PrimaryButton("Done", {
                error = when {
                    !draft.unknownTimes && (
                        start == null || !Instant.parse(
                            draft.end
                        ).isAfter(Instant.parse(start))
                        ) -> "The finish time must be after the start time."

                    draft.unknownTimes && draft.minutes.isNotBlank() && finishDuration(
                        draft,
                        recordedStart
                    ) == null -> "Enter positive whole minutes"

                    else -> null
                }
                if (error == null) onDone(draft)
            }, Modifier.fillMaxWidth())
        }
    }
    picker?.let { field ->
        val editingStart = field == "Started"
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
                    draft = if (editingStart) {
                        draft.copy(fixedStart = resolved)
                    } else {
                        draft.copy(fixedStart = zonedStart.toInstant().toString(), end = resolved)
                    }
                    error = null
                } catch (failure: IllegalArgumentException) {
                    error = failure.message
                }
                picker = null
            },
            { picker = null },
            validOffsetsAt = { time -> zone.rules.getValidOffsets(dateFor(time).atTime(time)) },
            originalOffset = zonedOriginal?.offset
        )
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
