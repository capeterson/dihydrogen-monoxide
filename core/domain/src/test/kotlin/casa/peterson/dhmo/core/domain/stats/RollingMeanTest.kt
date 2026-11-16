package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class RollingMeanTest {
    private fun assertSeries(actual: List<Double?>, expected: List<Double?>) {
        assertThat(actual).hasSize(expected.size)
        expected.forEachIndexed { i, want ->
            if (want == null) {
                assertThat(actual[i]).isNull()
            } else {
                assertThat(actual[i]).isNotNull()
                assertThat(actual[i]!!).isWithin(TOLERANCE).of(want)
            }
        }
    }

    @Test
    fun `window must be positive even for empty input`() {
        for (window in listOf(0, -1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { rollingMean(emptyList(), window, false) }
            assertThrows(IllegalArgumentException::class.java) { rollingMean(datasetA(), window, true) }
        }
    }

    @Test
    fun `window one and windows longer than input`() {
        val days = daysOf(listOf(2000, 0, 1000))
        assertSeries(rollingMean(days, 1, false), listOf(2000.0, null, 1000.0))
        assertSeries(rollingMean(days, 1, true), listOf(2000.0, 0.0, 1000.0))
        assertSeries(rollingMean(days, Int.MAX_VALUE, true), listOf(null, null, null))
    }

    @Test
    fun `window 7 without no-log days`() {
        val expected = List(6) { null } + listOf(1925.0, 1941.6667, 2025.0, 1964.2857, 1892.8571, 1907.1429, 1750.0, 1921.4286)
        assertSeries(rollingMean(datasetA(), 7, includeNoLogDays = false), expected)
    }

    @Test
    fun `window 7 with no-log days`() {
        val expected = List(6) { null } + listOf(1650.0, 1664.2857, 1735.7143, 1964.2857, 1892.8571, 1907.1429, 1750.0, 1921.4286)
        assertSeries(rollingMean(datasetA(), 7, includeNoLogDays = true), expected)
    }

    @Test
    fun `window 3 without no-log days`() {
        val expected = listOf(
            null, null, 1950.0, 2150.0, 2225.0, 2150.0, 1716.6667, 1800.0, 1900.0, 2033.3333, 1966.6667, 1883.3333, 1650.0, 1783.3333,
        )
        assertSeries(rollingMean(datasetA(), 3, includeNoLogDays = false), expected)
    }

    @Test
    fun `window without any included day is null`() {
        val days = daysOf(listOf(2000, 0, 0, 2000))
        assertSeries(rollingMean(days, 2, includeNoLogDays = false), listOf(null, 2000.0, null, 2000.0))
    }
}
