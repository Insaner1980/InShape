package com.emmah.inshape.feature.workout

import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.parseWorkoutInstant
import com.emmah.inshape.ui.theme.displayDate
import com.emmah.inshape.ui.theme.elapsedSeconds
import com.emmah.inshape.ui.theme.workoutTime
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FinishDraft(
    val end: String,
    val note: String,
    val fixedStart: String? = null,
    val unknownTimes: Boolean = false,
    val minutes: String = "",
    // Older drafts did not record how their end was chosen. Preserve their stored end.
    val endMode: EndMode = EndMode.Manual,
    val originalNote: String? = null
) {
    enum class EndMode { Automatic, RefreshOnOpen, Manual }

    internal fun validateTimestamps() {
        parseWorkoutInstant(end)
        fixedStart?.let(::parseWorkoutInstant)
    }

    internal fun withCurrentWorkoutNote(current: String): FinishDraft = when {
        originalNote == null -> copy(originalNote = current)
        note == originalNote -> copy(note = current, originalNote = current)
        else -> this
    }

    internal fun afterReturningToWorkout(): FinishDraft? {
        if (endMode == EndMode.Manual) return this
        val timeEdited = fixedStart != null || unknownTimes || minutes.isNotBlank()
        if (note == originalNote && !timeEdited) return null
        return copy(endMode = EndMode.RefreshOnOpen)
    }
}

fun finishDraft(payload: String?, now: Instant, note: String): FinishDraft {
    val fresh = FinishDraft(now.toString(), note, endMode = FinishDraft.EndMode.Automatic, originalNote = note)
    val draft = payload?.let { runCatching { Json.decodeFromString<FinishDraft>(it) }.getOrNull() }
        ?: return fresh
    val valid = runCatching { draft.also { it.validateTimestamps() } }.getOrElse {
        fresh.copy(note = draft.note, unknownTimes = true, minutes = draft.minutes)
    }.withCurrentWorkoutNote(note)
    return if (valid.endMode == FinishDraft.EndMode.RefreshOnOpen) {
        valid.copy(end = fresh.end, endMode = FinishDraft.EndMode.Automatic)
    } else {
        valid
    }
}

fun workoutTimeRange(start: Instant, end: Instant, zone: ZoneId, uses24Hours: Boolean = true): String {
    val crossesMidnight = start.atZone(zone).toLocalDate() != end.atZone(zone).toLocalDate()
    fun label(time: Instant): String {
        val date = if (crossesMidnight) time.atZone(zone).toLocalDate().displayDate("EEE") + " " else ""
        return date + time.workoutTime(zone, uses24Hours)
    }
    return "${label(start)} to ${label(end)}"
}

fun finishDuration(draft: FinishDraft, recordedStart: String?): Long? = if (draft.unknownTimes) {
    draft.minutes.toLongOrNull()?.takeIf { it > 0 && it <= Long.MAX_VALUE / 60 }?.times(60)
} else {
    (draft.fixedStart ?: recordedStart)?.let { elapsedSeconds(Instant.parse(it), Instant.parse(draft.end)) }
}

fun nothingRecorded(workout: Workout, note: String): Boolean = !workout.hasPerformedWork && note.isBlank()
