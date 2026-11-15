package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CorrelationTest {
    private val xs = DATASET_G.map { it.first }
    private val ys = DATASET_G.map { it.second }

    @Test
    fun `ranks of dataset G totals`() {
        val expected = listOf(2.0, 4.5, 6.5, 11.5, 9.0, 1.0, 14.0, 4.5, 10.0, 6.5, 3.0, 13.0, 11.5, 8.0)
        assertThat(Correlation.ranks(ys)).isEqualTo(expected)
    }

    @Test
    fun `ties get the average rank`() {
        assertThat(Correlation.ranks(listOf(10.0, 20.0, 20.0, 30.0))).containsExactly(1.0, 2.5, 2.5, 4.0).inOrder()
    }

    @Test
    fun `dataset G spearman and pearson`() {
        assertThat(Correlation.spearman(xs, ys)!!).isWithin(1e-6).of(0.974647)
        assertThat(Correlation.pearson(xs, ys)!!).isWithin(1e-6).of(0.947238)
    }

    @Test
    fun `dataset G is a strong positive correlation`() {
        val result = requireNotNull(correlate(DATASET_G))
        assertThat(result.strength).isEqualTo(CorrelationStrength.STRONG)
        assertThat(result.positive).isTrue()
        assertThat(result.n).isEqualTo(14)
        assertThat(result.rho).isWithin(1e-6).of(0.974647)
    }

    @Test
    fun `small spearman case`() {
        val rho = Correlation.spearman(listOf(1.0, 2.0, 3.0, 4.0, 5.0), listOf(2.0, 1.0, 4.0, 3.0, 5.0))
        assertThat(rho!!).isWithin(1e-9).of(0.8)
    }

    @Test
    fun `undefined cases are null`() {
        assertThat(Correlation.pearson(listOf(1.0, 2.0, 3.0), listOf(1.0, 1.0, 1.0))).isNull()
        assertThat(Correlation.pearson(listOf(1.0), listOf(1.0))).isNull()
        assertThat(correlate(DATASET_G.take(13))).isNull()
        assertThat(correlate(DATASET_G.map { it.first to 5.0 })).isNull()
    }

    @Test
    fun `strength bands and sign`() {
        val negative = correlate(DATASET_G.map { it.first to -it.second })!!
        assertThat(negative.positive).isFalse()
        assertThat(negative.strength).isEqualTo(CorrelationStrength.STRONG)
        // Sum of squared rank differences 346 gives rho = 1 - 6 * 346 / (14 * 195) = 0.2396.
        val ys14 = listOf(7, 3, 10, 1, 12, 5, 14, 2, 9, 4, 13, 6, 11, 8)
        val weak = correlate((1..14).map { it.toDouble() to ys14[it - 1].toDouble() })!!
        assertThat(weak.rho).isWithin(1e-4).of(0.2396)
        assertThat(weak.strength).isEqualTo(CorrelationStrength.WEAK)
    }
}
