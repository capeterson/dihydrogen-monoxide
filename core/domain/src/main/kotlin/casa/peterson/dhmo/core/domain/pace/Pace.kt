package casa.peterson.dhmo.core.domain.pace

import java.time.Duration
import java.time.Instant
import kotlin.math.floor

private const val PERCENT = 100
private const val HALF = 0.5

fun expectedFraction(now: Instant, window: WakeWindow, curve: PaceCurve): Double = curve.fraction(
    Duration.between(window.wake, now).toMillis().toDouble() /
        Duration.between(window.wake, window.sleep).toMillis(),
)

/** The display value: `floor(goalMl * expectedFraction + HALF)`. */
fun expectedMl(goalMl: Int, now: Instant, window: WakeWindow, curve: PaceCurve): Int =
    floor(goalMl * expectedFraction(now, window, curve) + HALF).toInt()

enum class PaceStatus { AHEAD, ON_TRACK, BEHIND }

/** `deficitMl` = expected minus consumed (may be negative). */
data class Pace(val expectedMl: Int, val deficitMl: Int, val status: PaceStatus)

fun pace(goalMl: Int, consumedMl: Int, tolerancePercent: Int, now: Instant, window: WakeWindow, curve: PaceCurve): Pace {
    val expected = expectedMl(goalMl, now, window, curve)
    val deficit = expected - consumedMl
    val toleranceMl = (goalMl * tolerancePercent + PERCENT / 2) / PERCENT
    val status = when {
        deficit > toleranceMl -> PaceStatus.BEHIND
        deficit < -toleranceMl -> PaceStatus.AHEAD
        else -> PaceStatus.ON_TRACK
    }
    return Pace(expected, deficit, status)
}
