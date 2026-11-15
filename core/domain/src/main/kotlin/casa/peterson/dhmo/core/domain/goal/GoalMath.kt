@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.goal

import casa.peterson.dhmo.core.model.ClampFlag
import java.time.LocalDate

object GoalMath {
    /** `ChronoUnit.YEARS.between`, floored at 0. */
    fun ageYears(birthDate: LocalDate, on: LocalDate): Int = TODO("T1")

    fun weightBaseMl(weightKg: Double, factorMlPerKg: Int): Int = TODO("T1")

    fun roundTo50(ml: Int): Int = TODO("T1")

    /** Returns (clamped, rounded, flag). */
    fun clampAndRound(rawMl: Int, minMl: Int, maxMl: Int): Triple<Int, Int, ClampFlag> = TODO("T1")
}
