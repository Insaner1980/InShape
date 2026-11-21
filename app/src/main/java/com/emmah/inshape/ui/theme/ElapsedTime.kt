package com.emmah.inshape.ui.theme

import com.emmah.inshape.data.WorkoutEntity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

fun elapsedSeconds(start: Instant, now: Instant): Long = Duration.between(start, now).seconds.coerceAtLeast(0)

internal fun displayedIntervalSeconds(start: Instant, end: Instant): Long =
    Duration.between(start.truncatedTo(ChronoUnit.MINUTES), end.truncatedTo(ChronoUnit.MINUTES)).seconds

internal fun WorkoutEntity.displayedDurationSeconds(): Long? =
    if (start != null && end != null) {
        displayedIntervalSeconds(Instant.parse(start), Instant.parse(end)).coerceAtLeast(0)
    } else {
        durationSeconds
    }

fun formatElapsed(seconds: Long): String {
    val value = seconds.coerceAtLeast(0)
    return if (value < 3600) {
        String.format(finnishLocale, "%02d:%02d", value / 60, value % 60)
    } else {
        String.format(finnishLocale, "%d:%02d:%02d", value / 3600, value / 60 % 60, value % 60)
    }
}

internal fun durationParts(seconds: Long): List<Pair<Long, String>> {
    val minutes = seconds.coerceAtLeast(0) / 60
    return if (minutes < 60) listOf(minutes to "min") else listOf(minutes / 60 to "h", minutes % 60 to "min")
}

fun formatDuration(seconds: Long): String = durationParts(seconds).joinToString(" ") { (number, unit) -> "$number $unit" }

private val time24 = DateTimeFormatter.ofPattern("HH:mm", finnishLocale)

@Suppress("UNUSED_PARAMETER")
fun LocalTime.workoutTime(uses24Hours: Boolean): String = format(time24)

fun Instant.workoutTime(zone: ZoneId, uses24Hours: Boolean): String = atZone(zone).toLocalTime().workoutTime(uses24Hours)

@Composable
fun rememberElapsedSeconds(start: Instant?, clock: Clock): State<Long?> = produceState(
    initialValue = start?.let { elapsedSeconds(it, clock.instant()) },
    start,
    clock
) {
    if (start == null) {
        value = null
    } else {
        while (true) {
            val now = clock.instant()
            value = elapsedSeconds(start, now)
            delay(1000L - Math.floorMod(now.toEpochMilli(), 1000L))
        }
    }
}

internal fun String.decimalDisplay(): String = replace('.', ',')
