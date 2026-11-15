@file:Suppress("MagicNumber") // 0.1 kg units and the 50 ml step are defined by TECHNICAL_PLAN §7.2

package casa.peterson.dhmo.core.domain.goal

import casa.peterson.dhmo.core.model.ClampFlag
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object GoalMath {
    /** `ChronoUnit.YEARS.between`, floored at 0. */
    fun ageYears(birthDate: LocalDate, on: LocalDate): Int = ChronoUnit.YEARS.between(birthDate, on).toInt().coerceAtLeast(0)

    /** Weight in 0.1 kg (half up, via BigDecimal), then `(hg * factor + 5) / 10`. */
    fun weightBaseMl(weightKg: Double, factorMlPerKg: Int): Int {
        val hg = BigDecimal.valueOf(weightKg).movePointRight(1).setScale(0, RoundingMode.HALF_UP).intValueExact()
        return (hg * factorMlPerKg + 5) / 10
    }

    /** Nearest multiple of 50; halves round up. For `ml >= 0`. */
    fun roundTo50(ml: Int): Int = ((ml + 25) / 50) * 50

    /** Returns (clamped, rounded, flag). */
    fun clampAndRound(rawMl: Int, minMl: Int, maxMl: Int): Triple<Int, Int, ClampFlag> {
        val clamped = rawMl.coerceIn(minMl, maxMl)
        val flag = when {
            rawMl < minMl -> ClampFlag.LOW
            rawMl > maxMl -> ClampFlag.HIGH
            else -> ClampFlag.NONE
        }
        return Triple(clamped, roundTo50(clamped), flag)
    }
}
