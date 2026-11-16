package casa.peterson.dhmo.core.domain.stats

import kotlin.math.exp
import kotlin.math.sqrt

object Normal {
    private val SQRT_TWO = sqrt(2.0)
    private const val HALF = 0.5
    private const val P = 0.3275911
    private const val A1 = 0.254829592
    private const val A2 = -0.284496736
    private const val A3 = 1.421413741
    private const val A4 = -1.453152027
    private const val A5 = 1.061405429

    /** Abramowitz and Stegun 7.1.26. */
    fun erf(x: Double): Double {
        if (x < 0) return -erf(-x)
        val t = 1.0 / (1.0 + P * x)
        val poly = ((((A5 * t + A4) * t + A3) * t + A2) * t + A1) * t
        return 1.0 - poly * exp(-x * x)
    }

    fun cdf(z: Double): Double = HALF * (1.0 + erf(z / SQRT_TWO))
}
