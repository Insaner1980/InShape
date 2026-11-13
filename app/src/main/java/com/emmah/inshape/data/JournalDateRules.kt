package com.emmah.inshape.data

import java.time.LocalDate
import java.time.YearMonth

fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())
fun monthGrid(month: YearMonth): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val days = List<LocalDate?>(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    return days + List<LocalDate?>((7 - days.size % 7) % 7) { null }
}
