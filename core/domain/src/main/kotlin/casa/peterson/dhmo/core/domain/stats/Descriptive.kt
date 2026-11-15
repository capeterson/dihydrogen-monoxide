@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

object Descriptive {
    /** Requires a non-empty list. */
    fun mean(values: List<Double>): Double = TODO("T3")

    /** Requires a non-empty list; the median of an even-sized list is the mean of the two middle values. */
    fun median(values: List<Double>): Double = TODO("T3")

    /** Sample standard deviation (divisor n - 1); 0.0 when n < 2. */
    fun sd(values: List<Double>): Double = TODO("T3")
}
