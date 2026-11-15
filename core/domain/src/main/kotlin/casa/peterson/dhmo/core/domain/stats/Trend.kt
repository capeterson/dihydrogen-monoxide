@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

enum class TrendDirection { UP, DOWN, STABLE, INSUFFICIENT_DATA }

data class Trend(val direction: TrendDirection, val slopePerWeekMl: Double, val changeOverRangeMl: Double, val pValue: Double?, val n: Int)

fun trend(days: List<DayStat>, includeNoLogDays: Boolean): Trend = TODO("T3")
