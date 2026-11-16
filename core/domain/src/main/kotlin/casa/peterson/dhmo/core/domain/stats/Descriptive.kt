package casa.peterson.dhmo.core.domain.stats

import kotlin.math.sqrt

object Descriptive {
    /** Requires a non-empty list. */
    fun mean(values: List<Double>): Double {
        require(values.isNotEmpty()) { "mean of an empty list" }
        return values.sum() / values.size
    }

    /** Requires a non-empty list; the median of an even-sized list is the mean of the two middle values. */
    fun median(values: List<Double>): Double {
        require(values.isNotEmpty()) { "median of an empty list" }
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** Sample standard deviation (divisor n - 1); 0.0 when n < 2. */
    fun sd(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = mean(values)
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }
}
