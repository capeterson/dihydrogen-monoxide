package casa.peterson.dhmo.core.domain.stats

/**
 * Trailing mean over the window ending at each index. Element i is null when `i < window - 1`, or when the
 * window holds no included day.
 */
fun rollingMean(days: List<DayStat>, window: Int, includeNoLogDays: Boolean): List<Double?> {
    require(window > 0) { "window must be positive" }
    var sum = 0.0
    var count = 0
    return days.mapIndexed { i, day ->
        if (includeNoLogDays || day.logged) {
            sum += day.totalMl
            count++
        }
        if (i >= window) {
            val expired = days[i - window]
            if (includeNoLogDays || expired.logged) {
                sum -= expired.totalMl
                count--
            }
        }
        if (i < window - 1 || count == 0) null else sum / count
    }
}
