package com.emmah.inshape.widget

import com.emmah.inshape.feature.today.suggestedRoutine
import android.content.Context
import com.emmah.inshape.feature.workout.plannedItemSummary
import com.emmah.inshape.feature.workout.itemSummary
import com.emmah.inshape.data.weekStart
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.ui.theme.displayedDurationSeconds
import com.emmah.inshape.ui.theme.formatDuration
import java.time.LocalDate
import java.time.ZoneId

data class WidgetState(
    val week: LocalDate,
    val count: Int,
    val goal: Int?,
    val resume: Boolean,
    val lastDuration: String?,
    val activeTitle: String? = null,
    val activeStart: String? = null,
    val timingNeedsReview: Boolean = false,
    val activeZone: ZoneId? = null,
    val doneExercises: Int = 0,
    val totalExercises: Int = 0,
    val today: LocalDate = week,
    val completedDays: Set<LocalDate> = emptySet(),
    val exerciseSummary: String? = null,
    val nextRoutineTitle: String? = null
)
fun widgetState(context: Context, data: JournalSnapshot, today: LocalDate): WidgetState {
    val week = weekStart(today)
    val active = data.active
    val completed = data.history.filter { it.qualifiesForAttendance }
    val days = completed.map { LocalDate.parse(it.row.journalDate) }.filter { it in week..today }
    val current = active?.currentItem()
    return WidgetState(
        week,
        days.size,
        data.preferences.weeklyGoal,
        active != null,
        completed.maxByOrNull {
            it.row.journalDate
        }?.row?.displayedDurationSeconds()?.let(::formatDuration),
        active?.row?.title,
        active?.row?.start,
        active?.row?.timingNeedsReview ?: false,
        active?.row?.zoneId?.let(ZoneId::of),
        active?.doneCount ?: 0,
        active?.items?.size ?: 0,
        today,
        days.toSet(),
        current?.let { if (it.hasPerformedWork) itemSummary(context, it) else plannedItemSummary(context, it).takeIf(String::isNotEmpty) },
        nextRoutineTitle = suggestedRoutine(data)?.title
    )
}
