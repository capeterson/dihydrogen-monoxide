package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MannKendallTest {
    private fun mk(values: List<Int>) = requireNotNull(mannKendall(values.asDoubles()))

    @Test
    fun `dataset A logged days`() {
        val r = requireNotNull(mannKendall(DATASET_A_LOGGED))
        assertThat(r.s).isEqualTo(-1)
        assertThat(r.variance).isWithin(TOLERANCE).of(267.6667)
        assertThat(r.z).isEqualTo(0.0)
        assertThat(r.pValue).isEqualTo(1.0)
    }

    @Test
    fun `dataset B`() {
        val r = mk(DATASET_B)
        assertThat(r.s).isEqualTo(39)
        assertThat(r.variance).isWithin(TOLERANCE).of(125.0)
        assertThat(r.z).isWithin(1e-5).of(3.398823)
        assertThat(r.pValue).isWithin(1e-5).of(0.000677)
    }

    @Test
    fun `dataset C ties`() {
        val r = mk(DATASET_C)
        assertThat(r.s).isEqualTo(2)
        assertThat(r.variance).isWithin(TOLERANCE).of(94.6667)
        assertThat(r.z).isWithin(TOLERANCE).of(0.102778)
        assertThat(r.pValue).isWithin(TOLERANCE).of(0.918139)
    }

    @Test
    fun `dataset D`() {
        val r = mk(DATASET_D)
        assertThat(r.s).isEqualTo(-58)
        assertThat(r.variance).isWithin(TOLERANCE).of(212.6667)
        assertThat(r.z).isWithin(1e-5).of(-3.908635)
        assertThat(r.pValue).isWithin(1e-6).of(0.0000929)
    }

    @Test
    fun `dataset E`() {
        val r = mk(DATASET_E)
        assertThat(r.s).isEqualTo(31)
        assertThat(r.variance).isWithin(TOLERANCE).of(125.0)
        assertThat(r.z).isWithin(1e-5).of(2.683282)
        assertThat(r.pValue).isWithin(1e-5).of(0.007290)
    }

    @Test
    fun `constant series`() {
        val r = mk(List(8) { 2000 })
        assertThat(r.s).isEqualTo(0)
        assertThat(r.variance).isEqualTo(0.0)
        assertThat(r.z).isEqualTo(0.0)
        assertThat(r.pValue).isEqualTo(1.0)
    }

    @Test
    fun `fewer than three points is null`() {
        assertThat(mannKendall(listOf(1.0, 2.0))).isNull()
    }
}
