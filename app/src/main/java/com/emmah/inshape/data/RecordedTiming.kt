package com.emmah.inshape.data

import java.time.*
import java.time.format.DateTimeFormatter

fun resolveRecordedTime(dateText: String, timeText: String, zone: ZoneId, originalInstant: String?): Instant {
    val date = LocalDate.parse(dateText)
    originalInstant?.let { raw ->
        val original = Instant.parse(raw).atZone(zone)
        if (date == original.toLocalDate() && timeText == original.format(DateTimeFormatter.ofPattern("HH:mm"))) return original.toInstant()
    }
    val explicit = runCatching { OffsetTime.parse(timeText) }.getOrNull()
    val time = explicit?.toLocalTime() ?: LocalTime.parse(timeText)
    val local = date.atTime(time); val offsets = zone.rules.getValidOffsets(local)
    require(offsets.isNotEmpty()) { "This local time did not exist because the clocks moved forward. Enter an actual time or keep timing unknown." }
    if (explicit != null) {
        require(explicit.offset in offsets) { "That UTC offset does not match this date and time zone." }
        return local.toInstant(explicit.offset)
    }
    require(offsets.size == 1) { "This time occurred twice. Include its UTC offset, for example 03:30+02:00, or keep timing unknown." }
    return local.toInstant(offsets.single())
}
