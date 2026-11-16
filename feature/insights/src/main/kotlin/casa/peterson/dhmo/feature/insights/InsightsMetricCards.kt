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
import casa.peterson.dhmo.core.designsystem.component.SectionCard
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
internal fun OverviewCard(state: InsightsUiState.Ready, locale: Locale, modifier: Modifier) {
    val summary = state.summary
    SectionCard(modifier, title = stringResource(R.string.feature_insights_card_overview)) {
        Metric(
            stringResource(R.string.feature_insights_average),
            summary?.let { VolumeFormatter.format(it.meanMl.roundToInt(), state.units, locale) }
                ?: stringResource(R.string.feature_insights_no_data),
        )
        Metric(
            stringResource(R.string.feature_insights_days_met),
            stringResource(R.string.feature_insights_days_met_value, state.attainment.daysMet, state.attainment.dayCount),
        )
        val comparison = state.comparison
        if (state.selection.compare) {
            Metric(
                stringResource(R.string.feature_insights_compare_previous),
                comparison?.let { VolumeFormatter.format(it.meanMl.roundToInt(), state.units, locale) }
                    ?: stringResource(R.string.feature_insights_no_data),
            )
            if (summary != null && comparison != null) {
                val delta = (summary.meanMl - comparison.meanMl).roundToInt()
                Text(
                    stringResource(
                        R.string.feature_insights_compare_change,
                        if (delta >= 0) "+" else "−",
                        VolumeFormatter.format(abs(delta), state.units, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun AttainmentCard(state: InsightsUiState.Ready, modifier: Modifier) {
    val a = state.attainment
    SectionCard(modifier, title = stringResource(R.string.feature_insights_card_attainment)) {
        Metric(stringResource(R.string.feature_insights_bucket_100), a.atLeast100.toString())
        Metric(stringResource(R.string.feature_insights_bucket_80_100), a.from80To100.toString())
        Metric(stringResource(R.string.feature_insights_bucket_50_80), a.from50To80.toString())
        Metric(stringResource(R.string.feature_insights_bucket_below50), a.below50.toString())
    }
}

@Composable
internal fun StreaksCard(state: InsightsUiState.Ready, modifier: Modifier) {
    @Composable
    fun days(n: Int) = pluralStringResource(R.plurals.feature_insights_streak_days, n, n)
    SectionCard(modifier, title = stringResource(R.string.feature_insights_card_streaks)) {
        Metric(
            stringResource(R.string.feature_insights_goal_streak),
            stringResource(R.string.feature_insights_streak_value, days(state.goalStreak.current), days(state.goalStreak.longest)),
        )
        Metric(
            stringResource(R.string.feature_insights_logging_streak),
            stringResource(R.string.feature_insights_streak_value, days(state.loggingStreak.current), days(state.loggingStreak.longest)),
        )
    }
}

@Composable
internal fun TrendCard(state: InsightsUiState.Ready, locale: Locale, modifier: Modifier) {
    val trend = state.trend
    val perWeek = VolumeFormatter.format(abs(trend.slopePerWeekMl).roundToInt(), state.units, locale)
    val text = when (trend.direction) {
        TrendDirection.UP -> stringResource(R.string.feature_insights_trend_up, perWeek)
        TrendDirection.DOWN -> stringResource(R.string.feature_insights_trend_down, perWeek)
        TrendDirection.STABLE -> stringResource(R.string.feature_insights_trend_stable)
        TrendDirection.INSUFFICIENT_DATA -> pluralStringResource(R.plurals.feature_insights_trend_insufficient, trend.n, trend.n)
    }
    SectionCard(modifier, title = stringResource(R.string.feature_insights_card_trend)) { Text(text) }
}

@Composable
internal fun ConsistencyCard(state: InsightsUiState.Ready, modifier: Modifier) {
    SectionCard(modifier, title = stringResource(R.string.feature_insights_card_consistency)) {
        Text(
            state.consistency?.let { stringResource(R.string.feature_insights_consistency_value, it) }
                ?: stringResource(R.string.feature_insights_consistency_none),
        )
    }
}

@Composable
internal fun TimeOfDayCard(state: InsightsUiState.Ready, locale: Locale, modifier: Modifier) {
    val profile = state.timeOfDay ?: return
    fun clock(sinceStart: Double) = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        .format(LocalTime.of(0, 0).plusMinutes(((sinceStart.roundToInt() + state.dayStartMinutes) % MINUTES_PER_DAY).toLong()))
    SectionCard(modifier, title = stringResource(R.string.feature_insights_card_time_of_day)) {
        profile.medianMinutesToHalfGoal?.let { Text(stringResource(R.string.feature_insights_half_goal_by, clock(it))) }
        profile.medianMinutesToFullGoal?.let { Text(stringResource(R.string.feature_insights_full_goal_by, clock(it))) }
        profile.eveningShare?.let { share ->
            Text(
                stringResource(
                    R.string.feature_insights_evening_share,
                    (share * PERCENT).roundToInt(),
                    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(LocalTime.of(EVENING_HOUR, 0)),
                ),
            )
        }
    }
}

@Composable
internal fun DayOfWeekCard(state: InsightsUiState.Ready, locale: Locale, modifier: Modifier) {
    val pattern = state.dayOfWeek
    SectionCard(modifier, title = stringResource(R.string.feature_insights_card_day_of_week)) {
        pattern.byDay.forEach { weekday ->
            Metric(
                weekday.day.getDisplayName(TextStyle.FULL, locale),
                weekday.meanMl?.let { VolumeFormatter.format(it.roundToInt(), state.units, locale) }
                    ?: stringResource(R.string.feature_insights_no_data),
            )
        }
        pattern.weakest?.let { Text(stringResource(R.string.feature_insights_weakest, it.getDisplayName(TextStyle.FULL, locale))) }
    }
}

@Composable
internal fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
