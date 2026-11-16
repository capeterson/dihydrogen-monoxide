package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class DescriptiveTest {
    @Test
    fun `mean median and sample sd`() {
        val values = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        assertThat(Descriptive.mean(values)).isEqualTo(5.0)
        assertThat(Descriptive.median(values)).isEqualTo(4.5)
        assertThat(Descriptive.median(listOf(3.0, 1.0, 2.0))).isEqualTo(2.0)
        assertThat(Descriptive.sd(values)).isWithin(1e-6).of(2.138090)
    }

    @Test
    fun `sd is zero for fewer than two values and empty input is rejected`() {
        assertThat(Descriptive.sd(listOf(5.0))).isEqualTo(0.0)
        assertThat(Descriptive.sd(emptyList())).isEqualTo(0.0)
        assertThrows(IllegalArgumentException::class.java) { Descriptive.mean(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { Descriptive.median(emptyList()) }
    }
}
