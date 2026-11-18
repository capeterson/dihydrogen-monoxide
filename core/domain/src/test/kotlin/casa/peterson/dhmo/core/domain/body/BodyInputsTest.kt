package casa.peterson.dhmo.core.domain.body

import casa.peterson.dhmo.core.model.settings.BodyUnits
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BodyInputsTest {
    @Test
    fun `metric to imperial converts weight and height`() {
        val result = BodyInputs(BodyUnits.METRIC, weight = "70", heightCm = "175").switchTo(BodyUnits.IMPERIAL)
        assertThat(result.units).isEqualTo(BodyUnits.IMPERIAL)
        assertThat(result.weight).isEqualTo("154.3")
        assertThat(result.heightFeet).isEqualTo("5")
        assertThat(result.heightInches).isEqualTo("8.9")
    }

    @Test
    fun `imperial to metric converts back`() {
        val result = BodyInputs(BodyUnits.IMPERIAL, weight = "154.3", heightFeet = "5", heightInches = "9").switchTo(BodyUnits.METRIC)
        assertThat(result.weight).isEqualTo("70")
        assertThat(result.heightCm).isEqualTo("175.3")
    }

    @Test
    fun `a round trip returns to the starting values`() {
        val start = BodyInputs(BodyUnits.METRIC, weight = "70", heightCm = "175")
        val back = start.switchTo(BodyUnits.IMPERIAL).switchTo(BodyUnits.METRIC)
        assertThat(back.weight).isEqualTo("70")
        assertThat(back.heightCm).isEqualTo("175")
    }

    @Test
    fun `blank and unreadable text is left alone`() {
        val blank = BodyInputs(BodyUnits.METRIC).switchTo(BodyUnits.IMPERIAL)
        assertThat(blank.weight).isEmpty()
        assertThat(blank.heightFeet).isEmpty()
        assertThat(blank.heightInches).isEmpty()
        val junk = BodyInputs(BodyUnits.METRIC, weight = "abc", heightCm = "tall").switchTo(BodyUnits.IMPERIAL)
        assertThat(junk.weight).isEqualTo("abc")
        assertThat(junk.heightFeet).isEmpty()
    }

    @Test
    fun `a decimal comma is understood`() {
        assertThat(BodyInputs(BodyUnits.METRIC, weight = "70,5").switchTo(BodyUnits.IMPERIAL).weight).isEqualTo("155.4")
    }

    @Test
    fun `switching to the same units changes nothing`() {
        val inputs = BodyInputs(BodyUnits.METRIC, weight = "70")
        assertThat(inputs.switchTo(BodyUnits.METRIC)).isEqualTo(inputs)
    }

    @Test
    fun `feet and inches never carry twelve inches`() {
        // 182.8 cm is 71.97 inches, which is 6 ft 0 in.
        assertThat(BodyMeasures.cmToFeetInches(182.8)).isEqualTo(6 to 0.0)
        assertThat(BodyMeasures.cmToFeetInches(152.4)).isEqualTo(5 to 0.0)
    }

    @Test
    fun `stored values are always kilograms and centimetres`() {
        val imperial = BodyInputs(BodyUnits.IMPERIAL, weight = "154.3", heightFeet = "5", heightInches = "9")
        assertThat(imperial.weightKg()).isWithin(0.01).of(69.99)
        assertThat(imperial.heightCmValue()).isWithin(0.01).of(175.26)
        val metric = BodyInputs(BodyUnits.METRIC, weight = "70", heightCm = "175")
        assertThat(metric.weightKg()).isEqualTo(70.0)
        assertThat(metric.heightCmValue()).isEqualTo(175.0)
    }

    @Test
    fun `height is optional and unreadable height is distinguished from blank`() {
        assertThat(BodyInputs(BodyUnits.METRIC).heightBlank).isTrue()
        assertThat(BodyInputs(BodyUnits.IMPERIAL).heightBlank).isTrue()
        val bad = BodyInputs(BodyUnits.IMPERIAL, heightFeet = "x")
        assertThat(bad.heightBlank).isFalse()
        assertThat(bad.heightCmValue()).isNull()
        assertThat(BodyInputs(BodyUnits.IMPERIAL, heightInches = "30").heightCmValue()).isWithin(0.01).of(76.2)
    }

    @Test
    fun `plain formatting trims zeros and keeps a point`() {
        assertThat(BodyMeasures.plain(70.0)).isEqualTo("70")
        assertThat(BodyMeasures.plain(154.32)).isEqualTo("154.3")
        assertThat(BodyMeasures.plain(0.04)).isEqualTo("0")
    }
}
