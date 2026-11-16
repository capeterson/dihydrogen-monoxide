package casa.peterson.dhmo.core.domain.stats

import java.time.DayOfWeek

private const val MIN_WEAKEST_SAMPLES = 2

data class WeekdayMean(val day: DayOfWeek, val meanMl: Double?, val sampleCount: Int)

data class DayOfWeekPattern(
    /** MONDAY..SUNDAY. */
    val byDay: List<WeekdayMean>,
    val weakest: DayOfWeek?,
    val overallMeanMl: Double?,
)

fun dayOfWeekPattern(days: List<DayStat>, includeNoLogDays: Boolean): DayOfWeekPattern {
    val included = days.included(includeNoLogDays)
    val byDay = DayOfWeek.entries.map { dow ->
        val totals = included.filter { it.date.dayOfWeek == dow }.map { it.totalMl.toDouble() }
        WeekdayMean(dow, if (totals.isEmpty()) null else Descriptive.mean(totals), totals.size)
    }
    val weakest = byDay
        .filter { it.sampleCount >= MIN_WEAKEST_SAMPLES && it.meanMl != null }
        .minByOrNull { it.meanMl ?: Double.MAX_VALUE }
        ?.day
    val overall = if (included.isEmpty()) null else Descriptive.mean(included.map { it.totalMl.toDouble() })
    return DayOfWeekPattern(byDay, weakest, overall)
}
