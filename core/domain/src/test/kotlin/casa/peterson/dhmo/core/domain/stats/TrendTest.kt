package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrendTest {
    @Test
    fun `dataset A is stable`() {
        val t = trend(datasetA(), includeNoLogDays = false)
        assertThat(t.direction).isEqualTo(TrendDirection.STABLE)
        assertThat(t.n).isEqualTo(13)
    }

    @Test
    fun `dataset B is up`() {
        val t = trend(daysOf(DATASET_B), includeNoLogDays = false)
        assertThat(t.direction).isEqualTo(TrendDirection.UP)
        assertThat(t.slopePerWeekMl).isWithin(TOLERANCE).of(466.6667)
        assertThat(t.changeOverRangeMl).isWithin(TOLERANCE).of(600.0)
        assertThat(t.pValue!!).isWithin(1e-5).of(0.000677)
        assertThat(t.n).isEqualTo(10)
    }

    @Test
    fun `dataset C is stable`() {
        assertThat(trend(daysOf(DATASET_C), false).direction).isEqualTo(TrendDirection.STABLE)
    }

    @Test
    fun `dataset D is down`() {
        assertThat(trend(daysOf(DATASET_D), false).direction).isEqualTo(TrendDirection.DOWN)
    }

    @Test
    fun `dataset E with an outlier is still up`() {
        assertThat(trend(daysOf(DATASET_E), false).direction).isEqualTo(TrendDirection.UP)
    }

    @Test
    fun `constant series is stable`() {
        assertThat(trend(daysOf(List(8) { 2000 }), false).direction).isEqualTo(TrendDirection.STABLE)
    }

    @Test
    fun `fewer than eight included days is insufficient`() {
        val t = trend(daysOf(DATASET_B.take(7)), includeNoLogDays = false)
        assertThat(t.direction).isEqualTo(TrendDirection.INSUFFICIENT_DATA)
        assertThat(t.n).isEqualTo(7)
        assertThat(t.pValue).isNull()
        val withGaps = daysOf(listOf(2000, 0, 2000, 0, 2000, 0, 2000, 0, 2000, 2000, 2000))
        assertThat(trend(withGaps, includeNoLogDays = false).direction).isEqualTo(TrendDirection.INSUFFICIENT_DATA)
    }
}
