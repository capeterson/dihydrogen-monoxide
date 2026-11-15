@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

/** Uses the last 30 elements of `days`. Null when fewer than 7 days are included or the mean is 0. */
fun consistencyScore(days: List<DayStat>, includeNoLogDays: Boolean): Int? = TODO("T3")
