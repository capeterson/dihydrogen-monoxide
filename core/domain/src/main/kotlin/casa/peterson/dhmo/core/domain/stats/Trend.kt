package casa.peterson.dhmo.core.domain.stats

import java.time.temporal.ChronoUnit

private const val MIN_TREND_DAYS = 8
private const val SIGNIFICANCE = 0.05

enum class TrendDirection { UP, DOWN, STABLE, INSUFFICIENT_DATA }

data class Trend(val direction: TrendDirection, val slopePerWeekMl: Double, val changeOverRangeMl: Double, val pValue: Double?, val n: Int)

fun trend(days: List<DayStat>, includeNoLogDays: Boolean): Trend {
    val included = days.included(includeNoLogDays)
    val n = included.size
    val insufficient = Trend(TrendDirection.INSUFFICIENT_DATA, 0.0, 0.0, null, n)
    if (n < MIN_TREND_DAYS) return insufficient
    val origin = days.first().date
    val xs = included.map { ChronoUnit.DAYS.between(origin, it.date).toDouble() }
    val ys = included.map { it.totalMl.toDouble() }
    val fit = theilSen(xs, ys)
    val test = mannKendall(ys)
    return if (fit == null || test == null) insufficient else classify(fit, test, xs, n)
}

private fun classify(fit: TheilSen, test: MannKendall, xs: List<Double>, n: Int): Trend {
    val significant = test.pValue < SIGNIFICANCE && fit.slopePerDay != 0.0
    val direction = when {
        !significant -> TrendDirection.STABLE
        fit.slopePerDay > 0 -> TrendDirection.UP
        else -> TrendDirection.DOWN
    }
    return Trend(direction, fit.slopePerWeek, fit.slopePerDay * (xs.last() - xs.first()), test.pValue, n)
}
