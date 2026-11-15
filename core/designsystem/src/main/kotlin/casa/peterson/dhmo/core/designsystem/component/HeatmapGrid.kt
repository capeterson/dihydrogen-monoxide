package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.R

private const val DAYS_PER_WEEK = 7
private const val HALF = 0.5f
private const val GLYPH_FRACTION = 0.7f
private const val PARTLY_BACKGROUND_ALPHA = 0.3f
private const val DARK_LUMINANCE = 0.5f
private const val CORNER_FRACTION = 0.2f
private const val STROKE_FRACTION = 0.15f
private val MIN_STROKE = 1.5.dp
private val GLYPH_MIN_CELL = 16.dp

/**
 * A calendar heatmap drawn on a Canvas (the year view). [cells] run column by column: each column is one week of
 * [rows] days (default 7), and `null` is a day with no data. Cell size is [cellSize].
 *
 * Results are never conveyed by colour alone. Small cells differ in shape (met is solid, partly met is filled in the
 * lower half, missed is an outline); cells of [GLYPH_MIN_CELL] or more also carry the result's icon. The grid is a
 * single accessible element described by [contentDescription] (by default a count of each result).
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < DARK_LUMINANCE
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val styles = DayResult.entries.associateWith { DayResultColors.style(it, isDark) }
    val painters = DayResult.entries.associateWith { painterResource(styles.getValue(it).icon) }

    Canvas(
        modifier = modifier.size(width, height).clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        val cell = cellSize.toPx()
        val gap = cellSpacing.toPx()
        val radius = CornerRadius(cell * CORNER_FRACTION)
        val showGlyph = cellSize >= GLYPH_MIN_CELL
        cells.forEachIndexed { index, result ->
            val origin = Offset((index / rows) * (cell + gap), (index % rows) * (cell + gap))
            val bounds = Size(cell, cell)
            if (result == null) {
                translate(origin.x, origin.y) { drawRoundRect(emptyColor, size = bounds, cornerRadius = radius) }
                return@forEachIndexed
            }
            val style = styles.getValue(result)
            translate(origin.x, origin.y) {
                when {
                    showGlyph -> {
                        drawRoundRect(style.container, size = bounds, cornerRadius = radius)
                        val glyph = cell * GLYPH_FRACTION
                        translate((cell - glyph) * HALF, (cell - glyph) * HALF) {
                            with(painters.getValue(result)) { draw(Size(glyph, glyph), colorFilter = ColorFilter.tint(style.onContainer)) }
                        }
                    }

                    result == DayResult.MET -> drawRoundRect(style.container, size = bounds, cornerRadius = radius)

                    result == DayResult.PARTLY_MET -> {
                        drawRoundRect(style.container, size = bounds, cornerRadius = radius, alpha = PARTLY_BACKGROUND_ALPHA)
                        val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, cell, cell, radius)) }
                        clipPath(clip) { drawRect(style.container, topLeft = Offset(0f, cell * HALF), size = Size(cell, cell * HALF)) }
                    }

                    else -> {
                        val stroke = maxOf(MIN_STROKE.toPx(), cell * STROKE_FRACTION)
                        drawRoundRect(
                            style.container,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(cell - stroke, cell - stroke),
                            cornerRadius = radius,
                            style = Stroke(width = stroke),
                        )
                    }
                }
            }
        }
    }
}

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
