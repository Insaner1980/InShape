// This file is named for its functions or UI role; its accompanying model is secondary.
@file:Suppress("MatchingDeclarationName")

package com.emmah.inshape.feature.workout

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.room.withTransaction
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutTiming
import com.emmah.inshape.data.resolveRecordedTime
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.RecordedDatePicker
import com.emmah.inshape.ui.components.RecordedTimePicker
import com.emmah.inshape.ui.components.SectionLabel
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
fun WorkoutTimingEditor(
    id: String,
    data: JournalSnapshot,
    vm: JournalViewModel,
    correcting: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val workout = data.workout(id) ?: return
    val zone = ZoneId.of(workout.row.zoneId)
    val key = "timing:$id"
    var form by remember(id) {
        val start = workout.row.start?.let { Instant.parse(it).atZone(zone) }
        val end =
            workout.row.end?.let { Instant.parse(it).atZone(zone) }
                ?: if (!correcting && workout.row.kind == "LIVE" &&
                    !workout.row.timingNeedsReview
                ) {
                    ZonedDateTime.now(vm.container.clock).withZoneSameInstant(zone)
                } else {
                    null
                }
        val format = DateTimeFormatter.ofPattern("HH:mm")
        mutableStateOf(
            data.drafts.find {
                it.id == key
            }?.let { runCatching { Json.decodeFromString<TimingDraft>(it.payload) }.getOrNull() }
                ?: TimingDraft(
                    workout.row.title, workout.row.journalDate, workout.row.note,
                    start?.toLocalDate()?.toString() ?: workout.row.journalDate,
                    start?.format(format) ?: "",
                    end?.toLocalDate()?.toString() ?: workout.row.journalDate,
                    end?.format(format) ?: "",
                    workout.row.durationSeconds?.div(60)?.toString() ?: "",
                    start == null || end == null,
                    workout.row.revision,
                    start?.toInstant()?.toString(),
                    end?.toInstant()?.toString(),
                    workout.row.durationSeconds
                )
        )
    }
    fun edit(value: TimingDraft) {
        form = value
        vm.saveDraft(key, "timing", Json.encodeToString(value))
    }
    var picker by remember { mutableStateOf<String?>(null) }
    val stale = form.revision != workout.row.revision
    Page(modifier, bottomBar = {
        PrimaryButton(if (correcting) "Save correction" else "Save workout", {
            val value = form
            vm.write {
                LocalDate.parse(value.date)
                val start = if (value.unknownTimes) {
                    null
                } else {
                    resolveRecordedTime(
                        value.startDate,
                        value.startTime,
                        zone,
                        value.originalStart
                    ).toString()
                }
                val end = if (value.unknownTimes) {
                    null
                } else {
                    resolveRecordedTime(
                        value.endDate,
                        value.endTime,
                        zone,
                        value.originalEnd
                    ).toString()
                }
                val duration = if (value.duration.isBlank()) {
                    null
                } else if (value.originalDurationSeconds !=
                    null &&
                    value.duration == (value.originalDurationSeconds / 60).toString()
                ) {
                    value.originalDurationSeconds
                } else {
                    value.duration.toLongOrNull()?.takeIf {
                        it > 0 && it <= Long.MAX_VALUE / 60
                    }?.times(60)
                        ?: throw IllegalArgumentException("Enter positive whole minutes")
                }
                val timing =
                    WorkoutTiming(
                        start,
                        end,
                        duration,
                        acknowledged = true,
                        journalDate = value.date,
                        clearTimes = value.unknownTimes,
                        inferDuration = false
                    )
                db.withTransaction {
                    correctWorkout(id, value.revision, value.title, value.date, value.note, timing)
                    if (!correcting) finishWorkout(id, timing)
                    deleteEditorDraft(key)
                }
                onSave()
            }
        }, Modifier.fillMaxWidth().heightIn(min = Dimens.button), enabled = !stale)
    }) {
        if (stale) {
            InShapeCard(inset = true) {
                SectionLabel("Saved values changed")
                Text("Review the saved values before keeping your draft.")
                Text("${workout.row.title}, ${workout.row.journalDate}")
                Text("Note: ${workout.row.note.ifBlank { "None" }}")
                Text("Start: ${workout.row.start ?: "Not recorded"}")
                Text("End: ${workout.row.end ?: "Not recorded"}")
                Text("Duration: ${workout.row.durationSeconds?.let { "$it seconds" } ?: "Not recorded"}")
                OutlineButton("Keep this draft", { edit(form.copy(revision = workout.row.revision)) })
            }
        }
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.itemGap)
        ) {
            SectionLabel(if (correcting) "Correct workout" else "Review your workout")
            Text("${workout.performedCount} exercises with recorded work")
            Text(
                "${workout.items.count {
                    it.row.disposition == "SKIPPED"
                }} skipped, ${workout.items.count {
                    it.row.disposition in listOf("PLANNED", "IN_PROGRESS", "INCOMPLETE")
                }} incomplete"
            )
            Text("Only explicitly completed sets and activities count as performed work.")
        }
        TextField(form.title, "Workout name", { edit(form.copy(title = it)) }, singleLine = true)
        ListRow("Workout date", trailing = { Text(form.date) }, onClick = { picker = "Workout date" })
        if (workout.row.timingNeedsReview) {
            Text(
                "This session was resumed on another date. Review the interval or keep the times unknown."
            )
        }
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.itemGap)
        ) {
            SectionLabel("Recorded timing")
            if (zone != vm.container.clock.zone) Text("Recorded in ${zone.id}", color = InShapeTheme.colors.textSecondary)
            TextAction(if (form.unknownTimes) "Use recorded times" else "I don't know the times", {
                edit(form.copy(unknownTimes = !form.unknownTimes))
            })
            if (!form.unknownTimes) {
                listOf(
                    "Start date" to form.startDate,
                    "Started" to form.startTime,
                    "End date" to form.endDate,
                    "Finished" to form.endTime
                ).forEach { (label, value) ->
                    ListRow(label, trailing = { Text(value.ifBlank { "Not recorded" }) }, onClick = { picker = label })
                }
            }
            NumberField(form.duration, {
                edit(form.copy(duration = it))
            }, "Recorded duration in minutes (optional)")
            Text(
                "Leave duration blank when it is unknown. It can differ from the start-to-end interval."
            )
        }
        TextField(form.note, "Workout note", { edit(form.copy(note = it)) })

        if (!correcting) Text("Check-ins are optional. You can add one after saving.")
    }
    picker?.let { field ->
        fun timeForDateChange(time: String, oldDate: String, newDate: String): String = if (oldDate == newDate) {
            time
        } else {
            runCatching { OffsetTime.parse(time).toLocalTime().toString() }.getOrDefault(time)
        }
        fun update(value: String) {
            edit(
                when (field) {
                    "Workout date" -> form.copy(date = value)
                    "Start date" -> form.copy(startDate = value, startTime = timeForDateChange(form.startTime, form.startDate, value))
                    "End date" -> form.copy(endDate = value, endTime = timeForDateChange(form.endTime, form.endDate, value))
                    "Started" -> form.copy(startTime = value)
                    else -> form.copy(endTime = value)
                }
            )
            picker = null
        }
        if (field.endsWith("date")) {
            RecordedDatePicker(
                field,
                when (field) {
                    "Start date" -> form.startDate
                    "End date" -> form.endDate
                    else -> form.date
                },
                workout.row.journalDate,
                ::update,
                {
                    picker =
                        null
                }
            )
        } else {
            val isStart = field == "Started"
            val time = if (isStart) form.startTime else form.endTime
            val date = runCatching { LocalDate.parse(if (isStart) form.startDate else form.endDate) }.getOrNull()
            val original = (if (isStart) form.originalStart else form.originalEnd)?.let { raw ->
                runCatching { Instant.parse(raw).atZone(zone) }.getOrNull()
            }
            RecordedTimePicker(
                field,
                time,
                ::update,
                { picker = null },
                validOffsetsAt = { chosen -> date?.let { zone.rules.getValidOffsets(it.atTime(chosen)) }.orEmpty() },
                originalOffset = original?.takeIf {
                    it.toLocalDate() == date && it.toLocalTime().withSecond(0).withNano(0).toString() == time
                }?.offset
            )
        }
    }
}
