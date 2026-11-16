package casa.peterson.dhmo.core.domain.stats

import java.time.DayOfWeek
import kotlin.math.abs

private const val ANOMALY_DAYS = 7
private const val ANOMALY_PRIORITY = 90
private const val STREAK_MIN = 3
private const val STREAK_LONGEST_MIN = 7
private const val STREAK_PRIORITY = 80
private const val STREAK_LONGEST_PRIORITY = 85
private const val GOAL_HITS_PRIORITY = 70
private const val GOAL_HITS_MIN_LOGGED = 5
private const val TREND_DAYS = 56
private const val TREND_WEEKS = 8
private const val TREND_MIN_SLOPE_PER_WEEK = 20.0
private const val TREND_PRIORITY = 65
private const val TREND_ROUNDING_ML = 10.0
private const val WEEKEND_DAYS = 28
private const val WEEKEND_MIN_WEEKEND = 4
private const val WEEKEND_MIN_WEEKDAY = 8
private const val WEEKEND_MIN_DIFFERENCE = 0.15
private const val WEEKEND_PRIORITY = 60
private const val WINDOW_LAST_7 = 7
private const val PERCENT = 100.0

private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** Input slices shared by the rules. "Last N days" are the N complete days ending yesterday. */
internal class InsightContext(val input: InsightInput) {
    fun lastDays(n: Int): List<DayStat> = input.days.filter {
        !it.date.isBefore(input.today.minusDays(n.toLong())) && it.date.isBefore(input.today)
    }

    fun lastEntries(n: Int): List<TimedIntake> = input.entries.filter {
        !it.day.isBefore(input.today.minusDays(n.toLong())) && it.day.isBefore(input.today)
    }
}

internal fun roundPercent(fraction: Double): String = Math.round(fraction * PERCENT).toString()

internal fun anomalyInsight(context: InsightContext): Insight? {
    val windowStart = context.input.today.minusDays(ANOMALY_DAYS.toLong())
    val latest = anomalies(context.input.days)
        .filter { !it.date.isBefore(windowStart) && it.date.isBefore(context.input.today) }
        .maxByOrNull { it.date } ?: return null
    return Insight(
        InsightKind.ANOMALY,
        ANOMALY_PRIORITY,
        mapOf("date" to latest.date.toString(), "totalMl" to latest.totalMl.toString()),
        InsightChart.HISTORY_DAY,
    )
}

internal fun streakInsight(context: InsightContext): Insight? {
    val streaks = goalStreak(context.input.days, context.input.weeklyFreeze, todayInProgress = false)
    if (streaks.current < STREAK_MIN) return null
    val isLongest = streaks.current == streaks.longest
    val priority = if (isLongest && streaks.current >= STREAK_LONGEST_MIN) STREAK_LONGEST_PRIORITY else STREAK_PRIORITY
    return Insight(
        InsightKind.STREAK,
        priority,
        mapOf("days" to streaks.current.toString(), "isLongest" to isLongest.toString()),
        InsightChart.MONTH,
    )
}

internal fun goalHitsInsight(context: InsightContext): Insight? {
    val window = context.lastDays(WINDOW_LAST_7)
    if (window.count { it.logged } < GOAL_HITS_MIN_LOGGED) return null
    val met = window.count { it.goalMl != null && it.totalMl >= it.goalMl }
    return Insight(
        InsightKind.GOAL_HITS_LAST_7,
        GOAL_HITS_PRIORITY,
        mapOf("met" to met.toString(), "of" to WINDOW_LAST_7.toString()),
        InsightChart.WEEK,
    )
}

internal fun trendInsight(context: InsightContext): Insight? {
    val result = trend(context.lastDays(TREND_DAYS), context.input.includeNoLogDays)
    val moving = result.direction == TrendDirection.UP || result.direction == TrendDirection.DOWN
    if (!moving || abs(result.slopePerWeekMl) < TREND_MIN_SLOPE_PER_WEEK) return null
    val change = Math.round(abs(result.changeOverRangeMl) / TREND_ROUNDING_ML) * TREND_ROUNDING_ML.toLong()
    return Insight(
        InsightKind.TREND,
        TREND_PRIORITY,
        mapOf(
            "direction" to result.direction.name,
            "changeMl" to change.toString(),
            "weeks" to TREND_WEEKS.toString(),
        ),
        InsightChart.TREND,
    )
}

/** Relative difference of the weekend mean to the weekday mean; null when there are too few days or no weekday volume. */
private fun weekendDifference(included: List<DayStat>): Double? {
    val (weekend, weekdays) = included.partition { it.date.dayOfWeek in WEEKEND }
    if (weekend.size < WEEKEND_MIN_WEEKEND || weekdays.size < WEEKEND_MIN_WEEKDAY) return null
    val weekdayMean = Descriptive.mean(weekdays.map { it.totalMl.toDouble() })
    val weekendMean = Descriptive.mean(weekend.map { it.totalMl.toDouble() })
    return if (weekdayMean <= 0.0) null else (weekendMean - weekdayMean) / weekdayMean
}

internal fun weekendInsight(context: InsightContext): Insight? {
    val included = context.lastDays(WEEKEND_DAYS).included(context.input.includeNoLogDays)
    val difference = weekendDifference(included)?.takeIf { abs(it) >= WEEKEND_MIN_DIFFERENCE } ?: return null
    return Insight(
        InsightKind.WEEKEND_VS_WEEKDAY,
        WEEKEND_PRIORITY,
        mapOf("percent" to roundPercent(abs(difference)), "lower" to (difference < 0).toString()),
        InsightChart.DAY_OF_WEEK,
    )
}
