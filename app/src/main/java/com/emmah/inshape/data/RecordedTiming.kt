package com.emmah.inshape.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Android workout clocks and notifications use signed epoch milliseconds.
internal fun parseWorkoutInstant(value: String): Instant = Instant.parse(value).also { it.toEpochMilli() }

fun resolveRecordedTime(dateText: String, timeText: String, zone: ZoneId, originalInstant: String?): Instant {
    val date = LocalDate.parse(dateText)
    originalInstant?.let { raw ->
        val original = Instant.parse(raw).atZone(zone)
        if (date == original.toLocalDate() &&
            timeText == original.format(DateTimeFormatter.ofPattern("HH:mm"))
        ) {
            return original.toInstant()
        }
    }
    val explicit = runCatching { OffsetTime.parse(timeText) }.getOrNull()
    val time = explicit?.toLocalTime() ?: LocalTime.parse(timeText)
    val local = date.atTime(time)
    val offsets: List<ZoneOffset> = zone.rules.getValidOffsets(local)
    require(offsets.isNotEmpty()) {
        "This local time did not exist because the clocks moved forward. Enter an actual time or keep timing unknown."
    }
    val offset = if (explicit != null) {
        require(explicit.offset in offsets) {
            "That UTC offset does not match this date and time zone."
        }
        explicit.offset
    } else {
        require(offsets.size == 1) {
            "This time occurred twice. Include its UTC offset, for example 03:30+02:00, or keep timing unknown."
        }
        offsets.single()
    }
    return local.toInstant(offset)
}
