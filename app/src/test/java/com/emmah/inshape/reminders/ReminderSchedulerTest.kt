package com.emmah.inshape.reminders

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderSchedulerTest {
    @Test fun emptyDaysDisableAndNextSelectedDayUsesLocalTime() {
        val now = ZonedDateTime.parse("2026-11-13T12:00:00+02:00[Europe/Helsinki]")
        assertNull(nextReminder(emptySet(), LocalTime.NOON, now))
        assertEquals("2026-11-16T10:00+02:00[Europe/Helsinki]", nextReminder(setOf(DayOfWeek.MONDAY), LocalTime.of(10, 0), now).toString())
    }
    @Test fun springGapUsesNextValidInstant() {
        val now = ZonedDateTime.parse("2026-03-29T01:00:00+02:00[Europe/Helsinki]")
        assertEquals("2026-03-29T04:00+03:00[Europe/Helsinki]", nextReminder(setOf(DayOfWeek.SUNDAY), LocalTime.of(3, 30), now).toString())
    }
    @Test fun autumnOverlapDoesNotScheduleSecondOccurrence() {
        val now = ZonedDateTime.parse("2026-10-25T03:45:00+03:00[Europe/Helsinki]")
        assertEquals("2026-11-01T03:30+02:00[Europe/Helsinki]", nextReminder(setOf(DayOfWeek.SUNDAY), LocalTime.of(3, 30), now).toString())
    }
}
