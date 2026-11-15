package casa.peterson.dhmo.core.domain.pace

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Test

class WakeWindowTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun check(day: String, wake: String, sleep: String, dayStart: String, from: String, to: String) {
        val w = wakeWindow(
            LocalDate.parse(day),
            LocalTime.parse(wake),
            LocalTime.parse(sleep),
            LocalTime.parse(dayStart),
            berlin,
        )
        assertThat(w.wake).isEqualTo(OffsetDateTime.parse(from).toInstant())
        assertThat(w.sleep).isEqualTo(OffsetDateTime.parse(to).toInstant())
    }

    @Test
    fun `plain day`() = check("2026-06-10", "07:00", "23:00", "00:00", "2026-06-10T07:00+02:00", "2026-06-10T23:00+02:00")

    @Test
    fun `sleep after midnight`() = check("2026-06-10", "09:00", "01:00", "04:00", "2026-06-10T09:00+02:00", "2026-06-11T01:00+02:00")

    @Test
    fun `sleep truncated at day end`() = check("2026-06-10", "09:00", "01:00", "00:00", "2026-06-10T09:00+02:00", "2026-06-11T00:00+02:00")

    @Test
    fun `spring forward day is 15 hours`() =
        check("2026-03-29", "01:00", "17:00", "00:00", "2026-03-29T01:00+01:00", "2026-03-29T17:00+02:00")

    @Test
    fun `fall back day is 17 hours`() = check("2026-10-25", "01:00", "17:00", "00:00", "2026-10-25T01:00+02:00", "2026-10-25T17:00+01:00")

    @Test
    fun `wake inside the gap shifts forward`() =
        check("2026-03-29", "02:30", "18:30", "00:00", "2026-03-29T03:30+02:00", "2026-03-29T18:30+02:00")

    @Test
    fun `wake before day start belongs to the end of the day`() =
        check("2026-06-10", "03:00", "20:00", "04:00", "2026-06-11T03:00+02:00", "2026-06-11T04:00+02:00")
}
