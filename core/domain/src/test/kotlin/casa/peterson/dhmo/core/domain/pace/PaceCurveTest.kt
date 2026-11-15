package casa.peterson.dhmo.core.domain.pace

import casa.peterson.dhmo.core.model.settings.CurvePoint
import casa.peterson.dhmo.core.model.settings.PaceCurveSettings
import casa.peterson.dhmo.core.model.settings.PaceCurveType
import com.google.common.truth.Truth.assertThat
import kotlin.math.floor
import org.junit.Test

class PaceCurveTest {
    private val curve = PaceCurve.DEFAULT

    private fun pts(vararg v: Int) = v.toList().chunked(2).map { CurvePoint(it[0], it[1]) }

    @Test
    fun `reference table`() {
        val rows = listOf(
            Triple(0.0, 0.0, listOf(0, 0)),
            Triple(0.05, 0.075, listOf(150, 184)),
            Triple(0.10, 0.15, listOf(300, 368)),
            Triple(0.25, 0.309375, listOf(619, 758)),
            Triple(0.50, 0.575, listOf(1150, 1409)),
            Triple(0.75, 0.840625, listOf(1681, 2060)),
            Triple(0.875, 0.9734375, listOf(1947, 2385)),
            Triple(0.90, 1.0, listOf(2000, 2450)),
            Triple(1.0, 1.0, listOf(2000, 2450)),
        )
        for ((x, f, ml) in rows) {
            assertThat(curve.fraction(x)).isWithin(1e-9).of(f)
            assertThat(floor(2000 * curve.fraction(x) + 0.5).toInt()).isEqualTo(ml[0])
            assertThat(floor(2450 * curve.fraction(x) + 0.5).toInt()).isEqualTo(ml[1])
        }
    }

    @Test
    fun `fraction clamps x`() {
        assertThat(curve.fraction(-1.0)).isEqualTo(0.0)
        assertThat(curve.fraction(2.0)).isEqualTo(1.0)
    }

    @Test
    fun `validation`() {
        assertThat(PaceCurve.validate(PaceCurve.DEFAULT.points)).isTrue()
        assertThat(PaceCurve.validate(PaceCurve.LINEAR.points)).isTrue()
        assertThat(PaceCurve.validate(pts(0, 0))).isFalse()
        assertThat(PaceCurve.validate(emptyList())).isFalse()
        assertThat(PaceCurve.validate(pts(0, 1, 1000, 1000))).isFalse()
        assertThat(PaceCurve.validate(pts(0, 0, 1000, 999))).isFalse()
        assertThat(PaceCurve.validate(pts(0, 0, 500, 500, 500, 600, 1000, 1000))).isFalse()
        assertThat(PaceCurve.validate(pts(0, 0, 500, 600, 600, 500, 1000, 1000))).isFalse()
        assertThat(PaceCurve.validate(pts(0, 0, 500, 500, 600, 500, 1000, 1000))).isTrue()
        val twelve = (0..11).map { CurvePoint(it * 1000 / 11, it * 1000 / 11) }
        assertThat(PaceCurve.validate(twelve)).isTrue()
        val thirteen = (0..12).map { CurvePoint(it * 1000 / 12, it * 1000 / 12) }
        assertThat(PaceCurve.validate(thirteen)).isFalse()
    }

    @Test
    fun `from settings`() {
        assertThat(PaceCurve.from(PaceCurveSettings(PaceCurveType.DEFAULT))).isEqualTo(PaceCurve.DEFAULT)
        assertThat(PaceCurve.from(PaceCurveSettings(PaceCurveType.LINEAR))).isEqualTo(PaceCurve.LINEAR)
        val custom = pts(0, 0, 500, 800, 1000, 1000)
        assertThat(PaceCurve.from(PaceCurveSettings(PaceCurveType.CUSTOM, custom))).isEqualTo(PaceCurve(custom))
        assertThat(PaceCurve.from(PaceCurveSettings(PaceCurveType.CUSTOM, emptyList()))).isEqualTo(PaceCurve.DEFAULT)
    }

    @Test
    fun `lastXAtOrBelow on the default curve`() {
        assertThat(curve.lastXAtOrBelow(0.15)!!).isWithin(1e-9).of(0.1)
        assertThat(curve.lastXAtOrBelow(0.10)!!).isWithin(1e-4).of(0.0667)
        assertThat(curve.lastXAtOrBelow(0.35)!!).isWithin(1e-4).of(0.28824)
        assertThat(curve.lastXAtOrBelow(0.0)!!).isEqualTo(0.0)
    }

    @Test
    fun `lastXAtOrBelow returns the right end of flat segments`() {
        val flat = PaceCurve(pts(0, 0, 300, 500, 700, 500, 1000, 1000))
        assertThat(flat.lastXAtOrBelow(0.5)!!).isWithin(1e-9).of(0.7)
        assertThat(flat.lastXAtOrBelow(0.6)!!).isWithin(1e-9).of(0.7 + 0.3 * 0.2)
        assertThat(flat.lastXAtOrBelow(0.25)!!).isWithin(1e-9).of(0.15)
        val leadingFlat = PaceCurve(pts(0, 0, 200, 0, 1000, 1000))
        assertThat(leadingFlat.lastXAtOrBelow(0.0)!!).isWithin(1e-9).of(0.2)
    }

    @Test
    fun `q at or above one is null and below zero is zero`() {
        assertThat(curve.lastXAtOrBelow(1.0)).isNull()
        assertThat(curve.lastXAtOrBelow(1.5)).isNull()
        assertThat(curve.lastXAtOrBelow(-0.1)).isEqualTo(0.0)
    }
}
