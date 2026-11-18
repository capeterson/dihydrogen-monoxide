package casa.peterson.dhmo.feature.insights.chart

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors
import casa.peterson.dhmo.core.designsystem.theme.NumeralFontFamily
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.domain.stats.DayResult
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.util.Locale

private val ChartHeight = 220.dp
private const val DASH_LENGTH = 4
private const val DASH_GAP = 4
private const val AREA_ALPHA = 0.45f
private const val AXIS_TEXT_SIZE = 11
private val CandleThickness = 20.dp
private val SlimThickness = 8.dp
private const val CANDLE_MAX_COLUMNS = 8

/**
 * Daily (or hourly) columns with optional line overlays, drawn with Vico from a fixed model, so the chart is
 * deterministic (no producer coroutine) and screenshot-stable. Each column has its own colour, for example the day's
 * result colour. The chart is one TalkBack node described by [description]; the numbers are also in the cards.
 */
@Composable
internal fun ColumnsWithLines(
    values: List<Double>,
    columnColors: List<Color>,
    lines: List<ChartLine>,
    xLabel: (Int) -> String,
    units: VolumeUnit,
    locale: Locale,
    description: String,
    modifier: Modifier = Modifier,
    labelEvery: Int = 1,
) {
    val fallback = MaterialTheme.colorScheme.primary
    // Few columns are drawn as fat candles, many as slim ones; both have rounded tops like the design's candle bars.
    val thickness = if (values.size <= CANDLE_MAX_COLUMNS) CandleThickness else SlimThickness
    val top = thickness / 2
    val candle = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = 3.dp, bottomEnd = 3.dp)
    val columnComponents = columnColors.map { color -> rememberLineComponent(fill = Fill(color), thickness = thickness, shape = candle) }
    val fallbackColumn = rememberLineComponent(fill = Fill(fallback), thickness = thickness, shape = candle)
    val provider = remember(columnComponents, fallbackColumn) { PerColumnProvider(columnComponents, fallbackColumn) }
    val lineSpecs = lines.map { line ->
        LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(line.color)),
            stroke = if (line.dashed) {
                LineCartesianLayer.LineStroke.Dashed(line.thickness.dp, StrokeCap.Round, DASH_LENGTH.dp, DASH_GAP.dp)
            } else {
                LineCartesianLayer.LineStroke.Continuous(line.thickness.dp, StrokeCap.Round)
            },
            areaFill = if (line.area) LineCartesianLayer.AreaFill.single(Fill(line.color.copy(alpha = AREA_ALPHA))) else null,
        )
    }
    val axisLabel = rememberAxisLabelComponent(
        TextStyle(fontFamily = NumeralFontFamily, fontSize = AXIS_TEXT_SIZE.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
    )
    val drawnLines = lines.withIndex().filter { it.value.points.isNotEmpty() }
    val layers = buildList {
        add(rememberColumnCartesianLayer(columnProvider = provider))
        if (drawnLines.isNotEmpty()) {
            add(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(drawnLines.map { lineSpecs[it.index] }),
                ),
            )
        }
    }
    val chart = rememberCartesianChart(
        *layers.toTypedArray(),
        startAxis = VerticalAxis.rememberStart(
            label = axisLabel,
            valueFormatter = remember(units, locale) {
                VolumeAxisFormatter(units, locale)
            },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            label = axisLabel,
            valueFormatter = remember(xLabel) { IndexAxisFormatter(xLabel) },
            itemPlacer = remember(labelEvery) { HorizontalAxis.ItemPlacer.aligned(spacing = { labelEvery }) },
        ),
    )
    val model = remember(values, lines) {
        val models = buildList {
            add(ColumnCartesianLayerModel.build { series(values.indices.toList(), values) })
            if (drawnLines.isNotEmpty()) {
                add(
                    LineCartesianLayerModel.build {
                        drawnLines.forEach { (_, line) -> series(line.points.map { it.first }, line.points.map { it.second }) }
                    },
                )
            }
        }
        CartesianChartModel(models)
    }
    CartesianChartHost(
        chart = chart,
        model = model,
        // The whole range always fits the card: no horizontal scrolling or pinch zoom.
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content, minZoom = Zoom.Content, maxZoom = Zoom.Content),
        modifier = modifier.fillMaxWidth().height(ChartHeight).semantics { contentDescription = description },
    )
}

/**
 * The candle colour for a day result: the deep primary for a met goal, the soft water colour for a partly met one and a
 * grey for a missed or empty one. The three differ strongly in lightness, so they stay apart without colour vision.
 */
@Composable
internal fun resultColor(result: DayResult?): Color = when (result) {
    DayResult.MET -> MaterialTheme.colorScheme.primary
    DayResult.PARTLY_MET -> LocalDhmoExtraColors.current.water
    DayResult.MISSED, DayResult.NO_DATA, null -> MaterialTheme.colorScheme.outlineVariant
}

private class PerColumnProvider(private val columns: List<LineComponent>, private val fallback: LineComponent) :
    ColumnCartesianLayer.ColumnProvider {
    override fun getColumn(entry: ColumnCartesianLayerModel.Entry, extraStore: ExtraStore): LineComponent =
        columns.getOrElse(entry.x.toInt()) { fallback }

    override fun getWidestSeriesColumn(seriesKey: Any, seriesIndex: Int, extraStore: ExtraStore): LineComponent = fallback
}

private class VolumeAxisFormatter(private val units: VolumeUnit, private val locale: Locale) : CartesianValueFormatter {
    override fun format(context: CartesianMeasuringContext, value: Double, verticalAxisPosition: Axis.Position.Vertical?): CharSequence =
        VolumeFormatter.formatNumber(value.toInt(), units, locale)
}

private class IndexAxisFormatter(private val label: (Int) -> String) : CartesianValueFormatter {
    // Vico rejects blank labels; the item placer decides which indices are labelled.
    override fun format(context: CartesianMeasuringContext, value: Double, verticalAxisPosition: Axis.Position.Vertical?): CharSequence =
        label(value.toInt()).ifBlank { (value.toInt() + 1).toString() }
}
