package com.emmah.inshape.widget

import com.emmah.inshape.data.*
import java.time.LocalDate
import com.emmah.inshape.ui.theme.journalDateLabel

data class WidgetState(val week: LocalDate, val count: Int, val goal: Int?, val resume: Boolean, val latest: String?)
fun widgetState(data: JournalSnapshot, today: LocalDate): WidgetState {
    val week = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val completed = data.history.filter { it.qualifiesForAttendance }
    return WidgetState(week, completed.count { LocalDate.parse(it.row.journalDate) in week..today },
        data.preferences.weeklyGoal, data.active != null,
        completed.maxByOrNull { it.row.journalDate }?.row?.let { "${it.title}\n${it.journalDate.journalDateLabel()}" })
}
