package com.emmah.inshape.ui.theme

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun LocalDate.displayDate(pattern: String = "d MMM"): String = format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
fun String.journalDateLabel(pattern: String = "EEE d MMM"): String = LocalDate.parse(this).displayDate(pattern)
fun String.instantLabel(zone: ZoneId = ZoneId.systemDefault()): String = Instant.parse(
    this
).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH))
