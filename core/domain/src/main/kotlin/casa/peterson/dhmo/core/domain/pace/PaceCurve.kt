@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.pace

import casa.peterson.dhmo.core.model.settings.CurvePoint
import casa.peterson.dhmo.core.model.settings.PaceCurveSettings

/** A piecewise-linear function from the fraction of the wake window to the fraction of the goal (§8.2). */
data class PaceCurve(val points: List<CurvePoint>) {
    /** f(x); x is clamped to [0, 1]. */
    fun fraction(x: Double): Double = TODO("T2")

    /** max { x in [0,1] : f(x) <= q }; null when q >= 1.0; 0.0 when q < 0. */
    fun lastXAtOrBelow(q: Double): Double? = TODO("T2")

    companion object {
        val DEFAULT =
            PaceCurve(listOf(CurvePoint(0, 0), CurvePoint(100, 150), CurvePoint(900, 1000), CurvePoint(1000, 1000)))
        val LINEAR = PaceCurve(listOf(CurvePoint(0, 0), CurvePoint(1000, 1000)))

        fun validate(points: List<CurvePoint>): Boolean = TODO("T2")

        /** An invalid CUSTOM curve falls back to [DEFAULT]. */
        fun from(settings: PaceCurveSettings): PaceCurve = TODO("T2")
    }
}
