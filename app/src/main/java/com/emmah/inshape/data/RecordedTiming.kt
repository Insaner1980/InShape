package com.emmah.inshape.data

import com.emmah.inshape.R
import com.emmah.inshape.ui.requireUi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneId
import java.time.ZoneOffset

// Android workout clocks and notifications use signed epoch milliseconds.
internal fun parseWorkoutInstant(value: String): Instant = Instant.parse(value).also { it.toEpochMilli() }

fun resolveRecordedTime(dateText: String, timeText: String, zone: ZoneId, originalInstant: String?): Instant {
    val date = LocalDate.parse(dateText)
    originalInstant?.let { raw ->
        val original = Instant.parse(raw).atZone(zone)
        if (date == original.toLocalDate() &&
            timeText == original.toLocalTime().withSecond(0).withNano(0).toString()
        ) {
            return original.toInstant()
        }
    }
    val explicit = runCatching { OffsetTime.parse(timeText) }.getOrNull()
    val time = explicit?.toLocalTime() ?: LocalTime.parse(timeText)
    val local = date.atTime(time)
    val offsets: List<ZoneOffset> = zone.rules.getValidOffsets(local)
    requireUi(offsets.isNotEmpty(), R.string.invalid_local_time)
    val offset = if (explicit != null) {
        requireUi(explicit.offset in offsets, R.string.invalid_utc_offset)
        explicit.offset
    } else {
        requireUi(offsets.size == 1, R.string.ambiguous_local_time)
        offsets.single()
    }
    return local.toInstant(offset)
}
