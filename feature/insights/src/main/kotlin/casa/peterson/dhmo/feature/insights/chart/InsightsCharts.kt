package casa.peterson.dhmo.feature.insights.chart

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.component.DayResultColors
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.domain.stats.DayResult
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
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
private val ColumnThickness = 8.dp

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
    val columnComponents = columnColors.map { color -> rememberLineComponent(fill = Fill(color), thickness = ColumnThickness) }
    val fallbackColumn = rememberLineComponent(fill = Fill(fallback), thickness = ColumnThickness)
    val provider = remember(columnComponents, fallbackColumn) { PerColumnProvider(columnComponents, fallbackColumn) }
    val lineSpecs = lines.map { line -> LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(Fill(line.color))) }
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
        startAxis = VerticalAxis.rememberStart(valueFormatter = remember(units, locale) { VolumeAxisFormatter(units, locale) }),
        bottomAxis = HorizontalAxis.rememberBottom(
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

/** The column colour for a day result; days without data use the surface variant. */
@Composable
internal fun resultColor(result: DayResult?): Color = when (result) {
    DayResult.MET -> DayResultColors.style(casa.peterson.dhmo.core.designsystem.component.DayResult.MET).container
    DayResult.PARTLY_MET -> DayResultColors.style(casa.peterson.dhmo.core.designsystem.component.DayResult.PARTLY_MET).container
    DayResult.MISSED -> DayResultColors.style(casa.peterson.dhmo.core.designsystem.component.DayResult.MISSED).container
    DayResult.NO_DATA, null -> MaterialTheme.colorScheme.outlineVariant
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
