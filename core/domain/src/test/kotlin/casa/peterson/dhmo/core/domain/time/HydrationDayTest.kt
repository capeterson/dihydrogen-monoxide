package casa.peterson.dhmo.core.domain.time

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Test

/** Dataset H of TECHNICAL_PLAN §9.14. */
class HydrationDayTest {
    private fun day(utc: String, offset: String, dayStart: String) =
        hydrationDay(Instant.parse(utc), ZoneOffset.of(offset), LocalTime.parse(dayStart))

    @Test fun h01() = assertThat(day("2026-10-24T21:30:00Z", "+02:00", "00:00")).isEqualTo(LocalDate.of(2026, 10, 24))

    @Test fun h02() = assertThat(day("2026-10-24T22:30:00Z", "+02:00", "00:00")).isEqualTo(LocalDate.of(2026, 10, 25))

    @Test fun h03() = assertThat(day("2026-10-25T00:30:00Z", "+02:00", "00:00")).isEqualTo(LocalDate.of(2026, 10, 25))

    @Test fun h04() = assertThat(day("2026-10-25T01:30:00Z", "+01:00", "00:00")).isEqualTo(LocalDate.of(2026, 10, 25))

    @Test fun h05() = assertThat(day("2026-10-25T22:59:00Z", "+01:00", "00:00")).isEqualTo(LocalDate.of(2026, 10, 25))

    @Test fun h06() = assertThat(day("2026-10-25T23:00:00Z", "+01:00", "00:00")).isEqualTo(LocalDate.of(2026, 10, 26))

    @Test fun h07() = assertThat(day("2026-03-29T00:59:00Z", "+01:00", "00:00")).isEqualTo(LocalDate.of(2026, 3, 29))

    @Test fun h08() = assertThat(day("2026-03-29T01:00:00Z", "+02:00", "00:00")).isEqualTo(LocalDate.of(2026, 3, 29))

    @Test fun h09() = assertThat(day("2026-06-11T01:30:00Z", "+02:00", "04:00")).isEqualTo(LocalDate.of(2026, 6, 10))

    @Test fun h10() = assertThat(day("2026-06-11T02:00:00Z", "+02:00", "04:00")).isEqualTo(LocalDate.of(2026, 6, 11))

    @Test fun h11() = assertThat(day("2026-06-10T03:30:00Z", "-04:00", "00:00")).isEqualTo(LocalDate.of(2026, 6, 9))

    @Test fun h12() = assertThat(day("2026-06-10T03:30:00Z", "+02:00", "00:00")).isEqualTo(LocalDate.of(2026, 6, 10))

    @Test
    fun `h1 to h6 at 250 ml give the 25 hour transition day totals of the plan`() {
        val days =
            listOf(
                day("2026-10-24T21:30:00Z", "+02:00", "00:00"),
                day("2026-10-24T22:30:00Z", "+02:00", "00:00"),
                day("2026-10-25T00:30:00Z", "+02:00", "00:00"),
                day("2026-10-25T01:30:00Z", "+01:00", "00:00"),
                day("2026-10-25T22:59:00Z", "+01:00", "00:00"),
                day("2026-10-25T23:00:00Z", "+01:00", "00:00"),
            ).groupingBy { it }.eachCount().mapValues { it.value * 250 }
        assertThat(days)
            .containsExactly(
                LocalDate.of(2026, 10, 24),
                250,
                LocalDate.of(2026, 10, 25),
                1000,
                LocalDate.of(2026, 10, 26),
                250,
            )
    }
}
