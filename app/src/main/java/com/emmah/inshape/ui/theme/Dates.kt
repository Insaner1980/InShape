package com.emmah.inshape.ui.theme

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun LocalDate.displayDate(pattern: String = "d MMM"): String = format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
fun String.journalDateLabel(pattern: String = "EEE d MMM"): String = LocalDate.parse(this).displayDate(pattern)
fun String.instantLabel(zone: ZoneId, uses24Hours: Boolean): String {
    val time = Instant.parse(this).atZone(zone)
    return "${time.toLocalDate().displayDate("d MMM yyyy")}, ${time.toLocalTime().workoutTime(uses24Hours)}"
}
