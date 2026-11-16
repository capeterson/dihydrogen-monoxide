package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConsistencyTest {
    @Test
    fun `dataset A scores 76`() {
        assertThat(consistencyScore(datasetA(), includeNoLogDays = false)).isEqualTo(76)
    }

    @Test
    fun `identical days score 100`() {
        assertThat(consistencyScore(daysOf(List(10) { 2000 }), false)).isEqualTo(100)
    }

    @Test
    fun `a coefficient of variation above one clamps to zero`() {
        assertThat(consistencyScore(daysOf(listOf(100, 100, 100, 100, 100, 100, 5000)), false)).isEqualTo(0)
    }

    @Test
    fun `fewer than seven included days or a zero mean is null`() {
        assertThat(consistencyScore(daysOf(List(6) { 2000 }), false)).isNull()
        assertThat(consistencyScore(daysOf(List(7) { 0 }), includeNoLogDays = true)).isNull()
    }

    @Test
    fun `only the last 30 days count`() {
        val days = daysOf(List(10) { 100 } + List(30) { 2000 })
        assertThat(consistencyScore(days, false)).isEqualTo(100)
    }
}
