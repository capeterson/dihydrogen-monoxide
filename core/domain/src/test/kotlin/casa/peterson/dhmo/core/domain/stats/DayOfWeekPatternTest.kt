package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import org.junit.Test

class DayOfWeekPatternTest {
    @Test
    fun `dataset A`() {
        val p = dayOfWeekPattern(datasetA(), includeNoLogDays = false)
        assertThat(p.byDay.map { it.day }).containsExactlyElementsIn(DayOfWeek.entries).inOrder()
        val expected = listOf(2150.0 to 2, 2050.0 to 2, 1600.0 to 1, 2250.0 to 2, 2000.0 to 2, 1450.0 to 2, 1800.0 to 2)
        p.byDay.forEachIndexed { i, mean ->
            assertThat(mean.meanMl!!).isWithin(TOLERANCE).of(expected[i].first)
            assertThat(mean.sampleCount).isEqualTo(expected[i].second)
        }
        assertThat(p.weakest).isEqualTo(DayOfWeek.SATURDAY)
        assertThat(p.overallMeanMl!!).isWithin(TOLERANCE).of(1923.0769)
    }

    @Test
    fun `weekday without samples has a null mean and no weakest when none has two`() {
        val p = dayOfWeekPattern(daysOf(listOf(2000, 1000, 1500)), includeNoLogDays = false)
        assertThat(p.byDay[3].meanMl).isNull()
        assertThat(p.byDay[3].sampleCount).isEqualTo(0)
        assertThat(p.weakest).isNull()
    }

    @Test
    fun `ties go to the earlier weekday`() {
        val p = dayOfWeekPattern(daysOf(List(14) { 1000 }), includeNoLogDays = false)
        assertThat(p.weakest).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun `empty input`() {
        val p = dayOfWeekPattern(emptyList(), includeNoLogDays = true)
        assertThat(p.overallMeanMl).isNull()
        assertThat(p.weakest).isNull()
    }
}
