@file:Suppress("MagicNumber") // Paddings and strokes are the design's `.area` SVG rules.

package casa.peterson.dhmo.feature.insights.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors
import casa.peterson.dhmo.core.designsystem.theme.NumeralFontFamily
import kotlin.math.max

private val ChartHeight = 150.dp
private val SidePadding = 10.dp
private val TopPadding = 12.dp
private val LabelBand = 22.dp
private const val HEADROOM = 1.15

/**
 * The daily totals as the design's "Seven days" chart: a soft area under a 2 dp line, an amber dashed goal line across the
 * whole card, a ringed dot on the latest day and monospaced labels along the bottom. [values] has one entry per day of the
 * range (null for days with no total yet, such as the future), so a part-finished week keeps its seven slots.
 * [average] is an optional thin line, drawn over the same slots. The chart is one TalkBack node described by [description].
 */
@Composable
internal fun DailyAreaChart(
    values: List<Double?>,
    labels: List<String>,
    labelEvery: Int,
    description: String,
    goal: Double?,
    modifier: Modifier = Modifier,
    average: List<Double?>? = null,
) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalDhmoExtraColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = NumeralFontFamily, fontSize = 10.sp, color = colors.onSurfaceVariant)
    Canvas(modifier.fillMaxWidth().height(ChartHeight).clearAndSetSemantics { contentDescription = description }) {
        val left = SidePadding.toPx()
        val right = size.width - SidePadding.toPx()
        val top = TopPadding.toPx()
        val bottom = size.height - LabelBand.toPx()
        val slots = max(values.size - 1, 1)
        val ceiling = max(values.filterNotNull().maxOrNull() ?: 0.0, goal ?: 0.0).coerceAtLeast(1.0) * HEADROOM
        fun x(i: Int) = left + (right - left) * i / slots
        fun y(v: Double) = bottom - (bottom - top) * (v / ceiling).toFloat()
        val points = values.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) } }
        if (goal != null) {
            drawLine(
                extras.glow,
                Offset(0f, y(goal)),
                Offset(size.width, y(goal)),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
        if (points.size >= 2) {
            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, bottom)
                lineTo(points.first().x, bottom)
                close()
            }
            drawPath(area, extras.water.copy(alpha = 0.55f))
            drawPath(line, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        average?.let { series ->
            val avg = series.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) } }
            if (avg.size >= 2) {
                val path = Path().apply {
                    moveTo(avg.first().x, avg.first().y)
                    avg.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, colors.onSurfaceVariant, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        points.lastOrNull()?.let {
            drawCircle(colors.surfaceContainerLow, 4.dp.toPx(), it)
            drawCircle(colors.primary, 4.dp.toPx(), it, style = Stroke(2.dp.toPx()))
        }
        labels.forEachIndexed { i, text ->
            if (i % labelEvery != 0) return@forEachIndexed
            val layout = measurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset(x(i) - layout.size.width / 2f, size.height - LabelBand.toPx() + 6.dp.toPx()))
        }
    }
}
