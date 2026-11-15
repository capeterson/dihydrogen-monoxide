@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

import java.time.DayOfWeek

data class WeekdayMean(val day: DayOfWeek, val meanMl: Double?, val sampleCount: Int)

data class DayOfWeekPattern(
    /** MONDAY..SUNDAY. */
    val byDay: List<WeekdayMean>,
    val weakest: DayOfWeek?,
    val overallMeanMl: Double?,
)

fun dayOfWeekPattern(days: List<DayStat>, includeNoLogDays: Boolean): DayOfWeekPattern = TODO("T3")
