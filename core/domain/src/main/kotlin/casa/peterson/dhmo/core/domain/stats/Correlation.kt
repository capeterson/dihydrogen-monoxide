package casa.peterson.dhmo.core.domain.stats

import kotlin.math.abs
import kotlin.math.sqrt

private const val MIN_PAIRS = 14
private const val WEAK_FROM = 0.2
private const val MODERATE_FROM = 0.4
private const val STRONG_FROM = 0.6

object Correlation {
    /** 1-based; ties get the average rank. */
    fun ranks(values: List<Double>): List<Double> {
        val order = values.indices.sortedBy { values[it] }
        val ranks = DoubleArray(values.size)
        var start = 0
        while (start < order.size) {
            var end = start
            while (end + 1 < order.size && values[order[end + 1]] == values[order[start]]) end++
            val average = (start + end) / 2.0 + 1.0
            for (k in start..end) ranks[order[k]] = average
            start = end + 1
        }
        return ranks.toList()
    }

    /** Null when n < 2 or either variance is 0. */
    fun pearson(xs: List<Double>, ys: List<Double>): Double? {
        require(xs.size == ys.size) { "xs and ys differ in size" }
        if (xs.size < 2) return null
        val mx = Descriptive.mean(xs)
        val my = Descriptive.mean(ys)
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - mx
            val dy = ys[i] - my
            sxy += dx * dy
            sxx += dx * dx
            syy += dy * dy
        }
        return if (sxx == 0.0 || syy == 0.0) null else sxy / sqrt(sxx * syy)
    }

    fun spearman(xs: List<Double>, ys: List<Double>): Double? = pearson(ranks(xs), ranks(ys))
}

enum class CorrelationStrength { NONE, WEAK, MODERATE, STRONG }

data class CorrelationResult(val rho: Double, val n: Int, val strength: CorrelationStrength, val positive: Boolean)

/** Null when n < 14 or rho is undefined. */
fun correlate(pairs: List<Pair<Double, Double>>): CorrelationResult? {
    val rho = pairs.takeIf { it.size >= MIN_PAIRS }
        ?.let { Correlation.spearman(pairs.map { it.first }, pairs.map { it.second }) }
        ?: return null
    val magnitude = abs(rho)
    val strength = when {
        magnitude < WEAK_FROM -> CorrelationStrength.NONE
        magnitude < MODERATE_FROM -> CorrelationStrength.WEAK
        magnitude < STRONG_FROM -> CorrelationStrength.MODERATE
        else -> CorrelationStrength.STRONG
    }
    return CorrelationResult(rho, pairs.size, strength, rho > 0)
}
