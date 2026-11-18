package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.R
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors

private const val DAYS_PER_WEEK = 7

/**
 * A calendar heatmap drawn on a Canvas (the year view). [cells] run column by column: each column is one week of
 * [rows] days (default 7), and `null` is a day with no data. Cell size is [cellSize].
 *
 * Each day is a star in the Night Sky. Results are never conveyed by colour alone: met days are the biggest stars and
 * carry an amber halo, partly met days are mid-sized and missed days are small and dim. The grid is a single
 * accessible element described by [contentDescription] (by default a count of each result).
 */
@Composable
fun HeatmapGrid(
    cells: List<DayResult?>,
    modifier: Modifier = Modifier,
    rows: Int = DAYS_PER_WEEK,
    cellSize: Dp = 14.dp,
    cellSpacing: Dp = 2.dp,
    contentDescription: String = heatmapSummary(cells),
) {
    val columns = (cells.size + rows - 1) / rows
    val width = if (columns == 0) 0.dp else cellSize * columns + cellSpacing * (columns - 1)
    val height = if (cells.isEmpty()) 0.dp else cellSize * rows + cellSpacing * (rows - 1)

    val extras = LocalDhmoExtraColors.current
    Canvas(
        modifier = modifier.size(width, height).clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        val cell = cellSize.toPx()
        val gap = cellSpacing.toPx()
        cells.forEachIndexed { index, result ->
            val center = Offset((index / rows) * (cell + gap) + cell / 2f, (index % rows) * (cell + gap) + cell / 2f)
            val day =
                when (result) {
                    DayResult.MET -> StarDay(1f)
                    DayResult.PARTLY_MET -> StarDay(PARTLY_FULLNESS)
                    DayResult.MISSED -> StarDay(MISSED_FULLNESS)
                    null -> StarDay.NoData
                }
            drawSkyCell(extras, center, cell, day, density, scaleToCell = true)
        }
    }
}

private const val PARTLY_FULLNESS = 0.6f
private const val MISSED_FULLNESS = 0.2f

@Composable
private fun heatmapSummary(cells: List<DayResult?>): String {
    val met = cells.count { it == DayResult.MET }
    val partly = cells.count { it == DayResult.PARTLY_MET }
    val missed = cells.count { it == DayResult.MISSED }
    return listOf(
        pluralStringResource(R.plurals.core_designsystem_heatmap_met, met, met),
        pluralStringResource(R.plurals.core_designsystem_heatmap_partly_met, partly, partly),
        pluralStringResource(R.plurals.core_designsystem_heatmap_missed, missed, missed),
    ).joinToString(", ")
}
