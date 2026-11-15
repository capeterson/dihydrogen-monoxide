package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class AnomaliesTest {
    @Test
    fun `dataset A has exactly one anomaly`() {
        val result = anomalies(datasetA())
        assertThat(result).hasSize(1)
        val a = result.single()
        assertThat(a.date).isEqualTo(LocalDate.of(2026, 3, 14))
        assertThat(a.totalMl).isEqualTo(900)
        assertThat(a.direction).isEqualTo(AnomalyDirection.LOW)
        assertThat(a.medianMl).isEqualTo(2000.0)
        assertThat(a.robustSdMl).isWithin(0.01).of(296.52)
    }

    @Test
    fun `dataset F flags the high and the low day`() {
        val result = anomalies(daysOf(DATASET_F))
        assertThat(result.map { it.date }).containsExactly(MONDAY_MARCH_2.plusDays(8), MONDAY_MARCH_2.plusDays(10)).inOrder()
        assertThat(result.map { it.direction }).containsExactly(AnomalyDirection.HIGH, AnomalyDirection.LOW).inOrder()
        assertThat(result.map { it.robustSdMl }).containsExactly(200.0, 200.0)
    }

    private fun flaggedAfterEightRegularDays(last: Int) = anomalies(daysOf(List(8) { 2000 } + last)).size

    @Test
    fun `zero MAD uses the ten percent floor and a strict threshold`() {
        assertThat(flaggedAfterEightRegularDays(2600)).isEqualTo(1)
        assertThat(flaggedAfterEightRegularDays(2500)).isEqualTo(0)
        assertThat(flaggedAfterEightRegularDays(2150)).isEqualTo(0)
    }

    @Test
    fun `too few earlier logged days and no-log days are not evaluated`() {
        assertThat(anomalies(daysOf(List(6) { 2000 } + 5000))).isEmpty()
        assertThat(anomalies(daysOf(List(8) { 2000 } + 0))).isEmpty()
    }
}
