package casa.peterson.dhmo.core.domain.stats

/**
 * Trailing mean over the window ending at each index. Element i is null when `i < window - 1`, or when the
 * window holds no included day.
 */
fun rollingMean(days: List<DayStat>, window: Int, includeNoLogDays: Boolean): List<Double?> = days.indices.map { i ->
    if (i < window - 1) {
        null
    } else {
        val included = days.subList(i - window + 1, i + 1).included(includeNoLogDays)
        if (included.isEmpty()) null else included.sumOf { it.totalMl.toDouble() } / included.size
    }
}
