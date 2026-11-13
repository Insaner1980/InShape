package com.emmah.inshape.feature.workout

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import com.emmah.inshape.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.*
import com.emmah.inshape.ui.components.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.*
import java.time.format.DateTimeFormatter

@Serializable
data class TimingDraft(val title: String, val date: String, val note: String, val startDate: String, val startTime: String, val endDate: String, val endTime: String, val duration: String, val unknownTimes: Boolean, val revision: Long, val originalStart: String? = null, val originalEnd: String? = null, val originalDurationSeconds: Long? = null)

@Composable
fun WorkoutTimingEditor(id: String, data: JournalSnapshot, vm: JournalViewModel, correcting: Boolean, onSaved: () -> Unit) {
    val workout = data.workout(id) ?: return
    val zone = ZoneId.of(workout.row.zoneId)
    val key = "timing:$id"
    var form by remember(id) {
        val start = workout.row.start?.let { Instant.parse(it).atZone(zone) }
        val end = workout.row.end?.let { Instant.parse(it).atZone(zone) } ?: if (!correcting && workout.row.kind == "LIVE" && !workout.row.timingNeedsReview) ZonedDateTime.now(vm.container.clock).withZoneSameInstant(zone) else null
        val format = DateTimeFormatter.ofPattern("HH:mm")
        mutableStateOf(data.drafts.find { it.id == key }?.let { runCatching { Json.decodeFromString<TimingDraft>(it.payload) }.getOrNull() } ?: TimingDraft(
            workout.row.title, workout.row.journalDate, workout.row.note, start?.toLocalDate()?.toString() ?: workout.row.journalDate, start?.format(format) ?: "",
            end?.toLocalDate()?.toString() ?: workout.row.journalDate, end?.format(format) ?: "", workout.row.durationSeconds?.div(60)?.toString() ?: "",
            start == null || end == null, workout.row.revision, start?.toInstant()?.toString(), end?.toInstant()?.toString(), workout.row.durationSeconds,
        ))
    }
    fun edit(value: TimingDraft) { form = value; vm.saveDraft(key, "timing", Json.encodeToString(value)) }
    Page(bottomBar = {
        PrimaryButton(if (correcting) "Save correction" else "Save workout", {
            val value = form
            vm.write {
                LocalDate.parse(value.date)
                val start = if (value.unknownTimes) null else resolveRecordedTime(value.startDate, value.startTime, zone, value.originalStart).toString()
                val end = if (value.unknownTimes) null else resolveRecordedTime(value.endDate, value.endTime, zone, value.originalEnd).toString()
                val duration = if (value.duration.isBlank()) null else if (value.originalDurationSeconds != null && value.duration == (value.originalDurationSeconds / 60).toString()) value.originalDurationSeconds
                    else value.duration.toLongOrNull()?.takeIf { it > 0 && it <= Long.MAX_VALUE / 60 }?.times(60) ?: throw IllegalArgumentException("Enter positive whole minutes")
                val timing = WorkoutTiming(start, end, duration, acknowledged = true, journalDate = value.date, clearTimes = value.unknownTimes, inferDuration = false)
                if (correcting) correctWorkout(id, value.revision, value.title, value.date, value.note, timing)
                else {
                    val latest = snapshot().workout(id)!!.row
                    correctWorkout(id, latest.revision, value.title, value.date, value.note, timing)
                    finishWorkout(id, timing)
                }
                deleteEditorDraft(key); onSaved()
            }
        }, Modifier.fillMaxWidth().heightIn(min = Dimens.button))
    }) {
        InShapeCard {
            SectionLabel(if (correcting) "Correct workout" else "Review your workout")
            Text("${workout.performedCount} exercises with recorded work")
            Text("${workout.items.count { it.row.disposition == "SKIPPED" }} skipped, ${workout.items.count { it.row.disposition in listOf("PLANNED", "IN_PROGRESS", "INCOMPLETE") }} incomplete")
            Text("Only explicitly completed sets and activities count as performed work.")
        }
        TextField(form.title, "Workout name", { edit(form.copy(title = it)) }, true)
        TextField(form.date, "Workout date (YYYY-MM-DD)", { edit(form.copy(date = it)) }, true)
        if (workout.row.timingNeedsReview) Text("This session was resumed on another date. Review the interval or keep the times unknown.")
        InShapeCard {
            SectionLabel("Recorded timing")
            Text("Time zone: ${workout.row.zoneId}")
            Row { Checkbox(form.unknownTimes, { edit(form.copy(unknownTimes = it)) }); Text("Start and end times are not recorded") }
            if (!form.unknownTimes) {
                TextField(form.startDate, "Start date (YYYY-MM-DD)", { edit(form.copy(startDate = it)) }, true)
                TextField(form.startTime, "Start time (HH:mm)", { edit(form.copy(startTime = it)) }, true)
                TextField(form.endDate, "End date (YYYY-MM-DD)", { edit(form.copy(endDate = it)) }, true)
                TextField(form.endTime, "End time (HH:mm)", { edit(form.copy(endTime = it)) }, true)
                Text("You can include seconds or a UTC offset, such as 03:30+02:00, when needed. Unedited recorded times keep their original precision.")
            }
            NumberField(form.duration, { edit(form.copy(duration = it)) }, "Recorded duration in minutes (optional)")
            Text("Leave duration blank when it is unknown. It can differ from the start-to-end interval.")
        }
        TextField(form.note, "Workout note", { edit(form.copy(note = it)) })

        if (!correcting) Text("Check-ins are optional. You can add one after saving.")
    }
}
