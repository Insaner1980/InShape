package com.emmah.inshape.data

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordedTimingTest {
    private val zone = ZoneId.of("Europe/Helsinki")

    @Test fun unchangedMinuteDisplayKeepsRecordedSecondsAndOverlapOffset() {
        val original = "2026-10-25T01:30:25.123Z"
        assertEquals(
            Instant.parse(original),
            resolveRecordedTime("2026-10-25", "03:30", zone, original)
        )
    }

    @Test fun changedTimesRejectGapsAndRequireExplicitOverlapOffset() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveRecordedTime("2026-03-29", "03:30", zone, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveRecordedTime("2026-10-25", "03:30", zone, null)
        }
        assertEquals(
            Instant.parse("2026-10-25T01:30:00Z"),
            resolveRecordedTime("2026-10-25", "03:30+02:00", zone, null)
        )
        assertThrows(IllegalArgumentException::class.java) {
            resolveRecordedTime("2026-10-25", "03:30+04:00", zone, null)
        }
    }
}
