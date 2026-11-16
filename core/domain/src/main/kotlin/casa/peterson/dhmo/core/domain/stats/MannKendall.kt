package casa.peterson.dhmo.core.domain.stats

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

private const val MIN_POINTS = 3
private const val VARIANCE_DIVISOR = 18.0
private const val VAR_CONSTANT = 5.0

data class MannKendall(val s: Int, val variance: Double, val z: Double, val pValue: Double)

/** `ys` in time order; null when n < 3. */
fun mannKendall(ys: List<Double>): MannKendall? {
    val n = ys.size
    if (n < MIN_POINTS) return null
    var s = 0
    for (i in 0 until n) {
        for (j in i + 1 until n) s += sign(ys[j] - ys[i]).toInt()
    }
    val tieTerm = ys.groupingBy { it }.eachCount().values.sumOf { varianceTerm(it.toDouble()) }
    val variance = (varianceTerm(n.toDouble()) - tieTerm) / VARIANCE_DIVISOR
    val z = when {
        variance == 0.0 || s == 0 -> 0.0
        s > 0 -> (s - 1) / sqrt(variance)
        else -> (s + 1) / sqrt(variance)
    }
    val p = if (z == 0.0) 1.0 else 2.0 * (1.0 - Normal.cdf(abs(z)))
    return MannKendall(s, variance, z, p)
}

/** `t (t - 1) (2 t + 5)`. */
private fun varianceTerm(t: Double): Double = t * (t - 1) * (2 * t + VAR_CONSTANT)
