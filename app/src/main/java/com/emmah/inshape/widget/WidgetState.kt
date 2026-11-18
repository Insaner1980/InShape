package com.emmah.inshape.widget

import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.ui.theme.journalDateLabel
import java.time.LocalDate
import java.time.ZoneId

data class WidgetState(
    val week: LocalDate,
    val count: Int,
    val goal: Int?,
    val resume: Boolean,
    val latest: String?,
    val activeTitle: String? = null,
    val activeStart: String? = null,
    val timingNeedsReview: Boolean = false,
    val activeZone: ZoneId? = null,
    val doneExercises: Int = 0,
    val totalExercises: Int = 0
)
fun widgetState(data: JournalSnapshot, today: LocalDate): WidgetState {
    val week = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val active = data.active
    val completed = data.history.filter { it.qualifiesForAttendance }
    return WidgetState(
        week,
        completed.count { LocalDate.parse(it.row.journalDate) in week..today },
        data.preferences.weeklyGoal,
        data.active != null,
        completed.maxByOrNull {
            it.row.journalDate
        }?.row?.let { "${it.title}\n${it.journalDate.journalDateLabel()}" },
        active?.row?.title,
        active?.row?.start,
        active?.row?.timingNeedsReview ?: false,
        active?.row?.zoneId?.let(ZoneId::of),
        active?.doneCount ?: 0,
        active?.items?.size ?: 0
    )
}
