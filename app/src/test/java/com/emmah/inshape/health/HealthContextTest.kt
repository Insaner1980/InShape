package com.emmah.inshape.health

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class HealthContextTest {
    @Test fun windowsUseLocalDatesAcrossDaylightSavingAndClipSleepToNow() {
        val now = ZonedDateTime.parse("2026-03-29T08:00+03:00[Europe/Helsinki]")
        val windows = healthWindows(now)
        assertEquals(Instant.parse("2026-03-28T22:00:00Z"), windows.dayStart)
        assertEquals(Instant.parse("2026-03-28T16:00:00Z"), windows.sleepStart)
        assertEquals(now.toInstant(), windows.sleepEnd)
    }
    @Test fun heartRateNeedsSourceChoiceAndNeverCombinesMeasurements() {
        val first = HeartSample(70, Instant.parse("2026-11-13T08:00:00Z"), "a")
        val second = HeartSample(90, Instant.parse("2026-11-13T09:00:00Z"), "b")
        assertNull(selectHeart(listOf(first, second), null))
        assertEquals(first, selectHeart(listOf(first, second), "a"))
        assertEquals(second, selectHeart(listOf(second), null))
        assertNull(selectHeart(emptyList(), "a"))
    }
}
