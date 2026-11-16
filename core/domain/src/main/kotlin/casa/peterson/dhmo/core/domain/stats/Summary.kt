package casa.peterson.dhmo.core.domain.stats

data class Summary(
    /** Included days. */
    val dayCount: Int,
    val totalMl: Long,
    val meanMl: Double,
    val medianMl: Double,
    val sdMl: Double,
    val minMl: Int,
    val maxMl: Int,
)

/** Null when no day is included. */
fun summary(days: List<DayStat>, includeNoLogDays: Boolean): Summary? {
    val included = days.included(includeNoLogDays)
    if (included.isEmpty()) return null
    val totals = included.map { it.totalMl.toDouble() }
    return Summary(
        dayCount = included.size,
        totalMl = included.sumOf { it.totalMl.toLong() },
        meanMl = Descriptive.mean(totals),
        medianMl = Descriptive.median(totals),
        sdMl = Descriptive.sd(totals),
        minMl = included.minOf { it.totalMl },
        maxMl = included.maxOf { it.totalMl },
    )
}
