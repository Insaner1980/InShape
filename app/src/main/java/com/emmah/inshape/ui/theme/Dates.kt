package com.emmah.inshape.ui.theme

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val finnishLocale: Locale = Locale.forLanguageTag("fi-FI")

private val longDateFormat = DateTimeFormatter.ofPattern("cccc d.M.uuuu", finnishLocale)
private val shortDateFormat = DateTimeFormatter.ofPattern("EEE d.M.", finnishLocale)
private val axisDateFormat = DateTimeFormatter.ofPattern("d.M.", finnishLocale)
private val monthNameFormat = DateTimeFormatter.ofPattern("LLLL", finnishLocale)
private val monthTitleFormat = DateTimeFormatter.ofPattern("LLLL uuuu", finnishLocale)

fun LocalDate.longDate(): String = format(longDateFormat)
fun LocalDate.shortDate(): String = format(shortDateFormat)
fun LocalDate.todayTitle(): String = shortDate()
fun LocalDate.axisDate(): String = format(axisDateFormat)
fun YearMonth.monthName(): String = format(monthNameFormat)
fun YearMonth.monthTitle(): String = format(monthTitleFormat)

fun dateTime(instant: Instant, zone: ZoneId, uses24Hours: Boolean): String {
    val local = instant.atZone(zone)
    return "${local.toLocalDate().shortDate()}, ${local.toLocalTime().workoutTime(uses24Hours)}"
}
