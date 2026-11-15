package casa.peterson.dhmo.core.domain.format

import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class VolumeFormatterTest {
    private val nbsp = ' '

    @Test
    fun `conversion factors`() {
        assertThat(VolumeFormatter.fromMl(1000, VolumeUnit.L)).isEqualTo(1.0)
        assertThat(VolumeFormatter.fromMl(2957, VolumeUnit.US_FL_OZ)).isWithin(0.01).of(99.99)
        assertThat(VolumeFormatter.fromMl(284, VolumeUnit.IMP_FL_OZ)).isWithin(0.01).of(9.995)
        assertThat(VolumeFormatter.fromMl(250, VolumeUnit.ML)).isEqualTo(250.0)
    }

    @Test
    fun `fluid ounces convert to whole millilitres with halves up`() {
        assertThat(VolumeFormatter.toMl(8.0, VolumeUnit.US_FL_OZ)).isEqualTo(237)
        assertThat(VolumeFormatter.toMl(16.0, VolumeUnit.US_FL_OZ)).isEqualTo(473)
        assertThat(VolumeFormatter.toMl(24.0, VolumeUnit.US_FL_OZ)).isEqualTo(710)
        assertThat(VolumeFormatter.toMl(10.0, VolumeUnit.IMP_FL_OZ)).isEqualTo(284)
        assertThat(VolumeFormatter.toMl(1.5, VolumeUnit.L)).isEqualTo(1500)
        assertThat(VolumeFormatter.toMl(250.5, VolumeUnit.ML)).isEqualTo(251)
    }

    @Test
    fun `round trip of whole fluid ounces is stable`() {
        for (oz in 1..64) {
            val ml = VolumeFormatter.toMl(oz.toDouble(), VolumeUnit.US_FL_OZ)
            assertThat(VolumeFormatter.fromMl(ml, VolumeUnit.US_FL_OZ)).isWithin(0.02).of(oz.toDouble())
        }
    }

    @Test
    fun `formats millilitres with grouping`() {
        assertThat(VolumeFormatter.format(1250, VolumeUnit.ML, Locale.US)).isEqualTo("1,250${nbsp}ml")
        assertThat(VolumeFormatter.format(1250, VolumeUnit.ML, Locale.GERMANY)).isEqualTo("1.250${nbsp}ml")
        assertThat(VolumeFormatter.format(250, VolumeUnit.ML, Locale.US)).isEqualTo("250${nbsp}ml")
    }

    @Test
    fun `formats litres with at most two decimals`() {
        assertThat(VolumeFormatter.format(2000, VolumeUnit.L, Locale.US)).isEqualTo("2${nbsp}L")
        assertThat(VolumeFormatter.format(1250, VolumeUnit.L, Locale.US)).isEqualTo("1.25${nbsp}L")
        assertThat(VolumeFormatter.format(1250, VolumeUnit.L, Locale.GERMANY)).isEqualTo("1,25${nbsp}L")
        assertThat(VolumeFormatter.format(500, VolumeUnit.L, Locale.US)).isEqualTo("0.5${nbsp}L")
    }

    @Test
    fun `formats fluid ounces with one decimal`() {
        assertThat(VolumeFormatter.format(237, VolumeUnit.US_FL_OZ, Locale.US)).isEqualTo("8${nbsp}fl oz")
        assertThat(VolumeFormatter.format(250, VolumeUnit.US_FL_OZ, Locale.US)).isEqualTo("8.5${nbsp}fl oz")
        assertThat(VolumeFormatter.format(284, VolumeUnit.IMP_FL_OZ, Locale.UK)).isEqualTo("10${nbsp}fl oz")
    }

    @Test
    fun `first run unit follows the locale`() {
        assertThat(VolumeFormatter.defaultUnitFor(Locale.US)).isEqualTo(VolumeUnit.US_FL_OZ)
        assertThat(VolumeFormatter.defaultUnitFor(Locale.UK)).isEqualTo(VolumeUnit.ML)
        assertThat(VolumeFormatter.defaultUnitFor(Locale.GERMANY)).isEqualTo(VolumeUnit.ML)
        assertThat(VolumeFormatter.defaultUnitFor(Locale.CANADA)).isEqualTo(VolumeUnit.ML)
    }

    @Test
    fun `symbols`() {
        assertThat(VolumeFormatter.symbol(VolumeUnit.ML)).isEqualTo("ml")
        assertThat(VolumeFormatter.symbol(VolumeUnit.L)).isEqualTo("L")
        assertThat(VolumeFormatter.symbol(VolumeUnit.US_FL_OZ)).isEqualTo("fl oz")
        assertThat(VolumeFormatter.symbol(VolumeUnit.IMP_FL_OZ)).isEqualTo("fl oz")
    }
}
