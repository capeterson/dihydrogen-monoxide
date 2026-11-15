package casa.peterson.dhmo.core.domain.stats

import java.time.LocalDate

enum class InsightKind {
    GOAL_HITS_LAST_7,
    STREAK,
    TREND,
    WEEKEND_VS_WEEKDAY,
    WEAKEST_DAY,
    BIGGEST_GAP_BLOCK,
    EVENING_SHARE,
    CONSISTENCY,
    ANOMALY,
    TEMPERATURE_CORRELATION,
    ACTIVITY_CORRELATION,
}

enum class InsightChart {
    WEEK,
    MONTH,
    YEAR,
    TIME_OF_DAY,
    DAY_OF_WEEK,
    TREND,
    CORRELATION_TEMPERATURE,
    CORRELATION_ACTIVITY,
    HISTORY_DAY,
}

data class Insight(val kind: InsightKind, val priority: Int, val params: Map<String, String>, val chart: InsightChart)

data class InsightInput(
    val today: LocalDate,
    /** Contiguous, ending yesterday, up to 90 days. */
    val days: List<DayStat>,
    /** Last 14 days. */
    val entries: List<TimedIntake>,
    /** Last 60 days. */
    val temperature: List<Pair<Double, Double>>,
    val activeMinutes: List<Pair<Double, Double>>,
    val dayStartMinutes: Int,
    val includeNoLogDays: Boolean,
    val weeklyFreeze: Boolean,
)

/** Sorted by priority descending, then by [InsightKind] ordinal; the UI shows the first 5. */
fun buildInsights(input: InsightInput): List<Insight> {
    val context = InsightContext(input)
    return listOfNotNull(
        anomalyInsight(context),
        streakInsight(context),
        goalHitsInsight(context),
        trendInsight(context),
        weekendInsight(context),
        biggestGapInsight(context),
        eveningShareInsight(context),
        weakestDayInsight(context),
        correlationInsight(
            InsightKind.TEMPERATURE_CORRELATION,
            InsightChart.CORRELATION_TEMPERATURE,
            input.temperature,
        ),
        correlationInsight(
            InsightKind.ACTIVITY_CORRELATION,
            InsightChart.CORRELATION_ACTIVITY,
            input.activeMinutes,
        ),
        consistencyInsight(context),
    ).sortedWith(compareByDescending<Insight> { it.priority }.thenBy { it.kind.ordinal })
}
