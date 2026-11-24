// This file is named for its functions or UI role; its accompanying model is secondary.
@file:Suppress("MatchingDeclarationName")

package com.emmah.inshape.feature.workout

import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.WorkoutTiming
import com.emmah.inshape.data.newId
import com.emmah.inshape.data.parseWholeMinutesToSeconds
import com.emmah.inshape.data.resolveRecordedTime
import com.emmah.inshape.ui.UiException
import com.emmah.inshape.ui.components.EditorSheet
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.RecordedDatePicker
import com.emmah.inshape.ui.components.RecordedTimePicker
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.dateTime
import com.emmah.inshape.ui.theme.displayedDurationSeconds
import com.emmah.inshape.ui.theme.displayedIntervalSeconds
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.ui.theme.shortDate
import com.emmah.inshape.ui.theme.workoutTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val WORKOUT_DATE = "workout_date"
private const val START_DATE = "start_date"
private const val END_DATE = "end_date"

@Serializable
data class TimingDraft(
    val title: String,
    val date: String,
    val note: String,
    val startDate: String,
    val startTime: String,
    val endDate: String,
    val endTime: String,
    val duration: String,
    val unknownTimes: Boolean,
    val revision: Long,
    val originalStart: String? = null,
    val originalEnd: String? = null,
    val originalDurationSeconds: Long? = null
)

@Composable
fun WorkoutTimingEditor(id: String, data: JournalSnapshot, vm: JournalViewModel, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val workout = data.workout(id) ?: return
    val zone = ZoneId.of(workout.row.zoneId)
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    val key = "timing:$id"
    val origin = rememberSaveable(id) { newId() }
    val submissions by vm.submissions.collectAsStateWithLifecycle()
    val submission = submissions[key]
    val submitting = submission != null
    val latestOnSave by rememberUpdatedState(onSave)
    LaunchedEffect(submission) {
        if (submission != null && vm.consumeSubmission(key, submission) && submission.origin == origin) latestOnSave()
    }
    var form by remember(id) {
        mutableStateOf(initialTimingDraft(workout.row, data, key))
    }
    fun edit(value: TimingDraft) {
        if (vm.isSubmitting(key)) return
        form = value
        vm.saveDraft(key, "timing", Json.encodeToString(value))
    }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var endpoint by rememberSaveable { mutableStateOf<String?>(null) }
    val stale = form.revision != workout.row.revision
    Page(modifier, bottomBar = {
        PrimaryButton(stringResource(R.string.save_correction), {
            val value = form
            vm.submit(key, origin) {
                val timing = value.toTiming(zone)
                db.withTransaction {
                    correctWorkout(id, value.revision, value.title, value.date, value.note, timing)
                    deleteEditorDraft(key)
                }
                id
            }
        }, Modifier.fillMaxWidth().heightIn(min = Dimens.button), enabled = !stale && !submitting)
    }) {
        if (stale) {
            InShapeCard(inset = true) {
                SectionHeader(stringResource(R.string.saved_values_changed))
                Text("${workout.row.title}, ${dateLabel(workout.row.journalDate)}")
                Text(stringResource(R.string.note_text, workout.row.note.ifBlank { stringResource(R.string.none) }))
                KeyValueRow(
                    stringResource(R.string.started),
                    workout.row.start?.let { dateTime(Instant.parse(it), zone, uses24Hours) } ?: stringResource(R.string.not_recorded)
                )
                KeyValueRow(
                    stringResource(R.string.finished),
                    workout.row.end?.let { dateTime(Instant.parse(it), zone, uses24Hours) } ?: stringResource(R.string.not_recorded)
                )
                Text(
                    stringResource(
                        R.string.duration_text,
                        workout.row.displayedDurationSeconds()?.let(::formatDuration) ?: stringResource(R.string.not_recorded)
                    )
                )
                OutlineButton(stringResource(R.string.keep_draft), {
                    edit(form.copy(revision = workout.row.revision))
                }, enabled = !submitting)
            }
        }
        TextField(form.title, stringResource(R.string.workout_name), {
            edit(form.copy(title = it))
        }, singleLine = true, enabled = !submitting)
        KeyValueRow(stringResource(R.string.workout_date), dateLabel(form.date), enabled = !submitting, onClick = { picker = WORKOUT_DATE })
        if (workout.row.timingNeedsReview) {
            Text(
                stringResource(R.string.resumed_on_other_day)
            )
        }
        RecordedTimingFields(form, zone, vm.container.clock.zone, submitting, uses24Hours, ::edit) { endpoint = it }
        TextField(form.note, stringResource(R.string.workout_note), { edit(form.copy(note = it)) }, enabled = !submitting)
    }
    endpoint?.takeUnless { submitting }?.let { label ->
        TimingEndpointSheet(form, label, uses24Hours, { picker = it }, { endpoint = null })
    }
    picker?.takeUnless { submitting }?.let { field ->
        TimingPicker(form, field, workout.row.journalDate, zone, ::edit, { picker = null }, { endpoint = null })
    }
}

internal fun initialTimingDraft(row: WorkoutEntity, data: JournalSnapshot, key: String): TimingDraft {
    val zone = ZoneId.of(row.zoneId)
    val start = row.start?.let { Instant.parse(it).atZone(zone) }
    val end = row.end?.let { Instant.parse(it).atZone(zone) }
    return data.drafts.find {
        it.id == key
    }?.let { runCatching { Json.decodeFromString<TimingDraft>(it.payload) }.getOrNull() }
        ?: TimingDraft(
            row.title, row.journalDate, row.note,
            start?.toLocalDate()?.toString() ?: row.journalDate,
            start?.toLocalTime()?.workoutTime(uses24Hours = true) ?: "",
            end?.toLocalDate()?.toString() ?: row.journalDate,
            end?.toLocalTime()?.workoutTime(uses24Hours = true) ?: "",
            row.durationSeconds?.div(60)?.toString() ?: "",
            start == null || end == null,
            row.revision,
            start?.toInstant()?.toString(),
            end?.toInstant()?.toString(),
            row.durationSeconds
        )
}

internal fun TimingDraft.toTiming(zone: ZoneId): WorkoutTiming {
    LocalDate.parse(date)
    val start = if (unknownTimes) {
        null
    } else {
        resolveRecordedTime(
            startDate,
            startTime,
            zone,
            originalStart
        ).toString()
    }
    val end = if (unknownTimes) {
        null
    } else {
        resolveRecordedTime(
            endDate,
            endTime,
            zone,
            originalEnd
        ).toString()
    }
    val durationSeconds = if (duration.isBlank()) {
        null
    } else if (originalDurationSeconds !=
        null &&
        duration == (originalDurationSeconds / 60).toString()
    ) {
        originalDurationSeconds
    } else {
        parseWholeMinutesToSeconds(duration)
            ?: throw UiException(R.string.positive_whole_minutes)
    }
    return WorkoutTiming(
        start,
        end,
        durationSeconds,
        acknowledged = true,
        journalDate = date,
        clearTimes = unknownTimes,
        inferDuration = false
    )
}

@Composable
private fun RecordedTimingFields(
    form: TimingDraft,
    zone: ZoneId,
    currentZone: ZoneId,
    submitting: Boolean,
    uses24Hours: Boolean,
    edit: (TimingDraft) -> Unit,
    onEndpoint: (String) -> Unit
) {
    val computedDuration = if (form.unknownTimes) {
        null
    } else {
        runCatching {
            val start = resolveRecordedTime(form.startDate, form.startTime, zone, form.originalStart)
            val end = resolveRecordedTime(form.endDate, form.endTime, zone, form.originalEnd)
            if (end.isBefore(start)) -1L else displayedIntervalSeconds(start, end)
        }.getOrNull()
    }
    androidx.compose.foundation.layout.Column(
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.itemGap)
    ) {
        val startLabel = stringResource(R.string.started)
        val endLabel = stringResource(R.string.finished)
        SectionHeader(stringResource(R.string.recorded_timing))
        if (zone != currentZone) Text(stringResource(R.string.recorded_in_zone, zone.id), color = InShapeTheme.colors.textSecondary)
        if (!form.unknownTimes) {
            KeyValueRow(
                stringResource(R.string.started),
                "${dateLabel(form.startDate)}, ${timeLabel(form.startTime, uses24Hours)}",
                numeric = true,
                enabled = !submitting
            ) {
                onEndpoint(startLabel)
            }
            KeyValueRow(
                stringResource(R.string.finished),
                "${dateLabel(form.endDate)}, ${timeLabel(form.endTime, uses24Hours)}",
                numeric = true,
                enabled = !submitting
            ) {
                onEndpoint(endLabel)
            }
            val elapsed = when {
                computedDuration == null -> stringResource(R.string.not_recorded)
                computedDuration < 0 -> stringResource(R.string.finish_before_start)
                else -> formatDuration(computedDuration)
            }
            if (computedDuration != null && (computedDuration < 0 || computedDuration / 60 != form.duration.toLongOrNull())) {
                KeyValueRow(stringResource(R.string.elapsed_time), elapsed, numeric = true)
            }
        }
        TextAction(
            if (form.unknownTimes) stringResource(R.string.use_recorded_times) else stringResource(R.string.times_unknown),
            { edit(form.copy(unknownTimes = !form.unknownTimes)) },
            icon = Icons.Outlined.Schedule,
            enabled = !submitting
        )
        NumberField(
            form.duration,
            { edit(form.copy(duration = it)) },
            stringResource(R.string.duration_minutes),
            Modifier.fillMaxWidth(),
            readOnly = submitting
        )
    }
}

@Composable
private fun TimingEndpointSheet(
    form: TimingDraft,
    label: String,
    uses24Hours: Boolean,
    openPicker: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isStart = label == stringResource(R.string.started)
    EditorSheet(label, onDismiss) {
        KeyValueRow(stringResource(R.string.date), dateLabel(if (isStart) form.startDate else form.endDate)) {
            openPicker(if (isStart) START_DATE else END_DATE)
        }
        KeyValueRow(stringResource(R.string.time), timeLabel(if (isStart) form.startTime else form.endTime, uses24Hours), numeric = true) {
            openPicker(label)
        }
    }
}

private fun timeForDateChange(time: String, oldDate: String, newDate: String): String = if (oldDate == newDate) {
    time
} else {
    runCatching { OffsetTime.parse(time).toLocalTime().toString() }.getOrDefault(time)
}

@Composable
private fun TimingPicker(
    form: TimingDraft,
    field: String,
    journalDate: String,
    zone: ZoneId,
    edit: (TimingDraft) -> Unit,
    onDismiss: () -> Unit,
    onCloseEndpoint: () -> Unit
) {
    val startLabel = stringResource(R.string.started)
    fun update(value: String) {
        edit(
            when (field) {
                WORKOUT_DATE -> form.copy(date = value)
                START_DATE -> form.copy(startDate = value, startTime = timeForDateChange(form.startTime, form.startDate, value))
                END_DATE -> form.copy(endDate = value, endTime = timeForDateChange(form.endTime, form.endDate, value))
                startLabel -> form.copy(startTime = value)
                else -> form.copy(endTime = value)
            }
        )
        onDismiss()
        if (!field.endsWith("date")) onCloseEndpoint()
    }
    if (field.endsWith("date")) {
        RecordedDatePicker(
            stringResource(
                when (field) {
                    START_DATE -> R.string.start_date
                    END_DATE -> R.string.end_date
                    else -> R.string.workout_date
                }
            ),
            when (field) {
                START_DATE -> form.startDate
                END_DATE -> form.endDate
                else -> form.date
            },
            journalDate,
            ::update,
            onDismiss
        )
    } else {
        val isStart = field == stringResource(R.string.started)
        val time = if (isStart) form.startTime else form.endTime
        val date = runCatching { LocalDate.parse(if (isStart) form.startDate else form.endDate) }.getOrNull()
        val original = (if (isStart) form.originalStart else form.originalEnd)?.let { raw ->
            runCatching { Instant.parse(raw).atZone(zone) }.getOrNull()
        }
        RecordedTimePicker(
            field,
            time,
            ::update,
            onDismiss,
            validOffsetsAt = { chosen -> date?.let { zone.rules.getValidOffsets(it.atTime(chosen)) }.orEmpty() },
            originalOffset = original?.takeIf {
                it.toLocalDate() == date && it.toLocalTime().withSecond(0).withNano(0).toString() == time
            }?.offset
        )
    }
}

@Composable
private fun dateLabel(date: String): String =
    runCatching { LocalDate.parse(date).shortDate() }.getOrDefault(stringResource(R.string.invalid_date))

@Composable
private fun timeLabel(time: String, uses24Hours: Boolean): String =
    runCatching { LocalTime.parse(time.take(5)).workoutTime(uses24Hours) }.getOrDefault(stringResource(R.string.not_recorded))
