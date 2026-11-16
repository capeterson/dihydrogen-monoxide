package casa.peterson.dhmo.core.domain.stats

import casa.peterson.dhmo.core.model.DailyTotal
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class DayStatTest {
    private fun total(date: LocalDate, ml: Int, count: Int) = DailyTotal(date, ml, count, Instant.EPOCH, Instant.EPOCH)

    @Test
    fun `builds a contiguous list filling missing days`() {
        val from = LocalDate.of(2026, 3, 2)
        val to = LocalDate.of(2026, 3, 5)
        val totals = listOf(total(from, 1500, 3), total(to, 900, 1))
        val goals = mapOf(from to 2000, from.plusDays(1) to 2100)
        val result = buildDayStats(from, to, totals, goals)
        assertThat(result.map { it.date }).containsExactly(from, from.plusDays(1), from.plusDays(2), to).inOrder()
        assertThat(result[0]).isEqualTo(DayStat(from, 1500, 2000, 3))
        assertThat(result[1]).isEqualTo(DayStat(from.plusDays(1), 0, 2100, 0))
        assertThat(result[2]).isEqualTo(DayStat(from.plusDays(2), 0, null, 0))
        assertThat(result[3].logged).isTrue()
        assertThat(result[1].logged).isFalse()
    }

    @Test
    fun `reversed range is empty`() {
        val day = LocalDate.of(2026, 3, 2)
        assertThat(buildDayStats(day, day.minusDays(1), emptyList(), emptyMap())).isEmpty()
    }
}
