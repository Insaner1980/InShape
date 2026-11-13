package com.emmah.inshape.reminders

import java.time.*

fun nextReminder(days: Set<DayOfWeek>, time: LocalTime, now: ZonedDateTime): ZonedDateTime? {
    if (days.isEmpty()) return null
    for (offset in 0L..7L) {
        val date = now.toLocalDate().plusDays(offset)
        if (date.dayOfWeek !in days) continue
        val local = date.atTime(time)
        val offsets = now.zone.rules.getValidOffsets(local)
        val candidate = if (offsets.isEmpty()) now.zone.rules.getTransition(local).dateTimeAfter.atZone(now.zone)
            else ZonedDateTime.ofLocal(local, now.zone, offsets.first())
        if (candidate.toInstant() > now.toInstant()) return candidate
    }
    return null
}
