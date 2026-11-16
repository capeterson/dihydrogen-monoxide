package casa.peterson.dhmo.core.domain.pace

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class PaceTest {
    private val window = WakeWindow(Instant.parse("2026-06-10T07:00:00Z"), Instant.parse("2026-06-10T23:00:00Z"))

    private fun at(hhmm: String): Instant = Instant.parse("2026-06-10T$hhmm:00Z")

    @Test
    fun `expected values on the default curve`() {
        assertThat(expectedMl(2000, at("07:00"), window, PaceCurve.DEFAULT)).isEqualTo(0)
        assertThat(expectedMl(2000, at("08:36"), window, PaceCurve.DEFAULT)).isEqualTo(300)
        assertThat(expectedMl(2000, at("15:00"), window, PaceCurve.DEFAULT)).isEqualTo(1150)
        assertThat(expectedMl(2000, at("21:24"), window, PaceCurve.DEFAULT)).isEqualTo(2000)
        assertThat(expectedMl(2450, at("21:00"), window, PaceCurve.DEFAULT)).isEqualTo(2385)
    }

    @Test
    fun `before wake and after sleep are clamped`() {
        assertThat(expectedMl(2000, at("05:00"), window, PaceCurve.DEFAULT)).isEqualTo(0)
        assertThat(expectedMl(2000, Instant.parse("2026-06-11T01:00:00Z"), window, PaceCurve.DEFAULT)).isEqualTo(2000)
    }

    @Test
    fun `status around the tolerance`() {
        // linear, goal 3000, tolerance 5% = 150 ml; at 11:00 expected is 750
        val lin = PaceCurve.LINEAR
        assertThat(pace(3000, 600, 5, at("11:00"), window, lin)).isEqualTo(Pace(750, 150, PaceStatus.ON_TRACK))
        assertThat(pace(3000, 599, 5, at("11:00"), window, lin).status).isEqualTo(PaceStatus.BEHIND)
        assertThat(pace(3000, 900, 5, at("11:00"), window, lin)).isEqualTo(Pace(750, -150, PaceStatus.ON_TRACK))
        assertThat(pace(3000, 901, 5, at("11:00"), window, lin).status).isEqualTo(PaceStatus.AHEAD)
    }

    @Test
    fun `tolerance rounds half up`() {
        // 2450 * 5% = 122.5 -> 123
        val end = at("23:00")
        assertThat(pace(2450, 0, 5, end, window, PaceCurve.DEFAULT).deficitMl).isEqualTo(2450)
        assertThat(pace(2450, 2450 - 123, 5, end, window, PaceCurve.DEFAULT).status).isEqualTo(PaceStatus.ON_TRACK)
        assertThat(pace(2450, 2450 - 124, 5, end, window, PaceCurve.DEFAULT).status).isEqualTo(PaceStatus.BEHIND)
    }
}
