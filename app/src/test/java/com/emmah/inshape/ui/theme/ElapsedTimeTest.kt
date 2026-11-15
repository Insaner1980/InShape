package com.emmah.inshape.ui.theme

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedTimeTest {
    @Test fun clockAndSavedDurationBoundaries() {
        val start = Instant.parse("2026-11-13T10:00:00Z")
        listOf(
            Triple(0L, "00:00", "0 min"),
            Triple(59L, "00:59", "0 min"),
            Triple(60L, "01:00", "1 min"),
            Triple(3599L, "59:59", "59 min"),
            Triple(3600L, "1:00:00", "1 h 0 min"),
            Triple(36000L, "10:00:00", "10 h 0 min")
        ).forEach { (seconds, elapsed, duration) ->
            assertEquals(seconds, elapsedSeconds(start, start.plusSeconds(seconds)))
            assertEquals(elapsed, formatElapsed(seconds))
            assertEquals(duration, formatDuration(seconds))
        }
        assertEquals(0, elapsedSeconds(start, start.minusSeconds(1)))
        assertEquals("00:00", formatElapsed(-1))
        assertEquals("0 min", formatDuration(-1))
    }
}
