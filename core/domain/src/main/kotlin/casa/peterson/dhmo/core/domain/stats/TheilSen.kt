package casa.peterson.dhmo.core.domain.stats

private const val DAYS_PER_WEEK = 7

data class TheilSen(val slopePerDay: Double, val intercept: Double) {
    val slopePerWeek: Double get() = slopePerDay * DAYS_PER_WEEK
}

/** Null when there are fewer than two points or no valid pair. */
fun theilSen(xs: List<Double>, ys: List<Double>): TheilSen? {
    require(xs.size == ys.size) { "xs and ys differ in size" }
    val slopes = ArrayList<Double>()
    for (i in xs.indices) {
        for (j in i + 1 until xs.size) {
            if (xs[j] != xs[i]) slopes.add((ys[j] - ys[i]) / (xs[j] - xs[i]))
        }
    }
    if (slopes.isEmpty()) return null
    val slope = Descriptive.median(slopes)
    val intercept = Descriptive.median(xs.indices.map { ys[it] - slope * xs[it] })
    return TheilSen(slope, intercept)
}
