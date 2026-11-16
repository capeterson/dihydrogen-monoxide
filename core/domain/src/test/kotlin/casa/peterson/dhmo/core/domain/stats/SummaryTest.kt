package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SummaryTest {
    @Test
    fun `dataset A without no-log days`() {
        val s = requireNotNull(summary(datasetA(), includeNoLogDays = false))
        assertThat(s.dayCount).isEqualTo(13)
        assertThat(s.totalMl).isEqualTo(25_000L)
        assertThat(s.meanMl).isWithin(TOLERANCE).of(1923.0769)
        assertThat(s.medianMl).isEqualTo(2000.0)
        assertThat(s.sdMl).isWithin(TOLERANCE).of(458.9914)
        assertThat(s.minMl).isEqualTo(900)
        assertThat(s.maxMl).isEqualTo(2500)
    }

    @Test
    fun `dataset A with no-log days`() {
        val s = requireNotNull(summary(datasetA(), includeNoLogDays = true))
        assertThat(s.dayCount).isEqualTo(14)
        assertThat(s.totalMl).isEqualTo(25_000L)
        assertThat(s.meanMl).isWithin(TOLERANCE).of(1785.7143)
        assertThat(s.medianMl).isEqualTo(2000.0)
        assertThat(s.sdMl).isWithin(TOLERANCE).of(677.2196)
        assertThat(s.minMl).isEqualTo(0)
        assertThat(s.maxMl).isEqualTo(2500)
    }

    @Test
    fun `dataset B`() {
        val s = requireNotNull(summary(daysOf(DATASET_B), includeNoLogDays = false))
        assertThat(s.meanMl).isWithin(TOLERANCE).of(1785.0)
        assertThat(s.medianMl).isWithin(TOLERANCE).of(1775.0)
        assertThat(s.sdMl).isWithin(TOLERANCE).of(201.4531)
    }

    @Test
    fun `null when no day is included`() {
        assertThat(summary(emptyList(), includeNoLogDays = true)).isNull()
        assertThat(summary(daysOf(listOf(0, 0)), includeNoLogDays = false)).isNull()
    }
}
