package com.emmah.inshape.ui.theme

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class DatesTest {
    @Test fun finnishDatesAndMonths() {
        val wednesday = LocalDate.of(2026, 11, 11)
        assertEquals("ke 11.11.", wednesday.shortDate())
        assertEquals("keskiviikko 11.11.2026", wednesday.longDate())
        assertEquals("11.11.", wednesday.axisDate())
        assertEquals("marraskuu 2026", YearMonth.from(wednesday).monthTitle())
        assertEquals("pe 13.11.", wednesday.plusDays(2).todayTitle())
    }

    @Test fun dateTimeUsesTheRecordedZoneAnd24HourClock() {
        val instant = LocalDate.of(2026, 11, 13).atTime(23, 15).toInstant(ZoneOffset.UTC)
        assertEquals("la 14.11., 01:15", dateTime(instant, ZoneOffset.ofHours(2), uses24Hours = false))
    }
}
