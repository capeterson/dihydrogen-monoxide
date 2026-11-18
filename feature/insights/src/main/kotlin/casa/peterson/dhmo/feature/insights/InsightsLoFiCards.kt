package casa.peterson.dhmo.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import casa.peterson.dhmo.core.designsystem.component.InsightCard
import casa.peterson.dhmo.core.designsystem.component.LanternRow
import casa.peterson.dhmo.core.designsystem.component.NightSkyCard
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.StarDay
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors
import casa.peterson.dhmo.core.designsystem.theme.NumeralFontFamily
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val LANTERN_COUNT = 7
private const val BUCKET_HOURS = 3
private const val BUCKETS = 24 / BUCKET_HOURS
private val CandleAreaHeight = 140.dp

/** The headline: how many days reached the goal as a big monospaced percentage, with the streak as a row of lanterns. */
@Composable
internal fun StatCard(state: InsightsUiState.Ready, modifier: Modifier) {
    // The same days the stars and candles show, today included, so the rate and the lanterns never disagree.
    val counted = state.days.filter { !it.date.isAfter(state.today) && (it.goalMl ?: 0) > 0 }
    val daysMet = counted.count { it.totalMl >= checkNotNull(it.goalMl) }
    val percent = if (counted.isEmpty()) 0 else (daysMet * PERCENT / counted.size.toFloat()).roundToInt()
    val streak = state.goalStreak.current
    val lit = streak.coerceAtMost(LANTERN_COUNT)
    val caption =
        if (streak == 0) {
            stringResource(R.string.feature_insights_lanterns_none)
        } else {
            pluralStringResource(R.plurals.feature_insights_lanterns_lit, streak, streak)
        }
    SectionCard(modifier) {
        Column {
            Text(
                "$percent%",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = NumeralFontFamily, fontWeight = FontWeight.Medium),
            )
            Text(
                stringResource(R.string.feature_insights_stat_label, daysMet, counted.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LanternRow(lit = lit, total = LANTERN_COUNT, description = caption)
        Text(caption, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    }
}

/** Every day of the range as a star on the dark Night Sky card: fuller days are bigger and brighter, met goals glow. */
@Composable
internal fun NightSkyRangeCard(state: InsightsUiState.Ready, locale: Locale, modifier: Modifier) {
    val leading = state.selection.from.dayOfWeek.value - DayOfWeek.MONDAY.value
    val stars = state.days.map { day ->
        val goal = day.goalMl
        when {
            day.date.isAfter(state.today) -> null

            // Today is still in progress, so it is a dashed outline until the goal is met.
            day.date == state.today -> if (goal != null && goal > 0 &&
                day.totalMl >= goal
            ) {
                StarDay(day.totalMl / goal.toFloat())
            } else {
                StarDay(null)
            }

            !day.logged -> StarDay.NoData

            goal == null || goal == 0 -> StarDay(UNKNOWN_GOAL_FULLNESS)

            else -> StarDay(day.totalMl / goal.toFloat())
        }
    }
    NightSkyCard(
        title = stringResource(R.string.feature_insights_night_sky),
        caption = stringResource(R.string.feature_insights_night_sky_caption),
        days = List(leading) { null } + stars,
        weekdayLabels = DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, locale) },
        description = stringResource(R.string.feature_insights_night_sky_description, state.days.size),
        modifier = modifier,
    )
}

private const val UNKNOWN_GOAL_FULLNESS = 0.5f

/** The average day in three-hour stretches, as rounded candles with an amber dashed line for the pace the goal needs. */
@Composable
internal fun ThroughTheDayCard(state: InsightsUiState.Ready, locale: Locale, modifier: Modifier) {
    val profile = state.timeOfDay ?: return
    val buckets = List(BUCKETS) { b -> profile.hourlyMeanMl.drop(b * BUCKET_HOURS).take(BUCKET_HOURS).sum() }
    val max = buckets.max()
    if (max <= 0.0) return
    val goalPace = state.goalMl?.let { it / BUCKETS.toDouble() }
    val top = maxOf(max, goalPace ?: 0.0) * HEADROOM
    val peak = buckets.indexOf(max)
    val clock = DateTimeFormatter.ofPattern("ha", locale)
    fun label(bucket: Int) = clock.format(
        LocalTime.of(0, 0).plusMinutes((state.dayStartMinutes + bucket * BUCKET_HOURS * MINUTES_PER_HOUR).toLong()),
    ).lowercase(locale)
    val water = LocalDhmoExtraColors.current.water
    val glow = LocalDhmoExtraColors.current.glow
    val summary = stringResource(R.string.feature_insights_through_the_day_description, label(peak))
    SectionCard(modifier, title = stringResource(R.string.feature_insights_through_the_day)) {
        Text(
            stringResource(R.string.feature_insights_through_the_day_caption),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.fillMaxWidth().height(CandleAreaHeight).clearAndSetSemantics { contentDescription = summary }) {
            Row(
                Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                buckets.forEachIndexed { index, value ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            VolumeFormatter.formatNumber(value.roundToInt(), state.units, locale),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = NumeralFontFamily, fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Box(
                            Modifier
                                .widthIn(max = 30.dp)
                                .fillMaxWidth()
                                .height(CandleAreaHeight * (value / top).toFloat().coerceIn(MIN_CANDLE, 1f))
                                .background(if (index == peak) MaterialTheme.colorScheme.primary else water, CandleShape),
                        )
                    }
                }
            }
            if (goalPace != null) {
                val lineBottom = CandleAreaHeight * (goalPace / top).toFloat().coerceIn(0f, 1f)
                Text(
                    stringResource(R.string.feature_insights_goal_pace),
                    Modifier.align(
                        Alignment.BottomEnd,
                    ).offset(y = -lineBottom - 2.dp).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Box(
                    Modifier.align(Alignment.BottomStart).offset(y = -lineBottom).fillMaxWidth().height(2.dp).drawBehind {
                        drawLine(
                            glow,
                            Offset(0f, 1.dp.toPx()),
                            Offset(size.width, 1.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                        )
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(BUCKETS) {
                Text(
                    label(it),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = NumeralFontFamily, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private val CandleShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
private const val HEADROOM = 1.1
private const val MIN_CANDLE = 0.02f

/** "You drink most around 3pm": the tallest candle of [ThroughTheDayCard], as a lamp-lit insight. */
@Composable
internal fun PeakTimeInsight(state: InsightsUiState.Ready, locale: Locale, modifier: Modifier) {
    val profile = state.timeOfDay ?: return
    val buckets = List(BUCKETS) { b -> profile.hourlyMeanMl.drop(b * BUCKET_HOURS).take(BUCKET_HOURS).sum() }
    if (buckets.max() <= 0.0) return
    val start = state.dayStartMinutes + buckets.indexOf(buckets.max()) * BUCKET_HOURS * MINUTES_PER_HOUR
    val time = DateTimeFormatter.ofPattern(
        "ha",
        locale,
    ).format(LocalTime.of(0, 0).plusMinutes((start + BUCKET_HOURS * MINUTES_PER_HOUR / 2).toLong())).lowercase(locale)
    InsightCard(
        title = stringResource(R.string.feature_insights_peak_title, time),
        body = stringResource(R.string.feature_insights_peak_body),
        icon = DhmoIcons.History,
        modifier = modifier,
    )
}
