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
import androidx.compose.ui.platform.LocalResources
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
import casa.peterson.dhmo.core.designsystem.component.InsightCard
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.SegmentedPill
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.domain.stats.DayResult
import casa.peterson.dhmo.core.domain.stats.Insight
import casa.peterson.dhmo.core.domain.stats.InsightChart
import casa.peterson.dhmo.core.domain.stats.TrendDirection
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.feature.insights.chart.ChartLine
import casa.peterson.dhmo.feature.insights.chart.ColumnsWithLines
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
internal fun InsightsScreen(
    onOpenDay: (LocalDate) -> Unit,
    onOpenCorrelation: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InsightsContent(
        state = state,
        actions = InsightsActions(
            onRange = viewModel::onRangeSelected,
            onPrevious = viewModel::onPrevious,
            onNext = viewModel::onNext,
            onCustomRange = viewModel::onCustomRange,
            onInsight = { insight ->
                when (insight.chart) {
                    InsightChart.WEEK -> viewModel.onRangeSelected(InsightsRange.WEEK)

                    InsightChart.MONTH, InsightChart.TREND, InsightChart.TIME_OF_DAY, InsightChart.DAY_OF_WEEK ->
                        viewModel.onRangeSelected(InsightsRange.MONTH)

                    InsightChart.YEAR -> viewModel.onRangeSelected(InsightsRange.YEAR)

                    InsightChart.HISTORY_DAY -> insight.params["date"]?.let {
                        runCatching { LocalDate.parse(it) }.getOrNull()
                    }?.let(onOpenDay)

                    InsightChart.CORRELATION_TEMPERATURE -> onOpenCorrelation(DRIVER_TEMPERATURE)

                    InsightChart.CORRELATION_ACTIVITY -> onOpenCorrelation(DRIVER_ACTIVE_MINUTES)
                }
            },
            onBack = onBack,
        ),
    )
}

@Composable
internal fun InsightsContent(state: InsightsUiState, actions: InsightsActions, showRangePicker: Boolean = false) {
    var pickerOpen by rememberSaveable { mutableStateOf(showRangePicker) }
    val title = stringResource(
        if ((state as? InsightsUiState.Ready)?.selection?.range == InsightsRange.CUSTOM && actions.onBack != null) {
            R.string.feature_insights_custom_range_title
        } else {
            R.string.feature_insights_insights_title
        },
    )
    DhmoScreen(topBar = { DhmoTopBar(title, onNavigateBack = actions.onBack) }) { padding ->
        when (state) {
            InsightsUiState.Loading -> Text(
                stringResource(R.string.feature_insights_loading),
                Modifier.padding(padding).padding(16.dp),
            )

            InsightsUiState.Empty -> EmptyState(
                title = stringResource(R.string.feature_insights_empty_title),
                message = stringResource(R.string.feature_insights_empty_message),
                icon = DhmoIcons.BarChart,
                modifier = Modifier.padding(padding),
            )

            is InsightsUiState.Ready -> ReadyContent(state, actions, padding, onOpenPicker = { pickerOpen = true })
        }
    }
    val ready = state as? InsightsUiState.Ready
    if (pickerOpen && ready != null) {
        val custom = ready.selection.takeIf { it.range == InsightsRange.CUSTOM }
        RangePickerDialog(
            initialFrom = custom?.from ?: ready.today.minusDays(DEFAULT_CUSTOM_DAYS - 1),
            initialTo = custom?.to ?: ready.today,
            initialCompare = custom?.compare ?: false,
            today = ready.today,
            onApply = { from, to, compare ->
                pickerOpen = false
                actions.onCustomRange(from, to, compare)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
internal fun ReadyContent(state: InsightsUiState.Ready, actions: InsightsActions, padding: PaddingValues, onOpenPicker: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val range = state.selection.range
    LazyColumn(contentPadding = padding) {
        item { RangeTabs(range, onSelect = { if (it == InsightsRange.CUSTOM) onOpenPicker() else actions.onRange(it) }) }
        item { PeriodHeader(state, actions, locale) }
        val cards = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        if (range == InsightsRange.WEEK && state.insights.isNotEmpty()) {
            item { FeedCard(state.insights.take(FEED_SIZE), state.units, locale, actions.onInsight, cards) }
        }
        if (range != InsightsRange.DAY) item { StatCard(state, cards) }
        if (range == InsightsRange.WEEK || range == InsightsRange.MONTH || range == InsightsRange.CUSTOM) {
            item(key = "night_sky") { NightSkyRangeCard(state, locale, cards) }
        }
        item { ChartCard(state, locale, cards) }
        if (range != InsightsRange.DAY) {
            item { ThroughTheDayCard(state, locale, cards) }
            item { PeakTimeInsight(state, locale, cards) }
        }
        if (!state.selection.from.isAfter(state.today) && !state.selection.to.isBefore(state.today) && range != InsightsRange.DAY) {
            item {
                Text(
                    stringResource(R.string.feature_insights_today_excluded),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
        if (range == InsightsRange.DAY) {
            item { StreaksCard(state, cards) }
        } else {
            item { OverviewCard(state, locale, cards) }
            item { AttainmentCard(state, cards) }
            item { StreaksCard(state, cards) }
            item { TrendCard(state, locale, cards) }
            item { ConsistencyCard(state, cards) }
            state.timeOfDay?.let { item { TimeOfDayCard(state, locale, cards) } }
            item { DayOfWeekCard(state, locale, cards) }
        }
    }
}

@Composable
internal fun RangeTabs(selected: InsightsRange, onSelect: (InsightsRange) -> Unit) {
    val ranges = InsightsRange.entries
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        SegmentedPill(
            options = ranges.map { stringResource(rangeLabel(it)) },
            selected = selected.ordinal,
            onSelect = { onSelect(ranges[it]) },
        )
    }
}

@Composable
internal fun PeriodHeader(state: InsightsUiState.Ready, actions: InsightsActions, locale: Locale) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onPrevious, enabled = state.hasPrevious) {
            Icon(painterResource(DhmoIcons.ArrowBack), contentDescription = stringResource(R.string.feature_insights_previous))
        }
        Text(
            periodLabel(state.selection, locale),
            modifier = Modifier.weight(1f).semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = actions.onNext, enabled = state.hasNext) {
            Icon(
                painterResource(DhmoIcons.ArrowBack),
                contentDescription = stringResource(R.string.feature_insights_next),
                modifier = Modifier.rotate(HALF_TURN),
            )
        }
    }
}

@Composable
internal fun FeedCard(insights: List<Insight>, units: VolumeUnit, locale: Locale, onInsight: (Insight) -> Unit, modifier: Modifier) {
    val resources = LocalResources.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        insights.forEach { insight ->
            InsightCard(
                title = InsightText.format(insight, resources, units, locale),
                body = null,
                icon = DhmoIcons.History,
                onClick = { onInsight(insight) },
            )
        }
    }
}

internal fun rangeLabel(range: InsightsRange): Int = when (range) {
    InsightsRange.DAY -> R.string.feature_insights_range_day
    InsightsRange.WEEK -> R.string.feature_insights_range_week
    InsightsRange.MONTH -> R.string.feature_insights_range_month
    InsightsRange.YEAR -> R.string.feature_insights_range_year
    InsightsRange.CUSTOM -> R.string.feature_insights_range_custom
}

internal fun mediumDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

@Composable
internal fun periodLabel(selection: RangeSelection, locale: Locale): String = when (selection.range) {
    InsightsRange.DAY -> DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(selection.from)

    InsightsRange.MONTH -> DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(selection.from)

    InsightsRange.YEAR -> selection.from.year.toString()

    InsightsRange.WEEK, InsightsRange.CUSTOM ->
        stringResource(R.string.feature_insights_range_label, mediumDate(selection.from, locale), mediumDate(selection.to, locale))
}

internal const val DRIVER_TEMPERATURE = "TEMPERATURE"
internal const val DRIVER_ACTIVE_MINUTES = "ACTIVE_MINUTES"
internal const val FEED_SIZE = 5
internal const val DAYS_PER_WEEK = 7
internal const val ROLLING_7_ONLY_MAX_DAYS = 14
internal const val LABEL_EVERY_HOURS = 6
internal const val LABEL_EVERY_DAYS = 5
internal const val MINUTES_PER_HOUR = 60
internal const val MINUTES_PER_DAY = 1_440
internal const val HALF_TURN = 180f
internal const val PERCENT = 100
internal const val EVENING_HOUR = 18
internal const val DEFAULT_CUSTOM_DAYS = 30L
