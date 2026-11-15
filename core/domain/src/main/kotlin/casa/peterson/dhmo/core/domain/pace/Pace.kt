@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.pace

import java.time.Instant

fun expectedFraction(now: Instant, window: WakeWindow, curve: PaceCurve): Double = TODO("T2")

/** The display value: `floor(goalMl * expectedFraction + 0.5)`. */
fun expectedMl(goalMl: Int, now: Instant, window: WakeWindow, curve: PaceCurve): Int = TODO("T2")

enum class PaceStatus { AHEAD, ON_TRACK, BEHIND }

/** `deficitMl` = expected minus consumed (may be negative). */
data class Pace(val expectedMl: Int, val deficitMl: Int, val status: PaceStatus)

fun pace(goalMl: Int, consumedMl: Int, tolerancePercent: Int, now: Instant, window: WakeWindow, curve: PaceCurve): Pace = TODO("T2")
