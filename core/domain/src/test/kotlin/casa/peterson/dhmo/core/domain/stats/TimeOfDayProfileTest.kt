package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class TimeOfDayProfileTest {
    private val day1 = LocalDate.of(2026, 3, 2)
    private val day2 = LocalDate.of(2026, 3, 3)
    private val day3 = LocalDate.of(2026, 3, 4)

    private fun at(day: LocalDate, hour: Int, minute: Int, ml: Int) = TimedIntake(day, hour * 60 + minute, ml)

    /** Dataset T of TECHNICAL_PLAN §9.14. */
    private val datasetT = listOf(
        at(day1, 8, 0, 500), at(day1, 12, 30, 500), at(day1, 19, 0, 600), at(day1, 21, 15, 400),
        at(day2, 7, 30, 250), at(day2, 10, 0, 750), at(day2, 15, 0, 500), at(day2, 18, 0, 500),
        at(day3, 9, 0, 300), at(day3, 13, 0, 300), at(day3, 20, 0, 900),
    )
    private val goals = mapOf(day1 to 2000, day2 to 2000, day3 to 2000)

    @Test
    fun `dataset T headline numbers`() {
        val p = timeOfDayProfile(datasetT, goals, dayStartMinutes = 0)
        assertThat(p.medianMinutesToHalfGoal).isEqualTo(750.0)
        assertThat(p.daysReachingHalf).isEqualTo(3)
        assertThat(p.medianMinutesToFullGoal).isEqualTo(1177.5)
        assertThat(p.daysReachingFull).isEqualTo(2)
        assertThat(p.eveningShare!!).isWithin(1e-6).of(2400.0 / 5500.0)
    }

    @Test
    fun `dataset T hourly means`() {
        val p = timeOfDayProfile(datasetT, goals, dayStartMinutes = 0)
        val expected = mapOf(
            7 to 83.3333, 8 to 166.6667, 9 to 100.0, 10 to 250.0, 12 to 166.6667, 13 to 100.0,
            15 to 166.6667, 18 to 166.6667, 19 to 200.0, 20 to 300.0, 21 to 133.3333,
        )
        assertThat(p.hourlyMeanMl).hasSize(24)
        for (slot in 0 until 24) {
            assertThat(p.hourlyMeanMl[slot]).isWithin(TOLERANCE).of(expected[slot] ?: 0.0)
        }
    }

    @Test
    fun `dataset T cumulative fraction`() {
        val p = timeOfDayProfile(datasetT, goals, dayStartMinutes = 0)
        val expected = listOf(
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0417, 0.125, 0.1917, 0.3167, 0.3167,
            0.4, 0.4667, 0.4667, 0.55, 0.55, 0.55, 0.6333, 0.7333, 0.9333, 1.0, 1.0, 1.0,
        )
        assertThat(p.cumulativeFraction).hasSize(24)
        expected.forEachIndexed { slot, want -> assertThat(p.cumulativeFraction[slot]).isWithin(1e-4).of(want) }
    }

    @Test
    fun `day start shifts the slots and the evening threshold`() {
        // Day start 04:00: a drink at 02:00 is the last slot of the hydration day and counts as evening.
        val entries = listOf(at(day1, 4, 0, 100), at(day1, 18, 0, 100), at(day1, 2, 0, 200))
        val p = timeOfDayProfile(entries, mapOf(day1 to 400), dayStartMinutes = 240)
        assertThat(p.hourlyMeanMl[0]).isEqualTo(100.0)
        assertThat(p.hourlyMeanMl[14]).isEqualTo(100.0)
        assertThat(p.hourlyMeanMl[22]).isEqualTo(200.0)
        assertThat(p.eveningShare!!).isWithin(1e-9).of(0.75)
        assertThat(p.medianMinutesToHalfGoal).isEqualTo(840.0)
        assertThat(p.medianMinutesToFullGoal).isEqualTo(1320.0)
    }

    @Test
    fun `empty input and days without a goal`() {
        val empty = timeOfDayProfile(emptyList(), emptyMap(), 0)
        assertThat(empty.eveningShare).isNull()
        assertThat(empty.medianMinutesToHalfGoal).isNull()
        assertThat(empty.hourlyMeanMl).isEqualTo(List(24) { 0.0 })
        val noGoal = timeOfDayProfile(datasetT, emptyMap(), 0)
        assertThat(noGoal.daysReachingHalf).isEqualTo(0)
        assertThat(noGoal.medianMinutesToFullGoal).isNull()
    }
}
