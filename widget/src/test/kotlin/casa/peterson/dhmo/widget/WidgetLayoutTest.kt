package casa.peterson.dhmo.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** The launcher's choice of layout for the cell sizes Android publishes (portrait 73n-16 by 118n-16, landscape 142n-15 by 66n-15). */
class WidgetLayoutTest {
    private fun size(w: Int, h: Int) = DpSize(w.dp, h.dp)

    private val portrait = mapOf(
        "2x1 compact" to (size(130, 102) to WidgetLayout.COMPACT),
        "3x1 wide strip" to (size(203, 102) to WidgetLayout.WIDE_STRIP),
        "1x3 tall strip" to (size(57, 338) to WidgetLayout.TALL_STRIP),
        "2x2 square" to (size(130, 220) to WidgetLayout.SQUARE),
        "4x2 wide card" to (size(276, 220) to WidgetLayout.WIDE_CARD),
        "2x4 tall card" to (size(130, 455) to WidgetLayout.TALL_CARD),
    )

    private val landscape = mapOf(
        "2x1 compact" to (size(269, 51) to WidgetLayout.COMPACT),
        "3x1 wide strip" to (size(412, 51) to WidgetLayout.WIDE_STRIP),
        "1x3 tall strip" to (size(127, 184) to WidgetLayout.TALL_STRIP),
        "2x2 square" to (size(269, 117) to WidgetLayout.SQUARE),
        "4x2 wide card" to (size(554, 117) to WidgetLayout.WIDE_CARD),
        "2x4 tall card" to (size(269, 250) to WidgetLayout.TALL_CARD),
    )

    @Test
    fun `portrait cells map to the intended layout`() {
        portrait.forEach { (name, case) -> assertWithMessage(name).that(layoutFor(case.first)).isEqualTo(case.second) }
    }

    @Test
    fun `landscape cells map to the intended layout`() {
        landscape.forEach { (name, case) -> assertWithMessage(name).that(layoutFor(case.first)).isEqualTo(case.second) }
    }

    @Test
    fun `larger and in-between sizes land on something sensible`() {
        // Same cell counts, wider than the table: a 4x1 is a strip, 3x2 portrait is a square, 4x3 and 4x4 are cards.
        assertThat(layoutFor(size(276, 102))).isEqualTo(WidgetLayout.WIDE_STRIP)
        assertThat(layoutFor(size(203, 220))).isEqualTo(WidgetLayout.SQUARE)
        assertThat(layoutFor(size(276, 338))).isEqualTo(WidgetLayout.TALL_CARD)
        assertThat(layoutFor(size(276, 455))).isEqualTo(WidgetLayout.TALL_CARD)
        assertThat(layoutFor(size(412, 117))).isEqualTo(WidgetLayout.WIDE_CARD)
        assertThat(layoutFor(size(57, 220))).isEqualTo(WidgetLayout.TALL_STRIP)
    }

    @Test
    fun `a size smaller than every breakpoint falls back to the smallest layout`() {
        assertThat(layoutFor(size(57, 102))).isEqualTo(WidgetLayout.COMPACT)
    }

    @Test
    fun `every layout is reachable and a breakpoint maps back to its own layout`() {
        assertThat(WidgetBreakpoints.map { it.layout }.toSet()).containsExactlyElementsIn(WidgetLayout.entries)
        WidgetBreakpoints.forEach { assertThat(layoutForBreakpoint(it.size)).isEqualTo(it.layout) }
    }
}
