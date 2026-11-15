package casa.peterson.dhmo.core.domain.pace

import casa.peterson.dhmo.core.model.settings.CurvePoint
import casa.peterson.dhmo.core.model.settings.PaceCurveSettings
import casa.peterson.dhmo.core.model.settings.PaceCurveType

private const val PERMILLE = 1000.0
private const val MIN_POINTS = 2
private const val MAX_POINTS = 12
private const val FULL = 1000

/** A piecewise-linear function from the fraction of the wake window to the fraction of the goal (§8.2). */
data class PaceCurve(val points: List<CurvePoint>) {
    /** f(x); x is clamped to [0, 1]. */
    fun fraction(x: Double): Double {
        val px = x.coerceIn(0.0, 1.0) * PERMILLE
        for (i in 0 until points.size - 1) {
            val (x0, y0) = points[i]
            val (x1, y1) = points[i + 1]
            if (px >= x0 && px <= x1) {
                return (y0 + (y1 - y0) * (px - x0) / (x1 - x0)) / PERMILLE
            }
        }
        return points.last().yPermille / PERMILLE
    }

    /** max { x in [0,1] : f(x) <= q }; null when q >= 1.0; 0.0 when q < 0. */
    fun lastXAtOrBelow(q: Double): Double? {
        if (q >= 1.0) return null
        var candidate = 0.0
        for (i in 0 until points.size - 1) {
            val (x0, y0) = points[i]
            val (x1, y1) = points[i + 1]
            if (y0 / PERMILLE > q) continue
            candidate = if (y1 / PERMILLE <= q) {
                x1 / PERMILLE
            } else {
                (x0 + (PERMILLE * q - y0) * (x1 - x0) / (y1 - y0)) / PERMILLE
            }
        }
        return candidate
    }

    companion object {
        val DEFAULT =
            PaceCurve(listOf(CurvePoint(0, 0), CurvePoint(100, 150), CurvePoint(900, 1000), CurvePoint(1000, 1000)))
        val LINEAR = PaceCurve(listOf(CurvePoint(0, 0), CurvePoint(1000, 1000)))

        fun validate(points: List<CurvePoint>): Boolean = points.size in MIN_POINTS..MAX_POINTS &&
            points.first() == CurvePoint(0, 0) &&
            points.last() == CurvePoint(FULL, FULL) &&
            points.zipWithNext().all { (a, b) -> b.xPermille > a.xPermille && b.yPermille >= a.yPermille }

        /** An invalid CUSTOM curve falls back to [DEFAULT]. */
        fun from(settings: PaceCurveSettings): PaceCurve = when (settings.type) {
            PaceCurveType.DEFAULT -> DEFAULT

            PaceCurveType.LINEAR -> LINEAR

            PaceCurveType.CUSTOM ->
                if (validate(settings.customPoints)) PaceCurve(settings.customPoints) else DEFAULT
        }
    }
}
