package casa.peterson.dhmo.core.domain.stats

import java.time.temporal.IsoFields

data class Streaks(val current: Int, val longest: Int)

fun goalStreak(days: List<DayStat>, weeklyFreeze: Boolean, todayInProgress: Boolean): Streaks =
    scanStreak(days, weeklyFreeze, todayInProgress) { it.goalMl != null && it.totalMl >= it.goalMl }

fun loggingStreak(days: List<DayStat>, weeklyFreeze: Boolean, todayInProgress: Boolean): Streaks =
    scanStreak(days, weeklyFreeze, todayInProgress) { it.entryCount > 0 }

private fun scanStreak(days: List<DayStat>, weeklyFreeze: Boolean, todayInProgress: Boolean, predicate: (DayStat) -> Boolean): Streaks {
    var run = 0
    var longest = 0
    val usedWeeks = mutableSetOf<Pair<Int, Int>>()
    days.forEachIndexed { index, day ->
        val week = day.date.get(IsoFields.WEEK_BASED_YEAR) to day.date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        when {
            predicate(day) -> run++
            index == days.lastIndex && todayInProgress -> Unit
            weeklyFreeze && run > 0 && week !in usedWeeks -> usedWeeks.add(week)
            else -> run = 0
        }
        longest = maxOf(longest, run)
    }
    return Streaks(current = run, longest = longest)
}
