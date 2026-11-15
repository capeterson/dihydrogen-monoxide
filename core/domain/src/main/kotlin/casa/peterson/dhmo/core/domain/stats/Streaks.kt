@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

data class Streaks(val current: Int, val longest: Int)

fun goalStreak(days: List<DayStat>, weeklyFreeze: Boolean, todayInProgress: Boolean): Streaks = TODO("T3")

fun loggingStreak(days: List<DayStat>, weeklyFreeze: Boolean, todayInProgress: Boolean): Streaks = TODO("T3")
