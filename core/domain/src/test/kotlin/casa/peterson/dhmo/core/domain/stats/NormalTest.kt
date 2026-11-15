package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NormalTest {
    @Test
    fun `cdf golden values`() {
        assertThat(Normal.cdf(0.5)).isWithin(1e-6).of(0.691462)
        assertThat(Normal.cdf(1.0)).isWithin(1e-6).of(0.841345)
        assertThat(Normal.cdf(1.96)).isWithin(1e-6).of(0.975002)
        assertThat(Normal.cdf(2.5)).isWithin(1e-6).of(0.993790)
        assertThat(Normal.cdf(3.0)).isWithin(1e-6).of(0.998650)
        assertThat(Normal.cdf(-1.96)).isWithin(1e-6).of(0.024998)
    }

    @Test
    fun `erf is odd and has the right anchors`() {
        assertThat(Normal.erf(0.0)).isWithin(1e-7).of(0.0)
        assertThat(Normal.erf(1.0)).isWithin(1.5e-7).of(0.8427008)
        assertThat(Normal.erf(-1.0)).isEqualTo(-Normal.erf(1.0))
        assertThat(Normal.cdf(0.0)).isWithin(1e-7).of(0.5)
    }
}
