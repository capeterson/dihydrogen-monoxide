package casa.peterson.dhmo.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.EmptyState
import casa.peterson.dhmo.core.designsystem.component.HeatmapGrid
import casa.peterson.dhmo.core.designsystem.component.NightSkyPanel
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.domain.stats.DayResult
import casa.peterson.dhmo.core.domain.stats.Insight
import casa.peterson.dhmo.core.domain.stats.InsightChart
import casa.peterson.dhmo.core.domain.stats.TrendDirection
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.feature.insights.chart.ChartLine
import casa.peterson.dhmo.feature.insights.chart.ColumnsWithLines
import casa.peterson.dhmo.feature.insights.chart.DailyAreaChart
import casa.peterson.dhmo.feature.insights.chart.resultColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun ChartCard(state: InsightsUiState.Ready, locale: Locale, modifier: Modifier) {
    when (state.selection.range) {
        InsightsRange.DAY -> HourlyChart(state, locale, modifier)
        InsightsRange.YEAR -> YearHeatmap(state, modifier)
        else -> DailyChart(state, locale, modifier)
    }
}

@Composable
internal fun DailyChart(state: InsightsUiState.Ready, locale: Locale, modifier: Modifier) {
    val goalColor = MaterialTheme.colorScheme.secondary
    val rolling7Color = MaterialTheme.colorScheme.onSurfaceVariant
    val days = state.days
    val goal = state.goalMl
    val from = state.selection.from
    val slots = ChronoUnit.DAYS.between(from, state.selection.to).toInt() + 1
    val byDate = days.withIndex().associate { (index, day) -> day.date to index }
    fun series(source: (Int) -> Double?) = (0 until slots).map { offset ->
        val date = from.plusDays(offset.toLong())
        if (date.isAfter(state.today)) null else byDate[date]?.let(source)
    }
    val week = slots <= DAYS_PER_WEEK
    val labelFormat = DateTimeFormatter.ofPattern(if (week) "EEEEE" else "d", locale)
    val met = days.count { state.results[it.date] == DayResult.MET }
    SectionCard(modifier, title = stringResource(R.string.feature_insights_chart_daily)) {
        DailyAreaChart(
            values = series { days[it].totalMl.toDouble() },
            labels = (0 until slots).map { labelFormat.format(from.plusDays(it.toLong())) },
            labelEvery = if (week) 1 else LABEL_EVERY_DAYS,
            goal = goal?.toDouble(),
            average = if (days.size > DAYS_PER_WEEK) series { state.rolling7.getOrNull(it) } else null,
            description = pluralStringResource(
                R.plurals.feature_insights_chart_daily_description,
                days.size,
                mediumDate(state.selection.from, locale),
                mediumDate(minOf(state.selection.to, state.today), locale),
                goal?.let { VolumeFormatter.format(it, state.units, locale) } ?: stringResource(R.string.feature_insights_no_data),
                met,
                days.size,
            ),
        )
        Legend(
            buildList {
                if (goal != null) add(goalColor to stringResource(R.string.feature_insights_legend_goal))
                if (days.size > DAYS_PER_WEEK) add(rolling7Color to stringResource(R.string.feature_insights_legend_rolling7))
            },
        )
    }
}

@Composable
internal fun HourlyChart(state: InsightsUiState.Ready, locale: Locale, modifier: Modifier) {
    val hourly = state.hourly.orEmpty()
    val expectedColor = MaterialTheme.colorScheme.tertiary
    val barColor = MaterialTheme.colorScheme.primary
    val expected = state.expectedLine
    val clock = DateTimeFormatter.ofPattern("H", locale)
    SectionCard(modifier, title = stringResource(R.string.feature_insights_chart_hourly)) {
        ColumnsWithLines(
            values = hourly.map { it.cumulativeMl.toDouble() },
            columnColors = hourly.map { barColor },
            lines = listOfNotNull(expected?.let { line -> ChartLine(line.mapIndexed { i, v -> i to v.toDouble() }, expectedColor) }),
            xLabel = { slot -> clock.format(LocalTime.of(0, 0).plusMinutes((state.dayStartMinutes + slot * MINUTES_PER_HOUR).toLong())) },
            labelEvery = LABEL_EVERY_HOURS,
            units = state.units,
            locale = locale,
            description = stringResource(
                R.string.feature_insights_chart_hourly_description,
                VolumeFormatter.format(hourly.lastOrNull()?.cumulativeMl ?: 0, state.units, locale),
                VolumeFormatter.format(expected?.lastOrNull()?.roundToInt() ?: 0, state.units, locale),
            ),
        )
        Text(stringResource(R.string.feature_insights_chart_hourly_caption), style = MaterialTheme.typography.bodySmall)
        if (expected != null) Legend(listOf(expectedColor to stringResource(R.string.feature_insights_legend_expected)))
        Metric(
            stringResource(R.string.feature_insights_day_total),
            state.days.firstOrNull()?.let { day ->
                val goal = day.goalMl ?: return@let stringResource(R.string.feature_insights_no_data)
                "${VolumeFormatter.format(day.totalMl, state.units, locale)} / ${VolumeFormatter.format(goal, state.units, locale)}"
            } ?: stringResource(R.string.feature_insights_no_data),
        )
    }
}

@Composable
internal fun YearHeatmap(state: InsightsUiState.Ready, modifier: Modifier) {
    val from = state.selection.from
    val leading = from.dayOfWeek.value - DayOfWeek.MONDAY.value
    val length = ChronoUnit.DAYS.between(from, state.selection.to).toInt() + 1
    val cells = List(leading) { null } + (0 until length).map { offset ->
        when (state.results[from.plusDays(offset.toLong())]) {
            DayResult.MET -> casa.peterson.dhmo.core.designsystem.component.DayResult.MET
            DayResult.PARTLY_MET -> casa.peterson.dhmo.core.designsystem.component.DayResult.PARTLY_MET
            DayResult.MISSED -> casa.peterson.dhmo.core.designsystem.component.DayResult.MISSED
            DayResult.NO_DATA, null -> null
        }
    }
    NightSkyPanel(
        title = stringResource(R.string.feature_insights_chart_year),
        caption = stringResource(R.string.feature_insights_year_heatmap_caption),
        modifier = modifier,
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
        Row(Modifier.horizontalScroll(scroll).padding(top = 8.dp)) {
            HeatmapGrid(cells = cells, cellSize = 14.dp)
        }
    }
}

@Composable
internal fun Legend(items: List<Pair<Color, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (color, label) ->
            Text("— $label", color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

internal fun List<Double?>.points(): List<Pair<Int, Double>> = mapIndexedNotNull { index, value -> value?.let { index to it } }
private const val GOAL_LINE_DP = 1.5f
