package casa.peterson.dhmo.core.domain.stats

import casa.peterson.dhmo.core.model.DailyTotal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DayStat(
    val date: LocalDate,
    val totalMl: Int,
    /** `DailyGoal.targetMl` snapshot; null when no snapshot exists for that day. */
    val goalMl: Int?,
    val entryCount: Int,
) {
    val logged: Boolean get() = entryCount > 0
}

/**
 * Builds a contiguous list, one element per day in [from, to], filling days without entries with
 * `totalMl = 0`, `entryCount = 0`.
 */
fun buildDayStats(from: LocalDate, to: LocalDate, totals: List<DailyTotal>, goals: Map<LocalDate, Int>): List<DayStat> {
    if (to.isBefore(from)) return emptyList()
    val byDate = totals.associateBy { it.date }
    val count = ChronoUnit.DAYS.between(from, to) + 1
    return (0 until count).map { offset ->
        val date = from.plusDays(offset)
        val total = byDate[date]
        DayStat(date, total?.totalMl ?: 0, goals[date], total?.entryCount ?: 0)
    }
}

/** The days that take part in a statistic: all of them, or only the logged ones. */
internal fun List<DayStat>.included(includeNoLogDays: Boolean): List<DayStat> = if (includeNoLogDays) this else filter { it.logged }
