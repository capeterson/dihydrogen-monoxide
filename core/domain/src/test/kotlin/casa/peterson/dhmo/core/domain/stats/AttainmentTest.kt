package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class AttainmentTest {
    @Test
    fun `dataset A without no-log days`() {
        val a = attainment(datasetA(), includeNoLogDays = false)
        assertThat(a.dayCount).isEqualTo(13)
        assertThat(a.daysMet).isEqualTo(8)
        assertThat(a.rate).isWithin(1e-6).of(0.615385)
        assertThat(listOf(a.below50, a.from50To80, a.from80To100, a.atLeast100)).containsExactly(1, 1, 3, 8).inOrder()
    }

    @Test
    fun `dataset A with no-log days`() {
        val a = attainment(datasetA(), includeNoLogDays = true)
        assertThat(a.dayCount).isEqualTo(14)
        assertThat(a.daysMet).isEqualTo(8)
        assertThat(a.rate).isWithin(1e-6).of(0.571429)
        assertThat(listOf(a.below50, a.from50To80, a.from80To100, a.atLeast100)).containsExactly(2, 1, 3, 8).inOrder()
    }

    @Test
    fun `bucket edges are exact`() {
        fun bucket(total: Int): List<Int> {
            val a = attainment(daysOf(listOf(total)), includeNoLogDays = true)
            return listOf(a.below50, a.from50To80, a.from80To100, a.atLeast100)
        }
        assertThat(bucket(1600)).containsExactly(0, 0, 1, 0).inOrder()
        assertThat(bucket(1599)).containsExactly(0, 1, 0, 0).inOrder()
        assertThat(bucket(1000)).containsExactly(0, 1, 0, 0).inOrder()
        assertThat(bucket(999)).containsExactly(1, 0, 0, 0).inOrder()
        assertThat(bucket(2000)).containsExactly(0, 0, 0, 1).inOrder()
    }

    @Test
    fun `days without a goal are not counted and an empty list has rate zero`() {
        val noGoal = daysOf(listOf(2000, 2500), goal = null) + DayStat(LocalDate.of(2026, 3, 4), 100, 0, 1)
        val a = attainment(noGoal, includeNoLogDays = true)
        assertThat(a.dayCount).isEqualTo(0)
        assertThat(a.rate).isEqualTo(0.0)
    }

    @Test
    fun `day result classes`() {
        assertThat(dayResult(DayStat(MONDAY_MARCH_2, 2000, 2000, 2))).isEqualTo(DayResult.MET)
        assertThat(dayResult(DayStat(MONDAY_MARCH_2, 1600, 2000, 2))).isEqualTo(DayResult.PARTLY_MET)
        assertThat(dayResult(DayStat(MONDAY_MARCH_2, 1599, 2000, 2))).isEqualTo(DayResult.MISSED)
        assertThat(dayResult(DayStat(MONDAY_MARCH_2, 0, 2000, 0))).isEqualTo(DayResult.NO_DATA)
        assertThat(dayResult(DayStat(MONDAY_MARCH_2, 1500, null, 2))).isEqualTo(DayResult.NO_DATA)
    }
}
