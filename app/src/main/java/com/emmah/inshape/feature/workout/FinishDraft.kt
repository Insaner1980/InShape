package com.emmah.inshape.feature.workout

import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.parseWorkoutInstant
import com.emmah.inshape.ui.theme.elapsedSeconds
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FinishDraft(
    val end: String,
    val note: String,
    val fixedStart: String? = null,
    val unknownTimes: Boolean = false,
    val minutes: String = ""
) {
    internal fun validateTimestamps() {
        parseWorkoutInstant(end)
        fixedStart?.let(::parseWorkoutInstant)
    }
}

fun finishDraft(payload: String?, now: Instant, note: String): FinishDraft {
    val draft = payload?.let { runCatching { Json.decodeFromString<FinishDraft>(it) }.getOrNull() }
        ?: return FinishDraft(now.toString(), note)
    return runCatching { draft.also { it.validateTimestamps() } }.getOrNull()
        ?: FinishDraft(now.toString(), draft.note, unknownTimes = true, minutes = draft.minutes)
}

fun workoutTimeRange(start: Instant, end: Instant, zone: ZoneId): String {
    val pattern = if (start.atZone(zone).toLocalDate() == end.atZone(zone).toLocalDate()) "HH:mm" else "EEE HH:mm"
    val format = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withZone(zone)
    return "${format.format(start)} to ${format.format(end)}"
}

fun finishDuration(draft: FinishDraft, recordedStart: String?): Long? = if (draft.unknownTimes) {
    draft.minutes.toLongOrNull()?.takeIf { it > 0 && it <= Long.MAX_VALUE / 60 }?.times(60)
} else {
    (draft.fixedStart ?: recordedStart)?.let { elapsedSeconds(Instant.parse(it), Instant.parse(draft.end)) }
}

fun nothingRecorded(workout: Workout, note: String): Boolean = !workout.hasPerformedWork && note.isBlank()
