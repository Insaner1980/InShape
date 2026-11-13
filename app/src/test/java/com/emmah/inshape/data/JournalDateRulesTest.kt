package com.emmah.inshape.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class JournalDateRulesTest {
    @Test fun groupsByMondayAcrossYearAndSunday() {
        assertEquals(LocalDate.parse("2026-11-09"), weekStart(LocalDate.parse("2026-11-15")))
        assertEquals(LocalDate.parse("2025-12-29"), weekStart(LocalDate.parse("2026-01-01")))
        assertEquals(LocalDate.parse("2026-11-16"), weekStart(LocalDate.parse("2026-11-16")))
    }
    @Test fun calendarKeepsMondayColumnsAndLeapDay() {
        val grid = monthGrid(java.time.YearMonth.of(2024, 2))
        assertEquals(listOf(null, null, null, LocalDate.parse("2024-02-01")), grid.take(4))
        assertEquals(29, grid.filterNotNull().size)
        assertEquals(0, grid.size % 7)
    }
}
