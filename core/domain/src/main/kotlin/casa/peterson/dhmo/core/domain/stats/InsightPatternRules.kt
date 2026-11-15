package casa.peterson.dhmo.core.domain.stats

import kotlin.math.abs

private const val GAP_DAYS = 14
private const val GAP_MIN_DAYS_WITH_ENTRIES = 7
private const val GAP_MAX_SHARE_OF_OTHERS = 0.5
private const val GAP_PRIORITY = 55
private const val EVENING_MIN_SHARE = 0.40
private const val EVENING_PRIORITY = 50
private const val WEAKEST_DAYS = 56
private const val WEAKEST_MIN_SAMPLES = 3
private const val WEAKEST_MAX_SHARE = 0.85
private const val WEAKEST_PRIORITY = 45
private const val CORRELATION_MIN_RHO = 0.4
private const val CORRELATION_PRIORITY = 40
private const val CONSISTENCY_DAYS = 30
private const val CONSISTENCY_HIGH = 80
private const val CONSISTENCY_LOW = 40
private const val CONSISTENCY_PRIORITY = 30
private const val MINUTES_PER_HOUR = 60

/** Clock-time blocks `[startHour, endHour)` used by the biggest-gap rule. */
private val GAP_BLOCKS = listOf(8 to 11, 11 to 14, 14 to 17, 17 to 20)

/** Needs entries on enough days. Index in [GAP_BLOCKS] of the block with less than half the mean volume of the other blocks, if any. */
private fun gapBlockIndex(entries: List<TimedIntake>): Int? {
    if (entries.map { it.day }.distinct().size < GAP_MIN_DAYS_WITH_ENTRIES) return null
    val totals = GAP_BLOCKS.map { (start, end) ->
        entries
            .filter { it.minuteOfDay >= start * MINUTES_PER_HOUR && it.minuteOfDay < end * MINUTES_PER_HOUR }
            .sumOf { it.volumeMl.toDouble() }
    }
    val smallest = totals.indices.minBy { totals[it] }
    val othersMean = Descriptive.mean(totals.filterIndexed { index, _ -> index != smallest })
    return smallest.takeIf { totals[it] < GAP_MAX_SHARE_OF_OTHERS * othersMean }
}

internal fun biggestGapInsight(context: InsightContext): Insight? {
    val index = gapBlockIndex(context.lastEntries(GAP_DAYS)) ?: return null
    val (start, end) = GAP_BLOCKS[index]
    return Insight(
        InsightKind.BIGGEST_GAP_BLOCK,
        GAP_PRIORITY,
        mapOf("block" to "%02d-%02d".format(start, end)),
        InsightChart.TIME_OF_DAY,
    )
}

internal fun eveningShareInsight(context: InsightContext): Insight? {
    val input = context.input
    val goals = input.days.mapNotNull { day -> day.goalMl?.let { day.date to it } }.toMap()
    val share = timeOfDayProfile(context.lastEntries(GAP_DAYS), goals, input.dayStartMinutes).eveningShare
    if (share == null || share < EVENING_MIN_SHARE) return null
    return Insight(
        InsightKind.EVENING_SHARE,
        EVENING_PRIORITY,
        mapOf("percent" to roundPercent(share)),
        InsightChart.TIME_OF_DAY,
    )
}

internal fun weakestDayInsight(context: InsightContext): Insight? {
    val pattern = dayOfWeekPattern(context.lastDays(WEAKEST_DAYS), context.input.includeNoLogDays)
    val weakest = pattern.byDay.firstOrNull { it.day == pattern.weakest }
    val overall = pattern.overallMeanMl
    val share = if (weakest?.meanMl == null || overall == null) null else weakest.meanMl / overall
    val qualifying = share
        ?.takeIf { pattern.byDay.all { it.sampleCount >= WEAKEST_MIN_SAMPLES } }
        ?.takeIf { it <= WEAKEST_MAX_SHARE }
    if (weakest == null || qualifying == null) return null
    return Insight(
        InsightKind.WEAKEST_DAY,
        WEAKEST_PRIORITY,
        mapOf("day" to weakest.day.name, "percentBelow" to roundPercent(1.0 - qualifying)),
        InsightChart.DAY_OF_WEEK,
    )
}

internal fun correlationInsight(kind: InsightKind, chart: InsightChart, pairs: List<Pair<Double, Double>>): Insight? {
    val result = correlate(pairs)
    if (result == null || abs(result.rho) < CORRELATION_MIN_RHO) return null
    return Insight(
        kind,
        CORRELATION_PRIORITY,
        mapOf("positive" to result.positive.toString(), "strength" to result.strength.name),
        chart,
    )
}

internal fun consistencyInsight(context: InsightContext): Insight? {
    val score = consistencyScore(context.lastDays(CONSISTENCY_DAYS), context.input.includeNoLogDays)
        ?.takeIf { it >= CONSISTENCY_HIGH || it <= CONSISTENCY_LOW }
        ?: return null
    return Insight(
        InsightKind.CONSISTENCY,
        CONSISTENCY_PRIORITY,
        mapOf("score" to score.toString(), "high" to (score >= CONSISTENCY_HIGH).toString()),
        InsightChart.MONTH,
    )
}
