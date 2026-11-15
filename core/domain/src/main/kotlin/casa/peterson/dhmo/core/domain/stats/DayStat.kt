@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

import casa.peterson.dhmo.core.model.DailyTotal
import java.time.LocalDate

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
fun buildDayStats(from: LocalDate, to: LocalDate, totals: List<DailyTotal>, goals: Map<LocalDate, Int>): List<DayStat> = TODO("T3")
