package com.emmah.inshape

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** New records follow the current device zone; saved records retain their recorded zone. */
class SystemLocalClock : Clock() {
    override fun instant(): Instant = Instant.now()
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
}
