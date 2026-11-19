package casa.peterson.dhmo.feature.insights.chart

import androidx.compose.ui.graphics.Color

/** A line over the columns: [dashed] for the goal, [area] to fill under it, [thickness] in dp. */
internal class ChartLine(
    val points: List<Pair<Int, Double>>,
    val color: Color,
    val dashed: Boolean = false,
    val area: Boolean = false,
    val thickness: Float = 2f,
)
