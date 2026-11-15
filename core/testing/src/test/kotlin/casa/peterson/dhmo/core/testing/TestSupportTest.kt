package casa.peterson.dhmo.core.testing

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class TestSupportTest {
    @Test
    fun `mutable clock moves by hand`() {
        val clock = MutableClock(Instant.parse("2026-06-10T08:00:00Z"))
        clock.advanceBy(Duration.ofMinutes(90))
        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-06-10T09:30:00Z"))
        clock.set(Instant.EPOCH)
        assertThat(clock.millis()).isEqualTo(0)
        assertThat(clock.withZone(ZoneId.of("Asia/Tokyo")).zone.id).isEqualTo("Asia/Tokyo")
    }

    @Test
    fun `fixed zone provider can be changed`() {
        val provider = FixedZoneProvider()
        assertThat(provider.current().id).isEqualTo("Europe/Berlin")
        provider.zone = ZoneId.of("America/New_York")
        assertThat(provider.current().id).isEqualTo("America/New_York")
    }
}
