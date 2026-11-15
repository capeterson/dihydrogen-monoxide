@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

object Correlation {
    /** 1-based; ties get the average rank. */
    fun ranks(values: List<Double>): List<Double> = TODO("T3")

    /** Null when n < 2 or either variance is 0. */
    fun pearson(xs: List<Double>, ys: List<Double>): Double? = TODO("T3")

    fun spearman(xs: List<Double>, ys: List<Double>): Double? = pearson(ranks(xs), ranks(ys))
}

enum class CorrelationStrength { NONE, WEAK, MODERATE, STRONG }

data class CorrelationResult(val rho: Double, val n: Int, val strength: CorrelationStrength, val positive: Boolean)

/** Null when n < 14 or rho is undefined. */
fun correlate(pairs: List<Pair<Double, Double>>): CorrelationResult? = TODO("T3")
