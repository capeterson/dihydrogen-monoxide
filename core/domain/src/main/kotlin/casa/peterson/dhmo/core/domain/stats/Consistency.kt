package casa.peterson.dhmo.core.domain.stats

private const val WINDOW_DAYS = 30
private const val MIN_DAYS = 7
private const val SCORE_SCALE = 100.0

/** Uses the last 30 elements of `days`. Null when fewer than 7 days are included or the mean is 0. */
fun consistencyScore(days: List<DayStat>, includeNoLogDays: Boolean): Int? {
    val totals = days.takeLast(WINDOW_DAYS).included(includeNoLogDays).map { it.totalMl.toDouble() }
    val mean = totals.takeIf { it.size >= MIN_DAYS }?.let(Descriptive::mean)?.takeIf { it != 0.0 } ?: return null
    val cv = Descriptive.sd(totals) / mean
    return Math.round(SCORE_SCALE * (1.0 - cv).coerceIn(0.0, 1.0)).toInt()
}
