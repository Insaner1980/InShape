package com.emmah.inshape

import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemLocalClockTest {
    @Test fun existingClockFollowsChangedDeviceZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
            val clock = SystemLocalClock()
            assertEquals(ZoneId.of("Europe/Helsinki"), clock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(ZoneId.of("America/New_York"), clock.zone)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
