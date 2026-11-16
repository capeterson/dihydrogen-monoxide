package casa.peterson.dhmo.feature.insights.chart

import androidx.compose.ui.graphics.Color

/** One line overlay: its points (x = index, y = ml; gaps allowed) and colour. */
internal class ChartLine(val points: List<Pair<Int, Double>>, val color: Color)
