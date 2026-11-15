package casa.peterson.dhmo.core.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A [Clock] that tests move by hand. */
class MutableClock(initial: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    private var now: Instant = initial

    fun set(instant: Instant) {
        now = instant
    }

    fun advanceBy(duration: Duration) {
        now = now.plus(duration)
    }

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now
}
