package casa.peterson.dhmo.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The six arrangements of the widget. Each has its own file; all of them draw the same [WidgetModel]. */
internal enum class WidgetLayout { COMPACT, WIDE_STRIP, TALL_STRIP, SQUARE, WIDE_CARD, TALL_CARD }

/**
 * The smallest widget size, in dp, at which [layout] is used. `SizeMode.Responsive` composes one layout per
 * breakpoint and the launcher shows the one with the largest area that still fits the widget, so the numbers below are
 * chosen against the cell sizes Android publishes (portrait 73n-16 wide and 118n-16 tall, landscape 142n-15 wide and
 * 66n-15 tall): a layout is only listed for sizes where it fits, and a bigger layout never fits a smaller cell.
 *
 * A layout may have two breakpoints, because a cell is wider and shorter in landscape: a 2x1 widget in landscape
 * (269x51) is wider than a 3x1 in portrait (203x102), so "wide strip" needs to be reachable from both without the
 * landscape 2x1 qualifying.
 */
internal data class Breakpoint(val size: DpSize, val layout: WidgetLayout)

internal val WidgetBreakpoints: List<Breakpoint> = listOf(
    Breakpoint(DpSize(110.dp, 40.dp), WidgetLayout.COMPACT),
    Breakpoint(DpSize(112.dp, 72.dp), WidgetLayout.COMPACT),
    Breakpoint(DpSize(190.dp, 40.dp), WidgetLayout.COMPACT),
    Breakpoint(DpSize(50.dp, 170.dp), WidgetLayout.TALL_STRIP),
    // A portrait 1x3 (about 57 x 338): the same layout, told apart from the short landscape one so its jar can keep a
    // steady size instead of stretching to fill the height.
    Breakpoint(DpSize(50.dp, 280.dp), WidgetLayout.TALL_STRIP),
    Breakpoint(DpSize(190.dp, 60.dp), WidgetLayout.WIDE_STRIP),
    Breakpoint(DpSize(300.dp, 40.dp), WidgetLayout.WIDE_STRIP),
    Breakpoint(DpSize(120.dp, 200.dp), WidgetLayout.SQUARE),
    Breakpoint(DpSize(200.dp, 109.dp), WidgetLayout.SQUARE),
    Breakpoint(DpSize(260.dp, 200.dp), WidgetLayout.WIDE_CARD),
    Breakpoint(DpSize(400.dp, 90.dp), WidgetLayout.WIDE_CARD),
    Breakpoint(DpSize(120.dp, 400.dp), WidgetLayout.TALL_CARD),
    Breakpoint(DpSize(250.dp, 230.dp), WidgetLayout.TALL_CARD),
    Breakpoint(DpSize(260.dp, 400.dp), WidgetLayout.TALL_CARD),
)

/** Launchers round sizes to whole dp, and Glance allows the same one dp of slack. */
private val Tolerance = 1.dp

/**
 * The breakpoint the launcher picks for a widget of [available] size: of those that fit, the one with the largest area;
 * when none fits, the smallest. This is the same rule Glance's responsive mode uses, so a test of this function is a test
 * of what the user sees.
 */
internal fun selectBreakpoint(available: DpSize, breakpoints: List<Breakpoint> = WidgetBreakpoints): Breakpoint {
    val fitting = breakpoints.filter { it.size.width <= available.width + Tolerance && it.size.height <= available.height + Tolerance }
    return fitting.maxByOrNull { it.size.area() } ?: breakpoints.minBy { it.size.area() }
}

internal fun layoutFor(available: DpSize): WidgetLayout = selectBreakpoint(available).layout

/** The layout of a breakpoint size handed back by Glance's `LocalSize`. */
internal fun layoutForBreakpoint(size: DpSize): WidgetLayout = WidgetBreakpoints.firstOrNull { it.size == size }?.layout ?: layoutFor(size)

private fun DpSize.area(): Float = width.value * height.value

/**
 * The largest system font scale a layout's text follows. A widget's size is fixed by the launcher's grid, so its text
 * cannot reflow onto more lines the way a screen scrolls: in the strips, the compact, the square and the wide card every
 * line is already sized to its space, and larger text cut numbers off and split words. Only the tall card has room to
 * grow. Checked at font scale 2.0 by `WidgetRenderTest`; the app itself follows the full setting.
 */
internal fun WidgetLayout.maxFontScale(): Float = if (this == WidgetLayout.TALL_CARD) TALL_CARD_MAX_FONT_SCALE else 1f

private const val TALL_CARD_MAX_FONT_SCALE = 1.3f

/** What every text size in [layout] is multiplied by, so the system's [fontScale] times it stays within the cap. */
internal fun textScaleFor(layout: WidgetLayout, fontScale: Float): Float = (layout.maxFontScale() / fontScale).coerceAtMost(1f)
