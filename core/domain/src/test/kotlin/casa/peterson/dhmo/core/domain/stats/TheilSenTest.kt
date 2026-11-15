package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TheilSenTest {
    private fun fit(values: List<Int>) = requireNotNull(theilSen(values.indices.map { it.toDouble() }, values.asDoubles()))

    @Test
    fun `dataset A logged days`() {
        val xs = (0..13).filter { it != 2 }.map { it.toDouble() }
        val result = requireNotNull(theilSen(xs, DATASET_A_LOGGED))
        assertThat(result.slopePerDay).isWithin(TOLERANCE).of(-2.272727)
        assertThat(result.intercept).isWithin(TOLERANCE).of(2022.7273)
        assertThat(result.slopePerWeek).isWithin(TOLERANCE).of(-15.9091)
    }

    @Test
    fun `dataset B`() {
        val result = fit(DATASET_B)
        assertThat(result.slopePerDay).isWithin(TOLERANCE).of(66.666667)
        assertThat(result.intercept).isWithin(TOLERANCE).of(1500.0)
        assertThat(result.slopePerWeek).isWithin(TOLERANCE).of(466.6667)
    }

    @Test
    fun `dataset C ties`() {
        val result = fit(DATASET_C)
        assertThat(result.slopePerDay).isWithin(TOLERANCE).of(0.0)
        assertThat(result.intercept).isWithin(TOLERANCE).of(2000.0)
    }

    @Test
    fun `dataset D`() {
        val result = fit(DATASET_D)
        assertThat(result.slopePerDay).isWithin(TOLERANCE).of(-66.666667)
        assertThat(result.intercept).isWithin(TOLERANCE).of(2400.0)
    }

    @Test
    fun `dataset E is robust to the outlier`() {
        val result = fit(DATASET_E)
        assertThat(result.slopePerDay).isWithin(TOLERANCE).of(66.666667)
        assertThat(result.intercept).isWithin(TOLERANCE).of(1500.0)
    }

    @Test
    fun `constant series has slope zero`() {
        assertThat(fit(List(8) { 2000 }).slopePerDay).isEqualTo(0.0)
    }

    @Test
    fun `fewer than two points or no valid pair is null`() {
        assertThat(theilSen(listOf(1.0), listOf(5.0))).isNull()
        assertThat(theilSen(emptyList(), emptyList())).isNull()
        assertThat(theilSen(listOf(1.0, 1.0), listOf(5.0, 6.0))).isNull()
    }
}
