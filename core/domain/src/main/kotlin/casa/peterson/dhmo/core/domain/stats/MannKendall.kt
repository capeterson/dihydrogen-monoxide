@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

data class MannKendall(val s: Int, val variance: Double, val z: Double, val pValue: Double)

/** `ys` in time order; null when n < 3. */
fun mannKendall(ys: List<Double>): MannKendall? = TODO("T3")
