@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

data class TheilSen(val slopePerDay: Double, val intercept: Double) {
    val slopePerWeek: Double get() = slopePerDay * 7
}

/** Null when there are fewer than two points or no valid pair. */
fun theilSen(xs: List<Double>, ys: List<Double>): TheilSen? = TODO("T3")
