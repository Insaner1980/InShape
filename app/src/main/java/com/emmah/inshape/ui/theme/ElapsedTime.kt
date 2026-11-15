package com.emmah.inshape.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

fun elapsedSeconds(start: Instant, now: Instant): Long = Duration.between(start, now).seconds.coerceAtLeast(0)

fun formatElapsed(seconds: Long): String {
    val value = seconds.coerceAtLeast(0)
    return if (value < 3600) {
        String.format(Locale.ROOT, "%02d:%02d", value / 60, value % 60)
    } else {
        String.format(Locale.ROOT, "%d:%02d:%02d", value / 3600, value / 60 % 60, value % 60)
    }
}

fun formatDuration(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0) / 60
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

fun Instant.workoutTime(zone: ZoneId): String = atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH))

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
